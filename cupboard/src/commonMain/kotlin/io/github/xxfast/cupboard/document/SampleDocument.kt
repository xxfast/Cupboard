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

/**
 * Shows off the highlighted code element, and the code steps it is walked
 * through: the deck's own document model, written out a piece at a time.
 *
 * Four states over four clicks. The block arrives holding the declaration with
 * an empty body (lines 1-3 and the closing 7, which is what the gutter's jump
 * from 3 to 7 is there to show), fills its properties in, brings the rest of the
 * file up, and finally drops everything but the two lines that use it.
 */
private fun codeSlide(): Slide {
    val title = TextElement(
        frame = Frame(146f, 130f, 1627f, 142f),
        text = "Slides as Data",
        fontSize = 94f,
        fontWeight = 700,
        letterSpacing = -1f,
        color = 0xFFFFFFFF,
    )
    val subtitle = TextElement(
        frame = Frame(146f, 281f, 1627f, 61f),
        text = "The document model, not compiled-in composables",
        fontSize = 39f,
        color = 0xFFA9A0D8,
    )
    val code = CodeElement(
        frame = Frame(146f, 390f, 1627f, 560f),
        language = "kotlin",
        fontSize = 28f,
        showLineNumbers = true,
        code = """
            // Slides are data, so one deck renders on every platform
            @Serializable
            data class Slide(
                val title: String = "Untitled",
                val depth: Int = 0,
                val elements: List<Element> = emptyList(),
            )

            fun Slide.aspect(): Float = 1920f / 1080f

            val opening = Slide(title = "Cupboard", depth = 1)
        """.trimIndent(),
        steps = listOf(
            CodeStep(reveal = listOf(LineRange(1, 3), LineRange(7, 7))),
            CodeStep(reveal = listOf(LineRange(1, 7))),
            CodeStep(),
            CodeStep(highlight = listOf(LineRange(9, 11))),
        ),
    )

    return Slide(
        title = "Slides as Data",
        depth = 1,
        elements = listOf(title, subtitle, code),
        builds = listOf(
            // The first build brings the block in at its first state; the rest
            // only advance it, and it stays on screen through all of them.
            Build(code.id),
            Build(code.id, codeStep = 1),
            Build(code.id, codeStep = 2),
            Build(code.id, codeStep = 3),
        ),
        notes = "The canvas never compiles a slide. It reads one. That is what keeps the " +
            "editor, the thumbnails, and play mode pixel-identical.",
    )
}

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
            codeSlide(),
            titleSlide("Native Interop"),
            titleSlide("Benchmarks"),
            titleSlide("Roadmap"),
        ),
    )
}
