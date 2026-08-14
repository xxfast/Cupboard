package io.github.xxfast.cupboard.document

/**
 * This element wearing [source]'s style: how it looks, never what it says and
 * never where it sits.
 *
 * Opacity always transfers, whatever the two types are, because every element has
 * one and it reads as an appearance rather than as content. Everything else is
 * per-type and only transfers between elements of the same type: a shape's fill
 * has no meaning on a text box, and a style paste that quietly did nothing is
 * better than one that quietly changed the wrong thing.
 *
 * What is deliberately left out is the content ([TextElement.text],
 * [ShapeElement.label], [CodeElement.code], [ShapeElement.kind]), the geometry
 * (frame, rotation, flips) and the lock. Copying a style is not copying an
 * element, and pasting one onto a laid-out slide must not move anything.
 */
fun Element.applyingStyle(source: Element): Element = when {
    this is TextElement && source is TextElement -> copy(
        opacity = source.opacity,
        fontSize = source.fontSize,
        fontWeight = source.fontWeight,
        lineHeight = source.lineHeight,
        letterSpacing = source.letterSpacing,
        color = source.color,
        align = source.align,
    )

    this is ShapeElement && source is ShapeElement -> copy(
        opacity = source.opacity,
        cornerRadius = source.cornerRadius,
        fill = source.fill,
        strokeColor = source.strokeColor,
        strokeWidth = source.strokeWidth,
        labelSize = source.labelSize,
        labelColor = source.labelColor,
    )

    this is CodeElement && source is CodeElement -> copy(
        opacity = source.opacity,
        fontSize = source.fontSize,
    )

    // Images and groups have no style of their own, and so does any pair of
    // types that don't match: opacity is all there is to carry across.
    else -> update(opacity = source.opacity)
}
