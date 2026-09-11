package com.azurpilot.mobile.data

// ─────────────────────────────────────────────────────────────
// 任务菜单树（来自数据桥 /api/task_tree，源头是 menu.json + i18n）
// ─────────────────────────────────────────────────────────────

data class MenuItem(val key: String, val name: String)

data class MenuGroup(
    val key: String,
    val name: String,
    val page: String,
    val collapsible: Boolean,
    val tasks: List<MenuItem>,
)

data class TaskTree(val groups: List<MenuGroup>) {
    val totalTasks: Int get() = groups.sumOf { it.tasks.size }

    /** 任务键 -> 中文名。数据来自 menu.json + i18n，93 个任务全都有 */
    private val nameIndex: Map<String, String> by lazy {
        groups.flatMap { it.tasks }.associate { it.key to it.name }
    }

    /** 取中文名；查不到返回 null，调用方再退回英文键 */
    fun nameOf(key: String): String? = nameIndex[key]

    /** 搜索：按中文名或英文 key 模糊匹配，返回命中的任务与所属分组 */
    fun search(query: String): List<Pair<MenuGroup, MenuItem>> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val lower = q.lowercase()
        return groups.flatMap { group ->
            group.tasks
                .filter { it.name.lowercase().contains(lower) || it.key.lowercase().contains(lower) }
                .map { group to it }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 任务配置（来自 MCP get_task_help + get_config）
// ─────────────────────────────────────────────────────────────

/** 一个可编辑的配置项 */
data class ConfigArg(
    val key: String,
    val name: String,
    val help: String,
    /** input / select / switch / textarea … 见 args.json 的 type 字段 */
    val type: String,
    val default: Any?,
    /** 选项：值 -> 中文名。switch 类型通常只有 true/false */
    val options: Map<String, String>,
    /** 用户当前的配置值（来自 get_config，不是 args.json 的默认值） */
    val current: Any?,
) {
    val isSwitch: Boolean get() = type == "checkbox" || type == "switch"

    /**
     * 只能在 PC 端调整的参数 —— App 里**只展示、不可点**。
     *
     * `task_priority`（任务优先级调整，`General.YukikazeTaskManager.TaskPriorityAdjustment`）
     * 在 WebUI 里是一个**拖拽排序列表**，手机上没有对应的交互；
     * 而且它的值是一大段多行文本（默认就有 20+ 行），在手机上编辑几乎必然出错 ——
     * 一旦存坏，调度顺序会整个乱掉，而用户很难发现。
     *
     * 全项目里 `type == "task_priority"` **只出现 1 次**（在 args.json 里核对过），
     * 所以按类型判断是安全的，也免得写死一个路径。
     */
    val isPcOnly: Boolean get() = type == "task_priority"

    /**
     * 显示用文本。
     *
     * 选项键在 args.json / i18n 里是 `True` / `False`（首字母大写），
     * 而配置里的实际值是 JSON 布尔 `true` / `false` —— 必须忽略大小写匹配，
     * 否则开关会显示成「开/关」而不是 i18n 里的「已启用/关闭」。
     */
    fun displayValue(): String {
        val v = current ?: return "—"
        val raw = v.toString()

        options.entries.firstOrNull { it.key.equals(raw, ignoreCase = true) }?.let {
            return it.value
        }

        return when {
            raw.equals("true", ignoreCase = true) -> "开"
            raw.equals("false", ignoreCase = true) -> "关"
            raw.isBlank() -> "—"
            else -> raw
        }
    }
}

data class ConfigGroup(
    val key: String,
    val name: String,
    val help: String,
    val args: List<ConfigArg>,
)

data class TaskConfig(
    val task: String,
    val displayName: String,
    val help: String,
    val groups: List<ConfigGroup>,
)

// ─────────────────────────────────────────────────────────────
// 耄耋相接（指挥喵）—— 来自数据桥 /api/meow_stats
// ─────────────────────────────────────────────────────────────

data class MeowRow(
    val level: Int,
    val rounds: Int,
    val coinPerRound: Double,
    val goldPerRound: Double,
    val abyssPerRound: Double,
    val obscurePerRound: Double,
)

data class MeowStats(
    val available: Boolean,
    val reason: String?,
    val rows: List<MeowRow>,
)

// ─────────────────────────────────────────────────────────────
// 耄耋相接**数据收集** —— 来自数据桥 /api/meow_hazard
//
// ⚠️ 和上面的 MeowStats 是**两张不同的表**：
//   MeowStats   = 「耄耋相接收获」（战利品：黄币/金菜/深渊/隐秘 每轮均值），
//                 源头 log/azurstat_meowofficer_farming.csv
//   MeowHazard  = 「耄耋相接数据收集」（按侵蚀等级 3 / 5 分组的场次与耗时），
//                 源头 config/cl1_data.db 的 meow_hazard_stats
// PC 端两张表都显示，所以 App 也都要。
// ─────────────────────────────────────────────────────────────

/**
 * 耄耋相接数据收集的一行（一个侵蚀等级）。
 *
 * PC 端只统计 **3 级和 5 级**（`app_stat_opsi.py:313` 的 `for hazard_level in (3, 5)`）。
 */
data class MeowHazardRow(
    val hazardLevel: Int,
    /** 战斗场次 */
    val battleCount: Int,
    /** 出击轮次（有效轮数，PC 会四舍五入到 1 位小数） */
    val rounds: Double,
    /** 平均战斗时间（秒） */
    val avgBattleTime: Double,
    /** 平均一轮耄耋相接时长（秒） */
    val avgRoundTime: Double,
    /** 吊机（塞壬研究装置）次数 */
    val sirenCount: Int,
    /** 吊机概率（0~1，显示时乘 100） */
    val sirenRate: Double,
)

data class MeowHazardStats(
    val month: String,
    val rows: List<MeowHazardRow>,
)

// ─────────────────────────────────────────────────────────────
// 每日经验检测 —— 来自数据桥 /api/ship_exp
// ─────────────────────────────────────────────────────────────

data class ShipExpRow(
    val position: Int,
    val level: Int,
    val currentExp: Long,
    val totalExp: Long,
    val targetExp: Long,
    val expNeeded: Long,
    val battlesNeeded: Int,
    val timeNeeded: String,
)

data class ShipExpDaily(
    val date: String,
    val battleCount: Int,
    val expGained: Double,
    val expPerHour: Double,
    val runTime: Double,
)

data class ShipExpStats(
    val available: Boolean,
    val reason: String?,
    val lastCheckTime: String,
    val targetLevel: Int,
    val avgBattleSeconds: Double,
    val avgRoundSeconds: Double,
    val avgMeowBattleSeconds: Double,
    val ships: List<ShipExpRow>,
    val daily: List<ShipExpDaily>,
    /** 经验效率（每小时），PC 显示成 `52388/小时` */
    val expPerHour: Double = 0.0,
    val todayBattleCount: Int = 0,
    val todayExp: Long = 0L,
    val todayRunMinutes: Int = 0,
)

// ─────────────────────────────────────────────────────────────
// 委托收益统计 —— 来自数据桥 /api/commission_income
//
// 源头是 `config/cl1_data.db` 里 `cl1_data.data_json` 的
// `commission_income_entries`（按月份分库存），聚合逻辑抄的是
// `module/statistics/commission_income_stats.py`：
//   - 只统计 5 种资源：Gem / Cube / Chip / Oil / Coin
//   - 原始数据里的名字有复数形式，要过一层别名表
//     （Gems→Gem、Cubes→Cube、CognitiveChips→Chip、Coins→Coin）
//   - 汇总：total 求和、count 计条数、avg = round(total / count, 1)
//   - 周期：day = 今天、week = 本周一 0 点起、month = 整月
// ─────────────────────────────────────────────────────────────

/** 统计周期。值要和协议里的一致（day / week / month） */
enum class CommissionPeriod(val wire: String, val label: String) {
    Day("day", "今日"),
    Week("week", "本周"),
    Month("month", "本月"),
}

/** 一种资源的汇总行 */
data class CommissionRow(
    /** 归一化后的英文键：Gem / Cube / Chip / Oil / Coin */
    val name: String,
    /** 中文名（钻石 / 心智魔方 / 心智 / 石油 / 物资），由桥从 i18n 取 */
    val label: String,
    /** PC 端用的强调色，桥直接给 hex，App 只负责解析 */
    val color: String,
    val total: Long,
    val count: Int,
    val avg: Double,
) {
    /**
     * 解析 [color]。
     *
     * 桥给的是 `#rrggbb`。解析失败返回 null，让调用方退回主题色 ——
     * 宁可少一个颜色，也不要因为一个脏字段让整个统计页崩掉。
     */
    val parsedColor: androidx.compose.ui.graphics.Color?
        get() = runCatching {
            val hex = color.removePrefix("#")
            if (hex.length != 6) null
            else androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor("#$hex"))
        }.getOrNull()
}

/** 一条委托结算记录 */
data class CommissionEntry(
    /** ISO 时间串，原样来自数据库 */
    val ts: String,
    val commissionCount: Int,
    /** 归一化后的资源名 -> 数量 */
    val items: Map<String, Long>,
)

data class CommissionIncome(
    val available: Boolean,
    val period: CommissionPeriod,
    val totalCommissions: Int,
    val rows: List<CommissionRow>,
    val recent: List<CommissionEntry>,
) {
    /** 五种资源全是 0 —— PC 端这种情况会显示「暂无委托收益数据」 */
    val isEmpty: Boolean get() = rows.all { it.total == 0L }
}
