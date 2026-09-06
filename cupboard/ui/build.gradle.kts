import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Everything that draws: the slide canvas, element renderers, the editor screen
// and the CuP play layer. Same targets as :cupboard minus mingwX64, because no
// Compose UI artifact publishes for it.
kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Cupboard"
            isStatic = true
            // MainViewController hands out EditorViewModel and Document.
            export(project(":cupboard"))
        }
    }

    jvm()

    macosArm64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
       namespace = "io.github.xxfast.cupboard.ui"
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

    // Default hierarchy plus two groups of our own.
    //
    // "cup" is CuP's targets only (jvm, js, wasmJs, macosArm64): android and iOS
    // must never see CuP. "skiko" is every target Compose draws with Skia on,
    // which is all of them but android: PNG encoding has no common API, and the
    // one android needs is its own framework's. jvm sits in both, which is what
    // a hierarchy is for.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("cup") {
                withJvm()
                withJs()
                withWasmJs()
                withMacosArm64()
            }

            group("skiko") {
                withJvm()
                withJs()
                withWasmJs()
                withMacosArm64()
                withIosArm64()
                withIosSimulatorArm64()
            }
        }
    }

    sourceSets {
        val cupMain by getting {
            dependencies {
                // Substituted to the cup/Compose-Ur-Pres included build.
                implementation("net.kodein.cup:cup:1.0.0-Beta-17")
            }
        }

        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            // `api`: shells take EditorViewModel/Document from the core and hand
            // them straight to the composables here, so they get it transitively.
            api(project(":cupboard"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            // Code element syntax highlighting, rendered into an AnnotatedString.
            implementation(libs.highlights)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            // skiko natives for offscreen render tests
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
compose.resources {
    packageOfResClass = "io.github.xxfast.cupboard.resources"
}
