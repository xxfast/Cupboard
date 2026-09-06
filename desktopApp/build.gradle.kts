import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    // api-exposes the :cupboard core, so the document model comes along.
    implementation(project(":cupboard:ui"))

    // The document lifecycle takes paths as kotlinx-io's, and `:cupboard` keeps
    // that an implementation detail, so the shell names it for itself.
    implementation(libs.kotlinx.io.core)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "io.github.xxfast.cupboard.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "io.github.xxfast.cupboard"
            packageVersion = "1.0.0"
        }
    }
}