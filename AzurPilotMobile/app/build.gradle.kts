plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.screenshot)
}

android {
    namespace = "com.azurpilot.mobile"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.azurpilot.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.0.3"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Compose 预览截图测试需要在这里显式开一次（gradle.properties 里的那个是给 AGP 看的）
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.miuix.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)

    // Compose 预览截图测试：用 layoutlib 离屏渲染 @Preview，不需要模拟器
    screenshotTestImplementation(platform(libs.androidx.compose.bom))
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)

    // 纯 JVM 单元测试（src/test）。用来验 ConfigCache 的磁盘格式 ——
    // 那类代码坏掉是**静默**的：JSON 一坏，所有读都返回 null，
    // 表现成「缓存从来没生效过」，在手机上根本看不出哪里错了。
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

// ─────────────────────────────────────────────────────────────
// 绕开坏掉的 Gradle test worker 的单元测试入口
//
// 本机 `:app:testDebugUnitTest` **起不来**：worker 子进程刚启动就退出，
// 报 `ClassNotFoundException: worker.gradle.process.internal.worker.GradleWorkerMain`
// 并伴随 `java.io.IOException: 管道正在被关闭`。已排除的猜想：
//   - gradle-worker-main / gradle-worker-process 的 jar 都在，没缺
//   - 换成 ASCII 路径（目录联接）跑一样挂 → 不是中文路径的锅
//   - `--no-daemon` 一样挂
// 但 `compileDebugUnitTestKotlin` 是**好的**（测试能正常编译）。
// 环境是 Gradle 9.5 + JDK 25，怀疑是 worker 与 JDK 25 的兼容问题。
//
// 所以给一个直接 fork java 的入口（JavaExec 不走 worker API）：
//     gradlew :app:unitTest
// ─────────────────────────────────────────────────────────────
tasks.register<JavaExec>("unitTest") {
    group = "verification"
    description = "直接跑 JVM 单元测试（绕开起不来的 Gradle test worker）"

    dependsOn("compileDebugUnitTestKotlin")

    val testTask = tasks.named<Test>("testDebugUnitTest")
    classpath = files({ testTask.get().classpath })
    mainClass.set("org.junit.runner.JUnitCore")
    // 新增测试类时要手动加到这里 —— JUnitCore 不会自己发现
    args = listOf(
        "com.azurpilot.mobile.data.ConfigCacheTest",
        "com.azurpilot.mobile.data.UpdateCheckerTest",
    )
}
