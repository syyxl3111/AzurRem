package com.azurpilot.mobile.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INT_FMT: NumberFormat = NumberFormat.getIntegerInstance(Locale.CHINA)
private val CLOCK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 2,517 */
fun formatNumber(v: Long?): String = if (v == null) "—" else INT_FMT.format(v)

/** 19.1K —— 用于空间紧张处 */
fun formatCompact(v: Long?): String {
    if (v == null) return "—"
    val abs = kotlin.math.abs(v)
    return when {
        abs < 10_000 -> INT_FMT.format(v)
        abs < 1_000_000 -> trimZero(v / 1000.0) + "K"
        else -> trimZero(v / 1_000_000.0) + "M"
    }
}

private fun trimZero(d: Double): String {
    val s = String.format(Locale.US, "%.1f", d)
    return if (s.endsWith(".0")) s.dropLast(2) else s
}

/** 02:25 */
fun formatClock(epochMillis: Long): String =
    if (epochMillis <= 0L) "--:--"
    else Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(CLOCK_FMT)

/** 09-11 */
fun formatDayShort(epochMillis: Long): String =
    if (epochMillis <= 0L) "--"
    else Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd"))

/** 上次同步：「1 分钟前」 */
fun syncAgeText(epochMillis: Long): String {
    if (epochMillis <= 0L) return "尚未同步"
    val secs = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        secs < 10 -> "刚刚同步"
        secs < 60 -> "${secs} 秒前同步"
        secs < 3600 -> "${secs / 60} 分钟前同步"
        else -> "${secs / 3600} 小时前同步"
    }
}

/**
 * 缓存年龄：「刚刚」/「3 分钟前」/「2 小时前」。
 *
 * 和 [syncAgeText] 分开是有意的：那个说的是「和服务器同步」，强调的是
 * *数据新旧*；这里说的是「页面内容来自本地缓存」，强调的是*为什么这么快*。
 * 两句话在同一个界面上出现时不能长得一样，否则用户分不清哪个是哪个。
 */
fun cacheAgeText(epochMillis: Long): String {
    if (epochMillis <= 0L) return "缓存"
    val secs = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        // 阈值必须是 60 而不是 30 —— 30~59 秒时 `secs / 60` 是 0，
        // 会显示成「0 分钟前缓存」，真机上截到过
        secs < 60 -> "刚缓存"
        secs < 3600 -> "${secs / 60} 分钟前缓存"
        secs < 86400 -> "${secs / 3600} 小时前缓存"
        else -> "${secs / 86400} 天前缓存"
    }
}

/**
 * 数据桥取不到数据时给用户看的提示。
 *
 * 写法上刻意**给动作而不是给名词** ——
 * "需要 sidecar 数据桥（bridge/mobile_bridge.py）" 这种话只有写代码的人看得懂，
 * 用户看到只知道"坏了"。所以直接把要做的那一下写出来：双击哪个文件。
 *
 * 配置树 / 资源趋势 / 耄耋相接 / 经验检测共用同一句，免得各写一份、
 * 还写着不同的端口号。
 */
val BRIDGE_DOWN_HINT: String =
    "PC 上的数据桥没在运行。\n\n" +
        "在 PC 上双击 bridge\\start_bridge.bat 就行（窗口开着就别关）。\n" +
        "不想每次手动开：双击 bridge\\enable-autostart.bat 让它开机自启。"

/**
 * 任务名兜底表 —— **非权威**，只在数据桥不可达、拿不到任务树时用。
 *
 * 权威源是 `module/config/i18n/zh-CN.json` 的 `Task.<Key>.name`，
 * App 通过数据桥 `/api/task_tree` 拿到，用 `TaskTree.nameOf(key)` 查。
 *
 * 这里的取值是照抄那个文件的（2026-09-11 核对过一遍），
 * 之前手写的版本 22 条里有 13 条和 PC 对不上（比如把 `OpsiScheduling`
 * 写成「大世界调度」，PC 实际是「智能调度Plus」）。
 * 所以：**别凭感觉往里加**，要加就照抄 i18n。
 */
private val TASK_LABELS = mapOf(
    "OpsiScheduling" to "智能调度Plus",
    "OpsiAshBeacon" to "META作战",
    "OpsiAshAssist" to "META支援",
    "Commission" to "委托",
    "Tactical" to "战术学院",
    "Dorm" to "后宅",
    "Guild" to "大舰队",
    "Reward" to "收获",
    "Exercise" to "演习",
    "Meowfficer" to "指挥喵",
    "Daily" to "每日任务",
    "Hard" to "主线-困难图",
    "Gacha" to "每日抽卡",
    "Freebies" to "白嫖奖励",
    "Minigame" to "小游戏",
    "PrivateQuarters" to "宿舍计划",
    "OpsiShop" to "大世界商店Plus",
    "OpsiDaily" to "大世界每日Plus",
    "Restart" to "重启设置",
    "Main" to "主线图-1Plus",
    "Event" to "活动图-1Plus",
    "Research" to "科研",
)

fun taskLabel(task: String): String = TASK_LABELS[task] ?: task

/** 「2026-09-11 02:38:02」→ 今天只显示「02:38」，其它日子显示「09-11 02:38」 */
fun shortDateTime(raw: String): String {
    if (raw.isBlank()) return "—"
    val parts = raw.split(' ')
    if (parts.size != 2) return raw
    val date = parts[0]
    val time = parts[1].take(5)
    val today = java.time.LocalDate.now().toString()
    return if (date == today) time else "${date.takeLast(5)} $time"
}
