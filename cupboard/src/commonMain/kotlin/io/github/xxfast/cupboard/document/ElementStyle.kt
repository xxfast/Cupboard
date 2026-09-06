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
 * [TextElement.link], [TextElement.linkTarget], [ShapeElement.link],
 * [ImageElement.link], [ImageElement.assetId], [ImageElement.mask],
 * [ImageElement.caption], [ShapeElement.label], [CodeElement.code],
 * [CodeElement.language], [TerminalElement.text], [TerminalElement.title],
 * [DiagramElement.source], [DiagramElement.steps], [EquationElement.latex],
 * [VideoElement.assetId], [VideoElement.webUrl], [VideoElement.posterAssetId],
 * [AudioElement.assetId], the trims, both titles and both autoplays,
 * [ShapeElement.kind]), the
 * geometry (frame, rotation, flips) and the lock.
 * Copying a style is not copying an element, and pasting one onto a laid-out
 * slide must not move anything. A link is where the box points, which is what it
 * says rather than how it looks, however much of its look follows from it.
 *
 * A line's arrowheads travel with the rest of the shape's look: which ends are
 * capped is how the line is drawn, not what it joins. The kind stays behind
 * regardless, so a line's style pasted onto a rectangle leaves a rectangle.
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
        fontFamily = source.fontFamily,
        italic = source.italic,
        underline = source.underline,
        strikethrough = source.strikethrough,
        listStyle = source.listStyle,
    )

    this is ShapeElement && source is ShapeElement -> copy(
        opacity = source.opacity,
        cornerRadius = source.cornerRadius,
        fill = source.fill,
        gradient = source.gradient,
        strokeColor = source.strokeColor,
        strokeWidth = source.strokeWidth,
        shadow = source.shadow,
        startArrow = source.startArrow,
        endArrow = source.endArrow,
        labelSize = source.labelSize,
        labelColor = source.labelColor,
    )

    this is CodeElement && source is CodeElement -> copy(
        opacity = source.opacity,
        fontSize = source.fontSize,
        theme = source.theme,
        showLineNumbers = source.showLineNumbers,
        wrap = source.wrap,
    )

    this is TerminalElement && source is TerminalElement -> copy(
        opacity = source.opacity,
        prompt = source.prompt,
        fontSize = source.fontSize,
        showTitleBar = source.showTitleBar,
    )

    this is DiagramElement && source is DiagramElement -> copy(
        opacity = source.opacity,
        fontSize = source.fontSize,
        nodeFill = source.nodeFill,
        nodeStroke = source.nodeStroke,
        nodeText = source.nodeText,
        edgeColor = source.edgeColor,
    )

    this is EquationElement && source is EquationElement -> copy(
        opacity = source.opacity,
        fontSize = source.fontSize,
        color = source.color,
    )

    // The adjustment is the whole of an image's look: the mask is where the
    // element is cropped and the caption is what it says, both content rather
    // than style, and neither means anything on a different picture.
    this is ImageElement && source is ImageElement -> copy(
        opacity = source.opacity,
        adjust = source.adjust,
    )

    // A gallery's look is the correction its pictures are drawn through and
    // whether their captions show: the pictures themselves and what each of them
    // says are content, and mean nothing on a different carousel.
    this is GalleryElement && source is GalleryElement -> copy(
        opacity = source.opacity,
        adjust = source.adjust,
        showCaptions = source.showCaptions,
    )

    // How loudly and how many times a movie plays is the whole of its look: what
    // it is (the bytes, the url, the poster), where it is cut and whether it
    // starts on its own are content, and mean nothing on a different movie.
    this is VideoElement && source is VideoElement -> copy(
        opacity = source.opacity,
        loop = source.loop,
        volume = source.volume,
    )

    this is AudioElement && source is AudioElement -> copy(
        opacity = source.opacity,
        loop = source.loop,
        volume = source.volume,
    )

    // A group has no style of its own, and neither does any pair of types that
    // don't match: opacity is all there is to carry across.
    else -> update(opacity = source.opacity)
}
