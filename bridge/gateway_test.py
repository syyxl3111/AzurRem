#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""网关自测：鉴权、状态页、白名单、MCP 反代。

为什么单独一个文件：`gui_test.py` 管窗口，`smoke_test_exe.py` 管打包出来的 exe。
这一份管**网关这一层的行为** —— 而它恰好是唯一没法靠肉眼看出来的部分
（浏览器打开只看得到一个绿点，App 那边只会说"连不上"）。

覆盖：
  1. 状态页 `/` 不鉴权，且能正确区分"AP 没起来"和"密码不对"
  2. 其余出口一律要凭据；三种传法（Bearer / X-API-Key / ?key=）都认
  3. 9 条桥接口带凭据全通
  4. **MCP 反代端到端**：SSE 握手 → initialize → 调工具（需要 AzurPilot 在跑）
  5. `/api/ap_timeline`、`/api/cl1_stats` 反代（同上）
  6. 白名单之外的路径**不会**被转到 AzurPilot 去

用法：
    .venv\\Scripts\\python.exe bridge\\gateway_test.py
    .venv\\Scripts\\python.exe bridge\\gateway_test.py --root D:\\Tools\\AzurPilot
退出码 0 = 全部通过。AzurPilot 没在跑时，第 4/5 组会标 SKIP 而不是失败。
"""

from __future__ import annotations

import importlib.util
import json
import socket
import sys
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
PORT = 25566          # 不碰 25550（那儿可能跑着真的网关）

TEST_KEY = "gateway-test-key-abcdefghijklmnop"

PASSED: list = []
FAILED: list = []
SKIPPED: list = []


def check(label: str, ok: bool, extra=None) -> bool:
    mark = "[OK]" if ok else "[X ]"
    suffix = f"  → {extra}" if extra is not None else ""
    print(f"  {mark} {label}{suffix}")
    (PASSED if ok else FAILED).append(label)
    return ok


def skip(label: str, why: str) -> None:
    print(f"  [--] {label}（跳过：{why}）")
    SKIPPED.append(label)


def load_module():
    spec = importlib.util.spec_from_file_location("mobile_bridge", HERE / "mobile_bridge.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules["mobile_bridge"] = module
    spec.loader.exec_module(module)
    return module


def http(url: str, method: str = "GET", body: bytes = None, headers: dict = None):
    """返回 (status, bytes, headers)。4xx/5xx 不当异常抛，测试要自己看状态码。"""
    request = urllib.request.Request(url, data=body, method=method)
    for name, value in (headers or {}).items():
        request.add_header(name, value)
    try:
        with urllib.request.urlopen(request, timeout=10) as resp:
            return resp.status, resp.read(), dict(resp.headers)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(), dict(exc.headers or {})
    except Exception as exc:
        return -1, f"{type(exc).__name__}: {exc}".encode("utf-8"), {}


def ap_is_running(port: int) -> bool:
    probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    probe.settimeout(1.5)
    try:
        return probe.connect_ex(("127.0.0.1", port)) == 0
    finally:
        probe.close()


def kotlin_comment_traps() -> list:
    """扫 Kotlin 源码里「注释中写通配路径」的写法。

    ★ 为什么值得一条自动检查：**Kotlin 的块注释可以嵌套**。在 KDoc 里写
      「斜杠 + 星号」（比如描述某个 api 前缀）会开一个内层注释，把**后面
      整个文件**吞掉。编译器只在文件末尾报一句 "Unclosed comment"，
      往回找极费劲 —— 这个坑在 McpClient.kt 里已经踩过两次。
      与其第三次靠运气，不如让测试盯着。

    只查"注释行"里的星号（行首是 * 或 //），字符串字面量里的不算 ——
    那不进注释解析器，无害。
    """
    root = HERE.parent / "AzurPilotMobile" / "app" / "src"
    if not root.is_dir():
        return []
    hits = []
    for path in sorted(root.rglob("*.kt")):
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except OSError:
            continue
        for number, line in enumerate(lines, 1):
            stripped = line.strip()
            is_comment_line = stripped.startswith("*") or stripped.startswith("//")
            if is_comment_line and "/*" in stripped:
                hits.append(f"{path.relative_to(root)}:{number}  {stripped[:70]}")
    return hits


def mcp_roundtrip(module, base: str, key: str) -> tuple:
    """走网关反代跑一轮完整的 MCP：SSE 握手 → initialize → get_status。

    这是**整套改动里最该被验证的一条**：App 的操控能力全压在这条链路上，
    而它中间多了一跳（网关注入密码），任何一环没接好都表现成"App 连不上"。
    """
    messages: list = []
    endpoint: dict = {}

    request = urllib.request.Request(
        f"{base}/mcp/sse",
        headers={"Accept": "text/event-stream", "Authorization": f"Bearer {key}"},
    )
    try:
        stream = urllib.request.urlopen(request, timeout=20)
    except Exception as exc:
        return False, f"SSE 连不上：{type(exc).__name__}: {exc}"

    def reader(resp):
        event = None
        try:
            for raw in resp:
                line = raw.decode("utf-8", "replace").rstrip("\r\n")
                if line.startswith("event:"):
                    event = line[6:].strip()
                elif line.startswith("data:"):
                    data = line[5:].strip()
                    if event == "endpoint":
                        endpoint["path"] = data
                    elif event == "message":
                        try:
                            messages.append(json.loads(data))
                        except Exception:
                            pass
        except Exception:
            pass

    threading.Thread(target=reader, args=(stream,), daemon=True).start()
    for _ in range(200):
        if "path" in endpoint:
            break
        time.sleep(0.05)

    if "path" not in endpoint:
        return False, "网关没把 SSE 的 endpoint 帧转发过来"

    post_url = base + endpoint["path"]

    def post(obj):
        payload = json.dumps(obj).encode("utf-8")
        # ★ 每个请求都带凭据。直连 AzurPilot 时只有 SSE 那一次需要（之后靠
        #   session_id 认人），但**网关对自己的每个出口都验**，所以客户端
        #   统一带上才两边都能用。少了它，SSE 握得上、initialize 立刻 401 ——
        #   这条正是本测试第一次跑时抓到的真问题。
        return http(post_url, method="POST", body=payload,
                    headers={"Content-Type": "application/json",
                             "Authorization": f"Bearer {key}"})

    status, _body, _h = post({
        "jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {"protocolVersion": "2024-11-05", "capabilities": {},
                   "clientInfo": {"name": "gateway_test", "version": "1"}},
    })
    if status not in (200, 202):
        return False, f"initialize 失败：HTTP {status}"

    # 注意 POST **也带密码**：网关不跟踪会话，它对自己的每个出口都验凭据。
    # （AzurPilot 自己那边只验 SSE、之后认 session_id，但网关不该学这一套 ——
    #   少一份状态就少一类能出错的地方。）
    post({"jsonrpc": "2.0", "method": "notifications/initialized"})
    post({"jsonrpc": "2.0", "id": 2, "method": "tools/call",
          "params": {"name": "get_status", "arguments": {}}})

    for _ in range(200):
        if any(m.get("id") == 2 for m in messages):
            break
        time.sleep(0.05)

    try:
        stream.close()
    except Exception:
        pass

    status_msg = next((m for m in messages if m.get("id") == 2), None)
    if status_msg is None:
        return False, f"没等到 get_status 的响应（收到 {len(messages)} 条 message 帧）"
    if status_msg.get("error"):
        return False, f"get_status 报错：{status_msg['error']}"
    try:
        text = status_msg["result"]["content"][0]["text"]
    except Exception:
        return False, "get_status 返回结构不对"
    return True, f"实例状态 = {text.strip()[:80]}"


def main() -> int:
    import argparse

    parser = argparse.ArgumentParser(description="AzurRem 网关自测")
    parser.add_argument("--root", default=r"D:\Tools\AzurPilot", help="AzurPilot 根目录")
    args = parser.parse_args()

    module = load_module()
    root = Path(args.root)
    if not root.is_dir():
        print(f"[X] 找不到 AzurPilot 根目录：{root}")
        return 2

    module.configure_root(root)
    module.GATEWAY_KEY = TEST_KEY

    server = module._bind_server(PORT)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    time.sleep(0.4)
    base = f"http://127.0.0.1:{PORT}"

    upstream_port, upstream_password = module.deploy_config()
    print(f"上游 AzurPilot：127.0.0.1:{upstream_port}"
          f"（{'已读到密码' if upstream_password else '没读到密码'}）")
    ap_up = ap_is_running(upstream_port)
    print(f"AzurPilot 在跑吗：{'是' if ap_up else '否'}")
    print()

    try:
        # ── 0. 源码静态检查 ──
        print("[0] Kotlin 注释里的「斜杠+星号」陷阱")
        traps = kotlin_comment_traps()
        check("没有注释会吞掉整个文件", not traps, traps[:3] if traps else None)

        # ── 1. 状态页 ──
        print("\n[1] 状态页 GET /（唯一不鉴权的出口）")
        status, body, headers = http(f"{base}/")
        text = body.decode("utf-8", "replace")
        check("不带凭据就能打开", status == 200, f"HTTP {status}")
        check("返回的是 HTML", "text/html" in (headers.get("Content-Type") or ""),
              headers.get("Content-Type"))
        check("页面提到了 App", "App" in text)
        check("页面不含任何资源数字", "石油" not in text and "钻石" not in text)
        if ap_up:
            check("AzurPilot 在跑 → 页面说已连接", "已连接" in text)
        else:
            check("AzurPilot 没跑 → 页面说没连上", "没连上" in text or "未连接" in text)

        # ── 2. 鉴权 ──
        print("\n[2] 鉴权")
        status, _b, _h = http(f"{base}/api/health")
        check("不带凭据 → 401", status == 401, f"HTTP {status}")
        status, _b, _h = http(f"{base}/api/health?key=wrong")
        check("密码写错 → 401", status == 401, f"HTTP {status}")
        status, _b, _h = http(f"{base}/api/health?key={TEST_KEY}")
        check("?key= 传法 → 200", status == 200, f"HTTP {status}")
        status, _b, _h = http(f"{base}/api/health",
                              headers={"Authorization": f"Bearer {TEST_KEY}"})
        check("Authorization: Bearer → 200", status == 200, f"HTTP {status}")
        status, _b, _h = http(f"{base}/api/health", headers={"X-API-Key": TEST_KEY})
        check("X-API-Key → 200", status == 200, f"HTTP {status}")
        status, _b, _h = http(f"{base}/api/health",
                              headers={"Authorization": "Bearer " + TEST_KEY.upper()})
        check("大小写不同 = 不同密码 → 401（没被规范化掉）", status == 401, f"HTTP {status}")

        # ── 3. 只读接口 ──
        print("\n[3] 9 条只读接口（带凭据）")
        for path in ("/api/task_tree", "/api/resource_history", "/api/meow_hazard",
                     "/api/overview_tasks", "/api/ship_exp", "/api/meow_stats",
                     "/api/logs/tail", "/api/task_schema?task=Main",
                     "/api/commission_income?period=month"):
            joiner = "&" if "?" in path else "?"
            status, body, _h = http(f"{base}{path}{joiner}instance=alas&key={TEST_KEY}")
            ok = status == 200 and json.loads(body).get("success") is True
            check(f"{path.split('?')[0]} → success", ok, f"HTTP {status}")

        # ── 4. MCP 反代（需要 AzurPilot 在跑） ──
        print("\n[4] MCP 反代端到端")
        if not ap_up:
            skip("MCP 全链路", f"AzurPilot 没在 127.0.0.1:{upstream_port} 上跑")
        elif not upstream_password:
            skip("MCP 全链路", "deploy.yaml 里没读到密码")
        else:
            ok, detail = mcp_roundtrip(module, base, TEST_KEY)
            check("SSE 握手 → initialize → get_status 全通", ok, detail)

            status, _b, _h = http(f"{base}/mcp/sse",
                                  headers={"Accept": "text/event-stream"})
            check("MCP 反代同样要凭据（网关不替外人开门）", status == 401, f"HTTP {status}")

        # ── 5. 两个统计接口的反代 ──
        print("\n[5] /api/ap_timeline 与 /api/cl1_stats 反代")
        if not ap_up:
            skip("统计接口反代", "AzurPilot 没在跑")
        else:
            for path in ("/api/ap_timeline", "/api/cl1_stats"):
                status, body, _h = http(f"{base}{path}?instance=alas&key={TEST_KEY}")
                ok = status == 200 and json.loads(body).get("success") is True
                check(f"{path} → 被转到 AzurPilot 且返回 success", ok, f"HTTP {status}")

        # ── 6. 白名单 ──
        print("\n[6] 白名单：不该被转出去的路径")
        status, _b, _h = http(f"{base}/api/launcher/startup?key={TEST_KEY}")
        check("/api/launcher/startup 不反代（那是能拉起进程的写口）", status == 404,
              f"HTTP {status}")
        status, _b, _h = http(f"{base}/api/deploy/settings?key={TEST_KEY}")
        check("/api/deploy/settings 不反代", status == 404, f"HTTP {status}")

        print("\n[7] 网关自己没有任何写接口")
        status, _b, _h = http(f"{base}/api/task_tree", method="POST", body=b"{}",
                              headers={"Content-Type": "application/json",
                                       "Authorization": f"Bearer {TEST_KEY}"})
        check("POST 一个只读路径 → 405", status == 405, f"HTTP {status}")
        status, _b, _h = http(f"{base}/mcp/sse", method="POST", body=b"{}",
                              headers={"Content-Type": "application/json",
                                       "Authorization": f"Bearer {TEST_KEY}"})
        check("POST /mcp/sse → 405（MCP 的写入口在 /mcp/mcp/messages）",
              status == 405, f"HTTP {status}")

    finally:
        server.shutdown()
        server.server_close()

    total = len(PASSED) + len(FAILED)
    print("\n" + "=" * 70)
    print(f"  合计 {total} 项，通过 {len(PASSED)}，失败 {len(FAILED)}，跳过 {len(SKIPPED)}")
    for label in FAILED:
        print(f"    [X] {label}")
    if FAILED:
        print("\n  [FAIL] 有项目没通过")
        return 1
    print("\n  [PASS] 网关自测全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
