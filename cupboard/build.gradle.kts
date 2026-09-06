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
    // Explicit because it stops applying itself the moment any dependsOn edge
    // is set by hand, and `fileMain` below sets two. Without this the whole
    // default hierarchy silently vanishes: nativeMain stops reaching commonMain,
    // appleMain is never created, and the native factories fall out of the klib
    // while the build still passes.
    applyDefaultHierarchyTemplate()

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
        // `fileMain`: jvm plus every native target, the ones with a real
        // filesystem under them. Where `.cupboard` bundles are laid out and
        // where Cupboard.editor() builds its stores, so neither is written per
        // platform. Not android or web: neither opens a folder the user picked.
        //
        // Wired by hand rather than through applyDefaultHierarchyTemplate: a
        // group added under `common` there comes out a *sibling* of the
        // template's own `nativeMain`, so nativeMain cannot see it. Two
        // dependsOn edges say the intended shape and nothing else moves.
        //
        // kotlinx-io is already in the graph under kstore-file; naming it here
        // is just making the direct use direct.
        val fileMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.kstore.file)
                implementation(libs.kotlinx.io.core)
            }
        }
        jvmMain.get().dependsOn(fileMain)
        nativeMain.get().dependsOn(fileMain)
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            implementation(libs.kstore.file)
            implementation(libs.kotlinx.io.core)
        }
    }
}
