package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildAt
import io.github.xxfast.cupboard.document.BuildDelivery
import io.github.xxfast.cupboard.document.BuildKind
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.buildTimeline
import io.github.xxfast.cupboard.document.codeStepFor
import io.github.xxfast.cupboard.document.highlightedLines
import io.github.xxfast.cupboard.document.listBody
import io.github.xxfast.cupboard.document.listMarkers
import io.github.xxfast.cupboard.document.stepCount
import io.github.xxfast.cupboard.document.visibleLines

/** The identifier a slide is exported under: its position in the presentation. */
internal fun slideIdentifier(index: Int): String = "slide${index + 1}"

/**
 * The slides that make it into the export, in presentation order.
 *
 * Skipped slides are left out, which is the whole point of skipping one. A deck
 * with every slide skipped is the exception: an export with no slides is a
 * project that doesn't run, so the skips are ignored rather than obeyed into an
 * empty presentation. The same rule the play layer's `toCupSlides` follows.
 */
internal fun Document.exportedSlides(): List<Slide> =
    slides.filterNot { it.skipped }.ifEmpty { slides }

/** `Slides.kt`: one CuP slide per exported slide, drawn from the document. */
internal fun slidesSource(document: Document, packageName: String): String {
    val out = SourceWriter()
    out.import("net.kodein.cup.Slide")

    document.exportedSlides().forEachIndexed { index, slide ->
        if (index != 0) out.line()
        for (note in slide.notes.split("\n")) {
            if (note.isNotBlank()) out.line("// Notes: ${note.trim()}")
        }
        val specs: String = slide.transition.specsArgument(out)
        val timeline: List<BuildAt> = slide.buildTimeline()
        out.block(
            "val ${slideIdentifier(index)} by " +
                "Slide(stepCount = ${slide.stepCount()}$specs) { step ->",
        ) {
            // Once per slide rather than once per build: what the reader needs to
            // know is that this slide plays something the export leaves out.
            if (slide.builds.any { it.kind == BuildKind.Action }) {
                out.line("// TODO(cupboard): action builds are not exported yet")
            }
            if (slide.builds.any { it.delivery != BuildDelivery.All }) {
                out.line("// TODO(cupboard): builds arrive whole, not piece by piece, yet")
            }
            out.block("Board {") {
                for (element in slide.elements) out.element(slide, timeline, element, 1f, null)
            }
        }
    }

    return out.toFile(packageName)
}

/**
 * The `specs = ` argument the slide is exported with, empty for a slide on the
 * deck's default.
 *
 * Best effort, and only the transition on the way out: CuP ships four sets and
 * we have six kinds, so the two that have no CuP equivalent take the nearest one
 * (Magic Move crossfades, a wipe moves along its axis). What the export is for is
 * a project that builds and plays, not a frame-perfect copy of our own player.
 */
private fun SlideTransition?.specsArgument(out: SourceWriter): String {
    val set: String = when (this?.kind) {
        null, TransitionKind.None -> return ""
        TransitionKind.Dissolve, TransitionKind.MagicMove -> "TransitionSet.fade"
        TransitionKind.Push, TransitionKind.MoveIn, TransitionKind.Wipe -> when (direction) {
            TransitionDirection.Left, TransitionDirection.Right -> {
                out.import("androidx.compose.ui.unit.LayoutDirection")
                "TransitionSet.moveHorizontal(LayoutDirection.Ltr)"
            }

            TransitionDirection.Up, TransitionDirection.Down -> "TransitionSet.moveVertical"
        }
    }

    out.import("net.kodein.cup.SlideSpecs", "net.kodein.cup.TransitionSet")
    return ", specs = SlideSpecs(endTransitions = $set)"
}

/**
 * One element, positioned and, when the build order has anything to say about it,
 * wrapped in the `Appear` that says it.
 *
 * [opacity] is what the element's ancestors multiply into it, and [reveal] the
 * wrapper a group's build has its children wait on: a group's children are drawn
 * as its siblings, so both have to travel down rather than nest.
 */
private fun SourceWriter.element(
    slide: Slide,
    timeline: List<BuildAt>,
    element: Element,
    opacity: Float,
    reveal: String?,
) {
    val appear: String? = timeline.revealOf(element.id) ?: reveal

    if (element is GroupElement) {
        if (element.rotation != 0f) line("// TODO(cupboard): group rotation is not exported yet")
        for (child in element.children) {
            element(slide, timeline, child, opacity * element.opacity, appear)
        }
        return
    }

    val at = "At(${element.frame.literal()}, " +
        "rotation = ${element.rotation.literal()}, " +
        "opacity = ${(opacity * element.opacity).literal()}) {"

    block(at) {
        if (appear != null) block("Appear($appear) {") { body(slide, element) }
        else body(slide, element)
    }
}

/**
 * The arguments the `Appear` around [elementId] takes, null for an element that
 * opens with the slide and never leaves: step 0 is the slide as it opens, so a
 * build landing there needs no wrapper at all.
 *
 * The element's first `In` build says when it arrives and what it plays in on,
 * and its first `Out` build when it goes, so an element with both is visible over
 * the range between them. An `AfterPrevious` build's wait is already in
 * [BuildAt.delayMs], so a chain runs itself out here as it does in play.
 */
private fun List<BuildAt>.revealOf(elementId: String): String? {
    val mine: List<BuildAt> = filter { it.build.elementId == elementId }
    val entry: BuildAt? = mine.firstOrNull { it.build.kind == BuildKind.In }
    val exit: BuildAt? = mine.firstOrNull { it.build.kind == BuildKind.Out }
    val first: Int = entry?.firstStep ?: 0
    if (exit == null && first == 0) return null

    val visible: String =
        if (exit == null) "step >= $first" else "step in $first until ${exit.firstStep}"
    // An element only an Out build touches was there from the start, so there is
    // no entry effect to play it in on.
    val build: Build = entry?.build ?: return visible
    val delay: String = if (entry.delayMs == 0) "" else ", delayMs = ${entry.delayMs}"
    return "$visible, effect = BuildEffect.${build.effect.name}, " +
        "durationMs = ${build.durationMs}$delay"
}

private fun Frame.literal(): String =
    "FrameDp(${x.literal()}, ${y.literal()}, ${width.literal()}, ${height.literal()})"

private fun SourceWriter.body(slide: Slide, element: Element) {
    when (element) {
        is TextElement -> text(element)
        is ShapeElement -> shape(element)
        is CodeElement -> code(slide, element)
        is TerminalElement -> terminal(element)
        is DiagramElement -> diagram(element)
        is EquationElement -> equation(element)
        is ImageElement -> image(element)
        // Groups never reach here: they are flattened into their children above.
        is GroupElement -> Unit
    }
}

private fun SourceWriter.text(element: TextElement) {
    import(
        "androidx.compose.foundation.layout.fillMaxWidth",
        "androidx.compose.material3.Text",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.text.font.FontFamily",
        "androidx.compose.ui.text.style.TextAlign",
        "androidx.compose.ui.unit.sp",
    )

    block("Text(", ")") {
        line("text = ${kotlinString(element.displayText())},")
        line("color = ${element.color.colorLiteral()}.argb(),")
        line("fontSize = ${element.fontSize.literal()}.sp,")
        if (element.fontWeight != 400) {
            import("androidx.compose.ui.text.font.FontWeight")
            line("fontWeight = FontWeight(${element.fontWeight}),")
        }
        if (element.italic) {
            import("androidx.compose.ui.text.font.FontStyle")
            line("fontStyle = FontStyle.Italic,")
        }
        element.decoration()?.let { decoration ->
            import("androidx.compose.ui.text.style.TextDecoration")
            line("textDecoration = $decoration,")
        }
        line("textAlign = TextAlign.${element.align.compose()},")
        line("lineHeight = ${(element.fontSize * element.lineHeight).literal()}.sp,")
        if (element.letterSpacing != 0f) {
            line("letterSpacing = ${element.letterSpacing.literal()}.sp,")
        }
        line("fontFamily = FontFamily.${element.fontFamily.compose()},")
        line("modifier = Modifier.fillMaxWidth(),")
    }
}

/** The text as it draws: each line behind its list marker, with the nesting tabs gone. */
private fun TextElement.displayText(): String {
    if (listStyle == ListStyle.None) return text
    val markers: List<String> = listMarkers(text, listStyle)
    return text.split("\n").mapIndexed { index, line ->
        val marker: String = markers.getOrElse(index) { "" }
        if (marker.isEmpty()) line.listBody() else "$marker ${line.listBody()}"
    }.joinToString("\n")
}

private fun TextElement.decoration(): String? = when {
    underline && strikethrough ->
        "TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))"
    underline -> "TextDecoration.Underline"
    strikethrough -> "TextDecoration.LineThrough"
    else -> null
}

private fun TextAlign.compose(): String = when (this) {
    TextAlign.Start -> "Start"
    TextAlign.Center -> "Center"
    TextAlign.End -> "End"
}

private fun TextFont.compose(): String = when (this) {
    TextFont.Sans -> "Default"
    TextFont.Serif -> "Serif"
    TextFont.Monospace -> "Monospace"
}

/** The kinds that export as themselves. Everything else falls back to a rounded box. */
private val DrawnKinds: Set<ShapeKind> = setOf(ShapeKind.Rectangle, ShapeKind.Ellipse)

private fun SourceWriter.shape(element: ShapeElement) {
    import(
        "androidx.compose.foundation.background",
        "androidx.compose.foundation.layout.Box",
        "androidx.compose.foundation.layout.fillMaxSize",
        "androidx.compose.ui.Alignment",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.unit.dp",
    )

    if (element.kind !in DrawnKinds) {
        line("// TODO(cupboard): the ${element.kind.name.lowercase()} shape is not exported yet")
    }

    val shape: String = shapeLiteral(element)
    val fill: String = element.gradient?.let { gradient ->
        "gradientBrush(${gradient.start.colorLiteral()}, ${gradient.end.colorLiteral()}, " +
            "${gradient.angle.literal()}, ${element.frame.width.literal()}, " +
            "${element.frame.height.literal()})"
    } ?: "${element.fill.colorLiteral()}.argb()"

    line("Box(")
    indented {
        line("modifier = Modifier")
        indented {
            line(".fillMaxSize()")
            val stroked: Boolean = element.strokeWidth > 0f
            line(".background($fill, $shape)${if (stroked) "" else ","}")
            if (stroked) {
                import("androidx.compose.foundation.border")
                line(
                    ".border(${element.strokeWidth.literal()}.dp, " +
                        "${element.strokeColor.colorLiteral()}.argb(), $shape),",
                )
            }
        }
        line("contentAlignment = Alignment.Center,")
    }
    block(") {") { label(element) }
}

private fun SourceWriter.label(element: ShapeElement) {
    if (element.label.isEmpty()) return
    import(
        "androidx.compose.foundation.layout.fillMaxWidth",
        "androidx.compose.material3.Text",
        "androidx.compose.ui.text.style.TextAlign",
        "androidx.compose.ui.unit.sp",
    )
    block("Text(", ")") {
        line("text = ${kotlinString(element.label)},")
        line("color = ${element.labelColor.colorLiteral()}.argb(),")
        line("fontSize = ${element.labelSize.literal()}.sp,")
        line("textAlign = TextAlign.Center,")
        line("modifier = Modifier.fillMaxWidth(),")
    }
}

private fun SourceWriter.shapeLiteral(element: ShapeElement): String = when {
    element.kind == ShapeKind.Ellipse -> {
        import("androidx.compose.foundation.shape.CircleShape")
        "CircleShape"
    }

    element.kind == ShapeKind.Rectangle && element.cornerRadius <= 0f -> {
        import("androidx.compose.ui.graphics.RectangleShape")
        "RectangleShape"
    }

    else -> {
        import("androidx.compose.foundation.shape.RoundedCornerShape")
        "RoundedCornerShape(${element.cornerRadius.coerceAtLeast(0f).literal()}.dp)"
    }
}

/**
 * A code block, in the state each step shows it in.
 *
 * A block with steps of its own is emitted as a `when (step)` over the slide's
 * steps, since which of its states is showing is the slide's build order to
 * decide. A block without them draws whole and needs no `when` at all.
 */
private fun SourceWriter.code(slide: Slide, element: CodeElement) {
    val lines: List<String> = element.code.split("\n")
    val steps: Int = slide.stepCount()

    block("CodeBlock(", ")") {
        if (element.steps.isEmpty() || steps == 1) {
            codeLines(lines, slide.codeStepFor(element, steps - 1), "lines = listOf(", "),")
        } else {
            block("lines = when (step) {", "},") {
                for (step in 0 until steps) {
                    val label: String = if (step == steps - 1) "else" else "$step"
                    codeLines(lines, slide.codeStepFor(element, step), "$label -> listOf(", ")")
                }
            }
        }
        line("fontSize = ${element.fontSize.literal()},")
        line("theme = ${kotlinString(element.theme.name)},")
        line("showLineNumbers = ${element.showLineNumbers},")
    }
}

private fun SourceWriter.codeLines(
    lines: List<String>,
    step: CodeStep?,
    open: String,
    close: String,
) {
    val state: CodeStep = step ?: CodeStep()
    val shown: List<Int> = visibleLines(lines.size, state)
    val lit: Set<Int> = highlightedLines(lines.size, state)

    block(open, close) {
        for (number in shown) {
            val text: String = kotlinString(lines[number - 1])
            if (lit.isNotEmpty() && number !in lit) line("CodeLine($number, $text, dimmed = true),")
            else line("CodeLine($number, $text),")
        }
    }
}

private fun SourceWriter.terminal(element: TerminalElement) {
    block("TerminalBox(", ")") {
        line("text = ${kotlinString(element.text)},")
        line("prompt = ${kotlinString(element.prompt)},")
        line("title = ${if (element.showTitleBar) kotlinString(element.title) else "null"},")
        line("fontSize = ${element.fontSize.literal()},")
    }
}

private fun SourceWriter.diagram(element: DiagramElement) {
    import(
        "androidx.compose.material3.Text",
        "androidx.compose.ui.text.font.FontFamily",
        "androidx.compose.ui.unit.sp",
    )
    line("// TODO(cupboard): diagram layout is not exported yet")
    block("DashedBox {") {
        block("Text(", ")") {
            line("text = ${kotlinString(element.source)},")
            line("color = ${element.nodeText.colorLiteral()}.argb(),")
            line("fontSize = ${element.fontSize.literal()}.sp,")
            line("fontFamily = FontFamily.Monospace,")
        }
    }
}

private fun SourceWriter.equation(element: EquationElement) {
    import(
        "androidx.compose.foundation.layout.fillMaxWidth",
        "androidx.compose.material3.Text",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.text.font.FontFamily",
        "androidx.compose.ui.text.font.FontStyle",
        "androidx.compose.ui.text.style.TextAlign",
        "androidx.compose.ui.unit.sp",
    )
    line("// TODO(cupboard): equation layout is not exported yet")
    block("Text(", ")") {
        line("text = ${kotlinString(element.latex)},")
        line("color = ${element.color.colorLiteral()}.argb(),")
        line("fontSize = ${element.fontSize.literal()}.sp,")
        line("fontStyle = FontStyle.Italic,")
        line("fontFamily = FontFamily.Serif,")
        line("textAlign = TextAlign.Center,")
        line("modifier = Modifier.fillMaxWidth(),")
    }
}

private fun SourceWriter.image(element: ImageElement) {
    import(
        "androidx.compose.foundation.layout.fillMaxWidth",
        "androidx.compose.material3.Text",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.text.style.TextAlign",
        "androidx.compose.ui.unit.sp",
    )
    line("// TODO(cupboard): image content is not exported yet")
    block("DashedBox {") {
        block("Text(", ")") {
            line("text = ${kotlinString(element.placeholder)},")
            line("color = 0xFF6F66A8L.argb(),")
            line("fontSize = 28.0f.sp,")
            line("textAlign = TextAlign.Center,")
            line("modifier = Modifier.fillMaxWidth(),")
        }
    }
}
