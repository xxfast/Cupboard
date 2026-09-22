package io.github.xxfast.cupboard.document

/** One snippet, shown in every palette, so the palettes are the only difference. */
private val PaletteSample: String = """
    fun greet(name: String): String {
        val greeting = "Hello, ${'$'}name"
        return greeting.uppercase() // shout it
    }
""".trimIndent()

/** Code blocks: palettes, gutters, languages, steps and morphs. */
internal fun codeSlides(): List<Slide> = listOf(
    codeThemesSlide(),
    codeGuttersSlide(),
    codeLanguagesSlide(),
    codeStepsSlide(),
    codeMagicMoveSlide(),
    codeTokenTravelSlide(),
)

/** Every [CodeTheme], off `entries`, with one snippet in all of them. */
private fun codeThemesSlide(): Slide {
    val palettes: List<Element> = CodeTheme.entries.flatMapIndexed { index, theme ->
        val cell: Frame = gridCell(stage(560f), columns = 3, rows = 2, index = index)
        tile(cell, theme.name) { box ->
            CodeElement(frame = box, code = PaletteSample, fontSize = 19f, theme = theme)
        }
    }

    return featureSlide(
        title = "Code Themes",
        subtitle = "every CodeTheme, on the same four lines of Kotlin",
        elements = palettes,
        notes = "Look for: six blocks of the same code in six palettes. Notepad is the one " +
            "light block, so its ink must be dark and legible on a pale panel while the " +
            "slide around it stays dark. Keywords, the string and the comment should be " +
            "told apart in every palette, not just the first.",
    )
}

/** The gutter and the wrap, which are the two switches on a block's layout. */
private fun codeGuttersSlide(): Slide {
    val long: String = """
        // A deliberately long line, so wrapping has something to do with it
        val pipeline = stages.filter { it.enabled }.map { it.render(frame, budget) }.joinToString(" -> ")
        val short = 1
    """.trimIndent()

    val cases: List<Triple<String, Boolean, Boolean>> = listOf(
        Triple("no gutter, no wrap", false, false),
        Triple("gutter, no wrap", true, false),
        Triple("no gutter, wrapped", false, true),
        Triple("gutter, wrapped", true, true),
    )

    val blocks: List<Element> = cases.flatMapIndexed { index, (label, numbers, wrap) ->
        tile(gridCell(columns = 2, rows = 2, index = index), label) { box ->
            CodeElement(
                frame = box,
                code = long,
                fontSize = 20f,
                showLineNumbers = numbers,
                wrap = wrap,
            )
        }
    }

    return featureSlide(
        title = "Code Gutters and Wrapping",
        subtitle = "showLineNumbers and wrap, over a line too long for its box",
        elements = blocks,
        notes = "Look for: the two right-hand-column blocks numbering their lines 1, 2, 3 " +
            "in a gutter. In the top row the long line runs out of the box and is cut " +
            "off; in the bottom row it reflows under itself and the block stays three " +
            "logical lines, so the gutter still reads 1, 2, 3 rather than 1, 2, 3, 4.",
    )
}

/** A few of [CodeLanguages], to show the highlighter is not Kotlin-only. */
private fun codeLanguagesSlide(): Slide {
    val samples: List<Pair<String, String>> = listOf(
        "Kotlin" to """
            data class Slide(val title: String, val depth: Int = 0)

            fun Slide.isChild(): Boolean = depth > 0
        """.trimIndent(),
        "Swift" to """
            struct Slide { let title: String; var depth: Int = 0 }

            extension Slide { var isChild: Bool { depth > 0 } }
        """.trimIndent(),
        "Python" to """
            class Slide:
                def __init__(self, title, depth=0):
                    self.title = title  # what the navigator shows
                    self.depth = depth
        """.trimIndent(),
        "Shell" to """
            # build every target that has to stay clean
            ./gradlew :cupboard:jvmTest && echo "ok"
            export CUPBOARD_HOME="${'$'}HOME/.cupboard"
        """.trimIndent(),
    )

    val blocks: List<Element> = samples.flatMapIndexed { index, (language, code) ->
        tile(gridCell(columns = 2, rows = 2, index = index), language) { box ->
            CodeElement(frame = box, code = code, language = language, fontSize = 22f)
        }
    }

    return featureSlide(
        title = "Code Languages",
        subtitle = "four of CodeLanguages, each highlighted by its own rules",
        elements = blocks,
        notes = "Look for: keywords coloured in all four. Python's `def` and `class`, " +
            "Swift's `struct` and `let`, the shell's `#` comment and its quoted string. " +
            "An unknown language falls back to plain text rather than failing, so a " +
            "block with no colour at all means the name did not resolve.",
    )
}

/** Code steps: reveal, then more, then a spotlight, walked by `elementStep` builds. */
private fun codeStepsSlide(): Slide {
    val code = CodeElement(
        frame = ShowcaseStage.copy(height = 700f),
        language = "kotlin",
        fontSize = 30f,
        showLineNumbers = true,
        code = """
            fun Slide.stepCount(): Int =
                1 + (buildTimeline().maxOfOrNull { it.lastStep } ?: 0)

            fun Slide.buildTimeline(): List<BuildAt> {
                val timeline = mutableListOf<BuildAt>()
                for ((index, build) in builds.withIndex()) {
                    timeline += build.placed(timeline.lastOrNull())
                }
                return timeline
            }
        """.trimIndent(),
        steps = listOf(
            CodeStep(reveal = listOf(LineRange(1, 2))),
            CodeStep(reveal = listOf(LineRange(1, 2), LineRange(4, 10))),
            CodeStep(),
            CodeStep(highlight = listOf(LineRange(6, 8))),
        ),
    )

    return featureSlide(
        title = "Code Steps",
        subtitle = "CodeStep reveal and highlight, advanced by elementStep builds",
        elements = listOf(code),
        builds = listOf(
            // The first build brings the block in on its first state; the three
            // after it only advance it, so it stays on screen throughout.
            Build(code.id),
            Build(code.id, elementStep = 1),
            Build(code.id, elementStep = 2),
            Build(code.id, elementStep = 3),
        ),
        notes = "Look for: at rest the whole block, numbered 1 to 10.\n\n" +
            "In play: click 4 times. First the two-line `stepCount` alone, numbered 1 and " +
            "2. Then the rest arrives and the gutter jumps 2 to 4, because the numbers " +
            "count the original lines rather than what is showing. Then the blank line " +
            "fills in. Then lines 6 to 8 stay bright and everything else dims.",
    )
}

/** A Magic Move over three versions: a refactor most of the block survives. */
private fun codeMagicMoveSlide(): Slide {
    val code = CodeElement(
        frame = ShowcaseStage.copy(height = 560f),
        language = "kotlin",
        fontSize = 34f,
        code = """
            fun render(slide: Slide): Image {
                val canvas = Canvas(slide.size)
                canvas.draw(slide.elements)
                return canvas.image()
            }
        """.trimIndent(),
        versions = listOf(
            """
                fun render(slide: Slide, step: Int): Image {
                    val canvas = Canvas(slide.size)
                    canvas.draw(slide.visibleAt(step))
                    return canvas.image()
                }
            """.trimIndent(),
            """
                suspend fun render(slide: Slide, step: Int): Image = withContext(Render) {
                    val canvas = Canvas(slide.size)
                    canvas.draw(slide.visibleAt(step))
                    return@withContext canvas.image()
                }
            """.trimIndent(),
        ),
        steps = listOf(CodeStep(version = 0), CodeStep(version = 1), CodeStep(version = 2)),
    )

    return featureSlide(
        title = "Code Magic Move",
        subtitle = "versions plus CodeStep(version = n): the block morphs into its rewrite",
        elements = listOf(code),
        builds = listOf(
            Build(code.id),
            Build(code.id, elementStep = 1),
            Build(code.id, elementStep = 2),
        ),
        notes = "Look for: at rest, version 0 of the function.\n\n" +
            "In play: click 2 times. First `step: Int` is threaded through the signature " +
            "and the draw call; then the function becomes suspending and the body moves " +
            "into a `withContext`. Watch `canvas`, `slide.size` and the closing brace: " +
            "those tokens travel rather than fading out and back in. Anything that flies " +
            "across the block to a place it does not belong is a diff bug worth noting.",
    )
}

/** A second morph, where the surviving tokens change line rather than staying put. */
private fun codeTokenTravelSlide(): Slide {
    val code = CodeElement(
        frame = ShowcaseStage.copy(height = 560f),
        language = "kotlin",
        fontSize = 34f,
        code = """
            fun place(x: Float, y: Float, width: Float) =
                Frame(x, y, width, width * ratio)
        """.trimIndent(),
        versions = listOf(
            """
                fun place(width: Float, x: Float, y: Float) =
                    Frame(x, y, width, width * ratio)
            """.trimIndent(),
            """
                fun place(width: Float, x: Float, y: Float): Frame {
                    val height = width * ratio
                    return Frame(x, y, width, height)
                }
            """.trimIndent(),
        ),
        steps = listOf(CodeStep(version = 0), CodeStep(version = 1), CodeStep(version = 2)),
    )

    return featureSlide(
        title = "Code Tokens in Flight",
        subtitle = "the same morph where tokens change line, not only column",
        elements = listOf(code),
        builds = listOf(
            Build(code.id),
            Build(code.id, elementStep = 1),
            Build(code.id, elementStep = 2),
        ),
        notes = "Look for: at rest, a two-line expression function.\n\n" +
            "In play: click 2 times. First `width` moves to the front of the parameter " +
            "list, so one identifier travels a long way sideways past two others. Then " +
            "`width * ratio` is lifted into a local on a new line and `height` takes its " +
            "place in the call, so surviving tokens move down a row. Both moves should " +
            "read as travel rather than as the block being retyped.",
    )
}

/** Terminal sessions: the prompt rule, the chrome, and the typewriter build. */
internal fun terminalSlides(): List<Slide> = listOf(
    terminalSessionSlide(),
    terminalChromeSlide(),
)

/** Commands against output, which the prompt at the start of a line decides. */
private fun terminalSessionSlide(): Slide {
    val terminal = TerminalElement(
        frame = ShowcaseStage.copy(height = 700f),
        fontSize = 28f,
        text = """
            $ ./gradlew :cupboard:jvmTest
            > Task :cupboard:jvmTest
            BUILD SUCCESSFUL in 4s
            12 actionable tasks: 12 executed
            $ git status --short
             M cupboard/src/commonMain/kotlin/.../ShowcaseDocument.kt
            $
        """.trimIndent(),
    )

    return featureSlide(
        title = "Terminal Session",
        subtitle = "a line starting with the prompt is a command; every other line is output",
        elements = listOf(terminal),
        notes = "Look for: a window with a title bar reading zsh. Three lines start with a " +
            "green $ and are drawn bright; the four output lines are dimmer. The last " +
            "line is a bare prompt with nothing typed after it, and it should still read " +
            "as a command rather than as output.",
    )
}

/** The chrome switches, and the one effect only a terminal honours. */
private fun terminalChromeSlide(): Slide {
    val left: Frame = gridCell(columns = 2, rows = 1, index = 0)
    val right: Frame = gridCell(columns = 2, rows = 1, index = 1)

    val titled = TerminalElement(
        frame = left.demoBox(),
        fontSize = 22f,
        prompt = "➜",
        title = "cupboard: fish",
        text = """
            ➜ swift build --configuration release
            Compiling Cupboard 12/12
            Build complete! (18.4s)
            ➜ open macosApp/build/Cupboard.app
        """.trimIndent(),
    )
    val bare = TerminalElement(
        frame = right.demoBox(),
        fontSize = 22f,
        showTitleBar = false,
        text = """
            $ dotnet build winuiApp/WinUiApp
              Cupboard.Kotlin 0.1.0 -> mingwX64
              WinUiApp -> bin/Debug/WinUiApp.dll
            Build succeeded in 6.2s
        """.trimIndent(),
    )

    return featureSlide(
        title = "Terminal Chrome",
        subtitle = "a custom prompt and title, and a window with no title bar at all",
        elements = listOf(
            titled,
            captionUnder(left, "prompt ➜, custom title, typed in on one click"),
            bare,
            captionUnder(right, "showTitleBar = false"),
        ),
        builds = listOf(
            Build(titled.id, effect = BuildEffect.Typewriter, durationMs = 2400),
        ),
        notes = "Look for: the left window has a title bar reading its own title and uses " +
            "➜ as its prompt, so its two ➜ lines are the bright ones. The right window " +
            "has no title bar and no traffic lights, just the panel.\n\n" +
            "In play: click 1 time. The left session types itself out character by " +
            "character, and each block of output lands only once the command above it " +
            "has finished typing. The right window is there from the start.",
    )
}
