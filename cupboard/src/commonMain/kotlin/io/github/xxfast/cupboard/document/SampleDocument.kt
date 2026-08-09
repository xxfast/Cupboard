package io.github.xxfast.cupboard.document

/** The "Rendering Pipeline" slide from the design mock, as document data. */
fun sampleDocument(): Document {
    val title = TextElement(
        frame = Frame(72f, 64f, 800f, 70f),
        text = "Rendering Pipeline",
        fontSize = 46f,
        fontWeight = 700,
        letterSpacing = -0.5f,
        color = 0xFFFFFFFF,
    )
    val subtitle = TextElement(
        frame = Frame(72f, 138f, 800f, 30f),
        text = "How a Compose frame reaches the screen",
        fontSize = 19f,
        color = 0xFFA9A0D8,
    )
    val stages = listOf("Compose", "Layout", "Draw", "Present").mapIndexed { i, label ->
        ShapeElement(
            frame = Frame(72f + i * 194f, 250f, 150f, 76f),
            label = label,
            fill = if (label == "Draw") 0x24F5C518 else 0x387F52FF,
            strokeColor = if (label == "Draw") 0x99F5C518 else 0xB3A98FFF,
            labelColor = if (label == "Draw") 0xFFFFE28A else 0xFFD9CFFF,
        )
    }
    val arrows = List(3) { i ->
        TextElement(
            frame = Frame(228f + i * 194f, 278f, 32f, 24f),
            text = "→",
            fontSize = 18f,
            color = 0xFF6F66A8,
            align = TextAlign.Center,
        )
    }
    val image = ImageElement(frame = Frame(72f, 372f, 380f, 110f))
    val body = TextElement(
        frame = Frame(472f, 372f, 400f, 120f),
        text = "Each stage hands the previous one's output to the next. " +
            "The Draw stage is where Skia records the display list that " +
            "Present hands to Metal.",
        fontSize = 15f,
        lineHeight = 1.55f,
        color = 0xFFB8B3D6,
    )

    val slide = Slide(
        title = "Rendering Pipeline",
        elements = listOf(title, subtitle) + stages + arrows + listOf(image, body),
        builds = listOf(
            Build(stages[0].id),
            Build(arrows[0].id, trigger = BuildTrigger.WithPrevious),
            Build(stages[1].id),
            Build(arrows[1].id, trigger = BuildTrigger.WithPrevious),
            Build(stages[2].id),
            Build(arrows[2].id, trigger = BuildTrigger.WithPrevious),
            Build(stages[3].id),
        ),
        notes = "Walk the pipeline left to right. Pause on Draw: this is the part " +
            "we replicate identically on all three platforms.",
    )

    return Document(
        name = "Rendering Pipeline",
        nodes = listOf(
            Slide(title = "Cupboard"),
            Slide(title = "Agenda"),
            SlideGroup(
                title = "Why KMP",
                children = listOf(
                    Slide(title = "Why KMP"),
                    slide,
                    Slide(title = "Scene Graph"),
                ),
            ),
            Slide(title = "Native Interop"),
            Slide(title = "Benchmarks"),
            Slide(title = "Roadmap"),
        ),
    )
}
