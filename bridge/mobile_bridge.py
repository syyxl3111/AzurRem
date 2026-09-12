#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AzurPilot 手机端 · 只读数据桥（sidecar）

为什么需要它：
    AzurPilot 的 WebUI（25548）自带 MCP，已经能提供「资源当前值、日志、状态、
    启停、调度队列、任务配置的读写」。但有三类数据它没暴露成任何 HTTP 接口，
    而手机端又读不到 PC 的文件：

      1. 资源历史趋势   → config/azurstats_local.db::resource_snapshots
      2. 任务菜单树     → module/config/argument/menu.json + i18n/zh-CN.json
      3. 耄耋相接 / 每日经验检测 → log/azurstat_meowofficer_farming.csv
                                   log/cl1/<实例>/ship_exp_data.json
      4. 耄耋相接数据收集 → config/cl1_data.db::cl1_data.data_json（明文 JSON）
      5. 概览页的**三段调度队列**（运行中/队列中/等待中）
                         → config/<实例>.json + module/config/argument/args.json
         MCP 的 get_scheduler_queue 只给一个扁平数组（按 next_run 排），既没有
         PC 的优先级顺序，也分不出「队列中」和「等待中」，所以桥自己复刻 PC 的
         get_next_task() 规则。

为什么用独立进程 + 新文件：
    AzurPilot 的升级方式是 `git reset --hard` + `git pull`，会还原所有**被跟踪**
    的文件（改 module/webui/api.py 加路由，升级后 100% 丢失）；但它**不执行
    git clean**，所以新增的未跟踪文件会保留下来。这个脚本就是这种文件。

安全性：
    纯只读。SQLite 以只读模式打开，其余只做文件读取，不写任何东西。

用法：
    # 打包后的 exe（推荐）：双击即可，自己找 AzurPilot
    AzurRemBridge.exe
    # 也可以把 AzurPilot 文件夹直接拖到 exe 上（argv[1] = 目录）
    AzurRemBridge.exe "D:\\Tools\\AzurPilot"
    # 源码方式跑：任意 Python 3.10+（只用标准库，**不需要** AzurPilot 的 venv）
    python mobile_bridge.py --root D:\\Tools\\AzurPilot --port 25550
    # 默认监听 0.0.0.0:25550

    --root 让桥不必被复制进项目目录（升级是 git reset --hard，项目里不留东西最干净）。
    main() 里会 os.chdir(ROOT)：读数据用的都是 ROOT 推出来的绝对路径，
    chdir 只是为了让外部工具（比如 alas 自己的日志相对路径）行为一致。

不再依赖项目的 Python 环境：
    经验统计（/api/ship_exp）原先 import module.statistics.ship_exp_stats，
    打包成 exe 后没有项目的 venv，import 必失败。现已把用到的那几个只读方法
    **逐行照抄**进本文件（见下面「3.5 舰船经验统计」一节），纯 stdlib，
    并对同一份 ship_exp_data.json 与原实现做过逐字段 diff 验证。

接口：
    GET /api/health
    GET /api/resource_history?instance=alas&hours=168&buckets=140
    GET /api/task_tree
    GET /api/meow_stats?instance=alas
    GET /api/meow_hazard?instance=alas
    GET /api/ship_exp?instance=alas
    GET /api/overview_tasks?instance=alas
    GET /api/logs/tail?instance=alas&offset=0&tail_lines=400&max_bytes=65536
"""

from __future__ import annotations

import csv
import hmac
import io
import json
import math
import os
import re
import secrets
import socket
import sqlite3
import string
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

# 委托收益聚合：刻意独立成文件（bridge/commission_stats.py），好跟 AzurPilot 的
# module/statistics/commission_income_stats.py 逐字段对照，见 verify_commission_parity.py。
#
# 同级模块的导入路径，三种跑法都要成立：
#   · `python bridge\mobile_bridge.py`：脚本目录就是 sys.path[0]，普通 import 就行；
#   · PyInstaller 打包后：分析阶段脚本目录在 sys.path 上，模块会被收进 PYZ，也在；
#   · **被 importlib 从别的目录加载**（gui_test.py、外部脚本都可能这么干）：
#     sys.path 上没有 bridge\，裸 import 会 ModuleNotFoundError ——
#     实测踩到过，所以这里兜一手：把自己的目录塞进 sys.path 再试一次。
try:
    import commission_stats
except ModuleNotFoundError:                     # pragma: no cover - 取决于导入方式
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import commission_stats


def _app_dir() -> Path:
    """本程序（exe 或 .py）真正所在的目录。

    PyInstaller --onefile 跑起来时 __file__ 指向临时解包目录（_MEIPASS），
    拿它去找 azurpilot-root.txt / 写缓存会跑进临时目录、下次就没了，
    所以冻结后必须用 sys.executable（= 用户双击的那个 exe）。
    """
    if getattr(sys, "frozen", False):
        try:
            return Path(sys.executable).resolve().parent
        except OSError:
            return Path(os.getcwd())
    return Path(__file__).resolve().parent


APP_DIR = _app_dir()

# 记住上次找到的 AzurPilot 根目录（写在 exe/脚本旁边，卸载=删文件，不碰系统）
ROOT_CACHE_PATH = APP_DIR / "azurpilot-root.txt"

# 项目根目录：默认 = 本程序所在目录（历史行为），可用 --root / configure_root() 覆盖。
# 所有数据路径都由 ROOT 推出来，**不再依赖 __file__ 的位置**。
DEFAULT_ROOT = APP_DIR
ROOT = DEFAULT_ROOT
CONFIG_DIR = ROOT / "config"
DB_PATH = ROOT / "config" / "azurstats_local.db"
CL1_DB_PATH = ROOT / "config" / "cl1_data.db"
MENU_PATH = ROOT / "module" / "config" / "argument" / "menu.json"
ARGS_PATH = ROOT / "module" / "config" / "argument" / "args.json"
I18N_PATH = ROOT / "module" / "config" / "i18n" / "zh-CN.json"
LOG_DIR = ROOT / "log"
MEOW_CSV = ROOT / "log" / "azurstat_meowofficer_farming.csv"
CL1_LOG_DIR = ROOT / "log" / "cl1"

HOST = "0.0.0.0"
# 手机端 App 的默认端口就是 25550（AzurPilotMobile Settings.BRIDGE_PORT），
# 所以 exe 默认也监听它。25549 上可能还跑着早期版本（只有 /api/history），别用。
PORT = 25550

# ─────────────────────────────────────────────────────────────
# 网关：把「只读数据桥」升级成 AzurPilot 的**唯一对外入口**
#
#   浏览器 ── GET /      ──→ 极简状态页（已连接 / 失败，请在 App 上查看）
#   App    ── /mcp/*     ──→ 反代 AzurPilot，服务端注入它的密码
#   App    ── /api/…     ──→ 那两个统计接口，同样注入密码
#   App    ── 其余 9 条  ──→ 自己读本地库（就是原来桥干的事）
#
# 这样一来：App 只需要一个地址 + 一个密码；AzurPilot 不必再直接暴露公网；
# 而且 AzurPilot 以后改自己的接口（比如 2026-09-11 那次给 MCP 加鉴权），
# 要改的是这里，不是 App。
# ─────────────────────────────────────────────────────────────

# 网关**自己的**密码，和 AzurPilot 的 WebUI 密码刻意分开：
# 网关密码是公网入口的钥匙，AzurPilot 的密码是服务端最后一道门。
# 两个混用，等于把最后一道门也一起交出去了。
GATEWAY_KEY_PATH = APP_DIR / "azurrem-gateway.key"

# 接受凭据的查询参数名 —— 与 AzurPilot `module/webui/mcp_auth.py` 的
# QUERY_KEY_NAMES 保持一致，同一套客户端习惯两边通用。
QUERY_KEY_NAMES = ("key", "api_key", "token")

# 反代到 AzurPilot 的超时。SSE 是长连接，单列一个宽得多的值：客户端
# 每轮只连几秒，但中间可能长时间没有数据帧，不能用普通请求的超时掐它。
UPSTREAM_TIMEOUT = 8.0
UPSTREAM_SSE_TIMEOUT = 300.0

# 反代**白名单**：只放这三个前缀过去。
#
# ★ 这是安全边界，不是偷懒。AzurPilot 的 FastAPI 上还挂着
#   `/api/launcher/startup`（POST，能拉起进程）、`/api/deploy/*`、
#   `/api/import_legacy_upload`、以及两个 WebSocket 控制通道
#   （`/ws/live_control` 是**真能点屏幕**的）。一旦做成"整个站点通用反代"，
#   这些会一起被搬上公网。App 需要的只有 MCP（它自己就是完整控制面）
#   加两个只读统计接口，所以这里按前缀白名单放行。
PROXY_PREFIXES = ("/mcp/",)
PROXY_PATHS = ("/api/ap_timeline", "/api/cl1_stats")


DEFAULT_INSTANCE = "alas"
DEFAULT_HOURS = 168          # 7 天
DEFAULT_BUCKETS = 140
MAX_BUCKETS = 400

# 与 module/statistics/resource_stats.py:36-49 的 RESOURCE_COLUMNS 保持一致
RESOURCE_COLUMNS = [
    ("Oil", "oil"),
    ("Coin", "coin"),
    ("Gem", "gem"),
    ("Pt", "pt"),
    ("Cube", "cube"),
    ("Core", "core"),
    ("Medal", "medal"),
    ("Merit", "merit"),
    ("GuildCoin", "guild_coin"),
    ("ActionPoint", "action_point"),
    ("YellowCoin", "yellow_coin"),
    ("PurpleCoin", "purple_coin"),
]

# 与 azurstats.py:154 的 meowofficer_farming_labels 一致
MEOW_LABELS = ["侵蚀等级", "上次记录时间", "有效战斗轮数", "平均黄币/轮", "平均金菜/轮", "平均深渊/轮", "平均隐秘/轮"]


def _clamp(value: int, low: int, high: int) -> int:
    return max(low, min(value, high))


def _int_param(query: dict, key: str, default: int) -> int:
    """query 里的整数参数：缺失 / 空串 / 非法值都退回默认值。

    日志接口的 offset 由客户端每次原样回传，空串或脏值不该让整个请求 500。
    """
    raw = (query.get(key) or [""])[0].strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def configure_root(root) -> Path:
    """切换项目根目录，并重算所有数据路径常量。

    桥的数据全部在 AzurPilot 项目里，但脚本本身不必放在项目里（项目的升级方式是
    `git reset --hard`，往里放文件迟早被清掉或者碍事），所以路径统一从 ROOT 推。
    """
    global ROOT, CONFIG_DIR, DB_PATH, CL1_DB_PATH, MENU_PATH, ARGS_PATH, I18N_PATH, LOG_DIR, MEOW_CSV, CL1_LOG_DIR

    ROOT = Path(root).expanduser().resolve()
    CONFIG_DIR = ROOT / "config"
    DB_PATH = ROOT / "config" / "azurstats_local.db"
    CL1_DB_PATH = ROOT / "config" / "cl1_data.db"
    MENU_PATH = ROOT / "module" / "config" / "argument" / "menu.json"
    ARGS_PATH = ROOT / "module" / "config" / "argument" / "args.json"
    I18N_PATH = ROOT / "module" / "config" / "i18n" / "zh-CN.json"
    LOG_DIR = ROOT / "log"
    MEOW_CSV = ROOT / "log" / "azurstat_meowofficer_farming.csv"
    CL1_LOG_DIR = ROOT / "log" / "cl1"
    return ROOT


def validate_root(root: Path) -> list:
    """--root 必须指向一个真的 AzurPilot 项目：至少得有 module/ 和 config/。

    自动发现走更严的 is_azurpilot_root()（多要一条 ship_exp_stats.py，免得认错
    同名的空壳目录）；显式 --root / 拖拽进来的目录只做这里的硬校验 —— 桥自己带
    了经验表，缺那个 .py 也照样能跑，不该因此拒绝启动。
    """
    problems = []
    if not root.is_dir():
        problems.append(f"项目根目录不存在：{root}")
        return problems
    for name in ("module", "config"):
        if not (root / name).is_dir():
            problems.append(f"{root} 下找不到 {name}/，不像是 AzurPilot 项目根目录")
    return problems


# ─────────────────────────────────────────────────────────────
# 0. 找 AzurPilot 装在哪（exe 换台电脑双击就能用的关键）
#
# 优先级：
#   1. 命令行 --root（也接受把文件夹直接拖到 exe 上，argv[1] 就是路径）
#   2. exe 旁边的 azurpilot-root.txt（上次找到的，自动写入）
#   3. 环境变量 AZURPILOT_ROOT
#   4. 常见安装路径
#   5. 各盘根目录 / <盘>:\Tools\ 下扫一层（名字含 AzurPilot / Alas）
#   6. 正在运行的 alas-launcher.exe 所在目录
# ─────────────────────────────────────────────────────────────

def is_azurpilot_root(path) -> bool:
    """判定一个目录是不是 AzurPilot 项目根。

    三条**同时**成立才算，最后一条专门用来排除同名的空壳 / 残留目录：
        1. module\\ 是目录
        2. config\\ 是目录
        3. module\\statistics\\ship_exp_stats.py 是文件
    """
    if not path:
        return False
    try:
        p = Path(path)
    except (TypeError, ValueError):
        return False
    try:
        return (
            (p / "module").is_dir()
            and (p / "config").is_dir()
            and (p / "module" / "statistics" / "ship_exp_stats.py").is_file()
        )
    except OSError:
        return False


def _normalize_path_arg(raw):
    """把「用户给的路径」规范化。

    拖拽到 exe 上时，Windows 传进来的 argv 里**带空格的路径会带引号**
    （cmd 里是 `"D:\\My Tools\\AzurPilot"`），所以引号、首尾空格都要剥掉。
    """
    if raw is None:
        return None
    text = str(raw).strip().strip('"').strip("'").strip()
    if not text:
        return None
    try:
        path = Path(text).expanduser()
    except (OSError, ValueError, RuntimeError):
        return None
    # 拖进来的是文件（比如 alas.py）就取它所在目录
    try:
        if path.is_file():
            path = path.parent
    except OSError:
        pass
    return path


def read_cached_root():
    """读 exe 旁边的 azurpilot-root.txt（上次找到的目录）。

    用 utf-8-sig 读：这个文件用户可能用记事本手改，带 BOM 的话普通 utf-8
    会在路径前面留一个 \\ufeff，导致路径被判成无效、缓存白白失效。
    """
    try:
        if not ROOT_CACHE_PATH.is_file():
            return None
        text = ROOT_CACHE_PATH.read_text(encoding="utf-8-sig", errors="replace").strip()
    except OSError:
        return None
    path = _normalize_path_arg(text)
    if path is not None and is_azurpilot_root(path):
        return path
    return None


def write_cached_root(root: Path) -> None:
    """记住这次找到的目录，下次启动直接命中。

    写不进去（exe 放在只读目录 / Program Files）不影响使用，只提示一句。
    """
    try:
        ROOT_CACHE_PATH.write_text(str(root) + "\n", encoding="utf-8")
    except OSError as exc:
        # ⚠️ --noconsole 下 sys.stdout 是 None，print 会抛 AttributeError，
        #    所以这里必须走 log()（它自己会判断 stdout 在不在）
        log(f"提示：无法写入 {ROOT_CACHE_PATH}（{exc}），下次仍会自动查找")


def _common_root_candidates() -> list:
    """常见安装位置，按可能性排序。"""
    candidates = [
        Path("D:/Tools/AzurPilot"), Path("C:/Tools/AzurPilot"),
        Path("E:/Tools/AzurPilot"), Path("F:/Tools/AzurPilot"),
        Path("D:/AzurPilot"), Path("C:/AzurPilot"),
        Path("E:/AzurPilot"), Path("F:/AzurPilot"),
        Path("D:/Tools/Alas"), Path("C:/Tools/Alas"),
        Path("D:/Alas"), Path("C:/Alas"),
    ]
    home = os.environ.get("USERPROFILE") or os.environ.get("HOME")
    if home:
        base = Path(home)
        candidates += [
            base / "AzurPilot",
            base / "Desktop" / "AzurPilot",
            base / "Desktop" / "AzurPilot-master",
            base / "Documents" / "AzurPilot",
            base / "Desktop" / "Alas",
        ]
    return candidates


def _drive_roots() -> list:
    """所有可用的**本地**盘根，例如 C:\\ D:\\ 。

    只保留固定盘 / 可移动盘：网络盘断线时 iterdir 会卡十几秒，光驱、RAM 盘也
    没意义。盘类型用 GetDriveTypeW 问系统（拿不到就不过滤，退回列字母）。
    """
    DRIVE_REMOVABLE, DRIVE_FIXED = 2, 3

    def drive_type(root: str):
        if os.name != "nt":
            return None
        try:
            import ctypes
            return int(ctypes.windll.kernel32.GetDriveTypeW(ctypes.c_wchar_p(root)))
        except Exception:
            return None

    candidates = []
    listdrives = getattr(os, "listdrives", None)
    if callable(listdrives):
        try:
            candidates = [str(item) for item in listdrives()]
        except OSError:
            candidates = []
    if not candidates:
        candidates = [f"{letter}:\\" for letter in "CDEFGHIJKLMNOPQRSTUVWXYZ"]

    roots = []
    for item in candidates:
        root = item if item.endswith("\\") else item + "\\"
        kind = drive_type(root)
        if kind is not None and kind not in (DRIVE_FIXED, DRIVE_REMOVABLE):
            continue
        try:
            if Path(root).exists():
                roots.append(Path(root))
        except OSError:
            continue
    return roots


def _scan_drives_for_root() -> list:
    """各盘根目录 + <盘>:\\Tools\\ 下扫一层，找名字含 AzurPilot / Alas 的目录。

    只扫这两层：再深就是瞎猜，而且会很慢。每个目录下最多看 500 个条目，
    免得遇到超大目录卡住。
    """
    found = []
    for base in _drive_roots():
        parents = [base]
        tools = base / "Tools"
        try:
            if tools.is_dir():
                parents.append(tools)
        except OSError:
            pass
        for parent in parents:
            try:
                children = sorted(parent.iterdir(), key=lambda p: p.name.lower())
            except OSError:
                continue
            for child in children[:500]:
                try:
                    if not child.is_dir():
                        continue
                except OSError:
                    continue
                name = child.name.lower()
                if "azurpilot" not in name and "alas" not in name:
                    continue
                if is_azurpilot_root(child):
                    found.append(child)
    return found


def _root_from_running_process():
    """从正在运行的 alas-launcher.exe / alas.exe 反推安装目录。

    项目没装 psutil，这里借一次 PowerShell（只读查询，不写任何东西）；
    查不到就当没有，绝不让它影响启动。
    """
    if os.name != "nt":
        return None
    script = (
        "Get-Process -Name 'alas-launcher','alas' -ErrorAction SilentlyContinue "
        "| Select-Object -First 1 -ExpandProperty Path"
    )
    try:
        done = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            timeout=10, check=False,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    except (OSError, subprocess.SubprocessError):
        return None
    text = (done.stdout or b"").decode("utf-8", errors="replace")
    for line in text.splitlines():
        path = _normalize_path_arg(line)
        if path is None:
            continue
        for candidate in (path, path.parent):
            if is_azurpilot_root(candidate):
                return candidate
    return None


def _argv_root_candidate(argv: list):
    """把「拖到 exe 上的那个路径」从命令行里挑出来。

    双击时没有参数；把文件夹拖到 exe 上时 Windows 把它作为 argv[1] 传进来。
    只有确实存在的路径才认，别的裸参数忽略（免得把 `--help` 之类当成目录）。
    """
    for item in argv:
        if not item or item.startswith("-"):
            continue
        path = _normalize_path_arg(item)
        if path is None:
            continue
        try:
            if path.exists():
                return path
        except OSError:
            continue
    return None


def find_azurpilot_root(explicit=None):
    """按优先级找 AzurPilot 根目录，返回 (路径 或 None, 来源说明)。"""
    # 1) 命令行 --root / 拖拽进来的目录：显式指定最高优先，认了就不再挑
    if explicit:
        path = _normalize_path_arg(explicit)
        if path is None:
            return None, f"指定的路径看不懂：{explicit}"
        problems = validate_root(path)
        if problems:
            return None, "；".join(problems)
        try:
            return path.resolve(), "命令行 / 拖拽指定"
        except OSError:
            return path, "命令行 / 拖拽指定"

    # 2) 上次记住的（写在 exe 旁边）
    path = read_cached_root()
    if path is not None:
        return path, f"上次记住的（{ROOT_CACHE_PATH.name}）"

    # 3) 环境变量
    env = os.environ.get("AZURPILOT_ROOT")
    if env:
        path = _normalize_path_arg(env)
        if path is not None and is_azurpilot_root(path):
            return path, "环境变量 AZURPILOT_ROOT"

    # 4) 常见安装位置
    for candidate in _common_root_candidates():
        if is_azurpilot_root(candidate):
            return candidate, "常见安装位置"

    # 5) 各盘根目录 / <盘>:\Tools\ 扫一层
    for candidate in _scan_drives_for_root():
        return candidate, "磁盘扫描"

    # 6) 正在运行的 alas-launcher.exe
    path = _root_from_running_process()
    if path is not None:
        return path, "运行中的 alas-launcher.exe"

    return None, "以上都没找到"


def _read_json(path: Path):
    with io.open(path, encoding="utf-8") as fh:
        return json.load(fh)


# ─────────────────────────────────────────────────────────────
# 1. 资源历史
# ─────────────────────────────────────────────────────────────

def query_resource_history(instance: str, hours: int, buckets: int) -> dict:
    """读 resource_snapshots，按时间分桶返回每个资源的趋势。

    资源值是阶跃式的（只在变化时写一行），所以每个桶取**桶内最后一个值**
    而不是平均值 —— 平均值会把「从 0 涨到 100」平掉，看不出真实水平。
    """
    if not DB_PATH.exists():
        raise FileNotFoundError(f"找不到数据库：{DB_PATH}")

    cutoff = (datetime.now() - timedelta(hours=hours)).isoformat()
    columns = [col for _, col in RESOURCE_COLUMNS]
    select_cols = ", ".join(["ts"] + columns)

    # 只读模式打开：不会创建 -wal/-journal，也不会写任何东西
    uri = f"file:{DB_PATH.as_posix()}?mode=ro"
    conn = sqlite3.connect(uri, uri=True, timeout=5.0)
    try:
        rows = conn.execute(
            f"SELECT {select_cols} FROM resource_snapshots "
            f"WHERE instance = ? AND ts >= ? ORDER BY ts",
            (instance, cutoff),
        ).fetchall()
    finally:
        conn.close()

    if not rows:
        return {
            "instance": instance, "hours": hours, "buckets": 0, "points": 0,
            "from": None, "to": None,
            "resources": {key: [] for key, _ in RESOURCE_COLUMNS},
        }

    total = len(rows)
    buckets = _clamp(buckets, 1, MAX_BUCKETS)
    if total <= buckets:
        picks = list(range(total))
    else:
        step = total / buckets
        picks = sorted({int(i * step) for i in range(buckets)} | {total - 1})

    series: dict[str, list] = {key: [] for key, _ in RESOURCE_COLUMNS}
    for idx in picks:
        row = rows[idx]
        ts = row[0]
        for offset, (key, _) in enumerate(RESOURCE_COLUMNS):
            value = row[offset + 1]
            if value is None:
                continue
            series[key].append({"ts": ts, "v": int(value)})

    return {
        "instance": instance, "hours": hours, "buckets": len(picks), "points": total,
        "from": rows[0][0], "to": rows[-1][0],
        "resources": series,
    }


# ─────────────────────────────────────────────────────────────
# 2. 任务菜单树（图片里那个左栏）
# ─────────────────────────────────────────────────────────────

def query_task_tree() -> dict:
    """按 menu.json 的分组结构 + i18n 的中文名，拼出侧栏菜单树。"""
    if not MENU_PATH.exists():
        raise FileNotFoundError(f"找不到 menu.json：{MENU_PATH}")

    menu = _read_json(MENU_PATH)
    i18n = _read_json(I18N_PATH) if I18N_PATH.exists() else {}

    menu_names = i18n.get("Menu", {})
    task_names = i18n.get("Task", {})

    groups = []
    for group_key, node in menu.items():
        if not isinstance(node, dict):
            continue
        g_name = (menu_names.get(group_key) or {}).get("name", group_key)
        tasks = []
        for task_key in node.get("tasks", []):
            t_name = (task_names.get(task_key) or {}).get("name", task_key)
            tasks.append({"key": task_key, "name": t_name})
        groups.append({
            "key": group_key,
            "name": g_name,
            "page": node.get("page", "setting"),
            "collapsible": node.get("menu") == "collapse",
            "tasks": tasks,
        })

    return {"groups": groups, "count": sum(len(g["tasks"]) for g in groups)}


# ─────────────────────────────────────────────────────────────
# 3. 耄耋相接（指挥喵）统计
# ─────────────────────────────────────────────────────────────

def query_meow_stats(instance: str) -> dict:
    """读 azurstat_meowofficer_farming.csv（6 行 = 侵蚀等级 1~6）。"""
    if not MEOW_CSV.exists():
        return {"available": False, "reason": "还没有耄耋相接数据文件", "rows": []}

    rows = []
    with io.open(MEOW_CSV, encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        for raw in reader:
            try:
                level = int(float(raw.get("侵蚀等级", 0)))
                rows.append({
                    "level": level,
                    "lastRecordTs": int(float(raw.get("上次记录时间", 0) or 0)),
                    "rounds": int(float(raw.get("有效战斗轮数", 0) or 0)),
                    "coinPerRound": float(raw.get("平均黄币/轮", 0) or 0),
                    "goldPerRound": float(raw.get("平均金菜/轮", 0) or 0),
                    "abyssPerRound": float(raw.get("平均深渊/轮", 0) or 0),
                    "obscurePerRound": float(raw.get("平均隐秘/轮", 0) or 0),
                })
            except (TypeError, ValueError):
                continue

    rows.sort(key=lambda r: r["level"])
    return {
        "available": any(r["rounds"] > 0 for r in rows),
        "labels": MEOW_LABELS,
        "rows": rows,
    }


# ─────────────────────────────────────────────────────────────
# 3b. 耄耋相接**数据收集**（按侵蚀等级 3 / 5 分组）
#
# ⚠️ 和上面的 /api/meow_stats 是**两张不同的表**：
#   /api/meow_stats  = 「耄耋相接收获」战利品（黄币/金菜/深渊/隐秘 每轮均值）
#                      → log/azurstat_meowofficer_farming.csv
#   /api/meow_hazard = 「耄耋相接数据收集」按侵蚀等级分组的场次与耗时
#                      → config/cl1_data.db::cl1_data.data_json（明文 JSON）
#
# 为什么复刻而不是直接调 module.statistics.cl1_database.Cl1Database.get_meow_stats()：
#   它内部 `_reconcile_meow_counts(..., persist=True)`（cl1_database.py:1112-1120）
#   **会回写数据库**，违反本桥"纯只读"的约束。所以这里只复刻其中的**纯计算**部分
#   （cl1_database.py:1084-1262 的推导 + 几个 helper），一行都不写盘。
# ─────────────────────────────────────────────────────────────

# PC 端只显示 3 级和 5 级（app_stat_opsi.py:313 `for hazard_level in (3, 5)`）
MEOW_HAZARD_LEVELS = (3, 5)


def _read_cl1_data(instance: str, month: str) -> dict:
    """只读打开 config/cl1_data.db，取出该实例该月的明文 data_json。

    只读模式（`file:...?mode=ro`）+ uri=True：不会创建 -wal/-journal，不会写任何东西。
    读不到（库不存在 / 没有这行 / JSON 坏了 / 没有明文只有旧密文）一律返回 {}。
    """
    if not CL1_DB_PATH.exists():
        return {}
    try:
        uri = f"file:{CL1_DB_PATH.as_posix()}?mode=ro"
        conn = sqlite3.connect(uri, uri=True, timeout=5.0)
        try:
            row = conn.execute(
                "SELECT data_json FROM cl1_data WHERE instance = ? AND month = ?",
                (instance, month),
            ).fetchone()
        finally:
            conn.close()
    except Exception:
        return {}

    if not row or not row[0]:
        # encrypted_blob 只用于旧版迁移，本桥不解密
        return {}
    try:
        data = json.loads(row[0])
    except Exception:
        return {}
    return data if isinstance(data, dict) else {}


def _siren_research_device_count(data: dict, hazard_level: int) -> int:
    """吊机（塞壬研究装置）次数。

    对齐 cl1_database.py:58 `get_siren_research_device_count(source="meow")`：
    `_normalize_siren_research_devices` 会把 meow 的键统一成 str(int(key))、
    值统一成 int，所以这里也按 str(等级) 取值。
    """
    devices = data.get("siren_research_devices")
    if not isinstance(devices, dict):
        return 0
    meow = devices.get("meow")
    if not isinstance(meow, dict):
        return 0
    try:
        return int(meow.get(str(int(hazard_level)), 0) or 0)
    except (TypeError, ValueError):
        return 0


def _meow_battles_per_round(hazard_level: int):
    """每轮战斗次数（cl1_database.py:377 _get_meow_battles_per_round）。"""
    if hazard_level in (2, 3):
        return 2
    if hazard_level in (4, 5, 6):
        return 3
    return None


def _normalize_meow_round_times(round_times) -> list:
    """兼容旧格式轮次样本，统一成 {"duration": float, "hazard_level": ...}（cl1_database.py:355）。"""
    normalized = []
    if not isinstance(round_times, list):
        return normalized
    for entry in round_times:
        if isinstance(entry, dict) and "duration" in entry:
            normalized.append(entry)
        elif isinstance(entry, (int, float)):
            normalized.append({"duration": float(entry), "hazard_level": None})
    return normalized


def _normalize_meow_hazard_stats(data: dict) -> dict:
    """兼容旧格式的分级统计结构（cl1_database.py:381）。"""
    raw_stats = data.get("meow_hazard_stats", {})
    if not isinstance(raw_stats, dict):
        return {}

    normalized = {}
    for hazard_key, bucket in raw_stats.items():
        try:
            hazard_level = int(hazard_key)
        except (TypeError, ValueError):
            continue
        if hazard_level not in {2, 3, 4, 5, 6} or not isinstance(bucket, dict):
            continue

        def _times(key):
            values = bucket.get(key, [])
            if not isinstance(values, list):
                return []
            out = []
            for entry in values:
                if isinstance(entry, (int, float)):
                    out.append(float(entry))
                elif isinstance(entry, dict) and isinstance(entry.get("duration"), (int, float)):
                    out.append(float(entry["duration"]))
            return out

        try:
            battle_raw_count = int(bucket.get("battle_raw_count", 0) or 0)
        except Exception:
            battle_raw_count = 0
        try:
            effective_rounds = float(bucket.get("effective_rounds", 0) or 0)
        except Exception:
            effective_rounds = 0.0

        normalized[str(hazard_level)] = {
            "battle_raw_count": max(0, battle_raw_count),
            "effective_rounds": max(0.0, effective_rounds),
            "round_times": _times("round_times"),
            "battle_times": _times("battle_times"),
        }
    return normalized


def _infer_meow_battles_per_round(round_times):
    """从轮次样本推断每轮战斗数（cl1_database.py:462）。"""
    hazard_levels = [
        entry.get("hazard_level")
        for entry in round_times
        if isinstance(entry, dict) and entry.get("hazard_level") in {2, 3, 4, 5, 6}
    ]
    if not hazard_levels:
        return None, None
    samples = [2 if hl in (2, 3) else 3 for hl in hazard_levels]
    inferred = sum(samples) / len(samples)
    return (2 if inferred < 2.5 else 3), inferred


def _reconcile_meow_counts(data: dict, effective_rounds: float, round_times, battle_times):
    """复刻 cl1_database.py:495 `_reconcile_meow_counts` 的纯计算部分。

    **没有 persist 参数** —— 原方法在 persist=True 时会 save_stats() 写库，
    这里只把结果返给调用方，永不落盘。
    """
    inferred_divisor, inferred_battles_per_round = _infer_meow_battles_per_round(round_times)
    estimated_from_rounds = None
    if effective_rounds > 0:
        if inferred_battles_per_round is not None:
            estimated_from_rounds = int(round(effective_rounds * inferred_battles_per_round))
        else:
            estimated_from_rounds = int(round(effective_rounds * 3))

    raw_battle_count = data.get("meow_battle_raw_count")
    current_raw = int(raw_battle_count) if raw_battle_count is not None else 0
    by_battle_times = len(battle_times) if battle_times else 0

    need_backfill = (
        raw_battle_count is None
        or estimated_from_rounds is not None
        and current_raw > 0
        and current_raw < int(estimated_from_rounds * 0.85)
    )

    if need_backfill:
        candidates = [
            candidate
            for candidate in [current_raw, estimated_from_rounds, by_battle_times]
            if candidate is not None
        ]
        raw_battle_count = max(candidates, default=int(round(effective_rounds)))
        if inferred_divisor in (2, 3) and effective_rounds > 0:
            effective_rounds = float(round(raw_battle_count / inferred_divisor, 2))
    else:
        raw_battle_count = current_raw

    if raw_battle_count > 0 and effective_rounds > 0:
        ratio = raw_battle_count / effective_rounds
        if ratio > 5:
            divisor_for_fix = inferred_divisor if inferred_divisor in (2, 3) else 3
            fixed_rounds = round(raw_battle_count / divisor_for_fix, 2)
            if abs(fixed_rounds - effective_rounds) > 0.01:
                effective_rounds = float(fixed_rounds)

    return int(raw_battle_count), effective_rounds


def query_meow_hazard(instance: str) -> dict:
    """「耄耋相接数据收集」：按侵蚀等级 3 / 5 返回场次、轮次、耗时、吊机。

    逐列对齐 PC 的 app_stat_opsi.py:309-363 `_build_meow_rows()`
    + cl1_database.py:1084-1262 `get_meow_stats()`（去掉会写库的 reconcile）。
    没有数据时返回空 rows，不报错。
    """
    now = datetime.now()
    month = f"{now.year:04d}-{now.month:02d}"

    try:
        data = _read_cl1_data(instance, month)
    except Exception:
        data = {}
    if not data:
        return {"month": month, "rows": []}

    try:
        round_times = data.get("meow_round_times", [])
        if not isinstance(round_times, list):
            round_times = []
        battle_times = data.get("meow_battle_times", [])
        if not isinstance(battle_times, list):
            battle_times = []

        normalized_round_times = _normalize_meow_round_times(round_times)
        effective_rounds = float(data.get("meow_battle_count", 0) or 0)
        battle_count, effective_rounds = _reconcile_meow_counts(
            data, effective_rounds, round_times, battle_times
        )

        # 旧格式兜底：轮次样本整体没有 hazard_level 时，按等级从顶层样本里挑
        hazard_sample_total = 0
        hazard_round_samples = {3: [], 5: []}
        for entry in normalized_round_times:
            duration = entry.get("duration")
            if not isinstance(duration, (int, float)):
                continue
            hl = entry.get("hazard_level")
            if hl in {2, 3, 4, 5, 6}:
                hazard_sample_total += 1
            if hl in hazard_round_samples:
                hazard_round_samples[hl].append(float(duration))

        hazard_stats = _normalize_meow_hazard_stats(data)
        rows = []
        for hazard_level in MEOW_HAZARD_LEVELS:
            bucket = hazard_stats.get(str(hazard_level), {})

            hz_battle_count = int(bucket.get("battle_raw_count", 0) or 0)
            hz_effective_rounds = float(bucket.get("effective_rounds", 0) or 0)
            hz_round_times = list(bucket.get("round_times") or []) or hazard_round_samples[hazard_level]
            hz_battle_times = list(bucket.get("battle_times") or [])

            # 该等级没有独立计数时，用总量按样本占比推算（同 PC 的 estimated 分支）
            if hz_battle_count <= 0 and hazard_sample_total > 0 and battle_count > 0:
                hz_battle_count = int(round(
                    battle_count * (len(hazard_round_samples[hazard_level]) / hazard_sample_total)
                ))

            battles_per_round = _meow_battles_per_round(hazard_level) or 1
            if hz_effective_rounds <= 0 and hz_battle_count > 0:
                hz_effective_rounds = hz_battle_count / battles_per_round

            hz_avg_round_time = 0.0
            if hz_round_times:
                hz_avg_round_time = round(sum(hz_round_times) / len(hz_round_times), 2)

            hz_avg_battle_time = 0.0
            if hz_battle_times:
                hz_avg_battle_time = round(sum(hz_battle_times) / len(hz_battle_times), 2)
            elif hz_avg_round_time > 0:
                hz_avg_battle_time = round(hz_avg_round_time / battles_per_round, 2)

            siren_count = _siren_research_device_count(data, hazard_level)
            siren_rate = 0.0
            if hz_effective_rounds > 0:
                siren_rate = round(siren_count / hz_effective_rounds, 4)

            rows.append({
                "hazardLevel": hazard_level,
                "battleCount": hz_battle_count,
                # PC 是 round(x, 1)，接近整数时显示成整数；JSON 里保持 float（客户端字段是 Double）
                "rounds": round(hz_effective_rounds, 1),
                "avgBattleTime": hz_avg_battle_time,
                "avgRoundTime": hz_avg_round_time,
                "sirenCount": siren_count,
                "sirenRate": siren_rate,
            })
    except Exception:
        return {"month": month, "rows": []}

    return {"month": month, "rows": rows}


# ─────────────────────────────────────────────────────────────
# 3.5 舰船经验统计（从 AzurPilot 移植，纯 stdlib）
#
# 为什么搬进来：exe 里没有 AzurPilot 的 venv，`import module.statistics.*` 必失败。
# 下面这段是这两个文件的**逐行照抄**，只去掉了写盘 / 项目日志 / 日报那几条与本桥
# 无关的分支（原版 on_battle_start / on_battle_end / _save 等写入路径一律没搬）：
#     module/os/ship_exp_data.py          → LIST_SHIP_EXP（自动生成的累计经验表）
#     module/statistics/ship_exp_stats.py → 只读的 get_all_progress 等几个方法
# 抄的时候没做任何「顺手优化」：我早先按 LIST_SHIP_EXP 猜公式，105 级被算成
# 「已完成」，和项目行为对不上。验证脚本：bridge/verify_ship_exp_parity.py
# （同一份 ship_exp_data.json，项目原实现 vs 本文件，逐字段 diff）。
# ─────────────────────────────────────────────────────────────

# 出处：D:\Tools\AzurPilot\module\os\ship_exp_data.py
# This file is auto-generated by dev_tools/ship_exp_extract.py.
# Do not edit this file manually.
# ⚠️ 自动生成、勿手改。索引 = 等级 - 1，值是到达该等级的**累计**经验。
LIST_SHIP_EXP = [
    0, 100, 300, 600, 1000, 1500, 2100, 2800, 3600, 4500,  # Lv1-10
    5500, 6600, 7800, 9100, 10500, 12000, 13600, 15300, 17100, 19000,  # Lv11-20
    21000, 23100, 25300, 27600, 30000, 32500, 35100, 37800, 40600, 43500,  # Lv21-30
    46500, 49600, 52800, 56100, 59500, 63000, 66600, 70300, 74100, 78000,  # Lv31-40
    82000, 86200, 90600, 95200, 100000, 105000, 110200, 115600, 121200, 127000,  # Lv41-50
    133000, 139200, 145600, 152200, 159000, 166000, 173200, 180600, 188200, 196000,  # Lv51-60
    204000, 212300, 220900, 229800, 239000, 248500, 258300, 268400, 278800, 289500,  # Lv61-70
    300500, 311900, 323700, 335900, 348500, 361500, 374900, 388700, 402900, 417500,  # Lv71-80
    432500, 448000, 464000, 480500, 497500, 515000, 533000, 551500, 570500, 590000,  # Lv81-90
    610000, 631000, 653000, 677000, 703000, 733000, 768000, 808000, 868000, 1000000,  # Lv91-100
    1050000, 1103000, 1159000, 1218000, 1280000, 1345000, 1416000, 1493000, 1576000, 1665000,  # Lv101-110
    1760000, 1865000, 1980000, 2105000, 2240000, 2385000, 2545000, 2720000, 2910000, 3115000,  # Lv111-120
    3335000, 3576000, 3838000, 4121000, 4425000,  # Lv121-125
]

# 抄漏一行就会在这里直接炸掉，而不是悄悄算错经验（125 = 1..125 级）
assert len(LIST_SHIP_EXP) == 125, f"LIST_SHIP_EXP 长度应为 125，实际 {len(LIST_SHIP_EXP)}"


class ShipExpStatsLocal:
    """module/statistics/ship_exp_stats.py::ShipExpStats 的**只读**移植版。

    与原版一致：数据全部来自 ship_exp_data.json，计算结果不落盘。
    原版的写入方法（on_battle_start / on_battle_end / _record_battle_time /
    record_round_time / _update_daily_stats / save_ship_data / _save）没有搬。

    构造参数是**数据文件路径**：原版是收 instance_name 再自己拼
    <项目根>/log/cl1/<实例>/ship_exp_data.json，而本桥的实例目录解析在
    _instance_log_dir()（可能回退扫描），所以由调用方把路径传进来。
    """

    # 每个位置的每场战斗经验值（原样抄）
    EXP_PER_BATTLE = {
        1: 431,  # 旗舰
        2: 288, 3: 288, 4: 288, 5: 288, 6: 288  # 其他位置
    }
    AVG_EXP_PER_BATTLE = 312  # 平均每场经验
    BATTLES_PER_ROUND = 2     # 侵蚀1每轮默认2场战斗

    def __init__(self, path):
        self._path = Path(path)
        self.data = self._load()

    def _load(self) -> dict:
        """加载数据文件（原版 _load，去掉 logger.warning）"""
        if not self._path.exists():
            return {}
        try:
            text = self._path.read_text(encoding='utf-8')
            data = json.loads(text)
            if isinstance(data, dict):
                return data
            return {}
        except Exception:
            return {}

    # ========== 每日经验效率统计（只读） ==========

    def get_average_battle_time(self) -> float:
        """获取平均每场战斗时间(秒)"""
        return self.data.get('battle_times', {}).get('average', 52.0)

    def get_average_round_time(self) -> float:
        """获取平均每轮侵蚀1时间(秒)"""
        if 'round_times' in self.data and self.data['round_times'].get('samples'):
            return self.data['round_times']['average']
        return self.get_average_battle_time() * 2 + 15

    def get_exp_per_hour(self) -> float:
        """
        获取经验效率 (经验/小时)
        使用公式估算：平均每场经验 * 2 / 平均一轮时长
        """
        avg_round_time = self.get_average_round_time()
        if avg_round_time > 0:
            exp_per_hour = (
                self.AVG_EXP_PER_BATTLE
                * self.BATTLES_PER_ROUND
                * 3600
                / avg_round_time
            )
            return round(exp_per_hour, 2)

        return 22000.0  # 默认值

    def get_today_stats(self):
        """获取今日统计数据"""
        today = date.today().isoformat()
        if 'daily_stats' not in self.data:
            return None
        return self.data['daily_stats'].get(today)

    # ========== 舰船进度计算（只读） ==========

    def calculate_progress(
        self,
        ship: dict,
        target_level: int,
        current_battle_count: int
    ) -> dict:
        """
        计算单艘舰船的升级进度

        Args:
            ship: 舰船数据 (position, level, current_exp, total_exp)
            target_level: 目标等级
            current_battle_count: 当前战斗场次

        Returns:
            进度数据字典
        """
        # 处理等级边界 (1-125)
        if target_level < 1:
            target_level = 1
        elif target_level > 125:
            target_level = 125

        target_exp = LIST_SHIP_EXP[target_level - 1]
        current_total_exp = ship.get('total_exp', 0)
        exp_needed = max(0, target_exp - current_total_exp)

        # 计算还需出击次数
        position = ship.get('position', 1)
        exp_per_battle = self.EXP_PER_BATTLE.get(position, 288)
        battles_needed = math.ceil(exp_needed / exp_per_battle) if exp_needed > 0 else 0

        # 计算已战斗场次 (自上次检测以来)
        battle_count_at_check = self.data.get('battle_count_at_check', 0)
        battles_done = max(0, current_battle_count - battle_count_at_check)

        # 计算预估时间 (经验值*2/平均一轮时长)
        avg_round_time = self.get_average_round_time()
        if avg_round_time > 0 and exp_needed > 0 and exp_per_battle > 0:
            ship_exp_per_hour = (
                exp_per_battle
                * self.BATTLES_PER_ROUND
                * 3600
                / avg_round_time
            )
            hours_needed = exp_needed / ship_exp_per_hour
            time_seconds = hours_needed * 3600
        else:
            time_seconds = 0

        return {
            'position': position,
            'level': ship.get('level', 0),
            'current_exp': ship.get('current_exp', 0),
            'total_exp': current_total_exp,
            'target_exp': target_exp,
            'battles_done': battles_done,
            'exp_needed': exp_needed,
            'battles_needed': battles_needed,
            'time_needed': self._format_time(time_seconds)
        }

    def get_all_progress(self, current_battle_count: int) -> list:
        """
        获取所有舰船的升级进度

        Args:
            current_battle_count: 当前战斗场次

        Returns:
            所有舰船的进度数据列表
        """
        ships = self.data.get('ships', [])
        target_level = self.data.get('target_level', 125)

        return [
            self.calculate_progress(ship, target_level, current_battle_count)
            for ship in ships
        ]

    @staticmethod
    def _format_time(seconds: float) -> str:
        """格式化时间显示"""
        if seconds <= 0:
            return "0分钟"

        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)

        if hours > 0:
            return f"{hours}小时{minutes}分钟"
        return f"{minutes}分钟"


# ─────────────────────────────────────────────────────────────
# 4. 每日经验检测
# ─────────────────────────────────────────────────────────────

def _instance_log_dir(instance: str) -> Path:
    """实例目录名可能和实例名不完全一致，找不到就退回扫描第一个存在的。"""
    direct = CL1_LOG_DIR / instance
    if direct.is_dir():
        return direct
    if CL1_LOG_DIR.is_dir():
        for child in sorted(CL1_LOG_DIR.iterdir()):
            if child.is_dir() and (child / "ship_exp_data.json").exists():
                return child
    return direct


def _ship_level_exp_table():
    """保留给需要原始表的场景；派生进度由上面的 ShipExpStatsLocal 计算。"""
    return None


def query_ship_exp(instance: str) -> dict:
    """每日经验检测。

    进度（距目标经验 / 还需出击 / 预计时间）与平均战斗/一轮时长、经验效率、
    今日统计，全部走本文件里的 `ShipExpStatsLocal`（= 项目
    `module.statistics.ship_exp_stats.get_ship_exp_stats()` 的只读移植版，
    见「3.5 舰船经验统计」），逐行照抄、纯 stdlib，不再 import 项目模块：

      * exe 里没有 AzurPilot 的 venv，import 必失败；
      * 早先自己按 LIST_SHIP_EXP 猜公式，105 级被算成「已完成」，和项目行为对不上。

    移植版与原实现的等价性由 bridge/verify_ship_exp_parity.py 逐字段 diff 保证。
    """
    path = _instance_log_dir(instance) / "ship_exp_data.json"
    if not path.exists():
        return {"available": False, "reason": f"还没有经验数据文件：{path}", "ships": []}

    # 算出来的值；None 表示"没拿到"，后面用 JSON 兜底
    avg_battle_project = None
    avg_round_project = None
    exp_per_hour_project = None
    today_stats_project = None

    try:
        stats = ShipExpStatsLocal(path)
        # 与原来的调用完全一致：原代码是
        #   getattr(stats, "battle_count_at_check", 0) or 0
        # 而 ShipExpStats 上并没有这个**属性**（battle_count_at_check 是 self.data
        # 里的键），所以那里恒为 0 —— 这里照抄同样的取值，保证结果一致。
        progress = stats.get_all_progress(getattr(stats, "battle_count_at_check", 0) or 0)
    except Exception as exc:
        return {"available": False, "reason": f"加载经验统计失败：{exc}", "ships": []}

    # 下面几个 getter 只读 self.data（_load 读文件），不写盘；单独 try 是为了
    # 万一某个 getter 出错，也只退化那一个字段，不至于整个接口挂掉
    try:
        avg_battle_project = float(stats.get_average_battle_time())
        avg_round_project = float(stats.get_average_round_time())
        exp_per_hour_project = float(stats.get_exp_per_hour())
        today_stats_project = stats.get_today_stats()
    except Exception:
        pass

    ships = []
    for row in progress or []:
        if not isinstance(row, dict):
            continue
        ships.append({
            "position": int(row.get("position", 0) or 0),
            "level": int(row.get("level", 0) or 0),
            "currentExp": int(row.get("current_exp", 0) or 0),
            "totalExp": int(row.get("total_exp", 0) or 0),
            "targetExp": int(row.get("target_exp", 0) or 0),
            "expNeeded": int(row.get("exp_needed", 0) or 0),
            "battlesNeeded": int(row.get("battles_needed", 0) or 0),
            "timeNeeded": str(row.get("time_needed", "")),
        })

    raw = _read_json(path)
    daily = raw.get("daily_stats") or {}
    today = datetime.now().strftime("%Y-%m-%d")
    out_daily = []
    for day, stat in sorted(daily.items(), reverse=True)[:14]:
        if not isinstance(stat, dict):
            continue
        out_daily.append({
            "date": day,
            "runTime": float(stat.get("total_run_time", 0) or 0),
            "expGained": float(stat.get("total_exp_gained", 0) or 0),
            "battleCount": int(stat.get("battle_count", 0) or 0),
            "expPerHour": float(stat.get("exp_per_hour", 0) or 0),
        })

    # 项目方法拿不到时退回旧读法：直接读 JSON 里预存的 average
    if avg_battle_project is None:
        avg_battle_project = float((raw.get("battle_times") or {}).get("average") or 0)
    if avg_round_project is None:
        avg_round_project = float((raw.get("round_times") or {}).get("average") or 0)

    today_stat = today_stats_project if isinstance(today_stats_project, dict) else None
    if today_stat is None:
        raw_today = daily.get(today)
        today_stat = raw_today if isinstance(raw_today, dict) else None

    if exp_per_hour_project is None:
        raw_today_exp_per_hour = (today_stat or {}).get("exp_per_hour")
        exp_per_hour_project = float(raw_today_exp_per_hour or 0)

    total_run_time = float((today_stat or {}).get("total_run_time", 0) or 0)

    return {
        "available": bool(ships),
        "lastCheckTime": raw.get("last_check_time", ""),
        "targetLevel": int(raw.get("target_level", 0) or 0),
        "fleetIndex": int(raw.get("fleet_index", 0) or 0),
        "battleCountAtCheck": int(raw.get("battle_count_at_check", 0) or 0),
        "avgBattleSeconds": avg_battle_project,
        "avgRoundSeconds": avg_round_project,
        "avgMeowBattleSeconds": float((raw.get("meow_battle_times") or {}).get("average") or 0),
        # 「侵蚀1 · 本月大世界」表格最后几列（PC: app_stat_opsi.py:236-265）
        "expPerHour": exp_per_hour_project,
        "todayBattleCount": int((today_stat or {}).get("battle_count", 0) or 0),
        "todayExp": int(float((today_stat or {}).get("total_exp_gained", 0) or 0)),
        # PC 就是 total_run_time // 60 取整（app_stat_opsi.py:248），没有今日数据给 0
        "todayRunMinutes": int(total_run_time // 60),
        "ships": ships,
        "daily": out_daily,
        "today": next((d for d in out_daily if d["date"] == today), None),
    }


def query_commission_income(instance: str, period: str = "month", limit: int = 10) -> dict:
    """委托收益统计。

    聚合逻辑在 bridge/commission_stats.py —— 刻意独立成文件，是为了能拿
    bridge/verify_commission_parity.py 和 AzurPilot 自己的
    module/statistics/commission_income_stats.py 逐字段对照（数字必须和
    PC 页面上的一模一样）。这里只负责把桥的全局路径喂给它。

    ⚠️ CL1_DB_PATH / ROOT 会被 configure_root() 重新赋值（「选目录」按钮会调它），
    所以必须在**函数体里**读全局，不能在模块顶层捕获成局部变量 —— 否则换完目录
    这个接口还在读旧库。
    """
    commission_stats.set_logger(log)
    return commission_stats.query(
        CL1_DB_PATH,
        instance,
        period=period,
        limit=limit,
        labels=commission_stats.load_labels(ROOT),
    )


# ─────────────────────────────────────────────────────────────
# 5. 概览页的调度队列（队列中 / 等待中）
#
# 对齐 PC 概览页的三段队列（骨架 app_overview.py:64-87，填充逻辑
# app_dashboard.py:35-58），分组与排序规则来自 config.py:312-342 `get_next_task()`：
#
#   pending（队列中）= 启用 且 next_run <  now（解析失败的 error 排在最前面）
#   waiting（等待中）= 启用 且 next_run >= now
#   running（运行中）= pending[:1]，**仅当 alas.alive**（app_dashboard.py:41-47）
#
# 桥**不返回** running：alive 只有 App 侧的 MCP get_status 知道，所以这里给的是
# 「还没被拿走的完整 pending」，App 自己按上面的规则切第一项。
#
# 关键：pending 不是按时间排的，而是按 SCHEDULER_PRIORITY 优先级表；只有 waiting 才
# 按 next_run 升序。优先级表是**运行时算出来的**（不在 alas.json 的某个字段里），
# 所以下面把 task_priority.py 的整套 parse/merge 逻辑和 config_manual.py 的默认表
# 都移植了过来 —— 桥不能 import 项目模块（会拉起项目自己的配置/日志/写盘逻辑），
# 只能复刻。移植时逐行对着原实现写，保持语义一致。
# ─────────────────────────────────────────────────────────────

# config_manual.py:53-80 `_DEFAULT_SCHEDULER_PRIORITY`（原样抄，缩进无所谓，
# parse_task_priority 会逐行 strip）。只有 args.json 里的默认值也拿不到时才用它。
_DEFAULT_SCHEDULER_PRIORITY = """
Restart
> OpsiCrossMonth
> Commission > Tactical > Research
> Exercise
> Dorm > Meowfficer > Guild > Gacha
> Reward
> ShopFrequent > EventShop > ShopOnce > Shipyard > Freebies
> PrivateQuarters
> OpsiExplore
> Minigame > Awaken
> OpsiAshBeacon
> OpsiDaily > OpsiShop > OpsiVoucher
> OpsiScheduling
> OpsiAbyssal > OpsiStronghold > OpsiObscure > OpsiArchive
> Daily > Hard > OpsiAshBeacon > OpsiAshAssist > OpsiMonthBoss
> Sos > EventSp > EventA > EventB > EventC > EventD
> RaidDaily > CoalitionSp > WarArchives > MaritimeEscort
> IslandJuuEatery > IslandJuuCoffee > IslandGrill > IslandTeahouse > IslandRestaurant
> IslandFarm > IslandRancher > IslandMineForest > IslandDailyGather > IslandManufacture
> IslandAirDrop > IslandBusiness
> Event > Event2 > Event3 > Raid > Hospital > HospitalEvent > Coalition > CoalitionScuttle > RaidScuttle > Main > Main2 > Main3
> OpsiMeowfficerFarming
> GemsFarming
> Ambush11
> OpsiHazard1Leveling
> ThreeOilLowCost
"""

# task_priority.py:12
PRIORITY_SEPARATOR = "\n> "

# 实例名会拼进文件名（config/<实例>.json），挡一下路径穿越
INSTANCE_NAME_RE = re.compile(r"^[A-Za-z0-9_\-]+$")


def _deep_get(data, keys, default=None):
    """module/config/deep.py:22 `deep_get` 的等价实现（也支持点分字符串路径）。"""
    if isinstance(keys, str):
        keys = keys.split(".")
    try:
        for key in keys:
            data = data[key]
        return data
    except (KeyError, IndexError, TypeError):
        return default


def _deep_iter_depth3(data):
    """module/config/deep.py:289 `deep_iter(data, depth=3)` 的等价实现。

    原实现只把 dict 放进队列，所以产出的一定是「三层祖先都是 dict」的
    [k1, k2, k3] 路径；这里直接三层循环，语义一致。
    """
    if not isinstance(data, dict):
        return
    for k1, v1 in data.items():
        if not isinstance(v1, dict):
            continue
        for k2, v2 in v1.items():
            if not isinstance(v2, dict):
                continue
            for k3, v3 in v2.items():
                yield [k1, k2, k3], v3


def parse_task_priority(value) -> list:
    """task_priority.py:15 `parse_task_priority`：优先级文本 → 去重后的任务名列表。"""
    if not value:
        return []

    text = str(value)
    text = re.sub(r"[＞﹥›˃ᐳ❯]", ">", text)
    tasks = []
    seen = set()
    for raw_line in text.splitlines():
        line = raw_line.split("#", 1)[0].strip()
        if not line:
            continue
        for raw_task in line.split(">"):
            task = raw_task.strip()
            if not task or task in seen:
                continue
            seen.add(task)
            tasks.append(task)
    return tasks


def format_task_priority(tasks) -> str:
    """task_priority.py:37 `format_task_priority`：任务名列表 → 优先级文本。"""
    return PRIORITY_SEPARATOR.join(str(task).strip() for task in tasks if str(task).strip())


def get_scheduler_tasks(args: dict) -> list:
    """task_priority.py:47 `get_scheduler_tasks`：从 args.json 里挑出参与调度的任务。

    判据是路径 `<任务>.Scheduler.Command` 的 value。
    """
    tasks = []
    for path, data in _deep_iter_depth3(args):
        if path[-2:] != ["Scheduler", "Command"]:
            continue
        if not isinstance(data, dict):
            continue
        command = data.get("value")
        if isinstance(command, str) and command and command not in tasks:
            tasks.append(command)
    return tasks


def _insert_by_default_neighbors(ordered: list, task: str, default_order: list) -> None:
    """task_priority.py:61：把不在表里的任务插到「默认表里左右邻居」之间。"""
    if task in ordered:
        return

    try:
        default_index = default_order.index(task)
    except ValueError:
        ordered.append(task)
        return

    prev_task = None
    for candidate in reversed(default_order[:default_index]):
        if candidate in ordered:
            prev_task = candidate
            break

    next_task = None
    for candidate in default_order[default_index + 1:]:
        if candidate in ordered:
            next_task = candidate
            break

    if prev_task is not None:
        ordered.insert(ordered.index(prev_task) + 1, task)
    elif next_task is not None:
        ordered.insert(ordered.index(next_task), task)
    else:
        ordered.append(task)


def merge_task_priority(current, default, available_tasks=None) -> str:
    """task_priority.py:91 `merge_task_priority`：用户优先级 + 默认优先级 + 可用任务集。

    规则（原样）：
      1. 用户优先级里**可用**的任务先按用户顺序排（这一步保证用户自定义串优先）；
      2. 默认表里有、可用、但还没进表的任务，按默认表里的邻居位置插进去；
      3. args.json 里可用的其余任务同样按默认表邻居位置插入。
    """
    default_order = parse_task_priority(default)
    available = list(available_tasks or default_order)
    available_set = set(available)

    current_order = [
        task
        for task in parse_task_priority(current)
        if task in available_set
    ]
    ordered = list(dict.fromkeys(current_order))

    default_available = [task for task in default_order if task in available_set]
    for task in default_available:
        if task not in ordered:
            _insert_by_default_neighbors(ordered, task, default_available)

    for task in available:
        if task not in ordered:
            _insert_by_default_neighbors(ordered, task, default_available)

    return format_task_priority(ordered)


def _scheduler_priority(config_data: dict) -> str:
    """复刻 config_manual.py:107-136 的 `SCHEDULER_PRIORITY` property。

    取值顺序和 PC 完全一致：
      1. 用户自定义值 = alas.json 的 `General.YukikazeTaskManager.TaskPriorityAdjustment`。
         PC 先试 `cross_get(["YukikazeTaskManager", "TaskPriorityAdjustment"])`，那是从
         顶层开始找的路径（alas.json 顶层没有 YukikazeTaskManager），取到 None；
         再退回绑定的生成配置属性 `YukikazeTaskManager_TaskPriorityAdjustment`
         （utils.py:path_to_arg 把 `YukikazeTaskManager.TaskPriorityAdjustment` 映射成
         这个名字），实际就是 General 下的这一项。所以这里直接读该路径。
      2. 默认值 = args.json 的 `General.YukikazeTaskManager.TaskPriorityAdjustment.value`；
      3. 兜底 = 内置 `_DEFAULT_SCHEDULER_PRIORITY`；
      4. 可用任务集 = `get_scheduler_tasks(args)`，参与合并。
    """
    task_adj = _deep_get(
        config_data, ["General", "YukikazeTaskManager", "TaskPriorityAdjustment"], None
    )

    try:
        args = _read_json(ARGS_PATH)
        default_priority = _deep_get(
            args,
            "General.YukikazeTaskManager.TaskPriorityAdjustment.value",
            _DEFAULT_SCHEDULER_PRIORITY,
        )
        available_tasks = get_scheduler_tasks(args)
    except Exception:
        # 和 PC 的 except 分支一致：退回内置表、可用任务集为空
        default_priority = _DEFAULT_SCHEDULER_PRIORITY
        available_tasks = None

    return merge_task_priority(task_adj, default_priority, available_tasks)


def _priority_index(priority: str) -> dict:
    """优先级文本 → {小写任务名: 序号}，用于复刻 PC 的 Filter 排序。

    PC 是 `Filter(regex=r"(.*)", attr=["command"]).load(priority).apply(pending)`
    （config.py:331-334，filter.py:29/45/94）：
      - `load` 删掉所有空白（含换行）、把各种 Unicode `>` 归一化，再按 `>` 切开，
        每个片段小写化后当成一个「过滤器」；
      - `apply` **按过滤器顺序**逐个扫描任务列表，命中就追加，同一个过滤器内保持
        原列表顺序；
      - **不在过滤器里的任务直接被丢掉**（apply 不会把未命中的补到末尾）。
    所以等价于「按 序号 稳定排序 + 丢弃未命中」，重复项取首次出现的位置。
    """
    index = {}
    for position, task in enumerate(parse_task_priority(priority)):
        index.setdefault(task.lower(), position)
    return index


def _apply_priority(tasks: list, index: dict) -> list:
    """`Filter.apply()` 的等价实现：按优先级顺序重排，未命中的丢弃。

    list.sort 是稳定排序，所以同一个优先级条目下的多个任务会保持原来的相对顺序，
    和 apply 里「顺着原列表扫」的结果一致。
    """
    matched = [item for item in tasks if item["command"].lower() in index]
    matched.sort(key=lambda item: index[item["command"].lower()])
    return matched


def _parse_next_run(value):
    """`Scheduler.NextRun` → datetime（本地时区，naive）；解析不了返回 None。

    PC 走的是 module/config/utils.py:257 的 `datetime.fromisoformat`（先试 int/float
    再试 fromisoformat），所以这里也用 fromisoformat，而不是自己 strptime：
    行为一致，而且**按本地时间解析**，不做 UTC 转换、也不按字符串比较 ——
    alas.json 里是 "YYYY-MM-DD HH:MM:SS"，字符串比较在跨月/跨年时才会碰巧正确。
    """
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value)
        except ValueError:
            return None
    return None


def query_overview_tasks(instance: str) -> dict:
    """PC 概览页的调度队列：队列中（pending）+ 等待中（waiting）。

    逐步对齐 config.py:312-342 `get_next_task()`：
      - 只遍历有 `Scheduler` 分组的顶层任务，`Scheduler.Enable` 为假直接跳过；
      - `Scheduler.NextRun` 不是合法时间 → 归入 error（PC 的 error 分支），
        最终 **拼接在 pending 最前面**（`pending = error + pending`）；
      - `next_run < now` 是**严格小于**；等于 now 属于 waiting；
      - pending 按 SCHEDULER_PRIORITY 排序，waiting 先过同一套过滤器再按 next_run 升序。
    注意 `Alas.Optimization.TaskHoardingDuration` 目前是 0，PC 的 `now -= hoarding`
    是空操作，所以这里不加偏移；如果将来调大这个值，桥需要跟着改（见回报）。
    """
    if not INSTANCE_NAME_RE.fullmatch(instance or ""):
        raise ValueError(f"实例名不合法：{instance!r}")

    config_path = CONFIG_DIR / f"{instance}.json"
    if not config_path.exists():
        raise FileNotFoundError(f"找不到实例配置：{config_path}")

    config_data = _read_json(config_path)
    if not isinstance(config_data, dict):
        raise ValueError(f"实例配置不是 JSON 对象：{config_path}")

    i18n = _read_json(I18N_PATH) if I18N_PATH.exists() else {}
    task_names = i18n.get("Task", {}) if isinstance(i18n, dict) else {}

    priority = _scheduler_priority(config_data)
    index = _priority_index(priority)

    now = datetime.now()
    pending, waiting, error = [], [], []

    for task_key, node in config_data.items():
        if not isinstance(node, dict):
            continue
        scheduler = node.get("Scheduler")
        if not isinstance(scheduler, dict):
            continue
        if not scheduler.get("Enable"):
            continue

        command = scheduler.get("Command")
        if not isinstance(command, str) or not command:
            # PC 的 Function.command 这时是 "Unknown"（进不了优先级表 → 被丢弃）；
            # 桥退回顶层键，实际数据里 Scheduler.Command 总是有的（args.json 里
            # 它是 display=hide 的生成字段，每个任务都带）
            command = task_key

        raw_next = scheduler.get("NextRun")
        next_run = _parse_next_run(raw_next)
        if next_run is None and (raw_next is None or (isinstance(raw_next, str) and not raw_next.strip())):
            # PC 的 config_update 会用 args.json 的默认值 "2020-01-01 00:00:00" 补上
            # 缺失/空值（config_updater.py:731-737 parse_value），所以这种不是 error
            # 分支，而是一个很久以前的 next_run → 走优先级排序进 pending
            next_run = datetime(2020, 1, 1, 0, 0)

        item = {"command": command, "raw": raw_next, "next_run": next_run}
        if next_run is None:
            error.append(item)          # PC: `not isinstance(func.next_run, datetime)`
        elif next_run < now:
            pending.append(item)
        else:
            waiting.append(item)

    pending = _apply_priority(pending, index)
    if waiting:
        waiting = _apply_priority(waiting, index)
        waiting.sort(key=lambda item: item["next_run"])
    if error:
        pending = error + pending       # PC: error 排在最前面，且不参与优先级排序

    all_items = error + pending + waiting
    unmatched = sorted({
        item["command"] for item in all_items if item["command"].lower() not in index
    })

    def _render(items):
        out = []
        for item in items:
            meta = task_names.get(item["command"])
            name = meta.get("name") if isinstance(meta, dict) else None
            next_run = item["next_run"]
            out.append({
                "task": item["command"],
                # PC 页面上显示的是 str(func.next_run)（app_dashboard.py:67），所以这里
                # 也规范化成同一种格式，而不是回显文件里的原始字符串
                "nextRun": next_run.strftime("%Y-%m-%d %H:%M:%S")
                if isinstance(next_run, datetime) else str(item["raw"]),
                "name": name if isinstance(name, str) and name else item["command"],
            })
        return out

    return {
        "instance": instance,
        "now": now.strftime("%Y-%m-%d %H:%M:%S"),
        # 完整 pending（含 PC 会当成「运行中」的第一项），App 按 alive 自己切
        "pending": _render(pending),
        "waiting": _render(waiting),
        # 排序依据，排查用
        "priority": priority,
        # 启用了但不在优先级表里、因而被 PC 丢弃的任务（正常应为空）
        "unmatched": unmatched,
    }


# ─────────────────────────────────────────────────────────────
# 6. 任务配置的中文结构
#
# 为什么不让 App 直接用 MCP 的 get_task_help：
#   module/config/mcp_helper.py:63 查的是 i18n[task_name][group][arg]，
#   但 i18n 的实际布局是 i18n[group][arg]（分组名在顶层，没有再按任务嵌一层）。
#   结果 i18n["Guild"] 取到 None，**所有参数名都退回英文键**
#   （Scheduler / Enable / PushNotification …），而 WebUI 显示的是
#   任务设置 / 启用该功能 / 推送通知。
#   这是上游 helper 的 bug，我们不改服务端，改在桥里自己做这个 join。
# ─────────────────────────────────────────────────────────────

def _localized_label(i18n_node, key: str, fallback: str) -> str:
    if isinstance(i18n_node, dict):
        value = i18n_node.get(key)
        if isinstance(value, str) and value:
            return value
    return fallback


def query_task_schema(task: str) -> dict:
    """把 args.json 的结构和 i18n 的中文名合并成一份可直接渲染的配置结构。"""
    if not ARGS_PATH.exists():
        raise FileNotFoundError(f"找不到 args.json：{ARGS_PATH}")

    args_all = _read_json(ARGS_PATH)
    i18n = _read_json(I18N_PATH) if I18N_PATH.exists() else {}

    if task not in args_all:
        raise KeyError(f"args.json 里没有任务 {task}")

    task_meta = i18n.get("Task", {}).get(task, {})
    groups_out = []

    for group_key, group_args in args_all[task].items():
        if group_key == "Storage" or not isinstance(group_args, dict):
            continue

        group_i18n = i18n.get(group_key, {}) or {}
        info = group_i18n.get("_info", {}) if isinstance(group_i18n, dict) else {}
        group_name = _localized_label(info, "name", group_key)
        group_help = _localized_label(info, "help", "")

        args_out = []
        for arg_key, arg_meta in group_args.items():
            if not isinstance(arg_meta, dict):
                continue
            # WebUI 会把 display=hide 的参数藏起来（Command / SuccessInterval /
            # FailureInterval 这些），这里保持一致
            if str(arg_meta.get("display", "")).lower() == "hide":
                continue

            arg_i18n = group_i18n.get(arg_key, {}) if isinstance(group_i18n, dict) else {}
            if not isinstance(arg_i18n, dict):
                arg_i18n = {}

            options_out = {}
            for opt in arg_meta.get("option") or []:
                # args.json 里的布尔选项是 true/false，i18n 的键是 "True"/"False"
                key = "True" if opt is True else "False" if opt is False else str(opt)
                options_out[key] = _localized_label(arg_i18n, key, key)

            args_out.append({
                "key": arg_key,
                "name": _localized_label(arg_i18n, "name", arg_key),
                "help": _localized_label(arg_i18n, "help", ""),
                "type": arg_meta.get("type", "input"),
                "default": arg_meta.get("value"),
                "options": options_out or None,
            })

        if args_out:
            groups_out.append({
                "key": group_key,
                "name": group_name,
                "help": group_help,
                "args": args_out,
            })

    return {
        "task": task,
        "displayName": task_meta.get("name", task),
        "help": task_meta.get("help", ""),
        "groups": groups_out,
    }


# ─────────────────────────────────────────────────────────────
# 7. 增量日志尾巴（手机端日志页的「准实时」流）
#
# 为什么不继续用 MCP 的 get_recent_logs：
#   `mcp_server_sse.py:250-273` 每次都 `readlines()` **整读**日志文件再取尾部 N 行
#   （实测 log/<今天>_alas.txt = 16.2 MB / 12.4 万行）。30 秒一次已经是每轮全量读盘，
#   要做到 PC 那样的实时就是每秒读 16 MB。PC 的日志面板是内存里的 Rich 对象走
#   PyWebIO 会话通道（0.25 秒级），**没有任何可复用的 HTTP/WS 接口**，
#   所以只能在这边按**字节 offset** 做增量。
#
# 语义（服务端**无状态**，offset 由客户端携带 —— App 重启、切后台、换设备都不用对账）：
#   offset 不传 / <= 0 → 「首次·重置」：返回最后 tail_lines **行**，新 offset = 文件尾
#   offset > 0         → 从该**字节位置**读到末尾，只返回新增内容 + 新 offset
#   offset > 文件大小  → 文件被轮转/清空（日志按天换名，每天 0 点新建）→ reset=true
#                        并直接把新文件的尾部给出去，客户端整体替换，不用多跑一趟
#
# 四个必须这么做的细节（都是坑）：
#   1. `open(path, "rb")` + `seek(offset)`：文件是 UTF-8，按**字符**偏移 seek 会错位，
#      而且只有二进制读才能保证 offset 就是字节位置；
#   2. 只交付**最后一个 \n 之前**的完整行：日志正在被写时尾巴上会有半行，把它留给
#      下一次（offset 只前进到最后一个 \n 之后）—— 否则那半行会和它后面补上的内容
#      一起被重复返回，UTF-8 多字节字符还会被劈成乱码；
#      ⚠️ 这个日志文件是 **CRLF**（Rich 的 FileHandler 走 Windows 文本模式），
#         所以切完行还要 rstrip 掉 \r；
#   3. 空行**原样保留**：日志里混着面板的 hr-group 空白行，过滤掉会让行号对不上，
#      客户端自己会处理；
#   4. `max_bytes` 撞顶时 `truncated=true`，客户端会立刻再拉一次；**offset 必须前进**，
#      否则客户端会拿着同一个 offset 死循环（超长行是这个分支唯一的入口）。
#
# 清洗下沉到桥（原来在 App 的 `Models.kt::cleanLogLine` 里做），所有客户端一起受益：
#   - 去行尾空白：Rich 把每行补齐到 119 列
#   - `np.int64(281)` / `np.float64(1.0)` 这类 numpy 泄漏还原成字面量
# ─────────────────────────────────────────────────────────────

DEFAULT_TAIL_LINES = 400          # 首次/重置时给多少行
MAX_TAIL_LINES = 5000
DEFAULT_LOG_MAX_BYTES = 65536     # 单次增量最多读多少字节
MAX_LOG_MAX_BYTES = 1048576
MIN_LOG_MAX_BYTES = 1024

# 扫尾部时分块往前读的大小 / 总量上限：5000 行 × ~120 字节 ≈ 600 KB，4 MB 足够，
# 免得一个「行超长」的日志把「只读尾部」退化成整读文件
LOG_TAIL_CHUNK = 128 * 1024
LOG_TAIL_SCAN_LIMIT = 4 * 1024 * 1024

# 抄 App 的 NP_WRAPPER（Models.kt:303），两边保持一致
NP_WRAPPER_RE = re.compile(r"np\.(?:int64|int32|float64|float32|bool_|str_|uint8)\(([^()]*)\)")


def _clean_log_line(line: str) -> str:
    """行清洗：去行尾空白（含 CRLF 的 \\r）+ 还原 numpy 泄漏。"""
    return NP_WRAPPER_RE.sub(r"\1", line.rstrip())


def _split_log_bytes(blob: bytes) -> list:
    """按 \\n 切字节，再逐行解码。

    先按字节切再 decode 是安全的：UTF-8 的多字节序列里不会出现 0x0A，所以每个片段
    都落在字符边界上。末尾的空片段是最后一行的换行符，不是一行（丢掉）。
    """
    pieces = blob.split(b"\n")
    if pieces and pieces[-1] == b"":
        pieces.pop()
    return [_clean_log_line(piece.decode("utf-8", errors="replace")) for piece in pieces]


def _scan_tail(path: Path, tail_lines: int) -> tuple:
    """从文件末尾**往前分块**扫，凑够 tail_lines 行就停 —— 不整读文件。

    返回 `(lines, size, offset)`：
      lines  = 最后 tail_lines 个完整行（已清洗，空行保留）
      size   = 本次看到的文件大小
      offset = 下次续读的字节位置 = 最后一个 \\n 之后；正常就等于 size，
               文件正写到一半时会 < size（那个半行留给下一次）
    """
    with open(path, "rb") as fh:
        fh.seek(0, os.SEEK_END)
        size = fh.tell()

        pos = size
        blob = b""
        # 多凑一个换行：文件没读到头时，开头那截是被切开的半行，要丢掉
        while pos > 0 and blob.count(b"\n") <= tail_lines and size - pos < LOG_TAIL_SCAN_LIMIT:
            step = min(LOG_TAIL_CHUNK, pos)
            pos -= step
            fh.seek(pos)
            blob = fh.read(step) + blob

    # blob 覆盖文件的 [start, size)。没扫到文件头时开头那截是被切开的半行 → 丢掉；
    # ⚠️ 丢完必须同步挪 start —— 后面的 offset 全从 start 算，否则 offset 会短几十字节，
    #    下一次增量就把最后一行的一部分**重复**返回（实测踩到过：offset 比文件小 76 字节，
    #    紧接着的那次增量吐出一截断行）。
    scan_start = pos
    start = pos
    if start > 0 and blob:
        cut = blob.find(b"\n")
        dropped = cut + 1 if cut >= 0 else len(blob)
        blob = blob[dropped:]
        start += dropped

    if not blob:
        # 扫描范围内一个完整行都没有（超长单行）→ 退回扫描起点，等它写完再整行给
        return [], size, scan_start
    if blob.endswith(b"\n"):
        # 干净结尾：blob 全是完整行，续读点就是 blob 末尾（正常 == 文件大小）
        return _split_log_bytes(blob)[-tail_lines:], size, start + len(blob)

    # 尾巴上是半行：不进 lines，也不进 offset
    cut = blob.rfind(b"\n")
    if cut < 0:
        return [], size, start
    return _split_log_bytes(blob[:cut + 1])[-tail_lines:], size, start + cut + 1


def _utf8_complete_len(blob: bytes) -> int:
    """blob 去掉末尾被截断的多字节序列后的长度（保证切在字符边界上）。

    只有「一行比 max_bytes 还长」这条退化路径用得到：正常路径都在 \\n 处切，
    而 0x0A 不可能出现在 UTF-8 多字节序列内部，所以那里天然是字符边界。
    """
    for back in range(1, min(4, len(blob)) + 1):
        byte = blob[-back]
        if byte < 0x80:
            return len(blob)                  # 末尾是 ASCII → 完整
        if byte >= 0xC0:                      # 找到多字节的首字节
            need = 2 if byte < 0xE0 else 3 if byte < 0xF0 else 4
            return len(blob) if back >= need else len(blob) - back
    return len(blob)                          # 4 个字节全是续接字节（数据本身坏了）


def _read_increment(path: Path, offset: int, max_bytes: int) -> tuple:
    """从 offset 读到文件末尾（最多 max_bytes 字节），只交付完整行。

    返回 `(lines, size, offset, truncated)`：
      - 撞到 max_bytes（后面还有数据）→ truncated=True，客户端会立刻再拉一次；
      - 尾巴上的半行留在下一次：新 offset 只到最后一个 \\n 之后。
    """
    with open(path, "rb") as fh:
        fh.seek(offset)
        blob = fh.read(max_bytes)
        size = fh.seek(0, os.SEEK_END)

    truncated = offset + len(blob) < size

    if blob.endswith(b"\n"):
        return _split_log_bytes(blob), size, offset + len(blob), truncated

    cut = blob.rfind(b"\n")
    if cut >= 0:
        return _split_log_bytes(blob[:cut + 1]), size, offset + cut + 1, truncated

    if truncated:
        # 单行比 max_bytes 还长：offset 必须往前走，否则客户端会拿着同一个位置死循环；
        # 但只交付到字符边界，剩下的字节留给下一次，免得把多字节字符劈成乱码
        safe = _utf8_complete_len(blob)
        return _split_log_bytes(blob[:safe]), size, offset + safe, truncated

    # 读到末尾但整段是半行 → 一行都不给，offset 不动，等它写完
    return [], size, offset, truncated


def _log_file_for(instance: str):
    """今天的日志文件；找不到就退回 <今天>_alas.txt（抄 mcp_server_sse.py:254-262）。"""
    today = datetime.now().strftime("%Y-%m-%d")
    direct = LOG_DIR / f"{today}_{instance}.txt"
    if direct.is_file():
        return direct
    fallback = LOG_DIR / f"{today}_{DEFAULT_INSTANCE}.txt"
    return fallback if fallback.is_file() else None


def query_log_tail(instance: str, offset: int, tail_lines: int, max_bytes: int) -> dict:
    """增量日志尾巴 —— 语义和坑见上面 # 7 的说明。

    文件不存在时返回全空结构（App 拿不到日志不该是个错误）；文件被轮转时 reset=true。
    """
    if not INSTANCE_NAME_RE.fullmatch(instance or ""):
        raise ValueError(f"实例名不合法：{instance!r}")

    path = _log_file_for(instance)
    if path is None:
        return {
            "file": "", "size": 0, "offset": 0,
            "reset": False, "truncated": False, "lines": [],
        }

    # 客户端只认相对路径（log/<今天>_<实例>.txt），顺带用来判断文件有没有换
    file_label = path.relative_to(ROOT).as_posix()

    if offset <= 0:
        # 首次 / 客户端主动重置：给最后 tail_lines 行，offset 交给客户端
        lines, size, new_offset = _scan_tail(path, tail_lines)
        return {
            "file": file_label, "size": size, "offset": new_offset,
            "reset": False, "truncated": False, "lines": lines,
        }

    if path.stat().st_size < offset:
        # 文件比客户端手里的 offset 还小 = 换天/被清空（日志按天换名），offset 已失效。
        # reset=true 让客户端整体替换，所以这里直接把新文件的尾部一起给出去。
        lines, size, new_offset = _scan_tail(path, tail_lines)
        return {
            "file": file_label, "size": size, "offset": new_offset,
            "reset": True, "truncated": False, "lines": lines,
        }

    lines, size, new_offset, truncated = _read_increment(path, offset, max_bytes)
    return {
        "file": file_label, "size": size, "offset": new_offset,
        "reset": False, "truncated": truncated, "lines": lines,
    }


# ─────────────────────────────────────────────────────────────
# HTTP
# ─────────────────────────────────────────────────────────────

ROUTES = {
    "/api/resource_history": lambda q: query_resource_history(
        (q.get("instance") or [DEFAULT_INSTANCE])[0],
        # ★ 必须走 _int_param 而不是裸 int()：`?hours=abc` 会抛 ValueError，
        #   被上层兜成 HTTP 500。这个接口是给手机用的，但桥一旦公开，
        #   任何手抖的参数都不该让整个请求炸掉 —— 退回默认值就够了。
        _clamp(_int_param(q, "hours", DEFAULT_HOURS), 1, 24 * 90),
        _clamp(_int_param(q, "buckets", DEFAULT_BUCKETS), 1, MAX_BUCKETS),
    ),
    "/api/task_tree": lambda q: query_task_tree(),
    "/api/meow_stats": lambda q: query_meow_stats((q.get("instance") or [DEFAULT_INSTANCE])[0]),
    "/api/meow_hazard": lambda q: query_meow_hazard((q.get("instance") or [DEFAULT_INSTANCE])[0]),
    "/api/ship_exp": lambda q: query_ship_exp((q.get("instance") or [DEFAULT_INSTANCE])[0]),
    "/api/commission_income": lambda q: query_commission_income(
        (q.get("instance") or [DEFAULT_INSTANCE])[0],
        (q.get("period") or ["month"])[0],
        _clamp(_int_param(q, "limit", 10), 0, 200),
    ),
    "/api/task_schema": lambda q: query_task_schema((q.get("task") or [""])[0]),
    "/api/overview_tasks": lambda q: query_overview_tasks(
        (q.get("instance") or [DEFAULT_INSTANCE])[0]
    ),
    "/api/logs/tail": lambda q: query_log_tail(
        (q.get("instance") or [DEFAULT_INSTANCE])[0],
        _int_param(q, "offset", 0),
        _clamp(_int_param(q, "tail_lines", DEFAULT_TAIL_LINES), 1, MAX_TAIL_LINES),
        _clamp(
            _int_param(q, "max_bytes", DEFAULT_LOG_MAX_BYTES),
            MIN_LOG_MAX_BYTES, MAX_LOG_MAX_BYTES,
        ),
    ),
}


# ─────────────────────────────────────────────────────────────
# 网关：自己的密码 / 上游配置 / 鉴权 / 状态页
# ─────────────────────────────────────────────────────────────

#: 网关自己的密码。模块加载后由 main() 用 load_or_create_gateway_key() 灌进来。
GATEWAY_KEY = ""


def load_or_create_gateway_key() -> str:
    """读网关密码；没有就生成一个 32 位的写下来。

    和 AzurPilot 生成 `password.txt` 是同一个思路：用户不必自己发明密码，
    但必须**拿得到**它 —— 所以窗口里会显示出来，可选中复制。
    """
    try:
        existing = GATEWAY_KEY_PATH.read_text(encoding="utf-8").strip()
    except OSError:
        existing = ""
    if existing:
        return existing

    alphabet = string.ascii_letters + string.digits
    key = "".join(secrets.choice(alphabet) for _ in range(32))
    try:
        GATEWAY_KEY_PATH.write_text(key + "\n", encoding="utf-8")
    except OSError as exc:
        # 写不下来（只读目录 / 权限）也不该让网关起不来：用内存里这一把，
        # 只是下次启动会换一把。窗口里那句提示会说清楚。
        log(f"[!] 网关密码写入失败（{exc}），本次使用临时密码")
    return key


# deploy.yaml 里只要两个值：WebuiPort 和 Password。
# 刻意不引 yaml —— 本文件是纯标准库（要打包成 exe），而这两个都是单行标量，
# 正则足够；注释行以 # 开头，不会被下面这两个表达式命中。
_DEPLOY_PORT_RE = re.compile(r"^\s*WebuiPort:\s*(\d+)", re.M)
_DEPLOY_PASSWORD_RE = re.compile(r"^\s*Password:\s*(\S.*?)\s*$", re.M)

_deploy_cache = {"mtime": None, "port": 25548, "password": ""}


def _parse_deploy(path) -> tuple:
    """从 deploy.yaml 里抠出 (WebuiPort, Password)。"""
    try:
        text = Path(path).read_text(encoding="utf-8", errors="replace")
    except OSError:
        return 25548, ""

    port = 25548
    hit = _DEPLOY_PORT_RE.search(text)
    if hit:
        try:
            port = int(hit.group(1))
        except ValueError:
            port = 25548

    password = ""
    hit = _DEPLOY_PASSWORD_RE.search(text)
    if hit:
        password = hit.group(1).strip()
        if len(password) >= 2 and password[0] == password[-1] and password[0] in "\"'":
            password = password[1:-1]          # Password: "xxx"
        else:
            password = password.split(" #", 1)[0].strip()   # 行尾注释

    # deploy.yaml 没写密码、但 AzurPilot 监听公网时，它会自动生成一个写进
    # 根目录的 password.txt。取值顺序与 module/webui/mcp_auth.py 保持一致。
    if not password:
        try:
            password = (ROOT / "password.txt").read_text(encoding="utf-8").strip()
        except OSError:
            password = ""

    return port, password


def deploy_config() -> tuple:
    """(WebuiPort, Password)，按 mtime 缓存。

    缓存是为了别每个请求都读一次文件；带 mtime 是为了用户改了 deploy.yaml
    并重启 AzurPilot 之后，网关**不用重启**也能跟上。
    """
    path = CONFIG_DIR / "deploy.yaml"
    try:
        mtime = path.stat().st_mtime
    except OSError:
        mtime = None
    if _deploy_cache["mtime"] != mtime:
        port, password = _parse_deploy(path)
        _deploy_cache.update(mtime=mtime, port=port, password=password)
    return _deploy_cache["port"], _deploy_cache["password"]


def _extract_credential(handler) -> str:
    """从请求头或查询参数里取候选凭据。

    三种传法与 AzurPilot 的 `mcp_auth.extract_credential()` 一致，这样同一套
    客户端习惯两边通用；`?key=` 那条是留给"只能填 URL 的客户端"的。
    """
    auth = handler.headers.get("Authorization") or ""
    if auth[:7].lower() == "bearer ":
        candidate = auth[7:].strip()
        if candidate:
            return candidate
    header_key = (handler.headers.get("X-API-Key") or "").strip()
    if header_key:
        return header_key
    query = parse_qs(urlparse(handler.path).query)
    for name in QUERY_KEY_NAMES:
        values = query.get(name)
        if values and values[0]:
            return values[0]
    return ""


def _authorized(handler) -> bool:
    """常数时间比较，避免用响应时间逐位试出密码。

    用 bytes 比而不是 str：`hmac.compare_digest` 对 str 只接受 ASCII，
    密码里出现中文会直接抛 TypeError。
    """
    candidate = _extract_credential(handler)
    if not candidate or not GATEWAY_KEY:
        return False
    return hmac.compare_digest(
        candidate.encode("utf-8"), GATEWAY_KEY.encode("utf-8")
    )


def _probe_upstream() -> tuple:
    """探一次 AzurPilot，给状态页用。返回 (状态码, 标题, 说明)。

    分三种失败，因为**用户要做的事完全不同**：
      ap_down  → 去网关窗口点「启动 AzurPilot」
      key_bad  → 密码不一致，重启 AzurPilot 同步
      error    → 其它，看说明
    混成一句"连接失败"的话，用户只能干瞪眼。
    """
    port, password = deploy_config()

    probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    probe.settimeout(2.0)
    try:
        if probe.connect_ex(("127.0.0.1", port)) != 0:
            return (
                "ap_down",
                "没连上 AzurPilot",
                f"本机 {port} 端口没有响应。请在电脑上的网关窗口点「启动 AzurPilot」。",
            )
    finally:
        probe.close()

    request = urllib.request.Request(f"http://127.0.0.1:{port}/api/cl1_stats")
    if password:
        request.add_header("Authorization", f"Bearer {password}")
    try:
        with urllib.request.urlopen(request, timeout=4.0) as resp:
            if resp.status == 200:
                return ("ok", "已连接 AzurPilot", "请在 App 上查看数据。")
            return ("error", "AzurPilot 返回了意外状态", f"HTTP {resp.status}")
    except urllib.error.HTTPError as exc:
        if exc.code == 401:
            return (
                "key_bad",
                "AzurPilot 拒了密码",
                "config\\deploy.yaml 里的 Password 与运行中的 AzurPilot 不一致。"
                "重启一次 AzurPilot 让两边同步即可。",
            )
        return ("error", "AzurPilot 报错", f"HTTP {exc.code}")
    except Exception as exc:
        return ("error", "探测 AzurPilot 失败", f"{type(exc).__name__}: {exc}")


#: 状态 → 圆点颜色 / 角标
_STATUS_LOOK = {
    "ok": ("#28C840", "运行中"),
    "ap_down": ("#FF9F0A", "未连接"),
    "key_bad": ("#FF3B30", "鉴权失败"),
    "error": ("#FF3B30", "出错"),
}

# 刻意不用任何外部资源（字体 / CDN）：这一页要在内网、公网、手机浏览器上
# 都秒开，断网时也得显示得出来。所以样式和内联字体栈都写死在里面。
_STATUS_TEMPLATE = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title>AzurRem 网关</title>
<style>
  html,body{margin:0;height:100%}
  body{display:grid;place-items:center;background:#F2F2F7;color:#1D1D1F;
       font:16px/1.6 -apple-system,"Microsoft YaHei UI","PingFang SC",sans-serif}
  .card{background:#fff;border-radius:18px;padding:30px 30px 26px;max-width:420px;
        margin:20px;box-shadow:0 1px 3px rgba(0,0,0,.06)}
  .row{display:flex;align-items:center;gap:9px}
  .dot{width:11px;height:11px;border-radius:50%;background:__COLOR__;flex:none}
  .tag{font-size:13px;color:#8A8A8E}
  h1{font-size:21px;margin:14px 0 6px}
  p{margin:0;color:#4A4A4F;font-size:15px}
  .foot{margin-top:20px;padding-top:14px;border-top:1px solid #EDEDF0;
        font-size:12px;color:#A0A0A5;line-height:1.7}
</style>
</head>
<body>
  <div class="card">
    <div class="row"><span class="dot"></span><span class="tag">AzurRem 网关 · __TAG__</span></div>
    <h1>__TITLE__</h1>
    <p>__DETAIL__</p>
    <div class="foot">数据与操作都在 AzurRem App 里。<br>这一页只报告网关与 AzurPilot 的连接状态。</div>
  </div>
</body>
</html>
"""


def _status_html() -> str:
    state, title, detail = _probe_upstream()
    color, tag = _STATUS_LOOK.get(state, _STATUS_LOOK["error"])
    return (
        _STATUS_TEMPLATE
        .replace("__COLOR__", color)
        .replace("__TAG__", tag)
        .replace("__TITLE__", title)
        .replace("__DETAIL__", detail)
    )



def _is_proxy_path(path: str) -> bool:
    """这个路径要不要转给 AzurPilot。

    ★ **白名单，不是通配**。理由见 PROXY_PREFIXES 上面那段：AzurPilot 的
      FastAPI 上还挂着 `/api/launcher/startup`（POST，能拉起进程）、
      `/api/import_legacy_upload`（能传文件）、以及 `/ws/live_control`
      （**真能点屏幕**）。做成"整个站点通用反代"等于把它们一起搬上公网。
      App 需要的只有 MCP（它本身就是完整控制面）+ 两个只读统计接口。
    """
    return path.startswith(PROXY_PREFIXES) or path in PROXY_PATHS


class Handler(BaseHTTPRequestHandler):
    server_version = "AzurRemGateway/2.0"

    # ★ 必须是 HTTP/1.1，不能用默认的 1.0。
    #   MCP 的 SSE 是**长连接 + 事先不知道长度**，只有 1.1 才能用 chunked
    #   边收边发；1.0 只能靠"关连接"表示结束，中间隔着 Cloudflare 时不稳。
    #   代价：每个响应都必须自带 Content-Length 或 chunked —— 本类的出口
    #   全部走 _send / _send_html / _send_text / _proxy，已经覆盖到了。
    protocol_version = "HTTP/1.1"

    # ---- 响应工具 ----

    def _send(self, payload: dict, status: int = 200) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_html(self, html: str, status: int = 200) -> None:
        body = html.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, text: str, status: int = 200) -> None:
        body = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _require_auth(self) -> bool:
        """没通过就自己回 401 并返回 False，调用方直接 return。"""
        if _authorized(self):
            return True
        # 措辞与 AzurPilot 的 mcp_auth 保持一致，两边行为对得上好排查
        self._send_text(
            "Unauthorized: 缺少或无效的凭据。请携带 "
            "Authorization: Bearer <网关密码>、X-API-Key 或 ?key=<网关密码>。",
            status=401,
        )
        return False

    # ---- 路由 ----

    def do_GET(self) -> None:  # noqa: N802  (BaseHTTPRequestHandler 的命名约定)
        STATS["requests"] = STATS.get("requests", 0) + 1   # 挂件里显示的请求计数
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        path = parsed.path.rstrip("/") or "/"

        # 状态页是**唯一不鉴权的出口**。它只报"网关和 AzurPilot 连上没有"，
        # 不含实例数据、不含资源数字，给人看到也无所谓；而正因为不鉴权，
        # 它才能用来排查"密码是不是填错了 / AP 起来了没"。
        if path == "/":
            try:
                self._send_html(_status_html())
            except Exception as exc:
                self._send_text(f"状态页出错：{type(exc).__name__}: {exc}", status=500)
            return

        if not self._require_auth():
            return

        try:
            if _is_proxy_path(path):
                self._proxy("GET")
                return

            if path in ("/api/health", "/health"):
                port, password = deploy_config()
                self._send({
                    "success": True,
                    "service": "azurrem-gateway",
                    "version": "2.0",
                    # 实际生效的项目根目录（--root 或自动发现），排查用
                    "root": str(ROOT),
                    "cwd": os.getcwd(),
                    "upstream_port": port,
                    "upstream_password_set": bool(password),
                    "db_exists": DB_PATH.exists(),
                    "cl1_db_exists": CL1_DB_PATH.exists(),
                    "menu_exists": MENU_PATH.exists(),
                    "args_exists": ARGS_PATH.exists(),
                    "meow_csv_exists": MEOW_CSV.exists(),
                    "log_dir_exists": LOG_DIR.exists(),
                    "cl1_log_exists": CL1_LOG_DIR.exists(),
                    "routes": ["/", "/api/health"] + sorted(ROUTES),
                    "proxied": list(PROXY_PREFIXES) + list(PROXY_PATHS),
                    "time": datetime.now().isoformat(),
                })
                return

            handler = ROUTES.get(path)
            if handler is None:
                self._send({"success": False, "error": f"未知路径 {parsed.path}"}, status=404)
                return

            self._send({"success": True, "data": handler(query)})

        except Exception as exc:  # 保证任何异常都以 JSON 返回，方便手机端显示
            self._send({"success": False, "error": f"{type(exc).__name__}: {exc}"}, status=500)

    def do_POST(self) -> None:  # noqa: N802
        STATS["requests"] = STATS.get("requests", 0) + 1
        path = urlparse(self.path).path.rstrip("/") or "/"

        if not self._require_auth():
            return

        if not _is_proxy_path(path):
            # 网关自己**没有任何写接口**：9 条桥路由全是只读，需要写的
            # （启停、执行任务、改配置）都走 /mcp/* 转给 AzurPilot。
            # 所以这里直接 405，不留想象空间。
            self._send_text("Method Not Allowed", status=405)
            return

        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            length = 0
        body = self.rfile.read(length) if length > 0 else b""
        self._proxy("POST", body)

    # ---- 反代 ----

    def _proxy(self, method: str, body: bytes = None) -> None:
        """原样转给 AzurPilot，并在**服务端**注入它的密码。

        这是整个网关的关键一步：App 只认识网关密码，AzurPilot 的密码始终
        留在电脑上，不用填进手机 —— 公网入口那把钥匙和最后一道门是分开的。
        """
        port, password = deploy_config()
        target = f"http://127.0.0.1:{port}{self.path}"

        request = urllib.request.Request(target, data=body, method=method)
        # 只挑必要的头转过去。Host / Connection / Content-Length 交给 urllib
        # 自己算 —— 照抄客户端的反而会打架。
        # **刻意不转 Accept-Encoding**：不转的话上游不会 gzip，我们回吐的就是
        # 明文，省掉一层解压/再压缩，也就不会出现 Content-Encoding 对不上的问题。
        accept = self.headers.get("Accept")
        if accept:
            request.add_header("Accept", accept)
        ctype = self.headers.get("Content-Type")
        if ctype:
            request.add_header("Content-Type", ctype)
        if password:
            request.add_header("Authorization", f"Bearer {password}")

        # SSE 是长连接：普通请求 8 秒足够，长连接必须给宽得多，
        # 否则 AzurPilot 一段空闲就把流掐了。
        is_stream = "text/event-stream" in (accept or "")
        timeout = UPSTREAM_SSE_TIMEOUT if is_stream else UPSTREAM_TIMEOUT

        try:
            upstream = urllib.request.urlopen(request, timeout=timeout)
        except urllib.error.HTTPError as exc:
            # 上游的 4xx/5xx **原样透传**，尤其 401 —— App 靠它区分
            # "密码错"和"连不上"，这两件事用户要做的事完全不同。
            payload = exc.read()
            ctype = exc.headers.get("Content-Type") or "text/plain"
            self.send_response(exc.code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        except Exception as exc:
            self._send(
                {"success": False,
                 "error": f"无法连接 AzurPilot（127.0.0.1:{port}）："
                          f"{type(exc).__name__}: {exc}"},
                status=502,
            )
            return

        try:
            self.send_response(upstream.status)
            for name, value in upstream.headers.items():
                lower = name.lower()
                # 长度和连接方式由我们自己决定 —— 照抄会和下面的 chunked 打架
                if lower in ("transfer-encoding", "connection", "content-length"):
                    continue
                self.send_header(name, value)
            self.send_header("Transfer-Encoding", "chunked")
            self.end_headers()

            # read1 而不是 read：read(n) 会一直等到凑满 n 字节，
            # SSE 这种"来一帧就得立刻发出去"的场景会被它卡住。
            while True:
                chunk = upstream.read1(65536)
                if not chunk:
                    break
                self.wfile.write(b"%x\r\n" % len(chunk))
                self.wfile.write(chunk)
                self.wfile.write(b"\r\n")
                self.wfile.flush()
            self.wfile.write(b"0\r\n\r\n")
            self.wfile.flush()

        except (BrokenPipeError, ConnectionResetError):
            # 客户端先走了。App 每轮 MCP 都是"开一条流、用完即关"，
            # 所以这是**正常路径**，不该记成错误。
            closed = getattr(self, "close_connection", None)
            if closed is not None:
                self.close_connection = True
        except Exception as exc:
            log(f"[!] 反代中断：{type(exc).__name__}: {exc}")
        finally:
            try:
                upstream.close()
            except Exception:
                pass

    def log_message(self, fmt: str, *args) -> None:
        # 默认实现会往 stderr 刷访问日志，这里压掉，只在出错时才有噪音
        pass


class BridgeServer(ThreadingHTTPServer):
    """数据桥的 HTTP server。

    allow_reuse_address 必须显式关掉：Windows 的 SO_REUSEADDR 语义和 Linux 不同，
    开着它**第二个实例也能绑上同一个端口** —— 双击两次就会有两个桥同时收请求，
    谁回哪一条是随机的，而用户完全看不出来。关掉之后第二次启动会直接报端口占用。
    （实测：不关的话第二个 exe 会打印「数据桥已启动」并真的开始服务。）
    """

    allow_reuse_address = False


def _something_listening(port: int) -> bool:
    """127.0.0.1:port 上真的有人在监听吗（用来区分"端口被占"和"TIME_WAIT 残留"）。"""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(1.0)
            return sock.connect_ex(("127.0.0.1", port)) == 0
    except OSError:
        return False


def _bind_server(port: int, attempts: int = 5, delay: float = 1.0) -> BridgeServer:
    """绑定端口。

    刚关掉桥又立刻重开时，旧连接可能还在 TIME_WAIT，此时 bind 会失败但其实没人在
    监听 —— 这种情况重试几次，别把用户吓一跳；如果确实有人在监听，立刻抛出去。
    """
    last_error = None
    for attempt in range(attempts):
        try:
            return BridgeServer((HOST, port), Handler)
        except OSError as exc:
            last_error = exc
            if _something_listening(port):
                raise
            if attempt < attempts - 1:
                log(f"端口 {port} 暂时不可用（{exc}），1 秒后重试 ...")
                time.sleep(delay)
    raise last_error

# ─────────────────────────────────────────────────────────────
# 小挂件窗口（tkinter）+ 启动流程
#
# 为什么不是一个控制台窗口：这个 exe 是给人双击「挂着跑」的 —— 控制台又丑又占地方，
# 而且「关掉窗口」和「停掉桥」在控制台里根本分不清。现在它是无边框小挂件：
#   · 无边框 + 真圆角（SetWindowRgn）+ 置顶，可以拖着走
#   · 四个圆点在**右上角**，悬停才显示符号，从左到右：
#       蓝 −  收进**系统托盘**（HTTP 继续跑，左键单击托盘图标找回窗口）
#       绿 ⤢  展开 340x216 ↔ 收起 240x126（**双向**：蓝点不再管尺寸了）
#       黄 ▼  最小化到**任务栏**（iconify；无边框要临时摘掉，见 on_minimize）
#       红 ×  退出：先 httpd.shutdown() 再退进程，不留孤儿占端口
#     托盘那条路是纯 ctypes 手写的 Shell_NotifyIconW，见下面「系统托盘」一节
#   · 紧凑 240x126（默认）与展开 340x210 两种尺寸，两种都放得下第二行
#   · 第一行「数据桥已启动」+ 手机要填的地址（可选中复制）
#   · 第二行 [启动 AzurPilot] [选目录]，第三行小字提示结果
#   · 失败时窗口里显示红字原因 —— 打包成 --noconsole 后没有控制台，不能靠 print
#   · 拖 exe 起来时顺带拉起 AzurPilot，勾选框能关掉这个自动行为
#   · HTTP 服务跑在后台 daemon 线程，关窗口时 shutdown() 掉，不留僵尸进程
# 所有消息同时写进 exe 旁边的 AzurRemBridge.log。
# ─────────────────────────────────────────────────────────────

try:
    import tkinter as tk
except Exception as _tk_import_error:  # 极少见：精简版 Python 没带 tkinter
    tk = None
    _TK_ERROR = str(_tk_import_error)
else:
    _TK_ERROR = ""

LOG_PATH = APP_DIR / "AzurRemBridge.log"
SETTINGS_PATH = APP_DIR / "azurrem-settings.json"
ICON_NAME = "azurrem.ico"

# Apple 风格浅色配色
CARD = "#FFFFFF"
TEXT = "#1D1D1F"
SUBTEXT = "#8A8A8E"
ERR_RED = "#FF3B30"
BTN_BG = "#EDEDF0"
BTN_HOVER = "#E0E0E5"
DOT_BLUE = "#0A84FF"
DOT_GREEN = "#28C840"
DOT_YELLOW = "#FEBC2E"
DOT_RED = "#FF5F57"
GLYPH = "#3A3A3C"
FOLDER = "#F0B429"

FONT = "Microsoft YaHei UI"

# 请求计数：Handler 每处理一次请求就 +1，展开视图里显示
STATS = {"requests": 0, "started_at": None}

# 全局引用：留着防止窗口对象被 GC（tkinter 里被回收会直接闪退）
_ROOT_WINDOW = None


def log(message: str) -> None:
    """写日志文件，同时在有控制台时回显。

    --noconsole 打包后 sys.stdout / sys.stderr 是 None，直接 print 会炸，
    所以窗口和这个日志文件就是唯一的排查入口。
    """
    line = f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}  {message}"
    try:
        with io.open(LOG_PATH, "a", encoding="utf-8") as fh:
            fh.write(line + "\n")
    except OSError:
        pass
    stream = sys.stdout
    if stream is not None:
        try:
            print(line, file=stream)
        except Exception:
            pass


def load_settings() -> dict:
    try:
        data = json.loads(SETTINGS_PATH.read_text(encoding="utf-8-sig"))
        if isinstance(data, dict):
            return data
    except (OSError, ValueError):
        pass
    return {}


def save_settings(data: dict) -> None:
    try:
        SETTINGS_PATH.write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    except OSError as exc:
        log(f"设置写不进去（{exc}），这次的选择下次不会记住")


def _resource_path(name: str):
    """找随 exe 打包进来的资源（onefile 解包在 sys._MEIPASS 里）。"""
    bases = [getattr(sys, "_MEIPASS", None), APP_DIR, Path(__file__).resolve().parent]
    for base in bases:
        if not base:
            continue
        try:
            path = Path(base) / name
            if path.is_file():
                return path
        except OSError:
            continue
    return None


def get_lan_ipv4_addresses() -> list:
    """枚举本机 IPv4 局域网地址（排除 127. 和 169.254. 链路本地）。

    窗口里那个 http://<局域网IP>:25550 就是从这里来的：手机要填的是这块网卡的
    地址，不是 127.0.0.1。
    """
    found = []

    def add(ip) -> None:
        if not isinstance(ip, str):
            return
        ip = ip.strip()
        if not ip or ip.startswith("127.") or ip.startswith("169.254."):
            return
        if ip not in found:
            found.append(ip)

    # 1) UDP connect：不会真的发包，只让系统按路由表挑出"能出网的那块网卡"
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.connect(("8.8.8.8", 80))
            add(sock.getsockname()[0])
        finally:
            sock.close()
    except OSError:
        pass

    # 2) 主机名解析：多网卡（有线 + 无线 + 虚拟机）时能多拿几个地址
    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            add(ip)
    except (OSError, UnicodeError):
        pass

    return found


def _url_text(port: int) -> str:
    """手机上要填的地址：取第一块局域网网卡，多网卡时把它们也列出来。"""
    addresses = get_lan_ipv4_addresses()
    primary = addresses[0] if addresses else "127.0.0.1"
    url = f"http://{primary}:{port}"
    if len(addresses) > 1:
        extra = "、".join(f"http://{ip}:{port}" for ip in addresses[1:])
        url = f"{url}   （其它网卡：{extra}）"
    return url


# ── 启动 / 检查 AzurPilot ─────────────────────────────────────

def _running_process_names(names) -> set:
    """问一次 PowerShell 拿正在运行的进程名（项目没装 psutil，查不到就当没有）。"""
    if os.name != "nt":
        return set()
    quoted = ",".join("'" + str(name) + "'" for name in names)
    script = (
        f"Get-Process -Name {quoted} -ErrorAction SilentlyContinue "
        "| Select-Object -ExpandProperty ProcessName"
    )
    try:
        done = subprocess.run(
            ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            timeout=10, check=False,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
    except (OSError, subprocess.SubprocessError):
        return set()
    text = (done.stdout or b"").decode("utf-8", errors="replace")
    return {line.strip().lower() for line in text.splitlines() if line.strip()}


def is_azurpilot_running() -> bool:
    return bool(_running_process_names(("alas-launcher", "alas")))


def find_ap_launcher(root):
    """找 AzurPilot 的启动器：alas-launcher.exe → launcher\\ 下 → 根目录任何 *launcher*.exe。

    找不到返回 None（调用方在窗口第三行给一句小字提示，不弹模态框）。
    """
    if not root:
        return None
    root = Path(root)
    candidates = [
        root / "alas-launcher.exe",
        root / "launcher" / "alas-launcher.exe",
        root / "alas.exe",
        root / "launcher" / "alas.exe",
    ]
    try:
        candidates += sorted(p for p in root.glob("*launcher*.exe") if p.is_file())
    except OSError:
        pass
    for candidate in candidates:
        try:
            if candidate.is_file():
                return candidate
        except OSError:
            continue
    return None


# ShellExecuteW 的返回值是 HINSTANCE 强转的整数，> 32 才算成功。
# 下面是 <= 32 时的官方含义表 —— 直接把它翻成人话，比甩一个数字有用。
_SHELL_EXECUTE_ERRORS = {
    0: "系统内存或资源不足",
    2: "找不到文件",
    3: "找不到路径",
    5: "被系统拒绝（UAC 弹窗里点了「否」？）",
    8: "内存不足",
    26: "共享冲突",
    27: "文件关联不完整",
    28: "DDE 等待超时",
    29: "DDE 事务失败",
    30: "DDE 忙",
    31: "没有关联的应用程序",
    32: "不支持的 DLL",
}


def shell_execute_runas(path: Path, workdir: Path) -> str:
    """用「以管理员身份运行」启动，成功返回空串，失败返回一句人话。

    为什么必须走这条路而不能用 subprocess.Popen：

        alas-launcher.exe 的内嵌 manifest 写着
            <requestedExecutionLevel level="requireAdministrator" />
        而 Popen → CreateProcess **不能**触发 UAC 提权，遇到这种 exe
        会直接失败并给出 `OSError: [WinError 740] 请求的操作需要提升`。
        实测就是这么挂的（见 dist\\AzurRemBridge.log）。

    用户点「启动 AzurPilot」就是想让 AP 起来，在这里直接报错放弃等于按钮没用。
    ShellExecuteW 的 "runas" 谓词会让**系统**弹 UAC，用户点一下就能起来 ——
    这也正是双击那个 exe 时发生的事。
    """
    if os.name != "nt":
        return "当前系统不支持提权启动"
    import ctypes

    SW_SHOWNORMAL = 1
    try:
        rc = ctypes.windll.shell32.ShellExecuteW(
            None, "runas", str(path), None, str(workdir), SW_SHOWNORMAL
        )
    except Exception as exc:
        return f"调用系统提权接口失败：{exc}"
    if rc > 32:
        return ""
    return _SHELL_EXECUTE_ERRORS.get(rc, f"系统返回错误码 {rc}")


def launch_azurpilot(root) -> str:
    """把 AzurPilot 拉起来，返回一句给窗口显示的状态。

    找不到启动器、启动失败都只是返回一句话（窗口第三行显示），不抛异常也不弹框；
    已经在跑就不重复启动。
    """
    if not root:
        return "还没有可用的 AzurPilot 目录，请点右边的文件夹按钮指定"
    launcher = find_ap_launcher(root)
    if launcher is None:
        return "未找到 alas-launcher.exe，请用文件夹按钮指定目录"
    try:
        if is_azurpilot_running():
            return "AzurPilot 已经在运行，没有重复启动"
    except Exception as exc:
        log(f"检查 AzurPilot 进程失败：{exc}")
    try:
        subprocess.Popen(
            [str(launcher)], cwd=str(Path(root)), close_fds=True,
            # DETACHED：不让 AP 挂在我们身上，关掉挂件后它继续跑
            creationflags=getattr(subprocess, "DETACHED_PROCESS", 0)
            | getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
        )
        return f"已启动 {launcher.name}"
    except OSError as exc:
        # WinError 740 = ERROR_ELEVATION_REQUIRED：这个启动器要求管理员权限。
        # CreateProcess 提不了权，改走 ShellExecuteW("runas") 让系统弹 UAC。
        winerror = getattr(exc, "winerror", None)
        reason = shell_execute_runas(launcher, Path(root))
        if reason:
            extra = "" if winerror == 740 else f"（{exc}）"
            return f"启动 {launcher.name} 失败：{reason}{extra}"
        if winerror == 740:
            return f"已请求以管理员身份启动 {launcher.name}，请在 UAC 弹窗点「是」"
        return f"已启动 {launcher.name}"


# ─────────────────────────────────────────────────────────────
# 系统托盘（Windows 通知区域）—— 蓝点「收进托盘」靠它
#
# 为什么自己用 ctypes 写，而不是 pip install pystray：
#   1. pystray 装得上（Python 3.14 实测 import 通过），但它的 Windows 后端
#      pystray/_win32.py 第一行就是 `from six.moves import queue`，而且 Icon 只吃
#      PIL.Image（_util.serialized_image 要 Pillow 才做得出 HICON）。走它就得往这个
#      14 MB 的单文件 exe 里再塞 **six + Pillow** 两个第三方包，并且必须在
#      build-exe.bat 与 AzurRemBridge.spec **两边**同步声明
#      （--collect-all pystray / --hidden-import pystray._win32 / PIL 的 hook）——
#      两份配置一旦不同步，就是那种「时好时坏」的毛病。
#   2. 本文件的前提是**纯标准库**（见文件头：exe 里没有 AzurPilot 的 venv，也不
#      指望目标机器装了什么）。ctypes 是 CPython 自带的，PyInstaller 一定会打进去：
#      零配置、零打包选项、bat 和 spec 之间没有任何东西需要同步。
#   3. 托盘事件最终必须在 **tk 主线程**上处理 —— 从托盘还原要重新装 overrideredirect /
#      -topmost / SetWindowRgn / WS_EX_APPWINDOW。自己写 WNDPROC 就天然跑在 tk 的
#      消息循环里；红点退出时 NIM_DELETE + DestroyWindow 也是同步确定的，
#      不会留一个点不动的死图标（pystray 的 __del__ 还会在解释器退出时 join 自己的
#      线程，对一个「绝不能留孤儿进程占着端口」的程序来说是额外的关闭风险）。
#
# 实现要点（都实测过，见下方注释里的探针结论）：
#   · 托盘图标挂在一个**隐藏的顶层窗口**上，而不是 HWND_MESSAGE 消息窗口：
#     explorer.exe 重启后系统广播 WM_TASKBARCREATED 走的是 HWND_BROADCAST，
#     **消息窗口收不到广播** —— 用消息窗口的话任务栏一重启，图标就再也回不来了。
#   · 消息泵挂在 tk 的 after 上（PeekMessage 只捞我们这个 hwnd）。实测 Python 3.14
#     的 Tk 8.6 notifier 本来就会把不属于 Tk 的窗口消息 DispatchMessage 掉，所以
#     多数时候这条 after 泵什么都不用做；留着它是因为「Tk 会转发别人的消息」并不是
#     文档保证的行为，有它在就不依赖这个实现细节。两条路径互斥、不会重复处理：
#     一条消息只会被其中一方从队列里取走一次。
#   · 左键单击 / 双击 → 窗口回来；右键 → 菜单（显示窗口 / 退出）。
#     TrackPopupMenu 之前 SetForegroundWindow、之后 PostMessage(WM_NULL) 是
#     KB135788 的老规矩，不做的话菜单点到别处不消失。
#   · ⚠️ WNDPROC 里**一行 tkinter 都不许写**，只把「该干什么」记下来，真正干活交给
#     tk 的 after 轮询（_pump → _drain）。原因见下面 TrayIcon._handle_message 的注释：
#     这条消息是 Tk 自己的 notifier 在 mainloop 里 dispatch 的，那时候 _tkinter
#     已经把 GIL 放掉了（Py_BEGIN_ALLOW_THREADS），在回调里再进 Tcl 会把主线程的
#     线程状态搞坏 —— 实测（Python 3.14.2）会打印
#         Fatal Python error: PyEval_RestoreThread: ... the GIL is released
#         (the current Python thread state is NULL)
#     然后 abort()（退出码 0xC0000409）。这不是猜的：bridge\tray_test_exe.py 和
#     D:\Temp\repro_tray_crash.py 都能稳定复现，改成延迟派发后消失。
# ─────────────────────────────────────────────────────────────

try:
    import ctypes
    from ctypes import wintypes
except Exception:                    # 非 Windows：ctypes.wintypes 直接 import 不了
    ctypes = None
    wintypes = None

TRAY_READY = bool(os.name == "nt" and ctypes is not None and wintypes is not None)

# 托盘回调消息用 WM_APP + 1，不占用系统消息号
TRAY_CALLBACK_MESSAGE = 0x8000 + 1
TRAY_CMD_SHOW = 0xE100
TRAY_CMD_EXIT = 0xE101
TRAY_TOOLTIP = "AzurRem 数据桥（左键单击找回窗口，右键菜单）"

# 常量（Win32 SDK）
_WM_NULL, _WM_COMMAND, _WM_DESTROY = 0x0000, 0x0111, 0x0002
_WM_LBUTTONUP, _WM_RBUTTONUP, _WM_LBUTTONDBLCLK = 0x0202, 0x0205, 0x0203
_WS_POPUP, _WS_EX_TOOLWINDOW = 0x80000000, 0x00000080
_NIM_ADD, _NIM_DELETE = 0, 2
_NIF_MESSAGE, _NIF_ICON, _NIF_TIP = 0x01, 0x02, 0x04
_IMAGE_ICON, _LR_LOADFROMFILE = 1, 0x0010
_IDI_APPLICATION = 32512
_MF_STRING, _MF_SEPARATOR, _MF_BYCOMMAND = 0x0000, 0x0800, 0x0000
_TPM_RIGHTBUTTON, _TPM_LEFTALIGN, _TPM_BOTTOMALIGN = 0x0002, 0x0000, 0x0020
_PM_REMOVE = 0x0001
_SM_CXSMICON, _SM_CYSMICON = 49, 50

if TRAY_READY:
    _LRESULT = ctypes.c_ssize_t
    # WNDPROC 的签名：LRESULT CALLBACK (HWND, UINT, WPARAM, LPARAM)
    _WNDPROC = ctypes.WINFUNCTYPE(
        _LRESULT, wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM
    )

    class _GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", wintypes.DWORD), ("Data2", wintypes.WORD),
            ("Data3", wintypes.WORD), ("Data4", ctypes.c_byte * 8),
        ]

    class _WNDCLASSW(ctypes.Structure):
        _fields_ = [
            ("style", wintypes.UINT),
            ("lpfnWndProc", _WNDPROC),
            ("cbClsExtra", ctypes.c_int),
            ("cbWndExtra", ctypes.c_int),
            ("hInstance", wintypes.HINSTANCE),
            ("hIcon", wintypes.HICON),
            ("hCursor", wintypes.HANDLE),
            ("hbrBackground", wintypes.HBRUSH),
            ("lpszMenuName", wintypes.LPCWSTR),
            ("lpszClassName", wintypes.LPCWSTR),
        ]

    class _NOTIFYICONDATAW(ctypes.Structure):
        """NOTIFYICONDATAW（Vista 之后那版，尾部 guidItem / hBalloonIcon 也写全了）。

        cbSize 必须给**这个结构体自己的 sizeof**：给小了会被当成旧版本结构体，
        后面的字段一律作废。写全 + sizeof 让它自己算，就不用记那几个魔数。
        """
        _fields_ = [
            ("cbSize", wintypes.DWORD),
            ("hWnd", wintypes.HWND),
            ("uID", wintypes.UINT),
            ("uFlags", wintypes.UINT),
            ("uCallbackMessage", wintypes.UINT),
            ("hIcon", wintypes.HICON),
            ("szTip", wintypes.WCHAR * 128),
            ("dwState", wintypes.DWORD),
            ("dwStateMask", wintypes.DWORD),
            ("szInfo", wintypes.WCHAR * 256),
            ("uVersion", wintypes.UINT),
            ("szInfoTitle", wintypes.WCHAR * 64),
            ("dwInfoFlags", wintypes.DWORD),
            ("guidItem", _GUID),
            ("hBalloonIcon", wintypes.HICON),
        ]

_WIN32_CACHE = {}


def _win32():
    """加载并声明 user32 / kernel32 / shell32 的签名（进程内只做一次）。

    ⚠️ 必须显式写 argtypes：64 位下 HWND / HICON 是 8 字节指针，不声明的话
    ctypes 会把 Python int 按 32 位 C int 传，**句柄被截断**，Shell_NotifyIconW
    只会返回 False，图标就是不出现，而且完全没有报错。用 use_last_error=True
    加载，失败时才能从 ctypes.get_last_error() 拿到真正的错误码。
    """
    if _WIN32_CACHE:
        return _WIN32_CACHE["user32"], _WIN32_CACHE["kernel32"], _WIN32_CACHE["shell32"]

    user32 = ctypes.WinDLL("user32", use_last_error=True)
    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    shell32 = ctypes.WinDLL("shell32", use_last_error=True)

    kernel32.GetModuleHandleW.restype = wintypes.HMODULE
    kernel32.GetModuleHandleW.argtypes = (wintypes.LPCWSTR,)

    user32.RegisterClassW.restype = wintypes.ATOM
    user32.RegisterClassW.argtypes = (ctypes.POINTER(_WNDCLASSW),)
    user32.UnregisterClassW.restype = wintypes.BOOL
    user32.UnregisterClassW.argtypes = (wintypes.LPCWSTR, wintypes.HINSTANCE)
    user32.CreateWindowExW.restype = wintypes.HWND
    user32.CreateWindowExW.argtypes = (
        wintypes.DWORD, wintypes.LPCWSTR, wintypes.LPCWSTR, wintypes.DWORD,
        ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int,
        wintypes.HWND, wintypes.HMENU, wintypes.HINSTANCE, wintypes.LPVOID,
    )
    user32.DefWindowProcW.restype = _LRESULT
    user32.DefWindowProcW.argtypes = (
        wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM
    )
    user32.DestroyWindow.restype = wintypes.BOOL
    user32.DestroyWindow.argtypes = (wintypes.HWND,)
    user32.LoadImageW.restype = wintypes.HANDLE
    user32.LoadImageW.argtypes = (
        wintypes.HINSTANCE, wintypes.LPCWSTR, wintypes.UINT,
        ctypes.c_int, ctypes.c_int, wintypes.UINT,
    )
    user32.LoadIconW.restype = wintypes.HICON
    user32.LoadIconW.argtypes = (wintypes.HINSTANCE, wintypes.LPCWSTR)
    user32.DestroyIcon.restype = wintypes.BOOL
    user32.DestroyIcon.argtypes = (wintypes.HICON,)
    user32.GetSystemMetrics.restype = ctypes.c_int
    user32.GetSystemMetrics.argtypes = (ctypes.c_int,)
    user32.CreatePopupMenu.restype = wintypes.HMENU
    user32.CreatePopupMenu.argtypes = ()
    user32.AppendMenuW.restype = wintypes.BOOL
    user32.AppendMenuW.argtypes = (
        wintypes.HMENU, wintypes.UINT, ctypes.c_size_t, wintypes.LPCWSTR
    )
    user32.TrackPopupMenu.restype = wintypes.BOOL
    user32.TrackPopupMenu.argtypes = (
        wintypes.HMENU, wintypes.UINT, ctypes.c_int, ctypes.c_int, ctypes.c_int,
        wintypes.HWND, wintypes.LPVOID,
    )
    user32.DestroyMenu.restype = wintypes.BOOL
    user32.DestroyMenu.argtypes = (wintypes.HMENU,)
    user32.GetMenuStringW.restype = ctypes.c_int
    user32.GetMenuStringW.argtypes = (
        wintypes.HMENU, wintypes.UINT, wintypes.LPWSTR, ctypes.c_int, wintypes.UINT
    )
    user32.GetCursorPos.restype = wintypes.BOOL
    user32.GetCursorPos.argtypes = (ctypes.POINTER(wintypes.POINT),)
    user32.SetForegroundWindow.restype = wintypes.BOOL
    user32.SetForegroundWindow.argtypes = (wintypes.HWND,)
    user32.PostMessageW.restype = wintypes.BOOL
    user32.PostMessageW.argtypes = (
        wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM
    )
    user32.SendMessageW.restype = _LRESULT
    user32.SendMessageW.argtypes = (
        wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM
    )
    user32.PeekMessageW.restype = wintypes.BOOL
    user32.PeekMessageW.argtypes = (
        ctypes.POINTER(wintypes.MSG), wintypes.HWND,
        wintypes.UINT, wintypes.UINT, wintypes.UINT,
    )
    user32.TranslateMessage.restype = wintypes.BOOL
    user32.TranslateMessage.argtypes = (ctypes.POINTER(wintypes.MSG),)
    user32.DispatchMessageW.restype = _LRESULT
    user32.DispatchMessageW.argtypes = (ctypes.POINTER(wintypes.MSG),)
    user32.RegisterWindowMessageW.restype = wintypes.UINT
    user32.RegisterWindowMessageW.argtypes = (wintypes.LPCWSTR,)

    shell32.Shell_NotifyIconW.restype = wintypes.BOOL
    shell32.Shell_NotifyIconW.argtypes = (
        wintypes.DWORD, ctypes.POINTER(_NOTIFYICONDATAW)
    )

    _WIN32_CACHE.update(
        user32=user32, kernel32=kernel32, shell32=shell32
    )
    return user32, kernel32, shell32


class TrayIcon:
    """Windows 通知区域（系统托盘）里的一个图标。非 Windows 上是个空壳。

    只做三件事：把图标加上去、把鼠标事件转成 on_show()/on_exit()、把图标撤掉。
    「隐藏窗口 / 还原窗口」是 BridgeWindow 的事，这里不碰窗口 —— 唯一的 tk 交互是
    用 after() 挂自己的消息轮询（_pump），真正调用回调也在那个 tk 上下文里。
    """

    UID = 1

    def __init__(self, on_show, on_exit, tooltip=TRAY_TOOLTIP, icon_path=None,
                 after=None, after_cancel=None):
        self.on_show = on_show
        self.on_exit = on_exit
        self.tooltip = (tooltip or "")[:127]      # szTip 只有 128 个 wchar（含结尾 \0）
        self.icon_path = icon_path
        self.available = TRAY_READY
        self.visible = False          # 图标现在在不在托盘里（= NIM_ADD 成功过）
        self.hwnd = None              # 收回调消息的隐藏窗口
        self.last_error = ""
        self.closed = False
        self._after = after
        self._after_cancel = after_cancel
        self._pump_job = None
        # WNDPROC 只往这里写字符串，真正干活在 _drain()（tk 回调里）——
        # 绝不能在 WNDPROC 里碰 tkinter，原因见 _handle_message 的注释。
        self._pending = None
        self._proc = None             # WNDPROC 回调，必须留引用，否则被 GC 掉就崩
        self._hicon = None
        self._owns_icon = False       # LoadImageW 出来的要 DestroyIcon，系统的不用
        self._class_atom = 0
        self._class_name = f"AzurRemBridgeTrayWnd_{os.getpid()}_{id(self):x}"
        self._taskbar_created_msg = 0

    # ---- 建/拆隐藏窗口 ----

    def _ensure_window(self) -> bool:
        """建一个隐藏的顶层窗口来收托盘消息（幂等）。失败返回 False。"""
        if self.hwnd:
            return True
        if not self.available:
            self.last_error = f"非 Windows 或没有 ctypes（os.name={os.name}）"
            return False
        try:
            user32, kernel32, _shell32 = _win32()
            hinst = kernel32.GetModuleHandleW(None)

            self._proc = _WNDPROC(self._wndproc)
            wc = _WNDCLASSW()
            wc.lpfnWndProc = self._proc
            wc.hInstance = hinst
            wc.lpszClassName = self._class_name
            self._class_atom = user32.RegisterClassW(ctypes.byref(wc))
            if not self._class_atom:
                self.last_error = f"RegisterClassW 失败（GetLastError={ctypes.get_last_error()}）"
                return False

            # WS_POPUP + 不调用 ShowWindow：永远不显示，但**是顶层窗口**，
            # 所以能收到 HWND_BROADCAST 的 WM_TASKBARCREATED（消息窗口收不到）。
            # WS_EX_TOOLWINDOW：万一它真被显示出来，也别出现在任务栏/Alt-Tab 里。
            self.hwnd = user32.CreateWindowExW(
                _WS_EX_TOOLWINDOW, self._class_name, "AzurRemBridge tray",
                _WS_POPUP, 0, 0, 0, 0, None, None, hinst, None,
            )
            if not self.hwnd:
                self.last_error = f"CreateWindowExW 失败（GetLastError={ctypes.get_last_error()}）"
                return False

            self._hicon, self._owns_icon = self._load_icon()
            self._taskbar_created_msg = user32.RegisterWindowMessageW("TaskbarCreated")
            self._start_pump()
            return True
        except Exception as exc:
            self.last_error = f"{type(exc).__name__}: {exc}"
            log(f"托盘：建隐藏窗口失败 —— {self.last_error}")
            return False

    def _load_icon(self):
        """优先用 exe 自带的多尺寸 azurrem.ico，取不到就退回系统默认图标。"""
        user32, _kernel32, _shell32 = _win32()
        if self.icon_path:
            try:
                handle = user32.LoadImageW(
                    None, str(self.icon_path), _IMAGE_ICON,
                    user32.GetSystemMetrics(_SM_CXSMICON),
                    user32.GetSystemMetrics(_SM_CYSMICON),
                    _LR_LOADFROMFILE,
                )
                if handle:
                    return handle, True
                log(f"托盘：LoadImageW 读不了 {self.icon_path}（GetLastError="
                    f"{ctypes.get_last_error()}），改用系统默认图标")
            except Exception as exc:
                log(f"托盘：加载图标失败（{exc}），改用系统默认图标")
        return user32.LoadIconW(None, wintypes.LPCWSTR(_IDI_APPLICATION)), False

    # ---- 加/撤图标 ----

    def _notify_data(self):
        data = _NOTIFYICONDATAW()
        data.cbSize = ctypes.sizeof(_NOTIFYICONDATAW)
        data.hWnd = self.hwnd
        data.uID = self.UID
        data.uFlags = _NIF_MESSAGE | _NIF_ICON | _NIF_TIP
        data.uCallbackMessage = TRAY_CALLBACK_MESSAGE
        data.hIcon = self._hicon
        data.szTip = self.tooltip
        return data

    def show_icon(self) -> bool:
        """把图标加到托盘。返回 True 才算真的加上了（调用方据此决定要不要隐藏窗口）。"""
        if self.closed or self.visible:
            return self.visible
        if not self._ensure_window():
            return False
        try:
            _user32, _kernel32, shell32 = _win32()
            data = self._notify_data()
            ok = bool(shell32.Shell_NotifyIconW(_NIM_ADD, ctypes.byref(data)))
            self.visible = ok
            if not ok:
                self.last_error = ("Shell_NotifyIconW(NIM_ADD) 失败，GetLastError="
                                   f"{ctypes.get_last_error()}")
                log(f"托盘：{self.last_error}")
            return ok
        except Exception as exc:
            self.last_error = f"{type(exc).__name__}: {exc}"
            log(f"托盘：加图标失败 —— {self.last_error}")
            return False

    def hide_icon(self) -> None:
        """把图标从托盘撤掉（幂等）。窗口回来了、或者程序要退出了都调它。"""
        if not self.visible:
            self.visible = False
            return
        self.visible = False
        if not self.hwnd:
            return
        try:
            _user32, _kernel32, shell32 = _win32()
            data = self._notify_data()
            data.uFlags = 0
            shell32.Shell_NotifyIconW(_NIM_DELETE, ctypes.byref(data))
        except Exception as exc:
            log(f"托盘：撤图标失败（{exc}）")

    def stop(self) -> None:
        """程序收尾：撤图标 + 销毁隐藏窗口 + 注销窗口类（同步生效，不留死图标）。"""
        if self.closed:
            return
        self.closed = True
        if self._pump_job is not None and self._after_cancel is not None:
            try:
                self._after_cancel(self._pump_job)
            except Exception:
                pass
            self._pump_job = None
        self.hide_icon()
        if not self.available:
            return
        try:
            user32, kernel32, _shell32 = _win32()
            if self.hwnd:
                user32.DestroyWindow(self.hwnd)
                self.hwnd = None
            if self._class_atom:
                user32.UnregisterClassW(self._class_name, kernel32.GetModuleHandleW(None))
                self._class_atom = 0
            if self._hicon and self._owns_icon:
                user32.DestroyIcon(self._hicon)
        except Exception as exc:
            log(f"托盘：收尾出错（{exc}）")
        self._hicon = None
        self._proc = None

    # ---- 消息 ----

    def _start_pump(self) -> None:
        """把 after 轮询挂上（见文件顶部：WNDPROC 只记账，这里才真的干活）。"""
        if self._pump_job is not None or self._after is None or self.closed:
            return
        try:
            self._pump_job = self._after(40, self._pump)
        except Exception:
            self._pump_job = None

    def _pump(self) -> None:
        """tk 的回调：先把我们这个 hwnd 的消息捞出来消掉，再处理攒下的请求。

        这个函数跑在**正常的 Tk 定时器回调**里（GIL 是正常持有的、线程状态是对的），
        所以在这里碰 tkinter 是安全的 —— 这正是 WNDPROC 不能自己动手的原因。
        """
        self._pump_job = None
        if self.closed:
            return
        if self.hwnd:
            try:
                user32, _kernel32, _shell32 = _win32()
                msg = wintypes.MSG()
                while user32.PeekMessageW(ctypes.byref(msg), self.hwnd, 0, 0, _PM_REMOVE):
                    user32.TranslateMessage(ctypes.byref(msg))
                    user32.DispatchMessageW(ctypes.byref(msg))
                    if self.closed:
                        return
            except Exception as exc:
                log(f"托盘：消息泵出错（{exc}）")
        self._drain()
        self._start_pump()

    def _drain(self) -> None:
        """把 WNDPROC 记下来的请求在 tk 上下文里执行掉（一次一个，不排队堆积）。"""
        action, self._pending = self._pending, None
        if not action or self.closed:
            return
        if action == "show":
            self._fire(self.on_show)
        elif action == "exit":
            self._fire(self.on_exit)
        elif action == "menu":
            self._popup_menu()          # TrackPopupMenu 阻塞，但这里已经是 tk 回调了
        elif action == "readd":
            if self.visible:            # 只有「本来就该显示」时才补
                self.visible = False
                self.show_icon()

    def _wndproc(self, hwnd, msg, wparam, lparam):
        try:
            return self._handle_message(hwnd, msg, wparam, lparam)
        except Exception as exc:
            # 绝不能让异常穿过 ctypes 回调边界（--noconsole 下会变成静默崩溃）
            try:
                log(f"托盘：消息 {msg} 处理出错 —— {type(exc).__name__}: {exc}")
            except Exception:
                pass
            return 0

    def _handle_message(self, hwnd, msg, wparam, lparam):
        """托盘回调消息的入口 —— ⚠️ 这里**绝对不能调用 tkinter**。

        为什么：这条消息是 **Tk 自己的 notifier** 在 mainloop 里 PeekMessage +
        DispatchMessage 出来的（实测 Tk 会转发不属于它的窗口消息）。而 tkinter 的
        `mainloop()` 是用 Py_BEGIN_ALLOW_THREADS 包着 Tk_MainLoop() 跑的 ——
        也就是**dispatch 发生时 GIL 已经被放掉、当前线程状态是 NULL**。ctypes 回调
        会自己 PyGILState_Ensure 把 GIL 拿回来，如果在这个上下文里再去调
        `root.deiconify()` / `update_idletasks()` / `after()`（这些都会再进 Tcl），
        主线程的线程状态就被搅乱了：Tk_MainLoop 一返回，Py_END_ALLOW_THREADS 里的
        PyEval_RestoreThread 发现线程状态是 NULL，直接
            Fatal Python error: PyEval_RestoreThread: ... (the current Python
            thread state is NULL)
        → abort()（进程退出码 0xC0000409，日志里什么都不会留下）。

        所以这里只做两件事：**记一个字符串** + DefWindowProcW。真正的动作由
        _pump()（tk 的 after 轮询）在正常的 Tk 回调上下文里执行。
        这里的赋值是纯 Python 字节码，不碰 Tcl，安全。
        """
        user32, _kernel32, _shell32 = _win32()

        if msg == TRAY_CALLBACK_MESSAGE:
            # 经典（非 V4）语义：wParam = 图标 id，lParam = 鼠标消息
            event = lparam & 0xFFFF
            if event in (_WM_LBUTTONUP, _WM_LBUTTONDBLCLK):
                self._pending = "show"
            elif event == _WM_RBUTTONUP:
                self._pending = "menu"
            return 0

        if self._taskbar_created_msg and msg == self._taskbar_created_msg:
            # explorer.exe 重启 → 托盘里所有图标都没了，得自己加回来。
            # Shell_NotifyIconW 是纯 Win32（不碰 Tcl），但为了「WNDPROC 只记账」
            # 这条规矩不打折，还是交给 _drain() 去做。
            self._pending = self._pending or "readd"
            return 0

        if msg == _WM_COMMAND:
            command = wparam & 0xFFFF
            if command == TRAY_CMD_SHOW:
                self._pending = "show"
            elif command == TRAY_CMD_EXIT:
                self._pending = "exit"
            return 0

        return user32.DefWindowProcW(hwnd, msg, wparam, lparam)

    def _fire(self, callback) -> None:
        if self.closed or callback is None:
            return
        try:
            callback()
        except Exception as exc:
            log(f"托盘：回调出错 —— {type(exc).__name__}: {exc}")

    def _popup_menu(self) -> None:
        """右键菜单。没有 TPM_RETURNCMD：命令以 WM_COMMAND 回投给窗口，
        这样测试可以 SendMessage(WM_COMMAND, TRAY_CMD_EXIT) 走**完全相同**的代码路径。"""
        user32, _kernel32, _shell32 = _win32()
        menu = user32.CreatePopupMenu()
        if not menu:
            log("托盘：CreatePopupMenu 失败，右键菜单打不开")
            return
        try:
            user32.AppendMenuW(menu, _MF_STRING, TRAY_CMD_SHOW, "显示窗口")
            user32.AppendMenuW(menu, _MF_SEPARATOR, 0, None)
            user32.AppendMenuW(menu, _MF_STRING, TRAY_CMD_EXIT, "退出（停掉数据桥）")
            point = wintypes.POINT()
            user32.GetCursorPos(ctypes.byref(point))
            # KB135788：不先把我们的窗口抢成前台，菜单点到别处不会消失
            foreground = bool(user32.SetForegroundWindow(self.hwnd))
            log(f"托盘：弹出右键菜单（{point.x},{point.y}，SetForegroundWindow={foreground}）")
            result = user32.TrackPopupMenu(
                menu, _TPM_RIGHTBUTTON | _TPM_LEFTALIGN | _TPM_BOTTOMALIGN,
                point.x, point.y, 0, self.hwnd, None,
            )
            user32.PostMessageW(self.hwnd, _WM_NULL, 0, 0)
            log(f"托盘：右键菜单已关闭（TrackPopupMenu 返回 {result}）")
        finally:
            user32.DestroyMenu(menu)

    # ---- 给测试用的小工具 ----

    def menu_items(self):
        """把右键菜单真的建出来，再逐个读回标题和命令号（不弹出来）。

        测试用它验证「右键菜单里有 显示窗口 / 退出」是**真的建出来了**，
        而不是只在源码里写了两个字符串。
        """
        if not self.available or not self.hwnd:
            return []
        user32, _kernel32, _shell32 = _win32()
        menu = user32.CreatePopupMenu()
        if not menu:
            return []
        items = []
        try:
            user32.AppendMenuW(menu, _MF_STRING, TRAY_CMD_SHOW, "显示窗口")
            user32.AppendMenuW(menu, _MF_SEPARATOR, 0, None)
            user32.AppendMenuW(menu, _MF_STRING, TRAY_CMD_EXIT, "退出（停掉数据桥）")
            buf = ctypes.create_unicode_buffer(128)
            for command in (TRAY_CMD_SHOW, TRAY_CMD_EXIT):
                buf.value = ""
                user32.GetMenuStringW(menu, command, buf, 128, _MF_BYCOMMAND)
                items.append((command, buf.value))
        finally:
            user32.DestroyMenu(menu)
        return items


# ── 两种按钮 ──────────────────────────────────────────────────

class TrafficButton(tk.Canvas if tk else object):
    """macOS 交通灯风格的小圆点按钮。

    平时是纯色圆点，鼠标悬停才在中间画出符号（− / ⤢ / ▼ / ×），离开又变回圆点。
    """

    def __init__(self, master, color, symbol, command, tooltip="", size=12):
        super().__init__(
            master, width=size + 4, height=size + 4, bg=CARD,
            highlightthickness=0, bd=0, cursor="hand2",
        )
        self._size = size
        self._symbol = symbol
        self._command = command
        self.tooltip = tooltip
        self._glyph = None
        self.create_oval(2, 2, size + 2, size + 2, fill=color, outline="")
        self.bind("<Enter>", self._on_enter)
        self.bind("<Leave>", self._on_leave)
        self.bind("<Button-1>", lambda _event: self.invoke())

    def _center(self) -> float:
        return (self._size + 4) / 2.0

    def _on_enter(self, _event=None) -> None:
        if self._glyph is None:
            self._glyph = self.create_text(
                self._center(), self._center() + 1.0, text=self._symbol,
                fill=GLYPH, font=(FONT, 8, "bold"),
            )

    def _on_leave(self, _event=None) -> None:
        if self._glyph is not None:
            self.delete(self._glyph)
            self._glyph = None

    def invoke(self) -> None:
        """等价于点一下这个按钮（自动化测试直接用这个）。"""
        self._command()


class PillButton(tk.Canvas if tk else object):
    """圆角小胶囊按钮：macOS 那种浅灰底、无边框的按钮。

    tk.Button 做不出圆角，所以自己在 Canvas 上画一个圆角多边形。
    icon="folder" 时画一个小文件夹图标（不依赖 emoji 字体，哪台机器都能显示出来）。
    """

    def __init__(self, master, text="", command=None, width=112, height=26,
                 radius=13, bg=BTN_BG, hover=BTN_HOVER, fg=TEXT,
                 font=None, icon=None):
        super().__init__(master, width=width, height=height, bg=CARD,
                         highlightthickness=0, bd=0, cursor="hand2")
        self._command = command or (lambda: None)
        # ⚠️ 不能叫 self._w：那是 tkinter 内部存 widget 路径的属性，覆盖掉之后
        #    所有 self.create_xxx 都会报 'invalid command name 112'
        self._width, self._height = width, height
        self._bg, self._hover = bg, hover
        self._shape = self._round_rect(1, 1, width - 1, height - 1, radius, fill=bg)
        self._label = None
        if icon == "folder":
            self._draw_folder()
        else:
            self._label = self.create_text(
                width / 2, height / 2 + 0.5, text=text, fill=fg, font=font or (FONT, 9)
            )
        self.bind("<Enter>", lambda _e: self.itemconfigure(self._shape, fill=self._hover))
        self.bind("<Leave>", lambda _e: self.itemconfigure(self._shape, fill=self._bg))
        self.bind("<Button-1>", lambda _e: self.invoke())

    def _round_rect(self, x1, y1, x2, y2, radius, **kwargs):
        points = [
            x1 + radius, y1, x2 - radius, y1, x2, y1, x2, y1 + radius,
            x2, y2 - radius, x2, y2, x2 - radius, y2, x1 + radius, y2,
            x1, y2, x1, y2 - radius, x1, y1 + radius, x1, y1,
        ]
        return self.create_polygon(points, smooth=True, **kwargs)

    def _draw_folder(self) -> None:
        """小文件夹图标：一个带标签的矩形，纯 Canvas 画，不依赖字体。"""
        cx, cy = self._width / 2, self._height / 2
        self.create_rectangle(cx - 7, cy - 5.5, cx - 2, cy - 3, fill=FOLDER, outline="")
        self.create_rectangle(cx - 8.5, cy - 4, cx + 8.5, cy + 5.5, fill=FOLDER, outline="")

    def set_text(self, text: str) -> None:
        if self._label is not None:
            self.itemconfigure(self._label, text=text)

    def invoke(self) -> None:
        self._command()


# ── 主窗口 ────────────────────────────────────────────────────

class BridgeWindow:
    """无边框小挂件窗口：显示状态，并作为「桥在跑」的唯一可视提示。"""

    # 默认（紧凑）也要放得下第二行按钮，所以不是 76 高而是 126
    COMPACT = (240, 126)
    # 展开：+ 提示行 / 路径 / 请求计数 / 勾选框 / 页脚，216 是量出来的（不是猜的）
    EXPANDED = (340, 216)

    def __init__(self, root, state: dict):
        self.root = root
        self.state = state
        self.closed = False
        self.expanded = False
        self._drag_offset = None
        self._frameless_suspended = False   # 最小化时临时摘掉无边框，还原时装回
        self._tick_job = None               # 每秒刷新那一枪的句柄，关窗口前要撤掉
        self._last_size = self.COMPACT      # 实际生效的尺寸（行高会撑高，不一定是常量）
        self._launch_ap = tk.BooleanVar(value=bool(state.get("launch_ap", True)))
        self.hidden_in_tray = False         # 蓝点：窗口现在是不是收在托盘里
        # 托盘图标（Windows 通知区域）。构造本身是廉价的：隐藏窗口和图标要等到
        # 第一次「收进托盘」才真的建出来，所以从不点蓝点的话没有任何托盘开销。
        self.tray = TrayIcon(
            on_show=self.show_from_tray,
            on_exit=self.on_close,          # 托盘菜单「退出」= 点红点，先 shutdown 再退
            icon_path=_resource_path(ICON_NAME),
            after=root.after,
            after_cancel=root.after_cancel,
        )

        root.title("AzurRem 数据桥")
        root.overrideredirect(True)            # 无边框，标题栏自己画
        root.configure(bg=CARD)
        try:
            root.attributes("-topmost", True)  # 挂着运行，别被别的窗口盖住
        except tk.TclError:
            pass
        icon = _resource_path(ICON_NAME)
        if icon is not None:
            try:
                root.iconbitmap(default=str(icon))
            except tk.TclError as exc:
                log(f"窗口图标设置失败：{exc}")
        root.report_callback_exception = self._on_tk_error
        root.protocol("WM_DELETE_WINDOW", self.on_close)
        root.bind("<Alt-F4>", lambda _event: self.on_close())
        root.bind("<Map>", self._on_map)      # 从任务栏还原 → 装回无边框

        self.frame = tk.Frame(root, bg=CARD, bd=0, highlightthickness=0)
        self.frame.place(x=0, y=0, relwidth=1, relheight=1)

        self._build_widgets()
        self._place_compact()                  # 默认紧凑
        self._move_to_default_position()
        # 无边框窗口默认没有任务栏按钮，"最小化"之后就再也找不回来了
        self._enable_taskbar_button()
        self._apply_round_corners()
        # 窗口真正映射之后 HWND 才稳，200ms 后再补一次（幂等）
        self.root.after(200, self._refresh_window_chrome)
        self._tick()

    # ---- 构建 ----

    def _build_widgets(self) -> None:
        ok = bool(self.state.get("ok"))

        self.lbl_title = tk.Label(
            self.frame, text=self.state.get("title", ""), bg=CARD,
            fg=TEXT if ok else ERR_RED,
            font=(FONT, 12, "bold"), anchor="w", justify="left",
        )

        # 四个圆点：**右上角**，从左到右 收进托盘(蓝) / 展开收起(绿) / 最小化(黄) / ×(红)
        self.dot_row = tk.Frame(self.frame, bg=CARD)
        self.dot_row.place(relx=1.0, x=-12, y=8, anchor="ne")
        self.btn_tray = TrafficButton(self.dot_row, DOT_BLUE, "−", self.on_hide_to_tray, "隐藏到系统托盘")
        self.btn_expand = TrafficButton(self.dot_row, DOT_GREEN, "⤢", self.on_toggle_size, "展开 / 收起（切换尺寸）")
        self.btn_minimize = TrafficButton(self.dot_row, DOT_YELLOW, "▼", self.on_minimize, "最小化到任务栏")
        self.btn_close = TrafficButton(self.dot_row, DOT_RED, "×", self.on_close, "退出（同时停掉数据桥）")
        for button in (self.btn_tray, self.btn_expand, self.btn_minimize, self.btn_close):
            button.pack(side="left", padx=2)   # 12px 圆点 + 8px 间距

        # 第一行：手机要填的地址（readonly Entry：可以选中、可以 Ctrl+C）
        self.entry_url = tk.Entry(
            self.frame, bg=CARD, fg=TEXT,
            readonlybackground=CARD, relief="flat", bd=0, highlightthickness=0,
            font=(FONT, 10), selectbackground="#B3D7FF", selectforeground=TEXT,
        )
        self.entry_url.insert(0, self.state.get("url", ""))
        self.entry_url.configure(state="readonly")
        self.entry_url.bind("<Control-c>", lambda _e: self.entry_url.event_generate("<<Copy>>"))
        self.entry_url.bind("<Control-a>", lambda _e: self._select_all(self.entry_url))

        # 第二行：网关密码。**可编辑**。
        #
        # 默认是首次运行自动生成的 32 位随机串，但用户想换成自己记得住的就让他换
        # （改完写回 azurrem-gateway.key 并立刻生效，同时提醒 App 那边也要改）。
        # 前缀「密码」单独做一个 Label 而不是塞进 Entry 里 —— Entry 是可编辑的，
        # 把说明文字混进去，用户一改就把说明也改掉了。
        self.lbl_key = tk.Label(
            self.frame, text="密码", bg=CARD, fg=SUBTEXT, font=(FONT, 9), anchor="w",
        )
        self.entry_key = tk.Entry(
            self.frame, bg=CARD, fg=TEXT, relief="flat", bd=0, highlightthickness=0,
            font=(FONT, 9), selectbackground="#B3D7FF", selectforeground=TEXT,
        )
        self.entry_key.insert(0, str(self.state.get("gateway_key") or ""))
        self.entry_key.bind("<Return>", self.on_commit_key)
        self.entry_key.bind("<FocusOut>", self.on_commit_key)
        self.entry_key.bind("<Control-a>", lambda _e: self._select_all(self.entry_key))

        # 第二行：手动启动 AzurPilot + 选目录（桥在不在跑都不影响这两个按钮）
        self.btn_start_ap = PillButton(
            self.frame, "启动 AzurPilot", self.on_start_ap, width=112, height=26
        )
        self.btn_pick_root = PillButton(
            self.frame, command=self.on_pick_root, width=32, height=26, icon="folder"
        )

        # 第三行：小字提示（启动结果 / 切换结果 / 报错都在这里，不弹模态框）
        self.lbl_hint = tk.Label(
            self.frame, text=self.state.get("hint", ""), bg=CARD, fg=SUBTEXT,
            font=(FONT, 8), anchor="w", justify="left", wraplength=306,
        )

        # 展开后才出现的细节
        self.lbl_detail = tk.Label(
            self.frame, text=self.state.get("detail", ""), bg=CARD, fg=SUBTEXT,
            font=(FONT, 8), anchor="w", justify="left", wraplength=306,
        )
        self.lbl_root = tk.Label(
            self.frame, text=self._root_text(), bg=CARD, fg=SUBTEXT,
            font=(FONT, 8), anchor="w", justify="left",
        )
        self.lbl_stats = tk.Label(
            self.frame, text="", bg=CARD, fg=SUBTEXT, font=(FONT, 8), anchor="w",
        )
        self.chk_ap = tk.Checkbutton(
            self.frame, text="exe 启动时自动拉起 AzurPilot", variable=self._launch_ap,
            command=self.on_toggle_ap, bg=CARD, fg=SUBTEXT, activebackground=CARD,
            activeforeground=TEXT, selectcolor=CARD, font=(FONT, 8),
            bd=0, highlightthickness=0, anchor="w", cursor="hand2",
        )
        self.lbl_footer = tk.Label(
            self.frame, text="这个窗口挂着 = 桥在跑 · 关掉 = 桥停止",
            bg=CARD, fg=SUBTEXT, font=(FONT, 8), anchor="w",
        )

        # 无边框窗口要自己处理拖动（Entry 上不绑，否则没法选文字复制）
        for widget in (self.frame, self.lbl_title, self.lbl_key, self.lbl_root,
                       self.lbl_stats, self.lbl_detail, self.lbl_hint, self.lbl_footer):
            widget.bind("<Button-1>", self._drag_start)
            widget.bind("<B1-Motion>", self._drag_move)

    def _root_text(self) -> str:
        root = self.state.get("root")
        if not root:
            return "AzurPilot：没找到（可用右侧文件夹按钮指定）"
        return f"AzurPilot：{self._shorten(str(root))}"

    # ---- 网关密码：允许用户改成自己记得住的 ----

    def on_commit_key(self, _event=None) -> None:
        """密码框回车 / 失焦：校验 → 落盘 → 立刻生效。

        为什么不给"随便填"：网关是**公网入口**，空密码等于对所有人开门。
        所以空的、太短的都直接还原并说明原因，不让用户糊里糊涂把自己敞开。
        """
        global GATEWAY_KEY
        try:
            candidate = self.entry_key.get().strip()
        except tk.TclError:
            return
        current = str(self.state.get("gateway_key") or "")
        if candidate == current:
            return

        if not candidate:
            self._write_entry(self.entry_key, current)
            self.set_hint("密码不能为空 —— 空密码等于对所有人开门，已还原", error=True)
            return
        if len(candidate) < 8:
            self._write_entry(self.entry_key, current)
            self.set_hint("密码太短了（至少 8 位），已还原", error=True)
            return

        try:
            GATEWAY_KEY_PATH.write_text(candidate + "\n", encoding="utf-8")
        except OSError as exc:
            self._write_entry(self.entry_key, current)
            self.set_hint(f"密码写不进 {GATEWAY_KEY_PATH.name}：{exc}", error=True)
            return

        GATEWAY_KEY = candidate
        self.state["gateway_key"] = candidate
        log(f"网关密码已更新（{len(candidate)} 位）")
        # 日志里**不写密码本身** —— AzurRemBridge.log 是排查时会被贴出来的东西
        self.set_hint("密码已更新，立刻生效 —— App 里的「服务端密码」也要改成这个")

    @staticmethod
    def _select_all(entry) -> None:
        """readonly Entry 里 Ctrl+A 默认不生效，得自己绑。"""
        try:
            entry.selection_range(0, "end")
            entry.icursor("end")
        except tk.TclError:
            pass

    @staticmethod
    def _shorten(text: str, limit: int = 46) -> str:
        if len(text) <= limit:
            return text
        head = text[: limit // 2 - 2]
        tail = text[-(limit // 2 - 1):]
        return f"{head}…{tail}"

    # ---- 布局：紧凑 / 展开 ----

    def _place_compact(self) -> None:
        self.expanded = False
        for widget in (self.lbl_detail, self.lbl_root, self.lbl_stats,
                       self.chk_ap, self.lbl_footer):
            widget.place_forget()
        self.lbl_title.configure(font=(FONT, 12, "bold"))
        self.dot_row.place(relx=1.0, x=-12, y=8, anchor="ne")
        y = self._stack([self.lbl_title], 14, 8, gap=5)
        self.entry_url.place(x=14, y=y, width=self.COMPACT[0] - 28, height=22)
        y += 22 + 2
        self.lbl_key.place(x=14, y=y, width=32, height=20)
        self.entry_key.place(x=48, y=y, width=self.COMPACT[0] - 28 - 34, height=20)
        y += 20 + 5
        self.btn_start_ap.place(x=14, y=y)
        self.btn_pick_root.place(x=132, y=y)
        y += 26 + 5
        self.lbl_hint.configure(wraplength=self.COMPACT[0] - 28)
        y = self._stack([self.lbl_hint], 14, y, gap=0)
        self._apply_geometry((self.COMPACT[0], max(self.COMPACT[1], y + 8)))

    def _place_expanded(self) -> None:
        self.expanded = True
        self.lbl_title.configure(font=(FONT, 14, "bold"))
        self.dot_row.place(relx=1.0, x=-14, y=12, anchor="ne")
        y = self._stack([self.lbl_title], 16, 10, gap=6)
        self.entry_url.place(x=16, y=y, width=self.EXPANDED[0] - 32, height=24)
        y += 24 + 3
        self.lbl_key.place(x=16, y=y, width=36, height=21)
        self.entry_key.place(x=54, y=y, width=self.EXPANDED[0] - 32 - 38, height=21)
        y += 21 + 6
        self.btn_start_ap.place(x=16, y=y)
        self.btn_pick_root.place(x=136, y=y)
        y += 26 + 6
        self.lbl_hint.configure(wraplength=self.EXPANDED[0] - 32)
        # detail 只在出错时才有内容（正常状态那句话和上面的地址栏重复）
        stack = [self.lbl_hint]
        if str(self.state.get("detail") or "").strip() and not self.state.get("ok"):
            stack.append(self.lbl_detail)
        stack += [self.lbl_root, self.lbl_stats, self.chk_ap, self.lbl_footer]
        y = self._stack(stack, 16, y, gap=4)
        self._apply_geometry((self.EXPANDED[0], max(self.EXPANDED[1], y + 10)))

    def _stack(self, widgets, x: int, start_y: int, gap: int = 4) -> int:
        """把一串控件自上而下摆好，返回最后一个的底边。

        高度取控件自己算出来的 reqheight —— 字号/中文字形不一样时，
        行高会变（14pt 标题实测 32px），写死 y 坐标必然重叠。
        """
        y = start_y
        for widget in widgets:
            widget.place(x=x, y=y)
            widget.update_idletasks()
            y += max(widget.winfo_reqheight(), 1) + gap
        return y - gap

    def _apply_geometry(self, size) -> None:
        width, height = size
        x, y = self.root.winfo_x(), self.root.winfo_y()
        if x <= 0 and y <= 0:
            x, y = self._default_position(width)
        x, y = self._clamp_to_screen(x, y, width, height)
        self.root.geometry(f"{width}x{height}+{x}+{y}")
        self._last_size = (width, height)
        self.root.update_idletasks()
        # 用**刚设置的目标尺寸**去裁圆角：Windows 的 resize 是异步的，
        # 这里读 winfo_width() 可能拿到还在变的中间值；80ms 后再补一次，幂等。
        self._apply_round_corners(width, height)
        self.root.after(80, lambda: self._apply_round_corners(width, height))

    def _clamp_to_screen(self, x: int, y: int, width: int, height: int):
        """别让窗口飘出屏幕。

        挂件默认停在屏幕右上角，那个位置是按**紧凑宽度**算的；一展开就宽了 100px，
        还留在原地的话右边就跑到屏幕外面去了 —— 表现是展开后右边两个圆点
        "不见了"（屏幕外那块截出来是黑的）。这里把 x/y 夹回屏幕内。
        """
        try:
            screen_w = self.root.winfo_screenwidth()
            screen_h = self.root.winfo_screenheight()
        except tk.TclError:
            return x, y
        return max(0, min(x, screen_w - width)), max(0, min(y, screen_h - height))

    def _default_position(self, width: int):
        """默认停在屏幕右上角，像个小挂件。"""
        try:
            screen_w = self.root.winfo_screenwidth()
            return max(20, screen_w - width - 48), 64
        except tk.TclError:
            return 80, 64

    def _move_to_default_position(self) -> None:
        """把窗口挪回屏幕右上角 —— **只挪位置，不动尺寸**。

        ★ 原来这里 `width, height = self.COMPACT`，等于每次归位都把高度压回
          126。而 126 是按**上一版**布局定的最小值：紧凑视图里加一行控件之后，
          `_place_compact` 明明已经算出 146 并设好了，紧接着归位又打回 126，
          结果第三行提示被窗口底边切掉（GUI 自测里「内容底边 ≤ 窗口高」那条
          抓的就是这个）。尺寸归布局管，这里只负责位置。
        """
        width, height = getattr(self, "_last_size", None) or self.COMPACT
        x, y = self._default_position(width)
        self.root.geometry(f"{width}x{height}+{x}+{y}")
        self.root.update_idletasks()

    def _refresh_window_chrome(self) -> None:
        """窗口映射稳了以后再设一次任务栏按钮和圆角（幂等，失败也不影响功能）。"""
        if self.closed:
            return
        self._enable_taskbar_button()
        self._apply_round_corners()

    # ---- 窗口外观 ----

    def _apply_round_corners(self, width=None, height=None, radius: int = 16) -> None:
        """真·圆角：用 SetWindowRgn 把窗口裁成圆角矩形（ctypes，纯标准库）。

        width/height 传了就按它裁（推荐：用刚设置的目标尺寸），
        没传才退回 winfo_width/height。
        """
        if os.name != "nt":
            return
        try:
            import ctypes
            self.root.update_idletasks()
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id()) or self.root.winfo_id()
            width = width or self.root.winfo_width()
            height = height or self.root.winfo_height()
            region = ctypes.windll.gdi32.CreateRoundRectRgn(0, 0, width + 1, height + 1, radius, radius)
            if region:
                ctypes.windll.user32.SetWindowRgn(hwnd, region, True)
        except Exception as exc:
            log(f"圆角裁剪失败（不影响使用）：{exc}")

    def _enable_taskbar_button(self) -> None:
        """让无边框窗口也出现在任务栏上（否则「最小化」以后就没法还原了）。"""
        if os.name != "nt":
            return
        try:
            import ctypes
            GWL_EXSTYLE = -20
            WS_EX_TOOLWINDOW = 0x00000080
            WS_EX_APPWINDOW = 0x00040000
            self.root.update_idletasks()
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id()) or self.root.winfo_id()
            style = ctypes.windll.user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
            ctypes.windll.user32.SetWindowLongW(
                hwnd, GWL_EXSTYLE, (style & ~WS_EX_TOOLWINDOW) | WS_EX_APPWINDOW
            )
        except Exception as exc:
            log(f"任务栏按钮设置失败（最小化后将无法还原）：{exc}")

    # ---- 拖动 ----

    def _drag_start(self, event) -> None:
        self._drag_offset = (event.x_root - self.root.winfo_x(), event.y_root - self.root.winfo_y())

    def _drag_move(self, event) -> None:
        if not self._drag_offset:
            return
        dx, dy = self._drag_offset
        self.root.geometry(f"+{event.x_root - dx}+{event.y_root - dy}")

    # ---- 四个圆点 ----

    def on_toggle_size(self) -> None:
        """绿点：展开 ↔ 收起（双向）。

        以前「收回紧凑尺寸」是蓝点的活，蓝点改成「收进系统托盘」之后，绿点必须能
        双向切换 —— 否则展开一次就再也回不到紧凑尺寸了。
        """
        if self.expanded:
            log("切换为紧凑尺寸 240x126")
            self._place_compact()
        else:
            log("切换为展开尺寸 340x216")
            self._place_expanded()

    def on_compact(self) -> None:
        log("切换为紧凑尺寸 240x126")
        self._place_compact()

    def on_expand(self) -> None:
        log("切换为展开尺寸 340x216")
        self._place_expanded()

    def on_minimize(self) -> None:
        """黄点：最小化到**任务栏**（桥继续跑）。

        ⚠️ Tk 不允许对 overrideredirect(True) 的窗口调 iconify()，会直接报
        "can't iconify: override-redirect flag is set"（实测）。所以先把无边框
        临时摘掉，缩到任务栏；等从任务栏还原（<Map> 事件）再把无边框装回来。
        """
        log("最小化到任务栏（桥继续跑）")
        try:
            if self.root.overrideredirect():
                self._frameless_suspended = True
                self.root.overrideredirect(False)
            self.root.iconify()
        except tk.TclError as exc:
            log(f"iconify 失败（{exc}），改用 ShowWindow 兜底")
            self._minimize_by_win32()

    # ---- 蓝点：收进系统托盘 ----

    def on_hide_to_tray(self) -> None:
        """蓝点：把窗口收进 Windows 通知区域（托盘）。

        桥**继续跑**：HTTP 是后台 daemon 线程，跟窗口在不在屏幕上没关系
        （gui_test 里专门验了「收进托盘期间 /api/health 照常 200」）。

        图标加不上就**不隐藏** —— 否则窗口没了、托盘里又什么都没有，用户只能去
        任务管理器杀进程。宁可不隐藏。
        """
        if self.closed or self.hidden_in_tray:
            return
        if not self.tray.show_icon():
            self.set_hint(f"收进托盘失败，窗口保持显示：{self.tray.last_error}", error=True)
            log(f"[X] 收进托盘失败，窗口保持显示：{self.tray.last_error}")
            return
        # 带着「无边框被摘掉」的状态进托盘的话，回来会变成一个方角窗口，先装回去
        if self._frameless_suspended:
            self._restore_frameless()
        self.hidden_in_tray = True
        self.root.withdraw()        # 实测：overrideredirect 窗口能 withdraw（iconify 不行）
        log("已收进系统托盘（桥继续跑，HTTP 照常响应）")

    def show_from_tray(self) -> None:
        """从托盘还原（左键单击图标 / 菜单「显示窗口」）。

        先 deiconify 再摘图标：反过来的话万一 deiconify 失败，窗口就既不在屏幕上、
        托盘里也没有了，等于把程序弄丢。
        """
        if self.closed or not self.hidden_in_tray:
            return
        try:
            self.root.deiconify()
        except tk.TclError as exc:
            log(f"从托盘还原失败：{exc}")
            return
        self.hidden_in_tray = False
        self.tray.hide_icon()           # 窗口都回来了，托盘里不留图标
        self._restore_frameless()       # 无边框 / 置顶都得重新装好
        # 复用 _apply_geometry：它内部会走 _clamp_to_screen —— 换过分辨率、拔过
        # 显示器、或者原来就贴着屏幕右边，还原回来都不会跑到屏幕外面去
        self._apply_geometry(self._last_size)
        self.root.after(50, self._refresh_window_chrome)
        log("已从系统托盘还原")

    def _restore_frameless(self) -> None:
        """把无边框 + 置顶装回来（从任务栏还原、从托盘还原都要用）。"""
        self._frameless_suspended = False
        try:
            if not self.root.overrideredirect():
                self.root.overrideredirect(True)
        except tk.TclError as exc:
            log(f"恢复无边框失败：{exc}")
        try:
            self.root.attributes("-topmost", True)   # 挂着跑的挂件，别被别的窗口盖住
        except tk.TclError:
            pass

    def _minimize_by_win32(self) -> None:
        """iconify 实在不行时的兜底：直接让系统最小化这个窗口。"""
        if os.name != "nt":
            return
        try:
            import ctypes
            hwnd = ctypes.windll.user32.GetParent(self.root.winfo_id()) or self.root.winfo_id()
            ctypes.windll.user32.ShowWindow(hwnd, 6)   # SW_MINIMIZE
        except Exception as exc:
            log(f"最小化失败：{exc}")

    def _on_map(self, _event=None) -> None:
        """从任务栏还原之后：把无边框 + 任务栏按钮装回去。

        托盘那条路径（withdraw/deiconify）也会触发 <Map>，但那时
        `_frameless_suspended` 是 False（进托盘之前特意清掉了），所以这里不会
        跟 show_from_tray() 打架。
        """
        if not self._frameless_suspended:
            return
        self._restore_frameless()
        self._apply_geometry(self._last_size)
        self.root.after(50, self._refresh_window_chrome)
        log("已从任务栏还原")

    def on_close(self) -> None:
        """× / Alt+F4 / 托盘菜单「退出」：停掉数据桥再退出，别留僵尸进程。"""
        if self.closed:
            return
        self.closed = True
        log("关闭窗口：停止数据桥")
        # 先撤掉还没跑的定时器：窗口销毁后再触发会报
        # "invalid command name ..._tick"（--noconsole 下还可能弹 Tk 错误框）
        if self._tick_job is not None:
            try:
                self.root.after_cancel(self._tick_job)
            except (tk.TclError, ValueError):
                pass
            self._tick_job = None
        # 窗口可能还收在托盘里：先 NIM_DELETE + DestroyWindow 把图标撤干净再毁窗口，
        # 否则托盘里会留一个「点不动、划过去才消失」的死图标。
        self.hidden_in_tray = False
        self.tray.stop()
        server = self.state.get("server")
        if server is not None:
            try:
                server.shutdown()
                server.server_close()
                log("HTTP 服务已停止")
            except Exception as exc:
                log(f"停止 HTTP 服务出错：{exc}")
        try:
            self.root.destroy()
        except tk.TclError:
            pass

    # ---- 第二行：启动 AzurPilot / 选目录 ----

    def on_start_ap(self) -> None:
        """手动启动 AzurPilot（桥在不在跑都能点，失败只写第三行小字）。"""
        self.set_hint("正在启动 AzurPilot ...")
        log("手动启动 AzurPilot")

        def worker():
            message = launch_azurpilot(self.state.get("root") or ROOT)
            if self.closed:
                return
            try:
                self.root.after(0, lambda: self.set_hint(message))
            except tk.TclError:
                pass

        threading.Thread(target=worker, name="launch-azurpilot", daemon=True).start()

    def on_pick_root(self) -> None:
        """选一个 AzurPilot 目录：立刻存进 azurpilot-root.txt 并热切换 httpd。"""
        from tkinter import filedialog
        initial = str(self.state.get("root") or APP_DIR)
        try:
            picked = filedialog.askdirectory(
                title="选择 AzurPilot 根目录（要有 module\\ 和 config\\）",
                initialdir=initial, mustexist=True,
            )
        except tk.TclError as exc:
            self.set_hint(f"打开目录选择框失败：{exc}", error=True)
            return
        if not picked:
            return

        candidate = Path(picked)
        log(f"手动选了 AzurPilot 目录：{candidate}")
        if not is_azurpilot_root(candidate):
            missing = [n for n in ("module", "config") if not (candidate / n).is_dir()]
            if missing:
                reason = f"目录里没有 {'、'.join(missing)}"
            elif not (candidate / "module" / "statistics" / "ship_exp_stats.py").is_file():
                reason = "目录里没有 module\\statistics\\ship_exp_stats.py"
            else:
                reason = "路径不可读"
            self.set_hint(f"这个目录不像 AzurPilot 根目录：{reason}", error=True)
            return

        self.switch_root(candidate)

    def switch_root(self, new_root: Path, persist: bool = True) -> bool:
        """热切换 AzurPilot 根目录：重算路径 + 重启 httpd（同一端口），失败回滚。

        一开始没找到目录、或者换电脑之后，不用关掉重开就能用。
        """
        port = self.state.get("port") or PORT
        old_root = ROOT
        old_server = self.state.get("server")

        # 1) 先停掉旧的 httpd（端口要复用，allow_reuse_address 是关着的）
        if old_server is not None:
            try:
                old_server.shutdown()
                old_server.server_close()
            except Exception as exc:
                log(f"停止旧 httpd 出错：{exc}")

        # 2) 换路径 + 重新绑定 + 重启线程
        try:
            configure_root(new_root)
            os.chdir(ROOT)
            if persist:
                write_cached_root(ROOT)
            new_server = _bind_server(port)
        except Exception as exc:
            log(f"[X] 切换到 {new_root} 失败：{exc}")
            try:
                configure_root(old_root)
                if old_root and not validate_root(old_root):
                    self.state["server"] = _bind_server(port)
                    threading.Thread(
                        target=self.state["server"].serve_forever,
                        name="bridge-http", daemon=True,
                    ).start()
                else:
                    self.state["server"] = None
            except Exception as exc2:
                log(f"[X] 回滚也失败了：{exc2}")
                self.state["server"] = None
            self.set_hint(f"切换失败：{exc}（已保留原目录）", error=True)
            return False

        self.state["server"] = new_server
        self.state["root"] = ROOT
        self.state["ok"] = True
        self.state["title"] = "网关已启动"
        self.state["url"] = _url_text(port)
        threading.Thread(
            target=new_server.serve_forever, name="bridge-http", daemon=True
        ).start()

        # 3) 界面跟着变（失败态也能变成已启动态）
        self.state["detail"] = "手机端 App「设置 → 服务器地址」填上面这个（可选中复制）："
        self.lbl_title.configure(text="网关已启动", fg=TEXT)
        self.lbl_detail.configure(text=self.state["detail"])
        self._set_entry(self.state["url"])
        self.lbl_root.configure(text=self._root_text())
        self.set_hint(f"已切换到 {self._shorten(str(ROOT), 34)} 并保存，立即生效")
        log(f"数据桥已切换到新目录：{ROOT}（{self.state['url']}）")
        return True

    @staticmethod
    def _write_entry(entry, text: str) -> None:
        """往 Entry 里写值，尊重它自己的 readonly 状态。

        entry_url 是只读展示，entry_key 是可编辑的 —— 同一段代码处理两者，
        免得各写一份、改一处忘一处。
        """
        try:
            readonly = str(entry.cget("state")) == "readonly"
            if readonly:
                entry.configure(state="normal")
            entry.delete(0, "end")
            entry.insert(0, text)
            if readonly:
                entry.configure(state="readonly", fg=TEXT)
        except tk.TclError as exc:
            log(f"更新输入框失败：{exc}")

    def _set_entry(self, text: str) -> None:
        self._write_entry(self.entry_url, text)

    # ---- 自动拉起 AP 的开关 ----

    def on_toggle_ap(self) -> None:
        enabled = bool(self._launch_ap.get())
        log(f"「exe 启动时自动拉起 AzurPilot」= {enabled}")
        save_settings({"launch_ap": enabled})
        if enabled:
            self.set_hint("已开启：下次双击 exe 会自动拉起 AzurPilot")
        else:
            self.set_hint("已关闭：双击 exe 只起数据桥（仍可手动点「启动 AzurPilot」）")

    def set_hint(self, text: str, error: bool = False) -> None:
        """第三行小字：启动结果、切换结果、报错都走这里，不弹模态框。"""
        log(f"提示：{text}")
        try:
            previous = str(self.lbl_hint.cget("text") or "")
            self.lbl_hint.configure(text=text, fg=ERR_RED if error else SUBTEXT)
        except tk.TclError:
            return
        # ★ 提示文字的行数会变（一行 ↔ 两行 ↔ 三行），窗口高度必须跟着重算 ——
        #   原来只改文字不重排，窗口仍是按**上一版提示**（往往还是空的）算的高度，
        #   长提示就被窗口底边切掉了。GUI 自测里那条
        #   「紧凑窗口里第二/三行没有被切掉」抓的就是这个。
        if previous != text:
            self._relayout()

    def _relayout(self) -> None:
        """按当前展开状态重排一次。文字变化后调用，让窗口自己长到够高。"""
        try:
            if getattr(self, "expanded", False):
                self._place_expanded()
            else:
                self._place_compact()
        except tk.TclError:
            pass

    # ---- 状态刷新 ----

    def _tick(self) -> None:
        if self.closed:
            return
        started = STATS.get("started_at")
        if started:
            minutes = int((time.time() - started) // 60)
            runtime = f"{minutes} 分钟" if minutes else "不到 1 分钟"
        else:
            runtime = "-"
        try:
            self.lbl_stats.configure(
                text=f"端口 {self.state.get('port')} · 请求 {STATS['requests']} 次 · 已运行 {runtime}"
            )
            self._tick_job = self.root.after(1000, self._tick)
        except tk.TclError:
            pass

    def _on_tk_error(self, exc_type, exc_value, exc_tb) -> None:
        """Tk 回调抛异常时默认写 stderr（--noconsole 下是 None），改成写日志。"""
        import traceback
        log("窗口回调出错：" + "".join(traceback.format_exception(exc_type, exc_value, exc_tb)))


# ── 启动 ──────────────────────────────────────────────────────

def _console_setup() -> None:
    """--headless 在真控制台里跑时，让中文正常显示、输出不被块缓冲吞掉。"""
    for stream in (sys.stdout, sys.stderr):
        if stream is None:
            continue
        try:
            stream.reconfigure(errors="replace", line_buffering=True)
        except Exception:
            pass


def _usage() -> str:
    return (
        "AzurRemBridge 用法：\n"
        "  AzurRemBridge.exe                        双击运行（自动找 AzurPilot）\n"
        "  AzurRemBridge.exe \"D:\\Tools\\AzurPilot\"  也可以把 AzurPilot 文件夹拖上来\n"
        "  AzurRemBridge.exe --root <目录>           显式指定 AzurPilot 根目录\n"
        "  AzurRemBridge.exe --port 25550           换监听端口（默认 25550）\n"
        "  AzurRemBridge.exe --no-ap                这次不顺带启动 AzurPilot\n"
        "  AzurRemBridge.exe --headless             只起服务、不开窗口（自动化测试用）\n"
    )


def _parse_args(argv):
    """解析命令行；返回 (root_arg, port, launch_ap, headless)。"""
    port = PORT
    if "--port" in argv:
        try:
            port = int(argv[argv.index("--port") + 1])
        except (IndexError, ValueError):
            raise ValueError("--port 需要跟一个端口号")

    root_arg = None
    if "--root" in argv:
        try:
            root_arg = argv[argv.index("--root") + 1]
        except IndexError:
            raise ValueError("--root 需要跟一个项目根目录路径")
    else:
        # 拖到 exe 上的文件夹：Windows 把它作为 argv[1] 传进来（可能带引号）
        root_arg = _argv_root_candidate(argv)

    return root_arg, port, "--no-ap" not in argv, "--headless" in argv


def _gui_available() -> bool:
    return tk is not None


def _build_error_state(title: str, detail: str, port: int, headless: bool) -> dict:
    return {
        "ok": False, "title": title, "detail": detail,
        "root": None, "root_source": "", "port": port,
        "url": "", "server": None, "hint": detail.splitlines()[0] if detail else "",
        "headless": headless, "launch_ap": False,
        # 出错态也把密码带上：窗口里那一行要能显示，用户才知道 App 该填什么
        "gateway_key": GATEWAY_KEY,
    }


def _run_window(state: dict) -> int:
    """建窗口 + 跑 mainloop（主线程），HTTP 服务在后台 daemon 线程里。"""
    global _ROOT_WINDOW
    root = tk.Tk()
    window = BridgeWindow(root, state)
    _ROOT_WINDOW = window
    log(f"窗口已显示：{state.get('title')}")
    if state.get("launch_ap"):
        # 窗口先显示出来，再在后台线程里拉 AzurPilot（它的 PowerShell 检查不该卡界面）
        root.after(300, window.on_start_ap)
    root.mainloop()
    log("窗口已关闭，进程退出")
    return 0


def main(argv=None) -> int:
    global PORT, GATEWAY_KEY

    argv = list(sys.argv[1:] if argv is None else argv)
    _console_setup()
    STATS["started_at"] = time.time()
    log("=" * 64)
    log(f"启动 AzurRemBridge：程序目录 = {APP_DIR}；参数 = {argv}")

    if any(item in ("-h", "--help", "/?") for item in argv):
        log(_usage())
        return 0

    # 网关密码：与 AzurPilot 的密码无关，是这个入口自己的钥匙。
    # 刻意**不写进日志** —— AzurRemBridge.log 是 issue 模板里会让人贴出来的东西，
    # 密码落进去等于随手泄露。窗口里能看能复制，文件也就在 exe 旁边。
    GATEWAY_KEY = load_or_create_gateway_key()
    log(f"网关密码已就绪（{len(GATEWAY_KEY)} 位，见 {GATEWAY_KEY_PATH.name}）")

    try:
        root_arg, port, launch_ap, headless = _parse_args(argv)
    except ValueError as exc:
        log(f"[X] {exc}")
        if not _gui_available() or headless:
            return 2
        return _run_window(_build_error_state("参数不对", str(exc), PORT, headless))

    PORT = port
    if "--no-ap" not in argv:
        launch_ap = bool(load_settings().get("launch_ap", launch_ap))

    # 1) 找 AzurPilot
    log("正在查找 AzurPilot ...")
    try:
        root, source = find_azurpilot_root(root_arg)
    except Exception as exc:
        log(f"[X] 查找 AzurPilot 出错：{type(exc).__name__}: {exc}")
        root, source = None, f"查找出错：{exc}"

    if root is None:
        detail = (
            "没找到 AzurPilot。任选一种解决：\n"
            "1) 把 AzurPilot 文件夹拖到这个 exe 上再运行\n"
            "2) 点窗口第二行的文件夹按钮手动选目录\n"
            "3) 设环境变量 AZURPILOT_ROOT 指向它\n"
            "判定标准：目录要同时有 module\\、config\\ 和 "
            "module\\statistics\\ship_exp_stats.py"
        )
        log(f"[X] 没找到 AzurPilot（{source}）")
        if headless or not _gui_available():
            log(detail)
            return 3
        state = _build_error_state("找不到 AzurPilot", detail, PORT, headless)
        state["hint"] = "没找到 AzurPilot：点右边的文件夹按钮指定目录，或把文件夹拖到 exe 上"
        return _run_window(state)

    log(f"已找到 AzurPilot：{root}（来源：{source}）")
    try:
        configure_root(root)
    except Exception as exc:
        log(f"[X] 解析目录失败：{exc}")
        if headless or not _gui_available():
            return 3
        return _run_window(_build_error_state("AzurPilot 目录不可用", str(exc), PORT, headless))

    problems = validate_root(ROOT)
    if problems:
        detail = "\n".join(problems) + "\nAzurPilot 根目录应同时含 module\\ 与 config\\。"
        log(f"[X] {detail}")
        if headless or not _gui_available():
            return 3
        return _run_window(_build_error_state("AzurPilot 目录不对", detail, PORT, headless))

    write_cached_root(ROOT)

    # 2) 起服务（失败要在窗口里说清楚，不能一闪而过）
    log("正在启动数据桥 ...")
    try:
        server = _bind_server(PORT)
    except OSError as exc:
        if _something_listening(PORT):
            detail = (
                f"端口 {PORT} 已经被占用 —— 多半是已经有一个数据桥在跑了。\n"
                "看看任务栏里有没有别的 AzurRem 挂件；\n"
                f"真想同时跑两个：AzurRemBridge.exe --port {PORT + 1}"
            )
        else:
            detail = (
                f"端口 {PORT} 监听失败：{exc}\n"
                "可能是安全软件拦了 bind，换个端口试试。"
            )
        log(f"[X] {detail}")
        if headless or not _gui_available():
            return 4
        return _run_window(_build_error_state("数据桥启动失败", detail, PORT, headless))

    url = _url_text(PORT)

    for item in (
        f"数据库不存在：{DB_PATH}" if not DB_PATH.exists() else None,
        f"menu.json 不存在：{MENU_PATH}" if not MENU_PATH.exists() else None,
        f"CL1 数据库不存在：{CL1_DB_PATH}" if not CL1_DB_PATH.exists() else None,
    ):
        if item:
            log(f"警告：{item}（对应接口会返回错误，其它接口正常）")

    thread = threading.Thread(target=server.serve_forever, name="bridge-http", daemon=True)
    thread.start()

    state = {
        "ok": True, "title": "网关已启动",
        "detail": "手机端 App「设置 → 服务器地址」填上面这个（可选中复制）：",
        "root": ROOT, "root_source": source, "port": PORT, "url": url,
        "gateway_key": GATEWAY_KEY,
        "server": server, "headless": headless, "launch_ap": launch_ap,
        "hint": "正在检查 AzurPilot ..." if launch_ap else "已关闭自动拉起，可手动点左侧按钮",
    }
    log(f"网关已启动：{url}（root={ROOT}，来源={source}）")

    if headless:
        if launch_ap:
            log("AzurPilot：" + launch_azurpilot(ROOT))
        try:
            thread.join()
        except KeyboardInterrupt:
            log("收到 Ctrl+C，停止数据桥")
        finally:
            server.shutdown()
            server.server_close()
        return 0

    if not _gui_available():
        log(f"[X] 这台机器上没有 tkinter（{_TK_ERROR}），起不了窗口；可用 --headless 只跑服务")
        return 5

    return _run_window(state)


def _install_excepthook() -> None:
    """未捕获异常写日志（--noconsole 下没人看得到 traceback）。"""
    import traceback

    def hook(exc_type, exc_value, exc_tb):
        log("未捕获异常：" + "".join(traceback.format_exception(exc_type, exc_value, exc_tb)))

    sys.excepthook = hook


def _show_fatal_window(message: str) -> None:
    """最兜底的一屏。

    --noconsole 下没有控制台，启动流程本身炸掉的话用户什么都看不到 ——
    这里硬撑一个窗口出来，明说「启动失败，详见 xxx.log」。
    """
    if tk is None:
        return
    try:
        root = tk.Tk()
        state = _build_error_state(
            "启动失败", f"{message}\n\n详细信息见：{LOG_PATH}", PORT, False
        )
        state["hint"] = f"详见 {LOG_PATH.name}"
        window = BridgeWindow(root, state)
        window.on_expand()          # 直接摊开，让原因看得全
        root.mainloop()
    except Exception:
        pass


if __name__ == "__main__":
    _install_excepthook()
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except BaseException as exc:  # 兜底：任何异常都要写日志 + 在窗口里说清楚
        import traceback
        log("启动失败：" + "".join(traceback.format_exception(type(exc), exc, exc.__traceback__)))
        _show_fatal_window(f"{type(exc).__name__}: {exc}\n详见 {LOG_PATH}")
        raise SystemExit(1)
