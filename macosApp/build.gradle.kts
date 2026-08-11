plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    macosArm64 {
        binaries.framework {
            baseName = "CupboardCanvas"
        }
    }

    sourceSets {
        macosMain.dependencies {
            implementation(project(":cupboard"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
            // Per-shell: kstore-file has no wasmJs variant, so :cupboard only carries the core.
            implementation(libs.kstore.file)
        }
    }
}
