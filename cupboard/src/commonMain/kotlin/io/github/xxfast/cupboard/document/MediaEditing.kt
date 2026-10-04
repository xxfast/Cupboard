package io.github.xxfast.cupboard.document

/**
 * This movie with its playing settings brought back inside what a player can do:
 * [VideoElement.volume] in 0 to 1, both trims at or after the start, and an end
 * that is either 0 (to the end) or after the start.
 *
 * An end at or before the start is a window with nothing in it, and goes back to
 * 0 rather than to some sliver after the start: a trim nobody could have meant is
 * better read as no trim at all than as a clip one millisecond long.
 *
 * What the editor folds every movie through on its way into the document, so the
 * inspector can send whatever its field holds and a host is never asked to play a
 * window that runs backwards.
 */
fun VideoElement.clamped(): VideoElement {
    val start: Int = trimStartMs.coerceAtLeast(0)
    return copy(
        trimStartMs = start,
        trimEndMs = trimEnd(start, trimEndMs),
        volume = volume.coerceIn(0f, 1f),
    )
}

/** [VideoElement.clamped] for a sound: the same three settings, the same rules. */
fun AudioElement.clamped(): AudioElement {
    val start: Int = trimStartMs.coerceAtLeast(0)
    return copy(
        trimStartMs = start,
        trimEndMs = trimEnd(start, trimEndMs),
        volume = volume.coerceIn(0f, 1f),
    )
}

/** This element clamped if it is a movie or a sound, as it is otherwise. */
fun Element.clampedMedia(): Element = when (this) {
    is VideoElement -> clamped()
    is AudioElement -> clamped()
    else -> this
}

/** [end] where it is after [start], 0 (to the end) where it is not. */
private fun trimEnd(start: Int, end: Int): Int = if (end > start) end else 0

/**
 * This movie playing [assetId]'s bytes instead: what Replace does.
 *
 * The box stays where it is, and so do the loop, the volume, autoplay and the
 * title, since none of them say anything about which bytes are behind them. The
 * trims go, since a window in milliseconds was a window onto the old movie, and
 * so do the poster and the url, since a still of the old movie and the page it
 * came from are both facts about bytes that are no longer here.
 */
fun VideoElement.replacingMedia(assetId: String): VideoElement = copy(
    assetId = assetId,
    webUrl = null,
    posterAssetId = null,
    trimStartMs = 0,
    trimEndMs = 0,
)

/** [VideoElement.replacingMedia] for a sound: new bytes, same pill, no trim. */
fun AudioElement.replacingMedia(assetId: String): AudioElement = copy(
    assetId = assetId,
    trimStartMs = 0,
    trimEndMs = 0,
)
