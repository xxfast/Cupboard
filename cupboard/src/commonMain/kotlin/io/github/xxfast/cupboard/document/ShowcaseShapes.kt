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
    imageFillsSlide(),
    strokesSlide(),
    shadowsSlide(),
    linesAndArrowsSlide(),
    pathsSlide(),
    objectStylesSlide(),
)

/**
 * Every [ShapeKind] with an outline of its own, off `entries` so a new one lands
 * on this grid by itself. [ShapeKind.Path] has none to show here: what it draws
 * is whatever path it carries, which is [pathsSlide]'s to demonstrate.
 */
private fun shapeCatalogSlide(): Slide {
    val kinds: List<ShapeKind> = ShapeKind.entries.filter { it != ShapeKind.Path }
    val shapes: List<Element> = kinds.flatMapIndexed { index, kind ->
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

/**
 * Every [ImageFillMode], off `entries`, in boxes taller than they are wide: the
 * picture is 4:3 and smaller than the box, so no two modes can land on the same
 * pixels. Under them, the picture clipped to two outlines that are not boxes.
 */
private fun imageFillsSlide(): Slide {
    val modesArea = Frame(ShowcaseStage.x, ShowcaseStage.y, ShowcaseStage.width, 470f)
    val clipsArea = Frame(ShowcaseStage.x, ShowcaseStage.y + 498f, ShowcaseStage.width, 262f)
    val modes: List<ImageFillMode> = ImageFillMode.entries

    val laid: List<Element> = modes.flatMapIndexed { index, mode ->
        val cell: Frame = gridCell(modesArea, columns = modes.size, rows = 1, index = index)
        // Tile repeats at the picture's own size, so it gets the small motif:
        // the 240 pixel picture would repeat barely once across the box.
        val asset: String = if (mode == ImageFillMode.Tile) ShowcaseTileId else ShowcasePictureId
        tile(cell, mode.name) { box -> plainShape(box).copy(imageFill = ImageFill(asset, mode)) }
    }

    val outlines: List<Pair<Int, ShapeKind>> = listOf(1 to ShapeKind.Star, 3 to ShapeKind.Ellipse)
    val clipped: List<Element> = outlines.flatMap { (index, kind) ->
        val cell: Frame = gridCell(clipsArea, columns = 5, rows = 1, index = index)
        tile(cell, "$kind, ScaleToFill") { box ->
            // Square, so the star is a star and the ellipse a circle.
            val square = Frame(box.x + (box.width - box.height) / 2, box.y, box.height, box.height)
            plainShape(square, kind).copy(imageFill = ImageFill(ShowcasePictureId))
        }
    }

    return featureSlide(
        title = "Image Fills",
        subtitle = "ImageFill: every ImageFillMode, then the picture clipped to an outline",
        elements = laid + clipped,
        notes = "Look for: five tall boxes holding the same picture five ways. ScaleToFill " +
            "covers its box and crops the picture's sides off, border and all. " +
            "ScaleToFit shows the whole picture, border on all four sides, with the " +
            "shape's own translucent purple fill as the letterbox above and below it. " +
            "Stretch pulls it to the box, so the circle stands up as a tall oval. Tile " +
            "repeats the small diamond motif from the top-left corner. Original draws " +
            "the picture at its own 240 by 180, smaller than the box, with the purple " +
            "fill showing on every side. Under them a star and a circle with the " +
            "picture inside their outlines and nothing outside them, the stroke drawn " +
            "over the edge.\n\n" +
            "Select a shape and change its mode in the Fill control of the Format " +
            "inspector. Switching Fill to Color or Gradient drops the picture and goes " +
            "back to the colour that was stored under it; picking a colour while the " +
            "picture shows only changes the letterbox.\n\n" +
            "The pictures are generated when the showcase is laid down. A showcase " +
            "from before they shipped has none until Help > Feature Showcase > Reset, " +
            "and every shape here then draws its plain purple fill, which is the " +
            "missing-bytes fallback working.",
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

/**
 * [ShapeKind.Path]: a shape drawn by a [ShapePath] of its own. One contour, the
 * two ways a second contour cuts a hole in the first, and an SVG read into shapes.
 */
private fun pathsSlide(): Slide {
    // Two squares, one inside the other. Wound the same way they only make a
    // hole under even-odd; wound against each other, nonzero makes it too.
    val outer = "M0 0 L100 0 L100 100 L0 100 Z"
    val sameWay = "M30 30 L70 30 L70 70 L30 70 Z"
    val against = "M30 30 L30 70 L70 70 L70 30 Z"
    val unit = Frame(0f, 0f, 100f, 100f)

    val paths: List<Pair<String, ShapePath>> = listOf(
        "one contour" to ShapePath("M0 100 C0 0 100 0 100 100 L50 60 Z", Frame(0f, 25f, 100f, 75f)),
        "nonzero, same winding" to ShapePath("$outer $sameWay", unit),
        "nonzero, opposite winding" to ShapePath("$outer $against", unit),
        "evenOdd, same winding" to ShapePath("$outer $sameWay", unit, evenOdd = true),
    )

    val drawn: List<Element> = paths.flatMapIndexed { index, (label, path) ->
        tile(gridCell(columns = 5, rows = 1, index = index), label) { box ->
            plainShape(box, ShapeKind.Path).copy(cornerRadius = 0f, path = path)
        }
    }

    // Two paints, so it arrives as a group of two shapes, the disc over the square.
    val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="240" height="240">
          <rect x="10" y="10" width="100" height="100" rx="16" fill="#7F52FF"/>
          <circle cx="60" cy="60" r="30" fill="#00C2A8" stroke="white" stroke-width="4"/>
        </svg>
    """.trimIndent()
    val imported: List<Element> = tile(gridCell(columns = 5, rows = 1, index = 4), "svgElement") { box ->
        val element: Element = svgElement(svg, box.width, box.height) ?: plainShape(box)
        element.update(
            frame = Frame(
                x = box.centerX - element.frame.width / 2,
                y = box.centerY - element.frame.height / 2,
                width = element.frame.width,
                height = element.frame.height,
            ),
        )
    }

    return featureSlide(
        title = "Paths",
        subtitle = "ShapeKind.Path: a ShapePath stretched over the frame, and an SVG read into one",
        elements = drawn + imported,
        notes = "Look for: an arch with a notch cut up into its base, stretched to fill its " +
            "box. Then three squares: the second solid with no hole, the third and fourth " +
            "each with a square hole showing the slide through it. Last, a purple rounded " +
            "square with a teal disc on it, ringed in white, at its own size in the middle " +
            "of its box. Select the third or fourth square and Break Apart: two filled " +
            "squares, the small one on top, both selected.",
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
