#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""验证：桥里移植的 ShipExpStatsLocal ≡ 项目自己的 get_ship_exp_stats()。

为什么需要它：
    /api/ship_exp 的进度计算原先直接 import 项目的
    module.statistics.ship_exp_stats，打包成 exe 后没有项目的 venv，import 必失败，
    所以把只读的那几个方法 + LIST_SHIP_EXP 逐行抄进了 mobile_bridge.py。
    「抄得对不对」不能靠看，只能跟原实现**逐字段 diff**。

怎么用（必须用项目自己的 venv 跑，因为它要 import 项目模块）：
    D:\\Tools\\AzurPilot\\.venv\\Scripts\\python.exe bridge\\verify_ship_exp_parity.py
    # 可选：--root <AzurPilot目录> --instance <实例名> --dump

判定：所有字段完全一致 → 打印 [PASS]；任何一处不同 → 打印每个字段的差异并
      以退出码 1 结束（方便挂在 CI / 打包前检查里）。**只读**，不写 AzurPilot。

注意：本脚本会把 cwd 切到 AzurPilot 根目录（项目模块内部有 ./log 这类相对路径），
      但只读文件，不修改项目里的任何东西。
"""

from __future__ import annotations

import argparse
import contextlib
import importlib.util
import io
import json
import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
BRIDGE_PY = HERE / "mobile_bridge.py"


def _load_module(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, str(path))
    if spec is None or spec.loader is None:
        raise RuntimeError(f"加载不了 {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


def _typed(value):
    """给值加类型标记，避免 0 == 0.0 这种"看着一样"的假通过。"""
    if isinstance(value, bool):
        return ("bool", value)
    if isinstance(value, int):
        return ("int", value)
    if isinstance(value, float):
        return ("float", repr(value))
    if isinstance(value, str):
        return ("str", value)
    if value is None:
        return ("none", None)
    if isinstance(value, list):
        return ("list", [_typed(v) for v in value])
    if isinstance(value, dict):
        return ("dict", {k: _typed(v) for k, v in sorted(value.items())})
    return (type(value).__name__, repr(value))


def _flatten(value, prefix="", out=None):
    """把嵌套结构拍平成 {路径: 带类型的值}，方便逐字段报差异。"""
    if out is None:
        out = {}
    if isinstance(value, dict):
        if not value:
            out[prefix or "$"] = ("dict", {})
        for key, item in value.items():
            _flatten(item, f"{prefix}.{key}" if prefix else str(key), out)
    elif isinstance(value, list):
        if not value:
            out[prefix or "$"] = ("list", [])
        for index, item in enumerate(value):
            _flatten(item, f"{prefix}[{index}]", out)
    else:
        out[prefix or "$"] = _typed(value)
    return out


def _diff(left: dict, right: dict) -> list:
    lines = []
    for key in sorted(set(left) | set(right)):
        a = left.get(key, "<缺失>")
        b = right.get(key, "<缺失>")
        if a != b:
            lines.append(
                f"  {key}\n      项目: {a[0]} = {a[1]}\n      桥  : {b[0]} = {b[1]}"
            )
    return lines


class _QuietStdout(io.TextIOBase):
    """吞掉项目 logger 的输出，但**支持 reconfigure()**。

    项目的 module/logger.py 在导入时会调 sys.stdout.reconfigure(...)，
    直接塞 io.StringIO() 进去会 AttributeError。
    """

    def write(self, text):  # noqa: D102
        return len(text)

    def flush(self):  # noqa: D102
        return None

    def isatty(self):  # noqa: D102
        return False

    def reconfigure(self, *args, **kwargs):  # noqa: D102
        return None


def _project_side(root: Path, instance: str) -> dict:
    """项目自己的实现算出来的值（原实现，作为基准）。"""
    sys.path.insert(0, str(root))
    os.chdir(root)

    # ⚠️ import 必须放在 redirect **外面**：项目 logger 在导入时就会
    #    sys.stdout.reconfigure()，被替换掉的 stdout 没有这个方法。
    #    （旧版桥把 import 放在 with 之外，正是这个原因。）
    from module.statistics.ship_exp_stats import get_ship_exp_stats  # type: ignore
    from module.os.ship_exp_data import LIST_SHIP_EXP  # type: ignore

    # 项目 logger 启动时会往 stdout 打横幅，调用期间吞掉，别污染验证输出
    with contextlib.redirect_stdout(_QuietStdout()):
        stats = get_ship_exp_stats(instance)
        progress = stats.get_all_progress(
            getattr(stats, "battle_count_at_check", 0) or 0
        )
        avg_battle = stats.get_average_battle_time()
        avg_round = stats.get_average_round_time()
        exp_per_hour = stats.get_exp_per_hour()
        today = stats.get_today_stats()
        consts = {
            "EXP_PER_BATTLE": dict(stats.EXP_PER_BATTLE),
            "AVG_EXP_PER_BATTLE": stats.AVG_EXP_PER_BATTLE,
            "BATTLES_PER_ROUND": stats.BATTLES_PER_ROUND,
        }
        format_samples = [
            stats._format_time(v)
            for v in (0, -1, 59, 60, 3599, 3600, 86399, 86400, 123456.789)
        ]

    return {
        "output": {
            "progress": progress,
            "get_average_battle_time": avg_battle,
            "get_average_round_time": avg_round,
            "get_exp_per_hour": exp_per_hour,
            "get_today_stats": today,
        },
        "constants": consts,
        "format_time": format_samples,
        "exp_table": list(LIST_SHIP_EXP),
        "data_path": str(getattr(stats, "_path", "")),
        "loaded_data_keys": sorted(stats.data.keys()),
    }


def _bridge_side(data_path: Path, exp_table_ref: list) -> dict:
    """桥里移植的版本算出来的值。"""
    bridge = _load_module(BRIDGE_PY, "mobile_bridge_parity_check")

    stats = bridge.ShipExpStatsLocal(data_path)
    progress = stats.get_all_progress(getattr(stats, "battle_count_at_check", 0) or 0)

    return {
        "output": {
            "progress": progress,
            "get_average_battle_time": stats.get_average_battle_time(),
            "get_average_round_time": stats.get_average_round_time(),
            "get_exp_per_hour": stats.get_exp_per_hour(),
            "get_today_stats": stats.get_today_stats(),
        },
        "constants": {
            "EXP_PER_BATTLE": dict(stats.EXP_PER_BATTLE),
            "AVG_EXP_PER_BATTLE": stats.AVG_EXP_PER_BATTLE,
            "BATTLES_PER_ROUND": stats.BATTLES_PER_ROUND,
        },
        "format_time": [
            stats._format_time(v)
            for v in (0, -1, 59, 60, 3599, 3600, 86399, 86400, 123456.789)
        ],
        "exp_table": list(bridge.LIST_SHIP_EXP),
        "exp_table_len": len(bridge.LIST_SHIP_EXP),
        "exp_table_ref_len": len(exp_table_ref),
        "data_path": str(stats._path),
        "loaded_data_keys": sorted(stats.data.keys()),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="桥移植版 vs 项目原实现 逐字段 diff")
    parser.add_argument("--root", default=r"D:\Tools\AzurPilot", help="AzurPilot 根目录")
    parser.add_argument("--instance", default="alas", help="实例名（log/cl1/<实例>）")
    parser.add_argument("--dump", action="store_true", help="把两边的 JSON 也打出来")
    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    data_path = root / "log" / "cl1" / args.instance / "ship_exp_data.json"

    print("=" * 68)
    print("  桥移植版  vs  项目原实现  逐字段 diff")
    print("=" * 68)
    print(f"  AzurPilot : {root}")
    print(f"  数据文件  : {data_path}")
    if not data_path.is_file():
        print("  [SKIP] 数据文件不存在，没法比对（先让项目跑一次经验检测）")
        return 2

    project = _project_side(root, args.instance)
    bridge = _bridge_side(data_path, project["exp_table"])

    failures = []

    # 1) 经验表：逐项（含长度）
    if bridge["exp_table"] != project["exp_table"]:
        failures.append("  LIST_SHIP_EXP 与项目不一致")
        for index, (a, b) in enumerate(zip(project["exp_table"], bridge["exp_table"])):
            if a != b:
                failures.append(f"    索引 {index}（{index + 1} 级）: 项目 {a} / 桥 {b}")
        if len(project["exp_table"]) != len(bridge["exp_table"]):
            failures.append(
                f"    长度: 项目 {len(project['exp_table'])} / 桥 {len(bridge['exp_table'])}"
            )
    else:
        print(f"\n  [OK] LIST_SHIP_EXP 逐项一致：{len(project['exp_table'])} 项（1-125 级）")

    # 2) 类常量
    if bridge["constants"] != project["constants"]:
        failures.append("  类常量不一致：")
        failures += _diff(_flatten(project["constants"]), _flatten(bridge["constants"]))
    else:
        print("  [OK] 类常量一致（EXP_PER_BATTLE / AVG_EXP_PER_BATTLE / BATTLES_PER_ROUND）")

    # 3) _format_time 边界值
    if bridge["format_time"] != project["format_time"]:
        failures.append("  _format_time 结果不一致：")
        failures.append(f"    项目: {project['format_time']}")
        failures.append(f"    桥  : {bridge['format_time']}")
    else:
        print(f"  [OK] _format_time 一致（9 个边界值：0/负数/59/60/3599/3600/86399/86400/123456.789）")

    # 4) 数据加载是否同一份、键是否相同
    if bridge["data_path"] != project["data_path"]:
        failures.append(
            "  读的不是同一个文件：\n"
            f"    项目: {project['data_path']}\n    桥  : {bridge['data_path']}"
        )
    if bridge["loaded_data_keys"] != project["loaded_data_keys"]:
        failures.append(
            "  _load() 读出来的顶层键不同：\n"
            f"    项目: {project['loaded_data_keys']}\n    桥  : {bridge['loaded_data_keys']}"
        )
    else:
        print(f"  [OK] 同一份 JSON，顶层键一致：{bridge['loaded_data_keys']}")

    # 5) 最终输出：逐字段
    left = _flatten(project["output"])
    right = _flatten(bridge["output"])
    diff_lines = _diff(left, right)
    print(f"\n  输出字段总数：{len(left)}（含每艘舰船的每个字段）")
    if diff_lines:
        failures.append("  输出字段有差异：")
        failures += diff_lines
    else:
        print("  [OK] 输出逐字段完全一致")

    # 人看的摘要
    print("\n  ---- get_all_progress() 结果（两边相同） ----")
    for row in project["output"]["progress"]:
        print(
            f"     位置{row['position']}: Lv{row['level']} "
            f"总经验 {row['total_exp']} / 目标 {row['target_exp']} "
            f"差 {row['exp_needed']} 还需 {row['battles_needed']} 场 "
            f"约 {row['time_needed']}"
        )
    print(
        f"\n  平均战斗 {project['output']['get_average_battle_time']}s / "
        f"平均一轮 {project['output']['get_average_round_time']}s / "
        f"经验效率 {project['output']['get_exp_per_hour']}/h"
    )
    print(f"  今日统计：{project['output']['get_today_stats']}")

    if args.dump:
        print("\n  ---- 两边完整 JSON ----")
        print("  项目: " + json.dumps(project["output"], ensure_ascii=False, sort_keys=True))
        print("  桥  : " + json.dumps(bridge["output"], ensure_ascii=False, sort_keys=True))

    print()
    if failures:
        print("  [FAIL] 有差异：")
        for line in failures:
            print(line)
        print()
        return 1

    print("=" * 68)
    print("  [PASS] 移植版与项目原实现逐字段完全一致")
    print("=" * 68)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
