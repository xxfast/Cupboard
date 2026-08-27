package io.github.xxfast.cupboard.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TransformedText
import io.github.xxfast.cupboard.document.ListStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The caret's half of the list markers. Compose asks the mapping about every
 * offset the field can reach and throws on one that lands outside the string,
 * so this walks all of them rather than sampling.
 */
class ListMarkerTransformationTest {
    private val text: String = "Alpha\n\tBeta\n\t\tGamma\n\nDelta"

    @Test
    fun theMarkersAndTheIndentAreDrawnInFrontOfEachLine() {
        val drawn: String = ListMarkerTransformation(ListStyle.Bullet)
            .filter(AnnotatedString(text))
            .text
            .text
        assertEquals("• Alpha\n    ◦ Beta\n        • Gamma\n\n• Delta", drawn)
    }

    @Test
    fun everyOffsetMapsBothWaysAndComesBackWhereItStarted() {
        for (style in listOf(ListStyle.Bullet, ListStyle.Numbered)) {
            val transformed: TransformedText =
                ListMarkerTransformation(style).filter(AnnotatedString(text))
            val drawn: String = transformed.text.text

            for (offset in 0..text.length) {
                val forward: Int = transformed.offsetMapping.originalToTransformed(offset)
                assertTrue(forward in 0..drawn.length, "$style: $offset drew at $forward")
                assertEquals(offset, transformed.offsetMapping.transformedToOriginal(forward))
            }

            for (offset in 0..drawn.length) {
                val back: Int = transformed.offsetMapping.transformedToOriginal(offset)
                assertTrue(back in 0..text.length, "$style: $offset came back as $back")
            }
        }
    }
}
