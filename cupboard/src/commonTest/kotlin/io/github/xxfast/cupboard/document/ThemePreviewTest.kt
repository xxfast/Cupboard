package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the new-deck chooser draws for a theme: Keynote's sample text on its title layout. */
class ThemePreviewTest {
    private fun Document.texts(): List<String> = slides.single().elements.map { (it as TextElement).text }

    @Test
    fun aPreviewSaysWhatKeynotesThumbnailSays() {
        val preview: Document = BuiltInThemes.BasicWhite.preview()

        assertEquals(listOf("My Presentation", "Donec quis nunc", "Author and Date"), preview.texts())
        assertEquals(preview.layouts.first().id, preview.slides.single().layoutId)
        assertEquals("Basic White", preview.themeName)
    }

    @Test
    fun aThemeWithoutAnAuthorLineShowsTwoLines() {
        assertEquals(listOf("My Presentation", "Donec quis nunc"), BuiltInThemes.White.preview().texts())
    }

    @Test
    fun aPreviewIsDrawnAtTheSizeCreateWouldMake() {
        val wide: Document = BuiltInThemes.Black.preview(SlideSizePreset.Widescreen)
        val standard: Document = BuiltInThemes.Black.preview(SlideSizePreset.Standard)

        assertEquals(1920f to 1080f, wide.slideWidth to wide.slideHeight)
        assertEquals(1440f to 1080f, standard.slideWidth to standard.slideHeight)
    }

    /** Keynote's 4:3 masters line up with its 16:9 ones, so a deck moving between them keeps every slot. */
    @Test
    fun aBasicThemesStandardLayoutsMatchItsWideOnesOneForOne() {
        for (theme in BuiltInThemes.basic) {
            val standard: List<Slide> = theme.presetLayouts.getValue(SlideSizePreset.Standard)
            assertEquals(theme.layouts.map { it.title }, standard.map { it.title }, theme.name)
            assertEquals(
                theme.layouts.map { layout -> layout.placeholders().keys.toList() },
                standard.map { layout -> layout.placeholders().keys.toList() },
                theme.name,
            )
        }
        assertTrue(BuiltInThemes.cupboard.all { it.presetLayouts.isEmpty() })
    }

    @Test
    fun aStandardDeckLandsOnTheStandardLayoutsAsDrawn() {
        val deck: Document = BuiltInThemes.BasicWhite.deck("Talk", SlideSizePreset.Standard)
        val standard: List<Slide> = BuiltInThemes.BasicWhite.presetLayouts.getValue(SlideSizePreset.Standard)

        assertEquals(
            standard.map { layout -> layout.elements.map { it.frame } },
            deck.layouts.map { layout -> layout.elements.map { it.frame } },
        )
    }

    /** No 4:3 masters of its own, so a Cupboard theme's 16:9 ones are scaled to fit, as before. */
    @Test
    fun aThemeWithoutStandardLayoutsIsScaledToFit() {
        val deck: Document = BuiltInThemes.Cupboard.deck("Talk", SlideSizePreset.Standard)
        val wide: Frame = BuiltInThemes.Cupboard.layouts.first().elements.first().frame

        assertEquals(wide.x * 0.75f, deck.layouts.first().elements.first().frame.x, 0.5f)
    }

    /** Putting a 4:3 deck on a Basic theme picks the 4:3 masters, the same as creating one on it. */
    @Test
    fun applyingABasicThemeToAStandardDeckPicksItsStandardLayouts() {
        val deck: Document = BuiltInThemes.Cupboard.deck("Talk", SlideSizePreset.Standard)
            .applyingTheme(BuiltInThemes.ClassicWhite)
        val standard: List<Slide> = BuiltInThemes.ClassicWhite.presetLayouts.getValue(SlideSizePreset.Standard)

        assertEquals(standard.first().elements.map { it.frame }, deck.layouts.first().elements.map { it.frame })
    }
}
