import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

apply(from = "download.gradle")


val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        load(FileInputStream(versionPropsFile))
    }
}

val versionMajor = versionProps["VERSION_MAJOR"]?.toString()?.toInt() ?: 0
val versionMinor = versionProps["VERSION_MINOR"]?.toString()?.toInt() ?: 0
val versionPatch = versionProps["VERSION_PATCH"]?.toString()?.toInt() ?: 0
val appName = "drducbook"
val projectVersionName = versionProps["VERSION_NAME"]
    ?.toString()
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: "$versionMajor.$versionMinor.$versionPatch"
val projectVersionCode = versionProps["VERSION_CODE"]?.toString()?.toIntOrNull() ?: 32641
val localAiCmakeFile = file("src/main/cpp/local-ai/CMakeLists.txt")
val localAiLlamaSource = file("src/main/cpp/local-ai/third_party/llama.cpp/CMakeLists.txt")
val buildLocalAiRuntime = !project.hasProperty("skipLocalAiRuntime") &&
    localAiCmakeFile.isFile &&
    localAiLlamaSource.isFile
val disableAbiSplits = project.findProperty("disableAbiSplits") == "true"
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::isFile)?.inputStream()?.use(::load)
}

fun publicBuildValue(name: String): String =
    System.getenv(name) ?: localProperties.getProperty(name).orEmpty()

fun quotedBuildValue(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    compileSdk = 37
    namespace = "com.drducbook.app"
    if (buildLocalAiRuntime) {
        ndkVersion = "27.0.12077973"
    }

    signingConfigs {
        if (project.hasProperty("RELEASE_STORE_FILE")) {
            create("myConfig") {
                storeFile = file(project.property("RELEASE_STORE_FILE") as String)
                storePassword = project.property("RELEASE_STORE_PASSWORD") as String
                keyAlias = project.property("RELEASE_KEY_ALIAS") as String
                keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.drducbook.app"
        minSdk = 26
        targetSdk = 37
        versionCode = System.getenv("COMMIT_NUMBER")?.toInt()?.let { 10000 + it }
            ?: projectVersionCode
        versionName = System.getenv("APP_VERSION_NAME") ?: projectVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", quotedBuildValue(publicBuildValue("SUPABASE_URL")))
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            quotedBuildValue(publicBuildValue("SUPABASE_PUBLISHABLE_KEY"))
        )
        buildConfigField(
            "String",
            "GOOGLE_AUTH_CLIENT_ID",
            quotedBuildValue(publicBuildValue("GOOGLE_AUTH_CLIENT_ID"))
        )
        buildConfigField(
            "String",
            "GOOGLE_DRIVE_CLIENT_ID",
            quotedBuildValue(publicBuildValue("GOOGLE_DRIVE_CLIENT_ID"))
        )
        buildConfigField(
            "String",
            "DRDUC_UPDATE_REPOSITORY",
            quotedBuildValue(publicBuildValue("DRDUC_UPDATE_REPOSITORY"))
        )

        if (buildLocalAiRuntime) {
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DANDROID_STL=c++_shared",
                    )
                }
            }
        }

        buildConfigField("String", "Cronet_Version", "\"${project.findProperty("CronetVersion")}\"")
        buildConfigField(
            "String",
            "Cronet_Main_Version",
            "\"${project.findProperty("CronetMainVersion")}\""
        )

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.incremental" to "true",
                    "room.expandProjection" to "true",
                    "room.schemaLocation" to "$projectDir/schemas"
                )
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    if (buildLocalAiRuntime) {
        externalNativeBuild {
            cmake {
                path = localAiCmakeFile
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.getByName("myConfig")
            }
            manifestPlaceholders["app_name"] = "@string/app_name"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "legacy-compat-rules.pro",
                "cronet-proguard-rules.pro"
            )
        }
        create("noR8") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-noR8"
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            if (project.hasProperty("RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.getByName("myConfig")
            }
            manifestPlaceholders["app_name"] = "@string/app_name"
            versionNameSuffix = "_debug"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "legacy-compat-rules.pro",
                "cronet-proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = !disableAbiSplits
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    flavorDimensions += "mode"
    productFlavors {
        create("app") {
            dimension = "mode"
            manifestPlaceholders["APP_CHANNEL_VALUE"] = "app"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes.add("META-INF/*")
        jniLibs.useLegacyPackaging = true
        jniLibs.keepDebugSymbols.add("**/libcloudflared.so")
    }

    androidResources {
        noCompress += listOf("onnx", "qtdict")
        ignoreAssetsPattern =
            "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:tts_models"
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    lint {
        checkDependencies = providers.gradleProperty("lintCheckDependencies")
            .orNull
            ?.toBooleanStrictOrNull()
            ?: true
        providers.gradleProperty("lintCheckOnly")
            .orNull
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.let { checkOnly += it }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.generateKotlin", "false")
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    coreLibraryDesugaring(libs.desugar)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.bundles.androidTest)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coroutines)
    implementation(libs.core.ktx)
    implementation(libs.appcompat.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.preference.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.flexbox)
    implementation(libs.gson)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.service)
    implementation(libs.media.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.splitties.appctx)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)
    implementation(libs.liveeventbus)
    implementation(libs.jsoup)
    implementation(libs.json.path)
    implementation(libs.jieba.analysis)
    implementation(libs.jsoupxpath)
    implementation(libs.intellij.markdown)
    implementation(project(":modules:book"))
    implementation(project(":modules:rhino"))
    implementation(libs.okhttp)
    implementation(libs.onnxruntime.android)
    implementation(libs.onnxruntime.extensions.android)
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"))
    implementation(fileTree(mapOf("dir" to "cronetlib", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.protobuf.javalite)
    implementation(libs.glide.glide)
    implementation(libs.glide.okhttp)
    ksp(libs.glide.ksp)
    implementation(libs.androidsvg)
    implementation(libs.glide.svg)
    implementation(libs.glide.recyclerview)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.zxing.lite)
    implementation(libs.colorpicker)
    implementation(libs.colorpicker.compose)
    implementation(libs.libarchive)
    implementation(libs.commons.text)
    implementation(libs.markwon.core)
    implementation(libs.markwon.image.glide)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.quick.chinese.transfer.core)
    implementation(libs.hutool.crypto)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.google.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.accompanist.webview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.compose.materialIcons)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.reorderable)
    implementation(libs.material.kolor)
    implementation(libs.haze.core)
    implementation(libs.haze.materials)
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.core)
    implementation(libs.capsule)
    implementation(libs.backdrop)
    implementation(libs.lyricViewx)
    implementation(libs.timber)
}
