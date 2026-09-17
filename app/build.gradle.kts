plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.guitarcoach.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guitarcoach.app"
        minSdk = 26
        targetSdk = 36
        // 版本注入：versionCode=git 提交数（本地与 CI 同源，单调递增——固定缺省值会被 CI 包降级拒装）；
        // versionName：tag 构建传 -PpkgVersionName，本地缺省随当前开发版本。
        val gitCommitCount = runCatching {
            providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
        }.getOrElse { 1 }
        versionCode = (project.findProperty("pkgVersionCode")?.toString()?.toInt()) ?: gitCommitCount
        versionName = (project.findProperty("pkgVersionName") as String?) ?: "0.2.10"

        ndk {
            // 适配基线：小米 14（骁龙 8 Gen 3 为 64 位专用 SoC），只出 arm64-v8a；
            // 其他机型适配后置（见 docs/开发方案.md §9）
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    // Room schema 导出入库（后续迁移测试用）
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // 相机（M3 视觉教练阶段启用界面，依赖先备好）
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // 端侧手部 21 关键点检测（MediaPipe）
    implementation(libs.mediapipe.tasks.vision)

    // LLM 网关
    implementation(libs.okhttp)
    // Guitar Pro 导入（F205）
    implementation(libs.alphatab)
    // 音源分离（F602 v2：Spleeter 2stems ONNX）
    implementation(libs.onnxruntime.android)
    // F602 端侧转写
    implementation(libs.tensorflow.lite)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Room：对话历史（F107）与练习记录（F106）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
