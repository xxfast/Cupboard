package io.github.xxfast.cupboard.document

/** A slide with just a styled title, for the deck's simple rows. */
private fun titleSlide(title: String, depth: Int = 0): Slide = Slide(
    title = title,
    depth = depth,
    elements = listOf(
        TextElement(
            frame = Frame(146f, 130f, 1627f, 191f),
            text = title,
            fontSize = 94f,
            fontWeight = 700,
            letterSpacing = -1f,
            color = 0xFFFFFFFF,
        )
    ),
)

/** The "Rendering Pipeline" slide from the design mock, as document data. */
fun sampleDocument(): Document {
    val title = TextElement(
        frame = Frame(146f, 130f, 1627f, 142f),
        text = "Rendering Pipeline",
        fontSize = 94f,
        fontWeight = 700,
        letterSpacing = -1f,
        color = 0xFFFFFFFF,
    )
    val subtitle = TextElement(
        frame = Frame(146f, 281f, 1627f, 61f),
        text = "How a Compose frame reaches the screen",
        fontSize = 39f,
        color = 0xFFA9A0D8,
    )
    val stages = listOf("Compose", "Layout", "Draw", "Present").mapIndexed { i, label ->
        ShapeElement(
            frame = Frame(146f + i * 395f, 508f, 305f, 155f),
            label = label,
            cornerRadius = 20f,
            strokeWidth = 3f,
            labelSize = 31f,
            fill = if (label == "Draw") 0x24F5C518 else 0x387F52FF,
            strokeColor = if (label == "Draw") 0x99F5C518 else 0xB3A98FFF,
            labelColor = if (label == "Draw") 0xFFFFE28A else 0xFFD9CFFF,
        )
    }
    val arrows = List(3) { i ->
        TextElement(
            frame = Frame(464f + i * 395f, 565f, 65f, 49f),
            text = "→",
            fontSize = 37f,
            color = 0xFF6F66A8,
            align = TextAlign.Center,
        )
    }
    val image = ImageElement(frame = Frame(146f, 757f, 773f, 224f))
    val body = TextElement(
        frame = Frame(960f, 757f, 814f, 244f),
        text = "Each stage hands the previous one's output to the next. " +
            "The Draw stage is where Skia records the display list that " +
            "Present hands to Metal.",
        fontSize = 31f,
        lineHeight = 1.55f,
        color = 0xFFB8B3D6,
    )

    val pipeline = Slide(
        title = "Rendering Pipeline",
        depth = 1,
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
        slides = listOf(
            titleSlide("Cupboard"),
            titleSlide("Agenda"),
            titleSlide("Why KMP"),
            pipeline,
            titleSlide("Scene Graph", depth = 1),
            titleSlide("Native Interop"),
            titleSlide("Benchmarks"),
            titleSlide("Roadmap"),
        ),
    )
}
