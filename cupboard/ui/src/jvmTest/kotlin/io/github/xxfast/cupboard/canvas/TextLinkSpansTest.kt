package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextLink
import io.github.xxfast.cupboard.editor.ListMarkerTransformation
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Range links from the text's offsets to what is drawn: the runs a list is set
 * in, the field's markers, the pixels, and the hit test a click in the show
 * makes.
 */
class TextLinkSpansTest {
    private val home: LinkTarget = LinkTarget.Url("https://home")
    private val away: LinkTarget = LinkTarget.Slide("away")

    @Test
    fun plainTextIsOneRunAndTheLinksPassStraightThrough() {
        val runs: List<TextRun> = textRuns("Hello world", ListStyle.None)
        assertEquals(listOf(TextRun(0, "Hello world")), runs)
        assertEquals(listOf(LinkSpan(6, 11, home)), runs.single().spans(listOf(TextLink(6, 11, home))))
    }

    @Test
    fun aListsRunsDropTheirNestingTabsAndKeepTheirPlaceInTheText() {
        // "Alpha" at 0, "\tBeta" at 6 with its body at 7, "\t\tGamma" at 12 with
        // its body at 14.
        val runs: List<TextRun> = textRuns("Alpha\n\tBeta\n\t\tGamma", ListStyle.Bullet)
        assertEquals(listOf(TextRun(0, "Alpha"), TextRun(7, "Beta"), TextRun(14, "Gamma")), runs)
        assertEquals(15, runs[2].textOffset(1))
    }

    @Test
    fun aLinkAcrossLinesIsCutToEachLinesBody() {
        // From "ha" through "Beta" and the tabs, into "Ga".
        val links: List<TextLink> = listOf(TextLink(3, 16, home))
        val runs: List<TextRun> = textRuns("Alpha\n\tBeta\n\t\tGamma", ListStyle.Numbered)
        assertEquals(listOf(LinkSpan(3, 5, home)), runs[0].spans(links))
        assertEquals(listOf(LinkSpan(0, 4, home)), runs[1].spans(links))
        assertEquals(listOf(LinkSpan(0, 2, home)), runs[2].spans(links))
    }

    @Test
    fun aLinkOnlyOverANestingTabDrawsNothing() {
        val runs: List<TextRun> = textRuns("Alpha\n\tBeta", ListStyle.Bullet)
        assertEquals(emptyList(), runs[1].spans(listOf(TextLink(6, 7, home))))
    }

    @Test
    fun theFieldDrawsTheLinkOnTheSameCharactersAfterTheMarkers() {
        val text = "Alpha\n\tBeta"
        val element = box(text, ListStyle.Bullet, TextLink(7, 11, home))
        val transformed: TransformedText =
            ListMarkerTransformation(ListStyle.Bullet, element.links, element.linkSpanStyle())
                .filter(AnnotatedString(text))

        val drawn: AnnotatedString = transformed.text
        val span = drawn.spanStyles.single()
        assertEquals("Beta", drawn.text.substring(span.start, span.end))
        assertEquals(transformed.offsetMapping.originalToTransformed(7), span.start)
    }

    @Test
    fun aRangeLinkChangesWhatIsDrawn() {
        val plain = box("Hello world", ListStyle.None)
        val linked = box("Hello world", ListStyle.None, TextLink(6, 11, home))
        assertFalse(pixels { ElementView(plain) }.contentEquals(pixels { ElementView(linked) }))
    }

    @Test
    fun aRangeLinkInAListChangesWhatIsDrawn() {
        val plain = box("Alpha\n\tBeta", ListStyle.Bullet)
        val linked = box("Alpha\n\tBeta", ListStyle.Bullet, TextLink(7, 11, away))
        assertFalse(pixels { ElementView(plain) }.contentEquals(pixels { ElementView(linked) }))
    }

    @Test
    fun aLinkOnTextOfItsOwnColourIsStillUnderlined() {
        val coloured = box("Hello world", ListStyle.None).copy(color = 0xFF30A46C)
        val linked = coloured.copy(links = listOf(TextLink(0, 11, home)))
        assertFalse(pixels { ElementView(coloured) }.contentEquals(pixels { ElementView(linked) }))
        assertEquals(androidx.compose.ui.graphics.Color.Unspecified, linked.linkSpanStyle().color)
    }

    @Test
    fun aBoxWithNoRangeLinksDrawsAsItAlwaysDid() {
        val plain = box("Hello world", ListStyle.None)
        val emptied = plain.copy(links = emptyList())
        assertContentEquals(pixels { ElementView(plain) }, pixels { ElementView(emptied) })
    }

    @Test
    fun aClickLandsOnTheLinkedCharacterAndNowhereElse() {
        val text = "Hello world"
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr)
        val layout: TextLayoutResult = measurer.measure(AnnotatedString(text), TextStyle(fontSize = 20.sp))
        val spans: List<LinkSpan> = listOf(LinkSpan(6, 11, home))

        val inWorld: Offset = layout.getBoundingBox(8).center
        val inHello: Offset = layout.getBoundingBox(1).center
        val pastTheEnd = Offset(layout.size.width + 40f, inWorld.y)

        assertEquals(spans.single(), layout.linkSpanAt(inWorld, spans))
        assertNull(layout.linkSpanAt(inHello, spans))
        assertNull(layout.linkSpanAt(pastTheEnd, spans))
    }

    private fun box(text: String, style: ListStyle, vararg links: TextLink): TextElement = TextElement(
        id = "t",
        frame = Frame(10f, 10f, 180f, 80f),
        text = text,
        fontSize = 20f,
        listStyle = style,
        links = links.toList(),
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
