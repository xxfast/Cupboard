package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * One face a deck carries: a font file in the asset store, and the family, weight
 * and slant it answers for.
 *
 * The bytes are an asset like an image's, so the deck stays a JSON file you can
 * read and the file travels with the bundle. The rest is read off the file once,
 * when it is added (see [parseFontInfo]), rather than every time something draws:
 * a [TextElement.fontName] matching [family] is all it takes for a box to be set
 * in it.
 *
 * One entry per cut, so a family with a bold and an italic is three of these
 * under one [family], and the canvas picks between them by the box's own weight
 * and slant.
 */
@Serializable
data class DeckFont(
    val assetId: String,
    val family: String,
    val weight: Int = 400,
    val italic: Boolean = false,
)

/**
 * [font] added to the deck. A cut the deck already has (the same family, weight
 * and slant) is replaced rather than doubled, so adding a newer file of a face is
 * an update; one that changes nothing returns this same instance.
 */
fun Document.addFont(font: DeckFont): Document {
    val existing: DeckFont? = fonts.firstOrNull { it.isSameCut(font) }
    if (existing == font) return this
    if (existing == null) return copy(fonts = fonts + font)
    return copy(fonts = fonts.map { if (it === existing) font else it })
}

/**
 * The font under [assetId] dropped from the deck. Text set in its family is left
 * alone: it names a face rather than a file, and falls back like any face that is
 * not there. An id the deck doesn't carry returns this same instance.
 */
fun Document.removeFont(assetId: String): Document {
    if (fonts.none { it.assetId == assetId }) return this
    return copy(fonts = fonts.filterNot { it.assetId == assetId })
}

/** Every family the deck carries, once each and in alphabetical order, case aside. */
val Document.fontFamilies: List<String>
    get() = fonts.map { it.family }
        .distinctBy { it.lowercase() }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

private fun DeckFont.isSameCut(other: DeckFont): Boolean =
    family.equals(other.family, ignoreCase = true) && weight == other.weight && italic == other.italic
