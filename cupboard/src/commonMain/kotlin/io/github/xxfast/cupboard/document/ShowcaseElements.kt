package io.github.xxfast.cupboard.document

/** A shape in the deck's dress, with a label on it so a transform is readable. */
private fun labelled(frame: Frame, kind: ShapeKind, label: String): ShapeElement =
    shapeElement(kind, frame, ShowcaseDefaults).copy(label = label, labelSize = 26f)

/** The properties every [Element] carries, whatever kind it is. */
internal fun elementSlides(): List<Slide> = listOf(
    opacitySlide(),
    rotationSlide(),
    flipSlide(),
    lockedSlide(),
    zOrderSlide(),
    groupSlide(),
)

/** The opacity ladder, on one shape repeated. */
private fun opacitySlide(): Slide {
    val steps: List<Float> = listOf(1f, 0.8f, 0.6f, 0.4f, 0.2f, 0.05f)
    val faded: List<Element> = steps.flatMapIndexed { index, opacity ->
        tile(gridCell(columns = 6, rows = 1, index = index), "opacity $opacity") { box ->
            labelled(box, ShapeKind.Rectangle, "${(opacity * 100).toInt()}%")
                .copy(opacity = opacity, cornerRadius = 16f)
        }
    }

    return featureSlide(
        title = "Opacity",
        subtitle = "opacity, 1 down to 0.05 on the same rectangle",
        elements = faded,
        notes = "Look for: six boxes fading evenly left to right. The label fades with its " +
            "shape rather than staying solid on top of it, and the last box is barely " +
            "there without disappearing entirely. The captions stay at full strength.",
    )
}

/** The rotation ladder. Degrees clockwise, about the frame's centre. */
private fun rotationSlide(): Slide {
    val angles: List<Float> = listOf(0f, 15f, 45f, 90f, 135f, 180f, 270f, -30f)
    val turned: List<Element> = angles.flatMapIndexed { index, angle ->
        tile(gridCell(columns = 4, rows = 2, index = index), "rotation ${angle.toInt()}") { box ->
            labelled(box, ShapeKind.Arrow, "${angle.toInt()}").copy(rotation = angle)
        }
    }

    return featureSlide(
        title = "Rotation",
        subtitle = "rotation in degrees clockwise, about the frame's centre",
        elements = turned,
        notes = "Look for: eight arrows. At 0 the arrow points right; at 90 it points " +
            "down, not up, because degrees run clockwise. 270 and -30 should look like " +
            "90 anticlockwise and a slight anticlockwise tilt. Each arrow stays centred " +
            "in its own cell: rotation must not move an element.",
    )
}

/** Both flips, on shapes asymmetric enough to tell them apart. */
private fun flipSlide(): Slide {
    val cases: List<Triple<String, Boolean, Boolean>> = listOf(
        Triple("as drawn", false, false),
        Triple("flipped horizontally", true, false),
        Triple("flipped vertically", false, true),
        Triple("both", true, true),
    )

    val arrows: List<Element> = cases.flatMapIndexed { index, (label, horizontal, vertical) ->
        tile(gridCell(columns = 4, rows = 2, index = index), label) { box ->
            labelled(box, ShapeKind.Arrow, "Arrow").copy(
                flippedHorizontally = horizontal,
                flippedVertically = vertical,
            )
        }
    }
    val bubbles: List<Element> = cases.flatMapIndexed { index, (label, horizontal, vertical) ->
        tile(gridCell(columns = 4, rows = 2, index = index + 4), label) { box ->
            labelled(box, ShapeKind.QuoteBubble, "Quote").copy(
                flippedHorizontally = horizontal,
                flippedVertically = vertical,
            )
        }
    }

    return featureSlide(
        title = "Flips",
        subtitle = "flippedHorizontally and flippedVertically, on two asymmetric shapes",
        elements = arrows + bubbles,
        notes = "Look for: the arrow pointing left once flipped horizontally and still " +
            "right when flipped vertically. The quote bubble's tail starts bottom-left, " +
            "moves to bottom-right under a horizontal flip and to top-left under a " +
            "vertical one. Note whether the labels mirror too: a mirrored word is worth " +
            "reporting.",
    )
}

/** A locked element beside an unlocked twin: identical on the canvas, not in the editor. */
private fun lockedSlide(): Slide {
    val left: Frame = gridCell(columns = 2, rows = 1, index = 0)
    val right: Frame = gridCell(columns = 2, rows = 1, index = 1)

    return featureSlide(
        title = "Locked Elements",
        subtitle = "locked: it still draws and still selects, but nothing edits it",
        elements = listOf(
            labelled(left.demoBox(), ShapeKind.Rectangle, "Unlocked").copy(cornerRadius = 18f),
            captionUnder(left, "drag it, resize it, restyle it"),
            labelled(right.demoBox(), ShapeKind.Rectangle, "Locked")
                .copy(cornerRadius = 18f, locked = true),
            captionUnder(right, "locked = true"),
        ),
        notes = "Look for: two identical boxes. Lock is not a look, so they must render " +
            "the same.\n\n" +
            "In the editor: click the right-hand box and try to drag it. It should select " +
            "and refuse to move, and the inspector should offer to unlock it.",
    )
}

/** The z-order: [Slide.elements] in order, last on top. */
private fun zOrderSlide(): Slide {
    val tints: List<Long> = listOf(0xFF3B2F63, 0xFF4D3C80, 0xFF60499E, 0xFF7458BD, 0xFF8A6BDC)
    val stack: List<Element> = tints.mapIndexed { index, tint ->
        labelled(
            Frame(
                x = ShowcaseStage.x + 180f + index * 190f,
                y = ShowcaseStage.y + 110f + index * 80f,
                width = 420f,
                height = 300f,
            ),
            ShapeKind.Rectangle,
            "${index + 1}",
        ).copy(fill = tint, labelSize = 56f, cornerRadius = 18f)
    }

    return featureSlide(
        title = "Z-Order",
        subtitle = "elements draw in list order, so the last one is on top",
        elements = stack,
        notes = "Look for: five opaque boxes fanned down-right, each overlapping the one " +
            "before it. Box 5 must be whole, and boxes 1 to 4 each cut off by the box " +
            "after them. If box 1 is on top the list is being drawn backwards.",
    )
}

/** A group of several elements, and the same group turned. */
private fun groupSlide(): Slide {
    val left: Frame = gridCell(stage(520f), columns = 2, rows = 1, index = 0)
    val right: Frame = gridCell(stage(520f), columns = 2, rows = 1, index = 1)

    // Children are stored in absolute slide coordinates, so the group is built
    // around them rather than them being placed inside it.
    val members: List<Element> = groupMembers(left.demoBox())
    val group = GroupElement(
        frame = boundingFrame(members.map { it.drawnBounds() }),
        children = members,
    )

    val turnedMembers: List<Element> = groupMembers(right.demoBox())
    val turned = GroupElement(
        frame = boundingFrame(turnedMembers.map { it.drawnBounds() }),
        children = turnedMembers,
        rotation = -12f,
    )

    return featureSlide(
        title = "Groups",
        subtitle = "a GroupElement of three shapes and a caption, flat and rotated",
        elements = listOf(
            group,
            captionUnder(left, "a group, as laid out"),
            turned,
            captionUnder(right, "the same group, rotation -12"),
        ),
        notes = "Look for: two identical arrangements of a circle, a diamond and a bar " +
            "with a line of text under them. The right-hand one is tilted slightly " +
            "anticlockwise as a single object: the three shapes and the text keep their " +
            "positions relative to each other, and the whole arrangement turns about its " +
            "own centre.",
    )
}

/** The three shapes and the caption every group on this slide is made of. */
private fun groupMembers(area: Frame): List<Element> {
    val row: Float = area.y + 60f
    val size = 180f
    val gap: Float = (area.width - 3 * size) / 4f

    return listOf(
        labelled(Frame(area.x + gap, row, size, size), ShapeKind.Ellipse, "one"),
        labelled(Frame(area.x + 2 * gap + size, row, size, size), ShapeKind.Diamond, "two"),
        labelled(Frame(area.x + 3 * gap + 2 * size, row, size, size), ShapeKind.Rectangle, "three"),
        TextElement(
            frame = Frame(area.x + gap, row + size + 30f, area.width - 2 * gap, 50f),
            text = "grouped, and moved as one",
            fontSize = 30f,
            align = TextAlign.Center,
            color = ShowcaseDefaults.bodyColor,
        ),
    )
}
