#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""委托收益统计：桥的移植版 vs AzurPilot 项目原实现，逐字段 diff。

# 为什么必须做这个

桥是**纯标准库**重写的一份聚合逻辑（`bridge/commission_stats.py`），
而 PC 页面用的是 `module/statistics/commission_income_stats.py`。
两份代码读同一个库，如果口径有任何一点不一致（别名表漏一个、
avg 的取整方式不同、week 的起点算错），用户就会看到
「手机上和电脑上数字不一样」—— 而且这种偏差**很隐蔽**，
不专门对比根本发现不了。

所以这个脚本把两边的输出拉出来逐字段比。数字对不上就是失败。

用法（**必须用项目的 venv**，因为它要 import module.*）：
    D:\\Tools\\AzurPilot\\.venv\\Scripts\\python.exe bridge\\verify_commission_parity.py
"""

from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT_ROOT = Path(r"D:\Tools\AzurPilot")

sys.path.insert(0, str(HERE))
sys.path.insert(0, str(PROJECT_ROOT))

import commission_stats as mine  # noqa: E402

FAILURES: list[str] = []
CHECKS = 0


def check(name: str, ok: bool, extra: str = "") -> None:
    global CHECKS
    CHECKS += 1
    mark = "OK" if ok else "X "
    print(f"  [{mark}] {name}" + (f"  → {extra}" if extra else ""))
    if not ok:
        FAILURES.append(name)


def main() -> int:
    print("=" * 74)
    print("  委托收益统计  桥移植版 vs 项目原实现  逐字段 diff")
    print("=" * 74)

    instance = "alas"
    db_path = PROJECT_ROOT / "config" / "cl1_data.db"
    print(f"  实例     : {instance}")
    print(f"  数据库   : {db_path}")
    print(f"  存在     : {db_path.exists()}")
    print()

    if not db_path.exists():
        print("  [X] 找不到 cl1_data.db，没法比对")
        return 1

    # ── 导入项目原实现 ──
    try:
        from module.statistics.commission_income_stats import (
            COMMISSION_ITEM_META,
            COMMISSION_ITEM_NAME_MAP,
            COMMISSION_TRACKED_ITEMS,
            get_commission_income_summary,
            get_recent_commission_entries,
        )
    except Exception as exc:
        print(f"  [X] 导入项目原实现失败：{exc}")
        print("      要用项目的 venv 跑：D:\\Tools\\AzurPilot\\.venv\\Scripts\\python.exe")
        return 1

    print("──── 常量对齐 ────")
    check(
        "COMMISSION_TRACKED_ITEMS 一致（含顺序）",
        list(mine.COMMISSION_TRACKED_ITEMS) == list(COMMISSION_TRACKED_ITEMS),
        f"桥={mine.COMMISSION_TRACKED_ITEMS} 项目={COMMISSION_TRACKED_ITEMS}",
    )
    check(
        "COMMISSION_ITEM_NAME_MAP 一致",
        mine.COMMISSION_ITEM_NAME_MAP == COMMISSION_ITEM_NAME_MAP,
        str(mine.COMMISSION_ITEM_NAME_MAP),
    )
    check(
        "COMMISSION_ITEM_META 的配色一致",
        {k: v["color"] for k, v in mine.COMMISSION_ITEM_META.items()}
        == {k: v["color"] for k, v in COMMISSION_ITEM_META.items()},
    )
    labels = mine.load_labels(PROJECT_ROOT)
    check(
        "中文名（从 i18n 读）都对上了",
        all(v != k for k, v in labels.items()),
        str(labels),
    )
    print()

    # ── 三个周期逐个比 ──
    for period in ("day", "week", "month"):
        print(f"──── period = {period} ────")
        try:
            ref = get_commission_income_summary(instance, period=period)
        except Exception as exc:
            print(f"  [X] 项目原实现抛异常：{exc}")
            FAILURES.append(f"{period}: 项目原实现异常")
            continue

        got = mine.query(db_path, instance, period=period, labels=labels)

        check(
            f"[{period}] total_commissions 一致",
            ref["total_commissions"] == got["totalCommissions"],
            f"项目={ref['total_commissions']} 桥={got['totalCommissions']}",
        )

        # detail_rows 是固定顺序的 5 行，逐行逐字段比
        ref_rows = ref["detail_rows"]
        got_rows = got["rows"]
        check(f"[{period}] 行数一致", len(ref_rows) == len(got_rows),
              f"项目={len(ref_rows)} 桥={len(got_rows)}")

        if len(ref_rows) == len(got_rows):
            for r_ref, r_got in zip(ref_rows, got_rows):
                tag = f"[{period}] {r_ref['name']}"
                ok = (
                    r_ref["name"] == r_got["name"]
                    and r_ref["color"] == r_got["color"]
                    and r_ref["total"] == r_got["total"]
                    and r_ref["count"] == r_got["count"]
                    and r_ref["avg"] == r_got["avg"]
                )
                check(
                    f"{tag} 逐字段一致",
                    ok,
                    f"项目=({r_ref['total']},{r_ref['count']},{r_ref['avg']},{r_ref['color']}) "
                    f"桥=({r_got['total']},{r_got['count']},{r_got['avg']},{r_got['color']})",
                )
        print()

    # ── 最近记录 ──
    print("──── 最近委托记录（limit=10）────")
    try:
        ref_recent = get_recent_commission_entries(instance, limit=10)
        got_recent = mine.query(
            db_path, instance, period="month", limit=10, labels=labels
        )["recent"]
        check("条数一致", len(ref_recent) == len(got_recent),
              f"项目={len(ref_recent)} 桥={len(got_recent)}")

        if len(ref_recent) == len(got_recent):
            for i, (a, b) in enumerate(zip(ref_recent, got_recent)):
                # 项目返回的 items 是原始键（可能带复数），桥已经把键归一化并过滤过，
                # 所以这里先按同一套规则把项目的 items 也归一化再比
                norm = {}
                for raw_name, amount in (a.get("items") or {}).items():
                    mapped = COMMISSION_ITEM_NAME_MAP.get(raw_name, raw_name)
                    if mapped not in COMMISSION_TRACKED_ITEMS:
                        continue
                    try:
                        value = int(amount)
                    except (TypeError, ValueError):
                        continue
                    if value > 0:
                        norm[mapped] = value
                ok = (
                    a.get("ts", "") == b["ts"]
                    and int(a.get("commission_count", 1)) == b["commissionCount"]
                    and norm == b["items"]
                )
                check(f"第 {i + 1} 条一致", ok, f"项目={a.get('ts')} {norm} / 桥={b['ts']} {b['items']}")
    except Exception as exc:
        print(f"  [X] 最近记录比对失败：{exc}")
        FAILURES.append("最近记录比对异常")

    print()
    print("=" * 74)
    if FAILURES:
        print(f"  [FAIL] {len(FAILURES)} / {CHECKS} 项不一致：")
        for name in FAILURES:
            print(f"         - {name}")
    else:
        print(f"  [PASS] 全部 {CHECKS} 项一致 —— 桥上算出来的和 PC 页面完全相同")
    print("=" * 74)
    return 1 if FAILURES else 0


if __name__ == "__main__":
    raise SystemExit(main())
