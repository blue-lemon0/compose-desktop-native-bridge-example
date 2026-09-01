import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
    id("com.bitsycore.compose-desktop-native.bridge")
}

kotlin {
    jvm()

    android {
        namespace = "com.bitsycore.bubblewrap.shared"
        compileSdk = 37
        minSdk = 24
        // For the KMP library plugin, Compose Multiplatform resources need
        // Android resource support enabled to be routed into the AAR/APK.
        androidResources.enable = true
    }

    // Compose Desktop Native
    macosArm64()
    linuxX64()
    linuxArm64()
    mingwX64()

    // The bridge plugin reports the exact Compose versions the port tracks, so
    // we never hand-match them against the release (material3 is versioned
    // separately upstream - an easy mismatch this removes). `.version` is the
    // port klib version being substituted.
    val cmp = composeDesktopNative.compose
    val cmpMaterial3 = composeDesktopNative.composeMaterial3
    val cmpRuntime = composeDesktopNative.composeRuntime
    val cdn = composeDesktopNative.version

    sourceSets {
        commonMain.dependencies {
            // Official CMP coords: the bridge substitutes these for the port's
            // klibs on the native desktop targets; android/jvm resolve them as-is.
            implementation("org.jetbrains.compose.runtime:runtime:$cmpRuntime")
            implementation("org.jetbrains.compose.ui:ui:$cmp")
            implementation("org.jetbrains.compose.ui:ui-geometry:$cmp")
            implementation("org.jetbrains.compose.ui:ui-graphics:$cmp")
            implementation("org.jetbrains.compose.ui:ui-text:$cmp")
            implementation("org.jetbrains.compose.ui:ui-unit:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation:$cmp")
            implementation("org.jetbrains.compose.foundation:foundation-layout:$cmp")
            implementation("org.jetbrains.compose.animation:animation-core:$cmp")
            implementation("org.jetbrains.compose.components:components-resources:$cmp")
            implementation("org.jetbrains.compose.material3:material3:$cmpMaterial3")
        }
        nativeMain.dependencies {
            // The port's own windowing / main-loop API (not substituted); tracks
            // the substituted version the bridge is using. Renamed from :window in 0.4.0.
            implementation("com.bitsycore.compose:desktop-native-window:$cdn")
        }
        jvmMain.dependencies {
            // Compose Desktop entry point
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    // Keep the Res package from the single-module days so App.kt's
    // bubble_wrap.generated.resources imports stay stable.
    packageOfResClass = "bubble_wrap.generated.resources"
    // Library module consumed by :androidApp - the generated Res class must be
    // public so the consumer can access the resource accessors.
    publicResClass = true
}

compose.desktop {
    // Upstream Compose Desktop (jvm) entry point.
    application {
        mainClass = "bubblewrap.MainJvmKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "BubbleWrap"
            packageVersion = "1.0.0"
        }
    }
    // compose-desktop-native entry point - the bridge plugin declares an
    // executable with this entry point on every native desktop target.
    native {
        entryPoint = "bubblewrap.main"
    }
}
