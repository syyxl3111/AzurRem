package com.azurpilot.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.ConfigArg
import com.azurpilot.mobile.data.ConfigGroup
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.cacheAgeText
import com.azurpilot.mobile.ui.components.AcrylicSurfacePlaceholder
import com.azurpilot.mobile.ui.components.EmptyCard
import com.azurpilot.mobile.ui.components.Hairline
import com.azurpilot.mobile.ui.components.SubPageScaffold
import com.azurpilot.mobile.ui.components.SwipeBackContainer
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppTheme
import org.json.JSONObject

/**
 * 单个任务的配置编辑页。
 *
 * 结构与当前值来自两个接口，缺一不可：
 *  - `get_task_help`  → 分组/参数的中文名、类型、可选项（`default` 是 args.json 的默认值）
 *  - `get_config`     → 用户**当前**的实际配置
 *
 * 写入走 `update_config`。
 */
@Composable
fun TaskConfigScreen(
    state: AppUiState,
    task: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (String, ConfigArg, Any?) -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.acrylic
    val meta = state.configTask
    val values = state.configValues

    var pickerGroup by remember { mutableStateOf<String?>(null) }
    var pickerArg by remember { mutableStateOf<ConfigArg?>(null) }

    SwipeBackContainer(onBack = onBack, modifier = modifier) {
        SubPageScaffold(
            title = meta?.displayName ?: task,
            // 副标题平时是英文任务键（排查用）。内容来自缓存时补一句时效 ——
            // 既解释了「这次为什么这么快」，也交代了数据可能还被后台悄悄校验着。
            subtitle = if (state.configFromCache) {
                "$task · ${cacheAgeText(state.configCachedAt)}"
            } else {
                task
            },
            onBack = onBack,
            insets = insets,
            actions = {
                // 手动重读。缓存模式下这个出口是必要的：后台校验是**静默**的，
                // 用户想立刻确认最新值时必须有个明确的动作，而不是只能等。
                if (meta != null && !state.configLoading) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "重新读取",
                            tint = t.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            },
        ) {
            when {
                state.configLoading && meta == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        repeat(4) { AcrylicSurfacePlaceholder(height = 120.dp) }
                    }
                }

                meta == null -> {
                    EmptyCard(
                        title = "读不到这个任务的配置",
                        detail = state.configError ?: "AzurPilot 的 MCP 没返回数据。",
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipButton(text = "重试", filled = true) { onRetry() }
                }

                meta.groups.isEmpty() -> {
                    EmptyCard(
                        title = "这个任务没有可编辑的配置",
                        detail = "它可能是纯展示型条目（比如舰队管理里的扫描结果）。",
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = insets.bottom, top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(meta.groups.size) { index ->
                            val group = meta.groups[index]
                            ConfigGroupCard(
                                group = group,
                                values = values,
                                saving = state.configSaving,
                                onToggleSwitch = { arg, checked -> onSave(group.key, arg, checked) },
                                onOpenPicker = { arg ->
                                    pickerGroup = group.key
                                    pickerArg = arg
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // ── 选项选择 / 文本输入 ──
    val arg = pickerArg
    val groupKey = pickerGroup
    if (arg != null && groupKey != null) {
        if (arg.options.isNotEmpty()) {
            OptionPickerDialog(
                title = arg.name,
                options = arg.options,
                current = readValue(values, groupKey, arg.key)?.toString(),
                onDismiss = { pickerArg = null; pickerGroup = null },
                onPick = { value ->
                    onSave(groupKey, arg, value)
                    pickerArg = null
                    pickerGroup = null
                },
            )
        } else {
            TextInputDialog(
                title = arg.name,
                help = arg.help,
                initial = readValue(values, groupKey, arg.key)?.toString().orEmpty(),
                onDismiss = { pickerArg = null; pickerGroup = null },
                onConfirm = { value ->
                    onSave(groupKey, arg, value)
                    pickerArg = null
                    pickerGroup = null
                },
            )
        }
    }
}

private fun readValue(values: JSONObject, group: String, arg: String): Any? {
    val g = values.optJSONObject(group) ?: return null
    if (!g.has(arg) || g.isNull(arg)) return null
    return g.opt(arg)
}

@Composable
private fun ConfigGroupCard(
    group: ConfigGroup,
    values: JSONObject,
    saving: String?,
    onToggleSwitch: (ConfigArg, Boolean) -> Unit,
    onOpenPicker: (ConfigArg) -> Unit,
) {
    val t = AppTheme.acrylic

    AcrylicSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleSmall,
                color = t.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            )

            group.args.forEachIndexed { index, item ->
                if (index > 0) Hairline(startInset = 16.dp)
                val raw = readValue(values, group.key, item.key)
                val busy = saving == "${group.key}.${item.key}"

                when {
                    item.isSwitch -> SwitchRow(
                        name = item.name,
                        help = item.help,
                        checked = raw?.toString()?.equals("true", ignoreCase = true) ?: false,
                        enabled = !busy,
                        onCheckedChange = { onToggleSwitch(item, it) },
                    )

                    // 任务优先级调整这类只在 PC 上改的参数：
                    // 只展示，**整行不可点**（连按压反馈都不给，免得让人以为点了没反应是卡了）
                    item.isPcOnly -> PcOnlyRow(name = item.name, help = item.help)

                    else -> ValueRow(
                        name = item.name,
                        help = item.help,
                        value = displayOf(item, raw),
                        busy = busy,
                        onClick = { onOpenPicker(item) },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun displayOf(arg: ConfigArg, raw: Any?): String {
    val v = raw ?: arg.default ?: return "—"
    val s = v.toString()
    // 同样忽略大小写：选项键是 True/False，值是 true/false
    arg.options.entries.firstOrNull { it.key.equals(s, ignoreCase = true) }?.let { return it.value }
    return when {
        s.equals("true", ignoreCase = true) -> "开"
        s.equals("false", ignoreCase = true) -> "关"
        s.isBlank() -> "—"
        else -> s
    }
}

@Composable
private fun SwitchRow(
    name: String,
    help: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val t = AppTheme.acrylic
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelledText(name = name, help = help, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ValueRow(
    name: String,
    help: String,
    value: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val t = AppTheme.acrylic
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !busy,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelledText(name = name, help = help, modifier = Modifier.weight(1f))
        Text(
            text = if (busy) "保存中…" else value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (busy) t.warning else t.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = t.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * 只能在 PC 端调整的参数行。
 *
 * 三个刻意的选择：
 *  1. **完全没有 clickable** —— 不是「点了没反应」，是压根没有点击语义，
 *     连按压高光都不给。给了反馈却没动作，用户会以为卡住了。
 *  2. **没有右箭头** —— 箭头是「点进去还有一层」的承诺，这里没有下一层。
 *  3. 右侧写「请在 PC 端调整」，而不是把那个多行值显示出来：
 *     一串 20+ 行的任务名对手机用户毫无意义，只会撑爆卡片。
 */
@Composable
private fun PcOnlyRow(name: String, help: String) {
    val t = AppTheme.acrylic
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelledText(name = name, help = help, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "请在 PC 端调整",
            style = MaterialTheme.typography.bodySmall,
            color = t.textTertiary,
            maxLines = 2,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LabelledText(name: String, help: String, modifier: Modifier = Modifier) {
    val t = AppTheme.acrylic
    Column(modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = t.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (help.isNotBlank()) {
            Text(
                text = help,
                style = MaterialTheme.typography.labelSmall,
                color = t.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: Map<String, String>,
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val t = AppTheme.acrylic
    val entries = options.entries.toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(entries.size) { i ->
                    val (key, label) = entries[i]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(key) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (key == current) t.accent else t.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (key == current) {
                            Text("✓", style = MaterialTheme.typography.bodyMedium, color = t.accent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    help: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                if (help.isNotBlank()) {
                    Text(
                        text = help,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppTheme.acrylic.textTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
