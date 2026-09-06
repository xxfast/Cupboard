package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A terminal is document data like any other element, so all of this is pure
 * functions over elements. What the chrome and the typewriter look like is the
 * canvas' own test.
 */
class TerminalElementTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    @Test
    fun serializationRoundTripsTheWholeTerminal() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        TerminalElement(
                            frame = frame,
                            text = "$ ls\nREADME.md",
                            prompt = "%",
                            title = "fish",
                            fontSize = 22f,
                            showTitleBar = false,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(document, loadedDocument(document.encodeToString()))
    }

    /** The tag is what a file on disk carries, so it has to stay "terminal". */
    @Test
    fun aTerminalDecodesFromItsWrittenForm() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "terminal",
                      "id": "term",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "text": "$ whoami"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val element = loadedDocument(json).slides.single().elements.single() as TerminalElement
        assertEquals("$ whoami", element.text)
        assertEquals("$", element.prompt)
        assertEquals("zsh", element.title)
        assertTrue(element.showTitleBar)
    }

    @Test
    fun styleCarriesTheLooksBetweenTerminalsAndLeavesTheTranscriptBehind() {
        val source = TerminalElement(
            id = "source",
            frame = frame,
            text = "$ make",
            prompt = "%",
            title = "fish",
            fontSize = 30f,
            opacity = 0.5f,
            showTitleBar = false,
        )
        val target = TerminalElement(id = "target", frame = Frame(50f, 50f, 10f, 10f), text = "$ ls")

        val styled = target.applyingStyle(source) as TerminalElement
        assertEquals("%", styled.prompt)
        assertEquals(30f, styled.fontSize)
        assertEquals(0.5f, styled.opacity)
        assertEquals(false, styled.showTitleBar)

        // Content and geometry stay the target's: the title names this session.
        assertEquals("$ ls", styled.text)
        assertEquals("zsh", styled.title)
        assertEquals(Frame(50f, 50f, 10f, 10f), styled.frame)
    }

    @Test
    fun aCopiedTerminalIsTheSameTerminalUnderAFreshId() {
        val element = TerminalElement(id = "term", frame = frame, text = "$ ls")
        val copy = element.withNewIds() as TerminalElement

        assertNotEquals(element.id, copy.id)
        assertEquals(element.copy(id = copy.id), copy)
    }

    @Test
    fun theEntryBuildIsTheFirstOneNamingTheElement() {
        val terminal = TerminalElement(id = "term", frame = frame)
        val text = TextElement(id = "text", frame = frame)
        val slide = Slide(
            elements = listOf(text, terminal),
            builds = listOf(
                Build(text.id),
                Build(terminal.id, effect = BuildEffect.Typewriter, durationMs = 2400),
                Build(terminal.id, effect = BuildEffect.Pop),
            ),
        )

        val entry: Build? = slide.entryBuild(terminal.id)
        assertEquals(BuildEffect.Typewriter, entry?.effect)
        assertEquals(2400, entry?.durationMs)
        assertNull(slide.entryBuild("nobody"))
    }

    @Test
    fun aTerminalTakesACaretAndAnImageDoesNot() {
        assertTrue(TerminalElement(frame = frame).takesCaret)
        assertTrue(CodeElement(frame = frame).takesCaret)
        assertTrue(TextElement(frame = frame).takesCaret)
        assertEquals(false, ImageElement(frame = frame).takesCaret)
    }

    @Test
    fun aFreshTerminalComesWithACommandToTypeOver() {
        val element = terminalElement(frame)
        assertEquals(frame, element.frame)
        assertEquals("$", element.prompt)
        assertTrue(element.text.startsWith("$ "))
        assertEquals(2, element.text.lines().size)
    }
}
