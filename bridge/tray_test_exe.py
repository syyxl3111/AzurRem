#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""打包后实测**托盘**：起 dist\\AzurRemBridge.exe（真窗口），跨进程把蓝点按下去。

为什么 gui_test.py 不够：
    gui_test.py 是 import mobile_bridge.py 跑源码，验的是「逻辑对不对」。
    真正只有 exe 才会踩的坑它一个都碰不到，典型的就是
    `_resource_path("azurrem.ico")` —— 源码运行时 ico 在 bridge\\ 下，
    --onefile 运行时它在 sys._MEIPASS 里；路径没兜住的话图标会静默退回
    系统默认图标，源码测试照样全绿。

这个脚本所以全部走真 exe、跨进程：
    1. 起 exe（**不加 --headless**，真开窗口），等 /api/health 200；
    2. 用 FindWindowW 按标题找到它的挂件窗口；
    3. 用 SendInput 在**蓝点真实的屏幕坐标**上点一下（真的鼠标事件）；
    4. 检查窗口不可见 + 托盘里出现了一个 class 前缀为 AzurRemBridgeTrayWnd_ 的
       隐藏窗口（那就是我们挂上去的托盘图标宿主）；
    5. 检查收进托盘期间 7 个接口里最轻的 /api/health 仍然 200；
    6. 跨进程 PostMessage WM_LBUTTONUP 给那个隐藏窗口 = 左键单击托盘图标；
    7. 检查窗口回来了、还是无边框、尺寸没变；
    8. 结束时 taskkill /T 杀干净。

用法：
    python bridge\\tray_test_exe.py            # 默认端口 25565
    python bridge\\tray_test_exe.py --port 25566

会短暂弹窗（约 6 秒），不需要人看着。退出码 0 = 全部通过。
"""

from __future__ import annotations

import argparse
import ctypes
import json
import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from ctypes import wintypes
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

HERE = Path(__file__).resolve().parent
DEFAULT_EXE = HERE.parent / "dist" / "AzurRemBridge.exe"
WINDOW_TITLE = "AzurRem 网关"
TRAY_CLASS_PREFIX = "AzurRemBridgeTrayWnd_"


def read_gateway_key(exe_path: Path, timeout: float = 20.0) -> str:
    """网关密码写在 exe 旁边（azurrem-gateway.key）。

    网关 2.0 起除状态页外的每个出口都要凭据，所以这个脚本里所有
    /api/health 都得带上它。首次运行才生成，得像等端口那样轮询一下。
    """
    key_file = exe_path.parent / "azurrem-gateway.key"
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            text = key_file.read_text(encoding="utf-8").strip()
        except OSError:
            text = ""
        if text:
            return text
        time.sleep(0.3)
    return ""

# 蓝点在挂件窗口里的位置（跟 gui_test 量出来的 dot_row / TrafficButton 布局一致：
# dot_row 右对齐在 relx=1.0,x=-12，第一个 16px 的圆点画布内圆心 8px）
DOT_TRAY_OFFSET = (160, 16)

user32 = ctypes.WinDLL("user32", use_last_error=True)
user32.FindWindowW.restype = wintypes.HWND
user32.FindWindowW.argtypes = (wintypes.LPCWSTR, wintypes.LPCWSTR)
user32.IsWindowVisible.restype = wintypes.BOOL
user32.IsWindowVisible.argtypes = (wintypes.HWND,)
user32.GetWindowRect.restype = wintypes.BOOL
user32.GetWindowRect.argtypes = (wintypes.HWND, ctypes.POINTER(wintypes.RECT))
user32.GetClassNameW.restype = ctypes.c_int
user32.GetClassNameW.argtypes = (wintypes.HWND, wintypes.LPWSTR, ctypes.c_int)
user32.SetCursorPos.argtypes = (ctypes.c_int, ctypes.c_int)
user32.mouse_event.argtypes = (wintypes.DWORD, wintypes.DWORD, wintypes.DWORD,
                               wintypes.DWORD, ctypes.c_size_t)
user32.PostMessageW.restype = wintypes.BOOL
user32.PostMessageW.argtypes = (wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM)
user32.GetWindowLongW.restype = ctypes.c_long
user32.GetWindowLongW.argtypes = (wintypes.HWND, ctypes.c_int)

ENUMPROC = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
user32.EnumWindows.restype = wintypes.BOOL
user32.EnumWindows.argtypes = (ENUMPROC, wintypes.LPARAM)

GWL_STYLE = -16
WS_EX_TOOLWINDOW = 0x00000080  # 我们不关心，只用来读一下
WS_POPUP = 0x80000000
MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP = 0x0002, 0x0004
WM_LBUTTONUP = 0x0202
TRAY_CALLBACK_MESSAGE = 0x8000 + 1     # 必须跟 mobile_bridge.TRAY_CALLBACK_MESSAGE 一致
TRAY_UID = 1


def port_open(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(1.0)
        return sock.connect_ex(("127.0.0.1", port)) == 0


def find_tray_windows() -> list:
    """枚举所有 class 前缀是 AzurRemBridgeTrayWnd_ 的顶层窗口（托盘图标宿主）。

    ⚠️ 这个窗口**整个进程生命周期都在**（只加/撤图标，不反复建窗口，免得
    NIM_DELETE 还没生效就又 NIM_ADD 出双图标），所以「窗口还在」**不代表**
    「托盘图标还在」。图标在不在要用 icon_present() 问系统。
    """
    found = []

    def callback(hwnd, _lparam):
        buf = ctypes.create_unicode_buffer(256)
        user32.GetClassNameW(hwnd, buf, 256)
        if buf.value.startswith(TRAY_CLASS_PREFIX):
            found.append((hwnd, buf.value, bool(user32.IsWindowVisible(hwnd))))
        return True

    user32.EnumWindows(ENUMPROC(callback), 0)
    return found


class NOTIFYICONIDENTIFIER(ctypes.Structure):
    _fields_ = [("cbSize", wintypes.DWORD), ("hWnd", wintypes.HWND),
                ("uID", wintypes.UINT), ("guidItem", ctypes.c_byte * 16)]


shell32 = ctypes.WinDLL("shell32", use_last_error=True)
shell32.Shell_NotifyIconGetRect.restype = ctypes.c_long
shell32.Shell_NotifyIconGetRect.argtypes = (ctypes.POINTER(NOTIFYICONIDENTIFIER),
                                            ctypes.POINTER(wintypes.RECT))


def icon_present(hwnd, uid=1):
    """问系统：这个 (hwnd, uID) 的托盘图标现在在不在？

    Shell_NotifyIconGetRect（Win7+）是**唯一**能跨进程查「图标在不在」的官方接口：
    找到就返回 S_OK（0）并给出图标所在的矩形，没找到返回失败 HRESULT。
    这比「数隐藏窗口个数」靠谱得多 —— 窗口一直在，图标是按需加/撤的。
    """
    ident = NOTIFYICONIDENTIFIER()
    ident.cbSize = ctypes.sizeof(NOTIFYICONIDENTIFIER)
    ident.hWnd = hwnd
    ident.uID = uid
    rect = wintypes.RECT()
    hr = shell32.Shell_NotifyIconGetRect(ctypes.byref(ident), ctypes.byref(rect))
    return hr, (rect.left, rect.top, rect.right, rect.bottom)


def click_until_hidden(x, y, hwnd, tries=3):
    """在 (x,y) 上点左键，直到挂件窗口不可见。

    为什么要重试：第一次点到**不是前台窗口**的窗口时，Windows 会把它当成
    「激活窗口」那一下（WM_MOUSEACTIVATE），点击不会传到控件上。实测从控制台
    启动时会稳定吞掉第一次点击，所以这里最多点 3 次。
    """
    for attempt in range(tries):
        user32.SetCursorPos(x, y)
        time.sleep(0.3)
        user32.mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)
        time.sleep(0.08)
        user32.mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)
        time.sleep(1.3)
        if not user32.IsWindowVisible(hwnd):
            return attempt + 1
    return 0


def cursor_pos():
    """当前光标位置。测试会把它挪到蓝点上点，完事必须放回去。"""
    p = wintypes.POINT()
    if not user32.GetCursorPos(ctypes.byref(p)):
        return None
    return p.x, p.y


def restore_cursor(pos) -> None:
    """把光标放回原处。

    ⚠️ 不做这件事会**污染整台机器后续的测试**：本脚本用 SetCursorPos 把光标挪到
    蓝点坐标上点，点完就不管了。只要光标留在那儿，之后**任何**在默认位置
    （屏幕右上角）新建的挂件窗口都会正好出生在光标底下，被 Windows 投递一次
    左键 → 蓝点被按下 → 窗口一建好就收进托盘。

    表现极具迷惑性：gui_test 报 `max() iterable argument is empty`（窗口没被
    map，取不到任何可见子控件），而窗口挪到别处就一切正常 —— 看起来像产品的
    bug，其实是测试自己留下的光标。而且一旦光标停住，权限不足的进程
    （SetCursorPos 返回 False）就再也挪不动它，只能人工动一下鼠标。
    """
    if pos:
        user32.SetCursorPos(pos[0], pos[1])


def find_widget_window() -> int:
    return user32.FindWindowW(None, WINDOW_TITLE) or 0


def window_rect(hwnd):
    rect = wintypes.RECT()
    if not user32.GetWindowRect(hwnd, ctypes.byref(rect)):
        return None
    return rect.left, rect.top, rect.right, rect.bottom


def kill_tree(pid: int) -> None:
    try:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       timeout=15, check=False,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    except (OSError, subprocess.SubprocessError):
        pass


def main() -> int:
    parser = argparse.ArgumentParser(description="AzurRemBridge.exe 托盘实测")
    parser.add_argument("--exe", default=str(DEFAULT_EXE))
    parser.add_argument("--port", type=int, default=25565)
    parser.add_argument("--root", default=os.environ.get("AZURPILOT_ROOT", ""),
                        help="AzurPilot 根目录（默认取环境变量 AZURPILOT_ROOT；留空则让 exe 自动查找）")
    parser.add_argument("--timeout", type=float, default=60.0)
    args = parser.parse_args()

    exe = Path(args.exe)
    if not exe.is_file():
        print(f"[X] 找不到 exe：{exe}（先跑 bridge\\build-exe.bat）")
        return 2

    exe_name = exe.stem
    exe_name_full = exe.name
    checks = []

    def check(name, ok, extra=""):
        checks.append((name, bool(ok), extra))
        print(f"  [{'OK' if ok else 'X '}] {name}{('  → ' + str(extra)) if extra else ''}")

    print("=" * 70)
    print("  exe 托盘实测（跨进程点真按钮 + 点真托盘图标）")
    print("=" * 70)

    before = {hwnd for hwnd, _cls, _vis in find_tray_windows()}
    before_widget = find_widget_window()
    if before_widget:
        print(f"[!] 已经有一个标题为 {WINDOW_TITLE!r} 的窗口（hwnd={before_widget}），"
              f"下面认的会是**新起的**那一个")

    # --root 从环境变量 AZURPILOT_ROOT 来（代码里不写死本机路径）。
    # 没给就不传 --root，让 exe 走自己的自动查找。
    cmd = [str(exe), "--port", str(args.port)]
    if args.root:
        cmd += ["--root", args.root]
    else:
        print("[i] 没给 AzurPilot 目录（AZURPILOT_ROOT 或 --root），让 exe 自己自动查找")
    print(f"启动：{' '.join(cmd)}")
    log_path = HERE / f"tray_test_{args.port}.log"
    with open(log_path, "w", encoding="utf-8", errors="replace") as log:
        proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT,
                                stdin=subprocess.DEVNULL,
                                env=dict(os.environ, PYTHONIOENCODING="utf-8"))

    exit_code = 0
    saved_cursor = cursor_pos()      # 见 restore_cursor 的注释：用完全程要放回去
    try:
        base = f"http://127.0.0.1:{args.port}"
        gateway_key = read_gateway_key(exe, timeout=args.timeout)
        if not gateway_key:
            print("[X] 等不到 azurrem-gateway.key（网关没起来，或目录不可写）")
            print(log_path.read_text(encoding="utf-8", errors="replace"))
            return 1
        ready = False
        deadline = time.time() + args.timeout
        while time.time() < deadline:
            if proc.poll() is not None:
                break
            try:
                with urllib.request.urlopen(
                    f"{base}/api/health?key={gateway_key}", timeout=2
                ) as resp:
                    if resp.status == 200:
                        ready = True
                        break
            except (urllib.error.URLError, OSError):
                time.sleep(0.4)
        if not ready:
            print(f"[X] 等了 {args.timeout:.0f}s 还没起来，退出码 {proc.poll()}")
            print(log_path.read_text(encoding="utf-8", errors="replace"))
            return 1
        check(f"exe 起来了，{base}/api/health 200", True)

        # 1) 找到挂件窗口
        hwnd = 0
        deadline = time.time() + 15
        while time.time() < deadline and not hwnd:
            hwnd = find_widget_window()
            if hwnd == before_widget:
                hwnd = 0
            time.sleep(0.3)
        check(f"找到挂件窗口「{WINDOW_TITLE}」", bool(hwnd), f"hwnd={hwnd}")
        if not hwnd:
            return 1
        # 「找到」≠「已经 map」：FindWindowW 只比对标题，Tk 把窗口建出来但还没
        # 显示时也会命中，紧接着查 IsWindowVisible 会偶发为假（真实遇到过的 flake，
        # 下一项尺寸检查和点击都是过的）。所以这里等它有界地变可见。
        vis_deadline = time.time() + 10
        while time.time() < vis_deadline and not user32.IsWindowVisible(hwnd):
            time.sleep(0.2)
        check("挂件窗口是可见的", bool(user32.IsWindowVisible(hwnd)))
        rect = window_rect(hwnd)
        check("挂件窗口有合理的尺寸（240x126 那一档）",
              rect and (rect[2] - rect[0]) >= 240 and (rect[3] - rect[1]) >= 126, rect)

        # 2) 在蓝点的真实屏幕坐标上点一下（真的鼠标事件，跨进程）
        x = rect[0] + DOT_TRAY_OFFSET[0]
        y = rect[1] + DOT_TRAY_OFFSET[1]
        attempts = click_until_hidden(x, y, hwnd)
        print(f"  · 在屏幕 ({x},{y}) 上左键单击蓝点（第 {attempts} 次生效）")

        check("点蓝点后挂件窗口不可见了", not user32.IsWindowVisible(hwnd),
              f"IsWindowVisible={user32.IsWindowVisible(hwnd)}")

        trays = [item for item in find_tray_windows() if item[0] not in before]
        check("托盘里出现了挂件自己的隐藏窗口（托盘图标宿主）",
              len(trays) >= 1, trays)
        if trays:
            t_hwnd, t_cls, t_vis = trays[0]
            check("托盘宿主窗口的 class 名带进程号（多次启动不会撞类名）",
                  t_cls.startswith(TRAY_CLASS_PREFIX) and len(t_cls) > len(TRAY_CLASS_PREFIX),
                  t_cls)
            check("托盘宿主窗口自己不显示（隐藏的）", not t_vis)
        else:
            t_hwnd = 0

        # 2b) 问系统「图标真的在托盘里吗」—— 这是跨进程唯一的官方查法
        if t_hwnd:
            hr, box = icon_present(t_hwnd)
            check("系统确认托盘图标真的注册上了（Shell_NotifyIconGetRect = S_OK）",
                  hr == 0, f"hr=0x{hr & 0xFFFFFFFF:08X} rect={box}")

        # 3) 收进托盘期间接口必须照常
        ok_health = False
        try:
            with urllib.request.urlopen(
                f"{base}/api/health?key={gateway_key}", timeout=5
            ) as resp:
                ok_health = resp.status == 200 and json.load(resp).get("success") is True
        except Exception as exc:
            print(f"     /api/health 失败：{exc}")
        check("收进托盘期间 /api/health 仍然 200", ok_health)
        check("收进托盘期间端口仍然在监听", port_open(args.port))

        # 4) 跨进程给托盘窗口发「左键单击」 = 用户点托盘图标
        if t_hwnd:
            user32.PostMessageW(t_hwnd, TRAY_CALLBACK_MESSAGE, TRAY_UID, WM_LBUTTONUP)
            time.sleep(1.2)
            check("左键单击托盘图标 → 挂件窗口回来了",
                  bool(user32.IsWindowVisible(hwnd)))
            rect2 = window_rect(hwnd) or (0, 0, 0, 0)
            check("回来之后尺寸没变",
                  (rect2[2] - rect2[0], rect2[3] - rect2[1])
                  == (rect[2] - rect[0], rect[3] - rect[1]),
                  f"{(rect2[2] - rect2[0], rect2[3] - rect[1])} vs {(rect[2] - rect[0], rect[3] - rect[1])}")
            # GWL_STYLE 里没有 WS_CAPTION(0x00C00000) 才算真的还是无边框
            style = user32.GetWindowLongW(hwnd, GWL_STYLE) & 0xFFFFFFFF
            check("回来之后仍然无边框（style 里没有 WS_CAPTION）",
                  (style & 0x00C00000) == 0, f"style=0x{style:08X}")
            trays_after = [item for item in find_tray_windows() if item[0] not in before]
            check("窗口回来之后托盘宿主窗口还在（它整个进程生命周期都在，不该反复重建）",
                  bool(trays_after), trays_after)
            hr_after, _box_after = icon_present(t_hwnd)
            check("窗口回来之后托盘**图标**已撤掉（宿主还在，但图标没了）",
                  hr_after != 0, f"hr=0x{hr_after & 0xFFFFFFFF:08X}（0 才表示图标还在）")

        # 5) 走托盘菜单的「退出」：跨进程发 WM_COMMAND（和真菜单回投的是同一条）
        if t_hwnd:
            attempts = click_until_hidden(x, y, hwnd)
            hidden_again = not user32.IsWindowVisible(hwnd)
            check("再点一次蓝点还能再收进托盘（可反复）",
                  attempts > 0 and hidden_again,
                  f"点了 {attempts} 次，visible={bool(user32.IsWindowVisible(hwnd))}")
            if hidden_again:
                hr2, _box2 = icon_present(t_hwnd)
                check("第二次收进托盘后图标又回来了", hr2 == 0, f"hr=0x{hr2 & 0xFFFFFFFF:08X}")
                user32.PostMessageW(t_hwnd, 0x0111, 0xE101, 0)   # WM_COMMAND / TRAY_CMD_EXIT
                deadline = time.time() + 15
                gone = False
                while time.time() < deadline:
                    if proc.poll() is not None and not port_open(args.port):
                        gone = True
                        break
                    time.sleep(0.4)
                check("托盘菜单「退出」→ exe 进程退出且端口释放",
                      gone, f"exit={proc.poll()} port_open={port_open(args.port)}")
                check("托盘菜单「退出」→ 退出码是 0（不是崩溃 0xC0000409、也不是被强杀）",
                      proc.poll() == 0, f"exit={proc.poll()}")
                check("托盘菜单「退出」→ 托盘宿主窗口也没了",
                      not [item for item in find_tray_windows() if item[0] not in before])

        # 6) 顺带确认没有残留
        left = [item for item in find_tray_windows() if item[0] not in before]
        check("全程结束后没有留下任何托盘窗口", not left, left)

        log_file = exe.parent / "AzurRemBridge.log"
        if log_file.is_file():
            print(f"---- exe 日志尾巴（{log_file}）----")
            for line in log_file.read_text(encoding="utf-8", errors="replace").splitlines()[-10:]:
                print("   " + line)
            print("---- 日志结束 ----")

    finally:
        restore_cursor(saved_cursor)
        if proc.poll() is None:
            kill_tree(proc.pid)
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                pass
        deadline = time.time() + 10
        while time.time() < deadline and port_open(args.port):
            time.sleep(0.4)
        print(f"[已结束] 端口 {args.port} "
              f"{'已释放' if not port_open(args.port) else '**仍在监听**'}（exit={proc.returncode}）")

    failed = [name for name, ok, _ in checks if not ok]
    print()
    print(f"  合计 {len(checks)} 项，失败 {len(failed)} 项")
    for name in failed:
        print(f"    [X] {name}")
    print("=" * 70)
    print("  [PASS] exe 托盘实测全部通过" if not failed else "  [FAIL] 有项目没通过")
    print("=" * 70)
    exit_code = 1 if failed else 0
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
