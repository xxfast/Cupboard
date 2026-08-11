import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinNativeNuget)
}

// The .NET export module: packs :cupboard as a NuGet package the C# hosts under
// this folder (Shared/, WinUiApp/) consume. macosArm64 rides along so the shared
// lib and the generated interop can be built and inspected from a Mac; the real
// target is mingwX64.
kotlin {
    applyDefaultHierarchyTemplate()

    listOf(
        mingwX64(),
        macosArm64(),
    ).forEach { target ->
        target.binaries {
            sharedLib(listOf(DEBUG, RELEASE)) {
                baseName = "cupboard"
            }
        }
    }

    sourceSets {
        val nativeMain by getting {
            dependencies {
                // Core only. :cupboard:ui has no mingw variant, and the canvas is
                // WinUI's job on this platform anyway.
                implementation(project(":cupboard"))
                implementation(libs.molecule.runtime)
                implementation(libs.kotlinx.coroutinesCore)
            }
        }
    }
}

nuget {
    publish {
        packageId = "Cupboard.Kotlin"
        version = "0.1.0"
        authors = "xxfast"
        description = "Cupboard shared editor core for .NET hosts"
        // The document model and editor state live under this root, so the
        // reachability closure admits them without local DTO projections.
        rootPackage = "io.github.xxfast.cupboard"
    }
}
