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
        }
    }
}
