#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成一份 config_cache.json，用来在**没有 AzurPilot** 的情况下验证 App 的缓存渲染。

为什么需要这个：
    App 的配置页正常来源是「桥的 schema + MCP 的 get_config」。要验证
    「缓存命中时能秒开」这条路径，最干净的办法是让**网络那一侧彻底不可用**，
    然后看页面还能不能渲染出来。可 AzurPilot 一关，get_config 就没了，
    连一份可缓存的条目都造不出来。

    所以这个脚本直接照 App 的磁盘格式手写一份缓存：
      schema  = 从**正在运行的数据桥**抓的真实 /api/task_schema（真数据）
      values  = 用 schema 里每个参数的 default 拼出来（等于「全默认配置」）
    对「页面能不能渲染」这件事，这两样足够了 —— 渲染只看结构和值，不看值从哪来。

用法：
    python bridge\\make_fake_cache.py --out D:\\Temp\\config_cache.json
（地址默认走 127.0.0.1，也可以用 AZURPILOT_SERVER / AZURREM_BRIDGE 环境变量或 --server/--bridge 指定）
"""

from __future__ import annotations

import argparse
import http.client
import json
import os
import time
from urllib.parse import urlparse


def http_json(base: str, path: str) -> dict:
    p = urlparse(base)
    conn = http.client.HTTPConnection(p.hostname, p.port or 80, timeout=20)
    conn.request("GET", path)
    resp = conn.getresponse()
    body = resp.read()
    conn.close()
    if resp.status != 200:
        raise RuntimeError(f"{path} -> HTTP {resp.status}")
    return json.loads(body.decode("utf-8"))


def values_from_schema(schema: dict) -> dict:
    """把 schema 里每个参数的 default 摊成一份「当前配置」。

    形状必须和 get_config 返回的一致：{ 分组键: { 参数键: 值 } }。
    """
    out: dict[str, dict] = {}
    for group in schema.get("groups") or []:
        g = {}
        for arg in group.get("args") or []:
            g[arg["key"]] = arg.get("default")
        if g:
            out[group["key"]] = g
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--server", default=os.environ.get("AZURPILOT_SERVER", "http://127.0.0.1:25548"))
    ap.add_argument("--bridge", default=os.environ.get("AZURREM_BRIDGE", "http://127.0.0.1:25550"))
    ap.add_argument("--instance", default="alas")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    tree = http_json(args.bridge, "/api/task_tree")["data"]
    tasks = [t["key"] for g in tree["groups"] for t in g["tasks"]]
    print(f"任务树：{len(tasks)} 个任务")

    server = args.server.strip().rstrip("/")
    now = int(time.time() * 1000)
    entries: dict[str, dict] = {}
    skipped: list[str] = []

    for i, task in enumerate(tasks, 1):
        try:
            data = http_json(args.bridge, f"/api/task_schema?task={task}")["data"]
        except Exception as exc:
            skipped.append(f"{task}({exc})")
            continue

        values = values_from_schema(data)
        if not values:
            # 没有可编辑参数的任务（纯展示型）—— App 端渲染出来是「没有可编辑的配置」，
            # 缓存它没有意义，跳过
            skipped.append(f"{task}(无可编辑参数)")
            continue

        key = f"{server}|{args.instance.strip()}|{task}"
        entries[key] = {
            "t": now,
            # s / g 存的都是**字符串**，因为 App 侧 ConfigCache 用的是
            # JSONObject.getString —— 存成嵌套对象会读不出来
            "s": json.dumps(data, ensure_ascii=False),
            "g": json.dumps(values, ensure_ascii=False),
        }
        if i % 20 == 0:
            print(f"  ... {i}/{len(tasks)}")

    payload = {"v": 1, "e": entries}
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False)

    print(f"写出 {len(entries)} 条 -> {args.out}")
    if skipped:
        print(f"跳过 {len(skipped)} 条：{', '.join(skipped[:10])}"
              + (" ..." if len(skipped) > 10 else ""))
    total = sum(len(v["s"]) + len(v["g"]) for v in entries.values())
    print(f"内容合计约 {total / 1024:.0f} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
