package com.azurpilot.mobile.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.azurpilot.mobile.data.ApPoint
import com.azurpilot.mobile.data.Cl1Stats
import com.azurpilot.mobile.data.ResourceItem
import com.azurpilot.mobile.data.RunState
import com.azurpilot.mobile.data.ScheduledTask
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.screens.HomeScreen
import com.azurpilot.mobile.ui.screens.LogsScreen
import com.azurpilot.mobile.ui.screens.SettingsScreen
import com.azurpilot.mobile.ui.screens.StatsScreen
import com.azurpilot.mobile.ui.screens.TasksScreen
import com.azurpilot.mobile.ui.theme.AppTheme

/**
 * Compose 预览截图测试。
 *
 * 用 layoutlib 离屏渲染成 PNG，不需要模拟器，也不会打扰正在跑 AzurPilot 的模拟器。
 *   ./gradlew updateDebugScreenshotTest   → 生成参考图
 *   ./gradlew validateDebugScreenshotTest → 与参考图比对
 *
 * 假数据直接取自 25548 端口的实测返回值，宽度统一 360dp
 * （等于实机 1080×1920 @480dpi，是最容易拥挤的那档）。
 */

private const val W = 360
private const val H = 860
private val INSETS = ScreenInsets(horizontal = 14.dp, top = 34.dp, bottom = 96.dp)

private fun res(
    key: String,
    label: String,
    value: Long?,
    limit: Long? = null,
    total: Long? = null,
    never: Boolean = false,
) = ResourceItem(
    key = key,
    label = label,
    value = value,
    limit = limit,
    total = total,
    // 保持截图基线稳定：relativeAge 对未来时间始终显示“刚刚”。
    lastUpdateRaw = if (never) "2020-01-01 00:00:00" else "2099-01-01 00:00:00",
    never = never,
)

private val resources = listOf(
    res("Oil", "石油", 2517, limit = 11200),
    res("Coin", "物资", 18981, limit = 68700),
    res("Gem", "钻石", 85),
    res("Pt", "活动PT", 19120),
    res("Cube", "魔方", 86),
    res("ActionPoint", "行动力", 80, total = 3080),
    res("YellowCoin", "大世界黄币", 117581),
    res("PurpleCoin", "大世界紫币", 2452),
    res("Core", "核心数据", null, never = true),
    res("Medal", "勋章", null, never = true),
    res("Merit", "功勋", null, never = true),
    res("GuildCoin", "舰队币", null, never = true),
)

private val overflowResources = resources.map {
    if (it.key == "Oil") it.copy(value = 12480) else it
}

private val queue = listOf(
    "Commission" to "2026-09-11 02:38:02",
    "OpsiAshBeacon" to "2026-09-11 02:48:35",
    "Dorm" to "2026-09-11 04:41:13",
    "Guild" to "2026-09-11 06:00:00",
    "Reward" to "2026-09-11 06:00:00",
    "Tactical" to "2026-09-11 07:37:40",
    "Exercise" to "2026-09-11 12:00:00",
    "Meowfficer" to "2026-09-12 00:00:00",
    "Daily" to "2026-09-12 00:00:00",
    "Hard" to "2026-09-12 00:00:00",
    "Gacha" to "2026-09-12 00:00:00",
    "Freebies" to "2026-09-12 00:00:00",
    "Minigame" to "2026-09-12 00:00:00",
    "PrivateQuarters" to "2026-09-12 00:00:00",
    "OpsiShop" to "2026-09-12 00:00:00",
    "OpsiDaily" to "2026-09-12 00:00:00",
    "Restart" to "2026-09-12 00:09:00",
).map { ScheduledTask(it.first, "2000-01-01 ${it.second.takeLast(8)}", null) }

private val logs = listOf(
    "═══════════════════════════════════════════ SP ═══════════════════════════════════════════",
    "2026-09-11 02:25:01.123 | INFO | [设备-控制] 点击 ( 653,  626) @ AUTO_SEARCH_REWARD",
    "2026-09-11 02:25:03.402 | INFO | <<< UI 导航到 PAGE_CAMPAIGN_MENU >>>",
    "2026-09-11 02:25:04.881 | INFO | [UI] 页面切换: page_main_white -> page_campaign_menu",
    "2026-09-11 02:25:06.204 | INFO | [活动战役] 活动可用",
    "2026-09-11 02:25:07.771 | WARNING | [设备-截图] 截图耗时 412ms，超过阈值",
    "2026-09-11 02:25:09.018 | INFO | Mode_switch_20241219 set to combat",
    "2026-09-11 02:25:10.556 | INFO | [剧情选项按钮] [(335, 281, 975, 320), (335, 366, 975, 405)]",
    "2026-09-11 02:25:12.930 | ERROR | 无法识别当前页面，重试 3 次后放弃",
    "2026-09-11 02:25:15.117 | INFO | <<< UI确保索引 >>>",
)

private val apPoints = List(28) { i ->
    ApPoint(
        epochMillis = 1_760_000_000_000L + i * 3_600_000L,
        ap = (900 + 620 * kotlin.math.sin(i / 3.4) + i * 22).toInt().coerceAtLeast(40),
        apTotal = 2_300 + i * 22,
        source = "dashboard",
    )
}

private val state = AppUiState(
    loading = false,
    refreshing = false,
    connected = true,
    running = true,
    stateCode = RunState.RUNNING,
    currentTask = "大世界调度",
    resources = resources,
    queue = queue,
    logs = logs,
    // syncAgeText 对未来时间稳定显示“刚刚同步”。
    lastSyncAt = 4_070_908_800_000L,
    apTimeline = apPoints,
    cl1Stats = Cl1Stats(
        month = "2026-09",
        battleCount = 412,
        akashiEncounters = 7,
        sirenResearchDevices = 11,
        apBought = 820,
        battleRounds = 206,
        sortieCost = 1_030,
        netAp = -210,
        loopEfficiency = -20.4,
        akashiRate = 3.4,
        sirenRate = 5.3,
        averageStamina = 117,
    ),
)

// ─────────────────────────────────────────────────────────────

@Preview(name = "home_light", widthDp = W, heightDp = H, showBackground = true)
@PreviewTest
@Composable
fun HomeLight() {
    AppTheme(darkTheme = false) {
        HomeScreen(state, onRefresh = {}, insets = INSETS)
    }
}

@Preview(name = "home_dark", widthDp = W, heightDp = H, showBackground = true)
@PreviewTest
@Composable
fun HomeDark() {
    AppTheme(darkTheme = true) {
        HomeScreen(state, onRefresh = {}, insets = INSETS)
    }
}

@Preview(name = "home_overflow", widthDp = W, heightDp = 430, showBackground = true)
@PreviewTest
@Composable
fun HomeOverflow() {
    AppTheme(darkTheme = false) {
        HomeScreen(state.copy(resources = overflowResources), onRefresh = {}, insets = INSETS)
    }
}

@Preview(name = "logs", widthDp = W, heightDp = H, showBackground = true)
@PreviewTest
@Composable
fun Logs() {
    AppTheme(darkTheme = false) {
        LogsScreen(state, onRefresh = {}, onClear = {}, onBack = {}, insets = INSETS)
    }
}

@Preview(name = "tasks", widthDp = W, heightDp = H, showBackground = true)
@PreviewTest
@Composable
fun Tasks() {
    AppTheme(darkTheme = false) {
        TasksScreen(
            state,
            onRefresh = {},
            onTrigger = {},
            onConfirmTrigger = {},
            onCancelTrigger = {},
            onOpenTask = {},
            insets = INSETS,
        )
    }
}

@Preview(name = "stats", widthDp = W, heightDp = H, showBackground = true)
@PreviewTest
@Composable
fun Stats() {
    AppTheme(darkTheme = false) {
        StatsScreen(state, onRefresh = {}, onCommissionPeriod = {}, insets = INSETS)
    }
}

@Preview(name = "settings", widthDp = W, heightDp = H, showBackground = true)
@PreviewTest
@Composable
fun Settings() {
    AppTheme(darkTheme = false) {
        SettingsScreen(
            state = state,
            onServerUrl = {},
            onBridgeUrl = {},
            onInstance = {},
            onPollSeconds = {},
            onLogLines = {},
            onThemeMode = {},
            onOpenLogs = {},
            onNotify = {},
            onReprefetch = {},
            onCheckUpdate = {},
            onDownloadUpdate = {},
            insets = INSETS,
        )
    }
}

@Preview(name = "offline", widthDp = W, heightDp = 620, showBackground = true)
@PreviewTest
@Composable
fun Offline() {
    AppTheme(darkTheme = false) {
        HomeScreen(
            state.copy(
                connected = false,
                running = false,
                stateCode = RunState.STOPPED,
                resources = emptyList(),
                error = "无法连接 AzurPilot：Connection refused",
            ),
            onRefresh = {},
            insets = INSETS,
        )
    }
}

@Preview(name = "loading", widthDp = W, heightDp = 620, showBackground = true)
@PreviewTest
@Composable
fun Loading() {
    AppTheme(darkTheme = false) {
        HomeScreen(
            state.copy(loading = true, resources = emptyList()),
            onRefresh = {},
            insets = INSETS,
        )
    }
}
