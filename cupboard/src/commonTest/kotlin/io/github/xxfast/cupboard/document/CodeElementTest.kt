package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A code box's looks are a document concern, so all of this is a pure function
 * over elements. What the renderer does with a theme is the canvas's own test.
 */
class CodeElementTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    /**
     * Theme, numbers and wrap arrived after the first documents were written, so
     * a file without them has to keep opening on the defaults. Hand-written JSON
     * on purpose, the way `DocumentTest` does it: a round trip through the
     * current encoder would write the fields and prove nothing.
     */
    @Test
    fun aCodeElementWrittenBeforeTheThemeFieldsStillDecodes() {
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
                      "language": "kotlin",
                      "fontSize": 14.0
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val element = decodeDocument(json).slides.single().elements.single() as CodeElement
        assertEquals(CodeTheme.Atom, element.theme)
        assertFalse(element.showLineNumbers)
        assertFalse(element.wrap)
        assertEquals("fun main() {}", element.code)
    }

    @Test
    fun serializationRoundTripsThemeNumbersAndWrap() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        CodeElement(
                            frame = frame,
                            theme = CodeTheme.Monokai,
                            showLineNumbers = true,
                            wrap = true,
                        ),
                    ),
                ),
            ),
        )

        val decoded = decodeDocument(document.encodeToString())
        assertEquals(document, decoded)
    }

    @Test
    fun styleCarriesTheLooksBetweenCodeBoxesAndLeavesTheCodeBehind() {
        val source = CodeElement(
            id = "source",
            frame = frame,
            code = "val a = 1",
            language = "swift",
            fontSize = 22f,
            opacity = 0.5f,
            theme = CodeTheme.Matrix,
            showLineNumbers = true,
            wrap = true,
        )
        val target = CodeElement(id = "target", frame = Frame(50f, 50f, 10f, 10f), code = "val b = 2")

        val styled = target.applyingStyle(source) as CodeElement
        assertEquals(CodeTheme.Matrix, styled.theme)
        assertTrue(styled.showLineNumbers)
        assertTrue(styled.wrap)
        assertEquals(22f, styled.fontSize)
        assertEquals(0.5f, styled.opacity)

        // Content and geometry stay the target's.
        assertEquals("val b = 2", styled.code)
        assertEquals("kotlin", styled.language)
        assertEquals(Frame(50f, 50f, 10f, 10f), styled.frame)
    }

    @Test
    fun formattingReachesUnlockedTopLevelCodeAndNothingElse() {
        val code = CodeElement(id = "code", frame = frame)
        val locked = CodeElement(id = "locked", frame = frame, locked = true)
        val text = TextElement(id = "text", frame = frame)
        val group = GroupElement(id = "group", frame = frame, children = listOf(code.copy(id = "inner")))

        val formatted = listOf(code, locked, text, group)
            .formatCode { it.copy(theme = CodeTheme.Darcula) }

        assertEquals(listOf("code"), formatted.map { it.id })
        assertEquals(CodeTheme.Darcula, (formatted.single() as CodeElement).theme)
    }

    @Test
    fun formattingHandsBackOnlyWhatChanged() {
        val already = CodeElement(id = "code", frame = frame, wrap = true)
        assertTrue(already.formattedAlone { it.copy(wrap = true) }.isEmpty())
        assertEquals(1, already.formattedAlone { it.copy(wrap = false) }.size)
    }

    @Test
    fun aFreshCodeBoxComesWithASnippetToTypeOver() {
        val element = codeBoxElement(frame)
        assertEquals(frame, element.frame)
        assertEquals("Kotlin", element.language)
        assertTrue(element.code.contains("fun main"))
        assertTrue(element.code.lines().size in 3..5)
    }

    @Test
    fun everyOfferedLanguageIsNamedOnce() {
        assertEquals(CodeLanguages.distinct(), CodeLanguages)
        assertTrue("Plain" in CodeLanguages)
        // The default a fresh box inserts with has to be one a picker can show.
        assertTrue(codeBoxElement(frame).language in CodeLanguages)
    }

    private fun CodeElement.formattedAlone(transform: (CodeElement) -> CodeElement): List<Element> =
        listOf(this).formatCode(transform)
}
