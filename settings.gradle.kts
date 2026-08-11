// Not "Cupboard": the root project name and the :cupboard module would both
// generate a getCupboard() typesafe accessor, which collides once the CuP and
// emoji-kt included builds (which enable typesafe accessors) join the composite.
rootProject.name = "Cupboard-Workspace"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Forked submodules: CuP (presentation runtime) and its emoji dependency, both
// carrying macosArm64 additions on ir/macos-targets. Composite substitution makes
// net.kodein.cup:* and org.kodein.emoji:* resolve from these local builds.
includeBuild("emoji-kt")
includeBuild("cup/Compose-Ur-Pres")

include(":desktopApp")
include(":macosApp")
include(":cupboard")
include(":cupboard:ui")
include(":winuiApp")