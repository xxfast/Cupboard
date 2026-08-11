plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    macosArm64 {
        binaries.framework {
            baseName = "CupboardCanvas"
            // Both halves: EditorHost's API surface spans the core (Document,
            // EditorViewModel) and the UI module (EditorCanvas, PresentationPlayer).
            export(project(":cupboard"))
            export(project(":cupboard:ui"))
        }
    }

    sourceSets {
        macosMain.dependencies {
            api(project(":cupboard"))
            api(project(":cupboard:ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
        }
    }
}
