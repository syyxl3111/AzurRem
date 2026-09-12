# AzurPilotMobile

这是 **AzurRem** 的安卓工程。

📖 **完整说明请看仓库根目录的 [README.md](../README.md)** —— 安装、网关、
架构、安全说明、预缓存原理都在那里。这里只放安卓工程自己的东西。

---

## 构建

```powershell
.\gradlew.bat assembleDebug
# 产出 app\build\outputs\apk\debug\app-debug.apk
```

| 组件 | 版本 |
|---|---|
| Android SDK | build-tools 36.0.0 / android-37.0 |
| AGP / Gradle / Kotlin | 9.3.2 / 9.5.0 / 2.4.10 |
| minSdk / targetSdk | 26 / 37 |

`local.properties` 里的 `sdk.dir` 指向本机 SDK 路径，**已被 gitignore**，
Android Studio 打开工程时会自己生成。

### 单元测试

```powershell
.\gradlew.bat :app:unitTest
```

> **为什么不是标准的 `:app:testDebugUnitTest`？**
> 它在某些环境下起不来：Gradle 的 test worker 子进程刚启动就退出，
> 报 `ClassNotFoundException: worker.gradle.process.internal.worker.GradleWorkerMain`
> 并伴随 `java.io.IOException: 管道正在被关闭`。
>
> 已排除的猜想：
> - `gradle-worker-main` / `gradle-worker-process` 的 jar 都在，没缺
> - 换成纯 ASCII 路径（目录联接）跑一样挂 → 不是中文路径的锅
> - `--no-daemon` 一样挂
>
> 但 `compileDebugUnitTestKotlin` 是好的（测试能正常编译）。
> `:app:unitTest` 用 `JavaExec` 直接 fork java 跑 JUnit，绕开 worker API。
> 详见 `app/build.gradle.kts` 里的注释。

---

## 三个构建坑（都已在配置里绕过）

1. **AGP 拒绝含非 ASCII 字符的路径** → `gradle.properties` 里 `android.overridePathCheck=true`
2. **AGP 9 内置 Kotlin**，不能再声明 `org.jetbrains.kotlin.android`，
   只用 `com.android.application` + `org.jetbrains.kotlin.plugin.compose`
3. **Compose 1.12 的矢量 API 变了**：`path` / `group` 是包级顶层函数（要单独 import），
   而且不再接受 SVG 字符串，改走 `PathBuilder` DSL（`moveTo` / `lineTo` / `arcTo`）

---

## 目录

```
app/src/main/java/com/azurpilot/mobile/
  data/          MCP 客户端、网关客户端、数据模型、设置存储、配置缓存
  ui/theme/      设计令牌（颜色 / 字体 / 亚克力）
  ui/icons/      手绘的 SF Symbols 风格矢量图标
  ui/components/ 状态卡、资源行、启停按钮、Tab 栏、滑动返回、覆盖层
  ui/screens/    5 个 Tab 页 + 任务配置页 + 日志页
app/src/test/    纯 JVM 单元测试（ConfigCache 的磁盘往返等）
```

## 设计语言

Apple HIG + 轻薄亚克力。

- **字体**：Inter（打包在 APK 里，可变字体）+ JetBrains Mono（日志）
- **结构**：iOS 分组内嵌列表（一个容器 + 内缩发丝分隔线），不是一叠独立卡片
- **层次**：靠背景明度差（`#F2F2F7` 背景 / 白色面板），**不用投影**
- **亚克力**：半透明填充 + 顶部 1dp 反光 + 发丝描边。**不做真模糊** ——
  Android 没有 CSS 的 `backdrop-filter`，真模糊在滚动列表上是低端机掉帧的主因
- **配色**：资源色沿用原配置的色相，但换成 Apple 系统色（`#0000FF` → `#0A84FF` 等）
- **日志**：严格对齐 PC 端的列序与配色 —— `INFO │ 时间.毫秒 │ 正文`，
  INFO 绿、时间蓝、WARN 橙、ERROR 红；不用斑马纹
