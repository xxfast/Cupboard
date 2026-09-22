package io.github.xxfast.cupboard.document

/** A labelled card, which is what every build on these slides animates. */
private fun card(frame: Frame, label: String): ShapeElement =
    shapeElement(ShapeKind.Rectangle, frame, ShowcaseDefaults)
        .copy(label = label, labelSize = 26f, cornerRadius = 16f)

/** The build order: how an element arrives, moves and leaves. */
internal fun buildSlides(): List<Slide> = listOf(
    buildEffectsSlide(),
    buildOutSlide(),
    textDeliverySlide(),
    codeDeliverySlide(),
    buildTriggersSlide(),
    buildActionsSlide(),
)

/** Every [BuildEffect], off `entries`, one card each and one click each. */
private fun buildEffectsSlide(): Slide {
    val cards: List<ShapeElement> = BuildEffect.entries.map { effect ->
        card(gridCell(columns = 4, rows = 2, index = effect.ordinal).demoBox(), effect.name)
    }
    val captions: List<Element> = BuildEffect.entries.map { effect ->
        captionUnder(gridCell(columns = 4, rows = 2, index = effect.ordinal), "BuildEffect.$effect")
    }

    return featureSlide(
        title = "Build Effects",
        subtitle = "every BuildEffect, each on a click of its own",
        elements = cards + captions,
        builds = BuildEffect.entries.mapIndexed { index, effect ->
            Build(cards[index].id, effect = effect, durationMs = 700)
        },
        notes = "Look for: at rest the captions alone, with every card still to come.\n\n" +
            "In play: click ${BuildEffect.entries.size} times, one card per click, in " +
            "caption order. Appear should simply be there with no motion. FadeUp rises " +
            "as it fades, Pop overshoots, Dissolve only fades, MoveIn slides in from the " +
            "leading edge, Scale grows out of nothing and Wipe uncovers top to bottom. " +
            "Typewriter is a terminal's effect, so on a shape it should fall back to " +
            "Dissolve rather than doing nothing.",
    )
}

/** [BuildKind.Out]: what takes an element that opened with the slide away again. */
private fun buildOutSlide(): Slide {
    val effects: List<BuildEffect> =
        listOf(BuildEffect.Dissolve, BuildEffect.Wipe, BuildEffect.Scale)
    val cards: List<ShapeElement> = effects.mapIndexed { index, effect ->
        card(gridCell(columns = 3, rows = 1, index = index).demoBox(), "leaves on ${effect.name}")
    }
    val captions: List<Element> = effects.mapIndexed { index, effect ->
        captionUnder(gridCell(columns = 3, rows = 1, index = index), "Out, $effect")
    }

    return featureSlide(
        title = "Build Out",
        subtitle = "an Out build on an element nothing brought in",
        elements = cards + captions,
        builds = cards.mapIndexed { index, each ->
            Build(each.id, kind = BuildKind.Out, effect = effects[index], durationMs = 700)
        },
        notes = "Look for: at rest all three cards on the slide. An element with only Out " +
            "builds is there from the start, which is the rule this slide exists to " +
            "prove.\n\n" +
            "In play: click 3 times, one card leaving per click, left to right. Each " +
            "leaves on the effect its caption names, played backwards: Wipe should " +
            "re-cover rather than uncover, Scale should shrink into nothing. After the " +
            "third click only the captions are left.",
    )
}

/** Every [BuildDelivery] a text box has pieces for, one box each. */
private fun textDeliverySlide(): Slide {
    val texts: Map<BuildDelivery, String> = mapOf(
        BuildDelivery.All to "All of it, in one click.",
        BuildDelivery.ByParagraph to "First paragraph.\nSecond paragraph.\nThird paragraph.",
        BuildDelivery.ByWord to "One word at a time",
        BuildDelivery.ByCharacter to "Cupboard",
    )

    val boxes: List<TextElement> = texts.entries.mapIndexed { index, (_, text) ->
        TextElement(
            frame = gridCell(stage(560f), columns = 2, rows = 2, index = index).demoBox(),
            text = text,
            fontSize = 40f,
            lineHeight = 1.5f,
            color = ShowcaseDefaults.textColor,
        )
    }
    val captions: List<Element> = texts.keys.mapIndexed { index, delivery ->
        val cell: Frame = gridCell(stage(560f), columns = 2, rows = 2, index = index)
        captionUnder(cell, "BuildDelivery.$delivery")
    }

    return featureSlide(
        title = "Text Delivery",
        subtitle = "every BuildDelivery a text box has pieces for",
        elements = boxes + captions,
        builds = texts.keys.mapIndexed { index, delivery ->
            Build(boxes[index].id, delivery = delivery, durationMs = 300)
        },
        notes = "Look for: at rest the four captions alone.\n\n" +
            "In play: click 17 times. The first box arrives whole on one click. The " +
            "second takes three, a paragraph each. The third takes five, a word each, " +
            "left to right. The fourth takes eight, a letter each, spelling Cupboard. " +
            "Text that has not arrived yet must not shift the text that has: the lines " +
            "should hold their positions throughout.",
    )
}

/** [BuildDelivery.ByLine], which is how a code block and a terminal are walked. */
private fun codeDeliverySlide(): Slide {
    val code = CodeElement(
        frame = ShowcaseStage.copy(width = 1100f, height = 620f),
        language = "kotlin",
        fontSize = 30f,
        showLineNumbers = true,
        code = """
            val document = showcaseDocument()
            val slide = document.slides.first()
            val steps = slide.stepCount()
            println("${'$'}{slide.title}: ${'$'}steps steps")
        """.trimIndent(),
    )

    return featureSlide(
        title = "Code Delivery by Line",
        subtitle = "BuildDelivery.ByLine, a click per line of the block",
        elements = listOf(code),
        builds = listOf(
            Build(
                elementId = code.id,
                effect = BuildEffect.FadeUp,
                durationMs = 300,
                delivery = BuildDelivery.ByLine,
            ),
        ),
        notes = "Look for: at rest nothing but the header, the whole block still to come.\n\n" +
            "In play: click 4 times, one line per click, top to bottom. The gutter should " +
            "grow with the block rather than showing all four numbers from the first " +
            "click, and the lines already out must not move as the next one lands.",
    )
}

/** Every [BuildTrigger]: the one that costs a click, and the two that don't. */
private fun buildTriggersSlide(): Slide {
    val labels: List<String> = listOf(
        "OnClick",
        "WithPrevious",
        "AfterPrevious, 500ms",
        "OnClick",
        "AfterPrevious, 1000ms",
    )
    val cards: List<ShapeElement> = List(labels.size) { index ->
        card(gridCell(columns = 5, rows = 1, index = index).demoBox(), "${index + 1}")
            .copy(labelSize = 48f)
    }
    val captions: List<Element> = labels.mapIndexed { index, label ->
        captionUnder(gridCell(columns = 5, rows = 1, index = index), label, size = 21f)
    }

    return featureSlide(
        title = "Build Triggers",
        subtitle = "OnClick opens a step; WithPrevious and AfterPrevious ride one",
        elements = cards + captions,
        builds = listOf(
            Build(cards[0].id, durationMs = 500),
            Build(cards[1].id, trigger = BuildTrigger.WithPrevious, durationMs = 500),
            Build(
                elementId = cards[2].id,
                trigger = BuildTrigger.AfterPrevious,
                delayMs = 500,
                durationMs = 500,
            ),
            Build(cards[3].id, durationMs = 500),
            Build(
                elementId = cards[4].id,
                trigger = BuildTrigger.AfterPrevious,
                delayMs = 1000,
                durationMs = 500,
            ),
        ),
        notes = "Look for: at rest the five captions alone.\n\n" +
            "In play: click 2 times, not five. The first click brings cards 1 and 2 in " +
            "together, then card 3 follows on its own half a second later with no second " +
            "click. The second click brings card 4, and card 5 follows a full second " +
            "after that. If any card waits for a click of its own the triggers are not " +
            "being honoured.",
    )
}

/** Every [ActionKind], through [Build.action], on elements already on the slide. */
private fun buildActionsSlide(): Slide {
    val actions: List<Pair<ActionKind, BuildAction>> = listOf(
        ActionKind.Move to BuildAction(ActionKind.Move, dx = 0f, dy = -140f),
        ActionKind.Opacity to BuildAction(ActionKind.Opacity, opacity = 0.2f),
        ActionKind.Rotate to BuildAction(ActionKind.Rotate, rotation = 180f),
        ActionKind.Scale to BuildAction(ActionKind.Scale, scale = 1.4f),
    )

    // The cards sit low in their cells, so the Move has somewhere to travel to
    // and the Scale has room to grow into.
    val cards: List<ShapeElement> = actions.mapIndexed { index, (kind, _) ->
        val cell: Frame = gridCell(columns = 4, rows = 1, index = index)
        card(
            Frame(cell.x + 40f, cell.y + 320f, cell.width - 80f, 200f),
            kind.name,
        ).copy(kind = ShapeKind.Arrow)
    }
    val captions: List<Element> = actions.mapIndexed { index, (kind, _) ->
        captionUnder(gridCell(columns = 4, rows = 1, index = index), "ActionKind.$kind")
    }

    return featureSlide(
        title = "Build Actions",
        subtitle = "every ActionKind, on elements that are already on the slide",
        elements = cards + captions,
        builds = actions.mapIndexed { index, (_, action) ->
            Build.action(cards[index].id, action)
        },
        notes = "Look for: at rest four arrows sitting low in their columns. An action " +
            "never brings an element in, so all four are on the slide from the start.\n\n" +
            "In play: click 4 times, left to right. The first arrow slides 140 units up " +
            "and stays there. The second fades to a fifth of its strength. The third " +
            "turns a half circle about its own centre, ending pointing left. The fourth " +
            "grows to 1.4 times its size about its centre, so it spreads evenly rather " +
            "than growing down and right.",
    )
}
