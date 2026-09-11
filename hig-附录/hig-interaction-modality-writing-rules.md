# HIG 交互 / 模态 / 文案 可执行规则提取

**来源范围**：指定 11 个文件全部通读（`modality.md`、`sheets.md`、`popovers.md`、`alerts.md`、`writing.md`、`feedback.md`、`loading.md`、`settings.md`、`gestures.md`、`searching.md`、`designing-for-ios.md`）。
其中多处规则显式指向姊妹页，为补齐"精确数值"另读了 8 个被引用文件并在每条后如实标注出处：`action-sheets.md`、`progress-indicators.md`、`undo-and-redo.md`、`buttons.md`、`disclosure-controls.md`、`lists-and-tables.md`、`toggles.md`、`layout.md`、`accessibility.md`（共 9 个）。凡原文未给数值者，一律标注"原文未给数值"，不做推测。

---

### 1. 模态：使用判断、层级、退出方式、detents 与滑动关闭

**定义与总原则**
- 模态 = 在独立专用模式中呈现内容，阻止与父视图交互，并且需要显式动作才能关闭 — `prevents interaction with the parent view and requires an explicit action to dismiss` (来源: modality.md › 开头)
- 仅以下四类收益成立时才用模态：①必须让人接收到关键信息并可能据此行动；②提供确认或修改"刚发生的那次操作"的选项；③完成独立、窄范围的任务且不丢失先前上下文；④提供沉浸体验或帮助专注复杂任务 (来源: modality.md › 开头)
- 只在有明确收益时才用模态，因为它把人带离当前上下文且必须动作才能关闭 (来源: modality.md › Best practices)
- 模态任务必须简单、短、流线化；任务过于复杂会让人忘记进入模态前"挂起"的任务，尤其当模态遮挡了先前上下文时 (来源: modality.md › Best practices)
- 禁止做出"应用里的应用"：模态任务内不要放视图层级；若必须有子视图，只提供**单一路径**通过该层级，并且不要放会被误认为"关闭模态"的按钮 (来源: modality.md › Best practices)
- 必须让人一眼识别模态的任务：给标题命名该任务，或加一段文字描述任务/给指引 (来源: modality.md › Best practices)

**"一次一个模态"硬规则**
- `Let people dismiss a modal view before presenting another one.` —— 必须先让人关掉当前模态，再呈现下一个 (来源: modality.md › Best practices)
- 多个模态视图同时可见会造成视觉杂乱、让 app 显得散乱；同时存在多个视图会加重认知负担，尤其当新模态盖住旧模态时 (来源: modality.md › Best practices)
- alert 可以出现在所有其他内容（包括其他模态视图）之上，但**永远不要同时显示超过一个 alert** (来源: modality.md › Best practices)
- 从主界面**一次只显示一个 sheet**：人关闭 sheet 后预期回到父视图/窗口，若关闭后落到另一个 sheet 就会失去位置感；若 sheet 内某操作导致另一个 sheet 出现，先关闭第一个再显示新的；必要时可在第二个被关闭后重新显示第一个 (来源: sheets.md › Best practices)
- **一次只显示一个 popover**；绝不显示级联或层级 popover（一个从另一个里长出来）；要显示新的就先关闭已打开的那个 (来源: popovers.md › Best practices)
- **不要在 popover 之上显示任何视图**，唯一例外是 alert (来源: popovers.md › Best practices)

**模态层级与选择（从最轻到最重）**
1. 内联披露（inline disclosure）：用披露控件隐藏细节直到相关 — 固定顺序是"把最可能用到的控件放在披露层级顶部始终可见，高级功能默认隐藏" (来源: disclosure-controls.md › Best practices)
2. popover：点击/轻点控件或交互区域时浮现在其他内容之上的**临时**视图，只装少量信息或功能、限制为几个相关任务；例：日历事件 popover 用来改日期/时间或换日历，改完即消失 (来源: popovers.md › 开头, popovers.md › Best practices)
3. action sheet：呈现**与人主动发起的操作相关**的选项的模态视图；用 action sheet 而**不是 alert**来提供与有意操作相关的选择 (来源: action-sheets.md › 开头, action-sheets.md › Best practices, alerts.md › Mobile (iOS, iPadOS))
4. sheet：帮助完成与当前上下文紧密相关的**有范围任务**，适合索取特定信息或呈现"能在返回父视图前完成的简单任务" (来源: sheets.md › 开头)
5. alert：给出人**立刻需要的关键信息**；用于告知问题、警告可能破坏数据的操作、给机会确认购买或其他重要操作 (来源: alerts.md › 开头)
6. 全屏模态：用于深度内容（视频、照片、相机视图）或复杂/多步任务（标注文档、编辑照片）(来源: modality.md › Best practices)
- 平台默认惯例：iOS/iPadOS/macOS 倾向用 sheet 或 popover 完成"独立任务"；iPadOS/macOS/visionOS 也可只用独立窗口 (来源: modality.md › 开头)
- macOS/tvOS/visionOS/watchOS 的 sheet **始终模态**；iOS/iPadOS 的 sheet 可模态**或非模态**，非模态 sheet 让人在不关闭的情况下影响父视图（Notes 用非模态 sheet 格式化文本选区）(来源: sheets.md › Anatomy)
- 复杂或长时间流程应另选方案：iOS/iPadOS 用全屏模态样式（`UIModalPresentationStyle.fullScreen`）；macOS 开新窗口或进入全屏（自包含任务如编辑文档适合独立窗口，看媒体适合全屏）(来源: sheets.md › Best practices)
- macOS 上需要人**反复输入并观察结果**时用 panel 而不是 sheet（例：查找替换中逐个执行替换以核对结果）(来源: sheets.md › Desktop (macOS))
- 要在"不关闭 sheet 的前提下"提供影响主任务的补充项：visionOS 用 split view、macOS 用 panel、iOS/iPadOS 用非模态 sheet (来源: sheets.md › Best practices)

**popover 什么时候是对的**
- 需要更多内容空间但只是临时需要时用 popover：侧边栏和面板占地大，临时内容放 popover 可精简界面 (来源: popovers.md › Best practices)
- 定位规则：popover 的箭头要尽可能直接指向触发它的元素；理想情况下不覆盖触发元素，也不覆盖使用期间需要看到的关键内容 (来源: popovers.md › Best practices)
- Close/Cancel/Done 按钮**只在提供明确性时才值得包含**（例如区分带或不带保存退出）；否则 popover 一般在"点击/轻点外部"或"选中项"时关闭；若支持多选，必须保持打开直到人显式关闭或点外部 (来源: popovers.md › Best practices)
- 非模态 popover 自动关闭时**必须保存工作**；只有人显式点击 Cancel 才丢弃工作 (来源: popovers.md › Best practices)
- 尽可能让人用**单次**点击/轻点关闭一个 popover 并打开另一个（多个 bar button 各自开 popover 时尤其重要）(来源: popovers.md › Best practices)
- 不要把 popover 做得太大：刚好能显示内容并指向来源即可；必要时系统会自动调整尺寸 (来源: popovers.md › Best practices)
- 改变 popover 尺寸时要动画过渡，避免看起来像"被一个新 popover 替换了" (来源: popovers.md › Best practices)
- 帮助文档里**避免出现 `popover` 这个词**，改指具体任务或选择：写 `Select the Show button.` 而不是 `Select the Show button at the bottom of the popover.` (来源: popovers.md › Best practices)

**popover 什么时候是错的**
- 紧凑视图（compact）中 → 错：不要用 popover，改用全屏模态如 sheet；按内容区域 size class 动态调整布局，宽视图才保留 popover (来源: popovers.md › Mobile (iOS, iPadOS))
- 用来显示警告 → 错：人可能错过或误关，改用 alert (来源: popovers.md › Best practices)
- 级联或多个 popover → 错 (来源: popovers.md › Best practices)
- 内容或功能偏多 → 错：限制为几个相关任务 (来源: popovers.md › Best practices)
- macOS 上需要反复输入并观察结果 → 改用 panel (来源: sheets.md › Desktop (macOS))

**sheet 的退出方式与按钮规则**
- 三个标准按钮语义：**Cancel**（或 Close）不保存任何更改即关闭，最常见；**Done** 完成任务或显式保存更改后关闭；**Back** 用于多步流程上一步或层级父视图，**不用于关闭 sheet** (来源: sheets.md › Anatomy)
- 提供 Done 时**必须**配 Cancel 或 Back；只靠 Done 意味着"完成任务是唯一出口"，会显得受限或误导 (来源: sheets.md › Best practices)
- `Avoid showing all three buttons — Cancel, Done, and Back — together.` (来源: sheets.md › Best practices)
- iOS/iPadOS 单视图 sheet：Cancel 在**顶部工具栏 leading edge**；Done 在 **trailing edge** (来源: sheets.md › Mobile (iOS, iPadOS))
- 多步流程 sheet 的按钮位置可随步骤变化 (来源: sheets.md › Mobile (iOS, iPadOS))
- 退出方式的平台惯例：iOS/iPadOS/watchOS 期望顶部工具栏按钮或**向下轻扫**；macOS/tvOS 期望主内容视图中的按钮 (来源: modality.md › Best practices)
- 关闭可能丢失用户生成内容的模态前必须确认并给出解决途径（iOS 例：给出包含保存选项的 action sheet）(来源: modality.md › Best practices)

**detents / grabber / 滑动关闭 / 圆角**
- detents = sheet 自然停靠的特定高度；为 iPhone 设计时系统定义**两个**：`large` = 完全展开 sheet 的高度；`medium` = 完全展开高度的**约一半**（`about half of the fully expanded height`）(来源: sheets.md › Mobile (iOS, iPadOS))
- sheet **自动支持 large** detent；同时加 medium 才能停在两档；只指定 medium 会阻止 sheet 展开到全高 (来源: sheets.md › Mobile (iOS, iPadOS))
- iPhone 应用**考虑支持 medium** 以实现内容的渐进披露：分享 sheet 在 medium 内显示最相关项（无需调整即可见），滚动或展开查看更多 (来源: sheets.md › Mobile (iOS, iPadOS))
- 内容在全高才更有用时**不要**支持 medium：Messages 和 Mail 的撰写 sheet 只在全高显示，以留出创作空间 (来源: sheets.md › Mobile (iOS, iPadOS))
- 可调整大小的 sheet 必须包含 **grabber**：它是 sheet 顶部边缘的小水平指示器，表明可拖动调整大小，也可**轻点以在 detents 间循环切换**，并与 VoiceOver 配合使人无需看屏即可调整尺寸 (来源: sheets.md › Mobile (iOS, iPadOS))
- **必须支持下滑关闭 sheet**：人预期垂直轻扫关闭而不是点关闭按钮；若开始下滑时 sheet 内有未保存更改，用 action sheet 让人确认 (来源: sheets.md › Mobile (iOS, iPadOS))
- sheet 圆角/角半径的**具体数值：原文未给数值**；仅描述 macOS 上 sheet 是 `a cardlike view with rounded corners that floats on top of its parent window`，父窗口在 sheet 期间变暗 (来源: sheets.md › Desktop (macOS))
- iPadOS 应用优先使用 page 或 form sheet 呈现样式：各自使用默认尺寸，把内容居中显示在变暗的背景视图之上，提供一致体验 (来源: sheets.md › Mobile (iOS, iPadOS))
- macOS 上使用合理的**默认尺寸**（人一般不期望调整 sheet 大小），但仍宜支持调整；打开 sheet 时把父窗口带到前面（父窗口是文档窗口时同时带出其无模式文档面板）；必须保证人在未关闭 sheet 时也能把其他 app 窗口带到前面 (来源: sheets.md › Desktop (macOS))

**action sheet 补充硬规则**
- 使用要节制，因为它会打断当前任务 (来源: action-sheets.md › Best practices)
- 标题尽量**单行**显示（过长标题难以快速阅读，会被截断或需要滚动）；仅在必要时提供 message（标题 + 当前操作上下文通常已足够）(来源: action-sheets.md › Best practices)
- 若有 Cancel，放在 action sheet **底部**（watchOS 在左上角）；SwiftUI confirmation dialog **默认**包含 Cancel (来源: action-sheets.md › Best practices)
- 破坏性选项用 destructive 样式，并放在 action sheet **顶部**最显眼处 (来源: action-sheets.md › Best practices)
- iOS/iPadOS：用 action sheet 而**不是 menu**提供与操作相关的选择 —— 人预期"执行操作时"出现 action sheet，而 menu 是"选择显示它时"才出现 (来源: action-sheets.md › Mobile (iOS, iPadOS))
- **避免 action sheet 滚动**：按钮越多选择越费时，且滚动时容易误触按钮 (来源: action-sheets.md › Mobile (iOS, iPadOS))

**内联披露（inline disclosure）细节**
- 把最可能用到的控件放在披露层级顶部**始终可见**，高级功能默认隐藏 (来源: disclosure-controls.md › Best practices)
- disclosure triangle 方向：内容隐藏时从 leading 边缘**向内**指，内容可见时**向下**；点击在两种状态间切换并相应展开/收起；必须提供描述性标签，如 `Advanced Options` (来源: disclosure-controls.md › Disclosure triangles)
- disclosure button 方向与 triangle **相反**：隐藏时向下，可见时向上；必须放在它所显示/隐藏的内容**附近**；**单个视图中最多一个** disclosure button (来源: disclosure-controls.md › Disclosure buttons)
- iOS/iPadOS/visionOS 通过 SwiftUI `DisclosureGroup` 提供披露控件 (来源: disclosure-controls.md › Mobile (iOS, iPadOS, visionOS))

**用 undo 替代"确认对话框"**
- 让人能**多次**撤销：不要设置不必要的撤销次数上限；一般预期能撤销自"打开文档或保存工作"以来执行的**每一个**操作 (来源: undo-and-redo.md › Best practices)
- 提高可预测性：菜单项标签要标识结果，如 `Undo Typing`、`Redo Bold` (来源: undo-and-redo.md › Best practices)
- iOS/iPadOS 摇动设备的 undo/redo alert 标题自动带前缀 `Undo ` 或 `Redo `（含**尾随空格**），你只需补 1–2 个词，例如 `Undo Name`、`Redo Address Change` (来源: undo-and-redo.md › Mobile (iOS, iPadOS))
- **必须显示** undo/redo 的结果（例如滚动到被恢复的段落），否则人会以为没生效而反复执行 (来源: undo-and-redo.md › Best practices)
- 考虑批量回退：撤销一批离散但相关的操作，或一键撤销自打开/保存以来的全部更改 (来源: undo-and-redo.md › Best practices)
- 只在必要时提供 undo/redo 按钮；用系统标准符号并放在工具栏 (来源: undo-and-redo.md › Best practices)
- macOS：undo/redo 放在 Edit 菜单**顶部**，快捷键 `Command–Z` / `Shift–Command–Z` (来源: undo-and-redo.md › Desktop (macOS))

---

### 2. 警告与破坏性操作（alerts / destructive）

**什么时候 alert 是正当的**
- 正当用途：告知问题、警告可能破坏数据的操作、给机会确认购买或其他重要操作 (来源: alerts.md › 开头)
- 传达**关键且尽量可操作**的信息；alert 会按设计打断上下文，重要性必须匹配打断程度；用得太频繁或传递无关紧要信息会失去影响力 (来源: feedback.md › Best practices)
- 破坏性操作**罕见且不可撤销**时必须弹 alert，因为可能是误触所致 (来源: alerts.md › Best practices)
- 仅在数据丢失**意外且不可逆**时警告 (来源: feedback.md › Best practices)
- 可以确认"足够重要"的活动已完成（例：Apple Pay 交易成功），但要稀缺使用 (来源: feedback.md › Best practices)

**什么时候 alert 不正当**
- 不要仅为传递信息而用 alert：不可操作的打断不讨喜；纯信息改用相关上下文里的替代方式（服务器连接不可用时 Mail 显示一个可展开了解的指示器）(来源: alerts.md › Best practices)
- 常见且**可撤销**的操作不要弹 alert，**即使具破坏性**：删邮件、删文件都不提示，因为人有意丢弃且可撤销 (来源: alerts.md › Best practices)
- 数据丢失是操作的**预期结果**时不要警告（Finder 丢垃圾不警告）(来源: feedback.md › Best practices)
- **不要在 app 启动时显示 alert**：若必须立刻告知新信息，应设计成易于发现；启动检测到问题（如无网络）时改用缓存或占位数据 + 一个非侵入性标签说明问题 (来源: alerts.md › Best practices)
- 不要用 popover 显示警告（人可能错过或误关）→ 改用 alert (来源: popovers.md › Best practices)
- 不要用 alert 提供与有意操作相关的**多个选择**（alert 不提供与该操作相关的额外选择）→ 改用 action sheet (来源: alerts.md › Mobile (iOS, iPadOS), action-sheets.md › Best practices)

**结构**
- 标题 + **可选**说明文字 + **最多三个按钮**（`up to three buttons`）；iOS/iPadOS/macOS/visionOS 的 alert 可含文本框；macOS 与 visionOS 可含图标和 accessory view；macOS 还可加抑制用的复选框和 Help 按钮 (来源: alerts.md › Content)
- 文本框**只在解决当前状况需要用户输入时**才包含（例：接收密码的安全文本框）(来源: alerts.md › Content)
- 尽量避免让 alert 滚动：保持标题短、只在必要时加简短消息 (来源: alerts.md › Mobile (iOS, iPadOS))

**按钮顺序 / 位置 / 默认 / 破坏性**
- 最可能被选择的按钮放在一行按钮的 **trailing 侧**，或按钮堆叠的**顶部** (来源: alerts.md › Buttons)
- **默认按钮**始终在一行按钮的 trailing 侧或堆叠的顶部 (来源: alerts.md › Buttons)
- **Cancel 按钮**通常在行按钮的 **leading 侧**或堆叠的**底部** (来源: alerts.md › Buttons)
- 有破坏性动作时**必须**包含 Cancel 按钮，给出一条清楚安全的规避路径 (来源: alerts.md › Buttons)
- **不要让 Cancel 成为默认按钮** (来源: alerts.md › Buttons)
- 若想让人读 alert 而不是自动按 Return 关掉 → **不要给任何按钮设默认** (来源: alerts.md › Buttons)
- 若必须显示只有一个按钮且该按钮又是默认按钮的 alert → 用 `Done`，**不要用 `Cancel`** (来源: alerts.md › Buttons)
- 破坏性样式用于**人并非有意选择**的破坏性动作，用来把注意力引到该按钮上 (来源: alerts.md › Buttons)
- 破坏性样式**例外**：人明确选择的破坏性动作（如 Empty Trash）**不加**破坏性样式，因为"按 Return 确认其原本意图"的便利胜过重申其破坏性 (来源: alerts.md › Buttons)
- **不要把 primary 角色给破坏性按钮**，即使它是最可能的选择：视觉突出会让人不读就点，应把 primary 给非破坏性按钮 (来源: buttons.md › Role)
- 替代取消路径（除 Cancel 按钮外）：iOS/iPadOS 可回到主屏；iOS/iPadOS/macOS/visionOS 可按 `Esc` 或 `Command-.`（Command + 句点）；tvOS 可按遥控器 `Menu` (来源: alerts.md › Buttons)

**永远不要用 OK 作默认**
- 除非 alert **纯信息型**，否则避免把 `OK` 作为默认按钮标题；`OK` 的语义可能不清 —— 是"OK，我要完成这个操作"还是"OK，我明白这个操作会带来的负面结果" (来源: alerts.md › Buttons)
- 用具体按钮标题替代：`Erase`、`Convert`、`Clear`、`Delete` (来源: alerts.md › Buttons)
- `OK` 的合法用法**仅限**信息型 alert 表示接受；同时**避免 `Yes` 和 `No`** (来源: alerts.md › Buttons)
- 取消 alert 动作的按钮**始终**用标题 `Cancel` (来源: alerts.md › Buttons)

**精确措辞**
- 所有 alert 文案都要**直接**，用中性、平易的语气；不要迂回、不要指责，也不要掩盖问题的严重性 (来源: alerts.md › Content)
- 标题要清楚简洁地描述状况，尽量说明**发生了什么、在什么上下文、为什么** (来源: alerts.md › Content)
- 标题**禁止**写法：`Error`、`Error 329347 occurred` 这类不传递有用信息的标题 (来源: alerts.md › Content)
- 标题**长度上限**：不要换行超过**两行** (来源: alerts.md › Content)
- 标题大小写：标题是**完整句子** → 用 sentence-style capitalization + 适当句末标点；标题是**句子片段** → 用 title-style capitalization 且**不加**句末标点 (来源: alerts.md › Content)
- 说明文字只在增加价值时包含，且尽量短、用完整句子、sentence-style capitalization、适当标点 (来源: alerts.md › Content)
- **不要解释 alert 按钮**：文案和按钮标题清楚时无需解释；极少数需要指引时用 `choose` 这类词（兼顾设备与交互方式），并直接引用按钮的**确切标题且不加引号** (来源: alerts.md › Content)
- 按钮标题目标**一到两个词**，描述"选择该按钮的结果"；优先用与 alert 文本直接相关的动词/动词短语，例如 `View All`、`Reply`、`Ignore` (来源: alerts.md › Buttons)
- 按钮标题用 **title-style capitalization** 且**无句末标点** (来源: alerts.md › Buttons)

**确认 vs 撤销 的取舍**
- 优先让人能撤销，而不是每次弹确认；破坏性但可撤销的常见操作不弹 alert (来源: alerts.md › Best practices)
- 数据丢失是预期结果 → 不警告；意外且不可逆 → 必须警告 (来源: feedback.md › Best practices)
- 命令无法执行时要**显示出来并帮助理解原因**（例：Maps 在起终点相同时说明无法提供导航）(来源: feedback.md › Best practices)

**符号使用**
- 谨慎使用警示符号（如 `exclamationmark.triangle`）：用得太多会削弱其意义 (来源: alerts.md › Desktop (macOS))
- **只在确实需要额外注意时**使用，例如确认可能导致意外数据丢失的操作 (来源: alerts.md › Desktop (macOS))
- **不要**把它用于目的只是覆盖或删除数据的任务，例如保存或清空垃圾桶 (来源: alerts.md › Desktop (macOS))

---

### 3. 文案与写作（writing / copy / 大小写）

**大小写：发生在哪里用哪一种**（原文各自指定，非通用偏好）
- 总策略：**title case 更正式，sentence case 更随意**；为每一类 UI 元素选定一种风格并**全程一致**（例如所有 alert 用 title case，或所有 headline 用 sentence case）(来源: writing.md › Best practices)
- **按钮标签** → title-style capitalization，并考虑**以动词开头**，例 `Add to Cart` (来源: buttons.md › Content)
- **alert 按钮标题** → title-style capitalization，**不加**句末标点 (来源: alerts.md › Buttons)
- **alert 标题（整句）** → sentence-style capitalization + 句末标点；**alert 标题（片段）** → title-style capitalization，不加句末标点 (来源: alerts.md › Content)
- **alert 说明文字** → sentence-style capitalization + 适当标点 (来源: alerts.md › Content)
- **菜单项 / 菜单标签** → title-style capitalization；其定义是"除冠词、并列连词和短介词外每个词都大写，并且**无论词性，最后一个词都大写**" (来源: menus.md › Labels)
- **菜单栏菜单标题** → 尽量**一个词**（占位少、易扫视）；超过一个词时用 title-style capitalization (来源: the-menu-bar.md › Best practices)
- **标签页（tab）标签** → 名词或短名词短语（某些语境可用动词或短动词短语）+ title-style capitalization (来源: tab-views.md › Best practices)
- **分段控件（segmented control）段标签** → 名词或名词短语 + title-style capitalization；带文字标签的分段控件**不需要**介绍性文字 (来源: segmented-controls.md › Content)
- **多列列表/表格的列标题** → 名词或短名词短语 + title-style capitalization + **不加**句末标点；单列视图若没有列标题，用标签或 header 提供上下文 (来源: lists-and-tables.md › Content)
- **大纲视图列标题** → 名词/短名词短语 + title-style capitalization + 无标点，**特别避免结尾冒号**；多列大纲视图**必须**提供列标题 (来源: outline-views.md › Best practices)
- **面板（panel）标题** → 名词或名词短语 + title-style capitalization（系统示例 `Fonts`、`Colors`、`Inspector`）(来源: panels.md › Best practices)
- **输入框/组合框的介绍性标签** → 一般用 title-style capitalization 并以**冒号**结尾 (来源: combo-boxes.md › Best practices)
- **滑块标签** → 一般用 **sentence-style capitalization** 并以冒号结尾 (来源: sliders.md › Best practices)
- **设置窗格里的 box 标题** → sentence-style capitalization；在设置窗格中可给标题**加冒号** (来源: boxes.md › Content)
- **按钮标签措辞**：用几句简短文字说明按钮做什么；不要用图标能更清楚表达时硬用文字 (来源: buttons.md › Content)

**标签措辞（写什么动词）**
- 按钮和链接的标签**几乎总是用动词**；优先清楚，**不要卖弄或耍聪明**：`Send` 通常比 `Let's do it!` 更好 (来源: writing.md › Best practices)
- 链接**避免** `Click here`，改用更有描述性的词句，如 `Learn more about UX Writing`（对屏幕阅读器用户尤其重要）(来源: writing.md › Best practices)
- 多步流程用语：用 `Get Started` 表示流程开始；可用按钮标签暗示下一步，或用 `Continue` / `Next`，但**选定后必须一致**；用 `Done` 这类词明确表明流程已完成 (来源: writing.md › Best practices)
- 设置标签要**清楚简单**；标签不足以说明时**加说明**，说明只描述"开启时"的行为，人可以自行推断相反情况（Apple Watch 洗手计时器：只说明洗手时会启动计时器，**不必**说关闭时不启动）(来源: writing.md › Best practices)
- 需引导人到某个设置时，给**直接链接或按钮**，而不是描述其位置 (来源: writing.md › Best practices)
- 避免行话、性别化用语；用简单平实语言；考虑无障碍与本地化 (来源: writing.md › Getting started)
- 减少填充词：逐个检查每个词是否必要，能用更少词就用更少；拿不准就**朗读出来** (来源: writing.md › Getting started)
- 手势动词要按设备正确：触屏设备（iPhone/iPad）不要说 `click`，应说 `tap` (来源: writing.md › Best practices)
- 少用物主代词：`Favorites` 与 `Your Favorites` 传达相同含义且更简洁；若使用则全程一致、不要切换视角 (来源: writing.md › Best practices)
- **完全避免 `we`**：指代对象可能不清，例如 `We're having trouble loading this content.` 改为 `Unable to load content` 清楚得多 (来源: writing.md › Best practices)
- 每屏一件事：把最重要信息放最前；如果一个屏要传达多个想法，考虑拆成多屏并设计屏间信息流 (来源: writing.md › Best practices)
- 建立**术语表**与语言模式：一致性带来熟悉感与整体感，也让后续写作更快 (来源: writing.md › Getting started, writing.md › Best practices)
- 按设备调整：iPhone 与 Apple Watch 屏幕小，**要求简洁**；电视常置于公共空间且可能多人观看，注意称呼对象；大屏同样要求简洁，因为文字必须大 (来源: writing.md › Best practices)

**错误消息结构**
- 最好的是帮人**避免**错误；必须给错误消息时：显示在**尽量靠近问题处**、**避免责备**、清楚说明**怎么修** (来源: writing.md › Best practices)
- 正确的示例结构（给条件、给可执行动作）：`Choose a password with at least 8 characters.` —— 优于 `That password is too short` (来源: writing.md › Best practices)
- 感叹词禁用：`oops!`、`uh-oh` 通常**不必要**且可能显得不真诚 (来源: writing.md › Best practices)
- 如果语言本身无法解决一个会影响很多人的错误，把这当成**重新思考交互**的机会 (来源: writing.md › Best practices)
- 表单字段错误：显示在字段**紧邻处**，指导如何正确输入而不是责备；`Use only letters for your name` 优于 `Don't use numbers or symbols` (来源: writing.md › Best practices)
- 禁止无有用信息的机械错误：如 `Invalid name` (来源: writing.md › Best practices)
- 用**名词短语 + sentence-style capitalization + 无句末标点**写格式/校验类消息（Apple Pay 指引：引用具体字段并说明确切期望，如 `Zip code doesn't match city`、`Shipping not available for this state`；目标 **≤128 字符**以避免截断）(来源: apple-pay.md › Best practices)

**空状态结构**
- 任何空白屏都要给出**清楚的下一步**；空屏若不明显该做什么会让人不知所措 (来源: writing.md › Best practices)
- 用空状态欢迎人、教育人了解 app，并可展示 app 的 voice，但内容必须**有用且贴合上下文** (来源: writing.md › Best practices)
- 引导人可采取的操作，**并尽可能给一个按钮或链接**去执行 (来源: writing.md › Best practices)
- 空状态通常是**临时**的：不要展示那些随后可能消失的关键信息 (来源: writing.md › Best practices)

**文本输入提示**
- 清楚标注**所有**字段，并用 hint 或 placeholder 文本说明格式（示例 `name@example.com`，或描述 `Your name`）(来源: writing.md › Best practices)
- placeholder 会在输入时消失，因此宜**另配一个标签**描述字段用途 (来源: text-fields.md › Best practices)
- 搜索场景的占位文本要用来强化"当前搜索范围"或说明搜索可访问的内容类型 (来源: search-fields.md › Best practices, searching.md › Best practices)

**语气**
- 先确定 app 的 **voice**（对谁说话、用什么词汇、要人有什么感受：银行 app 传达信任与稳定，游戏传达兴奋与乐趣），再按情境**变化 tone** (来源: writing.md › Getting started)
- 同一 app 内语气随情境切换是正当的：Apple Watch 示例中一处直白直接（体现状况严重），另一处轻快祝贺 (来源: writing.md › Getting started)
- 按紧急度与重要性、人看到消息的场景、是否需要立即行动、需要多少支持信息来**选择传达方式**（notification / alert / action sheet）并配相应语气 (来源: writing.md › Best practices)

**写作中禁止的替代做法**
- 帮人了解一个操作的说明不要用 popover 一词，也不要解释按钮；直接指具体任务或选择 (来源: popovers.md › Best practices)
- 不要用描述位置的方式引导人到设置，给直接入口 (来源: writing.md › Best practices)
- 注：原文中**没有** `Save changes` vs `Submit` 这一对具体措辞示例；本批文件给出的等价"反填充/动词优先"示例是 `Send` vs `Let's do it!`、`Learn more about UX Writing` vs `Click here`、`Unable to load content` vs `We're having trouble loading this content.`

---

### 4. 反馈与加载（feedback / loading / progress）

**反馈的用途与强度匹配**
- 反馈传达四件事：某事物的当前状态；重要任务或动作的成功/失败；对可能产生负面后果操作的警告；纠正错误或问题状况的机会 (来源: feedback.md › 开头)
- 强度匹配原则：反馈的传达方式要匹配信息的重要性 —— 状态信息宜**被动**显示供人按需查看；关于可能数据丢失的警告**必须打断**人 (来源: feedback.md › 开头)
- 反馈必须**可访问**：同时用颜色、文字、声音和触感，使人静音设备、移开视线或使用 VoiceOver 时都能收到 (来源: feedback.md › Best practices)
- 把状态反馈**整合进界面**：状态信息出现在它所描述的项目附近，人无需操作或离开当前上下文即可获得（Mail 在邮箱工具栏显示最近更新时间与未读数）(来源: feedback.md › Best practices)
- 用 alert 传达关键、且尽量**可操作**的信息；alert 会打断上下文，重要性必须匹配打断程度 (来源: feedback.md › Best practices)
- 动作或任务的**成功确认要稀缺**：人通常预期自己的操作会成功，因此一般只需知道**失败**时的情况；确认类反馈留给足够重要的活动 (来源: feedback.md › Best practices)
- 命令无法执行时必须显示并帮助理解原因 (来源: feedback.md › Best practices)

**加载的时间带（含原文措辞）**
- 黄金标准：`The best content-loading experience finishes before people become aware of it.` —— 最好的加载体验在人察觉之前就结束 (来源: loading.md › 开头)
- **立即**（理想）：内容直接显示，加载在人察觉前完成 (来源: loading.md › 开头)
- **"超过一两刻"**（`more than a moment or two`）→ 使用系统提供的 progress indicator (来源: loading.md › Showing progress) —— 注意：**本批文件未给出精确秒数阈值**（如 1 秒 / 10 秒），Apple 原文只给了这句定性措辞
- 知道要多久 → **determinate**；不知道 → **indeterminate** (来源: loading.md › Showing progress)
- **很长**时（不可避免的长加载）→ 给人可看的有趣内容：游玩提示、技巧、新功能介绍；并尽量准确估计剩余时间，避免占位内容"太短不够看"或"太长需要重复" (来源: loading.md › Best practices)
- 任何等待都必须**先显示东西**：若让人等到加载完成才看到任何内容，人会把空白理解为 app 有问题；改为显示占位文字、图形或动画，并在内容可用时替换 (来源: loading.md › Best practices)
- 加载期间**让人能做别的事**：后台加载内容；例：游戏在玩家了解下一关或查看游戏内菜单时后台加载 (来源: loading.md › Best practices)
- 大型资源**后台下载**以改善安装与启动时间：安排在安装后、更新期间或其他非打断时机（Background Assets）(来源: loading.md › Best practices)

**determinate vs indeterminate（含精确数值）**
- 可能时**优先用 determinate**：indeterminate 只表明"有进程在进行"，无法帮人估计时长；determinate 能帮人决定是否先做别的事、改时间重试、或放弃任务 (来源: progress-indicators.md › Best practices)
- determinate 的推进要**尽量准确并均匀**：`Showing 90 percent completion in five seconds and the last 10 percent in 5 minutes` 会让人怀疑 app 是否还在工作，甚至感觉受欺骗 (来源: progress-indicators.md › Best practices)
- 指示器**必须持续动**：静止的指示器会让人联想到进程停滞或 app 卡死；若进程确实停滞，提供帮人理解问题与可采取措施的反馈 (来源: progress-indicators.md › Best practices)
- 可能时**从 indeterminate 切换为 determinate**：一旦能确定时长就切 (来源: progress-indicators.md › Best practices)
- **禁止**从圆形样式切换到条形样式：activity indicator（spinner）与 progress bar 形状与尺寸不同，切换会破坏界面并让人困惑 (来源: progress-indicators.md › Best practices)
- 形状语义：determinate 进度条从 leading 向 trailing 填充；圆形 determinate 顺时针填充；indeterminate 用动画图像（spinner）；所有平台支持旋转的圆形图像，macOS 还支持不确定态进度条 (来源: progress-indicators.md › 开头)
- 可选说明文字要**准确简洁**：**避免** `loading`、`authenticating` 这类几乎不增加价值的模糊词 (来源: progress-indicators.md › Best practices)
- 进度指示器放在**一致的位置**，便于人跨平台、跨 app 可靠地找到状态 (来源: progress-indicators.md › Best practices)

**spinner vs 进度条**
- 后台操作状态、或**空间受限**时优先用 spinner：体积小不打扰，适合异步后台任务（如从服务器取消息），也适合在文本框内或某个控件旁边显示进度 (来源: progress-indicators.md › Desktop (macOS))
- **不要给旋转的进度指示器加标签**：spinner 通常出现在人发起进程时，标签一般多余 (来源: progress-indicators.md › Desktop (macOS))
- 已知时长 → 条形/圆形 determinate；未知时长 → spinner；**不要**在两者间切换样式 (来源: progress-indicators.md › Best practices)

**按钮内的加载反馈**
- 为"不立即完成"的操作提供反馈时，可在按钮内显示 activity indicator 以节省界面空间；可同时更换标签，例如 `Checkout` → `Checking out…` (来源: buttons.md › Mobile (iOS, iPadOS))
- 延迟发生时，系统会在原标签或替代标签旁显示指示器，并**隐藏按钮图像**（若有）(来源: buttons.md › Mobile (iOS, iPadOS))
- 自定义按钮**必须**有按下状态；缺少 press state 会让人怀疑输入未被接受 (来源: buttons.md › Best practices)

**占位 / skeleton 规则**
- 用占位文字、图形或动画充当骨架，随内容可用而替换 (来源: loading.md › Best practices)
- 启动时检测到问题（如无网络）可显示**缓存或占位数据** + 一个非侵入性标签描述问题，而不是弹 alert (来源: alerts.md › Best practices)

**中止与"不要阻塞"**
- 能让人中止就提供 **Cancel** 按钮；若中断可能有负面副作用（如丢失已下载的部分文件），可**同时**提供 Pause 与 Cancel (来源: progress-indicators.md › Best practices)
- 中止有负面后果时**必须**告知：取消会导致进度丢失时，用包含"确认取消"或"继续进程"选项的 alert (来源: progress-indicators.md › Best practices)
- **不要阻塞**：加载时让人能在 app 内做其他事；不要把整个界面卡在加载完成之后 (来源: loading.md › Best practices)
- iOS/iPadOS 的 refresh control：下拉视图时出现，用于立即重新加载内容；**不要**让人负责每次更新，仍需定期自动更新 (来源: progress-indicators.md › Mobile (iOS, iPadOS) › Refresh content controls)
- refresh control 的标题只在增加价值时提供：一般不需要（动画已表明在加载）；若提供，**不要**用它解释如何刷新，而要给出关于被刷新内容的有价值信息（Podcasts 显示上次更新时间）(来源: progress-indicators.md › Mobile (iOS, iPadOS) › Refresh content controls)
- 游戏可考虑自定义加载视图：标准进度指示器在多数 app 中好用，但在游戏里可能显得不搭 (来源: loading.md › Showing progress)
- **乐观 UI（optimistic UI）**：本批文件中**没有**对应规则；最接近的是"不要为常见且可撤销的操作弹确认"与"成功确认要稀缺" (来源: alerts.md › Best practices, feedback.md › Best practices)

---

### 5. 设置界面（settings）

**放在哪里**
- 系统 Settings app 承载全局设置（系统外观、网络、账号、无障碍、语言与地区），在部分平台也含各 app/游戏的设置（定位、麦克风/相机、通知/Siri/搜索集成）(来源: settings.md › 开头)
- 必要时才在 app 内提供**自定义设置区**，放影响整体体验的通用设置（界面风格、游戏存档行为）(来源: settings.md › 开头)
- 只影响某个**具体任务**的设置，应放在**任务内部**，让人不必离开当前体验去定制 (来源: settings.md › 开头)
- 通用、**极少更改**的设置才放自定义设置区：人必须中断当前操作才能打开设置区，所以只放不需要经常改的项（窗口配置、存档行为、键盘映射、账号相关）(来源: settings.md › General settings)
- 任务特定选项尽量**就地**修改，不要搬进设置区：显示/隐藏当前视图的某部分、重排集合、过滤列表等应放在受影响的界面中 —— 放设置区会脱离上下文、要求中断任务，且往往要回到任务后才能看到结果 (来源: settings.md › Task-specific options)
- 在系统 Settings app 中**只加最少更改**的选项；若合适，可在自己界面中提供**直接打开它**的按钮 (来源: settings.md › System settings)

**数量与默认值**
- 默认设置要为**最多人**提供最佳体验：例如自动为当前设备最大化性能，而不是启动后问玩家；好的默认值让人无需调整即可开始使用 (来源: settings.md › Best practices)
- **最小化设置数量**：设置太多会显得不友好，并且难以找到某一项 (来源: settings.md › Best practices)
- 不要用设置索取可自动获取的信息：自动检测已连接的手柄/配件、检测是否处于深色模式 (来源: settings.md › Best practices)
- **不要复制系统级设置**：无障碍、滚动行为、认证方式等全局选项应尊重系统设置；在自定义设置区做冗余版本会让人误以为系统设置对 app 不生效，或以为改自定义项会影响其他 app (来源: settings.md › Best practices)
- 按预期入口提供设置：接物理键盘时常用标准 `Command-逗号(,)` 打开 app 设置；游戏中玩家常用 `Esc` (来源: settings.md › Best practices)

**分组 / 标题 / 页脚**
- iOS/iPadOS 的 **grouped** 列表样式使用 **header、footer 和额外间距**来分隔数据组（这是设置类界面分组与说明文字的载体）(来源: lists-and-tables.md › Style)
- 分组与层级的一般手法：用负空间、背景形状、颜色、材质或**分隔线**表明元素相关性并分隔信息区域，同时确保内容与控件清晰可辨 (来源: layout.md › Best practices)
- 无法一次显示完的大集合要表明还有未显示项 —— 用披露控件，或显示条目的一部分暗示可滚动揭示 (来源: layout.md › Best practices)
- 高级设置用披露：把最可能用到的控件放在披露层级**顶部始终可见**，高级功能**默认隐藏**，并用描述性标签如 `Advanced Options` (来源: disclosure-controls.md › Best practices, disclosure-controls.md › Disclosure triangles)

**开关（switch）vs 下钻行**
- iOS/iPadOS：switch 样式**只用在列表行内**；此时不需要标签，因为行内内容已提供状态上下文 (来源: toggles.md › Mobile (iOS, iPadOS))
- iOS/iPadOS：**列表之外**用"行为像开关的按钮"，不要用 switch；此时避免给按钮加解释用途的标签，用界面图标与背景变化表达状态 (来源: toggles.md › Mobile (iOS, iPadOS))
- 开关颜色：默认绿色在多数场景合适；必要时可改用 app accent 色，但必须与"未着色外观"有足够对比以可感知 (来源: toggles.md › Mobile (iOS, iPadOS))
- 状态差异必须明显，且**不要只靠颜色**表达开关状态（不是所有人都能辨别颜色差异）(来源: toggles.md › Best practices)
- switch 用来让人在影响内容或视图状态的**两个对立值**之间选择；若需要从列表中选择，改用 pop-up button 等组件 (来源: toggles.md › Best practices)
- 需呈现设置**层级**时用 checkbox 而不是 switch：靠对齐（一般沿 checkbox 的 leading 边缘）与**缩进**表达依赖关系（如某 checkbox 的状态决定下级 checkbox 的状态）(来源: toggles.md › Desktop (macOS) › Checkboxes)
- 想**强调**的设置优先用 switch（视觉重量大于 checkbox，适合控制更多功能，如一次开关一组设置）；分组表单内控制单行设置可考虑 **mini switch** 以保持行高一致；层级设置可用 regular switch 作主设置 + mini switches 作从属设置 (来源: toggles.md › Desktop (macOS) › Switches)
- macOS 上一般**不要**把已有 checkbox 换成 switch (来源: toggles.md › Desktop (macOS) › Switches)
- macOS：switch、checkbox、radio button 放在**窗口主体**，不要放窗口框架（尤其避免工具栏或状态栏）(来源: toggles.md › Desktop (macOS))
- 超过 2 个互斥选项考虑用 radio buttons（通常 **2–5 个**一组）；**超过约 5 个**选项改用 pop-up button；需要多选则用 checkbox (来源: toggles.md › Desktop (macOS) › Radio buttons, toggles.md › Desktop (macOS) › Checkboxes)
- 单选按钮水平排列时用**一致间距**：以最长标签所需空间为准并统一使用该测量值 (来源: toggles.md › Desktop (macOS) › Radio buttons)
- 混合状态要如实反映：checkbox 状态可为 on / off / **mixed**；用 checkbox 全局开关多个下级 checkbox 时，下级状态不一致要显示 mixed (来源: toggles.md › Desktop (macOS) › Checkboxes)
- 用**下钻行**表达层级导航：需要让人钻进某一行的子视图时用 disclosure indicator（accessory control），而 **info button（detail disclosure button）只用于揭示该行内容的更多信息**、不支持层级导航 (来源: lists-and-tables.md › Mobile (iOS, iPadOS, visionOS))
- 有披露指示器等控件位于行尾时，**不要**再加 alphabet index（两者都在 trailing 侧，会互相误触）(来源: lists-and-tables.md › Mobile (iOS, iPadOS, visionOS))

**什么时候需要解释文字**
- 标签不够用就**加说明**：说明只描述"开启时"的行为，人可以推断相反情况 (来源: writing.md › Best practices)
- 需要在设置里引导人到别处时，给**直接链接或按钮**，不要描述位置 (来源: writing.md › Best practices)
- 设置窗口（macOS）：工具栏**不可自定义**、始终可见，并始终指示当前活动的工具栏按钮 (来源: settings.md › Desktop (macOS))
- macOS：窗口标题**更新为当前可见窗格**；若没有多个窗格，标题用 `App Name Settings` (来源: settings.md › Desktop (macOS))
- macOS：**恢复最近查看的窗格**（人常会多次调整相关设置）(来源: settings.md › Desktop (macOS))
- macOS：把设置窗口的**最小化与最大化按钮变暗**；因为可用标准 `Command-逗号(,)` 快速打开，无需留在 Dock，且窗口会适配当前窗格大小 (来源: settings.md › Desktop (macOS))
- macOS：设置入口放在 **App 菜单**；**避免**把设置按钮加到窗口工具栏（会挤占常用命令空间）；文档级选项放 File 菜单 (来源: settings.md › Desktop (macOS))
- macOS：帮助按钮位置 —— 有dismissal 按钮的对话框中放在与它们相对的**下角**并与它们垂直对齐；无 dismissal 按钮的对话框、设置窗口或窗格放在**左下角或右下角**；每个窗口**最多一个**帮助按钮 (来源: buttons.md › Desktop (macOS) › Help buttons)
- **About / 版本号位置、设置项排列顺序**：本批文件**未给出**规则；settings.md 只给了 macOS 的窗格标题与"恢复最近窗格"约定。需要 About/版本位置时须另查 Apple 其他页面。

---

### 6. 手势（gestures）

**标准手势与固定含义（不可随意改写）**
- 手势 = 人用来**直接影响**设备上 app 或游戏对象的身体动作；可在触屏、空中或各种输入设备（触控板、鼠标、遥控器、带触控面的手柄）上做 (来源: gestures.md › 开头)
- `tap`、`swipe`、`drag` 在所有平台都支持；具体动作可因平台与输入设备不同，但人对底层功能熟悉并**期望到处都能用** (来源: gestures.md › 开头)
- Tap → 激活控件、选择项目 (来源: gestures.md › Specifications › Standard gestures)
- Swipe → 揭示操作与控件、**关闭视图**、滚动 (来源: gestures.md › Specifications › Standard gestures)
- Drag → 移动 UI 元素 (来源: gestures.md › Specifications › Standard gestures)
- Touch（或 pinch）and hold → 揭示额外控件或功能 (来源: gestures.md › Specifications › Standard gestures)
- Double tap → 放大；已放大时缩小；在 Apple Watch Series 9 与 Apple Watch Ultra 2 上执行主要操作 (来源: gestures.md › Specifications › Standard gestures)
- Zoom → 缩放视图、放大内容；Rotate → 旋转所选项目 (来源: gestures.md › Specifications › Standard gestures)
- iOS/iPadOS 附加预期手势：**三指左滑 = 撤销**；**三指右滑 = 重做**；**三指捏合 = 拷贝所选文本**（捏合收拢）/ **捏合张开 = 粘贴**；**四指滑动（仅 iPadOS）= 切换 app**；**摇动 = 撤销/重做** (来源: gestures.md › Mobile (iOS, iPadOS))
- macOS：主要用键盘和鼠标；也可在 Magic Trackpad、Magic Mouse 或带触控面的手柄上做标准手势 (来源: gestures.md › Desktop (macOS))

**禁止覆盖 / 冲突的手势**
- 避免用**熟悉手势**去执行 app 独有动作，也避免**自造手势**去完成标准动作（激活按钮、滚动长视图）(来源: gestures.md › Best practices)
- 人期望多数手势**不随上下文变化**：例如 tap 应激活或选择对象 (来源: gestures.md › Best practices)
- **不要重定义 undo/redo 的标准手势**：三指滑动、摇动 iPhone 都属标准手势，重定义会造成困惑并让体验不可预测 (来源: undo-and-redo.md › Mobile (iOS, iPadOS))
- **不要与访问系统 UI 的手势冲突**：各平台有用系统行为的手势（watchOS 边缘滑动、visionOS 翻手访问系统浮层）；人期望这些控件一致工作；仅在游戏或沉浸体验的特定情形下可延迟系统手势 (来源: gestures.md › Custom gestures)
- 快捷手势**只补充不替代**标准手势：即使多一两次点击，也要保留简单熟悉的方式 —— 层级导航中人期望顶部工具栏的 **Back 按钮单击返回上一视图**，很多 app 额外提供"从窗口或触屏边缘滑动"的手势加速，同时**保留 Back 按钮** (来源: gestures.md › Custom gestures)
- 常见交互用**最简单**的手势：避免自定义多指、多手手势，使重复动作既舒适又好记 (来源: accessibility.md › Mobility)
- 为手势提供**屏幕上的等价操作**：不能假设人能用某个特定手势完成任务；例如用滑动手势关闭视图时，同时提供按钮，让人可点击或使用辅助设备 (来源: accessibility.md › Mobility, gestures.md › Best practices)

**swipe-to-go-back 与行内滑动**
- iOS 上要支持"滑动返回或滑动触发列表行操作"：控件位于屏幕**中部或底部**时更容易、更舒适地够到，因此这尤其重要 (来源: designing-for-ios.md › Best practices)
- 边缘滑动手势是 Back 按钮的**加速补充**，Back 按钮必须保留 (来源: gestures.md › Custom gestures)
- 列表/集合中的滑动：集合默认支持"轻点选择、**触摸并按住编辑**、滑动滚动"；如需可加自定义手势 (来源: collections.md › Best practices)
- 列表行选择反馈：用于**层级导航**的表格要**持续高亮**选中行以说明所处路径；用于**列选项**的表格通常只短暂高亮，随后加勾号等图像表示已选中 (来源: lists-and-tables.md › Best practices)
- iOS/iPadOS 中，人**必须先进入编辑模式**才能选择表格项 (来源: lists-and-tables.md › Best practices)
- 行内滑动操作（swipe actions）的**按钮数量/颜色/图标规范：本批文件未给出**；最接近的是"滑动的标准含义包含揭示操作与控件/关闭视图" (来源: gestures.md › Specifications › Standard gestures)

**长按 / 3D Touch 替代**
- 固定含义是 **Touch（或 pinch）and hold → 揭示额外控件或功能**（长按用于暴露更多功能，而非执行主操作）(来源: gestures.md › Specifications › Standard gestures)
- 集合中的默认长按行为 = **进入编辑** (来源: collections.md › Best practices)
- **3D Touch 的替代映射：本批文件没有相关内容**；`gestures.md` 未提及 3D Touch 或 Haptic Touch，需另查 context menus 等页面

**自定义手势的准入条件**
- 只在**必要时**添加：适合人**频繁执行**且现有手势未覆盖的专业任务（游戏或绘图 app）(来源: gestures.md › Custom gestures)
- 必须同时满足**四条**：①可发现（Discoverable）②易于执行（Straightforward to perform）③与其他手势可区分（Distinct from other gestures）④**不是完成重要动作的唯一方式**（Not the only way to perform an important action）(来源: gestures.md › Custom gestures)
- 要**易于学习**：在 app 中提供学习时机，并在真实使用场景中测试；若难以用简单语言和图形描述该手势，说明人也会觉得难以学习和执行 (来源: gestures.md › Custom gestures)

**可发现性与反馈**
- 手势要**尽可能响应及时**：提供帮助人预测结果的反馈，必要时传达"完成该动作所需的移动范围与类型" (来源: gestures.md › Best practices)
- 手势**不可用时必须明确告知**：否则人会以为 app 卡死或自己做错了 —— 例如拖动锁定对象时 UI 未表明位置已锁定，或不可用按钮与可用状态没有明显区别 (来源: gestures.md › Best practices)
- 同时识别多手势：非游戏 app 中同时手势**不太可能有用**；游戏可能同时有多个屏幕控件（摇杆 + 开火按钮）需同时操作 (来源: gestures.md › Mobile (iOS, iPadOS))
- 命中区域与最小尺寸：控件默认 **44x44 pt**、最小 **28x28 pt**（iOS/iPadOS）；macOS 默认 **28x28 pt**、最小 **20x20 pt** (来源: accessibility.md › Mobility)
- 按钮命中区域至少 **44x44 pt**（visionOS **60x60 pt**）(来源: buttons.md › Best practices)
- 控件之间的间距与尺寸同等重要：带 bezel 的元素周围约 **12 pt** 内边距；无 bezel 的元素在可见边缘外约 **24 pt** 内边距 (来源: accessibility.md › Mobility)

---

### 7. iOS 平台总览（designing-for-ios + layout/search 支撑）

**设备特征**
- 显示：iPhone 是**中等尺寸、高分辨率**显示屏 (来源: designing-for-ios.md › 开头)
- 人体工学：人通常**单手或双手**握持，按需在横竖屏间切换；交互时视距一般**不超过一到两英尺**（`no more than a foot or two`）(来源: designing-for-ios.md › 开头)
- 输入：Multi-Touch 手势、虚拟键盘、语音（Siri）让人在移动中完成任务；人常希望 app 使用个人数据以及**陀螺仪和加速度计**输入，并可能想参与空间交互 (来源: designing-for-ios.md › 开头)
- 会话时长分布：有时只花**一两分钟**查看事件/社交更新、跟踪数据或发消息；有时花**一小时以上**浏览网页、玩游戏或看媒体；人通常同时开着多个 app 并频繁切换 (来源: designing-for-ios.md › 开头)
- 系统功能入口：Widgets、Home Screen 快速操作、Spotlight、Shortcuts、Activity views (来源: designing-for-ios.md › 开头)

**布局与视觉层级**
- 限制屏上控件数量，让人专注主要任务与内容；次要细节与操作以**最小交互量**可被发现 (来源: designing-for-ios.md › Best practices)
- 内容要**铺满**：背景与全屏美术延伸到显示边缘；可滚动布局一直延续到屏幕底部和两侧；侧边栏、标签栏等控件浮在内容**之上**而非同一平面，布局必须考虑这一点 (来源: layout.md › Best practices)
- 用负空间、背景形状、颜色、材质或分隔线表达相关性并划分信息区域，同时保证内容与控件清晰可辨 (来源: layout.md › Best practices)
- 给关键信息足够空间：不要用非必要细节挤占，次要信息放到窗口其他区域或另一个视图 (来源: layout.md › Best practices)
- 阅读顺序：人通常自上而下、从 leading 到 trailing 查看，因此把最重要项放在**顶部与 leading 侧**；注意阅读顺序随语言变化，需考虑 RTL (来源: layout.md › Best practices)
- 对齐与缩进用于表达组织与层级，帮助人在滚动时跟踪内容 (来源: layout.md › Best practices)
- 渐进披露：无法一次显示大集合时，必须表明还有未显示项 —— 用披露控件，或显示条目的一部分暗示可滚动揭示 (来源: layout.md › Best practices)
- 控件周围留足空间并把它们按逻辑分区，否则控件难以区分、用途难懂 (来源: layout.md › Best practices)
- **避免全宽按钮**：iOS 按钮应尊重系统定义边距并内缩于屏幕边缘；若必须全宽，要与硬件曲率协调并对齐相邻安全区 (来源: layout.md › Phone (iOS))
- 按钮间距与区分度：按钮周围要有足够空间以便与周围组件和内容区分 (来源: buttons.md › Best practices)
- 每个视图的**突出（prominent）按钮限 1–2 个**；过多突出按钮会增加认知负担、让人花更多时间权衡 (来源: buttons.md › Style)
- 用**样式而非尺寸**区分首选选项：同尺寸按钮表示它们构成一组并列选项；两个不同尺寸按钮相邻会显得混乱不一致 (来源: buttons.md › Style)
- 不要给按钮标签与内容层背景使用相近颜色（内容层已经鲜艳时优先用默认单色按钮标签）(来源: buttons.md › Style)

**安全区与边距**
- safe area = 视图中**不被**工具栏、标签栏或其他窗口提供的视图覆盖的区域；安全区对避开设备的交互与显示特性（iPhone 灵动岛、部分 Mac 的摄像头区域）至关重要 (来源: layout.md › Guides and safe areas)
- 系统预定义 layout guide 用于应用**标准边距**并**限制文本宽度**以获得最佳可读性；也可自定义 (来源: layout.md › Guides and safe areas)
- 适配方式：尊重系统定义的 safe area、边距与 guides，并用布局修饰符微调视图放置 (来源: layout.md › Adaptability)
- 安全区还能帮助处理交互组件（如各种栏），在尺寸变化时动态重新定位内容 (来源: layout.md › Guides and safe areas)

**方向与状态栏**
- 尽量**同时支持竖屏与横屏**；若只能支持一种，可以依赖人会自行尝试两种方向，**无需**提示人旋转设备；若是**仅横屏**，要保证人向左或向右旋转设备都同样好用 (来源: layout.md › Phone (iOS))
- 游戏优先**全出血**界面：填满屏幕并兼顾圆角、传感器开孔与灵动岛；必要时可提供 letterbox 或 pillarbox 选项 (来源: layout.md › Phone (iOS))
- 状态栏**只在增加价值或增强体验时**隐藏：它显示有用信息且占据多数 app 未充分利用的区域；例外是游戏或看媒体这类深度体验 (来源: layout.md › Phone (iOS))
- 适配清单（需处理的变化）：不同屏幕尺寸/分辨率/色彩空间、设备方向、灵动岛与相机控制等功能、外接显示与 Display Zoom 与 iPad 可调整窗口、Dynamic Type 字号变化、语言环境特性（LTR/RTL、日期时间数字格式、字体变化、文本长度）(来源: layout.md › Adaptability)
- 支持 Dynamic Type，让 app 响应字号变化；先在**最大与最小**布局上测试，再用模拟器验证横屏左右两个方向是否都无裁切 (来源: layout.md › Adaptability)
- 无缝适配外观变化：设备方向、深色模式、Dynamic Type，让人选择最适合自己的配置 (来源: designing-for-ios.md › Best practices)

**尺寸类与点尺寸（数值）**
- size class：**regular** = 较大屏幕或横屏；**compact** = 较小屏幕或竖屏 (来源: layout.md › iOS, iPadOS device size classes)
- iPhone **竖屏** = compact width + regular height（所有机型）(来源: layout.md › iOS, iPadOS device size classes)
- iPhone **横屏**：Pro Max / Plus / Air 等大屏机型 = regular width + compact height；非 Max 的 iPhone（如 iPhone 17 Pro、iPhone 16、SE）= compact width + compact height (来源: layout.md › iOS, iPadOS device size classes)
- 竖屏点尺寸样例：iPhone 17 Pro Max **440x956 pt**；iPhone 17 Pro / iPhone 17 **402x874 pt**；iPhone Air **420x912 pt**；iPhone 16 **393x852 pt**；iPhone 16e **390x844 pt**；iPhone 13 mini / 12 mini **360x780 pt**；iPhone SE 4.7" **375x667 pt**；iPhone SE 4" **320x568 pt** (来源: layout.md › iOS, iPadOS device screen dimensions)
- iPad 竖屏点尺寸样例：iPad Pro 13" **1032x1376 pt**；iPad Pro 12.9" **1024x1366 pt**；iPad Air 11" / iPad 11" **820x1180 pt**；iPad mini 8.3" **744x1133 pt** (来源: layout.md › iOS, iPadOS device screen dimensions)

**单手可达性**
- 控件位于屏幕**中部或底部**时更容易、更舒适地够到；因此要特别支持"滑动返回"和"在列表行中滑动触发操作" (来源: designing-for-ios.md › Best practices)
- 相应地，避免把关键操作埋在需要精确点按的顶部角落（由上述可达性结论推导的原文直接依据：中部/底部更易够到）(来源: designing-for-ios.md › Best practices)

**最小点击目标**
- iOS/iPadOS 控件默认 **44x44 pt**，最小 **28x28 pt**；macOS 默认 **28x28 pt**，最小 **20x20 pt** (来源: accessibility.md › Mobility)
- 按钮命中区域至少 **44x44 pt**（visionOS 60x60 pt），无论用手指、指针、眼睛还是遥控器选择 (来源: buttons.md › Best practices)
- 控件之间的间距与尺寸同等重要：带 bezel 约 **12 pt** 内边距；无 bezel 约 **24 pt** 内边距 (来源: accessibility.md › Mobility)

**导航模型**
- 用**列表/表格的层级**表达整体信息架构：iOS Settings 用列表层级帮人选择选项 (来源: lists-and-tables.md › 开头)
- 层级导航的反馈规则：用于导航层级的表格**持续高亮**选中行以澄清人所走的路径；用于列选项的表格只短暂高亮后加勾号 (来源: lists-and-tables.md › Best practices)
- 钻进行的子视图用 **disclosure indicator**；**info button** 只用于揭示该行内容的更多信息，**不支持**层级导航 (来源: lists-and-tables.md › Mobile (iOS, iPadOS, visionOS))
- 层级导航中，人期望顶部工具栏有 **Back 按钮**可**单击**返回上一视图；边缘滑动只是加速补充 (来源: gestures.md › Custom gestures)
- 表格项选择在 iOS/iPadOS 需先进入**编辑模式** (来源: lists-and-tables.md › Best practices)
- 层级数据用 outline view（带披露三角）而不是普通表格 (来源: lists-and-tables.md › Desktop (macOS))

**搜索入口（iOS 相关）**
- 若搜索重要，给它**主位置**：Notes 把搜索框放在**底部工具栏**与其他重要操作并列；使用标签栏的 app（照片、Apple TV）把搜索做成**独立标签** (来源: searching.md › Best practices)
- 尽量让 app 内容通过**单一位置**可搜索；有明显分区时可另提供局部搜索（iOS 音乐中搜索作为当前视图的过滤器）(来源: searching.md › Best practices)
- 清楚显示当前搜索**范围**：用描述性占位文本、scope bar 或标题（Mail 总显示正在搜索的邮箱）(来源: searching.md › Best practices)
- 提供搜索建议：输入前显示**最近搜索**，输入中给**预测建议**，帮人更快搜索、少打字 (来源: searching.md › Best practices)
- 显示搜索历史前考虑**隐私**：历史可能被他人看到；若显示，必须提供清除方式 (来源: searching.md › Best practices)
- 搜索框组成：Search 图标、Clear 按钮、占位文本；可用 scope bar 与 token 过滤范围 (来源: search-fields.md › 开头)
- Spotlight：让内容可索引并提供元数据；为自定义文件类型定义元数据；可在 app 内放一个按钮直接发起基于当前选择的 Spotlight 搜索并展示结果或过滤子集；优先使用系统提供的打开/保存视图（自带搜索框）；为自定义文件类型实现 Quick Look 生成器 (来源: searching.md › Systemwide search)

---

**已知缺口（原文无对应规则，避免臆造）**：精确的加载秒数分带阈值（原文仅 `more than a moment or two`）；sheet 圆角具体数值；skeleton/占位内容的显示时长阈值；乐观 UI；设置界面中 About/版本号的位置与设置项排列顺序；行内 swipe actions 的按钮数量与颜色规范；3D Touch 的替代映射；`Save changes` vs `Submit` 这一对具体措辞示例（原文给出的是 `Send` vs `Let's do it!`、`Click here` vs `Learn more about UX Writing`）。
