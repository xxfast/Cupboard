package io.github.xxfast.cupboard.canvas

import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineRange
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TypingRhythm
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.showcaseDocument
import io.github.xxfast.cupboard.document.typingMs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The core counts a typed step change without a highlighter or a layout, so the
 * build timeline can wait it out; the renderer plans it on [morphGlyphs]. Two
 * classifications of one diff, held together here: for every fixture the core's
 * [typingMs] is the whole millisecond the renderer's clock runs over.
 */
class TypingParityTest {
    private val frame = Frame(0f, 0f, 400f, 300f)

    private val before = "fun greet() {\n    println(\"hi\")\n}"
    private val after = "fun greet(name: String) {\n    val loud = name.uppercase()\n    println(\"hi \$name\")\n    println(loud)\n}"

    private fun block(vararg steps: CodeStep): CodeElement =
        CodeElement(id = "code", frame = frame, code = before, versions = listOf(after), steps = steps.toList())

    private fun assertParity(element: CodeElement, from: CodeStep, to: CodeStep) {
        for (durationMs in listOf(0, 400, 5_000)) {
            val timeline: TypewriterTimeline = element.typewriterTimeline(from, to, durationMs, TypingRhythm.Natural)
            assertEquals(
                expected = ceil(timeline.totalMs).toInt(),
                actual = element.typingMs(from, to, durationMs),
                message = "$from -> $to at $durationMs ms",
            )
        }
    }

    @Test
    fun aVersionChangeAddingLines() {
        val from = CodeStep(version = 0)
        val to = CodeStep(version = 1)
        assertParity(block(from, to), from, to)
    }

    @Test
    fun aRewriteThatErasesAndTypes() {
        val element = CodeElement(id = "code", frame = frame, code = "val x = 10", versions = listOf("val y = 20"))
        assertParity(element, CodeStep(version = 0), CodeStep(version = 1))
        assertParity(element, CodeStep(version = 1), CodeStep(version = 0))
    }

    @Test
    fun onlyTheHighlightChanging() {
        val from = CodeStep(version = 1, highlight = listOf(LineRange(2, 2)))
        val to = CodeStep(version = 1, highlight = listOf(LineRange(3, 4)))
        assertParity(block(from, to), from, to)
        assertParity(block(CodeStep(version = 1), from), CodeStep(version = 1), from)
    }

    @Test
    fun onlyTheScrollToChanging() {
        val from = CodeStep(version = 1)
        val to = CodeStep(version = 1, scrollTo = 4)
        assertParity(block(from, to), from, to)
    }

    @Test
    fun linesHiddenByAReveal() {
        val from = CodeStep(version = 0, reveal = listOf(LineRange(1, 1)))
        val to = CodeStep(version = 1, reveal = listOf(LineRange(1, 2), LineRange(5, 5)))
        assertParity(block(from, to), from, to)
        assertParity(block(to, from), to, from)

        // A reveal within one version: only arrivals, and the brace dropping.
        val more = CodeStep(version = 1)
        assertParity(block(to, more), to, more)
    }

    @Test
    fun nothingChangingIsNoTimeAtAll() {
        // Nothing to erase, move or type is 0 on both sides, whatever the build
        // asks for: the renderer runs a zero-length clock, and the build timeline
        // reads a one-version change with no key to press as a plain step change.
        val step = CodeStep(version = 1, highlight = listOf(LineRange(2, 2)))
        val element: CodeElement = block(step)
        assertParity(element, step, step)
        assertEquals(0, element.typingMs(step, step, durationMs = 5_000))
    }

    @Test
    fun everyPairOfTheShowcasesCodeSteps() {
        val blocks: List<CodeElement> = showcaseDocument().allSlides()
            .flatMap { slide: Slide -> slide.elements.filterIsInstance<CodeElement>() }
            .filter { it.steps.size > 1 }

        for (element in blocks) {
            for (from in element.steps) for (to in element.steps) assertParity(element, from, to)
        }
    }
}
