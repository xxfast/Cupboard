package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The versions a code block holds, and the edits that add, drop and reorder
 * them: whatever happens to the list, every step keeps pointing at the text it
 * was pointing at.
 */
class CodeVersionsTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    /** Three versions, one step on each, so a remap has something to be wrong about. */
    private fun block(): CodeElement = CodeElement(
        id = "code",
        frame = frame,
        code = "one",
        versions = listOf("two", "three"),
        steps = listOf(CodeStep(version = 0), CodeStep(version = 1), CodeStep(version = 2)),
    )

    private fun CodeElement.stepVersions(): List<Int> = steps.map { it.version }

    @Test
    fun aBlockWithoutVersionsIsStillOneVersion() {
        val plain = CodeElement(frame = frame, code = "only")

        assertEquals(listOf("only"), plain.sources)
        assertEquals("only", plain.sourceAt(0))
    }

    @Test
    fun sourceAtClampsRatherThanThrowing() {
        val code = block()

        assertEquals(listOf("one", "two", "three"), code.sources)
        assertEquals("three", code.sourceAt(2))
        assertEquals("three", code.sourceAt(9))
        assertEquals("one", code.sourceAt(-1))
    }

    @Test
    fun withSourceWritesTheVersionAskedForAndLeavesTheRestAlone() {
        val edited: CodeElement = block().withSource(1, "TWO")

        assertEquals(listOf("one", "TWO", "three"), edited.sources)
        assertEquals("one", edited.code)

        // Version 0 is the `code` field, and writing it writes that.
        assertEquals("ONE", block().withSource(0, "ONE").code)
        // And an index it does not have clamps onto the last one it does.
        assertEquals(listOf("one", "two", "THREE"), block().withSource(7, "THREE").sources)
    }

    @Test
    fun addingAVersionDuplicatesItAndPushesTheStepsBehindItAlong() {
        val grown: CodeElement = block().withVersionAdded(0)

        assertEquals(listOf("one", "one", "two", "three"), grown.sources)
        // The step on version 0 stays on version 0; the two behind it move up.
        assertEquals(listOf(0, 2, 3), grown.stepVersions())
    }

    @Test
    fun removingAVersionPullsTheStepsBehindItBack() {
        val shrunk: CodeElement = block().withVersionRemoved(1)

        assertEquals(listOf("one", "three"), shrunk.sources)
        // Version 2's step follows "three" down to 1, and the step that pointed
        // at what went falls onto the version that took its place.
        assertEquals(listOf(0, 1, 1), shrunk.stepVersions())
    }

    @Test
    fun removingTheLastVersionDropsItsStepsOntoTheOneBefore() {
        val shrunk: CodeElement = block().withVersionRemoved(2)

        assertEquals(listOf("one", "two"), shrunk.sources)
        assertEquals(listOf(0, 1, 1), shrunk.stepVersions())
    }

    @Test
    fun removingVersionZeroPromotesTheNextOne() {
        val shrunk: CodeElement = block().withVersionRemoved(0)

        assertEquals("two", shrunk.code)
        assertEquals(listOf("three"), shrunk.versions)
        assertEquals(listOf(0, 0, 1), shrunk.stepVersions())
    }

    @Test
    fun removingTheOnlyVersionIsANoOp() {
        val only = CodeElement(id = "code", frame = frame, code = "only")

        assertSame(only, only.withVersionRemoved(0))
    }

    @Test
    fun movingAVersionCarriesItsStepsWithIt() {
        val moved: CodeElement = block().withVersionMoved(0, 2)

        assertEquals(listOf("two", "three", "one"), moved.sources)
        assertEquals(listOf(2, 0, 1), moved.stepVersions())
    }

    @Test
    fun movingAVersionNowhereIsANoOp() {
        val code = block()

        assertSame(code, code.withVersionMoved(1, 1))
        // Out of range clamps onto the ends, so this is the same no-op.
        assertSame(code, code.withVersionMoved(2, 9))
    }

    @Test
    fun aDocumentWrittenBeforeCodeVersionsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "code",
                      "id": "code",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "code": "fun main() {}",
                      "steps": [{ "reveal": [] }]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val code = loadedDocument(json).slides.single().elements.single() as CodeElement
        assertEquals(emptyList(), code.versions)
        assertEquals(listOf(0), code.steps.map { it.version })
        assertEquals("fun main() {}", code.sourceAt(0))
    }

    @Test
    fun serializationRoundTripsVersionsAndTheStepsOnThem() {
        val document = Document(slides = listOf(Slide(elements = listOf(block()))))

        assertEquals(document, loadedDocument(document.encodeToString()))
    }
}
