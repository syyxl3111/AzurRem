#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""量一下「点开一个任务配置页」到底慢在哪。

App 里 openTaskConfig() 会做两件事：
  1. GET 桥的 /api/task_schema?task=X      （纯 HTTP，一次往返）
  2. 开一整轮 MCP 会话调 get_config        （SSE 握手 + initialize + 通知 + tools/call）

第 2 步到底几次往返、每次多久，光看代码猜不出来（服务端还要读盘、要 deep merge
默认值）。这个脚本用标准库把整条流程复刻一遍，逐步计时。

用法：
    python bridge\\mcp_timing.py
    python bridge\\mcp_timing.py --task Commission --repeat 3
"""

from __future__ import annotations

import argparse
import http.client
import json
import os
import re
import statistics
import sys
import threading
import time
from urllib.parse import urlparse

DEFAULT_SERVER = os.environ.get("AZURPILOT_SERVER", "http://127.0.0.1:25548")
DEFAULT_BRIDGE = os.environ.get("AZURREM_BRIDGE", "http://127.0.0.1:25550")


class SseSession:
    """一轮 MCP SSE 会话，逐步骤记时。"""

    def __init__(self, base: str):
        parsed = urlparse(base)
        self.host = parsed.hostname
        self.port = parsed.port or 80
        self.origin = f"{parsed.scheme}://{self.host}:{self.port}"
        self.endpoint: str | None = None
        self._conn: http.client.HTTPConnection | None = None
        self._resp = None
        self._next_id = 1
        self._lock = threading.Lock()
        self._inbox: dict[int, dict] = {}
        self._event = threading.Event()
        self._reader: threading.Thread | None = None
        self.timings: dict[str, float] = {}

    # ---- SSE 读线程 -------------------------------------------------
    def _read_loop(self):
        buf_event = None
        data_lines: list[str] = []
        while True:
            try:
                raw = self._resp.readline()
            except Exception:
                return
            if not raw:
                return
            line = raw.decode("utf-8", "replace").rstrip("\r\n")
            if line == "":
                if buf_event and data_lines:
                    payload = "\n".join(data_lines)
                    if buf_event == "endpoint":
                        self.endpoint = payload.strip()
                        self._event.set()
                    elif buf_event == "message":
                        try:
                            obj = json.loads(payload)
                        except Exception:
                            obj = None
                        if isinstance(obj, dict) and "id" in obj:
                            with self._lock:
                                self._inbox[obj["id"]] = obj
                                self._event.set()
                buf_event, data_lines = None, []
                continue
            if line.startswith("event:"):
                buf_event = line[6:].strip()
            elif line.startswith("data:"):
                data_lines.append(line[5:].lstrip())

    # ---- 步骤 -------------------------------------------------------
    def connect(self) -> str:
        t0 = time.perf_counter()
        self._conn = http.client.HTTPConnection(self.host, self.port, timeout=15)
        self._conn.request("GET", "/mcp/sse", headers={"Accept": "text/event-stream"})
        self._resp = self._conn.getresponse()
        self._reader = threading.Thread(target=self._read_loop, daemon=True)
        self._reader.start()
        if not self._event.wait(15) or not self.endpoint:
            raise RuntimeError("等 MCP endpoint 超时")
        self.timings["1_sse_handshake"] = (time.perf_counter() - t0) * 1000
        return self.endpoint

    def _post(self, body: dict) -> None:
        conn = http.client.HTTPConnection(self.host, self.port, timeout=15)
        conn.request(
            "POST", self.endpoint, body=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json"},
        )
        resp = conn.getresponse()
        resp.read()
        conn.close()

    def rpc(self, method: str, params: dict | None = None, label: str | None = None):
        t0 = time.perf_counter()
        rid = self._next_id
        self._next_id += 1
        body = {"jsonrpc": "2.0", "id": rid, "method": method}
        if params is not None:
            body["params"] = params
        with self._lock:
            self._inbox.pop(rid, None)
            self._event.clear()
        self._post(body)
        deadline = time.time() + 30
        while time.time() < deadline:
            with self._lock:
                if rid in self._inbox:
                    obj = self._inbox.pop(rid)
                    break
            self._event.wait(0.05)
        else:
            raise RuntimeError(f"{method} 超时")
        if label:
            self.timings[label] = (time.perf_counter() - t0) * 1000
        return obj

    def notify(self, method: str, params: dict | None = None, label: str | None = None):
        t0 = time.perf_counter()
        body = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            body["params"] = params
        self._post(body)
        if label:
            self.timings[label] = (time.perf_counter() - t0) * 1000

    def close(self):
        try:
            if self._conn:
                self._conn.close()
        except Exception:
            pass


def tool_text(obj: dict) -> str:
    result = obj.get("result") or {}
    parts = []
    for item in result.get("content") or []:
        if item.get("type") == "text":
            parts.append(item.get("text") or "")
    return "".join(parts)


def time_schema(bridge: str, task: str) -> float:
    parsed = urlparse(bridge)
    conn = http.client.HTTPConnection(parsed.hostname, parsed.port or 80, timeout=15)
    t0 = time.perf_counter()
    conn.request("GET", f"/api/task_schema?task={task}")
    resp = conn.getresponse()
    body = resp.read()
    conn.close()
    dt = (time.perf_counter() - t0) * 1000
    if resp.status != 200:
        raise RuntimeError(f"桥返回 {resp.status}")
    return dt


def one_open(server: str, bridge: str, instance: str, task: str) -> dict:
    """模拟 App 里 openTaskConfig() 的完整耗时。"""
    out: dict[str, float] = {}
    out["0_bridge_schema"] = time_schema(bridge, task)

    sess = SseSession(server)
    try:
        sess.connect()
        sess.timings["2_initialize"] = 0.0
        t0 = time.perf_counter()
        sess.rpc("initialize", {
            "protocolVersion": "2024-11-05", "capabilities": {},
            "clientInfo": {"name": "timing", "version": "1.0"},
        })
        sess.timings["2_initialize"] = (time.perf_counter() - t0) * 1000

        sess.notify("notifications/initialized", label="3_initialized_notify")
        resp = sess.rpc("tools/call", {
            "name": "get_config",
            "arguments": {"instance": instance, "task": task},
        }, label="4_get_config")
        text = tool_text(resp)
        out["payload_bytes"] = float(len(text))
        out.update(sess.timings)
    finally:
        sess.close()
    return out


def batch_open(server: str, instance: str, tasks: list[str]) -> dict:
    """★ 关键实验：一个会话里连续调 N 次 get_config，看能不能省掉 N-1 次握手。"""
    out: dict[str, float] = {}
    sess = SseSession(server)
    try:
        sess.connect()
        t0 = time.perf_counter()
        sess.rpc("initialize", {
            "protocolVersion": "2024-11-05", "capabilities": {},
            "clientInfo": {"name": "timing", "version": "1.0"},
        })
        out["_initialize"] = (time.perf_counter() - t0) * 1000
        sess.notify("notifications/initialized")

        per_call = []
        for task in tasks:
            t0 = time.perf_counter()
            resp = sess.rpc("tools/call", {
                "name": "get_config",
                "arguments": {"instance": instance, "task": task},
            })
            per_call.append((time.perf_counter() - t0) * 1000)
            if "error" in resp:
                print(f"      [!] {task} 返回 error: {resp['error']}")
            elif not tool_text(resp).strip():
                print(f"      [!] {task} 返回空文本")
        out["_calls_total"] = sum(per_call)
        out["_calls_median"] = statistics.median(per_call) if per_call else 0.0
        out["_calls_max"] = max(per_call) if per_call else 0.0
        out["_n"] = float(len(per_call))
    finally:
        sess.close()
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default=DEFAULT_SERVER)
    ap.add_argument("--bridge", default=DEFAULT_BRIDGE)
    ap.add_argument("--instance", default="alas")
    ap.add_argument("--task", default="Guild")
    ap.add_argument("--repeat", type=int, default=2)
    ap.add_argument("--all", action="store_true",
                    help="批量实验改用任务树里的全部 93 个任务，而不是写死的 10 个")
    args = ap.parse_args()

    print("=" * 72)
    print("  打开一个任务配置页 —— 分步耗时")
    print("=" * 72)
    print(f"  MCP  : {args.server}")
    print(f"  桥    : {args.bridge}")
    print(f"  任务  : {args.task}   重复 {args.repeat} 次")
    print()

    steps = ["0_bridge_schema", "1_sse_handshake", "2_initialize",
             "3_initialized_notify", "4_get_config"]
    labels = {
        "0_bridge_schema": "桥 /api/task_schema (HTTP 单跳)",
        "1_sse_handshake": "MCP: GET /mcp/sse 等 endpoint 帧",
        "2_initialize": "MCP: initialize 往返",
        "3_initialized_notify": "MCP: notifications/initialized",
        "4_get_config": "MCP: tools/call get_config",
    }

    runs = []
    for i in range(args.repeat):
        try:
            r = one_open(args.server, args.bridge, args.instance, args.task)
            runs.append(r)
            total = sum(r.get(s, 0.0) for s in steps)
            print(f"  第 {i + 1} 次  合计 {total:7.1f} ms   "
                  + "  ".join(f"{s.split('_', 1)[1]}={r.get(s, 0):.0f}" for s in steps))
        except Exception as exc:
            print(f"  第 {i + 1} 次失败：{exc}")
    if not runs:
        return 1

    print()
    print("  ── 各步耗时（中位数）──")
    grand = 0.0
    for s in steps:
        vals = [r.get(s, 0.0) for r in runs]
        med = statistics.median(vals)
        grand += med
        print(f"    {med:8.1f} ms   {labels[s]}")
    print(f"    {'-' * 8}")
    print(f"    {grand:8.1f} ms   ★ 用户实际等待（缓存前）")
    print()

    # ---- 批量实验 ----
    tasks = ["Guild", "Commission", "Research", "Tactical", "Main",
             "Exercise", "Battle", "Dorm", "Meowfficer", "Mission"]
    if args.all:
        parsed_b = urlparse(args.bridge)
        conn = http.client.HTTPConnection(parsed_b.hostname, parsed_b.port or 80, timeout=15)
        conn.request("GET", "/api/task_tree")
        tree = json.loads(conn.getresponse().read().decode("utf-8"))
        conn.close()
        tasks = [t["key"] for g in tree["data"]["groups"] for t in g["tasks"]]
        print(f"  任务树里共 {len(tasks)} 个任务")
    print("=" * 72)
    print(f"  批量预缓存实验：单会话连续调 {len(tasks)} 次 get_config")
    print("=" * 72)
    try:
        b = batch_open(args.server, args.instance, tasks)
        print(f"  握手 + initialize : {b['_initialize']:8.1f} ms   （只付一次）")
        print(f"  {int(b['_n'])} 次 get_config    : {b['_calls_total']:8.1f} ms 合计"
              f"   中位 {b['_calls_median']:.0f} ms   最慢 {b['_calls_max']:.0f} ms")
        print(f"  平均每次          : {b['_calls_total'] / max(b['_n'], 1):8.1f} ms")
        print()
        naive = grand * len(tasks)
        batched = b["_initialize"] + b["_calls_total"]
        print(f"  逐个开新会话（现状）: {naive:8.1f} ms  = {naive / 1000:.1f} s")
        print(f"  单会话批量          : {batched:8.1f} ms  = {batched / 1000:.1f} s")
        print(f"  → 省下 {naive / max(batched, 1):.1f} 倍")
    except Exception as exc:
        print(f"  [X] 批量实验失败：{exc}")
        print("      → 服务端可能不支持单会话内多次 tools/call，预缓存只能退化成逐个拉")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
