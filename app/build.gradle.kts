import com.android.builder.model.PROPERTY_SIGNING_STORE_FILE

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // 打包选项
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
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
    implementation("io.github.jamaismagic.ffmpeg:ffmpeg-kit-lts-16kb:6.1.7")

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}