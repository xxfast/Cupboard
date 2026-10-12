package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.svgElement

/** How much of the slide a freshly inserted drawing is allowed to take: an inserted image's share. */
private const val InsertedSvgFraction: Float = 0.6f

/**
 * [bytes] read as an SVG and put on the selected slide as shapes.
 *
 * Here rather than in a shell for `insertImage`'s reason: a file dialog, a drop
 * and a paste all end up holding the same bytes, and what happens next is the
 * same whichever of them asked. Unlike an image it is not a suspending call and
 * writes nothing into the deck: there is no toolkit decode to wait on, and an
 * SVG lands as elements rather than as an asset the document points at (see
 * [svgElement]).
 *
 * [insertionFrame] is `EditorState.insertionFrame`: the caller owns where an
 * insertion lands, this only says how big a box to ask for. That is the
 * drawing's natural size, scaled down to fit when it would cover the slide and
 * never up, since an icon blown up to fill the slide is nobody's intent.
 *
 * False is bytes that are not an SVG, or one that draws nothing this can keep,
 * which inserts nothing rather than taking the window down: a shell hands over
 * whatever was dropped on it.
 */
fun EditorViewModel.insertSvg(bytes: ByteArray, insertionFrame: (Float, Float) -> Frame): Boolean {
    val document: Document = states.value.document
    val element: Element = svgElement(
        svg = bytes.decodeToString(),
        maxWidth = document.slideWidth * InsertedSvgFraction,
        maxHeight = document.slideHeight * InsertedSvgFraction,
    ) ?: return false

    // A group's `update` carries its children along with the new frame.
    onInsertElement(element.update(frame = insertionFrame(element.frame.width, element.frame.height)))
    return true
}
