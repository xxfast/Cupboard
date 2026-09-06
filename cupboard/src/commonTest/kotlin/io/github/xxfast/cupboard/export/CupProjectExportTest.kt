package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.BuildKind
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineRange
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.resized
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.setSlideSkipped
import io.github.xxfast.cupboard.document.stepCount
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CupProjectExportTest {
    private fun List<ExportedFile>.contentsOf(path: String): String =
        first { it.path == path }.contents

    @Test
    fun theExportIsAWholeGradleProject() {
        val files = sampleDocument().toCupProject()
        assertEquals(
            listOf(
                "settings.gradle.kts",
                "build.gradle.kts",
                "gradle.properties",
                "README.md",
                "src/commonMain/kotlin/presentation/Main.kt",
                "src/commonMain/kotlin/presentation/Slides.kt",
                "src/commonMain/kotlin/presentation/Support.kt",
            ),
            files.map { it.path },
        )
    }

    @Test
    fun aSlideWithActionsSaysSoOnceAndExportsTheRest() {
        val slides = sampleDocument().toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")
        val note = "// TODO(cupboard): action builds are not exported yet"

        // The pipeline slide swells its Draw stage, and it is the only one that does.
        assertEquals(1, slides.split("\n").count { it.trim() == note })
        assertContains(slides, "Draw")
    }

    @Test
    fun everyFileIsWrittenTheWayKotlinIsWritten() {
        for (file in sampleDocument().toCupProject()) {
            assertTrue(file.contents.endsWith("\n"), "${file.path} has no final newline")
            assertFalse(file.contents.contains("\t"), "${file.path} has a tab in it")
            assertFalse(
                file.contents.lines().any { it != it.trimEnd() },
                "${file.path} has trailing whitespace",
            )
        }
    }

    @Test
    fun everySlideIsExportedWithItsSteps() {
        val document = sampleDocument()
        val slides = document.toCupProject().contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertEquals(document.slides.size, slides.occurrences("by Slide("))
        document.slides.forEachIndexed { index, slide ->
            // Prefix only: a slide with a transition carries its specs on the
            // same line, which `aSlidesTransitionRidesOutWithIt` covers.
            assertContains(
                slides,
                "val slide${index + 1} by Slide(stepCount = ${slide.stepCount()}",
            )
        }
    }

    /**
     * The transition goes out with the slide it plays on the way out of, as CuP's
     * nearest set. Best effort: the export is a project that plays, not a copy of
     * our own player.
     */
    @Test
    fun aSlidesTransitionRidesOutWithIt() {
        val document = sampleDocument()
        val slides = document.toCupProject().contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        val magic: Int = document.slides.indexOfFirst {
            it.transition?.kind == TransitionKind.MagicMove
        }
        val push: Int = document.slides.indexOfFirst { it.transition?.kind == TransitionKind.Push }
        assertTrue(magic != -1 && push != -1)

        assertContains(slides, "import net.kodein.cup.SlideSpecs")
        assertContains(
            slides,
            "val slide${magic + 1} by Slide(stepCount = 1, " +
                "specs = SlideSpecs(endTransitions = TransitionSet.fade)) { step ->",
        )
        assertContains(
            slides,
            "specs = SlideSpecs(endTransitions = " +
                "TransitionSet.moveHorizontal(LayoutDirection.Ltr))",
        )
        // A slide on the deck's default says nothing at all.
        assertEquals(2, slides.occurrences("specs = SlideSpecs("))
    }

    /** The board is the deck's own slide, not the 16:9 one every deck used to be on. */
    @Test
    fun theBoardCarriesTheDecksSlideSize() {
        val document = sampleDocument().resized(1440f, 1080f, scaleContent = true)
        val support =
            document.toCupProject().contentsOf("src/commonMain/kotlin/presentation/Support.kt")

        assertContains(support, "const val BoardWidth: Float = 1440.0f")
        assertContains(support, "const val BoardHeight: Float = 1080.0f")
    }

    @Test
    fun mainListsEverySlideInOrder() {
        val document = sampleDocument()
        val main = document.toCupProject().contentsOf("src/commonMain/kotlin/presentation/Main.kt")

        val listed: String = main.substringAfter("slides = Slides(").substringBefore(")")
        assertEquals(
            List(document.slides.size) { "slide${it + 1}," },
            listed.lines().map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    @Test
    fun aSkippedSlideIsLeftOut() {
        val document = sampleDocument()
        val skipped = document.setSlideSkipped(document.slides[1].id, true)
        val slides = skipped.toCupProject().contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertEquals(document.slides.size - 1, slides.occurrences("by Slide("))
        assertFalse(slides.contains(document.slides[1].notes.ifEmpty { "Agenda" }))
    }

    @Test
    fun aDeckWithEverySlideSkippedExportsEverySlide() {
        val document = sampleDocument()
        val skipped = document.slides.fold(document) { deck, slide ->
            deck.setSlideSkipped(slide.id, true)
        }
        val slides = skipped.toCupProject().contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertEquals(document.slides.size, slides.occurrences("by Slide("))
    }

    @Test
    fun stringsAreEscaped() {
        assertEquals("\"a\\\"b\"", kotlinString("a\"b"))
        assertEquals("\"a\\\\b\"", kotlinString("a\\b"))
        assertEquals("\"a\\\$b\"", kotlinString("a\$b"))
        assertEquals("\"a\\nb\"", kotlinString("a\nb"))
        assertEquals("\"a\\tb\"", kotlinString("a\tb"))
    }

    @Test
    fun codeSourcesAreNeverRawStrings() {
        val slides = sampleDocument().toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")
        assertFalse(slides.contains("\"\"\""))
    }

    @Test
    fun aSteppedCodeBlockIsExportedAsAWhenOverTheSlidesSteps() {
        val code = CodeElement(
            frame = Frame(0f, 0f, 100f, 100f),
            code = "one\ntwo\nthree",
            steps = listOf(
                CodeStep(reveal = listOf(LineRange(1, 1))),
                CodeStep(highlight = listOf(LineRange(3, 3))),
            ),
        )
        val slide = Slide(
            elements = listOf(code),
            builds = listOf(Build(code.id), Build(code.id, elementStep = 1)),
        )
        val slides = Document(slides = listOf(slide)).toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertContains(slides, "lines = when (step) {")
        assertEquals(slide.stepCount(), slides.occurrences("-> listOf("))
        // The last step highlights line 3, so the two above it draw dimmed.
        assertEquals(2, slides.occurrences("dimmed = true"))
    }

    /** An element nothing brings in is on the slide until the build that takes it away. */
    @Test
    fun anOutBuildIsExportedAsTheRangeItIsVisibleOver() {
        val box = ShapeElement(frame = Frame(0f, 0f, 100f, 100f), label = "gone")
        val caption = TextElement(frame = Frame(0f, 0f, 100f, 100f), text = "stays")
        val slide = Slide(
            elements = listOf(box, caption),
            builds = listOf(Build(box.id, kind = BuildKind.Out)),
        )
        val slides = Document(slides = listOf(slide)).toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertContains(slides, "Appear(step in 0 until 1) {")
        // The caption has no builds at all, so it is drawn without a wrapper.
        assertEquals(1, slides.occurrences("Appear("))
    }

    /** The effect and the timing the deck was written with go out with the build. */
    @Test
    fun aBuildsEffectAndTimingRideOutWithIt() {
        val text = TextElement(frame = Frame(0f, 0f, 100f, 100f), text = "hi")
        val slide = Slide(
            elements = listOf(text),
            builds = listOf(
                Build(text.id, effect = BuildEffect.Pop, durationMs = 250, delayMs = 100),
            ),
        )
        val files = Document(slides = listOf(slide)).toCupProject()

        assertContains(
            files.contentsOf("src/commonMain/kotlin/presentation/Slides.kt"),
            "Appear(step >= 1, effect = BuildEffect.Pop, durationMs = 250, delayMs = 100) {",
        )
        val support = files.contentsOf("src/commonMain/kotlin/presentation/Support.kt")
        assertContains(support, "BuildEffect.Pop ->")
        assertContains(support, "scaleIn(tween(durationMs, delayMs))")
        assertContains(support, "BuildEffect.Appear, BuildEffect.Typewriter -> EnterTransition.None")
    }

    /** Piece delivery is one line per slide, not something the export plays. */
    @Test
    fun aSlideDeliveredInPiecesSaysSoOnce() {
        val slides = sampleDocument().toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")
        val note = "// TODO(cupboard): builds arrive whole, not piece by piece, yet"

        assertEquals(1, slides.split("\n").count { it.trim() == note })
    }

    @Test
    fun anUnsteppedCodeBlockIsExportedWhole() {
        val code = CodeElement(frame = Frame(0f, 0f, 100f, 100f), code = "one\ntwo")
        val slides = Document(slides = listOf(Slide(elements = listOf(code)))).toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertFalse(slides.contains("when (step)"))
        assertContains(slides, "lines = listOf(")
        assertContains(slides, "CodeLine(1, \"one\"),")
        assertContains(slides, "CodeLine(2, \"two\"),")
    }

    @Test
    fun listsAreExportedWithTheirMarkers() {
        val bullets = TextElement(
            frame = Frame(0f, 0f, 100f, 100f),
            text = "first\nsecond",
            listStyle = ListStyle.Bullet,
        )
        val numbered = bullets.copy(text = "first\nsecond", listStyle = ListStyle.Numbered)

        val document = Document(
            slides = listOf(Slide(elements = listOf(bullets)), Slide(elements = listOf(numbered))),
        )
        val slides = document.toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")

        assertContains(slides, "\"• first\\n• second\"")
        assertContains(slides, "\"1. first\\n2. second\"")
    }

    @Test
    fun theProjectIsNamedAfterTheDeck() {
        val settings = Document(name = "My Deck: Take 2!").toCupProject()
            .contentsOf("settings.gradle.kts")
        assertContains(settings, "rootProject.name = \"my-deck-take-2\"")
    }

    @Test
    fun aDeckWithNoUsableNameStillNamesItsProject() {
        val settings = Document(name = "  ").toCupProject().contentsOf("settings.gradle.kts")
        assertContains(settings, "rootProject.name = \"presentation\"")
    }

    @Test
    fun aPackageNameKotlinWouldRejectFallsBack() {
        assertEquals("presentation", "Deck Slides".orDefaultPackage())
        assertEquals("presentation", "2decks".orDefaultPackage())
        assertEquals("presentation", "".orDefaultPackage())
        assertEquals("com.example.deck", "com.example.deck".orDefaultPackage())

        val files = Document(name = "Deck").toCupProject(packageName = "Not A Package")
        assertTrue(files.any { it.path == "src/commonMain/kotlin/presentation/Main.kt" })
    }

    @Test
    fun aPackageNameKotlinTakesIsUsedForBothTheLayoutAndTheSources() {
        val files = sampleDocument().toCupProject(packageName = "com.example.deck")
        assertTrue(files.any { it.path == "src/commonMain/kotlin/com/example/deck/Slides.kt" })
        assertContains(
            files.contentsOf("src/commonMain/kotlin/com/example/deck/Main.kt"),
            "package com.example.deck",
        )
        assertContains(
            files.contentsOf("build.gradle.kts"),
            "targetDesktop(mainClass = \"com.example.deck.MainKt\")",
        )
    }

    @Test
    fun slideNotesRideAlongAsComments() {
        val slides = sampleDocument().toCupProject()
            .contentsOf("src/commonMain/kotlin/presentation/Slides.kt")
        assertContains(slides, "// Notes: The canvas never compiles a slide.")
    }

    private fun String.occurrences(text: String): Int = split(text).size - 1
}
