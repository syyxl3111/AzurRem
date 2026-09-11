# apple-design-skill 落地参考

> **面向**：接下来要给 `AzurPilotMobile`（Kotlin + Jetpack Compose / Material3，minSdk 26，targetSdk 37，竖屏锁定）做 UI 重构的人。
> **调研对象**：<https://github.com/dickwu/apple-design-skill>
> **本地克隆**：`D:\Temp\apple-design-skill`（`git clone --depth 1`，HEAD = `da2da6d`，"fix: report stale anchors, tighten Cursor rules and Liquid Glass accessibility notes"）
> **仓库自述**：122 页 Apple HIG（从 developer.apple.com 抓取，含 57 个组件页）+ 1 篇手工维护的 Liquid Glass 指南 + 生成脚本。文档里的日期口径是 2026 年（README 提到"Apple 于 2026 年 6 月重新引入八条设计原则"）。
>
> **一句话结论**：它不是一个"iOS 皮肤库"，而是一份**可执行的评审标尺 + 数值词典**。对安卓工程最大的价值在于：无障碍硬指标、Dynamic Type 字号/字距原始数值、以及"呈现方式选择树"（什么时候用模态、什么时候用气泡、什么时候用 sheet）。它同时明确写了"Android 不应该模仿 iOS 玻璃"——这跟你现在的"Apple HIG + 轻薄亚克力"路线**存在一处真实冲突**，第 5 节专门处理。

---

## 0. TL;DR（只读这一段的话）

1. **skill 是 SKILL.md 式的一篇指南 + 122 个可检索的 HIG 摘录文件**，不是规则引擎，没有可执行脚本（只有一个重新抓取上游的 `pull-hig.mjs`）。
2. 一次评审固定走 **5 个 lens**：无障碍 → 平台惯例 → 视觉与手艺 → 交互 → 文案。每条结论必须写成 `What / Why(带出处) / Fix`，并打 Critical/High/Medium/Low。
3. 它自己声明：**纯 Android 应用只适用「原则 + 基础（无障碍、颜色、排版、布局、文案）」，不适用 Apple 的平台惯例**（`SKILL.md` Step 1 明写）。也就是说，你按它改 UI 是"借用它的基础层"，平台层（tab bar 长什么样、sheet 怎么弹）需要自己判断。
4. 最硬的、可直接抄进代码的是**数字**：44×44pt 触控目标、17pt 默认/11pt 最小字号、4.5:1/3:1 对比度、12pt/24pt 控件周边留白、完整 Dynamic Type 阶梯 + SF Pro 字距表。第 3 节全部列出。
5. 最有价值的三条改造建议（详细版在第 6 节）：
   - **① 补上"身份识别"**：Tab 栏加文字标签 + 5 个页面补大标题，并修正 `headlineMedium` 的字距（现在是 `-0.7sp`，按 HIG 28pt 应该是 **+0.38sp**——符号反了）。现在 Home/Tasks/Stats 三页**既没有标题、Tab 也没有标签**，用户没有文字能确认自己在哪。
   - **② 无障碍补洞**（其中一条是真 bug）：Toast 超时接 `getRecommendedTimeoutMillis` + 可手动关闭；Toast/日志加 `liveRegion`、标题加 `heading()`；6 处低于 44pt 的触控目标套 `minimumInteractiveComponentSize()`；**修状态栏图标不跟随应用内主题**（系统深色 + 应用内选浅色 ⇒ 白图标配浅底，看不见）。
   - **③ 气泡确认条在手机（紧凑宽度）上不成立**：`popovers.md` 明确要求紧凑宽度改用 sheet。语义保留、形态改成底部 action sheet（Cancel 在底、破坏性在顶）。

---

## 1. 这个 skill 是什么

### 1.1 形态：一篇指南 + 一个资料库

| 文件 | 性质 | 行数 | 作用 |
| --- | --- | --- | --- |
| `SKILL.md` | **手写，核心** | 415 | 立场、八条原则、五个 lens、报告模板、改进模式、跨平台词表 |
| `AGENTS.md` | 手写 | 93 | 给 agent 的操作手册（怎么跑一次评审、文件分工、提交前检查） |
| `.cursorrules` | 手写 | 20 | Cursor 入口，内容是 SKILL.md 的 7 步摘要 |
| `README.md` | 手写 | 159 | 安装方式与总览 |
| `references/hig-lookup.md` | **生成** | — | 路由表：按 Apple 的 6 个大类分组，带每页一句话摘要 + Apple 最后修改日期 |
| `references/hig/*.md` | **生成 122 篇** | 共约 1.1 MB | Apple HIG 原文（保留原标题、表格、note、变更日志），按设备类重标了平台小节 |
| `references/hig/liquid-glass.md` | **手写 1 篇** | 125 | 唯一的人工策展指南：规则 + 评审清单 + 跨平台翻译 |
| `scripts/pull-hig.mjs` | 脚本 | — | Node 18+，零依赖，约 150 个请求，从 Apple 站点重新抓取。**评审时用不到** |

> 目录里 `references/hig/` 实际有 **123** 个 `.md`（122 篇生成 + 1 篇策展），README 说的 122 指的是生成的那批。

### 1.2 关键设计：它是"按需加载"的

这是整个 skill 里最值得学的一条工程约束——`SKILL.md` 明确规定：

- **每次都加载**：`accessibility.md`、`layout.md`、`typography.md`、`color.md`，加 `designing-for-ios.md` 或 `designing-for-macos.md`。
- **按屏幕内容再加载 3~6 篇**（有一张"屏上出现什么 → 加载什么"的路由表）。
- 一次评审总共加载 **8~12 篇，绝不整目录加载**。
- **没打开过的文件不许引用**；没有依据的观点必须标注"这是我的判断"。

`AGENTS.md` 里还写死了一条维护约束：`SKILL.md` 控制在 400 行左右，因为"它每次触发都会整体加载"。**如果你要把它接到自己的 agent 上，这条约束决定了你要不要保留全部 122 个文件**——保留意味着 agent 有能力自己路由到细节，代价是仓库有 1 MB 的 Markdown。

### 1.3 怎么用（挂载方式）

**官方推荐（skills CLI，覆盖 Claude Code / Codex / Cursor / OpenCode / Windsurf）**

```bash
npx skills add dickwu/apple-design-skill          # 装到当前项目
npx skills add dickwu/apple-design-skill -a claude-code   # 只给某一个 agent
npx skills add dickwu/apple-design-skill -g       # 装到用户级
npx skills add dickwu/apple-design-skill --list   # 预演，看会装什么
npx skills update apple-design                    # 之后拉更新
```

**Claude Code 手动**

```bash
git clone https://github.com/dickwu/apple-design-skill.git ~/.claude/skills/apple-design
```
触发方式：问"review my design"之类会自动触发，也提供 `/apple-design` 斜杠命令。

**Cursor**：`npx skills add ... -a cursor`，或把仓库放进项目里、把它的 `.cursorrules` 放在你自己的 rules 旁边。

**Codex / 其它 agent**：加 submodule，然后在 rules 文件里写三行指路：

```markdown
## Design reviews
Follow `.design-rules/SKILL.md`. Route topics with `.design-rules/references/hig-lookup.md` and
load the relevant `.design-rules/references/hig/*.md` files before giving design feedback.
```

**对这个工程的实际建议**：因为你要改的是一份 Kotlin 代码，最省事的挂法是

```powershell
git clone https://github.com/dickwu/apple-design-skill.git D:\Tools\apple-design-skill
```

然后在工程的 `AGENTS.md`（或你用的 agent 的 rules 文件）里写清三件事：**① 跟随 `SKILL.md`；② 本工程是 Android/Compose，只走 Foundations + 原则，平台惯例按第 5 节的裁定执行；③ 每次评审只加载 8~12 篇**。不要把它 vendored 进 App 的构建产物（见 1.6 授权）。

### 1.4 一次评审的固定流程

**Step 1 建立上下文** — 平台/框架、应用类别与受众、拿到的是什么物料（截图能看布局层级和文案，**对比度必须从真实色值算，不能从 JPEG 估**）、这一屏的"论点"是什么、用户想要什么。
**Step 2 载入参考**。
**Step 3 按顺序过 5 个 lens**：

| Lens | 内容 | 失败等级 |
| --- | --- | --- |
| 1 无障碍 | 文字缩放、字号、对比度、控件尺寸、不靠颜色传信息、动效可关 | **Critical** |
| 2 平台惯例 | tab bar / toolbar / sheet / 搜索 / 安全区；桌面是菜单栏 / 窗口 / 侧栏 | 通常 High |
| 3 视觉与手艺 | 颜色语义、字体层级、对齐分组、图标一致性、动效克制；**外加"有没有观点 / 是不是模板"** | High 或 Medium |
| 4 交互 | 加载、反馈、alert、模态、破坏性操作、撤销、数据录入 | 通常 Medium |
| 5 文案 | 标签说清结果、大小写规则、报错给解法、空状态给下一步 | 通常 Medium |

**Step 4 写报告**，固定六段（无内容的段可省，Summary 必须有）：
`Summary`（2~3 句 + 评级 Excellent/Good/Needs work/Critical issues + 点出这一屏的"记住点"）、`Critical`、`Improvements`、`Craft notes`、`What works`、`Platform notes`。

每条发现三段式：

```text
- **What**: 问题，有数字就写数字
- **Why**: 出处，格式 `file.md › Heading`，带一句短引用
- **Fix**: 在你的框架里的具体改法
```

**评级由标签推导**：有 Critical → `Critical issues`；多个 High → `Needs work`；无 Critical 且至多两三个 High → `Good`；全部不超过 Medium 且手艺 lens 找到了观点 → `Excellent`。

### 1.5 对安卓工程的适用范围（很重要）

`SKILL.md › Step 1: Establish context › Scope and limits` 原文：

> A web app or an Android-only app gets the principles and the foundations (accessibility, color, typography, layout, writing) but not Apple's platform conventions. Say which parts apply.

翻译成可执行的裁定：

| 层 | 对 AzurPilot Mobile 是否适用 |
| --- | --- |
| 八条设计原则 | ✅ 完全适用（产品层，与平台无关） |
| 无障碍 lens（数值部分） | ✅ 完全适用，且应作为硬门槛 |
| 排版 / 颜色 / 布局 / 文案 | ✅ 适用；但"系统语义色"要翻译成 Compose 的 token，不能照搬 |
| 平台惯例（tab bar / toolbar / sheet / 搜索） | ⚠️ **部分适用**：本工程是刻意模仿 iOS 观感的竖屏安卓应用，所以可以借用 iOS 惯例作为设计目标；但**手势（边缘返回）、系统栏、返回键、无障碍服务必须以 Android 为准** |
| macOS 相关的一切 | ❌ 不适用 |

另外 `liquid-glass.md › React Native` 里有一句直接针对安卓的裁定（见第 5 节），它是这份 skill 里对你**唯一一条明确的反对意见**。

### 1.6 授权与引用说明（动手前先看）

- **仓库里没有 LICENSE 文件。** 顶层只有 `.cursorrules / .gitignore / AGENTS.md / README.md / SKILL.md` 五个文件。没有 license 授权 = 默认"保留所有权利"。
- README 的 `Origin and license` 段落说明：**122 页指南正文的著作权属于 Apple Inc.**，是从公开的 HIG 复制过来的，每个文件顶部都有源链接，本项目与 Apple 无关联、未被背书；"skill、策展指南与脚本按原样提供，自行判断使用"。
- **落地口径建议**：
  - ✅ 可以在内部文档里**短引用**规则原文并把出处写成 `apple-design-skill/references/hig/<file>.md › <Heading>`（本文件第 7 节就是这么做的）。
  - ✅ 可以引用它的**数值**（字号、对比度、尺寸）——数值本身是事实，且与 Apple 公开规范一致。
  - ❌ 不要把 122 个 `.md` 打包进 App、发布物或对外分发的 SDK。
  - ❌ 不要用 SF Symbols 的名字/图形做自己的图标（`sf-symbols.md` 明确禁止在 app 图标、logo 或任何商标用途中使用符号或"易混淆的相似图形"）。
  - ⚠️ Apple HIG 说明里出现的 `SF Pro` 字体不可随 App 分发；本工程用 Inter 替代是正确做法。

---

## 2. 核心原则（按重要性排序）

前八条是 Apple 2026 年 6 月重新引入的八条原则（`design-principles.md`），后六条是 `SKILL.md` 的手艺 lens 加上跨 lens 反复出现的判断。

| # | 原则 | 一句话 |
| --- | --- | --- |
| 1 | **Purpose 目的** | 先问这一屏存在的意义，设计服务于它，而不是服务于好看。 |
| 2 | **Simplicity 简洁** | 简洁不是极简主义，是"每个元素都挣到了自己的位置"。 |
| 3 | **Craft 手艺** | 间距、对齐、措辞、动画都要做完，没做完就是没做完。 |
| 4 | **Familiarity 熟悉** | 沿用人们已经懂的物理与数字隐喻，并且**全局一致**。 |
| 5 | **Flexibility 灵活** | 适配不同尺寸、文字大小、输入方式与能力。 |
| 6 | **Agency 自主** | 让人能自由探索、能跳过、能撤销，出错能恢复。 |
| 7 | **Responsibility 责任** | 权限、数据、意图都透明可查。 |
| 8 | **Delight 愉悦** | 有情绪，但 **Apple 自己的警告：别把愉悦当成装饰**。 |
| 9 | **平台不是皮肤** | 系统组件承担导航与控件，身份认同放在颜色、字体、图像、语气和少数几个"决定性瞬间"里。 |
| 10 | **不要抹平个性** | "如果你的修改让设计变得和模板无法区分，你就改过头了。" |
| 11 | **数字，不是形容词** | 写"12px #AAAAAA on white, 2.3:1"而不是"很难读"；量不出来就说你需要什么才能量。 |
| 12 | **引用，或者标注为判断** | 绝不编造规范。 |
| 13 | **审流程，不只审单屏** | 一屏做得好，但周围的导航结构可能是坏的。 |
| 14 | **别过度批评** | "好的设计得到一份短评审。"

**三张它点名的"生成式 UI 模板脸"**（用于自查是不是做成了模板，`SKILL.md › Lens 3`）：
① 暖奶油底 + 高对比衬线 + 陶土色强调；② 近黑底 + 一个酸性绿或朱红；③ 大报式细发丝线 + 零圆角 + 密集分栏。
以及两个具体的套路化嫌疑：**"大数字压小标签 + 渐变强调"的 hero**、**给非序列内容加 01/02/03 编号**。

---

## 3. 可执行规则清单

> 格式约定：`规则 — 数值/做法`，括号里是出处。凡是标 ⚠️ 的是需要结合安卓判断的。

### 3.1 无障碍（这些是 Critical 门槛，先做）

**字号**

- 平台默认/最小字号：**iOS 17pt / 11pt**；macOS 13pt / 10pt。（`accessibility.md › Vision`、`typography.md › Ensuring legibility`）
- 自定义字体也要遵守上表；**细字重（Ultralight/Thin/Light）要放大到超过推荐值才用**；"一般避免细字重，优先 Regular / Medium / Semibold / Bold"。（`typography.md › Ensuring legibility`）
- 文字要能放大约 **200%**（watchOS 140%）。（`accessibility.md › Vision`）
- 文字变大时：**图标要一起变大**；**尽量减少截断**——不要在可滚动区域截断，除非能另开一个视图读全文；避免为了塞下而硬截 `maxLines`。（`typography.md › Supporting Dynamic Type`）
- 大字号下可以考虑**改成堆叠布局**（文字在上、次要信息在下），并减少分栏。（同上）
- **无论字号怎么变，信息层级必须保持**（主要元素仍然在顶部）。（同上）

**对比度**（从真实色值算，不许估）

| 文字尺寸 | 字重 | 最低对比度 |
| --- | --- | --- |
| ≤ 17pt | 全部 | **4.5:1** |
| 18pt | 全部 | **3:1** |
| 全部 | Bold | **3:1** |

（`accessibility.md › Vision`）
- 深色模式里的最低值同样是 **4.5:1**；自定义前景/背景色**尽量做到 7:1**，尤其是小字。（`dark-mode.md › Dark Mode colors`）
- 达不到就要在系统"增强对比度"打开时提供更高对比方案。（`accessibility.md › Vision`）

**控件尺寸与间距**

| 平台 | 默认控件尺寸 | 最小控件尺寸 |
| --- | --- | --- |
| iOS / iPadOS | **44×44 pt** | **28×28 pt** |
| macOS | 28×28 pt | 20×20 pt |

（`accessibility.md › Mobility`）
- 按钮命中区一般至少 **44×44 pt**。（`buttons.md › Best practices`）
- **控件之间的间距和尺寸一样重要**：带边框（bezel）的元素周围约 **12pt**；无边框元素按其可见边缘算约 **24pt**。（`accessibility.md › Mobility`）
- 玻璃/材质**不得把命中区缩到平台最小值以下**。（`liquid-glass.md › Review checklist`）

**其它**

- **任何信息都不能只靠颜色传达**（用形状、图标、文字标签补充）。（`accessibility.md › Vision`、`color.md › Inclusive color`）
- **每个纯图标控件都要有可被朗读的文本标签**。（`accessibility.md › Vision`）
- 常见交互**用最简单的手势**，避免自定义多指/多手手势。（`accessibility.md › Mobility`）
- **手势必须有替代路径**：如果用滑动关闭视图，也要给一个按钮。（`accessibility.md › Mobility`）
- **尽量减少"定时自动消失"的界面元素**：需要更长时间处理信息的人会被卡住；**优先用显式操作关闭**。（`accessibility.md › Cognitive`）
- 减少动态效果打开时：收紧弹簧减少回弹、让动画直接跟手、**不要动画 z 轴深度变化**、**用淡入淡出替换 x/y/z 位移**、**不要做模糊的进出动画**。（`accessibility.md › Cognitive`）
- 打开"减少透明度"要有**不透明兜底**；打开"增强对比度"要有**更实的填充和更明显的描边**。（`liquid-glass.md › Review checklist`）
- 尽量减少时间盒元素、不要自动播放音视频、注意闪烁。（`accessibility.md › Cognitive`）

### 3.2 排版

**iOS 默认（Large）字号阶梯** —— 这是最该照着做的一张表。（`typography.md › Specifications › iOS, iPadOS Dynamic Type sizes › Large (default)`）

| 样式 | 字重 | 字号 pt | 行高 pt | 强调字重 |
| --- | --- | --- | --- | --- |
| Large Title | Regular | **34** | 41 | Bold |
| Title 1 | Regular | **28** | 34 | Bold |
| Title 2 | Regular | **22** | 28 | Bold |
| Title 3 | Regular | **20** | 25 | Semibold |
| Headline | **Semibold** | **17** | 22 | Semibold |
| Body | Regular | **17** | 22 | Semibold |
| Callout | Regular | **16** | 21 | Semibold |
| Subhead | Regular | **15** | 20 | Semibold |
| Footnote | Regular | **13** | 18 | Semibold |
| Caption 1 | Regular | **12** | 16 | Semibold |
| Caption 2 | Regular | **11** | 13 | Semibold |

**同一个阶梯在 7 档标准字号 + 5 档无障碍字号下的缩放**（首尾最具参考性）：

| 档位 | Large Title | Body | Caption 2 |
| --- | --- | --- | --- |
| xSmall | 31 | 14 | 11 |
| Large（默认） | **34** | **17** | **11** |
| xxxLarge | 40 | 23 | 17 |
| AX1 | 44 | 28 | 20 |
| AX2 | 48 | 33 | 24 |
| AX3 | 52 | 40 | 29 |
| AX4 | 56 | 47 | 34 |
| AX5（最大） | **60** | **53** | **40** |

（完整档位：xSmall / Small / Medium / Large / xLarge / xxLarge / xxxLarge / AX1–AX5。**Body 从 17pt（Large）到 53pt（AX5），约 3.1 倍**——这是我前面说的"至少要能放大 200%"的实际含义，比 200% 更狠。）

**几个容易做错的细节**（`typography.md › iOS, iPadOS Dynamic Type sizes`）：
- **Body 和 Headline 在整个 12 档里字号与行高完全相同**，只差字重（Body = Regular，Headline = Semibold）。做 token 时不要给它们两个不同的字号。
- **Caption 1 / Caption 2 在 xSmall→Medium 三档里恒为 11/13**；Footnote 在 xSmall→Medium 恒为 12/16。小字号的缩放不是线性的。
- Large Title / Title 1 / Title 2 的基准字重是 **Regular**（强调时才 Bold）；Title 3 及以下强调用 **Semibold**。也就是说 iOS 的大标题默认**不是粗的**——用 Bold 是强调态。

**SF Pro 字距表**（单位 pt，正数放宽、负数收紧）—— 这条最容易被忽略，也最容易做错。（`typography.md › Specifications › Tracking values › iOS, iPadOS, visionOS tracking values › SF Pro`）

| 字号 | 字距 | 字号 | 字距 | 字号 | 字距 |
| --- | --- | --- | --- | --- | --- |
| 11 | **+0.06** | 17 | **−0.43** | 24 | **+0.07** |
| 12 | **0.00** | 18 | −0.44 | 26 | +0.22 |
| 13 | **−0.08** | 19 | −0.45 | 28 | **+0.38** |
| 14 | −0.15 | 20 | **−0.45** | 34 | **+0.40** |
| 15 | −0.23 | 21 | −0.36 | 40 | +0.37 |
| 16 | −0.31 | 22 | −0.26 | 48 | +0.35 |

**规律：负字距只出现在 13–22pt 这一段；12pt 及以下为正或零；24pt 及以上重新变正且快速增大。** 大标题（34pt）是 **+0.40**，不是负的。
> ⚠️ **重要限定**：这张表是给 **SF Pro** 的。Inter 本身的默认字距比 SF Pro 紧，把 SF Pro 的字距值直接套到 Inter 上会**二次收紧**。正确做法是把它当参考量级，用真机截图对齐视觉密度，而不是硬编码。

**其它排版规则**
- 字体家族越少越好。（`typography.md › Conveying hierarchy`）
- 用字重、字号、颜色共同建立层级，不要只靠字号。（同上）
- 行距：宽栏/长文用松行距；受限高度（如列表行）可以用紧行距，但**三行及以上不要用紧行距**。（`typography.md › Using system fonts`）
- 系统字体的强调字重可以是 medium / semibold / bold / heavy。（`typography.md › Specifications`）
- 字号变大时**不要等比放大所有东西**——优先放大用户真正在读的内容（例如 tab 标题不需要跟着变大）。（`typography.md › Conveying hierarchy`）

### 3.3 颜色与明暗

- **一种颜色只表达一件事**；同一个品牌色不能既表示"可点击"又拿来装饰非交互文本。（`color.md › Best practices`）
- **不要硬编码系统色值**；文档里给出的色值只是设计期参考，实际值会随版本浮动。（`color.md › System colors`）
- **每个自定义色都要给浅色变体、深色变体，以及各自的"增强对比度"变体。**（`color.md › Best practices`）
- **iOS 有两套背景层级**，各含 primary / secondary / tertiary：
  - 有分组表格视图时用 **grouped** 系列（`systemGroupedBackground` 等）；
  - 否则用 system 系列（`systemBackground` 等）。
  - 用法：primary = 整体视图；secondary = 整体视图内的分组；tertiary = 次级元素内的分组。（`color.md › Mobile (iOS, iPadOS)`）
- **iOS 前景语义色（8 个）**：`label`、`secondaryLabel`、`tertiaryLabel`、`quaternaryLabel`、`placeholderText`、`separator`（允许透出底层内容）、`opaqueSeparator`（不允许透出）、`link`。**不要重新定义它们的语义**（比如别拿 separator 当文字色）。（`color.md › Mobile (iOS, iPadOS)`）
- **不要提供 App 自己的外观（浅色/深色）开关。** 理由：用户要改两个地方；更糟的是用户会以为你的 App 坏了，因为它不跟随系统。（`dark-mode.md › Best practices`）
- 深色模式的背景分 **base（更暗，后退）** 与 **elevated（更亮，前进）**；模态/popover 在前景时自动用 elevated。（`dark-mode.md › Mobile (iOS, iPadOS)`）
- 深色模式下如果内容图自带白底，**把图稍微压暗**，别让它在深色环境里发光。（`dark-mode.md › Dark Mode colors`）
- 图标/图片：两套外观都要测；同一份资源两边都好看就用一份，否则做两套。（`dark-mode.md › Icons and images`）
- 文字用系统 label 色（primary/secondary/tertiary/quaternary），不要自己调透明度当层级。（`dark-mode.md › Text`）
- 深色**不是反色**。（`dark-mode.md › Dark Mode colors`）

### 3.4 布局与间距

- **把相关内容分组**（用留白、背景形状、颜色、材质或分隔线），并保证内容与控件清晰可辨。（`layout.md › Best practices`）
- **重要信息给足空间**，别被次要细节挤掉；次要信息可以放到别处或另一个视图。（同上）
- **内容延伸到屏幕边缘**；可滚动布局要滚到屏幕底部和两侧；**控件和导航组件（侧栏、tab 栏）是浮在内容之上的，不在同一平面**。（同上）
- **控件与内容要有区分**：优先用"滚动边缘效果"（scroll edge effect）做过渡，而不是给控件加背景。（`layout.md › Visual hierarchy`）
- **按重要性放置**：人们按从左上到右下的阅读顺序看，重要的放顶部和前面。（同上）
- **对齐组件**，对齐 + 缩进能帮助理解层级。（同上）
- **用渐进披露（progressive disclosure）** 而不是塞满；显示一部分来暗示还有更多。（同上）
- **控件周围要给足空间并逻辑分组**；不相关的控件挨太近会让人分不清。（同上）
- **避免全宽按钮**：iOS 上按钮应该尊重系统边距、与屏幕边缘有内缩。"如果你确实需要全宽按钮，确保它与硬件弧度和相邻安全区协调。"（`layout.md › Phone (iOS)`）
- **尊重安全区**；安全区不只是避让，也是"避让硬件特性 + 为可变大小的栏留位"的机制。（`layout.md › Guides and safe areas`）
- 目标方向：iPhone 默认支持竖屏 + 横屏（⚠️ 本工程锁定竖屏，见第 5 节）。（`layout.md › Phone (iOS)`）
- 状态栏一般保持可见，除非是沉浸式游戏/媒体。（同上）
- 层级靠空间、尺寸、字重建立，不靠装饰。（`SKILL.md › Cross-platform notes › Both`）
- **不要在没有理由的密集/极简之间摇摆**：宁可渐进披露，不要堆密度。（`SKILL.md › Lens 3`）

### 3.5 层级与材质（这一节直接决定你的"亚克力"该怎么收口）

**两层模型**（`liquid-glass.md › The two layers`、`materials.md`）

```text
┌──────────────────────────────────────────┐
│ 功能层 Functional layer                   │  玻璃只允许在这里
│ tab bar · toolbar · sidebar · sheet       │
├──────────────────────────────────────────┤
│ 内容层 Content layer                      │  不透明面或"标准材质"
│ 文字 · 图片 · 列表 · 媒体 · App 背景       │
└──────────────────────────────────────────┘
```

- **内容层绝对不能用玻璃/模糊。** 放进去会造成"不必要的复杂度和混乱的层级"。唯一例外：内容层里带**瞬时交互**的控件（slider、toggle），在**被操作的那一刻**可以短暂呈现玻璃感。（`materials.md › Liquid Glass`）
- **玻璃要用得非常克制。** 只给最重要的少数功能元素用；自定义控件上到处都是玻璃会分散对内容的注意力。（同上）
- 两个变体：

| 变体 | 行为 | 用在 |
| --- | --- | --- |
| regular | 模糊 + 调整底层亮度 | 大多数组件；文字多的（alert、侧栏、popover）；任何可能影响可读性的背景 |
| clear | 高度透光，保留底层内容 | 只用于浮在照片/视频等丰富媒体之上的控件 |

（`materials.md › Liquid Glass`）
- **clear 变体的压暗层**：底层内容亮 → 考虑加 **35% 不透明度的深色压暗层**；底层已经够暗、或用了自带压暗的媒体控件 → 不加。（同上）
- **玻璃上没有固有色**，它取背景的颜色；上面的符号和文字默认**单色**（浅底上变深、深底上变浅）。（`color.md › Liquid Glass color`）
- 小的元素（toolbar、tab bar）会在浅/深外观之间自适应；大的元素（侧栏）更不透明以保证可读性。（同上）
- **给玻璃和玻璃上的符号/文字上色要克制**，只留给确实需要强调的（状态指示、主操作）。（同上）
- **要强调主操作，就染背景，不要染符号或文字**（Done 按钮就是这么做的）；**不要给多个控件的背景上色**。（同上）
- 内容层本身颜色丰富时，**toolbar / tab bar 用单色**，或者选一个区分度足够的强调色。（同上）
- 注意**静止态**（比如可滚动屏幕的顶部）也必须清晰，即使之后会有彩色内容滚到控件下面。（同上）
- **一屏最多一到两个"强调"按钮**，绝不能是一排。（`buttons.md`、`liquid-glass.md › Review checklist`）

**跨平台复刻时它给的（非规范的）起始值**（`liquid-glass.md › Cross-platform translation`）

| 属性 | regular | clear |
| --- | --- | --- |
| 背板模糊 | **20–40 px** | 8–16 px |
| 填充不透明度 | **60–80%**（白或黑，看外观） | 20–40% |
| 饱和度提升 | **1.2–1.5×** | 同 |
| 自适应外观 | 采样下方亮度，在深浅标签色之间切换 | 同 |
| 滚动边缘效果 | 内容与栏相接处额外模糊 + 淡出 | 同 |
| 压暗层 | — | 亮背景下约 **35% 黑** |

> 原文明确：**除了 35% 这个数，其余都是"实用起点，不是规范"**。

**安卓的专门裁定（这条和本工程直接冲突，见第 5 节）**

> `liquid-glass.md › React Native`：On Android, blur is costly and inconsistent across versions, and the platform's own design language does not use glass. **Prefer an opaque or lightly translucent bar there rather than imitating iOS.**

**评审清单（7 条，可直接当 checklist 用）**（`liquid-glass.md › Review checklist`）
1. 层级纪律：玻璃只出现在浮动的控件与导航上；出现在 App 背景、卡片、列表行、内容容器上 = **缺陷**。
2. 克制：数一数自定义玻璃面有几个，超过"最重要的少数功能元素" = 缺陷；玻璃叠玻璃会毁掉它存在的意义。
3. 变体选对：文字多、背景杂 → regular；clear 只用在媒体之上，且亮媒体要考虑约 35% 的压暗层。
4. 颜色预算：一屏一到两个着色主操作，绝不成排；彩色内容之上标签保持单色；在静止滚动位置检查可读性。
5. 无障碍状态：减少透明度 → 不透明兜底；增强对比度 → 更实的填充和描边；减少动态效果 → 去掉形变与折射动画。
6. 滚动边缘效果：内容与栏相接处淡出/模糊，而不是硬碰硬。
7. 命中区：玻璃不得把命中区缩到平台最小值以下（移动 44×44 默认、28×28 最小）。

### 3.6 导航与组件

**Tab bar**（`tab-bars.md`）
- 用来**导航，不是用来放操作**；要放作用于当前视图的控件，用 toolbar。
- **导航到不同区块时 tab 栏必须可见**；隐藏会让人忘记自己在哪。唯一例外：被**模态**盖住（模态是临时的、自包含的）。
- **避免溢出 tab**（iPhone 上溢出会变成 More，让人更难发现内容）。
- **不要禁用或隐藏 tab 按钮**，即使内容不可用；内容为空就解释为什么。
- **必须有文字标签**；**尽量用单个词**。
- **优先用填充（filled）图标**，与平台一致；图标在紧凑视图里在标签上方。
- 徽章（badge）只留给关键信息，不要滥用。
- 不要给 tab 标签和内容层背景用相近的颜色。
- 如果让用户自定义 tab，**默认列表不超过 5 个**。
- ⚠️ iPhone 上 tab 栏浮在屏幕底部，垫在**玻璃**上，内容能从下面透出来。

**Navigation bar / Toolbar**（`toolbars.md`）
- 一个 toolbar 包含三类内容：**当前视图的标题**、导航控件（返回/前进/搜索）、操作项。
- **每一项都要有存在的理由**，别挤；定义好变窄时哪些项进溢出菜单。
- **iPhone 上要用大标题（large title）帮助人们定位**：默认行为是大标题在开始滚动时过渡为标准标题、滚回顶部时变回大标题，用来提醒当前位置。
- iOS 上导航栏也可以叫 navigation bar。
- **用标准的返回和关闭按钮**；**不要给返回/关闭按钮加"Back"/"Close"文字标签**。
- 用 `.prominent` 样式处理关键操作（Done / Submit）；**只指定一个主要操作，放在尾部**。
- 操作项分组**最多三组**；有文字标签的操作要和纯符号操作分开，避免看起来像一个合并控件。
- 标题：写得短，**控制在 15 字符以内**；**不要用 App 名当标题**。

**列表（分组内嵌列表）**（`lists-and-tables.md`）
- 优先用列表/表格承载文字——行格式最适合扫读。（`lists-and-tables.md › Best practices`）
- **iOS/iPadOS 的 grouped 样式用 header、footer 和额外间距来分隔数据组。**
- **选择反馈**：用于层级导航的表格要**持续高亮选中行**；用于列选项的表格通常在短暂高亮后打上勾。（同上）
- **行文本要短**，减少截断和换行。
- **必须截断时，中间的省略号有时比末尾省略号更好用**（保留开头和结尾）。
- 多列表格用**标题式大小写**的名词或短名词短语做列头，不加句末标点。
- **信息按钮（info button）只用来展示关于该行的更多信息，不做层级导航**；要下钻用**披露指示符（disclosure indicator）**。
- 行尾已经有控制件（如披露指示符）时，**不要再加 A–Z 索引**，两者会抢同一个位置。

**按钮**（`buttons.md`）
- **自定义按钮必须有按下状态**，否则会让人觉得没响应。
- **用样式（而不是尺寸）区分首选选项**；两个不同尺寸的按钮挨着会显得混乱。
- **一屏只放一到两个强调按钮**；强调按钮太多会增加认知负担。
- 按钮内容：**能用熟悉的图标就用**；需要文字时用短语、**以动词开头**、**标题式大小写**。
- 四种角色：**Normal / Primary（默认按钮）/ Cancel / Destructive**。
- **不要把 Primary 给破坏性操作**，哪怕它是最可能的选择——primary 的视觉显著性会让人不读就点。
- 破坏性操作用系统红。（同上）
- 需要等待时，**在按钮内显示活动指示器**，并可同时换标签（"Checkout" → "Checking out…"）。

**分段控件（Segmented control）**（`segmented-controls.md`）——**如果重构时想引入"任务/配置"的子切换，先看这段**
- 用途：让用户在一个对象/状态/视图上做**紧密相关**的选择；如果这些子视图是**完全独立的区块**，那就该用 tab bar，不是分段控件。（`segmented-controls.md › Mobile (iOS, iPadOS)`）
- **数量上限：宽界面 5~7 个，iPhone 上 ≤5 个。**（`segmented-controls.md › Best practices`）
- 各段**等宽**；图标与标题宽度也尽量一致；内容大小要相近（等宽下有的填满有的空着会很难看）。
- **不要在一个控件里混"动作段"和"选择段"**；**不要混用文字和图像**，优先全文字或全图像。
- 段标签用**名词或名词短语** + 标题式大小写；有文字标签的分段控件**不需要**介绍性文字。

**开关 / 复选框 / 单选**（`toggles.md`）
- ⚠️ **iOS 上 switch 只用在列表行里**；此时不需要标签，因为行内容已经提供了上下文。（`toggles.md › Mobile (iOS, iPadOS)`）
- **列表之外要用"行为像开关的按钮"，不要用 switch**；并且**不要给按钮式开关加解释用途的标签**。（同上）
- **状态的视觉差异必须明显**：加/去填充色、显示/隐藏背景形状、改变内部细节（勾或圆点）；**不要只靠颜色**。（`toggles.md › Best practices`）
- 开关颜色默认绿；必要时可改成 accent 色，但必须与"未着色外观"有足够对比。（同上）
- 需要表达设置**层级**时用复选框 + 缩进（不要用一堆 switch）。
- 互斥选项超过约 5 个 → 换成 pop-up button 之类，不要排一长串单选。

**数量上限速查（全部出自 HIG 原文）**

| 组件 | 上限 | 出处 |
| --- | --- | --- |
| Tab | 默认 ≤5 个 | `tab-bars.md` |
| 工具栏操作分组 | ≤3 组；主操作只 1 个且放尾部 | `toolbars.md` |
| 工具栏/窗口标题 | < 15 字符 | `toolbars.md` |
| 分段控件 | 宽界面 5~7，iPhone ≤5 | `segmented-controls.md` |
| 强调（prominent）按钮 | 每视图 1~2 个 | `buttons.md` |
| Alert 按钮 | ≤3 个；标题 ≤2 行；按钮标题 1~2 个词 | `alerts.md` |
| 单选按钮组 | 2~5 个 | `toggles.md` |
| 滚动边缘效果 | 每视图 1 个；分栏时各 1 个但高度必须一致 | `scroll-views.md` |
| 一次评审加载参考文件 | 8~12 篇，绝不整目录 | `SKILL.md` |

**图标**（`icons.md`、`sf-symbols.md`）
- 全部界面图标**尺寸、细节程度、描边粗细、视角必须一致**；视觉重量不同时可以用尺寸微调来"看起来一致"。
- **图标粗细要和相邻文字匹配。**
- 有必要时给图标加内边距做**视觉居中**（不对称图标按几何居中会显得偏）。
- **自定义图标必须用矢量（PDF/SVG）**，不要位图。
- **纯图标要有替代文本标签**（供屏幕阅读器朗读）。
- 文字只在与表达含义强相关时才放进图标，且要本地化。
- SF Symbols 规格（作为你手绘图标的目标）：**9 个字重**（ultralight→black，与 SF 字体字重一一对应，用于精确匹配相邻文字）、**3 个 scale**（small / medium 默认 / large，相对字体 cap height 定义）、**默认 outline，fill 变体用于表示选中**、rendering modes = monochrome / hierarchical / palette / multicolor。
- **不要在 app 图标、logo 或商标用途中使用 SF Symbols 或易混淆的相似图形。**

**空状态 / 错误 / 加载**（`writing.md`、`loading.md`、`feedback.md`）
- 空状态要**欢迎用户、教育用户、给出下一步**，并且**给一个按钮或链接**去做；空状态通常是暂时的，**不要放关键信息**。
- 错误消息**显示在离问题最近的地方**，**不责备用户**，**说清怎么修**。
  - 反例："That password is too short"；正例："Choose a password with at least 8 characters"。
  - **不要用"oops!""uh-oh"这类感叹**，很假。
- 文本字段要有 **hint/placeholder** 说明格式；错误显示在字段旁边并说明正确做法。（"Use only letters for your name" 好过 "Don't use numbers or symbols"；"Invalid name" 没有任何帮助。）
- 加载：**立刻有东西出现**（占位/骨架），**让人能继续操作**，**能确定进度就用确定进度**。（`SKILL.md › Lens 4`）

### 3.7 呈现方式选择树（"什么时候用模态、什么时候用气泡"）

这是 skill 里最能直接落地的一段。按下面的顺序问：

```text
这个交互需要读取或确认信息吗？
├─ 是，且是"关键、必须马上知道"的信息
│   └─ → Alert（模态、居中、最多 3 个按钮）
│        · 极少用；不要只为了告知；不要用于常见可撤销动作；不要在启动时弹
│        · 破坏性动作必须配一个标题就叫 "Cancel" 的取消按钮
│        · 默认按钮放尾部（横排）或顶部（竖排）；Cancel 放首部（横排）或底部（竖排）
│        · 破坏性样式只用于"用户并非故意选择"的破坏性动作
│        · 不要用 OK 当默认按钮，除非纯告知；用 "Erase"/"Delete"/"Clear" 这种具体动词
│        · iOS 上要提供多个选择 → 用 Action Sheet，不要用 Alert
├─ 是，但需要很多内容 / 是个独立的小任务
│   └─ → Sheet（一次只显示一个；支持 medium detent；带 grabber；支持下滑关闭；
│            有 Done 就必须有 Cancel 或 Back；绝不三个同时出现）
├─ 否，只是暴露少量信息或功能，且屏幕**宽**（iPad/桌面）
│   └─ → Popover（箭头指向来源；一次一个；上面不能再叠视图，除 alert 外；
│              不要用 popover 做警告——会被漏看或被误关）
└─ 否，且屏幕是**紧凑宽度**（iPhone 竖屏）
    └─ → ❌ 不要用 Popover；用 Sheet / Action Sheet，或就地内联展开
```

出处：`alerts.md`、`sheets.md`、`popovers.md`、`modality.md`。

**模态通用规则**（`modality.md`）
- **只在有明显收益时才用模态**；任务要**简单、短、单线**。
- **必须有一个明显的退出方式**；**一次只显示一个模态**（alert 可以盖在所有内容之上，但**永远不要同时显示两个 alert**）。
- 关闭可能丢失用户内容时，先确认。
- 模态要**有标题说明任务**。

**Sheet 细节**（`sheets.md`）
- iPhone 上考虑支持 **medium detent** 做渐进披露（medium ≈ 全高的**一半**）。
- **可调整大小的 sheet 要带 grabber**（它同时是 VoiceOver 用户调整高度的途径）。
- **支持下滑关闭**；有未保存改动时先弹 action sheet 确认。
- 单视图 sheet：**Cancel 在顶部工具条的起始边，Done 在结束边**。
- **只用 Done 会让人以为完成任务是唯一的退出方式**——所以 Done 必须配 Cancel 或 Back。

**Action Sheet vs Alert**（`alerts.md › Mobile (iOS, iPadOS)`）
- **对一个"用户主动发起的动作"提供多个选择 → 用 action sheet，不要用 alert。**
- Alert 适合"确认或取消一个有破坏性后果的动作"，但它**不能提供额外的选择**。

### 3.8 交互与反馈

- Alert 使用时机：**极少**；**不要只用来告知**；**不要用于常见且可撤销的动作**（即使有破坏性）；**不要在 App 启动时弹**。启动时发现问题（如无网络）更好的是显示缓存/占位数据 + 一个不打扰的说明标签。（`alerts.md › Best practices`）
- Alert 文案：标题简明说明处境（发生了什么、什么上下文、为什么）；**不要写"Error"或"Error 329347 occurred"**；**不要超过两行**。完整句子用**句式大小写 + 句末标点**；句子片段用**标题式大小写、不加句末标点**。补充文本只在真的增加价值时才有，用完整句子、句式大小写。（`alerts.md › Content`）
- **不要解释 alert 的按钮**（文案和按钮标题够清楚的话没必要）。（同上）
- 破坏性/不可逆动作：**给警告 + 一个 Cancel**；其余靠**撤销**覆盖。（`SKILL.md › Lens 4`）
- **反馈放在界面里，不要放在 alert 里。**（`SKILL.md › Lens 4`）
- 数据录入：**尽量从系统取、尽量给选项而不是让人打字、动态校验、绝不预填密码。**（`SKILL.md › Lens 4`）
- 动效要**有目的、短、可取消、在频繁交互上要罕见**。（`SKILL.md › Lens 3`）
- **不要让人等动画播完才能操作**，尤其是要重复经历的动画。（`motion.md › Providing feedback`）
- **App 里，频繁发生的 UI 交互一般不要加动效**——系统本身已经给了细微动画。（同上）
- 反馈动效要**跟随手势、符合预期**（从顶部滑下来的视图，就不能靠侧滑关掉）。（同上）
- **动效不能是传达重要信息的唯一方式**；配套用触觉或声音。（`motion.md › Best practices`）

### 3.9 文案

- **每个标签说清会发生什么**：写"Save changes"不写"Submit"；**一个动作在整条流程里保持同一个名字**（"Publish" 按钮产出 "Published"）。（`SKILL.md › Lens 5`）
- **大小写规则要选定并全局一致**：Apple 对按钮、菜单项、标题用**标题式大小写**，Material 用**句式大小写**——**但同一屏混用就是缺陷**。（`SKILL.md › Lens 5`、`writing.md › Best practices`）
  - 标题式大小写偏正式，句式大小写偏随意；**按 UI 元素类型各定一种，然后全 App 统一**。
- **按钮和链接标签几乎总是用动词**；宁可清楚也不要俏皮（"Send" 比 "Let's do it!" 好）。（`writing.md › Best practices`）
- **流程中的语言要一致**：用 "Get Started" 开头，用 "Continue"/"Next" 推进（选一个并坚持），用 "Done" 表示完成。（同上）
- **少用物主代词**："Favorites" 和 "Your Favorites" 一样清楚，前者更短；**完全避免用"我们"**——"We're having trouble loading this content" 不如 "Unable to load content"。（同上）
- 设备用词要对：触摸设备上不要写 "click"，写 "tap"。（同上）
- **错误信息**（见 3.6）。
- **空状态**（见 3.6）。
- **设置项的标签要"实用地"命名**；标签不够就加说明；**说明只描述"打开时会怎样"**，用户可以自行推断关掉时相反。（`writing.md › Best practices`）
- 要引导用户去某个设置，**给一个直接跳转的链接或按钮**，不要用文字描述位置。（同上）
- **不要把 App 名当标题**；**不要在 App 里到处放 logo**——"人们很少需要被提醒自己在用哪个 App"。（`toolbars.md`、`branding.md`）
- **品牌永远让位于内容**。（`branding.md`）
- 不要用"popover"这类实现术语写帮助文档，要说具体任务。（`popovers.md`）

### 3.10 设置页惯例（`settings.md`）

- **尽量减少设置项的数量**；设置太多会让体验变得难以接近、也不好找。
- **通用、不常改的设置** → 放在 App 自己的设置区。
- **任务相关的选项** → **留在它影响的那个界面里**（筛选、显隐、排序），放到独立设置区会把选项和上下文割裂。
- **不要复制系统级设置**（无障碍、滚动行为、认证方式等），也不要跟系统设置重复。
- **能自动探测的就不要问用户**（比如"当前是否深色模式"自己检测）。
- 默认设置要让**尽可能多的人**开箱即用。

### 3.11 动效（`motion.md`）

- 有目的地加，不要为了加而加。
- 简短精确的反馈动效比醒目动画更有效。
- **频繁交互一般不加动效**——系统本身已经给了细微动画。
- 让人能取消；**不要让人等动画播完才能操作**，尤其是要重复经历的动画。
- 反馈动效要**跟随手势、符合预期**（从顶部滑下来的视图，不能靠侧滑关掉）。
- 动效**不能是传达重要信息的唯一方式**；配套用触觉或声音。
- 减少动态效果打开时：**收紧弹簧减少回弹、动画直接跟手、不做 z 轴深度动画、x/y/z 位移换成淡入淡出、不做模糊的进出动画**。

### 3.12 加载、反馈与进度

**反馈强度要匹配**（`feedback.md`）
- **成功确认要稀缺**——"人通常预期自己的操作会成功，因此一般只需知道**失败**时的情况"。这条直接约束"每个操作都弹一个成功提示"的做法。
- 反馈方式要匹配信息重要性：状态信息宜**被动**显示供按需查看；可能丢数据的警告**必须打断**。
- 反馈必须**可访问**：同时用**颜色、文字、声音和触感**，让人静音、移开视线或使用屏幕阅读器时都能收到。
- 状态反馈要**整合进界面**，出现在它所描述的项目附近，人不需要离开当前上下文。
- 命令无法执行时**必须显示出来并帮助理解原因**。

**加载的时间带**（`loading.md`）
- 黄金标准原文：**"The best content-loading experience finishes before people become aware of it."**
- ⚠️ **HIG 没有给出精确的秒数分带**——唯一的措辞是 "**more than a moment or two**"（超过一两刻）就该用进度指示器。**不要自己发明 1 秒 / 10 秒这类阈值并声称来自 HIG。**
- 知道要多久 → **determinate**；不知道 → **indeterminate**。
- 任何等待都**先显示东西**：占位文字、图形或动画，内容可用时替换。（"若让人等到加载完成才看到任何内容，人会把空白理解为 App 有问题。"）
- 加载期间**让人能做别的事**。
- 加载**很长**时，给可看的有趣内容并尽量准确估计剩余时间。

**进度指示器**（`progress-indicators.md`）
- **能确定就用 determinate**：不确定型只说明"有进程在跑"，无法帮人估计时长；确定型能帮人决定等待、改期还是放弃。
- 推进要**准确且均匀**。反面示例（原文）："**Showing 90 percent completion in five seconds and the last 10 percent in 5 minutes**" 会让人怀疑 App 是否还在工作，甚至觉得受骗。
- **指示器必须持续运动**——静止会被联想为卡死。真的停了就必须给反馈说明问题与可采取的措施。
- 可以从 indeterminate **切换为** determinate；但**绝不要从圆形样式切换到条形样式**（形状尺寸不同，会破坏界面并让人困惑）。
- 说明文字要准确简洁；**避免 `loading`、`authenticating` 这类几乎不增加价值的模糊词**。
- **指示器放在一致的位置。**
- 能中止就提供 **Cancel**；中断有负面后果时同时提供 **Pause** 与 **Cancel**；取消会导致进度丢失时用 alert 确认。
- ⚠️ 在**按钮内**显示活动指示器（并可同时换标签，如 `Checkout` → `Checking out…`）比整屏阻塞更好。（`buttons.md › Mobile (iOS, iPadOS)`）
- iOS/iPadOS 的下拉刷新：**不要让人负责每次更新**，仍需周期性自动更新。（`progress-indicators.md › Refresh content controls`）

**搜索**（`searching.md`、`search-fields.md`）
- **若搜索重要，给它主位置**（一个搜索 tab，或有空间时放在底部的字段）。
- 让内容**通过单一位置**可搜索；有明显分区时可另提供局部搜索。
- **清楚显示当前搜索范围**（描述性占位文本、scope bar 或标题）。
- 提供建议：输入前显示最近搜索，输入中给预测建议；**显示搜索历史前考虑隐私**，若显示就必须提供清除方式。
- 搜索框组成：**Search 图标 + Clear 按钮 + 占位文本**。

### 3.13 ⚠️ 陷阱：这些数值 HIG 原文里**没有**，不要臆造

这一节很重要。skill 的 122 个文件是从 Apple 站点抓取的**忠实渲染**，但 Apple 的组件页**本身几乎不含尺寸数值**。实际的绝对数值只集中在少数几个文件里。整理如下，避免"看起来很有道理"地编造规范：

| 你可能想找的数值 | HIG 里到底有没有 |
| --- | --- |
| Tab 栏高度（49pt）、图标尺寸（25pt）、标签字号（10pt） | ❌ **不存在**。`tab-bars.md` 明确把图标尺寸指向 Apple Design Resources |
| iPhone 标准布局边距 16pt / iPad 20pt | ❌ **不在这批文件里**（`layout.md` 只讲 safe area 与 layout guide 的概念，不给数值）。**本文件 6.x 里出现的"16dp"是我的判断值，不是 HIG 规定** |
| 表格行高、section header 高度、列表内边距 | ❌ 不存在 |
| 圆角半径（alert / sheet / card / 分段控件） | ❌ **一处都没有**。`sheets.md` 只说 macOS 的 sheet 是 "a cardlike view with rounded corners" |
| 图标尺寸阶梯（16/20/24/28pt）、stroke weight 的 pt 值、图标网格/keyline | ❌ 不存在。`icons.md` 只有"尺寸/细节/描边/透视要一致"和"与相邻文字字重匹配" |
| 对比度数字（4.5:1 / 3:1 / 7:1） | ⚠️ **只在 `accessibility.md` 里**。`color.md` **完全没有**对比度规则（它只反复指向 accessibility.md） |
| Apple 系统色的 HEX / RGB | ❌ **完全没有**。`color.md` 的 Specifications 色板在下载版里是 `*image: colors unified ...*` 占位符，只剩色名与 API 名 |
| 不透明度 / alpha 百分比 | ❌ 不存在（唯一例外：clear 玻璃的 **35%** 压暗层，那在 `materials.md` / `liquid-glass.md`） |
| 动画时长 / 缓动曲线 | ❌ 不存在（`motion.md` 只有定性要求） |
| 填充 vs 线框该按尺寸还是按容器选 | ℹ️ 按**容器**：iOS 标签栏偏好 fill，工具栏取 outline（`sf-symbols.md`） |
| 精确的加载秒数分带 | ❌ 只有 "more than a moment or two" |
| sheet detent 的绝对 pt | ❌ 只有相对量：`medium ≈ 完全展开高度的一半` |
| 最小可点击文本尺寸 | ❌ 不存在（触控目标 44×44pt 是**控件**尺寸，不是文本尺寸） |

**已有的硬数值（可以放心引用）**：控件 44×44 / 28×28 pt（桌面 28×28 / 20×20）；按钮命中区 ≥44×44 pt（visionOS 60×60）；带边框元素周边 12pt、无边框按可见边缘 24pt；字号默认 17 / 最小 11（桌面 13 / 10）；文字可放大 ≥200%；对比度 4.5:1 / 3:1；完整 Dynamic Type 阶梯与 SF Pro 字距表；各种**数量上限**（见 3.6 末尾的表）；文案的**大小写与措辞规则**；`Cancel, Done, and Back` 不能同时出现；按钮标题 1–2 词、alert 标题 ≤2 行、alert ≤3 按钮。

**实践含义**：这份 skill 能给你的是**标尺**（尺寸下限、对比度、层级、选择树的判据）和**大量数量/文案约束**，**不是一套可以直接抄的视觉规格**。像素级的观感只能靠真机比对，不要假装有出处。

---

## 4. 与 Jetpack Compose 的落差表

> 说明：**"可行性"栏是我基于 Compose / Android 平台的判断，不是 skill 的内容。** skill 本身只覆盖 Flutter / React Native / Tauri / Electron / SwiftUI / UIKit / AppKit，没有 Compose 词表。

| # | skill 要求（出处） | iOS/SwiftUI 原生做法 | Compose 里的做法 | 可行性 |
| --- | --- | --- | --- | --- |
| 1 | 44×44pt 默认命中区（`accessibility.md`） | 系统保证 | `Modifier.minimumInteractiveComponentSize()`（Material3）或显式 `Modifier.size(48.dp)`；Material3 默认最小 48dp，已超过 44pt | ✅ **完全可行** |
| 2 | 12pt（有边框）/ 24pt（无边框）控件周边留白 | 手工 | `Arrangement.spacedBy(...)` / `PaddingValues` | ✅ 完全可行 |
| 3 | 对比度 4.5:1 / 3:1，从真实色值算（`accessibility.md`） | 系统色自带 | 自己写一个 WCAG 对比度计算（相对亮度公式 + 伽马）跑单元测试，把 token 组合全部断言一遍 | ✅ **可行且强烈建议做**（这是本工程最划算的一条投入） |
| 4 | 文字放大到 200%，层级不塌（`accessibility.md`） | Dynamic Type 自动 | `sp` 会自动跟随 `fontScale`，**但布局不会**：固定 `Modifier.height(46.dp)`、`maxLines = 1/2/3` + `Ellipsis`、`Box.size(52.dp)` 都会在大字号下裁切/截断。要做：改成 `heightIn(min = ...)`、关键文本去掉 `maxLines`、行高用 `heightIn` 而不是 `height`、用 `LocalDensity.current.fontScale` 分支调整布局 | ⚠️ **部分可行，需要逐屏改** |
| 5 | 完整 Dynamic Type 阶梯（`typography.md`） | `Font.TextStyle` | `MaterialTheme.typography` 里定义一套 `TextStyle`；**但 Compose 的 15 个 slot 与 iOS 11 个 text style 不是一一对应**，需要自己映射（本工程已经映射了一部分） | ⚠️ 部分可行，需人工映射 |
| 6 | SF Pro 字距表（`typography.md`） | 系统字体动态调整 | `TextStyle(letterSpacing = ...)`；**但本工程用 Inter，Inter 的原始字距与 SF Pro 不同，不能照抄** | ⚠️ **不能照抄，只能当量级参考** |
| 7 | 真背板模糊 20–40px（`liquid-glass.md`） | `.glassEffect()` / `UIVisualEffectView` | `Modifier.blur()` **只模糊自身内容，不模糊背后的东西**；真背板模糊需要 ①`dev.chrisbanes.haze`（`Modifier.hazeEffect`）②`RenderEffect.createBlurEffect`，**API 31+**，minSdk 26 的设备没有 ③`Dialog` 可以用 `Window.setBackgroundBlurRadius`（API 31+，只对窗口后面的内容生效） | ⚠️ **部分可行，且 skill 本身反对在安卓做** |
| 8 | 减少透明度 → 不透明兜底（`liquid-glass.md` 清单 5） | `UIAccessibility.isReduceTransparencyEnabled` | **Android 没有公开 API**。能做的：①提供降级分支但需要一个信号源 ②读 `AccessibilityManager` 的 `isHighTextContrastEnabled`（≈ 增强对比度）③用应用内开关——**但 `dark-mode.md` 禁止应用内外观开关，二者冲突** | ❌ **做不到（无系统信号）**，需选一个折中 |
| 9 | 减少动态效果（`accessibility.md`） | `UIAccessibility.isReduceMotionEnabled` | **Android 同样没有公开 API**（Google issue tracker 上仍有开放请求：#430201816 "Request for a public accessibility API for 'Reduce animations' system setting"）。可行近似：读 `Settings.Global.ANIMATOR_DURATION_SCALE`，为 `0f` 时视为关闭动画 | ⚠️ **可行但属于非公开约定**，要加 try/catch 与默认值 |
| 10 | 增强对比度变体（`color.md`） | 系统色自带第 3、4 套色值 | `AccessibilityManager.isHighTextContrastEnabled`（读 `Settings.Secure.HIGH_TEXT_CONTRAST_ENABLED`）可拿到信号，但要**自己准备第二套色板** | ⚠️ 部分可行，工作量大 |
| 11 | 大标题滚动折叠为标准标题（`toolbars.md`） | `UINavigationBar` + `prefersLargeTitles` | 需要手写：`TopAppBar` + `TopAppBarScrollBehavior`（M3，`enterAlwaysScrollBehavior` / `exitUntilCollapsedScrollBehavior`），或自己读 `LazyListState.layoutInfo` 插值字号与位置 | ✅ **可行，需自己实现** |
| 12 | 标准返回/关闭按钮，不加文字标签（`toolbars.md`） | 系统 `UIBarButtonItem` | `IconButton` + `Icons.AutoMirrored.Filled.ArrowBack`；**Android 上必须同时保留系统返回手势与返回键**（`BackHandler` / `NavHost` 集成） | ✅ 完全可行 |
| 13 | 边缘滑动返回（`gestures.md`） | 交互式 pop 手势 | **Android 系统级预测式返回手势已经提供**（左/右边缘向内滑）。不要自己再实现一套，否则会和系统手势打架 | ✅ **平台已提供，别重复造** |
| 14 | 列表行滑动操作（`gestures.md`） | `.swipeActions` | `SwipeToDismissBox`（Material3）或自绘 `AnchoredDraggable` | ✅ 可行 |
| 15 | Sheet + medium detent + grabber + 下滑关闭（`sheets.md`） | `presentationDetents([.medium,.large])` + `prefersGrabberVisible` + 系统手势 | `ModalBottomSheet`（M3）有 `SheetState`、`skipPartiallyExpanded`、`sheetGesturesEnabled`，**但没有 "detent" 语义，也没有 grabber**；需要自己加一个 36×5dp 的圆角条，并用 `AnchoredDraggable` 实现停在某一比例 | ⚠️ **部分可行**：默认弹法 + 自加 grabber 够用；要精确 medium detent 得自己写 |
| 16 | Popover（气泡），且**紧凑宽度禁用**（`popovers.md`） | `UIPopoverPresentationController`（iPhone 上自动降级为 sheet） | `Popup` / `DropdownMenu` 可做任意锚定气泡，**技术上完全能做**——但 skill 明确说手机上不该用。见第 5 节 | ⚠️ **技术可行，设计上不该做** |
| 17 | 一次只显示一个模态，alert 永不叠加（`modality.md`） | 系统保证 | Compose **允许同时叠多个 `Dialog`**，没有任何约束。只能靠状态机自律（把 `confirmStop`、`toast`、将来的 sheet 收敛到一个 sealed class 的"呈现槽"） | ⚠️ 靠自律，建议做成单一 state |
| 18 | Tab bar 用系统组件、自动拿到玻璃/标签/紧凑布局（`tab-bars.md`） | `TabView` | 必须自绘（本工程已自绘）。**代价**：文字标签、动态字号、选中态填充图标、无障碍角色、SafeArea 都要自己补 | ⚠️ 部分可行，已投入的部分不要推倒 |
| 19 | SF Symbols：9 字重 × 3 scale，outline/fill 双套（`sf-symbols.md`） | 系统库 | `ImageVector` 手绘（本工程已做）。**`ImageVector` 不能按字重切换**，要做到"骨架随字重变化"必须为每个字重各出一套 `ImageVector`，成本极高；outline/fill 双套是可行的（两套 `ImageVector`） | ⚠️ 字重维度基本放弃；**outline/fill 双套建议做** |
| 20 | 系统语义色自动适配深浅 + 增强对比（`color.md`） | `UIColor.label` 等 | Compose 需自己建 token 系统（本工程已建 `AcrylicTokens`）+ `isSystemInDarkTheme()`。增强对比那一层要自己加 | ✅ 部分已做 |
| 21 | 深色 base / elevated 背景双层（`dark-mode.md`） | 系统自动 | 需自己在 token 里补一组 `surfaceElevated` 并让 sheet/dialog 使用 | ✅ 可行 |
| 22 | App 图标分层 + dark/tinted 变体（`app-icons.md`） | Asset catalog 变体 | Android 自适应图标（`mipmap-anydpi-v26/ic_launcher.xml` 的 foreground/background + monochrome），Android 13+ 主题图标 | ✅ **平台原生机制，可行** |
| 23 | 文本字段 hint + 就近错误 + 正确键盘类型（`writing.md`、`entering-data.md`） | 系统控件 | `OutlinedTextField`/`BasicTextField` + `isError` + `supportingText` + `KeyboardOptions(keyboardType=...)` | ✅ 完全可行 |
| 24 | 键盘避让 / 安全区（`layout.md`） | 系统 | `Modifier.imePadding()` + `WindowInsets.safeDrawing`。**本工程 targetSdk 37，边到边是强制的**：`enableEdgeToEdge()` 已调、`adjustResize` 已开，所以 LazyColumn 里的输入框（Settings 的 3 个）问题不大；但 **`ConfigScreen` 的搜索框在 LazyColumn 之外的固定 Column 顶部，键盘弹出时不会被顶起** | ⚠️ 部分可行，缺一处必须补 |
| 25 | "不要在启动时弹 alert" / 启动体验（`launching.md`） | 系统 | 自己在首帧不弹任何 Dialog；把连接错误做成内联标签或占位数据 | ✅ 靠自律 |

### 4.1 Compose 侧的整体判断

- **能 100% 落地的**：所有数值类规则（尺寸、间距、对比度、字号、层级）、文案规则、呈现方式选择树、无障碍标签与角色。
- **能落地但要写代码的**：大标题折叠、sheet grabber、单一模态状态机、tab 标签与填充图标、IME/安全区。
- **做不到的**：真背板模糊（跨 API 26–37 一致）、减少透明度信号、"图标随字重变化"、系统级的增强对比度色板自动适配。
- **不该做的**：在紧凑宽度上做 popover；自己实现边缘返回手势；给每个卡片加玻璃。

---

## 5. 与本工程的冲突点（skill 主张 vs 现有路线）

这一节是写给你做决策用的，不是让你无条件服从 skill。

### 冲突 1：真模糊 —— **skill 站在你这一边，但理由不同**

- 你现在的做法（`Acrylic.kt` 注释）：*"不用模糊（Android 没有 CSS 的 backdrop-filter，真模糊在滚动列表上是掉帧主因），轻薄感来自细高光边 + 顶部 1dp 反光 + 恰当透明度"*。
- skill 的说法（`liquid-glass.md › React Native`）：*"On Android, blur is costly and inconsistent across versions, and the platform's own design language does not use glass. **Prefer an opaque or lightly translucent bar there rather than imitating iOS.**"*
- **裁定**：**维持现状，不要引入模糊。** skill 在"安卓不做玻璃"这一点上比你更激进——它建议的是"不透明或轻微半透明"，而你现在已经是"轻微半透明 + 高光 + 发丝描边"，比它推荐的还多一层质感。**你唯一需要补的是"减少透明度"降级分支的等价物**（因为拿不到系统信号，见下）。
- **但要把限制说清楚**：你的 `LightPanel = #C7FFFFFF`（78% 白）落在它给的 regular 填充区间（60–80%）里，`LightPanelStrong = #F7FFFFFF`（97%）已经接近不透明。**这是符合的。** 真正需要检查的是"玻璃用在了内容层"——见下面冲突 3。

### 冲突 2：App 内的浅色/深色开关 —— **skill 明确反对，但你这里应该保留**

- skill（`dark-mode.md › Best practices`）：*"**Avoid offering an app-specific appearance setting.** ... Worse, they may think your app is broken because it doesn't respond to their systemwide appearance choice."*
- 你的实现（`AppRoot.kt` L47–51）：`state.themeMode` 1=浅色 / 2=深色 / 其他=跟随系统。
- **裁定**：**保留，但把"跟随系统"设为默认并放在第一位。** 理由：`dark-mode.md` 的这条规则前提是"iOS 上系统外观是全局统一的"，而 Android 生态里 App 内主题开关是常见且被用户预期的（尤其这是一个长时间挂在后台的脚本控制台，用户可能想固定深色省电）。这是一个**明确的权衡取舍**，skill 自己也说了 *"Name the trade-off when a guideline collides with a business need, then recommend"*。
- **建议**：保留三选项，把"跟随系统"作为首项且是默认；不要改成二选一。

### 冲突 3：玻璃用在了内容层 —— **这条 skill 是对的，你应该改**

- skill（`materials.md`）：*"**Don't use Liquid Glass in the content layer.** ... it can result in unnecessary complexity and a confusing visual hierarchy. Instead, use standard materials for elements in the content layer, such as app backgrounds."*
- 现状：`AcrylicSurface` 是**默认组件**，首页的资源分组容器、状态卡、资源卡、空卡、Tab 栏、Toast、对话框、气泡**全都用它**。也就是说"玻璃"覆盖了内容层（分组列表容器）和功能层（Tab 栏、Toast、对话）两处。
- **裁定**：**这是本工程最实质的一条偏差。** 但注意：你的 `AcrylicSurface` 其实**不是 Liquid Glass**——它没有模糊，只是"半透明填充 + 高光 + 发丝描边"。所以在 skill 的术语里它更接近 iOS 的 **"标准材质（standard material）"** 用在内容层，**这是被允许的**。
- **真正要改的是语义混淆**：现在同一个组件既当"内容卡片"又当"浮动功能层"，只在 `strong`/`elevation` 两个参数上区分。建议拆成两类：
  - `ContentSurface`（内容层，**不允许**有 elevation，透明度可以更低）
  - `FunctionalSurface`（功能层：Tab 栏、Toast、对话、气泡，可以 strong + elevation）
  - 这样"玻璃只出现在浮动功能层"这条纪律就能被代码强制，而不是靠自觉。

### 冲突 4：气泡确认条在手机上不成立 —— **skill 明确反对，你应该改**

- skill（`popovers.md › Mobile (iOS, iPadOS)`）：*"**Avoid displaying popovers in compact views.** ... Reserve popovers for wide views; for compact views, use all available screen space by presenting information in a full-screen modal view like a sheet instead."* 以及 *"**Avoid using a popover to show a warning.**"*
- 现状：`Overlays.kt › BubbleConfirmPopup(anchor…)` —— 一个用 `androidx.compose.ui.window.Popup` 锚定在控件下方（固定宽 208dp）的自定义气泡，用在竖屏手机上（用 `LocalConfiguration.current.screenWidthDp` 做横向收敛）。调用方是 `TasksScreen` 的闪电按钮。
- **裁定**：**skill 是对的。** 你原本的意图（"点错了也无所谓的轻动作"不打断、不锁屏）是合理的，但 Apple 对这个意图给出的组件**不是 popover，而是 action sheet**——底部弹出、带 Cancel、点外部即散、不锁屏。
- **建议**：把 `BubbleConfirmPopup` 的**语义保留**（不打断、点外部取消、轻动作），**形态改成底部 action sheet**。`action-sheets.md` 给了具体规格：**Cancel 放底部**、**破坏性选项放顶部**、标题尽量单行、**避免 action sheet 滚动**。这样既满足了"不打断"，也满足了紧凑宽度规则，而且和 `ModalConfirmDialog`（Alert）的分工变得更清晰：轻动作 → 底部 action sheet，重动作 → 居中 alert。

### 冲突 5：竖屏锁定 —— **skill 建议支持横屏，但你这里应该坚持竖屏**

- skill（`layout.md › Phone (iOS)`）：*"**Aim to support both portrait and landscape orientations.**"*
- 现状：`android:screenOrientation="portrait"`。
- **裁定**：**坚持竖屏。** 这是"业务需求与规范冲突"的典型案例，skill 的立场是"说明取舍，然后给建议"。理由：这是一个控制台类工具，竖屏单手操作是主要场景；而且 `configChanges` 已经声明了 `orientation|screenSize`，改横屏还要重新验证所有固定高度布局。**建议在代码注释或 README 里写明这是有意为之，并记录被放弃的东西**（横屏下 5 个 tab 的空间会更宽裕）。

### 冲突 6：Tab 栏无文字标签 —— **skill 明确反对，你应该改**

- skill（`tab-bars.md`）：*"**Include tab labels to help with navigation.** ... Use single words whenever possible."* 以及 *"**Prefer filled symbols or icons for consistency with the platform.**"*
- 现状：`AcrylicTabBar.kt` 的注释写着"纯图标，无文字（按你的要求）"，`contentDescription = label` 只对屏幕阅读器可见。
- **裁定**：**skill 是对的，建议改。** 纯图标 tab 在 5 个不同语义的区块（主页/任务/配置/统计/设置）上，认知成本很高——`Chart`/`Sliders`/`Checklist` 三个图标本身就容易混。加了标签之后，即使文字只有 11–12sp，识别速度也会显著提升。**这是一个"用户明确要求"与"skill 主张"的冲突，需要你确认优先级。**

### 冲突 7：子页面盖住 Tab 栏 —— **skill 认为这是缺陷**

- skill（`tab-bars.md`）：*"**Make sure the tab bar is visible when people navigate to different sections of your app.** If you hide the tab bar, people can forget which area of the app they're in. The exception is when a modal view covers the tab bar, because a modal is temporary and self-contained."*
- 现状：`AppRoot.kt` 里 `Route.TaskConfig` 和 `Route.Logs` 是"全屏压栈，盖住 Tab 栏"。
- **裁定**：**skill 是对的，但优先级可以低。** 因为它们是**压栈**（push）而非**模态**，skill 的例外条款不适用，所以严格来说是缺陷。但考虑到这两个页面是"深入一层"的独立任务，且都有明确的返回按钮，实际影响小于冲突 6。

---

## 6. 针对 AzurPilot Mobile 的差距清单与改造优先级

> 依据：`ui/theme/*.kt`、`ui/AppRoot.kt`、`ui/ScreenInsets.kt`、`ui/components/*`、`ui/screens/HomeScreen.kt`，以及 `app/build.gradle.kts` / `AndroidManifest.xml`。
> **本文件没有修改工程的任何文件。**

### 6.1 已经符合的（**重构时不要动**）

| 项 | 现状 | skill 依据 |
| --- | --- | --- |
| 不用投影做层级 | `AcrylicSurface` 默认 `elevation = 0.dp`，注释明确写了"iOS 的卡片几乎不用投影" | `layout.md › Visual hierarchy`（层级靠空间、尺寸、字重） |
| 深浅两套独立设计的色板 | `LightAcrylic` / `DarkAcrylic` 两套完整 token，深色不是反色；资源色 light/dark 分开给 | `dark-mode.md › Dark Mode colors` |
| 语义 token 收敛 | 组件层不允许硬编码 hex，全部走 `AcrylicTokens` | `color.md › System colors`（不要硬编码） |
| 跟随系统深色 | `isSystemInDarkTheme()` 是默认分支 | `dark-mode.md` |
| 分组内嵌列表 | 首页资源 = **一个分组容器 + 内缩发丝分隔线**，不是一叠独立卡片 | `lists-and-tables.md › Style`（grouped 用 header/footer/间距分组） |
| 分隔线颜色 | `LightDivider = #5E3C3C43`（iOS separator = 3C3C43 @ 37%），且用**发丝**宽度 | `color.md`（separator 语义色） |
| 滚动边缘效果 | `AppRoot.kt` 底部有一层 `Brush.verticalGradient` 渐隐幕（注释说明"没有这层，Tab 栏会透出下面的文字，非常脏"） | `liquid-glass.md › Review checklist 6`（内容与栏相接处淡出，而不是硬碰硬）**已符合** |
| 无涟漪 | 所有可点区域都传 `indication = null` | 与 iOS 观感一致；`buttons.md` 只要求"有按下状态" |
| 无 Snackbar | 自己写了顶部 Toast | 避免 Material 默认观感 |
| Tab 数量 | 5 个 | `tab-bars.md`（自定义时默认不超过 5 个）**已符合** |
| Tab 无障碍角色 | `Modifier.selectable(role = Role.Tab)` + `selectableGroup()`，注释说明"屏幕阅读器不会把 5 个 Tab 读成 5 个普通按钮" | `accessibility.md`（正确的语义与朗读） |
| Tab 命中区 | `Modifier.size(52.dp)` | `accessibility.md`（≥ 44×44pt）**已符合** |
| 数字等宽 | `fontFeatureSettings = "tnum"`，避免 30s 刷新时抖动 | `typography.md`（层级与稳定性工艺） |
| 骨架占位屏 | `AcrylicSurfacePlaceholder` 在加载时立刻出现 | `SKILL.md › Lens 4`（加载时立刻有东西出现） |
| 空状态 | `EmptyCard` 有标题 + 详情，并给出下一步（"请到「设置」检查服务器地址，或点右上角刷新重试"） | `writing.md`（空状态给下一步） |
| 警告用系统色 + 图标 | 危险对话显示 `AppIcons.Warning` 且用 `t.danger` 着色 | `accessibility.md`（不靠颜色单独传达）+ `alerts.md`（谨慎用警告符号） |
| Alert 按钮布局 | Cancel 在左、确认在右，危险时确认按钮染红 | `alerts.md › Buttons`（Cancel 在首部，破坏性用红）**已符合** |
| 自定义矢量图标 | `AppIcons` 用 `ImageVector` 手绘 | `icons.md`（自定义图标必须矢量） |
| 危险动作不可点遮罩确认 | 注释明确写了"危险动作必须明确点到按钮" | `alerts.md`（破坏性动作要给清晰的取消路径） |
| 边到边 | `MainActivity.kt:13` 调了 `enableEdgeToEdge()` | Android 平台要求（targetSdk 37 强制） |
| RTL 就绪 | `supportsRtl="true"`，布局全用 start/end 而非 left/right | `layout.md › Adaptability`（语言环境适配） |
| 代码卫生（排版层） | **`sp` 全模块 24 处 100% 收敛在 `Type.kt`**，组件层零硬编码字号 | 这是重构时最该保护的一块，别破坏它 |
| 数字等宽 | `tnum` 用于 3 个 Numeral 样式 | iOS 数字排版惯例 |
| 按压态用 `indication = null` 去涟漪 13 处 | 与 iOS 观感一致 | `buttons.md` 只要求"有按下状态"，不要求涟漪 |

> ⚠️ **快照说明**：本节依据的是 2026-09-11 10:33 的代码快照。工程当时正在被并发修改——`Skeletons.kt` 是新建文件（201 行），`HomeScreen / TasksScreen / AppRoot / Overlays / AppViewModel` 都在同一分钟内被改写。**动手前请重新确认这几个文件**。已知在此期间发生的变化：`BubbleConfirm` 已重写为 `BubbleConfirmPopup(anchor…)`；`AcrylicTokens.hairline` 已改为 `divider`；`ConfirmStopBar` 已删除改用 `ModalConfirmDialog`。

### 6.2 P0 — 无障碍硬门槛（不改就是 Critical）

| # | 问题 | 位置（快照行号） | skill 依据 | 建议改法 |
| --- | --- | --- | --- | --- |
| P0-1 | **Toast 定时自动消失，无延长机制、无手动关闭**。实测 `delay(4200ms)`（Error）/ `delay(2600ms)`（其它）；KDoc 写的是"3 秒"，**注释与代码不一致** | `Overlays.kt:84-88`（`ToastBar`） | `accessibility.md › Cognitive`："**Minimize use of time-boxed interface elements.** ... Prefer dismissing views with an explicit action." | ① 时长基线改成 `AccessibilityManager.getRecommendedTimeoutMillis(base, FLAG_CONTENT_CONTROLS)`——**这是安卓上这条规则的标准答案，也是唯一有系统依据的做法**；② 给横幅加可点关闭；③ **错误类提示不要自动消失**（错误正是"需要更长时间处理"的信息） |
| P0-2 | **Toast/日志的更新不会被屏幕阅读器播报**：全工程 `semantics` / `liveRegion` / `stateDescription` / `heading()` **0 次引用** | 全工程 | `accessibility.md › Vision`（要能被感知到）；`feedback.md`（"反馈必须可访问"） | Toast 容器加 `Modifier.semantics { liveRegion = LiveRegionMode.Polite }`（错误用 `Assertive`）；`SectionTitle` 与页面大标题加 `semantics { heading() }`；`ConfigScreen` 的分组展开/折叠加 `expand`/`collapse` + `stateDescription` |
| P0-3 | **一批自定义点击目标低于 44pt/48dp**：`ChoiceRow` 药丸 ≈ **31dp**、`ChipButton` ≈ **31dp**、`AutoScrollChip` ≈ **29dp**、`BubbleButton` ≈ **34dp**、`LogsScreen` 清空 `IconButton` **40dp**、`GroupHeader` 的 chevron 盒 **16dp** | `SettingsScreen:342,348`、`ConfigScreen:347,354`、`LogsScreen:245,251`、`Overlays:373,380`、`LogsScreen:99`、`ConfigScreen:248` | `accessibility.md › Mobility`（默认 44×44pt，最小 28×28pt）；`buttons.md`（≥44×44pt） | 用 `Modifier.minimumInteractiveComponentSize()`（该 API 全工程 **0 次使用**）或把可点区域撑到 44dp 而视觉尺寸保持不变。29dp 的 chip 距离最小值 28dp 只差 1dp，**这是全工程最该先修的一处** |
| P0-4 | **对比度实证**：`LightTextTertiary #8E8E93` 在浅色面板（`0xC7FFFFFF` 叠在 `#F2F2F7` 上）≈ **3.0:1**，却被用于日志 PIPE 分隔符、`NumeralSmall` 次要值、Tab 未选中图标 | `Color.kt:24`、`AcrylicTabBar.kt:86`、`LogsScreen`、`ResourceCard` 的 ageText | `accessibility.md › Vision`：≤17pt 需 **4.5:1**；图标 3:1 属临界 | 写一个对比度断言测试（WCAG 相对亮度）把 token 两两组合跑一遍。文字用途的 tertiary 需要提到 ≈ `#6E6E73`。**深色反而更安全**：`#8E8E93` on `#0A0A0C` ≈ 6.5:1 |
| P0-5 | **状态栏图标明暗不跟随应用内主题**：窗口属性由静态 XML 决定（`values/themes.xml` 的 `windowLightStatusBar=true` / `values-night` 为 false），而 `AppRoot` 用 `state.themeMode` 覆写 Compose 主题。**系统深色 + 应用内选"浅色" ⇒ 浅底 + 白色状态栏图标（看不见）**。加剧因素：`configChanges` 含 `uiMode`，系统切换深浅色时 Activity 不重建，XML 属性不会重新解析 | `AppRoot.kt:47-51`、`AndroidManifest.xml`、`res/values*/themes.xml` | `layout.md › Guides and safe areas`（要尊重系统特性）；`dark-mode.md`（两套外观都要能用） | 用 `WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark`，并在 `themeMode` 变化时重新应用 |
| P0-6 | **表单没有 IME 避让**：全工程 `imePadding()` **0 次**、`WindowInsets.safeDrawing` **0 次**（inset 全靠手工 `statusBars`/`navigationBars` 换算）。`ConfigScreen` 的搜索框位于 LazyColumn **之外**的固定 Column 顶部，键盘弹出时不会被顶起 | 全工程；重点 `AppRoot.kt:56-65`、`ConfigScreen.kt:88-93` | `layout.md › Guides and safe areas` | 至少给 `ConfigScreen` 的搜索框所在 Column 加 `Modifier.imePadding()`；更彻底的做法是把 `ScreenInsets` 扩展为从 `WindowInsets.safeDrawing` 派生 |
| P0-7 | **完全没有触觉反馈**：全工程 haptic 相关引用 **0 次** | 全工程 | `feedback.md`："反馈必须**可访问**：同时用颜色、文字、声音和触感"；`accessibility.md › Hearing`（音频线索要配触觉） | 起停成功/失败、危险确认、Toast 出现时加 `HapticFeedback`。这是低成本高感知的一条 |
| ~~P0-5（旧）~~ | ~~多处 `contentDescription = null` 需确认~~ | — | — | ✅ **已核实：全部是装饰性图标，写 `null` 是正确的**（`ConfigScreen:198/246/312`、`SettingsScreen:282/303`、`TaskConfigScreen:286`、`Overlays:204`）。**真正缺的是上一条 P0-2 的 semantics**，不是这个 |

### 6.3 P1 — 导航与身份识别（用户最直观能感知的）

| # | 问题 | 位置（快照行号） | skill 依据 | 建议改法 |
| --- | --- | --- | --- | --- |
| P1-1 | **Tab 栏没有文字标签**。5 个标签文字（"主页/任务/配置/统计/设置"）**只作为 `contentDescription` 存在**，视觉上仍是纯图标 | `AcrylicTabBar.kt:116,123-129` | `tab-bars.md`："**Include tab labels to help with navigation.** ... Use single words whenever possible." | 每个 tab 在图标下加一行 11sp 标签（`Caption 2`，正好是 HIG 最小值）。**连锁调整**：现在高度是 `52dp(Item) + 5×2dp = 62dp`，与 `AppRoot.TAB_BAR_HEIGHT = 62.dp` **手工耦合**——加标签后两处都要改，建议顺手抽成共享常量。另外 HIG 说"紧凑视图里图标在标签上方"，符合你的竖屏场景 |
| P1-2 | **5 个 Tab 页里有 3 个完全没有页面标题**。`headlineMedium`（"大标题"）只用在 `SettingsScreen:76`（设置）与 `ConfigScreen:83`（配置）两处；`HomeScreen` / `TasksScreen` / `StatsScreen` 直接以 `StatusCard` 面板开头。加上 P1-1（tab 无标签），**用户在主页/任务/统计页没有任何文字能确认自己在哪** | `HomeScreen.kt`（无标题）、`TasksScreen.kt`、`StatsScreen.kt`、`SettingsScreen.kt:76`、`ConfigScreen.kt:83` | `toolbars.md › Phone (iOS)`："**Use a large title to help people stay oriented as they navigate and scroll.** By default, a large title transitions to a standard title as people begin scrolling the content, and transitions back to large when people scroll to the top"；`designing-for-ios.md`（当前位置始终可见） | 给 5 个页面各加一个大标题（"主页/任务/配置/统计/设置"），并实现滚动折叠。**注意 HomeScreen 的 StatusCard 是吸顶不滚动的**，标题需要放在它上面还是下面要先定；`toolbars.md` 那句话可以直接当验收标准：滚下去变小、滚回顶部变大 |
| P1-3 | **大标题字号与字距都不对**：`headlineMedium` 是 28sp **Bold** + `letterSpacing = -0.7sp`。HIG 里 28pt 是 **Title 1**（基准 Regular，强调才 Bold），Large Title 是 **34pt**；而且 **28pt 的字距应该是 +0.38pt（正值）**，不是 −0.7——**符号是反的** | `Type.kt:83-88` | `typography.md › Specifications`（Large 档：Large Title 34 / Title 1 28；字距表：28pt → **+0.38pt**，17pt → −0.43pt） | 二选一：① 真做 Large Title → 34sp，基准 Regular，`letterSpacing ≈ +0.40sp`；② 保持 28sp 就明确它是 Title 1，字号 28sp / 字距 ≈ +0.38sp。**无论选哪个，先把 −0.7sp 改掉**——这是全工程最明显的一处排版错误，会让大标题看起来被挤扁。⚠️ 用的是 Inter 不是 SF Pro，Inter 原始字距更紧，**落地时以真机截图对齐，不要盲抄数值**（把表当量级参考） |
| P1-4 | **气泡确认条用在紧凑宽度上**。已重写为 `BubbleConfirmPopup(anchor…)`，用 `Popup(alignment = TopStart, offset = IntOffset(...), PopupProperties(focusable = true))`，固定宽 208dp、锚在被点控件下方 | `Overlays.kt:296-361`，调用方 `TasksScreen.kt:170-176`（锚点来自闪电按钮的 `onGloballyPositioned`） | `popovers.md › Mobile (iOS, iPadOS)`："**Avoid displaying popovers in compact views.** ... for compact views, use all available screen space by presenting information in a full-screen modal view like a sheet instead."；以及 `Show one popover at a time.` | **语义保留，形态换成底部 action sheet。** 你的意图（轻动作、不打断、点外部即散）是对的，但 Apple 对"用户主动发起的动作 + 多个选择"给的组件是 **action sheet，不是 popover**。`action-sheets.md` 的具体规格：**Cancel 放底部**、**破坏性选项放顶部**、标题尽量单行、**避免 action sheet 滚动**。改完之后和 `ModalConfirmDialog`（alert，居中）的分工也更干净：轻动作→底部 sheet，重动作→居中 alert |
| P1-5 | **子页面压栈时盖住 Tab 栏**：`Route.TaskConfig` / `Route.Logs` 是整屏替换，`AcrylicTabBar` 不在子页渲染 | `AppRoot.kt:72-90` | `tab-bars.md`："**Make sure the tab bar is visible when people navigate to different sections of your app.** ... The exception is when a **modal** view covers the tab bar" | 让子页保留 Tab 栏，或明确把它们定义为模态并补 grabber + 下滑关闭。**优先级可以低于 P1-1/P1-2**——这两个页面都有明确的返回按钮和侧滑返回，实际影响较小 |
| P1-6 | **Tab 切换与路由跳转完全没有动效**：`when(tab)` 直接换 composition；子页瞬时出现/消失，**没有 iOS 那种横向推入/推出** | `AppRoot.kt:92-136` | `motion.md › Providing feedback`（动效要跟随预期：iOS 的层级导航就是横向推入）；`designing-for-ios.md`（familiarity） | 用 `AnimatedContent`（子页横向滑入 + 旧页左移）或至少 `Crossfade`。**注意**：`AnimatedContent` / `Crossfade` / `updateTransition` / `animateContentSize` 在全工程 **0 次引用**，所以这是新增能力而不是改参数 |
| P1-7 | **Tab 按钮的选中态只有换色 + 放大 1.08，没有 outline/fill 双套图标**（代码注释明确写了"不做填充/描边双套"） | `AcrylicTabBar.kt:34,85-93,114-119` | `tab-bars.md`："**Prefer filled symbols or icons** for consistency with the platform."；`sf-symbols.md`（"iOS 标签栏偏好 fill，工具栏取 outline"——**变体由容器决定**） | 为 5 个 Tab 各画一个 fill 版 `ImageVector`，选中时切换。这也正是 iOS 26 浮动胶囊 Tab 栏的实际做法。**顺带**：这也是全套图标体系里"outline/fill 双套"唯一值得投入的地方（字重维度可以放弃，见 6.5） |
| P1-8 | **没有"减少透明度 / 增强对比度"降级**，只有一套色板 | `Theme.kt`（`LightAcrylic`/`DarkAcrylic` 两套，无第三套） | `liquid-glass.md › Review checklist 5`（减少透明度→不透明兜底；增强对比度→更实填充与描边）；`color.md › Best practices`（自定义色要提供 increased contrast 变体） | 增强对比度**有**系统信号：`AccessibilityManager.isHighTextContrastEnabled` → 提高 `panelBorder` alpha（现 7%/12%）、提高文字色对比。减少透明度**没有**公开信号——用增强对比度作代理（skill 给 Flutter 的建议原文就是这个：*"Flutter exposes no reduce-transparency signal, so treat high contrast as the cue to fall back to opaque surfaces"*，安卓同理） |

### 6.4 P2 — 手艺与一致性

| # | 问题 | 位置（快照行号） | skill 依据 / 建议 |
| --- | --- | --- | --- |
| P2-1 | **形状 / 间距 / elevation / 动效四类都完全没有 token 对象**。唯一的结构化令牌是 `ScreenInsets`，加上 `AcrylicSurface` 的默认参数（14dp / 0.5dp / 0dp）在事实上扮演令牌 | `ScreenInsets.kt`、`Acrylic.kt:73-76` | 这是本工程**最高杠杆的一条**。建议抽 4 个对象：`Spacing` / `Radius` / `Elevation` / `Motion`。skill 的 `SKILL.md › Design improvement mode` 第 2 步就是"先规划一个紧凑的 token 系统再动布局" |
| P2-2 | **「15 vs 16」是全局最明显的对不齐**：行内边距用 `16.dp`（`ROW_PADDING`，`ResourceCard.kt:39`，另在 StatusCard/Tasks/Config/TaskConfig/Settings 共 8 处手写 16），但卡片内容边距用 `15.dp`（Settings 5 张卡、Stats 5 张卡、Toast）。**相差 1dp** | 见上 | `layout.md › Visual hierarchy`："**Align components with one another to make them easier to scan**"。统一到 16dp |
| P2-3 | **分隔线内缩不统一**：`Hairline` 默认 16dp，但 `StatsScreen:237/355` 与 `SettingsScreen:141` 直接调 `HorizontalDivider` 用 **15dp**，弹窗用 **0dp** | 见上 | 同上。建议把 inset 收敛成 3 档（`full=0` / `row=16` / `nested=34`）并强制走 `Hairline` |
| P2-4 | **深色分隔线过重**：`DarkDivider = 0x99545458`（@60%），约为 iOS 深色分隔线（约 29%）的 **2 倍重** | `Color.kt:40` | `color.md`（separator 是单一一档语义色）。建议降到 @30% 左右 |
| P2-5 | **圆角用了 11 个值 + CircleShape**：28 / 20 / 16 / 14 / 12 / 10 / 9 / 8 / 6 / 5 / 4 / 3 dp。尤其 `14.dp`（`AcrylicSurface` 默认）在 Stats/Settings/Skeletons 里被**重复显式传了 12 次**（等于没默认） | 见 6.4 说明 | 收敛成 3~4 档（如 `small 8 / medium 12 / large 16 / sheet 20`）。另外 `toolbars.md` 有一条**同心圆角**规则：bar 内自定义组件的圆角要与 bar 的圆角同心 |
| P2-6 | **一屏六种 elevation**：10（Toast）/ 12（FAB）/ 14（气泡）/ 16（Tab 栏）/ 24（弹窗）dp，全是 M3 `Modifier.shadow`，阴影色**写死在主题里**（`Acrylic.kt:87-88` 的 `0x0A000000` / `0x14000000`，调用方无法改）；FAB 还用**蓝色投影**（`PilotBlue@0.55f/0.30f`，`StartStopFab.kt:216-217`） | 见上 | iOS 的浮动功能层靠**材质 + 发丝描边**分层，不靠 Material 投影；彩色投影在 iOS 里没有对应物。建议下调并用描边/高光补偿。这是"看起来还像 Material"的主要残留 |
| P2-7 | **功能层与内容层共用同一个组件**：`AcrylicSurface` 同时用于内容卡片、Tab 栏、Toast、弹窗、气泡 | `Acrylic.kt:71-109` | 拆成 `ContentSurface`（内容层，**不允许** elevation）与 `FunctionalSurface`（功能层：Tab 栏/Toast/弹窗/气泡），用类型强制"玻璃只在功能层"（见第 5 节冲突 3） |
| P2-8 | **唯一一段非 Inter 文本**：`Overlays.kt:385` 的 `BubbleButton` 用 `MaterialTheme.typography.labelLarge`，而 `AppTypography` **只覆写了 15 个槽里的 9 个**，`labelLarge` 落到 M3 默认 → **Roboto 14sp Medium**。也就是气泡确认框的按钮文字字体和全 app 都不一样 | `Type.kt:81-141`、`Overlays.kt:385` | `typography.md › Conveying hierarchy`（字体家族越少越好）。**把其余 6 个槽也补齐**（`displayLarge/Medium/Small`、`headlineLarge`、`headlineSmall`、`labelLarge`），或至少补 `labelLarge` |
| P2-9 | **所有 TextStyle 的 `lineHeight` 都是 Unspecified**（只有 `LogLineStyle` 声明了 17sp） | `Type.kt` | `typography.md` 的 HIG 阶梯是成对的"字号/行高"（如 Body 17/**22**、Caption 1 12/**16**）。建议按 HIG 补齐，尤其是多行文本（空态详情、关于段落、Setup 说明） |
| P2-10 | **两套骨架屏并存**：新 `Skeletons.kt`（1400ms 线性扫光，Home/Tasks 在用）与旧 `AcrylicSurfacePlaceholder`（900ms 整体 alpha 呼吸，Stats 4 处 / Config 1 处 / TaskConfig 1 处还在用） | `EmptyCard.kt:73-82` vs `Skeletons.kt:55-64` | 统一到扫光。旧的整体呼吸在浅色背景上像故障闪烁（`Skeletons.kt:43` 的注释自己也这么说） |
| P2-11 | **弹窗入场实际只淡入，没有缩放（bug）**：`ModalConfirmDialog` 的 `scale` 变量 0.94→1（`tween(170)`）被施加为 `Modifier.alpha(scale)` 而不是 `graphicsLayer{ scaleX/scaleY }`，KDoc 却写"从 0.94 放大到 1" | `Overlays.kt:153-162,190` | 改成 `graphicsLayer` 就能同时拿到缩放；顺带核对 KDoc |
| P2-12 | **两套交互语言并存**：13 处自定义可点区用了 `indication = null` 去涟漪（对），但**保留下来的 M3 组件仍有 ripple**——`OutlinedTextField` ×4、`TextField` ×1、`Switch` ×1、`AlertDialog` ×2、`TextButton` ×3、`IconButton` ×6 | `SettingsScreen`、`TaskConfigScreen:327,373`、`ConfigScreen` | `buttons.md`（按钮要有按下状态，但形态要一致）。这也是"整体仍偏 Material"的第二大来源 |
| P2-13 | **按压反馈覆盖不全**：只有 `TasksScreen` 的行有按压底色（`background(t.track)`）。`SettingsScreen.NavRow`、`ConfigScreen` 的行与分组头、`LogsScreen` 的 chip、`ChoiceRow` 药丸、`BubbleButton`、`DialogAction` **完全没有按压态** | 见上 | `buttons.md`："**Always include a press state for a custom button.** Without a press state, a button can feel unresponsive" |
| P2-14 | **M3 ColorScheme 只填了 7 个槽**（primary/onPrimary/background/onBackground/surface/onSurface/error），其余（`secondary`/`tertiary`/`outline`/`outlineVariant`/`surfaceVariant`/`onSurfaceVariant`…）**全是 M3 默认值** → 上面那些 M3 组件的描边、标签、开关配色不来自亚克力令牌 | `Theme.kt:107-127` | 补齐 ColorScheme，至少让存活的 M3 组件（TextField / Switch / AlertDialog / TextButton）落到你的 token 上 |
| P2-15 | **对话框圆角 20dp**（其余面板 14/16dp） | `Overlays.kt:191` | 收敛进 P2-5 的圆角刻度。⚠️ 关于"iOS alert 圆角是 14pt"——**这不在本次读的 HIG 文件里，是我的判断**，请自行确认后再定 |
| P2-16 | **日志行 11sp 正好压在最小字号线上**，行高 17sp（1.55）远松于 HIG Caption 2 的 1.18 | `Type.kt:149-155` | 11sp 合规（= 最小 11pt），但**不要再小**。日志是长文可读性敏感区，建议加字号切换（小/中/大）；行距是终端排版习惯，可保留但要说明理由 |
| P2-17 | **图表没有坐标轴、网格、刻度、标签、交互**（手写 Canvas 折线，按自身 min/max 归一化）。两条量级不同的曲线视觉上不可比，唯一线索是 "min ~ max" 文字 | `StatsScreen.kt:510-571` | `charting-data.md` / `charts.md` 有规则可查（本次未展开）。**如果重构要动统计页，建议单独加载这两篇做一次专项评审** |
| P2-18 | **没有搜索/过滤，除了 Config 页的过滤框**；`LogsScreen` 几百行日志只能滚动看 | `LogsScreen.kt` | `searching.md`："若搜索重要，给它**主位置**"；`search-fields.md`（Search 图标 + Clear 按钮 + 占位文本）。日志页加一个关键字过滤是明显的收益点 |
| P2-19 | **残留/重复代码**：`Modifier.acrylicDivider`（零调用）、`ScaffoldSpacer`（零调用）、`NumeralLarge/Medium/Compact`（零引用）、`ConfigScreen.RowDivider`（零调用）、`StatsSectionSkeleton` / `ConfigTreeSkeleton`（零调用）、`SettingsScreen:350` 的 `Spacer(width 0.dp)`、`StatsScreen.kt:16-17` 重复 import | 见上 | 重构前先清理，否则会把死代码一起 token 化 |
| P2-20 | **`headlineMedium` 的 "Large Title" 语义与实现不符**（见 P1-3）；`StatusCard` 的实例名用 `titleMedium`(14sp) 而 HIG Headline 是 17pt，**偏小一档**；`sp` 阶梯里缺 HIG 的 Subhead 15（被 `bodyMedium` 占用） | `Type.kt`、`StatusCard.kt:87` | 重排一次完整的 11 档映射表（iOS 样式 ↔ M3 槽位 ↔ 实际用途），写在 `Type.kt` 顶部当文档 |

### 6.5 改不了 / 不该改的

| 项 | 原因 | 替代方案 |
| --- | --- | --- |
| 真背板模糊（regular 20–40px） | minSdk 26，`RenderEffect` 需要 API 31；`Modifier.blur()` 只模糊自身内容不模糊背板；haze 之类第三方库在低版本也要降级。**而且 skill 本身反对在安卓做玻璃** | **维持"半透明 + 高光 + 发丝描边"路线**；底部渐隐幕已经在替代 scroll edge effect（`AppRoot.kt:157-171`），这一条**已经符合** skill 的清单第 6 项 |
| "减少透明度"系统信号 | Android 无公开 API | 用 `isHighTextContrastEnabled` 作为代理信号（skill 给 Flutter 的建议原文就是这个思路，安卓同理） |
| "减少动态效果"系统信号 | 无公开 API（Google issue tracker #430201816 仍在请求中）→ 见下面的专门条目 | 读 `Settings.Global.ANIMATOR_DURATION_SCALE`，为 `0f` 时视为关闭动画；**必须 try/catch + 给默认值** |
| 图标"骨架随字重变化"（9 字重） | `ImageVector` 是按路径渲染的，不支持按字重切换骨架 | 放弃字重维度（`sf-symbols.md` 的 9 字重是 SF 字体的专属能力）；**只做 outline/fill 双套**，且只在 Tab 栏这一处投入（见 P1-7） |
| SF Pro 字体 | 不可随 App 分发 | 继续用 Inter（**这是正确选择**）；但**字距不能照抄 SF Pro 表**——Inter 的原始字距更紧，套上 SF 的负值会二次收紧。把表当量级参考，用真机截图对齐 |
| SF Symbols | 商标限制（禁止在 app 图标/logo/商标用途使用或画"易混淆的相似图形"）+ 平台限制 | 继续手绘 `ImageVector`（正确选择）；**但不要画得和 SF Symbols 过于相似**，尤其是 `square.and.arrow.up` 这类辨识度极高的形状 |
| 系统语义色的自动适配与 4 套变体 | Compose 无对应的动态色机制 | 已有 `AcrylicTokens`，继续走 token；补齐增强对比度那一套即可（P1-8） |
| 竖屏锁定 | 产品决策（`layout.md` 建议支持横屏） | **保留**，并在 README 或 Manifest 注释里写明这是有意为之、以及放弃了什么 |
| App 内的浅色/深色开关 | `dark-mode.md` + `settings.md` **两条**规则反对 | **保留**，但把"跟随系统"设为默认与首项（见第 5 节冲突 2）。同时修掉 P0-5——开关存在的前提是状态栏能跟着变 |
| 日志的 11sp / 行高 1.55 | 这是**有意为之**的终端排版（要放下 `INFO │ 03:24:37.292 │ ` 这 19 字符前缀） | 保留，但要说明理由；**不要再小**（11pt 就是 HIG 的最小值），并考虑给一个字号档位 |
| 图表无坐标轴 | 统计页是紧凑的概览卡，加轴网格会破坏分组列表的观感 | ⚠️ **这是我的判断，不是 skill 的结论。** 如果要动统计页，**应该单独加载 `charting-data.md` + `charts.md` 做一次专项评审**再决定 |

> 💡 **单独强调：没有"减少动态效果"响应，是本工程无障碍层面最大的一块空白，但代价最低、收益最高。**
> 全工程 20 处动画（`AcrylicTabBar` 的弹簧缩放、`Overlays` 的滑入/缩放、`StartStopFab` 的呼吸光晕、3 处无限骨架/呼吸动画）**全部无条件播放**，`ANIMATOR_DURATION_SCALE` 也没有被读过一次。
> skill 的原文要求是（`accessibility.md › Cognitive`）：收紧弹簧减少回弹、动画直接跟手、**不做 z 轴深度动画**、**x/y/z 位移换成淡入淡出**、**不做模糊的进出动画**。
> **最小可行改法**：建一个 `LocalReduceMotion`（读 `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`），为 true 时：① `slideInVertically` → `fadeIn`；② `spring(...)` → `snap()` 或 `tween(80)`；③ **关掉 3 处 `rememberInfiniteTransition` 的无限循环动画**（这是"自动且重复的动画"，HIG 明确要求减少）；④ FAB 的呼吸光晕和 `PulsingDot` 改成静态色。

### 6.6 建议的落地顺序

按 skill 自己的排序建议（`SKILL.md › Design improvement mode` 第 5 步：**无障碍 → 惯例 → 手艺 → 打磨**）：

```text
第 0 步（半天，先做这个）
  └─ 写一个对比度断言测试：把 AcrylicTokens 里所有「前景 × 背景」组合跑一遍 WCAG 相对亮度。
     这是 skill「数字，不是形容词」最直接的应用，也是唯一能一次性找出所有对比度问题的办法。
     （已经能预判的结果：LightTextTertiary #8E8E93 在浅色面板上 ≈3.0:1，不达 4.5:1）

第 1 轮｜无障碍补洞（不改视觉，1~2 天）
  ├─ P0-1 Toast 超时接 getRecommendedTimeoutMillis + 手动关闭 + 错误不自动消失
  ├─ P0-2 Toast 加 liveRegion；SectionTitle/大标题加 heading()；分组加 expand/collapse
  ├─ P0-3 六处小触控目标套 minimumInteractiveComponentSize()
  ├─ P0-4 修 LightTextTertiary（顺带修 DarkDivider 过重，P2-4）
  ├─ P0-5 状态栏图标跟随应用内主题（真 bug，优先级最高的单点修复）
  ├─ P0-6 ConfigScreen 搜索框加 imePadding()
  ├─ P0-7 起停/危险确认/Toast 加触觉反馈
  └─ 减少动态效果响应（见 6.5 的 💡 条目）

第 2 轮｜导航与身份识别（结构，2~3 天）
  ├─ P1-2 五个页面补大标题 + 滚动折叠（`toolbars.md` 那句原文可以直接当验收标准）
  ├─ P1-1 Tab 栏补文字标签（高度联动：52+10=62dp → 需同步 AppRoot.TAB_BAR_HEIGHT）
  ├─ P1-3 修 headlineMedium 的 28sp Bold / -0.7sp（符号反了）
  └─ P2-1 抽 Spacing / Radius / Elevation / Motion 四个 token 对象

第 3 轮｜语义与手艺（2~3 天）
  ├─ P1-4 气泡 → 底部 action sheet（Cancel 在底、破坏性在顶、标题单行、不滚动）
  ├─ P2-7 拆 ContentSurface / FunctionalSurface
  ├─ P2-6 下调功能层 elevation，去掉 FAB 的蓝色投影
  ├─ P1-7 Tab 的 outline/fill 双套图标
  ├─ P2-8/2-9 补齐 Typography 的 6 个槽 + 所有 lineHeight（顺手消灭那段 Roboto）
  └─ P2-14 补齐 M3 ColorScheme，让存活的 M3 组件落到 token 上

第 4 轮｜打磨
  ├─ P1-5 子页面 Tab 栏可见性
  ├─ P1-6 Tab 切换与路由跳转加动效（AnimatedContent / Crossfade）
  ├─ P1-8 增强对比度色板
  ├─ P2-10 统一两套骨架屏
  ├─ P2-11 修弹窗"只淡入不缩放"的 bug
  ├─ P2-12/2-13 统一按压反馈，去掉残留 ripple
  ├─ P2-2/2-3/2-5 统一 16dp 边距、分隔线 inset、圆角刻度
  ├─ P2-19 清理死代码
  └─ P2-17/2-18 统计页图表专项 + 日志页搜索（各自单独做一次 skill 评审）
```

**两点方法建议**

1. **每一轮改完，按 skill 的报告模板给自己写一份 review**（`Summary / Critical / Improvements / Craft notes / What works / Platform notes`），带 `What / Why / Fix` 和 Critical/High/Medium/Low 标签。这既是验收记录，也能逼着你把结论写成可验证的形式。

2. **Craft notes 里目前最该回答的一个问题**（`SKILL.md › Lens 3`）：
   > "**Does it have a point of view?** Name the one thing this design would be remembered by."
   >
   > "**Is the boldness spent in one place?** One signature element, everything around it quiet."
   >
   > "**Remove one accessory.** Ask what can go without loss."

   现在的 AzurPilot Mobile 有四个元素在同时用力：**吸顶状态卡 + 分组内嵌列表 + 手绘图标 + 亚克力面板**。没有一个是"签名元素"。skill 要求"大胆只花在一个地方，其余全部安静"——这是重构时最值得花时间想清楚的一件事，比任何单点修复都更能改变观感。**建议把 FAB 或状态卡选为签名元素，其余三个退到安静。**

---

## 7. 原仓库里值得直接引用的片段

> 以下引用都标注了 `文件 › 章节`，可按需回 `D:\Temp\apple-design-skill\references\hig\` 查证。**引用节制，短句即可；不要整段搬运**（见 1.6 授权说明）。

**关于在安卓上做玻璃**（`liquid-glass.md › Cross-platform translation › React Native`）

> "On Android, blur is costly and inconsistent across versions, and the platform's own design language does not use glass. **Prefer an opaque or lightly translucent bar there rather than imitating iOS.**"

—— 这一句是你"不做真模糊"这个决定最权威的外部背书。

**关于内容层不能用玻璃**（`materials.md › Liquid Glass`）

> "**Don't use Liquid Glass in the content layer.** Liquid Glass works best when it provides a clear distinction between interactive elements and content... Instead, use standard materials for elements in the content layer, such as app backgrounds."

—— 支撑第 5 节冲突 3 的组件拆分建议。

**关于手机上不要用 popover**（`popovers.md › Mobile (iOS, iPadOS)`）

> "**Avoid displaying popovers in compact views.** Make your app or game dynamically adjust its layout based on the size class of the content area. Reserve popovers for wide views; for compact views, use all available screen space by presenting information in a full-screen modal view like a sheet instead."

—— 支撑 P1-4。

**关于 Tab 栏必须有标签**（`tab-bars.md › Best practices`）

> "**Include tab labels to help with navigation.** A tab label appears beneath or beside a tab bar icon, and can aid navigation by clearly describing the type of content or functionality the tab contains. **Use single words whenever possible.**"

—— 支撑 P1-1。

**关于大标题**（`toolbars.md › Platform considerations › Phone (iOS)`）

> "**Use a large title to help people stay oriented as they navigate and scroll.** By default, a large title transitions to a standard title as people begin scrolling the content, and transitions back to large when people scroll to the top, reminding them of their current location."

—— 支撑 P1-2。**这句话同时描述了你要实现的折叠行为本身**，可以直接当验收标准。

**关于定时消失的元素**（`accessibility.md › Cognitive`）

> "**Minimize use of time-boxed interface elements.** Views and controls that auto-dismiss on a timer can be problematic for people who need longer to process information, and for people who use assistive technologies that require more time to traverse the interface. Prefer dismissing views with an explicit action."

—— 支撑 P0-1，这是全文档里最直接否定现有 Toast 实现的一条。

**关于无障碍的三个硬数字**（`accessibility.md`，三张表）

> 文字：iOS 默认 **17 pt**，最小 **11 pt**。
> 对比度：≤17pt 需 **4.5:1**；18pt 或粗体需 **3:1**。
> 控件：iOS 默认 **44×44 pt**，最小 **28×28 pt**；带边框元素周围约 **12 pt**，无边框按可见边缘约 **24 pt**。

—— 第 6 节所有 P0 检查的标尺。

**关于手艺的自我警告**（`SKILL.md › Lens 3: Visual design and craft`）

> "**Is it a template?** Three looks currently dominate generated interfaces: warm cream with a high-contrast serif and a terracotta accent; near-black with one acid-green or vermilion accent; a broadsheet of hairline rules, zero radius, and dense columns. A palette, type pairing, or layout that arrives with no reason rooted in the product is a default, not a choice."

以及同节的：

> "**Is the boldness spent in one place?** One signature element, everything around it quiet."

> "**Remove one accessory.** Ask what can go without loss. If nothing can, say the design is already lean."

—— 这三条是第 3 轮"手艺"改造时的自检清单。对 AzurPilot Mobile 来说，**"你这一屏会被记住的那一件事是什么"**这个问题目前还没有答案：状态卡吸顶 + 资源分组列表 + 手绘图标 + 亚克力面板，四个都在用力，没有一个是"签名元素"。这是 Craft notes 里最该回答的问题。

**关于不要过度修改**（`SKILL.md › Working rules`）

> "**Don't flatten the personality.** Guidelines exist to make apps usable, not identical. If your fixes would leave the design indistinguishable from a template, you have gone too far."

—— 用这条提醒自己：本工程刻意模仿 iOS 观感是**有理由的**（它是一个 iOS 风格的碧蓝航线脚本控制台，PC 端 WebUI 的重写版），不是为了套模板。skill 的规则用来修漏洞，不用来抹掉这个取向。

---

## 附录 A：可复用的数值速查

```text
字号（iOS 默认档）
  Large Title 34 / Title1 28 / Title2 22 / Title3 20 / Headline 17 SemiBold
  Body 17 / Callout 16 / Subhead 15 / Footnote 13 / Caption1 12 / Caption2 11
  默认 17 · 最小 11 · 放大上限 ~200%（AX5 正文字号约 33）

字距（SF Pro，仅供参考，Inter 需实测）
  11:+0.06  12:0.00  13:-0.08  14:-0.15  15:-0.23  16:-0.31
  17:-0.43  18:-0.44  19:-0.45  20:-0.45  21:-0.36  22:-0.26
  24:+0.07  26:+0.22  28:+0.38  34:+0.40

对比度
  ≤17pt / 全部字重 → 4.5:1
  ≥18pt 或 Bold    → 3:1
  深色模式最低 4.5:1，自定义色尽量 7:1

尺寸
  控件 命中区默认 44×44pt，最小 28×28pt（桌面 28×28 / 20×20）
  按钮 命中区至少 44×44pt（visionOS 60×60）
  带边框元素周边 12pt；无边框按可见边缘 24pt
  ⚠️ iPhone 标准布局边距 16pt（iPad 20pt）—— 这是平台惯例判断值，HIG 原文未给

加载
  唯一的时间措辞："more than a moment or two" → 超过就用进度指示器
  没有任何精确秒数阈值，不要发明

材质（跨平台起始值，非规范）
  regular: 模糊 20–40px，填充 60–80%，饱和 1.2–1.5×
  clear:   模糊 8–16px，  填充 20–40%，亮背景压暗层 35%

数量上限
  Tab 默认 ≤5 个
  一屏强调按钮 1~2 个
  Alert 最多 3 个按钮、标题不超过 2 行
  Toolbar 操作分组最多 3 组
  一次评审加载参考文件 8~12 篇（绝不整目录）
```

## 附录 B：本次调研没有做的事 / 已知局限

- **没有跑起来任何截图评审。** 所有判断基于**代码**（以及一份完整只读审计），不是渲染结果。第 6 节的对比度数字是**算式估算**（`#8E8E93` on `#F2F2F7` ≈ 3.0:1），**落地前请用测试断言确认**。
- **没有读全 122 页 HIG。** 精读了与移动端 UI 直接相关的约 30 页：无障碍、颜色、排版、布局、材质、Liquid Glass、动效、暗色、图标、SF Symbols、设计原则、品牌、iOS 概览、写作、Tab 栏、工具栏、按钮、分段控件、开关、列表、集合、标签、滚动视图、气泡、Sheet、Alert、Action Sheet、模态、反馈、加载、进度、设置、手势、搜索、披露控件。
- **`charting-data.md` / `charts.md` / `app-icons.md` / `voiceover.md` / `playing-haptics.md` / `entering-data.md` / `virtual-keyboards.md` 未展开。** 如果重构涉及统计页图表、应用图标、或表单大幅改动，**建议按 `SKILL.md` 的路由表单独加载这几篇做专项评审**。
- **没有验证第三方 Compose 模糊库（haze 等）的实际表现**——因为结论是"不引入模糊"，所以没做。
- **没有检查 `icons/AppIcons.kt` 里每个图标的路径质量**（只确认是 `ImageVector` 手绘、共 299 行、4 处 `SolidColor(Color.Black)` 是有意为之）。P1-7 建议画 fill 版时，要先做一次图标一致性评审（`icons.md`：尺寸、细节程度、描边粗细、视角四点一致 + 视觉居中）。
- **工程在审计期间被并发修改。** 第 6 节依据的是 2026-09-11 10:33 的快照；`Skeletons.kt`（新建）、`HomeScreen`、`TasksScreen`、`AppRoot`、`Overlays`、`AppViewModel` 都在同一分钟被写过。**动手前请重新核对，尤其是行号。**
- **本次为纯调研，没有修改 `AzurPilotMobile` 里的任何文件，也没有碰 `D:\Tools\AzurPilot`。**
- 仓库的 `references/hig-lookup.md` 里每个页面都带 **Apple 最后修改日期**（例如 `color.md` 2025-12-16、`typography.md` 2025-12-16、`layout.md` 2025-09-09、`tab-bars.md` 2026-06-08、`design-principles.md` 2026-06-08、`sheets.md` 2026-03-24）。**要确认某条规则是否最新，去那里对日期，不要靠记忆。**
