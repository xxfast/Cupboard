package io.github.xxfast.cupboard.document

/**
 * The tints the transition run is painted in, cycled so no two neighbours share
 * one: a transition between two identical slides is a transition you cannot see.
 */
private val TransitionTints: List<Long> = listOf(
    0xFF2B2150,
    0xFF1E3A4C,
    0xFF4A2338,
    0xFF243F2C,
    0xFF3E3320,
    0xFF32224A,
)

/**
 * One slide per [TransitionKind], plus a run per direction for the three kinds
 * that have one.
 *
 * The transition belongs to the slide being *left*, Keynote's convention, so the
 * slide demonstrating Push is the one you advance *out of* to see it. Every
 * slide's notes say so, because it is the one thing about transitions that is
 * easy to read backwards.
 */
internal fun transitionSlides(): List<Slide> {
    val plain: List<Slide> = listOf(
        transitionSlide(
            name = "None",
            transition = SlideTransition(kind = TransitionKind.None),
            index = 0,
            look = "a hard cut, with no motion at all",
        ),
        transitionSlide(
            name = "Dissolve",
            transition = SlideTransition(kind = TransitionKind.Dissolve),
            index = 1,
            look = "this slide fading out as the next fades in",
        ),
    )

    val directional: List<Slide> = listOf(
        TransitionKind.Push to "both slides travelling together",
        TransitionKind.MoveIn to "the next slide sliding in over this one",
        TransitionKind.Wipe to "the next slide uncovered across this one",
    ).flatMapIndexed { kindIndex, (kind, look) ->
        TransitionDirection.entries.mapIndexed { directionIndex, direction ->
            transitionSlide(
                name = "$kind $direction",
                transition = SlideTransition(kind = kind, direction = direction),
                index = 2 + kindIndex * TransitionDirection.entries.size + directionIndex,
                look = "$look, ${direction.name.lowercase()}wards",
            )
        }
    }

    val automatic: Slide = transitionSlide(
        name = "Automatic",
        transition = SlideTransition(
            kind = TransitionKind.Dissolve,
            trigger = TransitionTrigger.Automatic,
            delayMs = 2000,
        ),
        index = 14,
        look = "this slide leaving on its own after two seconds, with no click",
    )

    return plain + directional + listOf(automatic) + magicMoveSlides()
}

/**
 * One transition demo: the transition's name centred on the stage, dressed in
 * its own tint so neighbouring slides differ.
 */
private fun transitionSlide(
    name: String,
    transition: SlideTransition,
    index: Int,
    look: String,
): Slide {
    val tint: Long = TransitionTints[index % TransitionTints.size]

    val label: TextElement = TextElement(
        frame = Frame(ShowcaseMargin, ShowcaseStage.y + (ShowcaseStage.height - 120f) / 2f, ShowcaseWidth, 120f),
        text = name,
        fontSize = 96f,
        fontWeight = BoldWeight,
        align = TextAlign.Center,
        color = ShowcaseDefaults.textColor,
    )

    return featureSlide(
        title = "Transition: $name",
        subtitle = "SlideTransition(${transition.kind}, ${transition.direction})",
        elements = listOf(label),
        notes = "In play: advance *out of* this slide, not into it. A transition belongs to " +
            "the slide being left. Expect $look.",
    ).copy(
        background = SlideBackground.Color(tint),
        transition = transition,
    )
}

/**
 * The Magic Move pair: the same three objects on two slides, moved, resized and
 * recoloured.
 *
 * Matched by [Element.matchKey], which is kind plus content, so what has to be
 * identical across the cut is the words and the shape rather than the ids. The
 * header lines deliberately differ, so they crossfade while the three travellers
 * fly.
 */
private fun magicMoveSlides(): List<Slide> {
    val before: List<Element> = listOf(
        TextElement(
            frame = Frame(ShowcaseMargin, 300f, 900f, 120f),
            text = "It travels",
            fontSize = 96f,
            fontWeight = BoldWeight,
            color = ShowcaseDefaults.textColor,
        ),
        shapeElement(ShapeKind.Ellipse, Frame(ShowcaseMargin, 470f, 260f, 260f), ShowcaseDefaults)
            .copy(label = "circle", labelSize = 28f),
        shapeElement(
            ShapeKind.Rectangle,
            Frame(ShowcaseMargin + 320f, 470f, 500f, 260f),
            ShowcaseDefaults,
        ).copy(label = "bar", labelSize = 28f, cornerRadius = 16f),
    )

    val after: List<Element> = listOf(
        TextElement(
            frame = Frame(1100f, 820f, 700f, 70f),
            text = "It travels",
            fontSize = 48f,
            fontWeight = BoldWeight,
            align = TextAlign.End,
            color = ShowcaseDefaults.accent,
        ),
        shapeElement(ShapeKind.Ellipse, Frame(1420f, 300f, 400f, 400f), ShowcaseDefaults)
            .copy(label = "circle", labelSize = 28f, fill = 0x6600C2A8, strokeColor = 0xCC00C2A8),
        shapeElement(ShapeKind.Rectangle, Frame(ShowcaseMargin, 300f, 260f, 520f), ShowcaseDefaults)
            .copy(
                label = "bar",
                labelSize = 28f,
                cornerRadius = 16f,
                fill = 0x66F5C518,
                strokeColor = 0xCCF5C518,
            ),
    )

    return listOf(
        featureSlide(
            title = "Magic Move: Before",
            subtitle = "three objects, about to be moved, resized and recoloured",
            elements = before,
            notes = "Look for: a heading, a circle and a wide bar, all on the left.\n\n" +
                "In play: advance out of this slide. The three objects should fly to " +
                "their new places on the next slide rather than crossfading: the " +
                "heading shrinks and moves to the bottom right, the circle grows and " +
                "crosses to the right, the bar turns from wide to tall. Only the " +
                "header lines, which differ between the two slides, should crossfade.",
        ).copy(transition = SlideTransition(kind = TransitionKind.MagicMove, durationMs = 900)),
        featureSlide(
            title = "Magic Move: After",
            subtitle = "the same three objects, matched by content rather than by id",
            elements = after,
            notes = "Look for: the same heading, circle and bar, now in three different " +
                "places, at three different sizes, and the circle and the bar in new " +
                "colours. Matching is by content (the words on the box), so the ids " +
                "differ on the two slides and the transition still finds them.\n\n" +
                "In play: step backwards to this slide's predecessor and the same " +
                "objects should fly home.",
        ),
    )
}

/** Where clicking an element takes the show: every [LinkTarget], as buttons. */
internal fun linkSlides(): List<Slide> = listOf(linkButtonsSlide())

private fun linkButtonsSlide(): Slide {
    // Every subtype of LinkTarget, in the order they read as a row of controls.
    val targets: List<Pair<String, LinkTarget>> = listOf(
        "First slide" to LinkTarget.First,
        "Previous slide" to LinkTarget.Previous,
        "Next slide" to LinkTarget.Next,
        "Last slide" to LinkTarget.Last,
        "Back to the cover" to LinkTarget.Slide(ShowcaseCoverId),
        "Open a URL" to LinkTarget.Url("https://github.com/xxfast"),
        "Exit the show" to LinkTarget.ExitShow,
    )

    // The first four are shapes and the rest are text boxes, so both kinds that
    // can hold a link are on the slide carrying one.
    val buttons: List<Element> = targets.flatMapIndexed { index, (label, target) ->
        val cell: Frame = gridCell(stage(620f), columns = 4, rows = 2, index = index)
        val box: Frame = cell.demoBox()
        val face: Element =
            if (index < 4) {
                shapeElement(ShapeKind.Rectangle, box, ShowcaseDefaults)
                    .copy(label = label, labelSize = 28f, cornerRadius = 20f, link = target)
            } else {
                // A text box draws from its top, so the link is given a band in
                // the middle of the cell rather than the whole of it: level with
                // the buttons above, and close to its own caption.
                TextElement(
                    frame = Frame(box.x, box.y + box.height / 2 - 30f, box.width, 60f),
                    text = label,
                    fontSize = 32f,
                    underline = true,
                    align = TextAlign.Center,
                    color = ShowcaseDefaults.accent,
                    linkTarget = target,
                )
            }
        listOf(face, captionUnder(cell, target.description()))
    }

    return featureSlide(
        title = "Link Targets",
        subtitle = "every LinkTarget: four on shapes, three on text boxes",
        elements = buttons,
        notes = "Look for: four filled buttons and three underlined links, each captioned " +
            "with the target it carries. Nothing is clickable in the editor: a click " +
            "there selects the element.\n\n" +
            "In play: click each in turn. First, Previous, Next and Last walk the deck. " +
            "Back to the cover jumps to the first slide of the deck by id, so it still " +
            "works after the deck is reordered. Open a URL should be handed to the host " +
            "and open a browser. Exit the show closes play mode.",
    )
}

/** What a target does, for the caption under its button. */
private fun LinkTarget.description(): String = when (this) {
    is LinkTarget.First -> "LinkTarget.First"
    is LinkTarget.Previous -> "LinkTarget.Previous"
    is LinkTarget.Next -> "LinkTarget.Next"
    is LinkTarget.Last -> "LinkTarget.Last"
    is LinkTarget.Slide -> "LinkTarget.Slide(id)"
    is LinkTarget.Url -> "LinkTarget.Url(...)"
    is LinkTarget.ExitShow -> "LinkTarget.ExitShow"
}
