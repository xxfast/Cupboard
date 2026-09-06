package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Document

/** The Kotlin, Compose and CuP versions an exported project is pinned to. */
private const val KotlinVersion: String = "2.4.10"
private const val ComposeVersion: String = "1.11.1"
private const val CupVersion: String = "1.0.0-Beta-17"

/** The package an export falls back to, and the name a nameless deck takes. */
private const val DefaultPackage: String = "presentation"

/** A package name Kotlin will take: lowercase, dotted, and starting on a letter. */
private val PackagePattern = Regex("[a-z][a-z0-9_.]*")

/**
 * One file of an exported project: where it goes under the project root, and
 * what is in it.
 *
 * A path, not a handle: writing these to disk is the shell's job, so the
 * generator stays a pure function and the same list serves every host. Forward
 * slashes on every platform, since that is what a Gradle project's layout is
 * written in.
 */
data class ExportedFile(val path: String, val contents: String)

/**
 * The deck as a standalone Gradle project that plays it with CuP: six files,
 * ready to be written out and run.
 *
 * The export is a translation, not a re-implementation: a slide's elements come
 * out as composables laid out on a 1920x1080 board scaled into CuP's slide, and
 * the deck's builds come out as CuP steps. What Cupboard draws from a parsed
 * source (diagrams, equations) and what it hasn't loaded yet (images) export as
 * placeholders carrying a TODO, so the project always compiles.
 */
fun Document.toCupProject(packageName: String = DefaultPackage): List<ExportedFile> {
    val target: String = packageName.orDefaultPackage()
    val sources = "src/commonMain/kotlin/${target.replace('.', '/')}"

    return listOf(
        ExportedFile("settings.gradle.kts", settingsSource(name.slug())),
        ExportedFile("build.gradle.kts", buildSource(target)),
        ExportedFile("gradle.properties", GradlePropertiesSource),
        ExportedFile("README.md", readmeSource(name)),
        ExportedFile("$sources/Main.kt", mainSource(this, target)),
        ExportedFile("$sources/Slides.kt", slidesSource(this, target)),
        ExportedFile("$sources/Support.kt", supportSource(target)),
    )
}

/** This name, or [DefaultPackage] when Kotlin wouldn't take it as a package. */
internal fun String.orDefaultPackage(): String =
    if (PackagePattern.matchEntire(this) != null) this else DefaultPackage

/**
 * The name as a Gradle project name: lowercase, punctuation and spaces run
 * together into single dashes. A name with nothing usable in it falls back, the
 * same way a bad package name does.
 */
internal fun String.slug(): String {
    val slug: String = lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .split("-")
        .filter { it.isNotEmpty() }
        .joinToString("-")
    return slug.ifEmpty { DefaultPackage }
}

/** `pluginManagement` first, because Gradle will not read a settings file that buries it. */
private fun settingsSource(name: String): String = """
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "$name"
""".trimStart()

private fun buildSource(packageName: String): String = """
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "$KotlinVersion"
    id("org.jetbrains.compose") version "$ComposeVersion"
    id("org.jetbrains.kotlin.plugin.compose") version "$KotlinVersion"
    id("net.kodein.cup") version "$CupVersion"
}

cup {
    targetDesktop(mainClass = "$packageName.MainKt")
    targetWeb()
}

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        // The CuP plugin turns Compose resources on, and its generated `Res`
        // class does not compile without these.
        implementation(compose.components.resources)
        implementation("net.kodein.cup:cup:$CupVersion")
    }
}
""".trimStart()

private val GradlePropertiesSource: String = """
kotlin.code.style=official
org.gradle.jvmargs=-Xmx4g
""".trimStart()

private fun readmeSource(name: String): String = """
# $name

Exported from Cupboard. There is no Gradle wrapper here yet: run `gradle wrapper` first.

Then `./gradlew run` for the desktop presentation, or `./gradlew wasmJsBrowserDevelopmentRun` for the web one.
""".trimStart()

/** `Main.kt`: the presentation, and every exported slide in presentation order. */
private fun mainSource(document: Document, packageName: String): String {
    val out = SourceWriter()
    out.import(
        "net.kodein.cup.Presentation",
        "net.kodein.cup.Slides",
        "net.kodein.cup.cupApplication",
    )

    out.block("fun main() = cupApplication(title = ${kotlinString(document.name)}) {", "}") {
        out.block("Presentation(", ")") {
            out.block("slides = Slides(", "),") {
                document.exportedSlides().forEachIndexed { index, _ ->
                    out.line("${slideIdentifier(index)},")
                }
            }
        }
    }

    return out.toFile(packageName)
}
