# Apple HIG 可执行规则提取（Typography / Color / Icons / SF Symbols）

> 来源目录：`D:\Temp\apple-design-skill\references\hig\`
> 只提取可落地的数值与做法，剔除营销与散文。原文平台范围：iOS / iPadOS / macOS（tvOS、visionOS、watchOS 专属段落已在原文中剔除）。
> 标注「**原文未给出**」的行 = 该文件确实没有对应数值，不是提取遗漏。
> 格式：`规则 — 数值/做法 (来源: 文件名.md › 章节名)`

---

## 一、Typography（排版）

### 1.1 平台级字号底线与字重

- 默认 / 最小字号 — iOS、iPadOS 默认 `17 pt`、最小 `11 pt`；macOS 默认 `13 pt`、最小 `10 pt`；自定义字体同样按此表 (来源: typography.md › Ensuring legibility)
- 细字重补偿 — 自定义字体若用 thin 字重，取 **大于** 上表推荐值的字号 (来源: typography.md › Ensuring legibility)
- 禁用字重 — 避免 `Ultralight`、`Thin`、`Light`；系统字体优先 `Regular` / `Medium` / `Semibold` / `Bold` (来源: typography.md › Ensuring legibility)
- 强调字重仅 4 档 — `medium`、`semibold`、`bold`、`heavy`（SwiftUI `bold()`；UIKit `UIFontDescriptor` 的 `traitBold`） (来源: typography.md › Specifications)
- 字体家族 — SF = SF Pro / SF Compact / SF Arabic / SF Armenian / SF Georgian / SF Hebrew / SF Mono，以上每个都有 rounded 变体；NY = serif 家族 (来源: typography.md › Using system fonts)
- 字重区间与宽度 — Ultralight → Black 全区间；SF 另有 `Condensed` / `Expanded` 宽度；均为 variable font，支持 dynamic optical sizing，**无需**选离散 Text/Display 光学尺寸（除非设计工具不支持 variable font） (来源: typography.md › Using system fonts)
- 不内嵌系统字体 — 用 `Font.Design.default`（全平台系统字体）、`Font.Design.serif`（New York） (来源: typography.md › Using system fonts)
- 字体数量 — 尽量减少字体家族数量，高度定制界面也一样 (来源: typography.md › Conveying hierarchy)

### 1.2 行距 / leading 规则

- loose leading — 宽栏或长段落时加大行距，帮助换行时保持阅读位置 (来源: typography.md › Using system fonts)
- tight leading — 仅在高度受限处（如列表行）收紧行距 (来源: typography.md › Using system fonts)
- **≥3 行硬规则** — 显示 3 行及以上文本时，即使在高度受限区域也 **不要** 用 tight leading (来源: typography.md › Using system fonts)
- leading 调整方式 — 通过 symbolic traits 修改内置文本样式的 leading (来源: typography.md › Using system fonts)
- 文本样式定义 — 一个 text style = 字重 + 字号 + leading 的组合；body 面向多行舒适阅读，headline 用于与周围内容区分 (来源: typography.md › Using system fonts)

### 1.3 iOS / iPadOS Dynamic Type 标准字号阶梯（字号 pt / 行高 pt）

单元格格式 `字号/行高`。iOS 表中样式名为 `Subhead`，macOS 表中为 `Subheadline`。

| 样式 | xSmall | Small | Medium | **Large（默认）** | xLarge | xxLarge | xxxLarge |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Large Title | 31/38 | 32/39 | 33/40 | **34/41** | 36/43 | 38/46 | 40/48 |
| Title 1 | 25/31 | 26/32 | 27/33 | **28/34** | 30/37 | 32/39 | 34/41 |
| Title 2 | 19/24 | 20/25 | 21/26 | **22/28** | 24/30 | 26/32 | 28/34 |
| Title 3 | 17/22 | 18/23 | 19/24 | **20/25** | 22/28 | 24/30 | 26/32 |
| Headline | 14/19 | 15/20 | 16/21 | **17/22** | 19/24 | 21/26 | 23/29 |
| Body | 14/19 | 15/20 | 16/21 | **17/22** | 19/24 | 21/26 | 23/29 |
| Callout | 13/18 | 14/19 | 15/20 | **16/21** | 18/23 | 20/25 | 22/28 |
| Subhead | 12/16 | 13/18 | 14/19 | **15/20** | 17/22 | 19/24 | 21/28 |
| Footnote | 12/16 | 12/16 | 12/16 | **13/18** | 15/20 | 17/22 | 19/24 |
| Caption 1 | 11/13 | 11/13 | 11/13 | **12/16** | 14/19 | 16/21 | 18/23 |
| Caption 2 | 11/13 | 11/13 | 11/13 | **11/13** | 13/18 | 15/20 | 17/22 |

(来源: typography.md › iOS, iPadOS Dynamic Type sizes)
- 字号基准 — 按 @2x 144 ppi、@3x 216 ppi 的图像分辨率换算 (来源: typography.md › iOS, iPadOS Dynamic Type sizes)
- 关键观察 — `Caption 1/2` 在 xSmall → Medium 恒为 `11/13`；`Footnote` 在 xSmall → Medium 恒为 `12/16`；`Body` 与 `Headline` 全阶梯 **字号行高完全相同**，仅字重不同（Regular vs Semibold） (来源: typography.md › iOS, iPadOS Dynamic Type sizes)

### 1.4 iOS / iPadOS 无障碍大字号 AX1–AX5（字号 pt / 行高 pt）

| 样式 | AX1 | AX2 | AX3 | AX4 | AX5 |
| --- | --- | --- | --- | --- | --- |
| Large Title | 44/52 | 48/57 | 52/61 | 56/66 | 60/70 |
| Title 1 | 38/46 | 43/51 | 48/57 | 53/62 | 58/68 |
| Title 2 | 34/41 | 39/47 | 44/52 | 50/59 | 56/66 |
| Title 3 | 31/38 | 37/44 | 43/51 | 49/58 | 55/65 |
| Headline | 28/34 | 33/40 | 40/48 | 47/56 | 53/62 |
| Body | 28/34 | 33/40 | 40/48 | 47/56 | 53/62 |
| Callout | 26/32 | 32/39 | 38/46 | 44/52 | 51/60 |
| Subhead | 25/31 | 30/37 | 36/43 | 42/50 | 49/58 |
| Footnote | 23/29 | 27/33 | 33/40 | 38/46 | 44/52 |
| Caption 1 | 22/28 | 26/32 | 32/39 | 37/44 | 43/51 |
| Caption 2 | 20/25 | 24/30 | 29/35 | 34/41 | 40/48 |

(来源: typography.md › iOS, iPadOS larger accessibility type sizes)
- 打开路径 — Settings > Accessibility > Display & Text Size > Larger Text 开启 Larger Accessibility Text Sizes 后逐项验证 (来源: typography.md › Supporting Dynamic Type)
- 阶梯跨度 — Body 在 Large 为 `17 pt`，到 AX5 为 `53 pt`（≈3.1×） (来源: typography.md › iOS, iPadOS larger accessibility type sizes)

### 1.5 macOS 内置文本样式（字号 pt / 行高 pt / 字重）

| 文本样式 | 字重 | 字号 | 行高 | 强调字重 |
| --- | --- | --- | --- | --- |
| Large Title | Regular | 26 | 32 | Bold |
| Title 1 | Regular | 22 | 26 | Bold |
| Title 2 | Regular | 17 | 22 | Bold |
| Title 3 | Regular | 15 | 20 | Semibold |
| Headline | **Bold** | 13 | 16 | **Heavy** |
| Body | Regular | 13 | 16 | Semibold |
| Callout | Regular | 12 | 15 | Semibold |
| Subheadline | Regular | 11 | 14 | Semibold |
| Footnote | Regular | 10 | 13 | Semibold |
| Caption 1 | Regular | 10 | 13 | **Medium** |
| Caption 2 | **Medium** | 10 | 13 | Semibold |

(来源: typography.md › macOS built-in text styles)
- 基准分辨率 — macOS 表基于 @2x 144 ppi (来源: typography.md › macOS built-in text styles)
- **macOS 不支持 Dynamic Type** — 没有字号缩放阶梯 (来源: typography.md › Desktop (macOS))
- macOS 无 AX 阶梯 — 上表只有一档，无 xSmall…AX5 (来源: typography.md › macOS built-in text styles)

### 1.6 字重：基准字重 + 强调字重（iOS 与 macOS 差异）

- iOS/iPadOS 强调字重映射 — Large Title、Title 1、Title 2 → **Bold**；Title 3、Headline、Body、Callout、Subhead、Footnote、Caption 1、Caption 2 → **Semibold** (来源: typography.md › iOS, iPadOS Dynamic Type sizes)
- iOS 基准字重 — Headline = `Semibold`；其余全部 = `Regular`（Caption 1/2 也是 Regular） (来源: typography.md › iOS, iPadOS Dynamic Type sizes)
- macOS 三处例外 — Headline 基准是 `Bold`（强调 `Heavy`）；Caption 1 强调是 `Medium`；Caption 2 基准是 `Medium`、强调 `Semibold` (来源: typography.md › macOS built-in text styles)

### 1.7 Tracking（字距）值

- 单位 — 表中同时给 1/1000 em 与 points 两种；**并非所有 app 都按 1/1000 em 表达** (来源: typography.md › Tracking values)
- 运行时行为 — 系统字体在**每个字号上动态调整 tracking**；只有在做静态界面 mockup 时才需要手动设 (来源: typography.md › Using system fonts)
- 是否三套 — iOS/iPadOS/visionOS 与 macOS 的 SF Pro 表 **完全相同**；SF Pro Rounded 与 New York 各自独立 (来源: typography.md › iOS, iPadOS, visionOS tracking values / macOS tracking values)

**SF Pro（iOS/iPadOS/visionOS 与 macOS 通用）** `字号: 1/1000em (pt)`

```
6:+41(+0.24)  7:+34(+0.23)  8:+26(+0.21)  9:+19(+0.17)  10:+12(+0.12) 11:+6(+0.06)  12:0(0.0)
13:-6(-0.08)  14:-11(-0.15) 15:-16(-0.23) 16:-20(-0.31) 17:-26(-0.43) 18:-25(-0.44) 19:-24(-0.45)
20:-23(-0.45) 21:-18(-0.36) 22:-12(-0.26) 23:-4(-0.10)  24:+3(+0.07)  25:+6(+0.15)  26:+8(+0.22)
27:+11(+0.29) 28:+14(+0.38) 29:+14(+0.40) 30:+14(+0.40) 31:+13(+0.39) 32:+13(+0.41) 33:+12(+0.40)
34:+12(+0.40) 35:+11(+0.38) 36:+10(+0.37) 37:+10(+0.36) 38:+10(+0.37) 39:+10(+0.38) 40:+10(+0.37)
41:+9(+0.36)  42:+9(+0.37)  43:+9(+0.38)  44:+8(+0.37)  45:+8(+0.35)  46:+8(+0.36)  47:+8(+0.37)
48:+8(+0.35)  49:+7(+0.33)  50:+7(+0.34)  51:+7(+0.35)  52:+6(+0.33)  53:+6(+0.31)  54:+6(+0.32)
56:+6(+0.30)  58:+5(+0.28)  60:+4(+0.26)  62:+4(+0.24)  64:+4(+0.22)  66:+3(+0.19)  68:+2(+0.17)
70:+2(+0.14)  72:+2(+0.14)  76:+1(+0.07)  80:0  84:0  88:0  92:0  96:0
```

(来源: typography.md › iOS, iPadOS, visionOS tracking values › SF Pro；数值与 macOS tracking values 表逐行相同)
- 锚点值 — `12 pt` = 0（过零点）；`17 pt`（Body/Headline 默认）= `-26 (1/1000em)` / `-0.43 pt`，是全表最大负值；`28–30 pt` = `+14 (1/1000em)` / `+0.40 pt`，是最大正值；`80 pt` 及以上 = `0` (来源: typography.md › SF Pro（同上表）)
- 表有空档 — 55、57、59、61、63、65、67、69、71、73–75、77–79 pt 无官方值 (来源: typography.md › SF Pro)

**SF Pro Rounded（差异要点）** — `6 pt = +87 (+0.51)`（全表最大正值）；`12 pt = +46 (+0.54)`；`17 pt = +22 (+0.37)`；`20 pt = +18 (+0.36)`；`44–48 pt ≈ +8 (+0.35~0.37)`；`80 pt` 及以上 = `0`；**全程非负，没有负 tracking** (来源: typography.md › SF Pro Rounded)

**New York（差异要点）** — 过零点在 `15 pt = 0`（SF Pro 在 12 pt）；`20 pt = -10 (-0.20)`；`32 pt = -13 (-0.41)`；`54 pt = -15 (-0.79)`；`70 pt = -16 (-1.06)`；`96 pt = -16 (-1.50)`；最大负值 `-18 (1/1000em)`（220–260 pt）；表一直延伸到 `260 pt = -18 (-4.57)`；`6 pt = +40 (+0.23)` (来源: typography.md › New York)

### 1.8 Dynamic Type 缩放与布局规则

- 适用平台 — iOS、iPadOS、tvOS、visionOS、watchOS；**macOS 不支持** (来源: typography.md › Supporting Dynamic Type)
- 布局必须适配全部字号 — 在所有字号下文字与字形可读、布局不破 (来源: typography.md › Supporting Dynamic Type)
- 图标随字号放大 — 承载重要信息的 interface icon 在大字号下也要易看；SF Symbols 会自动随 Dynamic Type 缩放 (来源: typography.md › Supporting Dynamic Type)
- 截断最小化 — 最大无障碍字号下显示的有用文本量应接近最大标准字号的量；可滚动区域不要截断，除非能打开独立视图读全文；label 设成按需多行 (来源: typography.md › Supporting Dynamic Type)
- 大字号改布局 — 横向受限时，inline 元素（glyph、时间戳）与容器边界会挤压文本导致截断/重叠，改用「文本在上、次要项在下」的堆叠布局 (来源: typography.md › Supporting Dynamic Type)
- 大字号减少列数 — 多列文本在大字号下因横向空间不足而更难读，字号增大时减少列数 (来源: typography.md › Supporting Dynamic Type)
- 层级不随字号改变 — 例：主元素始终保持在视图顶部附近 (来源: typography.md › Supporting Dynamic Type)
- 有选择地放大 — 大字号下不要等比放大所有文字（如 tab 标题通常不该跟着放大），优先放大用户关心的内容 (来源: typography.md › Conveying hierarchy)
- 字号变化时保持相对层级与视觉区分 (来源: typography.md › Conveying hierarchy)
- 自定义字体必须自行实现 Dynamic Type 与 Bold Text 等无障碍行为；Unity 项目可用 Apple Unity 插件，否则提供其他调字号方式 (来源: typography.md › Using custom fonts)

### 1.9 macOS 专用动态字体变体（11 个 API，用于匹配标准控件）

`controlContentFont(ofSize:)`、`labelFont(ofSize:)`、`menuFont(ofSize:)`、`menuBarFont(ofSize:)`、`messageFont(ofSize:)`、`paletteFont(ofSize:)`、`titleBarFont(ofSize:)`、`toolTipsFont(ofSize:)`、`userFont(ofSize:)`、`userFixedPitchFont(ofSize:)`、`boldSystemFont(ofSize:)`、`systemFont(ofSize:)` (来源: typography.md › Desktop (macOS))

### 1.10 排版类：原文未给出的项

- **原文未给出** 最小可点击文本尺寸（如 44×44 pt）——typography.md 中不存在该数值 (来源: typography.md 全文)
- **原文未给出** 大写（uppercase / all-caps）使用规则——四个文件中均无 (来源: typography.md / icons.md 全文)
- **原文未给出** 斜体（italic）使用规则——仅作为 `italic` 符号名出现在图标清单中，无排版规则 (来源: typography.md / icons.md)
- **原文未给出** 具体「哪个样式用在哪个场景」的章节——只有 text style 定义层面的一句（body 多行阅读、headline 区分标题） (来源: typography.md › Using system fonts)
- **原文未给出** 字号的响应式断点/设备差异表——只有 Dynamic Type 类别阶梯 (来源: typography.md › Specifications)

---

## 二、Color（色彩）

### 2.1 语义色与层级（唯一带明确数量结构的部分）

- 动态背景色两套 — **system** 与 **grouped**，各含 **primary / secondary / tertiary** 三档 (来源: color.md › Mobile (iOS, iPadOS))
- 分组表格用 grouped — `systemGroupedBackground` / `secondarySystemGroupedBackground` / `tertiarySystemGroupedBackground`；否则用 `systemBackground` / `secondarySystemBackground` / `tertiarySystemBackground` (来源: color.md › Mobile (iOS, iPadOS))
- 三档层级语义 — Primary = 整个视图；Secondary = 视图内分组内容/元素；Tertiary = 次级元素内的分组内容/元素 (来源: color.md › Mobile (iOS, iPadOS))
- iOS 前景动态色 8 个 — `label`（主内容）、`secondaryLabel`、`tertiaryLabel`、`quaternaryLabel`、`placeholderText`（控件/文本视图占位）、`separator`（允许底层内容透出）、`opaqueSeparator`（不允许透出）、`link` (来源: color.md › Mobile (iOS, iPadOS))
- system color 共 12 个具名 — red、orange、yellow、green、mint、teal、cyan、blue、indigo、purple、pink、brown (来源: color.md › System colors)
- 每个 system color 有 4 个变体 — Default(light)、Default(dark)、Increased contrast(light)、Increased contrast(dark) (来源: color.md › System colors)
- iOS 系统灰 6 级 — `systemGray`、`systemGray2` … `systemGray6`，每级同样 4 个变体；SwiftUI 中 `systemGray` 的等价物是 `gray` (来源: color.md › iOS, iPadOS system gray colors)
- visionOS — 系统色直接使用 Default **dark** 值 (来源: color.md › System colors)
- macOS 动态色约 35 个具名语义色 — 含 `controlAccentColor`、`controlBackgroundColor`、`controlColor`、`controlTextColor`、`disabledControlTextColor`、`selectedContentBackgroundColor`、`selectedControlColor`、`selectedControlTextColor`、`selectedMenuItemTextColor`、`selectedTextBackgroundColor`、`selectedTextColor`、`unemphasizedSelectedContentBackgroundColor`、`unemphasizedSelectedTextBackgroundColor`、`unemphasizedSelectedTextColor`、`alternateSelectedControlTextColor`、`alternatingContentBackgroundColors`、`findHighlightColor`、`gridColor`、`headerTextColor`、`highlightColor`、`keyboardFocusIndicatorColor`、`labelColor`、`linkColor`、`placeholderTextColor`、`quaternaryLabelColor`、`secondaryLabelColor`、`tertiaryLabelColor`、`separatorColor`、`shadowColor`、`textBackgroundColor`、`textColor`、`underPageBackgroundColor`、`windowBackgroundColor`、`windowFrameTextColor` (来源: color.md › Desktop (macOS))

### 2.2 一色一义 / 语义不得重定义

- 一色一义 — 不要用同一个颜色表达不同含义；用于表示可交互的品牌色不要同时用于非交互文本 (来源: color.md › Best practices)
- 不得重定义动态色语义 — 例如不要拿 `separator` 当文字色，不要拿 `secondaryLabel` 当背景色 (来源: color.md › System colors)
- 不得硬编码系统色值 — 文档里的色值仅供设计参考，实际值会随版本波动；用 `Color` 等 API 取色 (来源: color.md › System colors)

### 2.3 颜色不得单独承载信息

- **颜色不可作为唯一区分手段** — 不能用颜色单独区分对象、指示可交互性或传达关键信息；必须提供替代方式，如文本标签或字形形状 (来源: color.md › Inclusive color)
- 避免难辨识配色 — 对比不足会让图标与文字融入背景；色盲用户可能无法区分某些组合 (来源: color.md › Inclusive color)
- 跨文化含义 — 同一颜色（如红色）在不同文化中含义相反，需确认传达意图 (来源: color.md › Inclusive color)

### 2.4 明暗模式与提高对比度

- 必须同时支持 light / dark / increased contrast 三种语境；system color 自带三语境变体 (来源: color.md › Best practices)
- 自定义色 — 必须为每个变体提供 light、dark 两个版本，并额外提供 increased contrast 选项；Increase Contrast 开启时颜色差异要「far more apparent」，即显著更高的视觉区分度 (来源: color.md › Best practices)
- 单外观模式也要做 — 即使 app 只发布一种外观，也要提供 light 与 dark 两套颜色以支持 Liquid Glass 的自适应 (来源: color.md › Best practices)
- 光照条件测试 — 明亮环境下颜色显得更暗、更不饱和；昏暗环境下显得更亮、更饱和 (来源: color.md › Best practices)
- 背景影响前景色 — 彩色或视觉丰富的内容区上，工具栏/标签栏优先用单色（monochromatic）外观，或选视觉区分度足够的强调色 (来源: color.md › Liquid Glass color)
- 内容层颜色位置 — 避免内容层与控件出现相近色重叠；彩色内容滚动到控件下方时，其默认/静止状态（如可滚动内容顶部）必须保持清晰可读 (来源: color.md › Liquid Glass color)

### 2.5 强调色（Accent Color）

- macOS 自 11 起可指定 accent color，作用于按钮、选中高亮、边栏图标 (来源: color.md › App accent colors)
- 仅当 General > Accent color 设为 **multicolor** 时系统才应用你的 accent color；用户选了其他色时系统用用户的颜色替换你的 (来源: color.md › App accent colors)
- 唯一例外 — 边栏图标使用你指定的**固定色**时，系统不覆盖（因为该颜色承载语义） (来源: color.md › App accent colors)
- 单色内容背景的 app — 用品牌色作 accent color 是有效做法；彩色背景的 app — 优先单色工具栏/标签栏 (来源: color.md › Liquid Glass color)

### 2.6 Liquid Glass 配色规则

- 默认无固有色 — 从正后方内容取色；可对部分元素着色，呈「彩绘/染色玻璃」效果 (来源: color.md › Liquid Glass color)
- 着色要克制 — 只给真正需要强调的元素着色，如状态指示器或主要操作 (来源: color.md › Liquid Glass color)
- 强调主操作的方式 — 给**背景**上色而不是给符号或文字上色（系统对 prominent button 如 Done 就是这么做的）；不要给多个控件的背景都上色 (来源: color.md › Liquid Glass color)
- 小元素自动明暗 — 工具栏、标签栏可根据底层内容在 light/dark 间自适应；符号与文字默认单色方案，底层内容亮时变暗、暗时变亮 (来源: color.md › Liquid Glass color)
- 大元素更不透明 — 侧边栏等大元素上的 Liquid Glass 更不透明，以保证复杂背景上的可读性与更丰富内容 (来源: color.md › Liquid Glass color)

### 2.7 色彩管理（可执行数值）

- sRGB — 在多数显示器上产生准确颜色；给图像套用 color profile (来源: color.md › Color management)
- 广色域 — 兼容显示器上用 Display P3，**16 bits per pixel（每通道）**，导出 **PNG**；设计 P3 需要广色域显示器 (来源: color.md › Color management)
- P3 → sRGB 例外 — 两个非常接近的 P3 色在 sRGB 显示器上可能难区分；P3 渐变在 sRGB 上可能被裁切；用 Xcode asset catalog 为两种色域分别提供图像/颜色版本 (来源: color.md › Color management)
- True Tone — 部分 iPhone/iPad/Mac 用环境光传感器自动调白点；阅读、照片、视频、游戏类 app 可通过 `UIWhitePointAdaptivityStyle` 增强或减弱该效果 (来源: color.md › Best practices)
- 测试方式 — 在 System Settings > Displays 切 P3 / sRGB 色彩描述文件查看外观差异 (来源: color.md › Best practices)
- 提供系统取色器 — app 若允许用户选色，优先用系统取色控件（如 `ColorPicker`），并让用户可存储一组颜色跨 app 访问 (来源: color.md › Best practices)

### 2.8 色彩类：原文未给出的项

- **原文未给出任何对比度数字** — 4.5:1 / 3:1 / 7:1 以及「多大字号对应哪个比值」在 color.md 中**完全不存在**；文件只反复指向 `accessibility.md`（本次未提供该文件） (来源: color.md › Inclusive color / Best practices)
- **原文未给出任何 HEX / RGB 色值** — Specifications 章节的色板在下载版中被替换为 `*image: colors unified ...*` 占位符，只有色名与 API 名 (来源: color.md › Specifications › System colors)
- **原文未给出任何 opacity / alpha 百分比** — 只提到 Hierarchical 渲染模式「varying the color's opacity」，无具体数值 (来源: color.md 全文；参见 sf-symbols.md › Rendering modes)
- **原文未给出「文字尺寸 vs 对比度」的分级规则** — 同上，不在本文件 (来源: color.md 全文)
- **原文未给出 vibrancy 的具体数值** — 仅说明系统色「can automatically adapt to vibrancy and accessibility settings」 (来源: color.md › 正文首段)

---

## 三、Icons（图标）

### 3.1 一致性与字重匹配

- 全app图标四点一致 — 所有 interface icon 必须统一 **size、level of detail、stroke thickness（或 weight）、perspective** (来源: icons.md › Best practices)
- 视觉重量补偿 — 按图标的视觉重量微调其尺寸（dimensions），使其与其他图标视觉一致 (来源: icons.md › Best practices)
- 图标字重匹配相邻文字 — 默认让图标与相邻文本用**相同字重**，除非要刻意强调其中一方 (来源: icons.md › Best practices)
- 光学居中 — 非对称图标几何居中会显得偏；用 **padding** 把调整量做进资源里，之后几何居中即等于光学居中；调整量「typically very small」但影响显著 (来源: icons.md › Best practices)
- 简化优先 — 单一概念、通用隐喻、细节过多会让图标难认 (来源: icons.md › Best practices)

### 3.2 格式与交付

- 自定义 interface icon 用矢量格式 — **PDF 或 SVG**；矢量由系统自动为高分辨率缩放，无需提供多倍图 (来源: icons.md › Best practices)
- PNG 例外 — PNG 不支持缩放，每个 PNG 图标必须提供多倍版本；PNG 用于 app icon 及带阴影/纹理/高光的图像 (来源: icons.md › Best practices)
- 替代方案 — 自建 SF Symbol 并指定 scale，使符号的强调程度匹配相邻文本 (来源: icons.md › Best practices)
- 颜色定义方式 — interface icon 与 symbol 都用 **黑色 + 透明** 定义形状，黑色区域由系统上色 (来源: icons.md › 正文)
- 必须提供替代文本标签（accessibility description）供 VoiceOver 朗读 (来源: icons.md › Best practices)
- 不要做 Apple 硬件产品的复刻 — 硬件设计变化频繁会显过时；必须展示时只用 Apple Design Resources 的素材或对应 SF Symbols (来源: icons.md › Best practices)

### 3.3 选中态与文本

- 选中态一般无需自备 — 工具栏、标签栏、按钮等标准系统组件中系统自动更新选中外观 (来源: icons.md › Best practices)
- 图标内文字 — 仅在传达含义必需时使用（如文本格式化的字符）；必须本地化；若表达「一段文字」用抽象表示，并额外提供镜像版本供 RTL 使用 (来源: icons.md › Best practices)

### 3.4 macOS Document Icon（唯一带完整尺寸规格的图标规则）

- 背景图尺寸（@1x / @2x 成对） — `512×512 / 1024×1024`、`256×256 / 512×512`、`128×128 / 256×256`、`32×32 / 64×64`、`16×16 / 32×32` (来源: icons.md › Document icons)
- 中心图尺寸 — 中心图画布 = 整体文档图标画布的 **一半**（例：32×32 的图标用 16×16 的中心图）；可提供 `256/512`、`128/256`、`32/64`、`16/32` (来源: icons.md › Document icons)
- 中心图边距 — 留出约为画布 **10%** 的边距；图像主体约占画布 **80%**；示例：256×256 画布中主体约落在 `205×205 px` 区域内 (来源: icons.md › Document icons)
- 最小可辨识尺寸 — 文档图标最小显示到 `16×16 px`，设计必须在每个尺寸都可辨识 (来源: icons.md › Document icons)
- 小尺寸降复杂度 — 大尺寸清晰的细节在小尺寸会糊；做法是按缩减后的像素网格对齐并加粗线条；在 `16×16 px` 尺寸可以考虑完全去掉线条 (来源: icons.md › Document icons)
- 右上角禁区 — 系统会自动遮罩并叠加白色折角，**不要把重要内容放在背景填充的右上角** (来源: icons.md › Document icons)
- 配色 — 用一个精简的、彼此区别明显的调色板，形状不复杂 (来源: icons.md › Document icons)
- 文档图标形状 — 传统是「纸张 + 右上角折下」；不提供时 macOS 会用你的 app icon + 文件扩展名合成一个 (来源: icons.md › Document icons)
- 底部文字 — 默认显示文件扩展名；可用更易懂的短词替换（如 SceneKit 用 `scene` 而非 `scn`）；系统自动缩放该文字以适应图标，词要短到在小尺寸下可读；默认**每个字母都被系统大写** (来源: icons.md › Document icons)

### 3.5 标准动作 → SF Symbol 名对照（可直接抄进代码）

- 编辑 — `scissors`(Cut)、`document.on.document`(Copy)、`document.on.clipboard`(Paste)、`checkmark`(Done / Save)、`xmark`(Cancel / Close)、`trash`(Delete)、`arrow.uturn.backward`(Undo)、`arrow.uturn.forward`(Redo)、`square.and.pencil`(Compose)、`plus.square.on.square`(Duplicate)、`pencil`(Rename)、`folder`(Move to / Folder)、`paperclip`(Attach)、`plus`(Add)、`ellipsis`(More) (来源: icons.md › Editing)
- 选择 — `checkmark.circle`(Select)、`xmark`(Deselect / Close)、`trash`(Delete) (来源: icons.md › Selection)
- 文本格式 — `textformat.superscript`、`textformat.subscript`、`bold`、`italic`、`underline`、`text.alignleft`、`text.aligncenter`、`text.justify`、`text.alignright` (来源: icons.md › Text formatting)
- 搜索 — `magnifyingglass`(Search)、`text.page.badge.magnifyingglass`(Find / Find and Replace / Find Next / Find Previous / Use Selection for Find)、`line.3.horizontal.decrease`(Filter) (来源: icons.md › Search)
- 分享导出 — `square.and.arrow.up`(Share / Export)、`printer`(Print) (来源: icons.md › Sharing and exporting)
- 用户账户 — `person.crop.circle`(Account / User / Profile) (来源: icons.md › Users and accounts)
- 评分 — `hand.thumbsdown`(Dislike)、`hand.thumbsup`(Like) (来源: icons.md › Ratings)
- 图层顺序 — `square.3.layers.3d.top.filled`(Bring to Front)、`square.3.layers.3d.bottom.filled`(Send to Back)、`square.2.layers.3d.top.filled`(Bring Forward)、`square.2.layers.3d.bottom.filled`(Send Backward) (来源: icons.md › Layer ordering)
- 其他 — `alarm`、`archivebox`、`calendar` (来源: icons.md › Other)

### 3.6 图标类：原文未给出的项

- **原文未给出图标尺寸阶梯** — 没有 16/20/24/28 pt 之类的 interface icon 推荐尺寸表；icons.md 只给了 macOS 文档图标的位图尺寸 (来源: icons.md 全文)
- **原文未给出 stroke weight 的具体数值** — 只要求「stroke thickness 一致」与「与相邻文字字重匹配」，无 pt 值 (来源: icons.md › Best practices)
- **原文未给出圆角半径或图标网格规则** — 无 corner radius、无 keyline/grid 尺寸 (来源: icons.md 全文)
- **原文未给出填充 vs 线框的通用取舍数值** — 该类规则在 sf-symbols.md 中，且是按容器而非按尺寸决定 (来源: icons.md 全文；见 sf-symbols.md › Design variants)
- **原文未给出图标容量/「图标必须配文字标签」的硬性规则** — 只有「在图标内加文字需本地化」与「必须提供替代文本标签」 (来源: icons.md 全文)

---

## 四、SF Symbols

### 4.1 数量与可用性

- 数量 — 原文表述为 `thousands of consistent, highly configurable symbols`，**未给确切数字** (来源: sf-symbols.md › 正文首段)
- 版本可用性 — 某个年份系统引入的 symbol 与特性在更早系统上不可用，需按目标系统版本判断 (来源: sf-symbols.md › 正文)
- 授权 — 禁止把 symbol（或易混淆的相似图形）用于 app icon、logo 或任何商标用途 (来源: sf-symbols.md › 正文)

### 4.2 字重与缩放（核心可执行规则）

- **9 档字重** — ultralight → black，每一档**逐一对应 San Francisco 系统字体的一个字重**，从而实现符号与相邻文字的精确字重匹配 (来源: sf-symbols.md › Weights and scales)
- **3 档 scale** — `small`、`medium`（**默认**）、`large`；三者**相对于 San Francisco 的 cap height 定义** (来源: sf-symbols.md › Weights and scales)
- scale 的作用 — 在不改变字重匹配的前提下调整符号相对相邻文字的强调程度（同理号文字仍保持字重一致） (来源: sf-symbols.md › Weights and scales)
- 落地 API — SwiftUI `imageScale(_:)`；UIKit `UIImage.SymbolScale`；AppKit `NSImage.SymbolConfiguration` (来源: sf-symbols.md › Weights and scales)

### 4.3 渲染模式（4 种）

- 四种 — `monochrome`、`hierarchical`、`palette`、`multicolor` (来源: sf-symbols.md › Rendering modes)
- Monochrome — 全部图层同一颜色，路径可以是实色或在填充路径中呈透明形状 (来源: sf-symbols.md › Rendering modes)
- Hierarchical — 全部图层同一颜色，按各图层的层级**改变不透明度**，产生深度 (来源: sf-symbols.md › Rendering modes)
- Palette — 两个或更多颜色，**每个图层一个颜色**；若给只有 2 个颜色的符号指定 3 级层级，则 secondary 与 tertiary 共用同一颜色 (来源: sf-symbols.md › Rendering modes)
- Multicolor — 给部分符号施加**固有色**以增强含义（`leaf` 用绿色；`trash.slash` 用红色表示数据丢失）；部分 multicolor 符号含可接收其他颜色的图层 (来源: sf-symbols.md › Rendering modes)
- 分层模型 — 符号路径被组织成图层，例：`cloud.sun.rain.fill` = 3 层（primary = 云，secondary = 太阳与光线，tertiary = 雨滴） (来源: sf-symbols.md › Rendering modes)
- 系统色优先 — 无论哪种渲染模式，使用系统提供的颜色才能让符号自动适配无障碍设置、vibrancy 与 Dark Mode (来源: sf-symbols.md › Rendering modes)
- 必须逐语境验证渲染模式 — 符号尺寸与背景对比度会影响细节可辨性；`automatic` 给出符号偏好的模式，但仍要检查是否有更易读的模式 (来源: sf-symbols.md › Rendering modes)
- 深度用 Hierarchical、变化用 variable color — 不要用 variable color 表达深度 (来源: sf-symbols.md › Variable color)

### 4.4 渐变与 variable color

- 渐变（SF Symbols 7+） — 由单一源色生成平滑线性渐变；可用于全部渲染模式、系统色与自定义色、自定义符号；**任意尺寸都渲染，但在较大尺寸下效果最好** (来源: sf-symbols.md › Gradients)
- variable color — 与渲染模式无关；按数值达到 **0 到 100 percent** 之间的不同阈值，给符号的不同图层上色 (来源: sf-symbols.md › Variable color)
- 阈值分配 — 系统按你要表达的非零状态数量来定义阈值；`speaker.wave.3` 例：无声时没有波形图层着色；无关图层（如喇叭本体）可**退出** variable color；一个符号可用任意数量的图层支持 variable color (来源: sf-symbols.md › Variable color)
- 动画形态 — variable color 动画分 **cumulative**（每层颜色变化保持到循环结束）与 **iterative**（一次一层）；可设置 autoreverse，可把非活动图层**隐藏**而不是降低不透明度 (来源: sf-symbols.md › Animations)
- open loop / closed loop — 图层线性排列、首尾不相接的标注为 open loop；形成完整闭合形状（如环形进度）的标注为 closed loop，其 variable color 动画是无缝连续播放 (来源: sf-symbols.md › Animations)

### 4.5 设计变体（outline / fill / 包围 / 斜杠）

- Outline 是最常见变体 — 无实色区域，外观贴近文字；绝大多数符号同时提供 fill 变体（部分形状内部为实色） (来源: sf-symbols.md › Design variants)
- Outline 适用 — 工具栏、列表，以及任何**符号与文字并排**显示的位置 (来源: sf-symbols.md › Design variants)
- Fill 适用 — **iOS 标签栏、swipe 操作**，以及用 accent color 表达选中的位置；实色区域给符号更强的视觉强调 (来源: sf-symbols.md › Design variants)
- 包围形状 — 圆形/方形/矩形包围可**提升小尺寸下的可辨识度**；包围与斜杠变体常可与 outline/fill 组合 (来源: sf-symbols.md › Design variants)
- 斜杠变体 — 表达某项不可用 (来源: sf-symbols.md › Design variants)
- 容器决定变体 — 多数情况无需手动指定：**iOS 标签栏偏好 fill，工具栏取 outline** (来源: sf-symbols.md › Design variants)
- 语言/书写系统变体 — 覆盖 Latin、Arabic、Hebrew、Hindi、Thai、Chinese、Japanese、Korean、Cyrillic、Devanagari 及若干印度数字系统；随设备语言自动切换 (来源: sf-symbols.md › Design variants)

### 4.6 动画（13 种，可枚举）

- 清单 — `Appear`、`Disappear`、`Bounce`、`Scale`、`Pulse`、`Variable color`、`Replace`、`Magic Replace`、`Wiggle`、`Breathe`、`Rotate`、`Draw On`、`Draw Off` (来源: sf-symbols.md › Animations)
- 兼容性 — 动画适用于库中**全部** SF Symbol，覆盖所有渲染模式、字重、scale，也适用于自定义符号 (来源: sf-symbols.md › Animations)
- Bounce — 默认只播放一次，随后回到初始状态 (来源: sf-symbols.md › Animations)
- Scale — 与 Bounce 不同，**持续保持**直到你设定新 scale 或移除效果 (来源: sf-symbols.md › Animations)
- Pulse — 只对标注为 pulse 的图层生效，可选地对全部图层生效；可一直循环直到条件满足；**只改变不透明度** (来源: sf-symbols.md › Animations)
- Breathe — 与 Pulse 类似，但同时改变**不透明度和尺寸** (来源: sf-symbols.md › Animations)
- Rotate — 对部分符号整体旋转，对另一些只旋转特定部分（如台扇只转扇叶，用 By Layer 选项） (来源: sf-symbols.md › Animations)
- Replace 三种配置 — `Down-up`（旧符号缩小、新符号放大，表达状态变化）、`Up-up`（两者都放大，表达带前进感的状态变化）、`Off-up`（旧符号立即隐藏、新符号放大，强调下一个可用状态/操作）；可在任意符号间、跨全部字重与渲染模式工作 (来源: sf-symbols.md › Animations)
- Magic Replace — 默认的 replace 动画；在形状相关的两个符号间做智能过渡（斜杠画出/擦除、角标出现/消失，或与基础符号独立替换）；**不相关符号之间不触发**，回退为默认 down-up，回退方向可自定义 (来源: sf-symbols.md › Animations)
- Draw On / Draw Off（SF Symbols 7+） — 沿一组引导点画出一条路径，从屏外到屏内（Draw On）或屏内到屏外（Draw Off）；可一次画全部图层、错开画、或逐层画 (来源: sf-symbols.md › Animations)
- **节制使用** — 视图上能加的动画数量没有上限，但过多动画会淹没界面；每个动画都要有明确的信息传达目的 (来源: sf-symbols.md › Animations)

### 4.7 自定义符号

- 制作流程 — 先导出与目标设计相近的符号模板，再用矢量工具修改 (来源: sf-symbols.md › Custom symbols)
- 一致性对齐项 — 细节量、光学重量、对齐、位置、透视都要与系统符号一致；目标是 Simple / Recognizable / Inclusive / Directly related (来源: sf-symbols.md › Custom symbols)
- 负边距（negative side margins） — 当符号含角标等导致宽度增加的元素时，用负边距做**光学水平对齐**（例：让一列含角标的 folder 符号左对齐）；命名格式必须遵循配置模式，如 `left-margin-Regular-M`（含字重与 scale） (来源: sf-symbols.md › Custom symbols)
- 图层注解（annotating） — 给每个图层指定具体颜色，或指定层级（primary / secondary / tertiary）；不同渲染模式可对同一符号的不同实例生效 (来源: sf-symbols.md › Custom symbols)
- 用动画必须注解 — 要按图层动画就必须在 SF Symbols app 中标注图层；**Z-order 决定 variable color 上色顺序**，可选 front-to-back 或 back-to-front；也可按图层组动画 (来源: sf-symbols.md › Custom symbols)
- 画整形状（whole shapes） — 例：类似 `person.2.fill` 的自定义符号不要给左边人物挖孔；画完整人物形状，再额外画一条偏移路径表示间隔，稍后把该偏移路径标注为 erase layer (来源: sf-symbols.md › Custom symbols)
- 必须测全部动画预设 — 图层运动时形状与路径可能不符合预期 (来源: sf-symbols.md › Custom symbols)
- 不要自造常见变体 — 圈围、角标等交给 SF Symbols app 的 component library 生成，以保持与内置符号的设计一致性 (来源: sf-symbols.md › Custom symbols)
- 版权 — 描绘 Apple 产品与特性的符号可在 app 中显示但**不可自定义**；SF Symbols app 用 Info 图标标出不可自定义的符号，inspector 面板说明使用限制 (来源: sf-symbols.md › Custom symbols)
- 必须提供替代文本标签供 VoiceOver 使用 (来源: sf-symbols.md › Custom symbols)

### 4.8 SF Symbols 类：原文未给出的项

- **原文未给出最小尺寸数值** — 没有「不得小于 N pt」之类的规定，只有「包围形状可提升小尺寸可辨识度」与「渐变在大尺寸最好看」 (来源: sf-symbols.md 全文)
- **原文未给出符号确切总数** — 只有 `thousands` (来源: sf-symbols.md › 正文)
- **原文未给出对齐的 pt 度量** — 只说明「相对于 SF 的 cap height 定义」与「自动与所有字重字号的文本对齐」，无 cap height 具体数值 (来源: sf-symbols.md › Weights and scales)
- **原文未给出动画时长/缓动数值** — 无 duration、无 easing 参数 (来源: sf-symbols.md › Animations)
