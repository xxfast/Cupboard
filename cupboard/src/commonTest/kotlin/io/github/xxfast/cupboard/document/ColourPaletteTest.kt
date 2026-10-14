package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The colour well's swatches: six hues by four tints off the theme, the greys
 * under them, and the theme's own colours in the grid exactly.
 */
class ColourPaletteTest {
    @Test
    fun theGridIsSixHuesByFourTintsAndTheGreysRunWhiteToBlack() {
        val palette: ColourPalette = ElementDefaults().palette()

        assertEquals(PALETTE_TINTS, palette.grid.size)
        assertTrue(palette.grid.all { row -> row.size == PALETTE_HUES })
        assertEquals(
            listOf(0xFFFFFFFF, 0xFFBFBFBF, 0xFF808080, 0xFF404040, 0xFF000000),
            palette.greys,
        )
    }

    @Test
    fun theAccentIsInTheGridExactlyAndLeadsIt() {
        val defaults = ElementDefaults(accent = 0xFF00A884)
        val palette: ColourPalette = defaults.palette()
        val firstColumn: List<Long> = palette.grid.map { row -> row.first() }

        assertTrue(0xFF00A884 in firstColumn)
    }

    @Test
    fun aTranslucentShapeFillComesBackOpaque() {
        val defaults = ElementDefaults(
            accent = 0xFF808080,
            shapeFill = 0x38FF0000,
        )
        val swatches: List<Long> = defaults.palette().grid.flatten()

        assertTrue(0xFFFF0000 in swatches)
        assertTrue(swatches.all { swatch -> swatch ushr 24 == 0xFFL })
    }

    @Test
    fun eachColumnRunsLightToDark() {
        for (column in 0 until PALETTE_HUES) {
            val lightness: List<Int> = ElementDefaults().palette().grid
                .map { row -> row[column].brightness() }

            assertEquals(lightness.sortedDescending(), lightness)
        }
    }

    @Test
    fun anAllGreyThemeStillGetsSixColumns() {
        val defaults = ElementDefaults(
            textColor = 0xFF000000,
            bodyColor = 0xFF333333,
            shapeFill = 0xFF000000,
            shapeStroke = 0xFF000000,
            shapeLabelColor = 0xFFFFFFFF,
            accent = 0xFFB9B9C6,
        )
        val palette: ColourPalette = defaults.palette()

        assertTrue(palette.grid.all { row -> row.size == PALETTE_HUES })
        // Every column is a hue the greys row doesn't already have.
        assertTrue(palette.grid.flatten().none { swatch -> swatch in palette.greys })
    }

    @Test
    fun nearbyHuesShareAColumn() {
        // The default theme's accent, stroke and label are all the one violet.
        val defaults = ElementDefaults(
            accent = 0xFF7F52FF,
            shapeFill = 0xFF7F52FF,
            shapeStroke = 0xFFA98FFF,
            shapeLabelColor = 0xFFD9CFFF,
            textColor = 0xFFFFFFFF,
            bodyColor = 0xFFB8B3D6,
        )
        val top: List<Long> = defaults.palette().grid.first()

        assertEquals(top.size, top.distinct().size)
    }

    private fun Long.brightness(): Int =
        ((this shr 16) and 0xFF).toInt() + ((this shr 8) and 0xFF).toInt() + (this and 0xFF).toInt()
}
