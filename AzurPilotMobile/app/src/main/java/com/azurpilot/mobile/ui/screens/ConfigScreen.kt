package com.azurpilot.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.data.MenuGroup
import com.azurpilot.mobile.data.MenuItem
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.BRIDGE_DOWN_HINT
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.components.ConfigTreeSkeleton
import com.azurpilot.mobile.ui.components.EmptyCard
import com.azurpilot.mobile.ui.components.Hairline
import com.azurpilot.mobile.ui.components.LargeTitle
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.ui.theme.AppTheme
import com.azurpilot.mobile.ui.theme.AppTypography
import com.azurpilot.mobile.ui.theme.MiuixSurface
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults

/**
 * 配置页 —— 对齐 WebUI 任务配置的左栏（search + 总览 + 可折叠分组）。
 *
 * 数据来自数据桥的 /api/task_tree（源头是 menu.json + i18n），
 * 10 个分组、93 个任务，带中文名。
 */
@Composable
fun ConfigScreen(
    state: AppUiState,
    onSelectTask: (String) -> Unit,
    onRetry: () -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.colors
    var query by remember { mutableStateOf("") }
    // 默认全部折叠 —— 和图一一致，10 行一眼看完
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    val tree = state.taskTree
    val hits: List<Pair<MenuGroup, MenuItem>> = remember(tree, query) {
        tree?.search(query) ?: emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 键盘弹出时把整列顶起来。搜索框在 LazyColumn **之外**的固定列里，
            // 不加这个的话键盘会直接盖住它 —— 用户正在打字的框被自己挡住。
            .imePadding()
            .padding(start = insets.horizontal, end = insets.horizontal, top = insets.top),
    ) {
        LargeTitle("配置")

        SearchField(query = query, onQueryChange = { query = it })

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = insets.bottom, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            when {
                state.treeLoading && tree == null -> {
                    item { ConfigTreeSkeleton(groups = 6) }
                }

                tree == null -> {
                    item {
                        EmptyCard(
                            title = "取不到任务菜单树",
                            detail = state.treeError ?: BRIDGE_DOWN_HINT,
                        )
                    }
                    item {
                        ChipButton(text = "重试", filled = true) { onRetry() }
                    }
                }

                query.isNotBlank() -> {
                    if (hits.isEmpty()) {
                        item {
                            EmptyCard(
                                title = "没有匹配的配置",
                                detail = "换个关键词试试，也可以搜英文键名（如 OpsiHazard1Leveling）。",
                            )
                        }
                    } else {
                        item { SubHeader("搜索结果 · ${hits.size}") }
                        item {
                            MiuixSurface(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    hits.forEachIndexed { index, (group, item) ->
                                        TaskRow(
                                            title = item.name,
                                            subtitle = "${item.key} · ${group.name}",
                                            onClick = { onSelectTask(item.key) },
                                        )
                                        if (index != hits.lastIndex) Hairline(startInset = 16.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipButton(text = "总览", filled = true) { }
                            ChipButton(text = "共 ${tree.totalTasks} 项", filled = false) { }
                        }
                    }

                    items(tree.groups.size) { index ->
                        val group = tree.groups[index]
                        val isOpen = expanded[group.key] == true

                        MiuixSurface(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                GroupHeader(
                                    name = group.name,
                                    count = group.tasks.size,
                                    expanded = isOpen,
                                    onToggle = { expanded[group.key] = !isOpen },
                                )
                                if (isOpen) {
                                    Hairline(startInset = 16.dp)
                                    group.tasks.forEachIndexed { i, task ->
                                        TaskRow(
                                            title = task.name,
                                            subtitle = null,
                                            indented = true,
                                            onClick = { onSelectTask(task.key) },
                                        )
                                        if (i != group.tasks.lastIndex) Hairline(startInset = 34.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val t = AppTheme.colors
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = "搜索配置",
        useLabelAsPlaceholder = true,
        leadingIcon = {
            Icon(
                imageVector = AppIcons.Magnifier,
                contentDescription = null,
                tint = t.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        },
        textStyle = AppTypography.bodyMedium.copy(color = t.textPrimary),
        cornerRadius = 12.dp,
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = t.panelStrong,
            labelColor = t.textTertiary,
            borderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun GroupHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val t = AppTheme.colors
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = t.textTertiary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = AppTypography.bodyLarge,
            color = t.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = AppTypography.labelSmall,
            color = t.textTertiary,
        )
    }
}

@Composable
private fun TaskRow(
    title: String,
    subtitle: String?,
    indented: Boolean = false,
    onClick: () -> Unit,
) {
    val t = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(
                start = if (indented) 42.dp else 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.bodyLarge,
                color = t.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppTypography.labelSmall,
                    color = t.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = t.textTertiary,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SubHeader(text: String) {
    val t = AppTheme.colors
    Text(
        text = text,
        style = AppTypography.titleSmall,
        color = t.textSecondary,
        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
    )
}

/** 小胶囊按钮（药丸视觉 ~31dp，触控目标 48dp） */
@Composable
fun ChipButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = AppTheme.colors
    val bg by animateColorAsState(if (filled) t.accent else t.track, label = "chipBg")
    val fg by animateColorAsState(if (filled) Color.White else t.textSecondary, label = "chipFg")

    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        cornerRadius = 10.dp,
        minWidth = 0.dp,
        minHeight = 31.dp,
        colors = ButtonDefaults.textButtonColors(
            color = bg,
            disabledColor = bg,
            textColor = fg,
            disabledTextColor = fg,
        ),
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        textStyle = AppTypography.labelMedium,
    )
}

/** 分组内的分隔线（与资源列表保持同一套视觉） */
@Composable
private fun RowDivider() = HorizontalDivider(
    thickness = 0.5.dp,
    color = AppTheme.colors.divider,
    modifier = Modifier.padding(start = 16.dp),
)
