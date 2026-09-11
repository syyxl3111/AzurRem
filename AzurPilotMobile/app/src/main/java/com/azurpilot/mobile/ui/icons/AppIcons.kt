package com.azurpilot.mobile.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 全部图标手绘，不引第三方图标库。
 *
 * 笔触对齐 SF Symbols 的观感：24×24 视口、1.8 描边、圆头圆角。
 * 颜色一律用不透明黑占位 —— Icon() 会用 ColorFilter.tint 覆盖 RGB、保留 alpha，
 * 所以这里填什么颜色都无所谓，只需要 alpha 正确。
 *
 * 两个 Compose 1.12 的坑：
 *  1. `path` / `group` 是 `androidx.compose.ui.graphics.vector` 包下的**顶层函数**，
 *     不是 ImageVector.Builder 的成员 —— import 了 ImageVector 也得单独 import 它们。
 *  2. 路径不再接受 SVG 字符串，改走 `PathBuilder` DSL（moveTo / lineTo / arcTo …）。
 */
private const val SW = 1.8f

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

/** 描边路径 */
private fun ImageVector.Builder.line(
    width: Float = SW,
    cap: StrokeCap = StrokeCap.Round,
    join: StrokeJoin = StrokeJoin.Round,
    pathBuilder: PathBuilder.() -> Unit,
) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = cap,
    strokeLineJoin = join,
    pathBuilder = pathBuilder,
)

/** 填充路径 */
private fun ImageVector.Builder.solid(pathBuilder: PathBuilder.() -> Unit) = path(
    fill = SolidColor(Color.Black),
    pathBuilder = pathBuilder,
)

object AppIcons {

    // ── Tab 1：主页（房子） ────────────────────────────────
    val House: ImageVector by lazy {
        icon("House") {
            line {
                moveTo(3.6f, 11.1f)
                lineTo(12f, 4.2f)
                lineTo(20.4f, 11.1f)
            }
            line {
                moveTo(5.6f, 9.6f)
                verticalLineTo(18.4f)
                arcTo(1.7f, 1.7f, 0f, false, false, 7.3f, 20.1f)
                horizontalLineTo(16.7f)
                arcTo(1.7f, 1.7f, 0f, false, false, 18.4f, 18.4f)
                verticalLineTo(9.6f)
            }
        }
    }

    // ── Tab 2：任务（三个勾 + 三行） ──────────────────────
    val Checklist: ImageVector by lazy {
        icon("Checklist") {
            line {
                moveTo(3.6f, 7.0f); lineTo(5.6f, 9.0f); lineTo(8.8f, 5.2f)
            }
            line {
                moveTo(3.6f, 12.0f); lineTo(5.6f, 14.0f); lineTo(8.8f, 10.2f)
            }
            line {
                moveTo(3.6f, 17.0f); lineTo(5.6f, 19.0f); lineTo(8.8f, 15.2f)
            }
            line { moveTo(11.6f, 7.0f); lineTo(20.4f, 7.0f) }
            line { moveTo(11.6f, 12.0f); lineTo(20.4f, 12.0f) }
            line { moveTo(11.6f, 17.0f); lineTo(20.4f, 17.0f) }
        }
    }

    // ── Tab 3：日志（折角文档 + 两行文本） ────────────────
    val LogDoc: ImageVector by lazy {
        icon("LogDoc") {
            line {
                moveTo(6.3f, 3.4f)
                lineTo(13.4f, 3.4f)
                lineTo(18.2f, 8.2f)
                lineTo(18.2f, 20.6f)
                lineTo(6.3f, 20.6f)
                close()
            }
            line {
                moveTo(13.4f, 3.4f)
                lineTo(13.4f, 8.2f)
                lineTo(18.2f, 8.2f)
            }
            line { moveTo(9.3f, 12.2f); lineTo(15.2f, 12.2f) }
            line { moveTo(9.3f, 15.9f); lineTo(15.2f, 15.9f) }
        }
    }

    // ── Tab 4：统计（柱状排行） ───────────────────────────
    val Chart: ImageVector by lazy {
        icon("Chart") {
            line(width = 2.6f) { moveTo(5.6f, 19.6f); lineTo(5.6f, 13.4f) }
            line(width = 2.6f) { moveTo(12f, 19.6f); lineTo(12f, 8.2f) }
            line(width = 2.6f) { moveTo(18.4f, 19.6f); lineTo(18.4f, 4.4f) }
        }
    }

    // ── Tab 5：设置（齿轮：6 个粗齿 + 中心环） ────────────
    // 之前用 8 个细齿，实测在 24dp 下看起来像太阳/星芒，所以改成 6 个更宽的齿。
    val Gear: ImageVector by lazy {
        icon("Gear") {
            for (i in 0 until 6) {
                group(rotate = i * 60f, pivotX = 12f, pivotY = 12f) {
                    solid {
                        moveTo(10.6f, 3.0f)
                        horizontalLineToRelative(2.8f)
                        arcToRelative(0.95f, 0.95f, 0f, false, true, 0.95f, 0.95f)
                        verticalLineToRelative(2.4f)
                        arcToRelative(0.95f, 0.95f, 0f, false, true, -0.95f, 0.95f)
                        horizontalLineToRelative(-2.8f)
                        arcToRelative(0.95f, 0.95f, 0f, false, true, -0.95f, -0.95f)
                        verticalLineToRelative(-2.4f)
                        arcToRelative(0.95f, 0.95f, 0f, false, true, 0.95f, -0.95f)
                        close()
                    }
                }
            }
            line(width = 2.6f) {
                moveTo(12f, 7.7f)
                arcToRelative(4.3f, 4.3f, 0f, true, true, -0.01f, 0f)
                close()
            }
        }
    }

    // ── 启动：圆角三角 ────────────────────────────────────
    val Play: ImageVector by lazy {
        icon("Play") {
            path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 3.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = {
                    moveTo(8.2f, 5.6f)
                    lineTo(18.4f, 12f)
                    lineTo(8.2f, 18.4f)
                    close()
                },
            )
        }
    }

    // ── 停止：圆角方块（对齐你给的截图，方块占外框约 57%） ──
    val Stop: ImageVector by lazy {
        icon("Stop") {
            solid {
                moveTo(8.6f, 5.2f)
                horizontalLineToRelative(6.8f)
                arcToRelative(3.4f, 3.4f, 0f, false, true, 3.4f, 3.4f)
                verticalLineToRelative(6.8f)
                arcToRelative(3.4f, 3.4f, 0f, false, true, -3.4f, 3.4f)
                horizontalLineToRelative(-6.8f)
                arcToRelative(3.4f, 3.4f, 0f, false, true, -3.4f, -3.4f)
                verticalLineToRelative(-6.8f)
                arcToRelative(3.4f, 3.4f, 0f, false, true, 3.4f, -3.4f)
                close()
            }
        }
    }

    // ── 刷新 ──────────────────────────────────────────────
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            line(width = 1.9f) {
                moveTo(19.2f, 12f)
                arcTo(7.2f, 7.2f, 0f, true, true, 16.9f, 6.9f)
            }
            line(width = 1.9f) {
                moveTo(17.0f, 3.4f)
                lineTo(17.0f, 7.4f)
                lineTo(13.0f, 7.4f)
            }
        }
    }

    // ── Tab 3：配置（三条滑杆，Apple 的「设置项」隐喻） ────
    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            // 三条轨道
            line(width = 1.9f) { moveTo(3.4f, 7.2f); lineTo(20.6f, 7.2f) }
            line(width = 1.9f) { moveTo(3.4f, 12f); lineTo(20.6f, 12f) }
            line(width = 1.9f) { moveTo(3.4f, 16.8f); lineTo(20.6f, 16.8f) }
            // 三个滑块
            solid {
                moveTo(8.4f, 5.6f)
                arcToRelative(1.6f, 1.6f, 0f, true, true, -0.01f, 0f)
                close()
            }
            solid {
                moveTo(15.6f, 10.4f)
                arcToRelative(1.6f, 1.6f, 0f, true, true, -0.01f, 0f)
                close()
            }
            solid {
                moveTo(7.2f, 15.2f)
                arcToRelative(1.6f, 1.6f, 0f, true, true, -0.01f, 0f)
                close()
            }
        }
    }

    // ── 右箭头（列表项） ──────────────────────────────────
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            line(width = 2.0f) {
                moveTo(9.6f, 5.4f)
                lineTo(16.2f, 12f)
                lineTo(9.6f, 18.6f)
            }
        }
    }

    // ── 左箭头（返回）——iOS 的返回键就是一根加粗的 chevron ──
    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            line(width = 2.4f) {
                moveTo(14.4f, 5.4f)
                lineTo(7.8f, 12f)
                lineTo(14.4f, 18.6f)
            }
        }
    }

    // ── 下箭头（折叠分组） ────────────────────────────────
    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            line(width = 2.2f) {
                moveTo(5.4f, 9.6f)
                lineTo(12f, 16.2f)
                lineTo(18.6f, 9.6f)
            }
        }
    }

    // ── 搜索 ──────────────────────────────────────────────
    val Magnifier: ImageVector by lazy {
        icon("Magnifier") {
            line(width = 2.0f) {
                moveTo(10.8f, 4.2f)
                arcToRelative(6.6f, 6.6f, 0f, true, true, -0.01f, 0f)
                close()
            }
            line(width = 2.0f) { moveTo(15.6f, 15.6f); lineTo(20.4f, 20.4f) }
        }
    }

    // ── 展开全屏 ──────────────────────────────────────────
    val Expand: ImageVector by lazy {
        icon("Expand") {
            line(width = 1.9f) {
                moveTo(14.2f, 4.6f); lineTo(19.4f, 4.6f); lineTo(19.4f, 9.8f)
            }
            line(width = 1.9f) {
                moveTo(9.8f, 19.4f); lineTo(4.6f, 19.4f); lineTo(4.6f, 14.2f)
            }
            line(width = 1.9f) { moveTo(19.4f, 4.6f); lineTo(13.4f, 10.6f) }
            line(width = 1.9f) { moveTo(4.6f, 19.4f); lineTo(10.6f, 13.4f) }
        }
    }

    // ── 立即执行（闪电） ──────────────────────────────────
    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            solid {
                moveTo(13.4f, 2.6f)
                lineTo(5.6f, 13.4f)
                lineTo(11.0f, 13.4f)
                lineTo(10.6f, 21.4f)
                lineTo(18.4f, 10.6f)
                lineTo(13.0f, 10.6f)
                close()
            }
        }
    }

    // ── 警告 ──────────────────────────────────────────────
    val Warning: ImageVector by lazy {
        icon("Warning") {
            line {
                moveTo(12f, 4.2f)
                lineTo(21.0f, 19.8f)
                lineTo(3.0f, 19.8f)
                close()
            }
            line(width = 1.9f) { moveTo(12f, 9.6f); lineTo(12f, 14.2f) }
            solid {
                moveTo(12f, 16.2f)
                arcToRelative(1.05f, 1.05f, 0f, true, true, -0.01f, 0f)
                close()
            }
        }
    }

    // ── 关闭 ──────────────────────────────────────────────
    val Close: ImageVector by lazy {
        icon("Close") {
            line(width = 2.0f) { moveTo(6.4f, 6.4f); lineTo(17.6f, 17.6f) }
            line(width = 2.0f) { moveTo(17.6f, 6.4f); lineTo(6.4f, 17.6f) }
        }
    }
}
