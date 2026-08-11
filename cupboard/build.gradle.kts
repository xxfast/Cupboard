import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// The UI-free core: document model, presenters, view models. Compose *runtime*
// only (molecule runs the presenters as flows), never foundation/material3/ui.
// That is what lets this module carry mingwX64 for the WinUI shell, which no
// Compose UI artifact publishes. Anything that touches a pixel goes in :cupboard:ui.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    macosArm64()

    // The .NET/WinUI shell links this module through :winuiApp.
    mingwX64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
       namespace = "io.github.xxfast.cupboard.core"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }

    sourceSets {
        commonMain.dependencies {
            // Runtime only: @Composable presenters, no UI toolkit. See the module comment.
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutinesCore)
            // Runs the composable presenters as plain state flows, off any UI.
            implementation(libs.molecule.runtime)
            // `api`: EditorViewModel takes a KStore<Document>, so shells that
            // build one need the type. Core only up here: kstore-file has no
            // wasmJs variant, so the file-backed factories live in the source
            // sets below and web keeps compiling.
            api(libs.kstore)
        }
        // Where Cupboard.editor() builds the store, so no shell has to.
        jvmMain.dependencies {
            implementation(libs.kstore.file)
        }
        nativeMain.dependencies {
            implementation(libs.kstore.file)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            implementation(libs.kstore.file)
        }
    }
}
