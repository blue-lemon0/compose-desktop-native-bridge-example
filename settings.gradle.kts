pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven {
            name = "ComposeDesktopNative"
            url = uri("https://maven.pkg.github.com/bitsycore/compose-desktop-native")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.token").orNull ?: ""
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/jetbrains") }
    }
    plugins {
        // -PbridgeVersion=… overrides (e.g. locally-published snapshot).
        val bridgeVersion = providers.gradleProperty("bridgeVersion").get()
        val cmpVersion = providers.gradleProperty("composeMultiplatformVersion").get()
        val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
        val agpVersion = providers.gradleProperty("agpVersion").get()

        kotlin("multiplatform") version kotlinVersion apply false
        id("org.jetbrains.kotlin.android") version kotlinVersion apply false
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion apply false

        id("com.bitsycore.compose-desktop-native.bridge") version bridgeVersion apply false

        id("org.jetbrains.compose") version cmpVersion apply false

        id("com.android.application") version agpVersion apply false
        id("com.android.kotlin.multiplatform.library") version agpVersion apply false
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven {
            name = "ComposeDesktopNative"
            url = uri("https://maven.pkg.github.com/bitsycore/compose-desktop-native")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.token").orNull ?: ""
            }
            // The skiko fork lives in its own repo (below), not here.
            content { excludeGroup("com.bitsycore.skiko") }
        }
        // Windows (mingwX64) renders through the bitsycore skiko fork, published to
        // its own GitHub Packages repo as com.bitsycore.skiko:skiko / :skiko-mingwx64.
        // (macOS/Linux use the official org.jetbrains.skiko from Maven Central.)
        maven {
            name = "ComposeDesktopNativeSkiko"
            url = uri("https://maven.pkg.github.com/bitsycore/skiko")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.token").orNull ?: ""
            }
            content { includeGroup("com.bitsycore.skiko") }
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/jetbrains") }
    }
}

rootProject.name = "bubble-wrap"

include(":shared")
include(":androidApp")
