plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    macosArm64 {
        binaries.executable {
            entryPoint = "main"
        }
    }

    sourceSets {
        macosMain.dependencies {
            implementation(project(":cupboard"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
        }
    }
}
