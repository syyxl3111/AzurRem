#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""挂件窗口自测：四个圆点按钮、标题、地址、托盘、任务栏、关窗口停服务。

为什么要写这个：这是一个 --noconsole 的 GUI exe，界面没法用 curl 测，人也不一定
每次都愿意点一遍。这个脚本直接 import mobile_bridge.py，建出真窗口、**真的按**
四个按钮，然后检查几何尺寸 / 窗口状态 / 端口是否释放。

四个圆点现在的分工（改过一次，别按老印象看）：
    蓝 −  收进**系统托盘**（真调 Shell_NotifyIconW，会短暂出现在你的托盘里）
    绿 ⤢  展开 ↔ 收起（双向）
    黄 ▼  最小化到**任务栏**（iconify）
    红 ×  退出：停 httpd + 退进程

会短暂弹出窗口 + 托盘里短暂出现一个图标（合计约 5 秒）再自己关掉，不需要人看着。
用法：
    D:\\Tools\\Python\\python.exe bridge\\gui_test.py
退出码 0 = 全部通过。
"""

from __future__ import annotations

import importlib.util
import socket
import sys
import threading
import time
import tkinter as tk
from pathlib import Path

HERE = Path(__file__).resolve().parent
PORT = 25563            # 不碰 25550（那上面可能跑着真的桥）
TRAY_PORT = 25564       # 「托盘菜单退出」那条路单独用一个口，别跟上面互相干扰

WM_LBUTTONUP = 0x0202
WM_COMMAND = 0x0111

# ★ 输出必须强制 UTF-8。
#
# 自测里要打印四个圆点的悬停符号，其中「收进托盘」用的是 U+2212 MINUS SIGN（−），
# 不是 ASCII 的连字符。Windows 的 Python 在**管道/重定向**时 stdout 会退回
# 系统 ANSI 代码页（中文机器上是 GBK），GBK 里没有 U+2212 —— 于是
# `print` 直接抛 UnicodeEncodeError，**测试会在打印结果那一刻崩掉**，
# 报一个跟挂件毫无关系的错，看起来像功能坏了（实际这条用例刚跑过、是过的）。
#
# 这个坑在从 PowerShell 里管道调用时必现；在 UTF-8 控制台里不会。
# 所以不指望调用方，自己把三条流都掰成 UTF-8。
for _stream in (sys.stdout, sys.stderr):
    try:
        _stream.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def _load_bridge():
    spec = importlib.util.spec_from_file_location("bridge_under_test", HERE / "mobile_bridge.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules["bridge_under_test"] = module
    spec.loader.exec_module(module)
    return module


def _port_open(port: int) -> bool:
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(1.0)
    try:
        return sock.connect_ex(("127.0.0.1", port)) == 0
    finally:
        sock.close()


def _window_region(root_widget, width, height, radius=16):
    """读回窗口当前的真实裁剪区域，判断圆角还在不在。

    这是唯一能**真的**验证「从托盘回来之后圆角还在」的办法：光看
    overrideredirect() 只能说明无边框标志还在，说明不了 SetWindowRgn 还在
    （overrideredirect 和 SetWindowRgn 是两码事，后者会被 restore 弄丢）。

    返回 (region_kind, same_as_round_rect)：
      region_kind    0=没有区域（方角） 2=SIMPLEREGION（矩形） 3=COMPLEXREGION（圆角）
      相同与否       拿 CreateRoundRectRgn 造一个期望区域跟它 EqualRgn 比
    """
    import ctypes
    user32 = ctypes.windll.user32
    gdi32 = ctypes.windll.gdi32
    root_widget.update_idletasks()
    hwnd = user32.GetParent(root_widget.winfo_id()) or root_widget.winfo_id()
    got = gdi32.CreateRectRgn(0, 0, 0, 0)
    expected = gdi32.CreateRoundRectRgn(0, 0, width + 1, height + 1, radius, radius)
    try:
        kind = user32.GetWindowRgn(hwnd, got)
        return kind, bool(gdi32.EqualRgn(got, expected))
    finally:
        gdi32.DeleteObject(got)
        gdi32.DeleteObject(expected)


def _send_tray_click(module, tray, message, root_widget, seconds=1.2):
    """往托盘的隐藏窗口发一条托盘回调消息 —— 等价于真的用鼠标点托盘图标。

    走的是和 explorer.exe 完全相同的入口（WNDPROC 收到 TRAY_CALLBACK_MESSAGE），
    只是把「谁来发」从系统换成了测试脚本，所以不需要人去点。

    ⚠️ 发完必须让 Tk 的事件循环真的转起来：托盘动作是**延迟派发**的
    （WNDPROC 只记一个字符串，真正干活在 tk 的 after(40) 轮询 _drain 里）。
    原因见 mobile_bridge.TrayIcon._handle_message 的注释 —— 在 WNDPROC 里直接调
    tkinter 会把主线程的线程状态搞坏，Tk_MainLoop 返回时 PyEval_RestoreThread
    拿到 NULL 直接 abort（实测退出码 0xC0000409）。所以这里不能只 time.sleep()。
    """
    user32, _kernel32, _shell32 = module._win32()
    user32.SendMessageW(tray.hwnd, module.TRAY_CALLBACK_MESSAGE, tray.UID, message)
    pump_tk(root_widget, seconds)


def pump_tk(root_widget, seconds=1.0):
    """把 Tk 的事件循环真的转起来等 seconds 秒（after 回调才会跑）。"""
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            root_widget.update()
        except tk.TclError:
            return
        time.sleep(0.02)


def main() -> int:
    module = _load_bridge()
    checks = []

    def check(name, ok, extra=""):
        checks.append((name, bool(ok), extra))
        print(f"  [{'OK' if ok else 'X '}] {name}{('  → ' + str(extra)) if extra else ''}")

    print("=" * 70)
    print("  挂件窗口自测")
    print("=" * 70)

    # 真起一个 HTTP 服务，才能验证「关窗口 = 停桥」和「收进托盘时接口照常」
    server = module._bind_server(PORT)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    time.sleep(0.3)
    check(f"HTTP 服务已在 {PORT} 上跑起来", _port_open(PORT))

    ip = (module.get_lan_ipv4_addresses() or ["127.0.0.1"])[0]
    # 服务端读的是模块级的 ROOT，测试里也要先指到真目录，
    # 否则 /api/health 报的 root 会是默认值（脚本自己所在的 bridge\）
    module.configure_root(Path(r"D:\Tools\AzurPilot"))
    state = {
        "ok": True, "title": "数据桥已启动",
        "detail": "手机端「设置 -> 数据桥地址」填这个（可选中复制）：",
        "root": Path(r"D:\Tools\AzurPilot"), "root_source": "测试",
        "port": PORT, "url": f"http://{ip}:{PORT}",
        "server": server, "headless": False, "launch_ap": False,
        "ap_status": "已关闭「顺带启动 AzurPilot」",
    }

    root = tk.Tk()
    window = module.BridgeWindow(root, state)
    root.update()
    time.sleep(0.2)
    root.update()

    def visible_rows():
        rows = []
        for name, wid in (('title', window.lbl_title), ('entry', window.entry_url),
                          ('btn_start_ap', window.btn_start_ap), ('btn_pick', window.btn_pick_root),
                          ('hint', window.lbl_hint), ('detail', window.lbl_detail),
                          ('root', window.lbl_root), ('stats', window.lbl_stats),
                          ('chk', window.chk_ap), ('footer', window.lbl_footer)):
            if wid.winfo_ismapped():
                rows.append((name, wid.winfo_x(), wid.winfo_x() + wid.winfo_width(),
                             wid.winfo_y(), wid.winfo_y() + wid.winfo_height()))
        return sorted(rows, key=lambda row: (row[3], row[1]))

    def check_layout(label):
        """行不能互相压、也不能超出窗口 —— 上一版写死 y 坐标就是这么翻车的。

        注意同一行的两个控件（启动按钮 / 文件夹按钮）在 y 上必然重叠，
        所以只有 x 区间也相交才算真重叠。
        """
        rows = visible_rows()
        overlaps = []
        for i, a in enumerate(rows):
            for b in rows[i + 1:]:
                if a[4] > b[3] and a[2] > b[1] and b[2] > a[1]:
                    overlaps.append((a[0], b[0], f"y {a[3]}-{a[4]} vs {b[3]}-{b[4]}"))
        check(f"{label}：行与行不重叠", not overlaps, overlaps)
        bottom = max(row[4] for row in rows)
        check(f"{label}：内容底边 {bottom} ≤ 窗口高 {root.winfo_height()}",
              bottom <= root.winfo_height())
        return rows

    # 1) 初始状态
    check("默认是紧凑尺寸 240x126+",
          root.geometry().startswith("240x") and root.winfo_height() >= 126, root.geometry())
    check("标题是「数据桥已启动」", window.lbl_title.cget("text") == "数据桥已启动")
    check("标题是黑色（正常态）", window.lbl_title.cget("fg") == module.TEXT)
    check("地址是 readonly Entry（可选中复制）", window.entry_url.cget("state") == "readonly")
    check("地址内容正确", window.entry_url.get() == f"http://{ip}:{PORT}", window.entry_url.get())
    check("紧凑模式下不显示细节行", not window.lbl_root.winfo_ismapped())
    check("第二行【启动 AzurPilot】在紧凑模式下可见", window.btn_start_ap.winfo_ismapped())
    check("第二行文件夹按钮在紧凑模式下可见", window.btn_pick_root.winfo_ismapped())
    check("第三行提示文字在紧凑模式下可见", window.lbl_hint.winfo_ismapped())
    compact_rows = check_layout("紧凑")

    # 第二行不能被切掉：控件的下边缘必须在窗口高度以内
    win_h = root.winfo_height()
    bottom = window.lbl_hint.winfo_y() + window.lbl_hint.winfo_height()
    check(f"紧凑窗口里第二/三行没有被切掉（底边 {bottom} <= {win_h}）", bottom <= win_h)

    # 2) 圆点必须在**右上角**，从左到右 托盘/展开/最小化/×
    row_right = window.dot_row.winfo_x() + window.dot_row.winfo_width()
    row_top = window.dot_row.winfo_y()
    check(f"圆点靠右上角（右边缘 {row_right} ≈ 窗口宽 240，顶边 {row_top}）",
          row_right >= 240 - 16 and row_top <= 16, f"x+w={row_right}, y={row_top}")
    order = [b.winfo_x() for b in (window.btn_tray, window.btn_expand,
                                   window.btn_minimize, window.btn_close)]
    check("四个圆点从左到右顺序正确（托盘/展开/最小化/×）",
          order == sorted(order) and len(set(order)) == 4, order)
    colors = [window.btn_tray.itemcget(1, "fill"), window.btn_expand.itemcget(1, "fill"),
              window.btn_minimize.itemcget(1, "fill"), window.btn_close.itemcget(1, "fill")]
    check("颜色是 蓝/绿/黄/红",
          colors == [module.DOT_BLUE, module.DOT_GREEN, module.DOT_YELLOW, module.DOT_RED], colors)
    gap = (window.btn_expand.winfo_x() + 2) - (window.btn_tray.winfo_x() + 14)
    check(f"圆点直径 12px、间距 8px（实测间距 {gap}）", gap == 8, gap)

    # 3) 圆点按钮的悬停符号
    for button, symbol in (
        (window.btn_tray, "−"), (window.btn_expand, "⤢"),
        (window.btn_minimize, "▼"), (window.btn_close, "×"),
    ):
        button._on_enter()
        root.update()
        shown = button._glyph is not None and button.itemcget(button._glyph, "text") == symbol
        button._on_leave()
        root.update()
        hidden = button._glyph is None
        check(f"悬停显示符号 {symbol}，离开后消失", shown and hidden)

    # 3b) 蓝点不再管尺寸了，尺寸由**绿点**双向切换
    check("托盘可用（能在本机建托盘图标）", window.tray.available, window.tray.last_error)

    window.btn_expand.invoke()
    root.update()
    time.sleep(0.2)
    root.update()
    check("点【绿点】→ 展开 340x210+",
          root.geometry().startswith("340x") and root.winfo_height() >= 200,
          root.geometry())
    check("展开后显示 AzurPilot 路径行", window.lbl_root.winfo_ismapped())
    check("展开后显示请求计数行", window.lbl_stats.winfo_ismapped())
    check("展开后显示自动拉起 AzurPilot 勾选框", window.chk_ap.winfo_ismapped())
    check_layout("展开")
    check("展开后窗口完全在屏幕内（宽度变大后不会飘出右边缘）",
          root.winfo_x() + root.winfo_width() <= root.winfo_screenwidth()
          and root.winfo_y() + root.winfo_height() <= root.winfo_screenheight(),
          f"x={root.winfo_x()} w={root.winfo_width()} 屏宽={root.winfo_screenwidth()}")

    window.btn_expand.invoke()          # 同一个绿点，第二次 = 收起
    root.update()
    time.sleep(0.2)
    root.update()
    check("再点一次【绿点】→ 收回 240x126+（绿点是双向的）",
          root.geometry().startswith("240x") and root.winfo_height() >= 126, root.geometry())

    # 4) 蓝点 → 收进系统托盘（HTTP 必须继续响应）
    import json as _json
    import urllib.request

    def health_ok(port):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/health", timeout=5) as resp:
                return resp.status == 200 and _json.load(resp).get("success") is True
        except Exception:
            return False

    size_before_tray = (root.winfo_width(), root.winfo_height())
    pos_before_tray = (root.winfo_x(), root.winfo_y())
    window.btn_tray.invoke()
    root.update()
    time.sleep(0.4)
    root.update()
    check("点【蓝点】→ 窗口不可见（withdraw）",
          root.state() == "withdrawn" and not root.winfo_viewable(),
          f"state={root.state()} viewable={root.winfo_viewable()}")
    check("点【蓝点】→ 托盘图标真的加上了",
          window.tray.visible and bool(window.tray.hwnd),
          f"visible={window.tray.visible} hwnd={window.tray.hwnd} err={window.tray.last_error}")
    check("挂件自己记着「我在托盘里」", window.hidden_in_tray)
    check("收进托盘期间 HTTP 照常 200（桥没被窗口带走）", health_ok(PORT))
    check("收进托盘期间端口还在监听", _port_open(PORT))

    items = window.tray.menu_items()
    check("右键菜单里真的建出了「显示窗口」和「退出」",
          [text for _cmd, text in items] == ["显示窗口", "退出（停掉数据桥）"], items)
    check("菜单命令号跟常量对得上",
          [cmd for cmd, _text in items] == [module.TRAY_CMD_SHOW, module.TRAY_CMD_EXIT],
          [cmd for cmd, _text in items])

    # 4b) 左键单击托盘图标（走 WNDPROC 的真实入口）→ 窗口回来
    _send_tray_click(module, window.tray, WM_LBUTTONUP, root, 1.2)
    root.update()
    check("左键单击托盘图标 → 窗口回来了",
          root.state() == "normal" and bool(root.winfo_viewable()),
          f"state={root.state()} viewable={root.winfo_viewable()}")
    check("还原后无边框装回来了", bool(root.overrideredirect()))
    check("还原后置顶装回来了", bool(root.attributes("-topmost")))
    check("还原后尺寸没丢",
          (root.winfo_width(), root.winfo_height()) == size_before_tray,
          f"{(root.winfo_width(), root.winfo_height())} vs {size_before_tray}")
    check("还原后位置没丢（也在屏幕内）",
          (root.winfo_x(), root.winfo_y()) == pos_before_tray
          and 0 <= root.winfo_x() <= root.winfo_screenwidth() - root.winfo_width(),
          f"{(root.winfo_x(), root.winfo_y())} vs {pos_before_tray}")
    kind, same = _window_region(root, root.winfo_width(), root.winfo_height())
    check(f"还原后圆角裁剪还在（region kind={kind}，3=COMPLEXREGION 圆角）",
          kind == 3, f"kind={kind} EqualRgn={same}")
    check("还原后托盘图标已撤掉（窗口都回来了，托盘里不留图标）",
          not window.tray.visible)
    check("还原后不是图标态（是正常的挂件窗口）", window.hidden_in_tray is False)

    # 4c) 再收一次 + 托盘菜单「显示窗口」也要能回来
    window.btn_tray.invoke()
    root.update()
    time.sleep(0.3)
    check("再点【蓝点】还能再收进托盘（可反复）", window.tray.visible and window.hidden_in_tray)
    _send_tray_click(module, window.tray, WM_LBUTTONUP, root, 1.2)
    root.update()
    check("再点一次托盘图标仍然能还原",
          root.state() == "normal" and not window.hidden_in_tray and not window.tray.visible,
          f"state={root.state()} hidden={window.hidden_in_tray}")

    # 5) 黄点 → 最小化到任务栏（这条行为没变，继续验）
    window.btn_minimize.invoke()
    time.sleep(0.6)
    root.update()
    state_now = root.state()
    check("点【黄点】→ 窗口进入图标态（任务栏最小化）", state_now == "iconic", state_now)
    root.deiconify()
    root.update()
    time.sleep(0.5)
    root.update()
    check("从任务栏还原 → 回到普通态", root.state() == "normal", root.state())
    check("还原后无边框自动装回来", bool(root.overrideredirect()))
    check("还原后位置尺寸没丢", root.geometry().startswith("240x") and root.winfo_height() >= 126,
          root.geometry())

    # 6) 📁 按钮背后的热切换：换目录 → 重开 httpd → 存进 azurpilot-root.txt
    import shutil
    import tempfile

    def health_root(port):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/health", timeout=5) as resp:
                return _json.load(resp).get("root")
        except Exception as exc:
            return f"<请求失败 {exc}>"

    fake_root = Path(tempfile.mkdtemp(prefix="fake-azurpilot-"))
    (fake_root / "module" / "statistics").mkdir(parents=True)
    (fake_root / "config").mkdir()
    (fake_root / "module" / "statistics" / "ship_exp_stats.py").write_text("# fake", encoding="utf-8")

    cache = module.ROOT_CACHE_PATH
    cache_backup = cache.read_text(encoding="utf-8") if cache.is_file() else None
    real_root = Path(r"D:\Tools\AzurPilot")
    try:
        check("切换前 /api/health 指向真目录", health_root(PORT) == str(real_root), health_root(PORT))
        switched = window.switch_root(fake_root)
        time.sleep(0.5)
        check("switch_root 返回成功", switched)
        check("切换后 httpd 已重启且指向新目录", health_root(PORT) == str(fake_root), health_root(PORT))
        check("新目录已写进 azurpilot-root.txt",
              cache.is_file() and cache.read_text(encoding="utf-8").strip() == str(fake_root))
        check("窗口里的路径跟着更新", str(fake_root) in window.lbl_root.cget("text"),
              window.lbl_root.cget("text"))
        check("端口没变（还是同一个）", f":{PORT}" in window.entry_url.get(), window.entry_url.get())

        window.switch_root(real_root)
        time.sleep(0.5)
        check("切回真目录后 httpd 再次可用", health_root(PORT) == str(real_root), health_root(PORT))
        check("切回后缓存也回到真目录",
              cache.read_text(encoding="utf-8").strip() == str(real_root))
    finally:
        if cache_backup is not None:
            cache.write_text(cache_backup, encoding="utf-8")
        shutil.rmtree(fake_root, ignore_errors=True)

    # 7) 红点关窗口：服务要停、端口要释放
    window.btn_close.invoke()
    time.sleep(1.0)
    check("关窗口后端口已释放（服务 shutdown）", not _port_open(PORT))
    check("窗口对象标记为已关闭", window.closed)
    check("关窗口后托盘图标也撤干净了（不留死图标）",
          not window.tray.visible and window.tray.hwnd is None)

    # 8) 「窗口还在托盘里的时候点红点 / 托盘菜单退出」——两条路都必须释放端口
    #
    #    8a) 人在托盘里 → 从托盘菜单点「退出」（WM_COMMAND 走的是和真菜单一样的入口）
    tray_server = module._bind_server(TRAY_PORT)
    threading.Thread(target=tray_server.serve_forever, daemon=True).start()
    time.sleep(0.3)
    tray_root = tk.Tk()
    tray_window = module.BridgeWindow(tray_root, dict(state, server=tray_server, port=TRAY_PORT))
    tray_root.update()
    time.sleep(0.2)
    tray_window.btn_tray.invoke()
    tray_root.update()
    time.sleep(0.3)
    check("（托盘退出用例）窗口已收进托盘", tray_window.hidden_in_tray and tray_window.tray.visible)
    check(f"（托盘退出用例）{TRAY_PORT} 上有服务", _port_open(TRAY_PORT))
    user32, _k, _s = module._win32()
    user32.SendMessageW(tray_window.tray.hwnd, WM_COMMAND, module.TRAY_CMD_EXIT, 0)
    pump_tk(tray_root, 1.6)
    check("（托盘退出用例）托盘菜单「退出」→ 端口已释放", not _port_open(TRAY_PORT))
    check("（托盘退出用例）窗口标记为已关闭", tray_window.closed)
    check("（托盘退出用例）托盘图标已撤掉、隐藏窗口已销毁",
          not tray_window.tray.visible and tray_window.tray.hwnd is None,
          f"visible={tray_window.tray.visible} hwnd={tray_window.tray.hwnd}")
    try:
        tray_root.destroy()
    except tk.TclError:
        pass

    #    8b) 反方向：人点红点的时候窗口正好收在托盘里 → 托盘图标不能留
    tray_server2 = module._bind_server(TRAY_PORT + 1)
    threading.Thread(target=tray_server2.serve_forever, daemon=True).start()
    time.sleep(0.3)
    tray_root2 = tk.Tk()
    tray_window2 = module.BridgeWindow(
        tray_root2, dict(state, server=tray_server2, port=TRAY_PORT + 1)
    )
    tray_root2.update()
    time.sleep(0.2)
    tray_window2.btn_tray.invoke()
    tray_root2.update()
    time.sleep(0.3)
    check("（红点用例）窗口已收进托盘", tray_window2.tray.visible)
    tray_window2.on_close()                 # 等价于点红点（托盘里的窗口收不到鼠标）
    pump_tk(tray_root2, 1.6)
    check("（红点用例）收在托盘里点红点 → 端口也释放了", not _port_open(TRAY_PORT + 1))
    check("（红点用例）托盘图标被清掉了（不留点不动的死图标）",
          not tray_window2.tray.visible and tray_window2.tray.hwnd is None,
          f"visible={tray_window2.tray.visible} hwnd={tray_window2.tray.hwnd}")
    try:
        tray_root2.destroy()
    except tk.TclError:
        pass

    # 9) 错误态：标题变红 + 显示原因
    error_root = tk.Tk()
    error_window = module.BridgeWindow(
        error_root,
        module._build_error_state(
            "找不到 AzurPilot", "没找到 AzurPilot。任选一种解决：1) 把文件夹拖到 exe 上", PORT, False,
        ),
    )
    error_root.update()
    time.sleep(0.2)
    error_root.update()
    check("失败时标题显示为红色", error_window.lbl_title.cget("fg") == module.ERR_RED)
    check("失败时窗口里有原因文字", "没找到" in error_window.lbl_title.cget("text")
          or "拖到" in error_window.lbl_detail.cget("text"))
    error_window.btn_expand.invoke()
    error_root.update()
    check("失败时展开能看到完整原因", error_window.lbl_detail.winfo_ismapped())
    error_window.on_close()
    time.sleep(0.3)

    # 10) 顺带启动 AP 的判断逻辑（不真的拉起用户的 AzurPilot）
    fake = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if fake and fake.is_dir():
        message = module.launch_azurpilot(fake)
        check("假目录里没有 alas-launcher.exe 时不报错", "没找到" in message, message)
    real = Path(r"D:\Tools\AzurPilot")
    if real.is_dir():
        message = module.launch_azurpilot(real)
        check("AP 已在运行时不会重复启动", "已经在运行" in message or "已启动" in message, message)

    failed = [name for name, ok, _ in checks if not ok]
    print()
    print(f"  合计 {len(checks)} 项，失败 {len(failed)} 项")
    for name in failed:
        print(f"    [X] {name}")
    print("=" * 70)
    print("  [PASS] 窗口自测全部通过" if not failed else "  [FAIL] 有项目没通过")
    print("=" * 70)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
