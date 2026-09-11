#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""打包后实测：起 dist\\AzurRemBridge.exe，逐个打 8 个接口，最后把进程杀掉。

为什么要有这个脚本：
    「exe 能起来」不等于「接口还能用」——移植经验统计、改根目录查找、改启动流程
    都可能悄悄弄坏某个接口。每次重新打包后跑一遍这个脚本，8 个接口必须全部
    200 + success:true。

用法：
    python bridge\\smoke_test_exe.py                 # 默认端口 25561
    python bridge\\smoke_test_exe.py --port 25562 --root D:\\Tools\\AzurPilot
    python bridge\\smoke_test_exe.py --no-root       # 故意不传 --root，测自动查找

默认用 25561：25550 上可能正跑着另一个数据桥，别去打扰它。
只读接口，不写 AzurPilot；结束时一定 kill 掉自己起的进程。
"""

from __future__ import annotations

import argparse
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_EXE = HERE.parent / "dist" / "AzurRemBridge.exe"

ENDPOINTS = [
    "/api/health",
    "/api/task_tree",
    "/api/resource_history",
    "/api/meow_hazard",
    "/api/overview_tasks",
    "/api/ship_exp",
    # 委托收益：要 instance + period 两个参数，默认值分别是 alas / month
    "/api/commission_income?instance=alas&period=month",
    "/api/logs/tail",
]


def _url(base: str, path: str) -> str:
    """拼 URL：path 里可能已经带了 ?query（比如 /api/commission_income?...

    不能无脑再拼一个 "?instance=alas" —— 那会变成
    `...?instance=alas&period=month?instance=alas`，period 变成 "month?instance=alas"。
    """
    joiner = "&" if "?" in path else "?"
    return f"{base}{path}{joiner}instance=alas"


def _summarize(path: str, payload: dict) -> str:
    """一句话概括返回内容，方便一眼看出"接口通了但数据是空的"这种问题。

    故意写得很"傻"：不假设每个接口的字段名，只把顶层键和它们的规模列出来，
    这样接口改了字段这里也不会跟着炸（第一版就是假设字段名，结果自己抛异常）。
    """
    # /api/health 是平铺的（没有 data 包一层），其它接口都是 {success, data}
    if path == "/api/health":
        routes = payload.get("routes") or []
        return f"service={payload.get('service')} root={payload.get('root')} routes={len(routes)}"

    data = payload.get("data")
    if not isinstance(data, dict):
        text = json.dumps(data, ensure_ascii=False)
        return text[:180]

    parts = []
    for key in sorted(data):
        value = data[key]
        if isinstance(value, list):
            parts.append(f"{key}[{len(value)}]")
        elif isinstance(value, dict):
            parts.append(f"{key}{{{len(value)}}}")
        elif isinstance(value, str):
            parts.append(f"{key}={value[:24]!r}")
        else:
            parts.append(f"{key}={value}")
    return " ".join(parts)[:200]


def _kill_tree(pid: int) -> None:
    """杀掉整棵进程树，而不只是父进程。

    --onefile 的 exe 是两层：引导进程先解包，再跑真正的子进程。只 kill 父进程
    会留下一个孤儿继续占着端口（还会锁住 dist\\AzurRemBridge.exe，下次重打包
    报 WinError 5 拒绝访问）。所以统一用 taskkill /T。

    这里**故意不用** taskkill /IM AzurRemBridge.exe：那会顺手杀掉用户自己正在
    跑的数据桥。杀不干净时下面只报告端口占用情况，让人自己判断。
    """
    try:
        subprocess.run(
            ["taskkill", "/F", "/T", "/PID", str(pid)],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            timeout=15, check=False,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    except (OSError, subprocess.SubprocessError):
        pass


def _port_busy(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(1.0)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def _wait_port_free(port: int, seconds: float = 10.0) -> bool:
    """等端口真的释放：kill 之后子进程还要一小会儿才退干净。"""
    deadline = time.time() + seconds
    while time.time() < deadline:
        if not _port_busy(port):
            return True
        time.sleep(0.5)
    return not _port_busy(port)


def _bridge_pids() -> set:
    """当前所有 AzurRemBridge.exe 的 PID（tasklist CSV，纯文本解析）。"""
    try:
        done = subprocess.run(
            ["tasklist", "/FI", "IMAGENAME eq AzurRemBridge.exe", "/FO", "CSV", "/NH"],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15, check=False,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    except (OSError, subprocess.SubprocessError):
        return set()
    pids = set()
    for line in (done.stdout or b"").decode("utf-8", errors="replace").splitlines():
        parts = [item.strip().strip('"') for item in line.split('","')]
        if len(parts) >= 2 and parts[1].isdigit():
            pids.add(int(parts[1]))
    return pids


def _report_window(exe: Path) -> None:
    """--gui 模式：确认真的有一个可见窗口（--noconsole 之后没有控制台可看）。"""
    try:
        done = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command",
             "Get-Process AzurRemBridge -ErrorAction SilentlyContinue | "
             "Where-Object { $_.MainWindowHandle -ne 0 } | "
             "Select-Object -First 1 -ExpandProperty MainWindowTitle"],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=20, check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        print(f"[!] 查窗口标题失败：{exc}")
        return
    title = (done.stdout or b"").decode("utf-8", errors="replace").strip()
    if title:
        print(f"[OK] 看到窗口了，标题 = {title!r}（说明不是控制台程序，是挂件窗口）")
    else:
        print("[!] 没查到带窗口的 AzurRemBridge 进程（可能窗口标题读取失败）")


def main() -> int:
    parser = argparse.ArgumentParser(description="AzurRemBridge.exe 接口冒烟测试")
    parser.add_argument("--exe", default=str(DEFAULT_EXE))
    parser.add_argument("--port", type=int, default=25561)
    parser.add_argument("--root", default=r"D:\Tools\AzurPilot")
    parser.add_argument("--no-root", action="store_true",
                        help="不传 --root，测自动查找（会用到 exe 旁的 azurpilot-root.txt）")
    parser.add_argument("--timeout", type=float, default=60.0, help="等待启动的秒数")
    parser.add_argument("--gui", action="store_true",
                        help="不传 --headless，真的开窗口（会短暂出现在桌面上），并检查窗口标题")
    parser.add_argument("--save-ship-exp", default="", help="把 /api/ship_exp 完整 JSON 存到这个文件")
    args = parser.parse_args()

    exe = Path(args.exe)
    if not exe.is_file():
        print(f"[X] 找不到 exe：{exe}（先跑 bridge\\build-exe.bat）")
        return 2

    cmd = [str(exe), "--port", str(args.port)]
    if not args.gui:
        # --noconsole 的 GUI exe：不加 --headless 就会弹窗口。
        # 默认用 headless 测接口，--gui 时才真的开窗口。
        cmd.append("--headless")
    if not args.no_root:
        cmd += ["--root", args.root]

    log_path = HERE / f"smoke_test_{args.port}.log"
    print(f"启动：{' '.join(cmd)}")
    print(f"输出重定向到：{log_path}")
    # 记下"我们起来之前就已经在跑的"同名进程，收尾时只清理多出来的那些，
    # 绝不误杀用户自己正在跑的数据桥
    pids_before = _bridge_pids()
    child_env = dict(os.environ, PYTHONIOENCODING="utf-8")  # 让日志文件是 UTF-8，好读
    with open(log_path, "w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(
            cmd, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, env=child_env
        )

    base = f"http://127.0.0.1:{args.port}"
    failures = []
    try:
        # 等 /api/health 真的响应：onefile 首次运行要先解包，别盲等固定秒数
        ready = False
        deadline = time.time() + args.timeout
        while time.time() < deadline:
            if proc.poll() is not None:
                break
            try:
                with urllib.request.urlopen(f"{base}/api/health", timeout=2) as resp:
                    if resp.status == 200:
                        ready = True
                        break
            except (urllib.error.URLError, OSError):
                time.sleep(0.4)

        if not ready:
            print(f"[X] 等了 {args.timeout:.0f}s 还没起来，进程退出码 {proc.poll()}")
            print("---- exe 输出 ----")
            print(log_path.read_text(encoding="utf-8", errors="replace"))
            return 1

        print(f"[OK] 已起来（{base}）\n")
        print(f"{'接口':<26} {'HTTP':<6} {'success':<8} 耗时      概要")
        print("-" * 130)

        for path in ENDPOINTS:
            url = _url(base, path)
            started = time.time()
            try:
                with urllib.request.urlopen(url, timeout=20) as resp:
                    code = resp.status
                    raw = resp.read().decode("utf-8", errors="replace")
            except urllib.error.HTTPError as exc:
                code = exc.code
                raw = exc.read().decode("utf-8", errors="replace")
            except (urllib.error.URLError, OSError) as exc:
                code = 0
                raw = json.dumps({"success": False, "error": str(exc)}, ensure_ascii=False)
            elapsed = (time.time() - started) * 1000

            try:
                payload = json.loads(raw)
            except ValueError:
                payload = {"success": False, "error": "返回不是 JSON"}

            ok = code == 200 and payload.get("success") is True
            if not ok:
                failures.append((path, code, payload.get("error")))
            print(
                f"{path:<26} {code:<6} {str(payload.get('success')):<8} "
                f"{elapsed:7.0f}ms {_summarize(path, payload)}"
            )

            if path == "/api/ship_exp":
                print("\n---- /api/ship_exp 完整返回 ----")
                print(json.dumps(payload, ensure_ascii=False, indent=2))
                print("---- 完整返回结束 ----\n")
                if args.save_ship_exp:
                    Path(args.save_ship_exp).write_text(
                        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
                    )

            if path.startswith("/api/commission_income"):
                # 原始 rows 直接打出来：total / count / avg / color / label 五个字段
                # 必须都在，而且要能和 PC 上的数字对上（只看概览行看不出这个）
                rows = ((payload.get("data") or {}).get("rows")) or []
                print("\n---- /api/commission_income rows ----")
                for row in rows:
                    print("   " + json.dumps(row, ensure_ascii=False))
                data = payload.get("data") or {}
                print(f"   totalCommissions={data.get('totalCommissions')} "
                      f"monthEntries={data.get('monthEntries')} "
                      f"available={data.get('available')} recent={len(data.get('recent') or [])}")
                print("---- rows 结束 ----\n")

        # 不带任何参数的默认值：instance=DEFAULT_INSTANCE、period="month"
        # （App 万一忘了带参数，这里也不能 500）
        bare = f"{base}/api/commission_income"
        try:
            with urllib.request.urlopen(bare, timeout=20) as resp:
                code = resp.status
                payload = json.loads(resp.read().decode("utf-8", errors="replace"))
        except Exception as exc:
            code, payload = 0, {"success": False, "error": str(exc)}
        ok = code == 200 and payload.get("success") is True
        if not ok:
            failures.append((bare, code, payload.get("error")))
        data = payload.get("data") or {}
        print(f"{'/api/commission_income(无参)':<26} {code:<6} {str(payload.get('success')):<8} "
              f"{'':7} instance={data.get('instance')!r} period={data.get('period')!r} "
              f"rows={len(data.get('rows') or [])}")

        print()
        if args.gui:
            _report_window(exe)

        log_file = exe.parent / "AzurRemBridge.log"
        if log_file.is_file():
            print(f"---- exe 自己的日志尾巴（{log_file}）----")
            tail = log_file.read_text(encoding="utf-8", errors="replace").splitlines()[-12:]
            for line in tail:
                print("   " + line)
            print("---- 日志结束 ----")

        if failures:
            print("[FAIL] 有接口没通过：")
            for path, code, error in failures:
                print(f"   {path}  HTTP {code}  {error}")
            return 1
        print("[PASS] 8 个接口全部 200 + success:true（含 /api/commission_income 无参默认值）")
        return 0
    finally:
        if proc.poll() is None:
            _kill_tree(proc.pid)
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                pass
            print(f"[已结束] 端口 {args.port} 上的测试进程树已杀掉（exit={proc.returncode}）")
        else:
            print(f"[已结束] 进程已退出（exit={proc.returncode}）")

        released = _wait_port_free(args.port, 10.0)
        if not released:
            # 兜底：只杀本次新出现的 AzurRemBridge 进程（pids_before 里的不动）
            leftovers = _bridge_pids() - pids_before
            if leftovers:
                print(f"[!] 端口 {args.port} 仍被占用，清理本次残留的进程 {sorted(leftovers)}")
                for pid in sorted(leftovers):
                    _kill_tree(pid)
                released = _wait_port_free(args.port, 10.0)

        if released:
            print(f"[OK] 端口 {args.port} 已释放")
        else:
            print(f"[X] 端口 {args.port} 还在监听，可能残留了子进程："
                  f"用 `tasklist | findstr AzurRemBridge` 查一下")
        print(f"（exe 控制台输出留在 {log_path}，里面能看到「数据桥已启动」和手机地址）")


if __name__ == "__main__":
    raise SystemExit(main())
