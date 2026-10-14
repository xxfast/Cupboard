package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals

/** Line styles: a line's stroke and ends under a name, the deck's six to start with. */
class LineStyleTest {
    private val arrowed = LineElement(
        id = "line",
        start = Point(0f, 0f),
        end = Point(100f, 0f),
        strokeColor = 0xFFFFFFFF,
        strokeWidth = 9f,
        startEnd = LineEnd.Arrow,
        endEnd = LineEnd.Circle,
        bend = Point(50f, 20f),
    )

    @Test
    fun applyingAThemeStyleSetsTheStrokeAndClearsBothEnds() {
        val dots: LineStyle = defaultLineStyles(ElementDefaults()).first { it.id == "line-dots" }

        val styled: LineElement = arrowed.applyingLineStyle(dots)

        assertEquals(dots.strokeColor, styled.strokeColor)
        assertEquals(3f, styled.strokeWidth)
        assertEquals(StrokeStyle.RoundDots, styled.strokeStyle)
        assertEquals(LineEnd.None, styled.startEnd)
        assertEquals(LineEnd.None, styled.endEnd)
        // Where it runs and how it bends are not a look.
        assertEquals(arrowed.start, styled.start)
        assertEquals(arrowed.bend, styled.bend)
    }

    @Test
    fun aSavedStyleKeepsTheEndsItWasSavedWith() {
        val saved: LineStyle = arrowed.asLineStyle("Mine")
        val plain: LineElement = arrowed.copy(startEnd = LineEnd.None, endEnd = LineEnd.None)

        assertEquals(arrowed, plain.applyingLineStyle(saved))
    }

    @Test
    fun everyDeckStartsWithSixInItsOwnColours() {
        val defaults = ElementDefaults(shapeStroke = 0xFF111111, accent = 0xFF00AA00)
        val styles: List<LineStyle> = defaultLineStyles(defaults)

        assertEquals(
            listOf(
                "line-solid", "line-accent", "line-long-dash",
                "line-short-dash", "line-muted", "line-dots",
            ),
            styles.map { it.id },
        )
        assertEquals(listOf(2f, 2f, 6f, 6f, 2f, 3f), styles.map { it.strokeWidth })
        assertEquals(0xFF111111, styles[0].strokeColor)
        assertEquals(0xFF00AA00, styles[1].strokeColor)
        assertEquals(0x7F111111, styles[4].strokeColor)
        assertEquals(defaultLineStyles(ElementDefaults()), Document().lineStyles)
    }

    @Test
    fun aThemeChangeRegeneratesTheSixInItsColours() {
        val theme = Theme(
            name = "Green",
            background = null,
            defaults = ElementDefaults(accent = 0xFF00AA00),
            layouts = emptyList(),
        )

        val themed: Document = Document().applyingTheme(theme)

        assertEquals(defaultLineStyles(theme.defaults), themed.lineStyles)
    }
}
