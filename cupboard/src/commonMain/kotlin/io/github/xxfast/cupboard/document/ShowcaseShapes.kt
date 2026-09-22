package io.github.xxfast.cupboard.document

/** A shape dressed in the deck's defaults, which is what a fresh one looks like. */
private fun plainShape(frame: Frame, kind: ShapeKind = ShapeKind.Rectangle): ShapeElement =
    shapeElement(kind, frame, ShowcaseDefaults)

/** Everything a [ShapeElement] can be, one slide per property. */
internal fun shapeSlides(): List<Slide> = listOf(
    shapeCatalogSlide(),
    shapeLabelsSlide(),
    cornerRadiusSlide(),
    gradientsSlide(),
    strokesSlide(),
    shadowsSlide(),
    linesAndArrowsSlide(),
    objectStylesSlide(),
)

/** Every [ShapeKind], off `entries` so a new one lands on this grid by itself. */
private fun shapeCatalogSlide(): Slide {
    val shapes: List<Element> = ShapeKind.entries.flatMapIndexed { index, kind ->
        tile(gridCell(columns = 5, rows = 2, index = index), kind.name) { box ->
            plainShape(box, kind)
        }
    }

    return featureSlide(
        title = "Shape Catalog",
        subtitle = "every ShapeKind, in the deck's default fill and stroke",
        elements = shapes,
        notes = "Look for: ten shapes, each matching its caption. Triangle points up, " +
            "Arrow points right, Star has five points, Polygon is a regular hexagon, " +
            "QuoteBubble's tail is bottom-left and Callout's is bottom-centre. Line is " +
            "the odd one out: a bare diagonal with an arrowhead, no fill at all.",
    )
}

/** A shape's label: its size, its ink, and how it centres. */
private fun shapeLabelsSlide(): Slide {
    val sizes: List<Float> = listOf(20f, 30f, 44f)
    val labelled: List<Element> = sizes.flatMapIndexed { index, size ->
        tile(gridCell(columns = 3, rows = 1, index = index), "labelSize ${size.toInt()}") { box ->
            plainShape(box).copy(label = "Label", labelSize = size)
        }
    }

    return featureSlide(
        title = "Shape Labels",
        subtitle = "label, labelSize and labelColor, drawn inside the shape",
        elements = labelled,
        notes = "Look for: three identical rectangles with the word Label centred in each, " +
            "horizontally and vertically, at three sizes. The label takes the deck's " +
            "shapeLabelColor, so it is a shade off the pure white of the headers.",
    )
}

/** The corner radius range. Only a [ShapeKind.Rectangle] honours it. */
private fun cornerRadiusSlide(): Slide {
    val radii: List<Float> = listOf(0f, 8f, 24f, 56f, 110f)
    val corners: List<Element> = radii.flatMapIndexed { index, radius ->
        tile(gridCell(columns = 5, rows = 1, index = index), "cornerRadius ${radius.toInt()}") {
            plainShape(it).copy(cornerRadius = radius)
        }
    }

    return featureSlide(
        title = "Corner Radius",
        subtitle = "cornerRadius on a Rectangle, from square to fully rounded",
        elements = corners,
        notes = "Look for: five rectangles rounding off left to right. The first has hard " +
            "corners, the last is a pill or close to it, and the stroke follows the " +
            "curve rather than cutting across it.",
    )
}

/** A solid fill beside the same shape gradient turned through several angles. */
private fun gradientsSlide(): Slide {
    val angles: List<Float> = listOf(0f, 90f, 140f, 225f, 315f)
    val gradient = ShapeGradient(start = 0xFF7F52FF, end = 0xFF00C2A8)

    val solid: List<Element> = tile(gridCell(columns = 6, rows = 1, index = 0), "solid fill") {
        plainShape(it)
    }
    val turned: List<Element> = angles.flatMapIndexed { index, angle ->
        tile(gridCell(columns = 6, rows = 1, index = index + 1), "angle ${angle.toInt()}") {
            plainShape(it).copy(gradient = gradient.copy(angle = angle))
        }
    }

    return featureSlide(
        title = "Fills and Gradients",
        subtitle = "fill, then the same ShapeGradient at five angles",
        elements = solid + turned,
        notes = "Look for: the first box flat, the other five running purple to teal. " +
            "0 points the purple end up, 90 points it left, and each later box turns " +
            "the ramp clockwise from there. No two of the five should look alike.",
    )
}

/** Stroke width and stroke colour, which are separate facts about a shape. */
private fun strokesSlide(): Slide {
    val widths: List<Float> = listOf(0f, 1.5f, 4f, 10f)
    val struck: List<Element> = widths.flatMapIndexed { index, width ->
        tile(gridCell(columns = 4, rows = 2, index = index), "strokeWidth $width") {
            plainShape(it).copy(strokeWidth = width)
        }
    }

    val colors: List<Pair<String, Long>> = listOf(
        "shapeStroke" to ShowcaseDefaults.shapeStroke,
        "accent" to ShowcaseDefaults.accent,
        "amber" to 0xFFF5C518,
        "no fill, stroke only" to ShowcaseDefaults.shapeStroke,
    )
    val coloured: List<Element> = colors.flatMapIndexed { index, (label, color) ->
        tile(gridCell(columns = 4, rows = 2, index = index + 4), label) { box ->
            plainShape(box).copy(
                strokeColor = color,
                strokeWidth = 5f,
                fill = if (index == colors.lastIndex) 0x00000000 else ShowcaseDefaults.shapeFill,
            )
        }
    }

    return featureSlide(
        title = "Strokes",
        subtitle = "strokeWidth on the top row, strokeColor on the bottom",
        elements = struck + coloured,
        notes = "Look for: the first box on the top row with no border at all, then three " +
            "borders getting heavier. On the bottom row three borders in three inks, and " +
            "the last box a hollow outline with the background showing through it.",
    )
}

/** The drop shadow, which is off by default and has four dials when it is on. */
private fun shadowsSlide(): Slide {
    val shadows: List<Pair<String, ShapeShadow?>> = listOf(
        "no shadow" to null,
        "default" to ShapeShadow(),
        "blur 40" to ShapeShadow(blur = 40f),
        "dy 24" to ShapeShadow(blur = 16f, dy = 24f),
        "dx -20, dy -12" to ShapeShadow(blur = 16f, dx = -20f, dy = -12f),
        "tinted, opaque" to ShapeShadow(color = 0xCC7F52FF, blur = 30f, dy = 10f),
    )

    val cast: List<Element> = shadows.flatMapIndexed { index, (label, shadow) ->
        tile(gridCell(columns = 3, rows = 2, index = index), label) {
            plainShape(it).copy(shadow = shadow, cornerRadius = 18f)
        }
    }

    return featureSlide(
        title = "Shadows",
        subtitle = "ShapeShadow: colour, blur and offset under the same rectangle",
        elements = cast,
        notes = "Look for: the first box sitting flat and the other five lifted off the " +
            "slide. The Compose canvas draws an elevation shadow, which carries its own " +
            "offset, so the dx and dy boxes may all cast downwards: the document keeps " +
            "the intent even where the toolkit rounds it off.",
    )
}

/** [ShapeKind.Line] and its arrowheads, the one kind that reads both flags. */
private fun linesAndArrowsSlide(): Slide {
    val combinations: List<Triple<String, Boolean, Boolean>> = listOf(
        Triple("no arrowheads", false, false),
        Triple("endArrow", false, true),
        Triple("startArrow", true, false),
        Triple("both ends", true, true),
    )

    val lines: List<Element> = combinations.flatMapIndexed { index, (label, start, end) ->
        tile(gridCell(columns = 2, rows = 2, index = index), label) { box ->
            plainShape(box, ShapeKind.Line).copy(
                startArrow = start,
                endArrow = end,
                strokeWidth = 5f,
            )
        }
    }

    return featureSlide(
        title = "Lines and Arrows",
        subtitle = "startArrow and endArrow, the two flags only a Line reads",
        elements = lines,
        notes = "Look for: four diagonals running top-left to bottom-right, with the " +
            "arrowheads the caption names and no others. None of the four draws a fill, " +
            "and the heads sit on the ends of the stroke rather than beyond them.",
    )
}

/** The deck's own style library: the six [defaultObjectStyles], worn by one shape. */
private fun objectStylesSlide(): Slide {
    val styles: List<ObjectStyle> = defaultObjectStyles(ShowcaseDefaults)
    val dressed: List<Element> = styles.flatMapIndexed { index, style ->
        tile(gridCell(columns = 3, rows = 2, index = index), style.name) { box ->
            plainShape(box).applyingObjectStyle(style)
        }
    }

    return featureSlide(
        title = "Object Styles",
        subtitle = "the six styles a deck starts with, applied to one rectangle",
        elements = dressed,
        notes = "Look for: Filled as the ordinary shape, Outlined hollow with a heavier " +
            "accent border, Subtle at half the fill and no border at all, Accent solid in " +
            "the theme colour, Warning in amber, Shadowed the same as Filled but lifted. " +
            "These are generated from the deck's defaults, so they follow a theme change.",
    )
}
