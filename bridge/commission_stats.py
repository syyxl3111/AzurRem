#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""委托收益聚合 —— 从 `config/cl1_data.db` 读原始条目并按周期汇总。

# 为什么单独一个文件

`mobile_bridge.py` 已经三千多行了，而这段逻辑**必须和 AzurPilot 的
`module/statistics/commission_income_stats.py` 逐行对齐**（数字要和 PC 页面
上的一模一样），独立成文件才好对照、好单独测。

# 数据链路

    config/cl1_data.db
      └─ 表 cl1_data(instance, month, data_json, encrypted_blob)
           └─ data_json 解析后取 "commission_income_entries"
                └─ [{"ts": "2026-09-01T20:54:27.409860",
                     "items": {"Oil": 300},
                     "commission_count": 1}, ...]

条目**按月份分库存**，所以查一个区间要枚举覆盖到的每个月。

# 口径（与项目原实现一致，别自己发挥）

- 只统计 5 种资源，顺序固定为 Gem / Cube / Chip / Oil / Coin
- 原始 items 的键有复数形式，要过一层别名表：
  Gems→Gem、Cubes→Cube、CognitiveChips→Chip、Coins→Coin
- `avg = round(total / count, 1)`，count 是**出现该资源的条目数**（不是委托数）
- `total_commissions` 是各条 `commission_count` 之和
- 周期：day = 今天；week = 本周一 00:00 起；month = 整月
- `ts` 带时区的条目会被**丢弃**（原实现的 `_parse_ts` 就是这么写的）

只读，不写任何东西。
"""

from __future__ import annotations

import json
import sqlite3
import sys
from datetime import datetime, timedelta
from pathlib import Path

#: 统计哪些资源，**顺序即展示顺序**
COMMISSION_TRACKED_ITEMS = ["Gem", "Cube", "Chip", "Oil", "Coin"]

#: 配色，与 module/statistics/commission_income_stats.py 的 COMMISSION_ITEM_META 一致
COMMISSION_ITEM_META = {
    "Gem": {"color": "#ff4757", "order": 0},
    "Cube": {"color": "#3742fa", "order": 1},
    "Chip": {"color": "#8854d0", "order": 2},
    "Oil": {"color": "#2d3436", "order": 3},
    "Coin": {"color": "#ffa502", "order": 4},
}

#: 原始数据里的复数名 → 归一化名
COMMISSION_ITEM_NAME_MAP = {
    "Gems": "Gem",
    "Cubes": "Cube",
    "CognitiveChips": "Chip",
    "Coins": "Coin",
}

#: 中文名的**兜底表**。正常路径是从 i18n 读，读不到才用这个
FALLBACK_LABELS = {
    "Gem": "钻石",
    "Cube": "心智魔方",
    "Chip": "心智",
    "Oil": "石油",
    "Coin": "物资",
}

#: i18n 里这五个键就是资源名，键名规律：Gui.Stat.CommissionIncomeItem<Name>
_I18N_KEY = {
    "Gem": "CommissionIncomeItemGem",
    "Cube": "CommissionIncomeItemCube",
    "Chip": "CommissionIncomeItemChip",
    "Oil": "CommissionIncomeItemOil",
    "Coin": "CommissionIncomeItemCoin",
}


def _log(msg: str) -> None:
    """日志钩子。mobile_bridge 会把 log 注进来，单独跑时退回 print。"""
    global _LOGGER
    (_LOGGER or (lambda m: print(m, file=sys.stderr)))(msg)


_LOGGER = None


def set_logger(fn) -> None:
    global _LOGGER
    _LOGGER = fn


# ─────────────────────────────────────────────────────────────
# i18n
# ─────────────────────────────────────────────────────────────

def load_labels(root) -> dict:
    """从 `module/config/i18n/zh-CN.json` 取五种资源的中文名。

    为什么不用写死的表：PC 页面显示的就是这份 i18n，跟着它走才能保证
    两边永远一致（用户改了语言包，App 也跟着变）。读不到就退回兜底表 ——
    这只是显示名，不该因为它读不到就让整个接口失败。
    """
    labels = dict(FALLBACK_LABELS)
    if not root:
        return labels
    path = Path(root) / "module" / "config" / "i18n" / "zh-CN.json"
    try:
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        stat = (data.get("Gui") or {}).get("Stat") or {}
        for name, key in _I18N_KEY.items():
            value = stat.get(key)
            if isinstance(value, str) and value.strip():
                labels[name] = value.strip()
    except Exception as exc:
        _log(f"[commission] 读 i18n 失败，用兜底中文名：{exc}")
    return labels


# ─────────────────────────────────────────────────────────────
# 数据库
# ─────────────────────────────────────────────────────────────

def _coerce_int(value, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def read_entries(db_path, instance: str, year: int, month: int) -> list:
    """读某个月的原始条目。读不到一律返回空列表（原实现也是这个行为）。

    注意：**不要**因为表/库不存在就抛异常 —— 新装的 AzurPilot 还没跑过委托时
    这个库可能压根没有，那是「零收益」而不是「出错」。
    """
    month_key = f"{year:04d}-{month:02d}"
    try:
        conn = sqlite3.connect(f"file:{Path(db_path).as_posix()}?mode=ro", uri=True)
    except Exception as exc:
        _log(f"[commission] 打不开 {db_path}：{exc}")
        return []
    try:
        cur = conn.cursor()
        cur.execute(
            "SELECT data_json FROM cl1_data WHERE instance = ? AND month = ?",
            (instance, month_key),
        )
        row = cur.fetchone()
        if not row or not row[0]:
            return []
        data = json.loads(row[0])
        entries = data.get("commission_income_entries") or []
        return entries if isinstance(entries, list) else []
    except Exception as exc:
        # 表不存在 / JSON 坏了 / 库被占用 —— 都当成「这个月没数据」
        _log(f"[commission] 读 {instance} {month_key} 失败：{exc}")
        return []
    finally:
        try:
            conn.close()
        except Exception:
            pass


def parse_ts(ts_str):
    """ISO 串 → naive datetime。**带时区的会被丢掉**（与原实现一致）。"""
    try:
        ts = datetime.fromisoformat(ts_str)
    except (TypeError, ValueError):
        return None
    return ts if ts.tzinfo is None else None


# ─────────────────────────────────────────────────────────────
# 聚合
# ─────────────────────────────────────────────────────────────

def build_summary(entries: list, period: str) -> dict:
    """把（已按周期过滤的）条目聚合成摘要。逐字段对齐原实现的 _build_income_summary。"""
    totals: dict = {}
    counts: dict = {}
    total_commissions = 0

    for entry in entries:
        if not isinstance(entry, dict):
            continue
        count = _coerce_int(entry.get("commission_count", 1), 0)
        total_commissions += count

        items = entry.get("items")
        if not isinstance(items, dict):
            continue
        for raw_name, amount in items.items():
            mapped = COMMISSION_ITEM_NAME_MAP.get(raw_name, raw_name)
            if mapped not in COMMISSION_TRACKED_ITEMS:
                continue
            amount = _coerce_int(amount, 0)
            totals[mapped] = totals.get(mapped, 0) + amount
            counts[mapped] = counts.get(mapped, 0) + 1

    rows = []
    for name in COMMISSION_TRACKED_ITEMS:
        total = totals.get(name, 0)
        count = counts.get(name, 0)
        rows.append({
            "name": name,
            "color": COMMISSION_ITEM_META.get(name, {}).get("color", "#888888"),
            "total": total,
            "count": count,
            "avg": round(total / count, 1) if count > 0 else 0,
        })

    return {
        "period": period,
        "totalCommissions": total_commissions,
        "rows": rows,
    }


def filter_by_period(entries: list, period: str, now: datetime | None = None) -> list:
    """按周期过滤。month 不过滤（查出来的本来就是那个月的）。"""
    if now is None:
        now = datetime.now()
    if period == "month":
        return list(entries)

    out = []
    week_start = None
    if period == "week":
        week_start = now - timedelta(days=now.weekday())
        week_start = week_start.replace(hour=0, minute=0, second=0, microsecond=0)

    for entry in entries:
        if not isinstance(entry, dict):
            continue
        ts = parse_ts(entry.get("ts", ""))
        if ts is None:
            continue
        if period == "day":
            if ts.date() == now.date():
                out.append(entry)
        elif period == "week":
            if ts >= week_start:
                out.append(entry)
    return out


def _iter_months(start: datetime, end: datetime):
    year, month = start.year, start.month
    while (year, month) <= (end.year, end.month):
        yield year, month
        if month == 12:
            year, month = year + 1, 1
        else:
            month += 1


def recent_entries(db_path, instance: str, limit: int, now: datetime | None = None) -> list:
    """最近 N 条（时间降序）。往前找 3 个月就够 —— 与原实现一致。"""
    if now is None:
        now = datetime.now()
    collected = []
    for offset in range(3):
        dt = now - timedelta(days=offset * 32)
        for entry in read_entries(db_path, instance, dt.year, dt.month):
            if isinstance(entry, dict) and parse_ts(entry.get("ts", "")) is not None:
                collected.append(entry)
        if len(collected) >= limit:
            break
    collected.sort(key=lambda e: e.get("ts", ""), reverse=True)
    return collected[:limit]


def query(
    db_path,
    instance: str,
    period: str = "month",
    limit: int = 10,
    labels: dict | None = None,
    now: datetime | None = None,
) -> dict:
    """对外唯一入口：返回 App 直接能渲染的结构。

    Args:
        db_path: `config/cl1_data.db` 的路径
        instance: 实例名
        period: day / week / month
        limit: 最近记录条数
        labels: 资源键 → 中文名；不给就用兜底表
        now: 参考时间，仅测试用

    Returns:
        {
          "instance": str,
          "period": str,
          "available": bool,          # 库里到底有没有条目（与「全是 0」不同）
          "totalCommissions": int,
          "rows": [{"name","label","color","total","count","avg"}],
          "recent": [{"ts","commissionCount","items"}],
          "monthEntries": int,        # 本月原始条目数，排查用
        }
    """
    if now is None:
        now = datetime.now()
    if period not in ("day", "week", "month"):
        period = "month"
    labels = labels or FALLBACK_LABELS

    raw = read_entries(db_path, instance, now.year, now.month)
    filtered = filter_by_period(raw, period, now)
    summary = build_summary(filtered, period)

    for row in summary["rows"]:
        row["label"] = labels.get(row["name"], row["name"])

    recent_out = []
    for entry in recent_entries(db_path, instance, limit, now):
        items = entry.get("items") or {}
        normalized = {}
        for raw_name, amount in items.items():
            mapped = COMMISSION_ITEM_NAME_MAP.get(raw_name, raw_name)
            if mapped not in COMMISSION_TRACKED_ITEMS:
                continue
            value = _coerce_int(amount, 0)
            if value > 0:
                normalized[mapped] = value
        # 一件受统计的资源都没有的条目没必要显示（PC 端也会显示成 "--"）
        if not normalized:
            continue
        recent_out.append({
            "ts": entry.get("ts", ""),
            "commissionCount": _coerce_int(entry.get("commission_count", 1), 0),
            "items": normalized,
        })

    return {
        "instance": instance,
        "period": period,
        "available": bool(raw),
        "totalCommissions": summary["totalCommissions"],
        "rows": summary["rows"],
        "recent": recent_out,
        "monthEntries": len(raw),
    }
