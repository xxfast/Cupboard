package io.github.xxfast.cupboard.document

/**
 * What the new-deck chooser draws for this theme at [size]: a deck on it, sized
 * the way Create would size it, with one slide on its first layout.
 *
 * The slide says what Keynote's own chooser thumbnails say: "My Presentation"
 * in the title and "Donec quis nunc" in the first body, which is the subtitle on
 * every title layout. Any further placeholder keeps its layout's own text, so a
 * Basic White thumbnail still carries its "Author and Date" line.
 *
 * A whole [Document] rather than a slide, because a slide alone can't be drawn:
 * its background is the deck's and its size is the deck's.
 */
fun Theme.preview(size: SlideSizePreset = SlideSizePreset.Widescreen): Document {
    val deck: Document = deck(name, size)

    val slide: Slide = Slide(title = name).instantiating(deck.layouts.firstOrNull())
    val samples: Map<String, String> = slide.placeholders().entries
        .mapNotNull { (slot, element) -> PreviewText[slot]?.let { text -> element.id to text } }
        .toMap()

    val written: List<Element> = slide.elements.map { element ->
        val text: String = samples[element.id] ?: return@map element
        if (element !is TextElement) return@map element
        return@map element.copy(text = text)
    }

    return deck.copy(slides = listOf(slide.copy(elements = written)))
}

/** Keynote's chooser sample text, by the slot it goes in. */
private val PreviewText: Map<PlaceholderSlot, String> = mapOf(
    PlaceholderSlot(PlaceholderRole.Title, 0) to "My Presentation",
    PlaceholderSlot(PlaceholderRole.Body, 0) to "Donec quis nunc",
)
