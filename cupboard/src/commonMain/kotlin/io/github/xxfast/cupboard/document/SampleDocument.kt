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
 * The line that travels: on "Why KMP" it is the subtitle, on the pipeline slide
 * that follows it is a caption in the top corner, and Magic Move carries it from
 * one to the other because both boxes say the same thing.
 */
private const val CarriedLine: String = "One codebase, three shells"

/** How far the pipeline's Draw stage swells while it is being talked about. */
private const val DrawEmphasis: Float = 1.15f

/**
 * "Why KMP", dressed in the Magic Move that hands [CarriedLine] to the pipeline
 * slide behind it.
 */
private fun whyKmpSlide(): Slide {
    val slide: Slide = titleSlide("Why KMP")
    val subtitle = TextElement(
        frame = Frame(146f, 281f, 1627f, 61f),
        text = CarriedLine,
        fontSize = 39f,
        color = 0xFFA9A0D8,
    )

    return slide.copy(
        elements = slide.elements + subtitle,
        transition = SlideTransition(kind = TransitionKind.MagicMove),
    )
}

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
            Build(code.id, elementStep = 1),
            Build(code.id, elementStep = 2),
            Build(code.id, elementStep = 3),
        ),
        notes = "The canvas never compiles a slide. It reads one. That is what keeps the " +
            "editor, the thumbnails, and play mode pixel-identical.",
    )
}

/**
 * Shows off the terminal element and its typewriter build: the two commands that
 * take the core to Windows, typed out one after the other with their output
 * landing between them.
 */
private fun terminalSlide(): Slide {
    val title = TextElement(
        frame = Frame(146f, 130f, 1627f, 142f),
        text = "Native Interop",
        fontSize = 94f,
        fontWeight = 700,
        letterSpacing = -1f,
        color = 0xFFFFFFFF,
    )
    val subtitle = TextElement(
        frame = Frame(146f, 281f, 1627f, 61f),
        text = "One core, three shells",
        fontSize = 39f,
        color = 0xFFA9A0D8,
    )
    val terminal = TerminalElement(
        frame = Frame(146f, 390f, 1627f, 560f),
        fontSize = 28f,
        text = """
            $ ./gradlew :winuiApp:packNuget
            BUILD SUCCESSFUL in 41s
            $ dotnet build winuiApp/WinUiApp
              Cupboard.Kotlin 0.1.0 -> mingwX64 + macosArm64
              WinUiApp -> bin/Debug/net10.0-windows/WinUiApp.dll
        """.trimIndent(),
    )

    return Slide(
        title = "Native Interop",
        elements = listOf(title, subtitle, terminal),
        // One build, and the whole block types itself out under it.
        builds = listOf(Build(terminal.id, effect = BuildEffect.Typewriter, durationMs = 2400)),
        notes = "The same Kotlin core the canvas runs on ships to Windows as a NuGet " +
            "package. Let the commands type themselves out.",
    )
}

/**
 * Shows off the diagram element and its steps: the frame's walk through the
 * scene graph, drawn from five lines of text.
 *
 * Four states over four clicks. The chart arrives holding the first two nodes,
 * grows the third, brings the rest up, and finally spotlights the two that
 * matter.
 */
private fun diagramSlide(): Slide {
    val title = TextElement(
        frame = Frame(146f, 130f, 1627f, 142f),
        text = "Scene Graph",
        fontSize = 94f,
        fontWeight = 700,
        letterSpacing = -1f,
        color = 0xFFFFFFFF,
    )
    val subtitle = TextElement(
        frame = Frame(146f, 281f, 1627f, 61f),
        text = "What a frame walks through",
        fontSize = 39f,
        color = 0xFFA9A0D8,
    )
    val diagram = DiagramElement(
        frame = Frame(146f, 390f, 1627f, 560f),
        fontSize = 30f,
        source = """
            graph LR
              C[Compose] --> L[Layout]
              L --> D[Draw]
              D --> P((Present))
              D -.->|cache| S[Skia]
        """.trimIndent(),
        steps = listOf(
            DiagramStep(reveal = listOf("C", "L")),
            DiagramStep(reveal = listOf("C", "L", "D")),
            DiagramStep(),
            DiagramStep(highlight = listOf("D", "P")),
        ),
    )

    return Slide(
        title = "Scene Graph",
        depth = 1,
        elements = listOf(title, subtitle, diagram),
        builds = listOf(
            // Same shape as the code slide's: the first build brings the chart in
            // at its first state, the rest only advance it.
            Build(diagram.id),
            Build(diagram.id, elementStep = 1),
            Build(diagram.id, elementStep = 2),
            Build(diagram.id, elementStep = 3),
        ),
        notes = "The diagram is five lines of text. Edit it live if someone asks what " +
            "happens after Draw.",
    )
}

/**
 * Shows off the equation element: the frame budget as one line of LaTeX, using
 * most of what the parser knows.
 *
 * No builds. An equation is read whole rather than walked through, and stepping
 * one is a different feature to drawing one.
 */
private fun equationSlide(): Slide {
    val title = TextElement(
        frame = Frame(146f, 130f, 1627f, 142f),
        text = "Frame Budget",
        fontSize = 94f,
        fontWeight = 700,
        letterSpacing = -1f,
        color = 0xFFFFFFFF,
    )
    val subtitle = TextElement(
        frame = Frame(146f, 281f, 1627f, 61f),
        text = "Sixteen milliseconds, spent three ways",
        fontSize = 39f,
        color = 0xFFA9A0D8,
    )
    val equation = EquationElement(
        frame = Frame(146f, 400f, 1627f, 420f),
        fontSize = 96f,
        latex = "t_{frame} = \\sum_{i=1}^{n} \\frac{w_i}{f} + " +
            "\\sqrt{\\alpha^2 + \\beta^2} \\leq 16.6\\,\\text{ms}",
    )

    return Slide(
        title = "Frame Budget",
        elements = listOf(title, subtitle, equation),
        notes = "The equation is one line of LaTeX. Every glyph on it is laid out " +
            "from the document, no webview in sight.",
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
    // Where the "Why KMP" subtitle lands: same words, so Magic Move flies it up
    // here and shrinks it into a caption.
    val carried = TextElement(
        frame = Frame(1200f, 60f, 600f, 50f),
        text = CarriedLine,
        fontSize = 24f,
        color = 0xFFA9A0D8,
    )
    val image = ImageElement(frame = Frame(146f, 757f, 773f, 224f))
    val body = TextElement(
        frame = Frame(960f, 757f, 814f, 244f),
        // Two paragraphs, because the build below hands them over one at a time.
        text = "Each stage hands the previous one's output to the next.\n" +
            "The Draw stage is where Skia records the display list that " +
            "Present hands to Metal.",
        fontSize = 31f,
        lineHeight = 1.55f,
        color = 0xFFB8B3D6,
    )

    val pipeline = Slide(
        title = "Rendering Pipeline",
        depth = 1,
        elements = listOf(title, subtitle, carried) + stages + arrows + listOf(image, body),
        builds = listOf(
            Build(stages[0].id),
            Build(arrows[0].id, trigger = BuildTrigger.WithPrevious),
            Build(stages[1].id),
            Build(arrows[1].id, trigger = BuildTrigger.WithPrevious),
            Build(stages[2].id),
            Build(arrows[2].id, trigger = BuildTrigger.WithPrevious),
            Build(stages[3].id),
            // A paragraph a click, then the drop frame goes to leave the caption
            // on its own: an Out build on an element nothing brought in.
            Build(body.id, delivery = BuildDelivery.ByParagraph),
            Build(image.id, kind = BuildKind.Out, effect = BuildEffect.Dissolve),
            // The stage under discussion swells on one click and settles on the
            // next. Two clicks rather than a chain, because actions compose per
            // step: a settle riding this build's own step would land with it and
            // cancel it out. Scales multiply, so the way back is the reciprocal.
            Build.action(
                elementId = stages[2].id,
                action = BuildAction(ActionKind.Scale, scale = DrawEmphasis),
                durationMs = 400,
            ),
            Build.action(
                elementId = stages[2].id,
                action = BuildAction(ActionKind.Scale, scale = 1f / DrawEmphasis),
            ),
        ),
        transition = SlideTransition(
            kind = TransitionKind.Push,
            direction = TransitionDirection.Left,
        ),
        notes = "Walk the pipeline left to right. Pause on Draw: this is the part " +
            "we replicate identically on all three platforms.",
    )

    return Document(
        name = "Rendering Pipeline",
        slides = listOf(
            titleSlide("Cupboard"),
            titleSlide("Agenda"),
            whyKmpSlide(),
            pipeline,
            diagramSlide(),
            codeSlide(),
            terminalSlide(),
            titleSlide("Benchmarks"),
            equationSlide(),
            titleSlide("Roadmap"),
        ),
    )
}
