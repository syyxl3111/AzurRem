package com.azurpilot.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.azurpilot.mobile.ui.icons.AppIcons
import com.azurpilot.mobile.data.Settings
import com.azurpilot.mobile.ui.AppUiState
import com.azurpilot.mobile.ui.ScreenInsets
import com.azurpilot.mobile.ui.components.LargeTitle
import com.azurpilot.mobile.ui.components.SectionTitle
import com.azurpilot.mobile.ui.syncAgeText
import com.azurpilot.mobile.ui.theme.AcrylicSurface
import com.azurpilot.mobile.ui.theme.AppTheme

@Composable
fun SettingsScreen(
    state: AppUiState,
    onServerUrl: (String) -> Unit,
    onInstance: (String) -> Unit,
    onWebuiPassword: (String) -> Unit,
    onPollSeconds: (Int) -> Unit,
    onLogLines: (Int) -> Unit,
    onThemeMode: (Int) -> Unit,
    onOpenLogs: () -> Unit,
    onNotify: (String) -> Unit,
    onReprefetch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    insets: ScreenInsets,
    modifier: Modifier = Modifier,
) {
    val t = AppTheme.acrylic
    val SAMPLE_URL = Settings.SAMPLE_URL

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = insets.horizontal,
            end = insets.horizontal,
            top = insets.top,
            bottom = insets.bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "pageTitle") {
            // iOS 的「大标题」：分组列表页面顶部就是这个（统一走 LargeTitle，带 heading 语义）
            LargeTitle("设置")
        }

        item(key = "connTitle") {
            SectionTitle("连接")
        }
        item(key = "connCard") {
            AcrylicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    CommitTextField(
                        label = "服务器地址",
                        value = state.serverUrl,
                        keyboardType = KeyboardType.Uri,
                        onCommit = onServerUrl,
                    )
                    // 地址是用户自己填的（代码里没有默认值，见 README 的「关于安全」），
                    // 所以没填的时候要在这里明确说一句，别让人对着空框发呆
                    if (state.serverUrl.isBlank()) {
                        Text(
                            text = "必填。填电脑上那个网关窗口第一行显示的地址，" +
                                "例如 $SAMPLE_URL",
                            style = MaterialTheme.typography.labelSmall,
                            color = t.warning,
                            modifier = Modifier.padding(top = 5.dp, start = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // 服务端密码 —— 就是网关窗口**第二行**显示的那串（默认 32 位随机）。
                    //
                    // 老名字叫「WebUI 密码」，因为那时它填的确实是 AzurPilot WebUI 的密码。
                    // 走网关之后，App 认的是**网关自己的**密码，AzurPilot 那把留在电脑上、
                    // 由网关注入 —— 所以名字跟着改，免得用户去翻 deploy.yaml 找密码。
                    //
                    // 留空 = 不带凭据。只有在服务端**确实没设密码**时才对（旧行为兼容）；
                    // 网关一律要密码，留空会看到全线 401。
                    CommitTextField(
                        label = "服务端密码",
                        value = state.webuiPassword,
                        keyboardType = KeyboardType.Password,
                        masked = true,
                        onCommit = onWebuiPassword,
                    )
                    Spacer(Modifier.height(12.dp))
                    CommitTextField(
                        label = "实例名",
                        value = state.instance,
                        keyboardType = KeyboardType.Text,
                        onCommit = onInstance,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        // 之前这里直接拼了 lastSyncAt，把裸的毫秒时间戳露出来了
                        text = if (state.connected) "已连接 · ${syncAgeText(state.lastSyncAt)}" else "未连接",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.connected) t.success else t.danger,
                    )
                }
            }
        }

        item(key = "pollTitle") {
            Spacer(Modifier.height(4.dp))
            SectionTitle("刷新")
        }
        item(key = "pollCard") {
            AcrylicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    ChoiceRow(
                        label = "轮询间隔",
                        options = listOf(10 to "10 秒", 30 to "30 秒", 60 to "1 分钟", 120 to "2 分钟"),
                        selected = state.pollSeconds,
                        onSelect = onPollSeconds,
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = t.divider,
                        modifier = Modifier.padding(horizontal = 15.dp),
                    )
                    ChoiceRow(
                        label = "日志行数",
                        options = listOf(200 to "200", 400 to "400", 800 to "800"),
                        selected = state.logLines,
                        onSelect = onLogLines,
                    )
                }
            }
        }

        item(key = "themeTitle") {
            Spacer(Modifier.height(4.dp))
            SectionTitle("外观")
        }
        item(key = "themeCard") {
            AcrylicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    ChoiceRow(
                        label = "主题",
                        options = listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"),
                        selected = state.themeMode,
                        onSelect = onThemeMode,
                    )
                }
            }
        }

        item(key = "toolTitle") {
            Spacer(Modifier.height(4.dp))
            SectionTitle("工具")
        }
        item(key = "toolCard") {
            AcrylicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 日志从原来的 Tab 挪到这里：点击进全屏子页面
                    NavRow(
                        title = "查阅日志",
                        value = "${state.logs.size} 行",
                        onClick = onOpenLogs,
                    )
                }
            }
        }

        item(key = "cacheTitle") {
            Spacer(Modifier.height(4.dp))
            SectionTitle("任务配置缓存")
        }
        item(key = "cacheCard") {
            AcrylicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(
                        text = if (state.prefetching) {
                            "正在预缓存 ${state.prefetchDone} / ${state.prefetchTotal}"
                        } else {
                            "已缓存 ${state.cacheEntries} 个任务的配置"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.textPrimary,
                    )
                    Spacer(Modifier.height(5.dp))
                    // 说清楚它解决的是什么问题 —— 否则「缓存」两个字容易被理解成
                    // 「看到的是旧数据」。实际是先显示缓存、后台同时核对最新值。
                    Text(
                        text = "打开任务配置页时先显示缓存内容，再在后台核对最新值。" +
                            "所以点进去立刻就出来，不用等网络。",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary,
                    )
                    if (state.prefetching && state.prefetchTotal > 0) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = {
                                state.prefetchDone.toFloat() / state.prefetchTotal.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = t.accent,
                            trackColor = t.track,
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                    ChipButton(text = "重新预缓存", filled = false) { onReprefetch() }
                }
            }
        }

        item(key = "aboutTitle") {
            Spacer(Modifier.height(4.dp))
            SectionTitle("关于")
        }
        item(key = "aboutCard") {
            // 版本号从 PackageManager 读**实际安装的**版本，而不是写死一个字符串 ——
            // 写死的话改了 build.gradle 忘了改这里，界面上就会一直显示旧版本号。
            val context = LocalContext.current
            val versionName = remember(context) {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty().ifBlank { "—" }
            }

            AcrylicSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 检查更新：查本项目的 GitHub Releases，有新版就下 APK 交给系统安装器
                    NavRow(
                        title = "版本",
                        value = if (state.updateChecking) "检查中…" else versionName,
                        icon = null,
                        onClick = onCheckUpdate,
                    )
                    UpdateStatus(state, onDownload = onDownloadUpdate)
                }
            }
        }
        item(key = "aboutFooter") {
            // iOS 的「分组说明文字」：放在组**外**、小字、次要色
            Text(
                text = "AzurRem · AzurPilot 的原生安卓客户端。\n" +
                    "电脑上跑 AzurRemBridge.exe 网关挂件，它统一对外：读本地统计数据，" +
                    "并把操控反代给 AzurPilot。\n" +
                    "App 只需要一个地址 + 一个密码。" +
                    "不修改 AzurPilot 任何被跟踪的文件。",
                style = MaterialTheme.typography.bodySmall,
                color = t.textSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }

        item(key = "tail") { Spacer(Modifier.height(4.dp)) }
    }
}

/**
 * 文本输入：按「完成」或失焦时才提交。
 * 不能每敲一个字符就提交 —— 那会让轮询反复重启。
 */
@Composable
private fun CommitTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    /** true = 掩码显示（密码类字段）。只影响显示，存的是原文。 */
    masked: Boolean = false,
    onCommit: (String) -> Unit,
) {
    val t = AppTheme.acrylic
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { text = value }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        visualTransformation = if (masked) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                if (text != value) onCommit(text)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (!focus.isFocused && text != value) onCommit(text)
            },
    )
}

/**
 * 可点击跳转的设置行（iOS 分组列表里带 > 的那种）。
 */
@Composable
private fun NavRow(
    title: String,
    value: String?,
    icon: ImageVector? = AppIcons.LogDoc,
    onClick: () -> Unit,
) {
    val t = AppTheme.acrylic
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = t.accent,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(11.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = t.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = t.textTertiary,
            )
            Spacer(Modifier.width(6.dp))
        }
        androidx.compose.material3.Icon(
            imageVector = com.azurpilot.mobile.ui.icons.AppIcons.ChevronRight,
            contentDescription = null,
            tint = t.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * 版本行下面那块状态区。
 *
 * 四种状态，**互斥**，所以用 when 而不是叠一堆 if：
 *   下载中（进度条）→ 有新版本（说明 + 下载按钮）→ 一句话结果 → 不显示
 *
 * 「不显示」是缺省态：没点过检查更新之前，这里一个字都不该占地方。
 */
@Composable
private fun UpdateStatus(
    state: AppUiState,
    onDownload: () -> Unit,
) {
    val t = AppTheme.acrylic
    val progress = state.updateProgress

    when {
        progress != null -> {
            val (got, total) = progress
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = if (total > 0) {
                        "正在下载 ${megabytes(got)} / ${megabytes(total)} MB"
                    } else {
                        "正在下载 ${megabytes(got)} MB"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                // total 未知时用**不确定态**（来回滚动那条），不要假装是 0%。
                // Material3 里这两种是两个不同的重载，只能分支写。
                val barModifier = Modifier.fillMaxWidth().height(3.dp)
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (got.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = barModifier,
                        color = t.accent,
                        trackColor = t.track,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = barModifier,
                        color = t.accent,
                        trackColor = t.track,
                    )
                }
            }
        }

        state.updateAvailable != null -> {
            val info = state.updateAvailable
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "发现新版本 ${info.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.textPrimary,
                )
                if (info.apkSize > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "APK 体积 ${megabytes(info.apkSize)} MB · 下载后交给系统安装程序",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.textTertiary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ChipButton(text = "下载并安装", filled = true) { onDownload() }
            }
        }

        state.updateMessage != null -> Text(
            text = state.updateMessage,
            style = MaterialTheme.typography.labelSmall,
            color = t.textTertiary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
    }
}

/** 字节 → MB，保留一位小数 */
private fun megabytes(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f", bytes / 1024.0 / 1024.0)

/** 一行里的分段选择 */
@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val t = AppTheme.acrylic
    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 9.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = t.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                val bg by animateColorAsState(
                    targetValue = if (isSelected) t.accent else t.track,
                    label = "choiceBg",
                )
                val fg by animateColorAsState(
                    targetValue = if (isSelected) androidx.compose.ui.graphics.Color.White else t.textSecondary,
                    label = "choiceFg",
                )
                // 药丸视觉 ~31dp，但触控目标撑到 48dp（外面套一层可点 Box）
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}
