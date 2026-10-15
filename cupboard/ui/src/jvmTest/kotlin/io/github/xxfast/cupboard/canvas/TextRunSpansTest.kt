package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.BoldWeight
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LineSpacingMode
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextBaseline
import io.github.xxfast.cupboard.document.TextCapitalization
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import io.github.xxfast.cupboard.document.TextRun
import io.github.xxfast.cupboard.document.VerticalAlign
import io.github.xxfast.cupboard.editor.ListMarkerTransformation
import io.github.xxfast.cupboard.editor.SpanTransformation
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Styled runs from the text's offsets to what is drawn: the spans they and the
 * range links set between them, the pixels, the field's markers and a build's
 * fade, and the Text inspector's casing, baselines, spacing and alignment.
 */
class TextRunSpansTest {
    private val red: Long = 0xFFFF0000
    private val home: LinkTarget = LinkTarget.Url("https://home")

    @Test
    fun aRunAndALinkAreCutWhereEitherBeginsOrEnds() {
        val element: TextElement = box(
            "Hello world",
            ListStyle.None,
            listOf(TextRun(0, 8, color = red)),
            listOf(TextLink(6, 11, home)),
        )
        val spans: List<AnnotatedString.Range<SpanStyle>> = element.spanStyles(DeckFontFamilies.None)
        assertEquals(listOf(0 to 6, 6 to 8, 8 to 11), spans.map { it.start to it.end })

        assertEquals(Color(red), spans[0].item.color)
        assertEquals(null, spans[0].item.textDecoration)
        assertEquals(Color(red), spans[1].item.color)
        assertEquals(TextDecoration.Underline, spans[1].item.textDecoration)
        assertEquals(Color(LinkColor), spans[2].item.color)
    }

    @Test
    fun aRunSetsOnlyWhatItNames() {
        val element: TextElement = box("Hello world", ListStyle.None, listOf(TextRun(0, 5, fontSize = 30f)))
        val span: SpanStyle = element.spanStyles(DeckFontFamilies.None).single().item
        assertEquals(30.sp, span.fontSize)
        assertEquals(Color.Unspecified, span.color)
        assertEquals(null, span.fontWeight)
        assertEquals(null, span.textDecoration)
    }

    @Test
    fun aBoxWithNoRunsAndNoLinksSetsNoSpans() {
        assertEquals(emptyList(), box("Hello world", ListStyle.None).spanStyles(DeckFontFamilies.None))
    }

    @Test
    fun aStyledRunChangesWhatIsDrawn() {
        val plain = box("Hello world", ListStyle.None)
        val bold = box("Hello world", ListStyle.None, listOf(TextRun(0, 5, fontWeight = BoldWeight, color = red)))
        assertFalse(pixels { ElementView(plain) }.contentEquals(pixels { ElementView(bold) }))
    }

    @Test
    fun aStyledRunInAListChangesWhatIsDrawn() {
        val plain = box("Alpha\n\tBeta", ListStyle.Bullet)
        val styled = box("Alpha\n\tBeta", ListStyle.Bullet, listOf(TextRun(7, 11, color = red)))
        assertFalse(pixels { ElementView(plain) }.contentEquals(pixels { ElementView(styled) }))
    }

    @Test
    fun theFieldDrawsARunOnTheSameCharactersAfterTheMarkers() {
        val text = "Alpha\n\tBeta"
        val element = box(text, ListStyle.Bullet, listOf(TextRun(7, 11, italic = true)))
        val transformed: TransformedText =
            ListMarkerTransformation(ListStyle.Bullet, element.spanStyles(DeckFontFamilies.None))
                .filter(AnnotatedString(text))

        val drawn: AnnotatedString = transformed.text
        val span = drawn.spanStyles.single()
        assertEquals("Beta", drawn.text.substring(span.start, span.end))
        assertEquals(transformed.offsetMapping.originalToTransformed(7), span.start)
    }

    @Test
    fun aFadingPieceFadesInItsOwnInk() {
        val body: AnnotatedString = DrawnRun(0, "Hello world")
            .styled(listOf(AnnotatedString.Range(SpanStyle(color = Color(red)), 6, 11)))
        val delivered: AnnotatedString = deliveredText(
            body = body,
            offset = 0,
            pieces = listOf(0..5, 6..10),
            shown = 2,
            alpha = 0.5f,
            color = Color.White,
        )
        val world: Color = delivered.spanStyles.last { it.start <= 6 && it.end >= 11 }.item.color
        assertEquals(Color(red).copy(alpha = 0.5f), world)
    }

    @Test
    fun aRunBiggerThanTheBoxSpacesTheLinesForIt() {
        val element = box("Hello\nworld", ListStyle.None, listOf(TextRun(0, 5, fontSize = 40f)))
            .copy(lineHeight = 1.5f)
        assertEquals(60.sp, element.textStyle(DeckFontFamilies.None).lineHeight)
    }

    @Test
    fun casingChangesTheLettersAndNoOffset() {
        val shouted = box("hello world", ListStyle.None, listOf(TextRun(6, 11, capitalization = TextCapitalization.AllCaps)))
        assertEquals("hello WORLD", shouted.casing.cased(shouted.text))

        val titled = box("hello world", ListStyle.None).copy(capitalization = TextCapitalization.Title)
        assertEquals("Hello World", titled.casing.cased(titled.text))

        // Title case finds a word where it starts, not where its run does.
        val midWord = box("hello", ListStyle.None, listOf(TextRun(2, 5, capitalization = TextCapitalization.Title)))
        assertEquals("hello", midWord.casing.cased(midWord.text))

        // A run back to none in a box in capitals keeps its letters as typed.
        val spared = box("ab cd", ListStyle.None, listOf(TextRun(3, 5, capitalization = TextCapitalization.None)))
            .copy(capitalization = TextCapitalization.AllCaps)
        assertEquals("AB cd", spared.casing.cased(spared.text))
    }

    @Test
    fun smallCapsAreTheFacesAndLeaveTheLettersAlone() {
        val element = box("Hello world", ListStyle.None, listOf(TextRun(6, 11, capitalization = TextCapitalization.SmallCaps)))
        assertEquals(element.text, element.casing.cased(element.text))
        assertEquals("smcp", element.spanStyles(DeckFontFamilies.None).single().item.fontFeatureSettings)

        val box = box("Hello", ListStyle.None).copy(capitalization = TextCapitalization.SmallCaps)
        assertEquals("smcp", box.textStyle(DeckFontFamilies.None).fontFeatureSettings)
    }

    @Test
    fun aSpanSaysTheBoxsLigaturesAgainSinceItsFeaturesReplaceTheBoxs() {
        val plain = box("office", ListStyle.None).copy(ligatures = false)
        assertEquals("liga 0, clig 0", plain.textStyle(DeckFontFamilies.None).fontFeatureSettings)
        assertNull(box("office", ListStyle.None).textStyle(DeckFontFamilies.None).fontFeatureSettings)

        val small = plain.copy(runs = listOf(TextRun(0, 3, capitalization = TextCapitalization.SmallCaps)))
        assertEquals("smcp, liga 0, clig 0", small.spanStyles(DeckFontFamilies.None).single().item.fontFeatureSettings)

        // Out of small caps inside a box in them, the span has to say so.
        val out = box("office", ListStyle.None, listOf(TextRun(0, 3, capitalization = TextCapitalization.AllCaps)))
            .copy(capitalization = TextCapitalization.SmallCaps)
        assertEquals("smcp 0", out.spanStyles(DeckFontFamilies.None).single().item.fontFeatureSettings)
    }

    @Test
    fun aRaisedRunDrawsSmallerAndShiftedOnLinesAsTallAsBefore() {
        val element = box("E=mc2", ListStyle.None, listOf(TextRun(4, 5, baseline = TextBaseline.Superscript)))
        val span: SpanStyle = element.spanStyles(DeckFontFamilies.None).single().item
        assertEquals(BaselineShift.Superscript, span.baselineShift)
        assertEquals((20f * ShiftedBaselineScale).sp, span.fontSize)
        assertEquals(26.sp, element.textStyle(DeckFontFamilies.None).lineHeight)

        val dropped = box("H2O", ListStyle.None).copy(baseline = TextBaseline.Subscript)
        val style: TextStyle = dropped.textStyle(DeckFontFamilies.None)
        assertEquals(BaselineShift.Subscript, style.baselineShift)
        assertEquals((20f * ShiftedBaselineScale).sp, style.fontSize)
        assertEquals(26.sp, style.lineHeight)

        // Back on the line inside a dropped box: full size, and unshifted.
        val back = dropped.copy(runs = listOf(TextRun(0, 1, baseline = TextBaseline.Normal)))
        val normal: SpanStyle = back.spanStyles(DeckFontFamilies.None).single().item
        assertEquals(BaselineShift.None, normal.baselineShift)
        assertEquals(20.sp, normal.fontSize)
    }

    @Test
    fun theFieldDrawsTheCasedLettersUnderTheCaret() {
        val element = box("alpha\n\tbeta", ListStyle.Bullet).copy(capitalization = TextCapitalization.AllCaps)

        val plain: TransformedText = SpanTransformation(emptyList(), element.casing).filter(AnnotatedString("alpha"))
        assertEquals("ALPHA", plain.text.text)
        assertEquals(OffsetMapping.Identity, plain.offsetMapping)

        val list: TransformedText = ListMarkerTransformation(ListStyle.Bullet, emptyList(), element.casing)
            .filter(AnnotatedString(element.text))
        val plainList: TransformedText = ListMarkerTransformation(ListStyle.Bullet).filter(AnnotatedString(element.text))
        assertEquals(plainList.text.text.uppercase(), list.text.text)
        assertEquals(plainList.offsetMapping.originalToTransformed(8), list.offsetMapping.originalToTransformed(8))
    }

    @Test
    fun spacingIsDrawnAtWhatItsModeMeasures() {
        val exactly = box("Hello", ListStyle.None).copy(lineSpacingMode = LineSpacingMode.Exactly, lineHeight = 31f)
        assertEquals(31.sp, exactly.textStyle(DeckFontFamilies.None).lineHeight)

        val between = box("Hello", ListStyle.None).copy(lineSpacingMode = LineSpacingMode.Between, lineHeight = 4f)
        assertEquals(24.sp, between.textStyle(DeckFontFamilies.None).lineHeight)
    }

    @Test
    fun paragraphSpacingGoesBetweenParagraphsOnly() {
        val one = box("Hello", ListStyle.None).copy(paragraphSpacingBefore = 6f, paragraphSpacingAfter = 4f)
        assertEquals(26.sp, one.textStyle(DeckFontFamilies.None).lineHeight)

        val two = one.copy(text = "Hello\nworld")
        assertEquals(36.sp, two.textStyle(DeckFontFamilies.None).lineHeight)
    }

    @Test
    fun aListDrawnALineAtATimeStacksAsTheFieldSetsIt() {
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val style: TextStyle = box("Alpha\nBeta\nGamma", ListStyle.Bullet)
            .copy(paragraphSpacingAfter = 10f)
            .textStyle(DeckFontFamilies.None)

        val field: TextLayoutResult = measurer.measure("Alpha\nBeta\nGamma", style)
        val rows: List<TextLayoutResult> = listOf("Alpha", "Beta", "Gamma").mapIndexed { index, line ->
            measurer.measure(line, style.forListLine(index, 3))
        }

        var top = 0f
        for ((index, row) in rows.withIndex()) {
            assertEquals(field.getLineBaseline(index), top + row.getLineBaseline(0), 0.5f)
            top += row.size.height
        }
        assertEquals(field.size.height.toFloat(), top, 0.5f)
    }

    @Test
    fun theTextSitsWhereItsAlignmentsSay() {
        val bottom = box("Hi", ListStyle.None).copy(verticalAlign = VerticalAlign.Bottom, align = TextAlign.Justify)
        assertEquals(BiasAlignment(-1f, 1f), bottom.alignment())
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Justify,
            bottom.textStyle(DeckFontFamilies.None).textAlign,
        )

        val top = box("Hi", ListStyle.None)
        assertFalse(pixels { ElementView(top) }.contentEquals(pixels { ElementView(bottom) }))
    }

    private fun box(
        text: String,
        style: ListStyle,
        runs: List<TextRun> = emptyList(),
        links: List<TextLink> = emptyList(),
    ): TextElement = TextElement(
        id = "t",
        frame = Frame(10f, 10f, 180f, 80f),
        text = text,
        fontSize = 20f,
        listStyle = style,
        runs = runs,
        links = links,
    )

    @OptIn(ExperimentalComposeUiApi::class)
    private fun pixels(content: @Composable () -> Unit): ByteArray {
        val image: Image = renderComposeScene(200, 100, density = Density(1f)) {
            SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 100f) { content() }
        }
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(image.width, image.height)
        image.readPixels(bitmap)
        return bitmap.readPixels()!!
    }
}
