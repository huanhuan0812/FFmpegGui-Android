import com.android.builder.model.PROPERTY_SIGNING_STORE_FILE
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.findKaptConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("kotlinx-serialization") // 添加序列化插件
}

android {
    namespace = "com.huanhuan.ffmpeggui"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }



    defaultConfig {
        applicationId = "com.huanhuan.ffmpeggui"
        minSdk = 26
        targetSdk = 36

        // 从环境变量或命令行参数读取版本号
        versionCode = project.findProperty("versionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("versionName")?.toString() ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ABI 拆分配置
    splits {
        abi {
            isEnable = true
            reset()

            // 支持命令行参数指定 ABI
            if (project.hasProperty("abiFilter")) {
                include(project.property("abiFilter") as String)
            } else {
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }

            isUniversalApk = false  // 单独生成 universal APK
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // 配置签名（从环境变量读取）
            signingConfig = if (System.getenv("CI") == "true") {
                signingConfigs.create("release") {
                    keyAlias = System.getenv("KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                    storeFile = file(System.getenv("KEYSTORE_PATH") ?: "keystore.jks")
                    storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                }
            } else {
                signingConfigs.getByName("debug")
            }
        }

        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 打包选项
    packaging {
        resources {
            excludes += "/META-INF/{AL2.,GPL3.0}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    configurations.all {
        resolutionStrategy {
            // 强制使用新版本的 annotations
            force("org.jetbrains:annotations:23.0.0")
            // 或者排除旧版本
            exclude(group = "com.intellij", module = "annotations")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    // FFmpeg 依赖 - 注意这个库可能已经包含多架构支持
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-16kb:6.1.7"){
        exclude(group = "com.intellij", module = "annotations")
    }

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.ui)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.exoplayer)
    implementation(libs.coil.compose)

    implementation(libs.androidx.room.runtime)
    //implementation(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation("org.semver4j:semver4j:5.3.0")

    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:editor:4.6.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation(libs.androidx.compose.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}