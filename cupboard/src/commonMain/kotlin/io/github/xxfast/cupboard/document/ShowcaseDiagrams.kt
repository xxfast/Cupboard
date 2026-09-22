package io.github.xxfast.cupboard.document

/** A diagram in the deck's dress: nodes are shapes, edges are body copy. */
private fun diagram(frame: Frame, source: String, size: Float = 26f): DiagramElement =
    diagramElement(frame, ShowcaseDefaults).copy(source = source, fontSize = size)

/** Flowcharts, written as a mermaid subset and laid out by the renderer. */
internal fun diagramSlides(): List<Slide> = listOf(
    diagramShapesSlide(),
    diagramEdgesSlide(),
    diagramDirectionsSlide(),
    diagramStepsSlide(),
)

/** Every node outline the subset spells, in one chart. */
private fun diagramShapesSlide(): Slide {
    val chart: DiagramElement = diagram(
        frame = ShowcaseStage.copy(height = 700f),
        size = 30f,
        source = """
            graph LR
              A[Box] --> B(Rounded)
              B --> C([Stadium])
              C --> D{Diamond}
              D --> E((Circle))
        """.trimIndent(),
    )

    return featureSlide(
        title = "Diagram Node Shapes",
        subtitle = "every outline the subset spells: [ ], ( ), ([ ]), { } and (( ))",
        elements = listOf(chart),
        notes = "Look for: five nodes in a row, each drawn as the outline its own label " +
            "names. Box is square-cornered, Rounded has soft corners, Stadium is a full " +
            "pill, Diamond stands on a point and Circle is round. The labels must sit " +
            "inside their outlines rather than overflowing them.",
    )
}

/** Every edge style, labelled and unlabelled, headed and headless. */
private fun diagramEdgesSlide(): Slide {
    val left: Frame = gridCell(columns = 2, rows = 1, index = 0)
    val right: Frame = gridCell(columns = 2, rows = 1, index = 1)

    // Edge labels are plain words on purpose: a label spelling a connector
    // (`-.->`) is read as one by the parser and takes the edge with it.
    val headed: DiagramElement = diagram(
        frame = left.demoBox(),
        size = 32f,
        source = """
            graph TD
              A[solid] -->|solid| B[target]
              C[dotted] -.->|dotted| D[target]
              E[thick] ==>|thick| F[target]
        """.trimIndent(),
    )
    val headless: DiagramElement = diagram(
        frame = right.demoBox(),
        size = 32f,
        source = """
            graph TD
              A[solid] --- B[no head]
              C[dotted] -.- D[no head]
              E[thick] === F[no head]
              G[labelled] -- in words --> H[target]
        """.trimIndent(),
    )

    return featureSlide(
        title = "Diagram Edge Styles",
        subtitle = "solid, dotted and thick, with and without arrowheads",
        elements = listOf(
            headed,
            captionUnder(left, "arrowheads, each edge labelled with its own syntax"),
            headless,
            captionUnder(right, "headless connectors, and a label written in words"),
        ),
        notes = "Look for: on the left, three pairs joined by a solid, a dotted and a " +
            "visibly heavier line, each carrying the label that names it. On the right " +
            "the same three styles with no arrowheads at all, and a fourth edge labelled " +
            "'in words' from the `-- text -->` form. Labels should sit on their edges " +
            "rather than under a node.",
    )
}

/** Every [DiagramDirection], as the four header words that spell them. */
private fun diagramDirectionsSlide(): Slide {
    val headers: List<Pair<String, String>> = listOf(
        "LR" to "left to right",
        "TD" to "top down",
        "RL" to "right to left",
        "BT" to "bottom to top",
    )

    val charts: List<Element> = headers.flatMapIndexed { index, (header, meaning) ->
        tile(gridCell(columns = 2, rows = 2, index = index), "graph $header, $meaning") { box ->
            diagram(
                frame = box,
                size = 22f,
                source = "graph $header\n  A[First] --> B[Second]\n  B --> C[Third]",
            )
        }
    }

    return featureSlide(
        title = "Diagram Directions",
        subtitle = "graph LR, TD, RL and BT: the four ways a flowchart runs",
        elements = charts,
        notes = "Look for: the same three nodes laid out four ways. First is leftmost " +
            "under LR and rightmost under RL; topmost under TD and bottom-most under BT. " +
            "The arrows always point First to Second to Third, whichever way the chart " +
            "is stacked.",
    )
}

/** Diagram steps: reveal, then reveal more, then spotlight. */
private fun diagramStepsSlide(): Slide {
    val chart: DiagramElement = diagram(
        frame = ShowcaseStage.copy(height = 700f),
        size = 30f,
        source = """
            graph LR
              E[Edit] --> P[Present]
              P --> R{Render}
              R -->|cached| S([Skia])
              R -.->|cold| C((Compile))
        """.trimIndent(),
    ).copy(
        steps = listOf(
            DiagramStep(reveal = listOf("E", "P")),
            DiagramStep(reveal = listOf("E", "P", "R")),
            DiagramStep(),
            DiagramStep(highlight = listOf("R", "S")),
        ),
    )

    return featureSlide(
        title = "Diagram Steps",
        subtitle = "DiagramStep reveal then highlight, advanced by elementStep builds",
        elements = listOf(chart),
        builds = listOf(
            Build(chart.id),
            Build(chart.id, elementStep = 1),
            Build(chart.id, elementStep = 2),
            Build(chart.id, elementStep = 3),
        ),
        notes = "Look for: at rest the whole chart, five nodes.\n\n" +
            "In play: click 4 times. Edit and Present first, then Render joins them, then " +
            "Skia and Compile arrive, then Render and Skia stay bright while the rest " +
            "dims. An edge should only be drawn when both its ends are, so no arrow " +
            "should ever point into empty space. The layout must not shift between " +
            "steps: nodes hold their positions as others appear.",
    )
}

/** Equations: the LaTeX subset, in one dense slide and one of set pieces. */
internal fun equationSlides(): List<Slide> = listOf(
    equationFormsSlide(),
    equationSetPiecesSlide(),
)

/** One construct per tile, so a broken one is obvious against its neighbours. */
private fun equationFormsSlide(): Slide {
    val forms: List<Pair<String, String>> = listOf(
        "fractions" to """\frac{a + b}{c - d}""",
        "roots" to """\sqrt{x^2 + y^2} + \sqrt[3]{8}""",
        "scripts" to """x_i^{2n} + e^{-\lambda t}""",
        "big operators" to """\sum_{i=1}^{n} i = \frac{n(n+1)}{2}""",
        "delimiters" to """\left( \frac{1}{2} \right] \binom{n}{k}""",
        "accents" to """\hat{x} \bar{y} \vec{v} \dot{q} \ddot{q} \tilde{n}""",
        "greek" to """\alpha \beta \gamma \Delta \Sigma \Omega \varphi""",
        "prose" to """v = 3\,\text{m}/\text{s} \quad \mathbf{F} = m\vec{a}""",
    )

    val tiles: List<Element> = forms.flatMapIndexed { index, (label, latex) ->
        tile(gridCell(columns = 2, rows = 4, index = index, gap = 18f), label) { box ->
            EquationElement(
                frame = box,
                latex = latex,
                fontSize = 46f,
                color = ShowcaseDefaults.textColor,
            )
        }
    }

    return featureSlide(
        title = "Equation Forms",
        subtitle = "one construct per tile: the LaTeX subset the parser knows",
        elements = tiles,
        notes = "Look for: fractions stacked with a rule between them, a radical whose " +
            "hook covers its whole body and a cube root with a small 3, sub- and " +
            "superscripts set smaller and off the baseline, a sigma with its limits above " +
            "and below it, brackets grown to fit what they hold, six different accents " +
            "over six letters, greek with the lowercase italic and the uppercase upright, " +
            "and upright prose inside the last one. Anything drawn as a literal " +
            "backslash-command is a construct the parser does not know.",
    )
}

/** Three equations a talk would actually put on a slide. */
private fun equationSetPiecesSlide(): Slide {
    val pieces: List<String> = listOf(
        """\int_{0}^{\infty} e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}""",
        """\lim_{h \to 0} \frac{f(x + h) - f(x)}{h} = f'(x)""",
        """t_{frame} = \sum_{i=1}^{n} \frac{w_i}{f} \leq 16.6\,\text{ms}""",
    )

    val stacked: List<Element> = pieces.mapIndexed { index, latex ->
        EquationElement(
            frame = gridCell(columns = 1, rows = pieces.size, index = index, gap = 24f),
            latex = latex,
            fontSize = 72f,
            color = ShowcaseDefaults.textColor,
        )
    }

    return featureSlide(
        title = "Equation Set Pieces",
        subtitle = "three whole formulas, set at slide size",
        elements = stacked,
        notes = "Look for: three equations, none of them clipped left, right or by each " +
            "other. The integral's limits ride the sign, the limit's `h -> 0` sits under " +
            "the word lim, and every fraction bar is as wide as the wider of its two " +
            "halves. Nothing here is animated.",
    )
}
