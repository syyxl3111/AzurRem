package com.azurpilot.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.ApPoint
import com.azurpilot.mobile.data.CommissionIncome
import com.azurpilot.mobile.data.CommissionPeriod
import com.azurpilot.mobile.data.CommissionRow
import com.azurpilot.mobile.data.TrendPoint
import com.azurpilot.mobile.data.downsampleAp
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.BRIDGE_DOWN_HINT
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.components.AcrylicSurfacePlaceholder
import com.azurpilot.mobile.ui.components.EmptyCard
import com.azurpilot.mobile.ui.components.Hairline
import com.azurpilot.mobile.ui.components.LargeTitle
import com.azurpilot.mobile.ui.components.SectionTitle
import com.azurpilot.mobile.ui.components.StatsSectionSkeleton
import com.azurpilot.mobile.ui.components.StatusCard
import com.azurpilot.mobile.ui.formatDayShort
import com.azurpilot.mobile.ui.formatNumber
import com.azurpilot.mobile.ui.taskLabel
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.NumeralSmall
import com.azurpilot.mobile.ui.theme.ResourceColors
import java.util.Locale
import kotlin.math.roundToLong

private const val AP_BUCKETS = 90

@Composable
fun StatsScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onCommissionPeriod: (CommissionPeriod) -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.acrylic

    val chartPoints = remember(state.apTimeline) { downsampleAp(state.apTimeline, AP_BUCKETS) }
    val latestAp = state.apTimeline.lastOrNull()

    // 资源历史按「当前资源清单」的顺序排，标签直接复用服务器返回的中文名
    val trendRows = remember(state.resources, state.resourceHistory) {
        state.resources.mapNotNull { res ->
            val series = state.resourceHistory[res.key] ?: return@mapNotNull null
            if (series.size < 2) null else res to series
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = insets.horizontal,
                end = insets.horizontal,
                top = insets.top,
            ),
    ) {
        LargeTitle("统计")

        StatusCard(
            instance = state.instance,
            running = state.running,
            stateCode = state.stateCode,
            currentTask = state.taskTree?.nameOf(state.currentTask) ?: taskLabel(state.currentTask),
            lastSyncAt = state.lastSyncAt,
            connected = state.connected,
            refreshing = state.refreshing,
            onRefresh = onRefresh,
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = insets.bottom, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            // ── 行动力曲线（大图） ──
            item(key = "apTitle") { SectionTitle("总行动力 · 含未开箱") }
            item(key = "apCard") {
                AcrylicSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        if (state.statsLoading && chartPoints.isEmpty()) {
                            AcrylicSurfacePlaceholder(height = 110.dp)
                        } else if (chartPoints.size < 2 || latestAp == null) {
                            Text(
                                text = "还没有足够的行动力采样点。\n" +
                                    "AzurPilot 在大世界任务运行时会持续记录，攒够 2 个点就会出曲线。",
                                style = MaterialTheme.typography.bodySmall,
                                color = t.textSecondary,
                            )
                        } else {
                            val minAp = chartPoints.minOf { it.ap }
                            val maxAp = chartPoints.maxOf { it.ap }

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                // 大数字给当前值，括号里给总量 —— 与主页「行动力 40 (3040)」的读法一致。
                                // 曲线本身画的是总量（ap_total），因为它才反映真实消耗。
                                Text(
                                    text = formatNumber(latestAp.ap.toLong()),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = t.textPrimary,
                                )
                                latestAp.apTotal?.let { total ->
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "(${formatNumber(total.toLong())})",
                                        style = NumeralSmall,
                                        color = t.textTertiary,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "低 $minAp · 高 $maxAp",
                                    style = NumeralSmall,
                                    color = t.textTertiary,
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            LineChart(
                                xs = chartPoints.map { it.epochMillis },
                                ys = chartPoints.map { it.ap.toFloat() },
                                color = t.accent,
                                showEndDot = true,
                                fillAlpha = 0.22f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                            )

                            Spacer(Modifier.height(8.dp))

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    formatDayShort(chartPoints.first().epochMillis),
                                    style = NumeralSmall,
                                    color = t.textTertiary,
                                )
                                Text(
                                    "${state.apTimeline.size} 个采样点",
                                    style = NumeralSmall,
                                    color = t.textTertiary,
                                )
                                Text(
                                    formatDayShort(chartPoints.last().epochMillis),
                                    style = NumeralSmall,
                                    color = t.textTertiary,
                                )
                            }
                        }
                    }
                }
            }

            // ── 全资源变化趋势 ──
            item(key = "trendTitle") {
                Spacer(Modifier.height(4.dp))
                SectionTitle("全资源变化趋势 · 近 7 天")
            }
            item(key = "trendCard") {
                when {
                    state.historyLoading && trendRows.isEmpty() -> {
                        AcrylicSurfacePlaceholder(height = 260.dp)
                    }

                    trendRows.isEmpty() -> {
                        EmptyCard(
                            title = "取不到资源历史",
                            detail = state.historyError ?: BRIDGE_DOWN_HINT,
                        )
                    }

                    else -> {
                        AcrylicSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                trendRows.forEachIndexed { index, (res, series) ->
                                    val color = ResourceColors.of(res.key, t.isDark)
                                    val values = series.map { it.value }
                                    val latest = values.last()
                                    val min = values.min()
                                    val max = values.max()

                                    TrendRow(
                                        label = res.label,
                                        color = color,
                                        latest = latest,
                                        min = min,
                                        max = max,
                                        series = series,
                                    )

                                    if (index != trendRows.lastIndex) {
                                        HorizontalDivider(
                                            thickness = 0.5.dp,
                                            color = t.divider,
                                            modifier = Modifier.padding(start = 15.dp),
                                        )
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "共 ${state.historySamples} 条快照 · " +
                                        "数据来自 azurstats_local.db",
                                    style = NumeralSmall,
                                    color = t.textTertiary,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── 侵蚀1（CL1） ──
            // 列与口径**逐格对齐 PC 的「大世界数据收集」表**（app_stat_opsi.py:267-307），
            // 包括最后那几列来自 get_ship_exp_stats() 的派生值。
            item(key = "cl1Title") {
                Spacer(Modifier.height(4.dp))
                SectionTitle("侵蚀1 · 本月大世界")
            }
            item(key = "cl1Card") {
                val cl1 = state.cl1Stats
                val exp = state.shipExp
                when {
                    cl1 == null && state.statsLoading -> AcrylicSurfacePlaceholder(height = 320.dp)

                    cl1 == null -> EmptyCard(
                        title = "暂无侵蚀1数据",
                        detail = "AzurPilot 还没写出本月的大世界统计。",
                    )

                    else -> AcrylicSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            // PC 把这行单独放在表格上方
                            StatRow("当月购买体力", formatNumber(cl1.apBought.toLong()))
                            Hairline(startInset = 0.dp)

                            StatRow("统计月份", cl1.month.ifBlank { "—" })
                            StatRow("战斗场次", formatNumber(cl1.battleCount.toLong()))
                            StatRow("战斗轮次", formatNumber(cl1.battleRounds.toLong()))
                            StatRow("出击消耗", formatNumber(cl1.sortieCost))
                            StatRow("遇见明石次数", "${cl1.akashiEncounters} 次")
                            StatRow("遇见明石概率", percent2(cl1.akashiRate))
                            StatRow("吊机次数", "${cl1.sirenResearchDevices} 次")
                            StatRow("吊机概率", percent2(cl1.sirenRate))
                            StatRow("平均体力", cl1.averageStamina.toString())
                            StatRow("净赚体力", formatNumber(cl1.netAp))
                            StatRow("循环效率", percent2(cl1.loopEfficiency))

                            // ↓ 以下取自 /api/ship_exp（同源于 PC 的 get_ship_exp_stats()）
                            if (exp != null) {
                                StatRow(
                                    "经验效率",
                                    "${formatNumber(exp.expPerHour.roundToLong())}/小时",
                                )
                                StatRow("平均战斗时间", "${oneDecimal(exp.avgBattleSeconds)}秒")
                                StatRow("平均一轮时长", "${oneDecimal(exp.avgRoundSeconds)}秒")
                                StatRow("今日战斗", formatNumber(exp.todayBattleCount.toLong()))
                                StatRow("今日经验", formatNumber(exp.todayExp))
                                StatRow("今日运行", "${exp.todayRunMinutes}分钟")
                            }
                        }
                    }
                }
            }

            // ── 委托收益统计 ──
            // 口径与列名对齐 PC 的 app_stat_commission.py（源头 config/cl1_data.db）
            item(key = "commissionTitle") {
                Spacer(Modifier.height(4.dp))
                SectionTitle("委托收益统计")
            }
            item(key = "commissionCard") {
                CommissionCard(
                    income = state.commission,
                    period = state.commissionPeriod,
                    loading = state.commissionLoading,
                    error = state.commissionError,
                    onPeriod = onCommissionPeriod,
                )
            }

            // ── 耄耋相接 · 数据收集（按侵蚀等级 3 / 5） ──
            // 和下面那个「收获」是**两张不同的表**，PC 端两张都显示
            item(key = "meowHazardTitle") {
                Spacer(Modifier.height(4.dp))
                SectionTitle("耄耋相接 · 数据收集")
            }
            item(key = "meowHazardCard") {
                val hz = state.meowHazard
                when {
                    state.meowHazardLoading && hz == null ->
                        AcrylicSurfacePlaceholder(height = 200.dp)

                    hz == null || hz.rows.isEmpty() -> EmptyCard(
                        title = "暂无耄耋相接数据收集",
                        detail = BRIDGE_DOWN_HINT,
                    )

                    else -> AcrylicSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = "统计月份 ${hz.month.ifBlank { "—" }}",
                                style = NumeralSmall,
                                color = t.textTertiary,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                            )
                            hz.rows.forEachIndexed { index, row ->
                                if (index > 0) Hairline(startInset = 0.dp)
                                StatGroupHeader("侵蚀 ${row.hazardLevel} 级")
                                StatRow("　战斗场次", formatNumber(row.battleCount.toLong()))
                                StatRow("　出击轮次", trimNumber(row.rounds))
                                StatRow("　平均战斗时间", "${oneDecimal(row.avgBattleTime)}秒")
                                StatRow("　平均一轮时长", "${oneDecimal(row.avgRoundTime)}秒")
                                StatRow("　吊机次数", "${row.sirenCount} 次")
                                StatRow("　吊机概率", percent2(row.sirenRate * 100.0))
                            }
                        }
                    }
                }
            }

            // ── 耄耋相接 · 收获（战利品，来自 CSV） ──
            item(key = "meowTitle") {
                Spacer(Modifier.height(4.dp))
                SectionTitle("耄耋相接 · 收获")
            }
            item(key = "meowCard") {
                val meow = state.meowStats
                when {
                    state.meowLoading && meow == null -> AcrylicSurfacePlaceholder(height = 120.dp)

                    meow == null || !meow.available -> EmptyCard(
                        title = "暂无耄耋相接收获数据",
                        detail = meow?.reason
                            ?: "完成一次耄耋相接（大世界指挥喵）后会自动记录到 azurstat_meowofficer_farming.csv。",
                    )

                    else -> AcrylicSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            meow.rows.filter { it.rounds > 0 }.forEach { row ->
                                StatRow("侵蚀 ${row.level}", "${row.rounds} 轮")
                                StatRow("　平均黄币/轮", oneDecimal(row.coinPerRound))
                                StatRow("　平均金菜/轮", oneDecimal(row.goldPerRound))
                                StatRow("　平均深渊/轮", oneDecimal(row.abyssPerRound))
                                StatRow("　平均隐秘/轮", oneDecimal(row.obscurePerRound))
                            }
                        }
                    }
                }
            }

            // ── 每日经验检测 ──
            item(key = "expTitle") {
                Spacer(Modifier.height(4.dp))
                SectionTitle("每日经验检测")
            }
            item(key = "expCard") {
                val exp = state.shipExp
                when {
                    state.shipExpLoading && exp == null -> AcrylicSurfacePlaceholder(height = 180.dp)

                    exp == null || !exp.available -> EmptyCard(
                        title = "暂无经验数据",
                        detail = exp?.reason
                            ?: "运行「大世界练级（侵蚀1）」后会在 log/cl1/<实例>/ship_exp_data.json 里生成。",
                    )

                    else -> AcrylicSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            StatRow("上次检测", exp.lastCheckTime.ifBlank { "—" })
                            StatRow("目标等级", "Lv.${exp.targetLevel}")
                            if (exp.avgRoundSeconds > 0) {
                                StatRow("平均一轮侵蚀1", "${oneDecimal(exp.avgRoundSeconds)} 秒")
                            }
                            if (exp.avgBattleSeconds > 0) {
                                StatRow("平均战斗耗时", "${oneDecimal(exp.avgBattleSeconds)} 秒")
                            }

                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = t.divider,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp),
                            )

                            exp.ships.forEach { ship ->
                                ShipExpRowView(ship)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一艘船的练级进度：左边舰位+等级，右边还差多少 */
@Composable
private fun ShipExpRowView(ship: com.azurpilot.mobile.data.ShipExpRow) {
    val t = AppTheme.acrylic
    val done = ship.expNeeded <= 0L

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${ship.position} 号位",
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary,
            modifier = Modifier.width(54.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = "Lv.${ship.level}",
                style = MaterialTheme.typography.bodyLarge,
                color = t.textPrimary,
            )
            Text(
                text = if (done) {
                    "已达目标 Lv. 要求"
                } else {
                    "还需 ${ship.battlesNeeded} 场 · ${ship.timeNeeded}"
                },
                style = NumeralSmall,
                color = if (done) t.success else t.textTertiary,
            )
        }
        Text(
            text = if (done) "✓" else formatNumber(ship.expNeeded),
            style = NumeralSmall,
            color = if (done) t.success else t.textPrimary,
        )
    }
}

private fun percent(v: Double): String =
    if (v <= 0.0) "—" else String.format(Locale.US, "%.1f%%", v * 100)

private fun oneDecimal(v: Double): String = String.format(Locale.US, "%.1f", v)

/**
 * 百分比，**固定两位小数**（和 PC 表格一致：4.90% / 5.30% / 12.81%）。
 * 传进来的已经是百分数本身（37.5 表示 37.5%），不是 0~1 的比例。
 */
private fun percent2(v: Double): String = String.format(Locale.US, "%.2f%%", v)

/** 轮次这类数：整数就不显示小数点，否则保留 1 位（PC 是 round(x, 1)） */
private fun trimNumber(v: Double): String {
    val rounded = Math.round(v * 10.0) / 10.0
    return if (rounded == Math.floor(rounded)) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

/** 一行资源趋势：色点 + 名称 + 当前值 + 迷你曲线 */
@Composable
private fun TrendRow(
    label: String,
    color: Color,
    latest: Long,
    min: Long,
    max: Long,
    series: List<TrendPoint>,
) {
    val t = AppTheme.acrylic

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 15.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(9.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatNumber(min)} ~ ${formatNumber(max)}",
                style = NumeralSmall,
                color = t.textTertiary,
                maxLines = 1,
            )
        }

        Text(
            text = formatNumber(latest),
            style = NumeralSmall,
            color = t.textPrimary,
            maxLines = 1,
        )

        Spacer(Modifier.width(10.dp))

        LineChart(
            xs = series.map { it.epochMillis },
            ys = series.map { it.value.toFloat() },
            color = color,
            showEndDot = false,
            fillAlpha = 0f,
            modifier = Modifier
                .width(74.dp)
                .height(26.dp),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val t = AppTheme.acrylic
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = NumeralSmall,
            color = t.textPrimary,
        )
    }
}

/** 卡片内的小分组标题（比如「侵蚀 3 级」） */
@Composable
private fun StatGroupHeader(text: String) {
    val t = AppTheme.acrylic
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = t.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 10.dp),
    )
}

/**
 * 委托收益统计卡片。
 *
 * 对齐 PC 的 `app_stat_commission.py`，但**不照搬那张四列表格** ——
 * 「物品 / 数量 / 委托次数 / 平均每次」四列在手机宽度下每列只剩七八个字符，
 * 中文名（「心智魔方」）会直接折行。改成两行式：
 *
 *     ● 心智魔方              1,234
 *        56 次 · 平均 22.0
 *
 * 信息一个不少，但每一行都能一眼读完。
 */
@Composable
private fun CommissionCard(
    income: CommissionIncome?,
    period: CommissionPeriod,
    loading: Boolean,
    error: String?,
    onPeriod: (CommissionPeriod) -> Unit,
) {
    val t = AppTheme.acrylic

    when {
        loading && income == null -> AcrylicSurfacePlaceholder(height = 240.dp)

        // ★ 「读不到」和「零收益」必须分开。
        //   桥没开的时候如果显示成「本月暂无委托收益」，用户会以为自己真的没收成，
        //   而实际上是数据根本没取到 —— 这两种情况要做的事完全不同。
        income == null -> EmptyCard(
            title = "读不到委托收益",
            detail = error ?: BRIDGE_DOWN_HINT,
        )

        else -> AcrylicSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                // ── 周期切换（今日 / 本周 / 本月）──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    CommissionPeriod.entries.forEach { p ->
                        val selected = p == period
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (selected) t.accent else t.track)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onPeriod(p) }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else t.textSecondary,
                            )
                        }
                    }
                }

                Hairline(startInset = 0.dp)

                if (income.isEmpty) {
                    Text(
                        text = "暂无委托收益数据，运行委托后将自动更新",
                        style = MaterialTheme.typography.bodySmall,
                        color = t.textSecondary,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp),
                    )
                } else {
                    income.rows.forEach { row ->
                        CommissionRowItem(row)
                    }

                    Hairline(startInset = 0.dp)
                    Text(
                        text = "委托总次数 ${income.totalCommissions}",
                        style = NumeralSmall,
                        color = t.textTertiary,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    )

                    if (income.recent.isNotEmpty()) {
                        StatGroupHeader("最近委托记录")
                        income.recent.forEach { entry ->
                            RecentCommissionRow(
                                time = commissionTime(entry.ts),
                                parts = entry.items.entries
                                    .filter { it.value > 0 }
                                    .joinToString("  ") { (name, amount) ->
                                        "${commissionLabel(name)}×$amount"
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一种资源：左边色点 + 中文名，右边数量；下面一行小字给次数与均值 */
@Composable
private fun CommissionRowItem(row: CommissionRow) {
    val t = AppTheme.acrylic
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    // 桥给的 hex 解析不出来就退回主题色 —— 一个脏字段不该让整页崩
                    .background(row.parsedColor ?: t.textTertiary),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyMedium,
                color = t.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatNumber(row.total),
                style = MaterialTheme.typography.titleMedium,
                color = t.textPrimary,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${row.count} 次 · 平均 ${trimNumber(row.avg)}",
            style = MaterialTheme.typography.labelSmall,
            color = t.textTertiary,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}

/** 一条最近的委托结算：左边时间，右边「资源×数量」串 */
@Composable
private fun RecentCommissionRow(time: String, parts: String) {
    val t = AppTheme.acrylic
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = NumeralSmall,
            color = t.textTertiary,
            maxLines = 1,
            // 100dp：`09-11 16:57` 在这个字号下要 ~96dp，给 78dp 会断成两行
            // （实测截到过「09-11」/「16:57」上下分开）。右边那串有 weight(1f)，
            // 少几个 dp 也不会挤坏。
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = parts,
            style = MaterialTheme.typography.bodySmall,
            color = t.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** `2026-09-11T12:34:56` → `09-11 12:34`（PC 端也是这个格式） */
private fun commissionTime(ts: String): String {
    if (ts.length < 16) return ts.ifBlank { "--" }
    return ts.substring(5, 10) + " " + ts.substring(11, 16)
}

/** 资源键 → 中文名。**兜底表**，正常路径下桥已经给了 label */
private fun commissionLabel(name: String): String = when (name) {
    "Gem" -> "钻石"
    "Cube" -> "心智魔方"
    "Chip" -> "心智"
    "Oil" -> "石油"
    "Coin" -> "物资"
    else -> name
}

/**
 * 通用折线。
 *
 * 纵轴按数据自身的 min/max 归一化 —— 资源值和行动力的量纲差了几个数量级，
 * 看趋势只需要相对形状。
 */
@Composable
private fun LineChart(
    xs: List<Long>,
    ys: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    showEndDot: Boolean = false,
    fillAlpha: Float = 0f,
) {
    Canvas(modifier) {
        if (ys.size < 2) return@Canvas

        val minY = ys.min()
        val maxY = ys.max()
        val range = (maxY - minY).takeIf { it > 0f } ?: 1f

        val padTop = size.height * 0.12f
        val padBottom = size.height * 0.08f
        val usable = (size.height - padTop - padBottom).coerceAtLeast(1f)
        val stepX = size.width / (ys.size - 1)

        val line = Path()
        ys.forEachIndexed { index, value ->
            val x = index * stepX
            val y = padTop + (1f - (value - minY) / range) * usable
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        if (fillAlpha > 0f) {
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = fillAlpha), Color.Transparent),
                ),
            )
        }

        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = if (size.height > 60f) 2.dp.toPx() else 1.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        if (showEndDot) {
            val lastY = padTop + (1f - (ys.last() - minY) / range) * usable
            drawCircle(
                color = color,
                radius = 3.2.dp.toPx(),
                center = Offset(size.width, lastY),
            )
        }
    }
}
