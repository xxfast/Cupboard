package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.BoldWeight
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineSpacingMode
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextBaseline
import io.github.xxfast.cupboard.document.TextCapitalization
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextRun
import io.github.xxfast.cupboard.document.VerticalAlign
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A deck with styled runs goes out with them: PowerPoint as runs of its own,
 * the CuP project as an AnnotatedString, and a box with none exactly as before.
 * So does the Text inspector's paragraph and character formatting, as far as
 * each format has it.
 */
class TextRunsExportTest {
    private val deck: Document = Document(
        name = "Styled deck",
        slides = listOf(
            Slide(
                id = "styled",
                title = "Styled",
                elements = listOf(
                    TextElement(
                        id = "text",
                        frame = Frame(0f, 0f, 400f, 80f),
                        text = "Text formatting",
                        fontSize = 20f,
                        runs = listOf(TextRun(5, 15, fontSize = 30f, fontWeight = BoldWeight, color = 0xFFFF0000)),
                    ),
                    TextElement(
                        id = "list",
                        frame = Frame(0f, 100f, 400f, 80f),
                        text = "One\n\tTwo",
                        listStyle = ListStyle.Bullet,
                        runs = listOf(TextRun(5, 8, italic = true)),
                    ),
                    TextElement(id = "plain", frame = Frame(0f, 200f, 400f, 40f), text = "Plain words"),
                ),
            ),
        ),
    )

    @Test
    fun powerPointGetsARunPerStyleChange() {
        val slide: String = readStoredZip(exportPptx(deck, FakeRasterizer()))
            .getValue("ppt/slides/slide1.xml")
            .latin1()

        assertContains(slide, """<a:rPr lang="en-US" sz="2000" b="0" i="0" u="none" strike="noStrike">""")
        assertContains(slide, "<a:t>Text </a:t>")
        assertContains(slide, """<a:rPr lang="en-US" sz="3000" b="1" i="0" u="none" strike="noStrike">""")
        assertContains(slide, """<a:srgbClr val="FF0000"/></a:solidFill></a:rPr><a:t>formatting</a:t>""")
        assertContains(slide, """i="1" u="none" strike="noStrike"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>Two</a:t>""")
    }

    private val formatted: Document = Document(
        name = "Formatted deck",
        slides = listOf(
            Slide(
                id = "formatted",
                title = "Formatted",
                elements = listOf(
                    TextElement(
                        id = "text",
                        frame = Frame(0f, 0f, 400f, 200f),
                        text = "the first\nE=mc2 and H2O",
                        fontSize = 20f,
                        align = TextAlign.Justify,
                        verticalAlign = VerticalAlign.Bottom,
                        capitalization = TextCapitalization.Title,
                        lineSpacingMode = LineSpacingMode.Exactly,
                        lineHeight = 30f,
                        paragraphSpacingBefore = 6f,
                        paragraphSpacingAfter = 4f,
                        letterSpacing = 1.5f,
                        runs = listOf(
                            TextRun(14, 15, baseline = TextBaseline.Superscript),
                            TextRun(16, 19, capitalization = TextCapitalization.AllCaps),
                            TextRun(22, 23, baseline = TextBaseline.Subscript, capitalization = TextCapitalization.SmallCaps),
                        ),
                    ),
                    TextElement(
                        id = "lines",
                        frame = Frame(0f, 300f, 400f, 40f),
                        text = "Lines",
                        lineHeight = 1.5f,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun powerPointGetsTheParagraphAndCharacterFormatting() {
        val slide: String = readStoredZip(exportPptx(formatted, FakeRasterizer()))
            .getValue("ppt/slides/slide1.xml")
            .latin1()

        // Anchored where the box sits its text, and kept its height to sit it there.
        assertContains(slide, """<a:bodyPr wrap="square" anchor="b"></a:bodyPr>""")
        assertContains(slide, """<a:bodyPr wrap="square" anchor="t"><a:spAutoFit/></a:bodyPr>""")

        // Spacing in the schema's order, the space before off the first paragraph
        // and the space after off the last.
        assertContains(
            slide,
            """<a:pPr algn="just"><a:lnSpc><a:spcPts val="3000"/></a:lnSpc>""" +
                """<a:spcAft><a:spcPts val="400"/></a:spcAft></a:pPr>""",
        )
        assertContains(
            slide,
            """<a:pPr algn="just"><a:lnSpc><a:spcPts val="3000"/></a:lnSpc>""" +
                """<a:spcBef><a:spcPts val="600"/></a:spcBef></a:pPr>""",
        )
        assertContains(slide, """<a:lnSpc><a:spcPct val="150000"/></a:lnSpc>""")

        // Title case goes over in the words; the casing PowerPoint has goes as its own.
        assertContains(slide, "<a:t>The First</a:t>")
        assertContains(slide, """strike="noStrike" spc="150" baseline="30000">""")
        assertContains(slide, """strike="noStrike" cap="all" spc="150"><a:solidFill><a:srgbClr val="FFFFFF"/></a:solidFill></a:rPr><a:t>and</a:t>""")
        assertContains(slide, """cap="small" spc="150" baseline="-25000">""")
    }

    @Test
    fun theCupProjectCasesTheWordsAndSpacesTheLinesAsTheCanvasDoes() {
        val slides: String = formatted.toCupProject()
            .single { it.path == "src/commonMain/kotlin/presentation/Slides.kt" }
            .contents

        assertContains(slides, "textAlign = TextAlign.Justify,")
        assertContains(slides, "append(\"The First\\nE=Mc\")")
        assertContains(slides, "withStyle(SpanStyle(fontSize = 13.333334f.sp, baselineShift = BaselineShift.Superscript)) {")
        assertContains(slides, "append(\" AND H2\")")
        assertFalse("SpanStyle()" in slides)
        // Exactly 30 points, and the paragraph spacing in every line.
        assertContains(slides, "lineHeight = 40.0f.sp,")
        assertContains(slides, "lineHeight = 22.5f.sp,")
    }

    @Test
    fun theCupProjectSetsTheRunsAsAnAnnotatedString() {
        val slides: String = deck.toCupProject()
            .single { it.path == "src/commonMain/kotlin/presentation/Slides.kt" }
            .contents

        assertContains(slides, "text = buildAnnotatedString {")
        assertContains(slides, "append(\"Text \")")
        assertContains(
            slides,
            "withStyle(SpanStyle(color = 0xFFFF0000L.argb(), fontSize = 30.0f.sp, fontWeight = FontWeight(700))) {",
        )
        assertContains(slides, "append(\"formatting\")")
        assertContains(slides, "withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {")
        assertContains(slides, "append(\"• \")")
        assertContains(slides, "import androidx.compose.ui.text.withStyle")

        // Spaced for the largest size in the box, the way the canvas spaces it.
        assertContains(slides, "lineHeight = 39.0f.sp,")

        assertContains(slides, "text = \"Plain words\",")
        assertEquals(2, Regex("text = buildAnnotatedString").findAll(slides).count())
        assertFalse("append(\"\\t" in slides)
    }
}
