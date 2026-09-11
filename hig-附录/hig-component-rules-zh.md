# Apple HIG 组件规则提取（可执行规格）

来源：`D:\Temp\apple-design-skill\references\hig\` 下 11 个已下载的 HIG 页面，逐字通读后提取。
覆盖平台：iOS / iPadOS / macOS（tvOS、visionOS、watchOS 专属段落已在这些文件中被移除；句子级提及保留）。

> **数值说明（先读）**：这 11 个文件是 Apple 现版 HIG 的忠实渲染，**本身几乎不含尺寸数值**。全批文件中出现的绝对数值只有三处：`44x44 pt`（按钮点击区，visionOS 为 `60x60 pt`）、`10 pixels`（macOS 图像按钮内边距）、以及进度指示器示例中的秒数/百分比。**49pt 标签栏高度、8pt/16pt 侧边距、行高、section header 高度、圆角半径、图标尺寸在这 11 个文件中均不存在**——不要臆造。文末「数值缺口与外部补充」给出同目录中真正写着这些数值的文件与章节。

---

### 列表与表格（Lists and tables）

- **用途** — 列表/表格以「一行或多列」呈现数据，适合表达分组与层级，并支持选择、添加、删除、重排序 (来源: lists-and-tables.md › 开篇)
- **选型：文本 vs 集合** — 行式格式最适合文本扫描阅读；若条目尺寸差异很大，或需要展示大量图片，改用 collection (来源: lists-and-tables.md › Best practices)
- **选型：多列表格 vs 单列列表** — 生产力类任务、需要分别排序的多列属性用多列表格（multicolumn table）(来源: lists-and-tables.md › 开篇)
- **选型：表格 vs 大纲视图（macOS）** — 层级数据用 outline view，不要用 table view；outline view 外观像表格但带 disclosure 三角 (来源: lists-and-tables.md › Desktop (macOS))
- **编辑** — 允许编辑表格（即使不能增删，也让人可重排）；iOS/iPadOS 中「people must enter an edit mode before they can select table items」——选择前必须先进入编辑模式 (来源: lists-and-tables.md › Best practices)
- **选择反馈（两种模式）** — 层级导航型表格：持久高亮选中行，以显示路径；选项型表格：短暂高亮后显示勾选标记（checkmark）表示已选 (来源: lists-and-tables.md › Best practices)
- **文本长度** — 行文本保持简短以减少截断与换行；若每项文本量很大，改为只列标题 + 详情视图 (来源: lists-and-tables.md › Content)
- **截断策略** — 窄表格优先考虑把省略号放在文本**中间**（`an ellipsis in the middle of text`），保留开头与结尾，比尾部截断更易区分 (来源: lists-and-tables.md › Content)
- **列标题** — 多列表格必须用描述性列标题：名词或短名词短语、title-style capitalization、结尾不加标点；单列表格若无列标题，用 label 或 header 补上下文 (来源: lists-and-tables.md › Content)
- **行样式** — 按内容选行样式，例如行首（leading）小图片 + 简短说明标签；iOS/iPadOS/tvOS 用 `UIListContentConfiguration` 排布行、header、footer (来源: lists-and-tables.md › Style)
- **样式分组语义** — iOS/iPadOS 的 grouped 样式用 header、footer 和**额外间距**分隔数据组；macOS 的 bordered 样式用**交替行背景**帮助大表格使用；watchOS 有 elliptical 样式 (来源: lists-and-tables.md › Style)
- **附件控件（iOS/iPadOS）** — info button（在列表行中称 detail disclosure button）**只用于**揭示该行内容的更多信息，**不支持**层级导航；要下钻子视图必须用 disclosure indicator (来源: lists-and-tables.md › Mobile (iOS, iPadOS))
- **索引冲突（禁止项）** — 行尾部已有控件（如 disclosure indicator）时**不要**再加 alphabet 索引；索引与附件都在尾部，会导致误触 (来源: lists-and-tables.md › Mobile (iOS, iPadOS))
- **macOS 列排序** — 点击列标题按该列排序；再次点击同一列则反向重排 (来源: lists-and-tables.md › Desktop (macOS))
- **macOS 列宽** — 允许用户调整列宽，以聚焦不同区域或显示被裁剪数据 (来源: lists-and-tables.md › Desktop (macOS))
- **macOS 交替行色** — 多列表格考虑交替行颜色，便于跨列追踪行值 (来源: lists-and-tables.md › Desktop (macOS))
- **尺寸/间距** — 本文件未给出任何 pt/px 数值：无行高、无行内边距、无侧边距、无图标尺寸、无 section header 高度 (来源: lists-and-tables.md › 全文)

### 集合（Collections）

- **用途** — 管理有序内容集，以可定制、高度视觉化的布局展示；「generally speaking, collections are ideal for showing image-based content」——以图像为主的内容 (来源: collections.md › 开篇)
- **选型：集合 vs 列表/表格** — 展示**文本**用可滚动列表更简单高效；集合留给图像型内容 (来源: collections.md › Best practices)
- **布局** — 默认横向单行（horizontal row）或网格（grid），「Use the standard row or grid layout whenever possible」；避免自定义布局 (来源: collections.md › Best practices)
- **内边距** — 给图像足够 padding（`adequate padding`），使 focus/hover 效果可见并防止内容重叠；文件未给出具体 pt 数值 (来源: collections.md › Best practices)
- **默认手势** — 点按选择（tap to select）、触摸并按住编辑（touch and hold to edit）、滑动滚动（swipe to scroll）；仅在需要时增加自定义手势 (来源: collections.md › Best practices)
- **增删改动画** — 插入、删除、重排时用动画提供反馈（系统支持标准动画，也可自定义）(来源: collections.md › Best practices)
- **动态布局（iOS/iPadOS 禁止项）** — 布局可动态变化，但避免在用户正在查看/交互时改变，除非是响应明确动作；变化必须易于追踪 (来源: collections.md › Mobile (iOS, iPadOS))
- **平台** — watchOS 不支持；macOS、tvOS、visionOS 无额外注意事项 (来源: collections.md › Platform considerations)
- **尺寸/间距** — 本文件未给出任何数值（无单元格尺寸、无间距、无列数上限）(来源: collections.md › 全文)

### 标签（Labels）

- **用途** — 静态、可读、**不可编辑**的文本；出现在按钮内（Edit、Cancel、Send）、列表项描述（常配符号或图片）、视图中介绍控件或描述常见任务 (来源: labels.md › 开篇)
- **选型：label vs text field vs text view** — 少量且**不需要**编辑的文本 → label；少量但**需要**编辑 → text field；大量文本（可选可编辑）→ text view (来源: labels.md › Best practices)
- **字体** — 优先系统字体；label 默认支持 Dynamic Type（可用处）；调样式或换自定义字体时必须保证可读 (来源: labels.md › Best practices)
- **颜色层级（四级系统标签色，共 4 种）** — Label = 主要信息；Secondary label = 副标题/补充文本；Tertiary label = 描述不可用项或行为；Quaternary label = 水印文本 (来源: labels.md › Best practices)
- **颜色 API 名称** — iOS/iPadOS/tvOS/visionOS：`label`、`secondaryLabel`、`tertiaryLabel`、`quaternaryLabel`；macOS：`labelColor`、`secondaryLabelColor`、`tertiaryLabelColor`、`quaternaryLabelColor` (来源: labels.md › Best practices)
- **选择/复制** — 含有用信息的标签（错误信息、位置、IP 地址）应允许选中并复制 (来源: labels.md › Best practices)
- **macOS 实现** — 用 `NSTextField` 的 `isEditable` 属性显示不可编辑文本 (来源: labels.md › Desktop (macOS))
- **尺寸/间距** — 本文件未给出字号、行高、内边距数值；字号规范指向 typography.md（不在本次清单内）(来源: labels.md › 全文)

### 滚动视图（Scroll views）

- **用途/外观** — scroll view 本身没有外观；可显示半透明 `scroll indicator`，通常在用户开始滚动后才出现，用于提示当前可见内容处于开头/中部/结尾 (来源: scroll-views.md › 开篇)
- **手势** — 支持系统默认滚动手势与键盘快捷键；自建滚动时指示器仍需使用用户预期的 elastic（回弹）行为 (来源: scroll-views.md › Best practices)
- **可滚动性可见** — 在视图边缘露出部分内容（partial content）以暗示该方向还有内容 (来源: scroll-views.md › Best practices)
- **嵌套（禁止项）** — 不要在同方向上嵌套 scroll view；横向嵌纵向（或反向）是允许的 (来源: scroll-views.md › Best practices)
- **分页滚动** — 页大小（page）通常取当前视图高或宽；可定义一个重叠单元（一行文字、一行字形、图片的一部分）并从页大小中减去，以维持上下文 (来源: scroll-views.md › Best practices)
- **自动滚动（4 个允许场景）** — ① 应用选中了当前不可见的内容或把插入点放到隐藏区域；② 用户在不常可见的位置开始输入（如插入点在另一页）；③ 指针在选择过程中越过视图边缘（朝指针方向跟随滚动）；④ 用户先选择再滚走，执行操作前把选择滚回可见 (来源: scroll-views.md › Best practices)
- **自动滚动幅度** — 只滚动「刚好足够」让用户保持上下文的距离；若选择的一部分已可见，不必把整个选择滚入视野 (来源: scroll-views.md › Best practices)
- **缩放** — 若支持缩放，必须设定合理的最小/最大 scale（例如放大到单个字符充满屏幕通常不合理）(来源: scroll-views.md › Best practices)
- **滚动边缘效果：样式** — 优先使用默认的 `automatic`（更不透明的分隔，适合顶部工具栏控件多、Liquid Glass 控件之外的文本、pinned table headers）；若改用 `soft` 样式，必须充分测试各种场景下控件的可读性 (来源: scroll-views.md › Scroll edge effects)
- **滚动边缘效果：使用条件（禁止项）** — 只在滚动视图位于「浮动界面元素」之后时使用；它不是装饰，不遮挡也不变暗，只为保证控件视觉可区分 (来源: scroll-views.md › Scroll edge effects)
- **滚动边缘效果：数量** — `Apply one scroll edge effect per view`，每个视图一个；iPad/Mac 分栏布局中每个 pane 可有各自的效果，但**高度必须保持一致**以维持对齐 (来源: scroll-views.md › Scroll edge effects)
- **iOS/iPadOS 分页 + 页码控件** — 分页模式下可显示 page control（如 Weather 的地点切换）；若显示 page control，**不要在同一轴上再显示滚动指示器**（冗余会让用户困惑）(来源: scroll-views.md › Mobile (iOS, iPadOS))
- **macOS 术语与尺寸** — macOS 中 scroll indicator 称 scroll bar；空间紧张的面板可用 small 或 mini 滚动条，但**同一面板内所有控件必须使用同一尺寸** (来源: scroll-views.md › Desktop (macOS))
- **尺寸/间距** — 本文件未给出滚动条粗细、边缘效果高度、惯性参数数值（仅要求分栏间高度一致）(来源: scroll-views.md › 全文)

### 标签栏（Tab bars）

- **用途 vs 工具栏** — tab bar 用于在**顶层区域之间导航**，「not to provide actions」；作用于当前视图内元素的控件用 toolbar (来源: tab-bars.md › Best practices)
- **用途 vs 分段控件** — 在**完全独立的应用区域**之间切换用 tab bar；紧密相关的子视图之间切换用 segmented control (来源: segmented-controls.md › Mobile (iOS, iPadOS)；tab-bars.md › Best practices)
- **可见性** — 导航到不同区域时 tab bar 必须保持可见；隐藏会让用户忘记自己在哪；唯一例外是被模态视图覆盖（模态是临时且自包含的）(来源: tab-bars.md › Best practices)
- **数量（核心数字）** — 用「刚好够导航」的标签数；标签越少越易导航；让用户自定义标签时，默认列表 `aim for a default list of five or fewer to preserve continuity between compact and regular view sizes`——**默认 ≤5 个** (来源: tab-bars.md › Tablet (iPadOS))
- **数量：溢出机制** — 受设备尺寸与方向限制，可见标签可能少于总数；水平空间不足时**尾部标签变成 More 标签**（iOS、iPadOS），其余项藏在单独列表里；More 标签使隐藏内容更难被到达和注意，因此限制这种情况发生 (来源: tab-bars.md › Best practices)
- **复杂信息结构替代方案** — 优先减少标签数；结构复杂时考虑 sidebar，或使用「可转换为 sidebar 的 tab bar」 (来源: tab-bars.md › Best practices；tab-bars.md › Tablet (iPadOS))
- **禁止项：禁用/隐藏标签按钮** — 「Don't disable or hide tab bar buttons, even when their content is unavailable」；时有时无会让界面显得不稳定、不可预测；区域为空时必须解释原因 (来源: tab-bars.md › Best practices)
- **标签文案** — 必须有标签（label 在图标下方或旁边）；「Use single words whenever possible」——尽量用**单个词** (来源: tab-bars.md › Best practices)
- **图标** — 用 SF Symbols 以获得熟悉、可缩放的图标并自动适配上下文；tab bar 可为 regular 或 compact（随设备与方向变化）：**compact 时图标在标签上方，regular 时图标与标签并排**；优先 filled（实心）符号以与平台一致 (来源: tab-bars.md › Best practices)
- **自定义图标尺寸** — 自行设计图标时，尺寸查阅 Apple Design Resources（本文件不给数值） (来源: tab-bars.md › Best practices)
- **Badge** — badge 是「红色椭圆 + 白字」，内容为数字或感叹号，表示该区域有新的/更新的关键信息；只用于关键信息以免稀释其分量 (来源: tab-bars.md › Best practices)
- **颜色/材质（禁止项）** — 避免标签文字与**内容层背景**使用相近颜色；内容层已经鲜艳时，tab bar 用单色（monochromatic）外观，或选对比足够强的 accent color (来源: tab-bars.md › Best practices)
- **iOS 位置与材质** — tab bar 浮在内容之上、位于**屏幕底部**；其项目落在 Liquid Glass 背景上，允许下方内容透出 (来源: tab-bars.md › Phone (iOS))
- **iOS 最小化行为** — 带附属控件（如 Music 的 MiniPlayer）时，可在用户**向下滚动**时最小化 tab bar 并把附属控件内联进来；用户点任一标签或滚回顶部即可退出最小化 (来源: tab-bars.md › Phone (iOS))
- **iOS 搜索标签** — 可在**尾部（trailing）**放置一个专门的 search tab (来源: tab-bars.md › Phone (iOS))
- **iPadOS 位置** — 系统把 tab bar 显示在**屏幕顶部附近**；可固定为固定元素，或提供按钮将其转换为 sidebar（`tabBarOnly` / `sidebarAdaptable`）(来源: tab-bars.md › Tablet (iPadOS))
- **iPadOS 优先项** — 导航优先用 tab bar（提供最常用区域的入口）；更复杂的应用再提供转 sidebar 的选项 (来源: tab-bars.md › Tablet (iPadOS))
- **iPadOS 不要用 tab view 冒充纯 sidebar** — 若只想呈现 sidebar 而不提供转 tab bar 的选项，用 navigation split view 而不是 tab view (来源: tab-bars.md › Tablet (iPadOS))
- **平台** — watchOS 不支持；macOS 无额外注意事项 (来源: tab-bars.md › Platform considerations)
- **缺口数值** — 本文件**没有**给出标签栏高度（常见引用的 49pt 不在此文件中）、图标尺寸、标签字号、项间距 (来源: tab-bars.md › 全文)

### 工具栏（Toolbars）

- **用途/组成** — 一组或多组控件**横向**排列在视图的**顶部或底部边缘**，按逻辑分组；包含三类内容：当前视图标题、导航控件（返回/前进、搜索字段）、动作项（按钮、菜单）(来源: toolbars.md › 开篇)
- **选型 vs tab bar** — toolbar 作用于视图内容、辅助导航与定位；tab bar **专门**用于应用区域之间的导航 (来源: toolbars.md › 开篇)
- **项数控制** — 刻意挑选条目避免拥挤（用户必须能区分并激活每一项）；需定义视图变窄时哪些项移入 overflow menu (来源: toolbars.md › Best practices)
- **禁止项：不要手动加 overflow** — 系统会在 macOS/iPadOS 自动添加 overflow menu；不要手工添加，也不要设计出默认就溢出的布局 (来源: toolbars.md › Best practices)
- **More 菜单** — 用 More 菜单收纳次要动作；尽量把所有动作放进 toolbar，确有必要才加 More (来源: toolbars.md › Best practices)
- **自定义工具栏** — iPadOS/macOS 可让用户自定义加入常用项；对项多、含高级功能、或长时间使用的应用尤其有用 (来源: toolbars.md › Best practices)
- **背景与着色（禁止项/倾向）** — 减少使用自定义工具栏背景与着色控件（会覆盖或干扰系统提供的背景效果）；用内容层决定工具栏颜色与外观，必要时用 `ScrollEdgeEffectStyle` 区分工具栏区与内容区 (来源: toolbars.md › Best practices)
- **颜色（禁止项）** — 避免工具栏项标签与内容层背景用相近颜色；内容层鲜艳时使用工具栏默认的单色外观 (来源: toolbars.md › Best practices)
- **圆角同心** — 优先用标准组件：标准按钮、文本字段、header、footer 的圆角默认与栏的圆角**同心（concentric）**；自建组件也必须让圆角与栏角同心 (来源: toolbars.md › Best practices)（文件未给出具体圆角半径数值）
- **临时隐藏** — 可情境化地临时隐藏工具栏以获得无干扰体验，但必须提供可靠恢复隐藏元素的方式 (来源: toolbars.md › Best practices)
- **标题：必要性** — 每个窗口都要有有用标题，帮助用户确认位置、区分多窗口内容；若标题显得冗余可留空（如 Notes 单窗口时不标题正文首行）(来源: toolbars.md › Titles)
- **标题：禁止项** — 不要用应用名做窗口标题（不提供内容层级信息）(来源: toolbars.md › Titles)
- **标题：长度** — 简洁，一词或短句；`keep the title under 15 characters long`——**<15 字符**，以给其他控件留空间 (来源: toolbars.md › Titles)
- **导航按钮** — 用标准 Back 与 Close 按钮及其标准符号；**不要**写 `Back` 或 `Close` 文字标签；若自建，必须外观与行为一致并在全应用统一实现 (来源: toolbars.md › Navigation)
- **导航工具栏位置** — 带导航控件的工具栏出现在**窗口顶部**（iOS 中即 navigation bar），常含搜索字段 (来源: toolbars.md › Navigation)
- **动作优先级** — 提供支持主任务的命令，优先用户最可能/最常使用的（或最高层级对象相关）命令 (来源: toolbars.md › Actions)
- **动作表达** — 每个控件的含义必须一目了然；优先**简单可识别的符号**而非文字；只有难以用符号表达的动作（如 edit）才用文字 (来源: toolbars.md › Actions)
- **符号样式（禁止项）** — 优先**无边框**的系统符号；不需要外圈/描边圆圈，因为分组本身已提供可见容器，且系统会自动定义 hover 与选中状态外观 (来源: toolbars.md › Actions)
- **主操作** — 关键动作（Done、Submit）使用 `.prominent` 样式以形成唯一焦点；`Only specify one primary action, and put it on the trailing side of the toolbar`——**只 1 个**，且放**尾部** (来源: toolbars.md › Actions)
- **三个位置：leading（前缘）** — 返回上一文档、显示/隐藏 sidebar 的元素放最前缘，随后是视图标题；标题旁可放文档菜单（Duplicate、Rename、Move、Export）；**leading 边缘的项不可自定义**，以保证始终可用 (来源: toolbars.md › Item groupings)
- **三个位置：center（中部）** — 放常用有用控件；视图标题不在 leading 时可放这里；macOS/iPadOS 中用户可增删重排；窗口缩小时这里的项**自动折叠进系统 overflow menu** (来源: toolbars.md › Item groupings)
- **三个位置：trailing（尾部）** — 放必须始终可用的重要项、打开邻近 inspector 的按钮、可选搜索字段、More 菜单（并支持自定义）、以及存在的主动作（如 Done）；尾部项在**所有窗口尺寸下都保持可见** (来源: toolbars.md › Item groupings)
- **定位手法** — 把项 pin 到 leading/center/trailing，并在按钮或其他项之间**插入 space** (来源: toolbars.md › Item groupings)
- **分组：数量上限** — 减少分组数（组太多即使 iPad/Mac 空间更大也显杂乱）；`In general, aim for a maximum of three`——**最多约 3 组** (来源: toolbars.md › Item groupings)
- **分组：原则** — 按功能与使用频率逻辑分组；导航控件与关键动作（Done、Close、Save）放在专门、熟悉、视觉上独立的组里；跨平台保持一致的分组与位置 (来源: toolbars.md › Item groupings)
- **分组：文字标签隔离** — 带文字标签的动作要与符号动作**分开**（文字按钮相邻符号按钮会被误读为一个「文字+符号」的组合动作；多个文字按钮的文案会连成一片）；用固定空格（`fixedSpace`）分隔 (来源: toolbars.md › Item groupings)
- **iOS：极简优先** — 主工具栏区只放最重要的项；其余放 More 菜单 (来源: toolbars.md › Phone (iOS))
- **iOS：大标题** — 用大标题帮助用户在导航与滚动中保持定位；默认滚动时大标题过渡为标准标题，滚回顶部恢复大标题 (来源: toolbars.md › Phone (iOS))
- **iPadOS：与 tab bar 合体** — toolbar 与 tab bar 可以共存于视图**顶部同一水平空间**，适合在少数几个主区域间导航同时把整宽留给内容 (来源: toolbars.md › Tablet (iPadOS))
- **macOS：位置与外观** — toolbar 位于窗口顶部 frame 内（标题栏下方或与之整合）；窗口标题可与控件同行显示；toolbar 项**不带 bezel** (来源: toolbars.md › Desktop (macOS))
- **macOS：菜单栏对齐（强制）** — 每个 toolbar 项都必须在菜单栏有对应命令（因为 toolbar 可被自定义或隐藏，不能是命令的唯一入口）；反之不必为每个菜单项都提供 toolbar 项 (来源: toolbars.md › Desktop (macOS))
- **缺口数值** — 本文件未给出栏高、项间距、图标尺寸、圆角半径的具体数值 (来源: toolbars.md › 全文)

### 按钮（Buttons）

- **用途/三属性** — 按钮触发**瞬时动作**；由 Style（大小、颜色、形状）、Content（符号/文字标签/两者）、Role（系统语义角色，可影响外观）构成 (来源: buttons.md › 开篇)
- **选型 vs 同类组件** — toggles、pop-up buttons、segmented controls 是外观与行为专用的「类按钮组件」，不要用普通按钮硬做 (来源: buttons.md › 开篇)
- **最小点击区域（唯一硬数值）** — `a button needs a hit region of at least 44x44 pt — in visionOS, 60x60 pt`：**≥44×44 pt**（visionOS 为 **60×60 pt**），无论用指尖、指针、眼睛还是遥控器 (来源: buttons.md › Best practices)
- **周边留白** — 按钮周围必须有足够空间，以便与相邻组件/内容视觉区分并便于激活（文件只给"足够"，未给具体 pt）(来源: buttons.md › Best practices)
- **按压状态（强制）** — 自定义按钮**必须**包含 press state，否则会显得无响应 (来源: buttons.md › Best practices)
- **突出按钮数量** — 最可能的操作用 prominent 样式（系统给背景上 accent color）；`Keep the number of prominent buttons to one or two per view`——**每视图 1–2 个**；过多会提高认知负荷 (来源: buttons.md › Style)
- **禁止项：用尺寸区分主次** — 不要用大小区分首选选项；同尺寸表示这些选项构成一组内聚选择，尺寸不同会让界面混乱不一致；用**样式**（prominent vs 次级）区分 (来源: buttons.md › Style)
- **颜色（禁止项）** — 避免按钮标签与内容层背景用相近颜色；内容层鲜艳时用按钮标签的默认单色外观 (来源: buttons.md › Style)
- **内容表达** — 每个按钮必须清楚传达用途（可含符号、文字标签或两者）(来源: buttons.md › Content)
- **图标** — 熟悉动作配熟悉图标，例如 `square.and.arrow.up` 表示分享；优先用现成或定制的 SF Symbol (来源: buttons.md › Content)
- **文字标签规则** — 短标签比图标更清楚时用文字；用 title-style capitalization；**以动词开头**以传达动作，例如 "Add to Cart" (来源: buttons.md › Content)
- **tooltip** — macOS 与 visionOS 中悬停片刻后系统显示 tooltip（简短短语说明按钮作用） (来源: buttons.md › Content)
- **角色（4 种）** — Normal（无语义）、Primary（默认按钮，用户最可能选）、Cancel（取消当前动作）、Destructive（可能造成数据破坏）；primary 用 accent color，destructive 用系统红 (来源: buttons.md › Role)
- **Primary 行为** — 给用户最可能选择的按钮分配 primary；primary 按钮响应 Return 键；在临时视图（sheet、可编辑视图、alert）中分配 primary 后，按 Return 会自动关闭该视图 (来源: buttons.md › Role)
- **禁止项：破坏性动作用 primary** — 不要把 primary role 给破坏性动作，即使它是最可能的选择；因为视觉突出，用户常不读就点 (来源: buttons.md › Role)
- **iOS/iPadOS：延迟反馈** — 动作不能瞬时完成时，在按钮内显示 activity indicator（省空间并解释延迟）；可同时更换标签，如 "Checkout" → "Checking out…"；系统把指示器显示在原/替代标签旁，并隐藏按钮图像（若有）(来源: buttons.md › Mobile (iOS, iPadOS))
- **macOS push button** — 可显示文字、符号、图标或图像及其组合；可作视图的默认按钮；可 tint (来源: buttons.md › Push buttons)
- **macOS flexible-height push button** — 仅在需要显示**高**或**可变高度**内容（两行文字、高图标）时使用；它与普通 push button 使用**相同的圆角与内容内边距**，因此外观一致 (来源: buttons.md › Push buttons)
- **macOS push button 标题** — 打开另一个窗口/视图/应用时，标题末尾加省略号（ellipsis 表示还需要用户输入）(来源: buttons.md › Push buttons)
- **macOS spring loading** — 支持 Magic Trackpad 上的 spring loading：拖拽选中项到按钮上并 force click（更用力按）即可激活，且不放下拖拽项、可继续拖拽 (来源: buttons.md › Push buttons)
- **macOS square button（gradient button）** — 触发与视图相关的动作（如增删表格行）；**只含符号/图标，不含文字**；可表现为 push button、toggle 或 pop-up button；出现在所影响视图的内部或紧下方 (来源: buttons.md › Square buttons)
- **macOS square button 位置（禁止项）** — 只用于视图内，**不用于工具栏或状态栏**；工具栏里需要用 toolbar item (来源: buttons.md › Square buttons)
- **macOS square button 内容** — 优先用 SF Symbol（自动获得合适着色）；不要用文字标签介绍方形按钮 (来源: buttons.md › Square buttons)
- **macOS help button** — 圆形、**固定尺寸**、含问号；必须用系统提供的 help button；尽量打开与当前上下文相关的帮助主题，否则打开帮助文档顶层 (来源: buttons.md › Help buttons)
- **macOS help button 数量** — `Include no more than one help button per window`——**每窗口最多 1 个** (来源: buttons.md › Help buttons)
- **macOS help button 位置（表格）** — 有 dismiss 按钮的对话框（OK/Cancel）：放在下角，与 dismiss 按钮**相对**且**垂直对齐**；无 dismiss 按钮的对话框：左下角或右下角；设置窗口或面板：左下角或右下角 (来源: buttons.md › Help buttons)
- **macOS help button 禁止项** — 只在视图内使用，不要放进工具栏或状态栏；不要用文字介绍 help button (来源: buttons.md › Help buttons)
- **macOS image button 内边距（唯一 px 数值）** — `Include about 10 pixels of padding between the edges of the image and the button edges`——图像边缘与按钮边缘之间约 **10 pixels**；按钮边缘定义可点击区域（即使不可见）；一般不要加系统边框 (来源: buttons.md › Image buttons)
- **macOS image button 位置/标签** — 只用于视图内，不放工具栏或状态栏（工具栏用 toolbar item）；若必须有标签，放在图片按钮**下方** (来源: buttons.md › Image buttons)

### 分段控件（Segmented controls）

- **用途/定义** — 由**两个或更多** segment 组成的线性集合，每个 segment 都像按钮；通常所有 segment **等宽** (来源: segmented-controls.md › 开篇)
- **选择模式** — 在 iOS/iPadOS 上提供一组选项中的**单一选择**；在 macOS 上可单选**也可多选**；还可以作为**不显示选择状态**的动作按钮组（`isMomentary` / `momentary`）(来源: segmented-controls.md › 开篇)
- **选型 vs 其他按钮** — 用于影响某个对象、状态或视图的**紧密相关**选择；当分组本身重要或需要清晰展示选择状态时选它；其分组在任何视图尺寸与位置下都保持 (来源: segmented-controls.md › Best practices)
- **选型 vs tab bar** — 在**紧密相关的子视图**间切换用分段控件（如 Calendar 新建事件 sheet 在"新建事件/新建提醒"间切换）；在**完全独立的应用区域**间切换用 tab bar (来源: segmented-controls.md › Mobile (iOS, iPadOS))
- **选型 vs tab view（macOS）** — 主窗口区域的视图切换用 tab view；分段控件适合在工具栏或 inspector 面板中切换视图 (来源: segmented-controls.md › Desktop (macOS))
- **数量上限（核心数字）** — `Aim for no more than about five to seven segments in a wide interface and no more than about five segments on iPhone`——**宽界面最多约 5–7 个**，**iPhone 上最多约 5 个**；过多难以解析且耗时的浏览 (来源: segmented-controls.md › Best practices)
- **尺寸一致性** — 所有 segment 等宽时控件感觉平衡；图标与标题宽度也应尽量一致 (来源: segmented-controls.md › Best practices)
- **禁止项：类型混用** — 同一个分段控件内控件类型必须一致：不要给本来表示选择状态的控件中的 segment 分配动作；也不要给本来执行动作的控件显示选择状态 (来源: segmented-controls.md › Best practices)
- **禁止项：文字与图像混用** — 单个分段控件内优先**只用文字或只用图像**，不要混用（会显得断裂、令人困惑）(来源: segmented-controls.md › Content)
- **内容大小** — 每个 segment 的内容大小应相近；因为 segment 通常等宽，某些填满某些空着会很难看 (来源: segmented-controls.md › Content)
- **文案** — segment 标签用**名词或名词短语**，title-style capitalization；显示文字标签的分段控件**不需要**介绍性文字 (来源: segmented-controls.md › Content)
- **macOS 补充文案** — 可用介绍性文字说明控件用途；当控件使用符号/界面图标时，可在每个 segment 下方加标签说明含义；若应用有 tooltip，则**每个 segment 都要有** tooltip (来源: segmented-controls.md › Desktop (macOS))
- **macOS 交互增强** — 支持 spring loading：拖拽选中项到某 segment 上 force click 即激活，且可继续拖拽 (来源: segmented-controls.md › Desktop (macOS))
- **平台** — watchOS 不支持 (来源: segmented-controls.md › Platform considerations)
- **缺口数值** — 本文件未给出控件高度、segment 最小宽度、圆角、内边距数值；segment 内文字下方标签的位置有描述但无尺寸 (来源: segmented-controls.md › 全文)

### 搜索字段（Search fields）

- **用途/组成** — 可编辑文本字段，显示 **Search 图标、Clear 按钮、占位文本**；可用 scope bar 与 tokens 过滤和细化范围 (来源: search-fields.md › 开篇)
- **占位文本** — 用占位文本帮助用户知道能搜什么；当需要强化搜索范围或告知可搜索内容类型时尤其有用 (来源: search-fields.md › Best practices)
- **即时搜索** — 尽可能在用户**输入时立即开始搜索**（结果随文本更具体而持续精化，体验更灵敏）(来源: search-fields.md › Best practices)
- **建议词** — 搜索前显示最近搜索，输入时显示预测建议 (来源: search-fields.md › Best practices)
- **结果排序** — 简化结果：最相关的结果排在最前，减少滚动；除优先级外可对结果分类 (来源: search-fields.md › Best practices)
- **结果过滤** — 可让用户过滤：在搜索结果内容区放一个 scope bar (来源: search-fields.md › Best practices)
- **Scope bar 规则** — 用于在**明确定义的搜索类别**之间过滤；帮助从更宽范围走向更窄范围；**默认更宽的范围**，让用户按需细化 (来源: search-fields.md › Scope bars and tokens)
- **Token 规则** — token 是搜索词的视觉化表示，可选中并编辑，并对其后追加的搜索词起过滤器作用；用于按常见搜索词或项目过滤（如 Mail 中按联系人、Messages 中按照片）；建议与搜索建议配对使用，以便用户学会可用 token (来源: search-fields.md › Scope bars and tokens)
- **iOS 入口位置（3 个）** — ① tab bar 中的标签；② 屏幕底部或顶部工具栏；③ 与内容内联 (来源: search-fields.md › Phone (iOS))
- **iOS 搜索标签的两种样式** — **standard tab**：与其余标签外观一致，点按进入带顶部搜索字段的搜索落地页；**button appearance**：显示为独立按钮，点按立即聚焦搜索字段并弹出键盘，退出后回到之前的标签 (来源: search-fields.md › Search as a tab)
- **iOS 样式选择** — 要提供建议、促进发现与探索 → standard tab（有专门落地页，可在用户点字段前展示内容/建议，适合内容丰富的应用，如 Apple TV）；要快速找到所需、体验更短暂 → button appearance (来源: search-fields.md › Search as a tab)
- **iOS 底部工具栏** — 可把搜索作为**展开的字段**或**工具栏按钮**（取决于可用空间）；点按后动画变成键盘上方的搜索字段 (来源: search-fields.md › Search in a toolbar)
- **iOS 顶部工具栏（navigation bar）** — 搜索显示为工具栏按钮；点按后动画变成搜索字段，出现在键盘上方；底部没空间时出现在顶部 (来源: search-fields.md › Search in a toolbar)
- **位置决策：底部优先** — `Place search at the bottom if there's room`；底部搜索在「搜索是优先事项」时更有用、更易到达（Settings 中为唯一项；Mail 与 Notes 中与其他重要控件并列）(来源: search-fields.md › Search in a toolbar)
- **位置决策：何时放顶部** — 需要把屏幕底部让给内容，或**没有底部工具栏**时放顶部（如 Wallet 底部有活动凭证堆叠）(来源: search-fields.md › Search in a toolbar)
- **内联字段** — 当搜索位置与所搜内容的**关系**很重要时用内联；适合应用有**多个搜索字段**、且位置对搜索范围起关键作用的情况（Music 的 tab 搜索 + 资料库内联过滤）(来源: search-fields.md › Search as an inline field)
- **内联字段位置** — 在顶部时，把内联搜索字段放在**它所搜索的列表之上**；滚动时考虑把它 **pin 到顶部工具栏**，以区别于其他位置的搜索 (来源: search-fields.md › Search as an inline field)
- **iPadOS/macOS 通用位置** — 常见用法把搜索字段放在**工具栏的尾部（trailing）**（适合需要跨多列搜索的分栏视图：Mail、Notes、Voice Memos；结果出现在详情视图时同理，如 Freeform）(来源: search-fields.md › Tablet and desktop (iPadOS, macOS))
- **侧边栏顶部** — 过滤侧边栏内容或导航时，把搜索放在侧边栏**顶部**（如 Settings 可暴露多层级深的区块）(来源: search-fields.md › Tablet and desktop (iPadOS, macOS))
- **作为侧边栏/标签栏的一项** — 想要专门的发现区域（配丰富建议、分类或需要更多空间的内容）时，把搜索做成侧边栏或标签栏的一项（Music、TV）；这也保证用户在切换区域时搜索始终可用 (来源: search-fields.md › Tablet and desktop (iPadOS, macOS))
- **聚焦时机** — 在专门区域的搜索字段中，用户导航到该区域时考虑**立即聚焦**字段；**例外**：iPad 上仅有虚拟键盘时保持未聚焦，以免键盘意外遮挡视图 (来源: search-fields.md › Tablet and desktop (iPadOS, macOS))
- **窗口缩放适配** — iPad 上搜索字段随窗口流体缩放；在 compact 视图中要保证搜索出现在最有上下文用处的位置（Notes 与 Mail 缩小后把搜索放到内容列表列之上）(来源: search-fields.md › Tablet and desktop (iPadOS, macOS))
- **跨平台一致性** — iPad 与 Mac 上搜索的放置与行为相似；两平台都有的应用应尽量让搜索体验一致 (来源: search-fields.md › Tablet and desktop (iPadOS, macOS))
- **缺口数值** — 本文件未给出字段高度、圆角、内边距、图标尺寸数值 (来源: search-fields.md › 全文)

### 开关（Toggles：switch / checkbox / radio button）

- **用途** — 让用户在一对对立状态（如开/关）间选择，用不同外观表示每个状态；样式有 switch、checkbox 等，各平台用法不同；此外所有平台都支持用不同外观表现两态的「类 toggle 按钮」 (来源: toggles.md › 开篇)
- **选型：不是 toggle 的场景** — toggle 始终管理**某个东西的状态**；若要从列表中选择项目等其他类型动作，用别的组件（如 pop-up button）(来源: toggles.md › Best practices)
- **标识对象** — 清楚标识该 toggle 影响的设置、视图或内容；一般周边上下文已足够；**macOS** 中常可额外提供标签描述它控制的状态；按钮式 toggle 一般用界面图标并通过改变背景更新外观 (来源: toggles.md › Best practices)
- **状态可辨识（强制）** — 视觉差异必须明显：增加/移除颜色填充、显示/隐藏背景形状、改变内部细节（勾选标记或圆点）；**不要只靠颜色**传达状态（不是所有人都能感知差异）(来源: toggles.md › Best practices)
- **iOS/iPadOS：switch 的位置限制** — `Use the switch toggle style only in a list row`——**只用在列表行中**；此时无需标签，因为行内容已提供上下文 (来源: toggles.md › Mobile (iOS, iPadOS))
- **iOS/iPadOS：颜色** — 仅在必要时改 switch 默认颜色（默认绿色在多数情况可用；也可用应用 accent color）；必须与未着色外观有足够对比 (来源: toggles.md › Mobile (iOS, iPadOS))
- **iOS/iPadOS：列表之外** — 列表之外用**表现为 toggle 的按钮**，不要用 switch（例：Phone 的通话筛选按钮，激活时加蓝色高亮，未激活时移除）(来源: toggles.md › Mobile (iOS, iPadOS))
- **iOS/iPadOS：禁止项** — 不要给按钮式 toggle 配解释其用途的标签（图标 + 替代背景外观已足够）(来源: toggles.md › Mobile (iOS, iPadOS))
- **macOS：位置（禁止项）** — switch、checkbox、radio button 都用在**窗口主体**，不要用于窗口 frame（尤其避免工具栏或状态栏）(来源: toggles.md › Desktop (macOS))
- **macOS switch 选型** — 想**强调**的设置优先用 switch（视觉重量大于 checkbox，适合控制比单个设置更多的功能，如开关一整组设置）(来源: toggles.md › Switches)
- **macOS mini switch** — 在分组表单（grouped form）中，控制**单行**设置时可用 mini switch，其高度与按钮等控件相近，因此行高一致；层级设置中主设置用 regular switch，从属设置用 mini switch (来源: toggles.md › Switches)
- **macOS 禁止项** — 一般不要用 switch 替换已有 checkbox（界面里已经在用 checkbox 就继续用）(来源: toggles.md › Switches)
- **checkbox 定义** — 小的方形按钮：off 为空、on 含勾选标记、mixed（混合）含短横；通常标题在**尾部（trailing）**；在可编辑清单中 checkbox 可以不带标题或任何附加内容 (来源: toggles.md › Checkboxes)
- **checkbox 选型** — 需要呈现设置的**层级**时用 checkbox 而不是 switch（视觉风格便于对齐与表达分组）；通过对齐（一般沿 checkbox 的**前缘**）与**缩进**表示依赖关系，例如父 checkbox 控制子 checkbox 状态 (来源: toggles.md › Checkboxes)
- **checkbox 组标签** — 当一组 checkbox 的关系不清晰时，用 label 介绍这组选项，并让 label 的**基线与组内第一个 checkbox 对齐** (来源: toggles.md › Checkboxes)
- **checkbox 状态准确性** — 状态可为 on / off / mixed；若用父 checkbox 总控多个从属 checkbox，当从属项状态不一致时显示 mixed（如文字样式总开关 + bold/italic/underline 子项）(来源: toggles.md › Checkboxes)
- **radio button 定义** — 小的**圆形**按钮 + 标签；通常以**2 到 5 个**为一组显示，表示一组互斥选择；状态为选中（实心圆）或未选中（空心圆）(来源: toggles.md › Radio buttons)
- **radio button 的 mixed** — 虽然也能显示 mixed（短横），但很少有用（多状态可用更多 radio button 表达）；需要表达 mixed 时改用 checkbox (来源: toggles.md › Radio buttons)
- **radio button 选型** — 互斥选项优先用一组 radio button；若允许选多个，用 checkbox (来源: toggles.md › Radio buttons)
- **radio button 数量上限** — 不要列太多（占空间且令人不知所措）；`If you need to present more than about five options`——**超过约 5 个**选项时改用 pop-up button 之类的组件 (来源: toggles.md › Radio buttons)
- **单个开关的选型** — 单个可开可关的设置优先用 checkbox（勾选标记的有无比单个 radio button 更易一眼看懂）；极少数情况下单个 checkbox 无法清楚表达对立状态时，可用**一对 radio button**，各自标签说明所控状态 (来源: toggles.md › Radio buttons)
- **radio button 间距** — 水平排列时使用**一致间距**：先测量容纳**最长标签**所需的空间，并把该测量值一致地使用 (来源: toggles.md › Radio buttons)
- **缺口数值** — 本文件未给出 switch/checkbox/radio 的 pixel 尺寸与间距数值（只说 mini switch 高度与按钮等控件相近）(来源: toggles.md › 全文)

### 进度指示器（Progress indicators）

- **用途/生命周期** — 告知应用没有卡住（加载内容或执行耗时操作时）；**所有**进度指示器都是**临时**的：操作进行中出现，完成后消失 (来源: progress-indicators.md › 开篇)
- **两类（选型）** — **Determinate**：时长明确的任务（如文件转换）；**Indeterminate**：不可量化的任务（如加载或同步复杂数据）(来源: progress-indicators.md › 开篇)
- **外观** — determinate 通过填充线性或圆形轨道显示进度：**progress bar 从前缘（leading）向尾部（trailing）填充**；**circular 顺时针填充**；indeterminate（又称 activity indicator）用动画图像；所有平台支持旋转的圆形，**macOS 还支持不确定进度条** (来源: progress-indicators.md › 开篇)
- **优先 determinate** — 尽可能用 determinate：不确定指示器只说明有过程在跑，无法帮用户估算时长；确定型可帮用户决定等待期间做别的、改期重试或放弃 (来源: progress-indicators.md › Best practices)
- **准确性/节奏** — determinate 报告进度要尽量准确，并**均匀化推进节奏**；反例：`Showing 90 percent completion in five seconds and the last 10 percent in 5 minutes` 会让用户怀疑应用是否还在工作，甚至觉得受骗 (来源: progress-indicators.md › Best practices)
- **持续运动（强制）** — 保持指示器持续运动，因为静止会被联想为进程停滞或应用冻结；若过程真的停了，给出反馈说明问题与用户可采取的措施 (来源: progress-indicators.md › Best practices)
- **允许的切换** — 不确定过程一旦能确定时长，就把 progress bar 从 indeterminate 切到 determinate（用户普遍更偏好确定型）(来源: progress-indicators.md › Best practices)
- **禁止的切换** — `Don't switch from the circular style to the bar style`：activity indicator（spinner）与 progress bar 形状大小不同，互切会破坏界面并让用户困惑 (来源: progress-indicators.md › Best practices)
- **描述文案** — 可显示补充上下文的描述，要**准确且简洁**；避免模糊词如 `loading` 或 `authenticating`（很少增加价值）(来源: progress-indicators.md › Best practices)
- **位置一致性** — 把进度指示器放在**一致的位置**，让用户能可靠地找到操作状态 (来源: progress-indicators.md › Best practices)
- **可终止性** — 条件允许就让用户中止处理：可无副作用地中断 → 提供 Cancel 按钮；中断可能造成负面后果（如丢失已下载部分）→ 可同时提供 Pause 与 Cancel (来源: progress-indicators.md › Best practices)
- **终止有代价时必须告知** — 取消会丢失进度时，用 alert 提供「确认取消」或「恢复处理」选项 (来源: progress-indicators.md › Best practices)
- **iOS/iPadOS refresh control 定义** — 让用户立即重新加载内容（通常在表格视图中），不必等下一次自动更新；**默认隐藏**，用户下拉要刷新的视图时可见（如 Mail 收件箱下拉）(来源: progress-indicators.md › Refresh content controls)
- **自动更新（强制）** — 仍要周期性自动更新内容；不要让用户负责发起每次更新 (来源: progress-indicators.md › Refresh content controls)
- **refresh control 标题** — 仅在增加价值时给短标题（多数情况下不必要，动画已说明正在加载）；**不要**用标题解释如何刷新，而要提供关于被刷新内容的有价值信息（Podcasts 用标题显示上次更新时间）(来源: progress-indicators.md › Refresh content controls)
- **macOS 选型** — 后台操作状态或空间受限时优先用 activity indicator（spinner）：体积小、不打扰，适合异步后台任务（如从服务器取消息），也适合在**小区域**内表达进度（如文本框内、按钮旁）(来源: progress-indicators.md › Desktop (macOS))
- **macOS 禁止项** — 不要给旋转指示器加标签（用户通常是自己发起该过程，标签多余）(来源: progress-indicators.md › Desktop (macOS))
- **缺口数值** — 本文件未给出 spinner 尺寸、进度条粗细/高度/圆角数值；仅给出一处节奏示例（5 秒到 90%、5 分钟到 100%）(来源: progress-indicators.md › 全文)

---

## 跨组件通用规则

### 触控目标与间距（全批文件唯一硬数值）

- **按钮点击区** — 至少 **44×44 pt**；visionOS **60×60 pt**（来源: buttons.md › Best practices）
- **周边留白** — 按钮需要有足够空间与相邻组件/内容区分并便于激活（来源: buttons.md › Best practices）；工具栏项之间用插入 space 分隔（来源: toolbars.md › Item groupings）；集合的图片要用 adequate padding 保证 focus/hover 可见且不重叠（来源: collections.md › Best practices）
- **本批文件不含**平台通用最小控件尺寸、控件间距、安全区/边距数值——见文末「数值缺口与外部补充」

### 颜色与材质

- **统一的一条禁止项（出现在 3 个文件）** — 不要让组件标签与**内容层背景**使用相近颜色；内容层已鲜艳时，组件用默认的**单色（monochromatic）**外观，或选对比足够的 accent color（来源: buttons.md › Style；toolbars.md › Best practices；tab-bars.md › Best practices）——三条都指向 `color.md#liquid-glass-color`
- **减少自定义背景/着色** — 减少工具栏的自定义背景与着色控件（会干扰系统背景效果），用内容层决定颜色，必要时用 `ScrollEdgeEffectStyle` 区分工具栏区与内容区（来源: toolbars.md › Best practices；scroll-views.md › Scroll edge effects）
- **Liquid Glass** — iOS tab bar 的项目落在 Liquid Glass 背景上并允许内容透出（来源: tab-bars.md › Phone (iOS)）
- **语义色** — 四级 label 色表达信息层级：Label / Secondary / Tertiary（不可用项）/ Quaternary（水印）（来源: labels.md › Best practices）；按钮角色映射到强调色与系统红：primary→accent，destructive→系统红（来源: buttons.md › Role）；iOS switch 默认绿色，可改 accent color 但需足够对比（来源: toggles.md › Mobile (iOS, iPadOS)）
- **不只靠颜色传状态** — toggle 的状态差异必须通过形状、填充、内部细节等共同表达（来源: toggles.md › Best practices）

### 组件选型与层级（跨文件对照）

- **顶层区域导航** → tab bar；**当前视图的动作/导航/搜索** → toolbar（来源: tab-bars.md › Best practices；toolbars.md › 开篇）
- **完全独立区域切换** → tab bar；**紧密相关子视图切换** → segmented control（来源: segmented-controls.md › Mobile (iOS, iPadOS)）
- **macOS 主窗口视图切换** → tab view，不是 segmented control；segmented control 用于工具栏或 inspector 面板（来源: segmented-controls.md › Desktop (macOS)）
- **文本数据** → list/table；**图像为主** → collection（来源: lists-and-tables.md › Best practices；collections.md › Best practices）
- **层级数据（macOS）** → outline view，不是 table view（来源: lists-and-tables.md › Desktop (macOS)）
- **少量不可编辑文本** → label；少量可编辑 → text field；大量文本 → text view（来源: labels.md › Best practices）
- **两态开关** → toggle（列表行内用 switch；列表外用按钮式 toggle；macOS 层级用 checkbox，互斥 2–5 项用 radio button）（来源: toggles.md › Best practices / Mobile / Desktop）
- **列表内下钻** → disclosure indicator；查看行内容更多信息 → info button；**不要**用 info button 做层级导航（来源: lists-and-tables.md › Mobile (iOS, iPadOS)）
- **结构复杂到 tab 装不下** → sidebar 或可转为 sidebar 的 tab bar；纯 sidebar 用 navigation split view（来源: tab-bars.md › Tablet (iPadOS)）
- **优先系统组件** — 优先标准组件与系统符号（tab bar 图标用 SF Symbols，优先 filled；toolbar 项优先无边框系统符号；按钮优先现成/定制 SF Symbol；label 优先系统字体并支持 Dynamic Type）（来源: tab-bars.md › Best practices；toolbars.md › Actions；buttons.md › Content；labels.md › Best practices）

### 数量上限（一表汇总，全部来自本次 11 个文件）

- **Tab 标签** — 默认 ≤5（自定义标签的默认列表 `five or fewer`）；溢出时尾部标签变为 More 标签（来源: tab-bars.md › Tablet (iPadOS) / Best practices）
- **工具栏分组** — 最多约 3 组（`aim for a maximum of three`）；主操作只 1 个且放尾部（来源: toolbars.md › Item groupings / Actions）
- **分段控件** — 宽界面约 5–7 个，iPhone 约 5 个（来源: segmented-controls.md › Best practices）
- **Radio button** — 每组 2–5 个；超过约 5 个改用 pop-up button（来源: toggles.md › Radio buttons）
- **突出按钮** — 每视图 1–2 个 prominent（来源: buttons.md › Style）
- **Help button** — 每窗口最多 1 个（来源: buttons.md › Help buttons）
- **滚动边缘效果** — 每视图 1 个（分栏可各 1 个但高度一致）（来源: scroll-views.md › Scroll edge effects）
- **工具栏标题** — 少于 15 字符（来源: toolbars.md › Titles）

### 文案与大小写

- **title-style capitalization** — 多列表格列标题（来源: lists-and-tables.md › Content）、按钮标签（来源: buttons.md › Content）、segment 标签（来源: segmented-controls.md › Content）
- **单字标签** — tab 标签尽量用一个词（来源: tab-bars.md › Best practices）
- **动词开头** — 按钮文字标签以动词开头（如 "Add to Cart"）（来源: buttons.md › Content）
- **名词/名词短语** — 列标题与 segment 标签（来源: lists-and-tables.md › Content；segmented-controls.md › Content）
- **不加结尾标点** — 列标题（来源: lists-and-tables.md › Content）
- **不要文字标签的地方** — 工具栏 Back/Close（用标准符号，不写字）（来源: toolbars.md › Navigation）；方形按钮、help button、按钮式 toggle、spinner（不要介绍性/解释性文字）（来源: buttons.md › Square buttons / Help buttons；toggles.md › Mobile；progress-indicators.md › Desktop）
- **省略号语义** — 控件标题末尾的省略号表示还会要求用户提供额外输入 / 会打开另一窗口或视图（来源: buttons.md › Push buttons）
- **编辑型截断** — 窄表格优先中间省略号（保留首尾）（来源: lists-and-tables.md › Content）
- **准确且简洁** — 进度描述避免 `loading`、`authenticating` 这类模糊词（来源: progress-indicators.md › Best practices）

### 交互与状态

- **必须有可见状态变化** — 自定义按钮必须有 press state（来源: buttons.md › Best practices）
- **选择反馈的两种时间形态** — 层级表格持久高亮路径；选项表格短暂高亮后转为勾选标记（来源: lists-and-tables.md › Best practices）
- **手势基线** — 集合默认 tap 选择 / touch and hold 编辑 / swipe 滚动（来源: collections.md › Best practices）；滚动视图必须支持系统默认滚动手势与键盘快捷键，自定义滚动也要保持 elastic 行为（来源: scroll-views.md › Best practices）
- **不可用状态的处理方式** — 不要禁用或隐藏 tab bar 按钮，改为解释该区域为空的原因（来源: tab-bars.md › Best practices）
- **延迟反馈** — 按钮内 activity indicator + 可选替换文案（来源: buttons.md › Mobile (iOS, iPadOS)）；refresh control 默认隐藏、下拉出现，且必须保留周期性自动更新（来源: progress-indicators.md › Refresh content controls）
- **可中止性** — 提供 Cancel，必要时加 Pause；取消有代价时用 alert 确认（来源: progress-indicators.md › Best practices）
- **动画** — 集合的插入/删除/重排用动画反馈（来源: collections.md › Best practices）
- **spring loading（macOS, Magic Trackpad）** — push button 与 segmented control 支持拖拽 + force click 激活（来源: buttons.md › Push buttons；segmented-controls.md › Desktop (macOS)）

### 布局与滚动

- **不要同方向嵌套滚动视图** — 横嵌纵（或反向）可以（来源: scroll-views.md › Best practices）
- **每视图 1 个滚动边缘效果**，分栏间高度一致（来源: scroll-views.md › Scroll edge effects）
- **同一轴不要同时出现 page control 与滚动指示器**（来源: scroll-views.md › Mobile (iOS, iPadOS)）
- **同一面板内滚动条尺寸统一**（small/mini 二选一并一致）（来源: scroll-views.md › Desktop (macOS)）
- **工具栏圆角同心** — 标准组件默认与栏角同心，自建组件也必须同心（来源: toolbars.md › Best practices）（无具体半径数值）
- **栏的屏幕位置** — iOS tab bar 浮在底部；iPadOS tab bar 在顶部附近；导航工具栏在窗口顶部；macOS toolbar 在窗口顶部 frame 内、无 bezel（来源: tab-bars.md › Phone / Tablet；toolbars.md › Navigation / Desktop (macOS)）
- **不要动态改变正在被交互的布局**（集合），除非是响应明确动作（来源: collections.md › Mobile (iOS, iPadOS)）

### 一致性与跨平台

- **保持分组与位置跨平台一致**（来源: toolbars.md › Item groupings）
- **iPad 与 Mac 搜索体验尽量一致**（来源: search-fields.md › Tablet and desktop (iPadOS, macOS)）
- **同一控件内不要混用类型** — 分段控件内不要混「动作段」与「选择段」（来源: segmented-controls.md › Best practices）；不要在同一分段控件里混文字与图像（来源: segmented-controls.md › Content）
- **扩展输入方式** — 关键动作同时提供指针/键盘可用的入口：macOS 中每个 toolbar 项都要在菜单栏有对应命令（来源: toolbars.md › Desktop (macOS)）；primary 按钮响应 Return 键（来源: buttons.md › Role）

---

## 数值缺口与外部补充（写实现前必须知道）

**这 11 个文件中完全不存在的数值**（不要声称出自这些文件）：
- 标签栏高度（无 49pt）、工具栏高度、导航栏高度
- 侧边距 / 安全区 / 内容边距（无 8pt、16pt、20pt）
- 表格行高、section header 高度、列表内边距、分隔线粗细
- 圆角半径（工具栏只要求与栏角"同心"，未给值）、分段控件高度/最小 segment 宽、搜索字段高度、开关与 checkbox 的 pixel 尺寸
- 图标尺寸：tab bar 图标尺寸被明确指向 Apple Design Resources（来源: tab-bars.md › Best practices）
- 动画时长、惯性参数

**同目录下确实写着这些数值的文件（本次任务清单之外，供后续补充提取）**：
- `accessibility.md › Mobility` — 控件尺寸与间距：iOS/iPadOS 默认 **44×44 pt**、最小 **28×28 pt**；macOS 默认 **28×28 pt**、最小 **20×20 pt**；带 bezel 的元素周围约 **12 pt** padding，无 bezel 元素可见边缘周围约 **24 pt** padding
- `accessibility.md › Vision` — 自定义字号默认/最小值：iOS/iPadOS **17 pt / 11 pt**，macOS **13 pt / 10 pt**；文字放大至少支持到 **200%**（watchOS 140%）
- `liquid-glass.md › Review checklist` — 命中区下限：移动端默认 **44×44 pt**、最小 **28×28 pt**；桌面默认 **28×28 pt**、最小 **20×20 pt**；并给出「每视图 1–2 个着色主操作」的复述（与 buttons.md 一致）
