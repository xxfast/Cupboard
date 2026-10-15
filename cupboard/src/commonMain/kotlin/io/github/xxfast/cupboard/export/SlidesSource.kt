package io.github.xxfast.cupboard.export

import io.github.xxfast.cupboard.document.AudioElement
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
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GalleryImage
import io.github.xxfast.cupboard.document.GradientKind
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.LineElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextBaseline
import io.github.xxfast.cupboard.document.TextCapitalization
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.TextRun
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.buildTimeline
import io.github.xxfast.cupboard.document.capitalized
import io.github.xxfast.cupboard.document.codeStepFor
import io.github.xxfast.cupboard.document.highlightedLines
import io.github.xxfast.cupboard.document.lineHeightPoints
import io.github.xxfast.cupboard.document.listBody
import io.github.xxfast.cupboard.document.listMarkers
import io.github.xxfast.cupboard.document.normalisedRuns
import io.github.xxfast.cupboard.document.resolved
import io.github.xxfast.cupboard.document.runsOver
import io.github.xxfast.cupboard.document.sourceAt
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
                for (element in slide.elements) {
                    if (element.hidden) continue
                    out.element(slide, timeline, element, 1f, null)
                }
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
        // Nothing drawn: the raster under the export still shows it.
        is LineElement -> line("// TODO(cupboard): the line shape is not exported yet")
        is CodeElement -> code(slide, element)
        is TerminalElement -> terminal(element)
        is DiagramElement -> diagram(element)
        is EquationElement -> equation(element)
        is ImageElement -> image(element)
        is GalleryElement -> gallery(element)
        is VideoElement -> video(element)
        is AudioElement -> audio(element)
        // Groups never reach here: they are flattened into their children above.
        is GroupElement -> Unit
    }
}

private fun SourceWriter.text(box: TextElement) {
    import(
        "androidx.compose.foundation.layout.fillMaxWidth",
        "androidx.compose.material3.Text",
        "androidx.compose.ui.Modifier",
        "androidx.compose.ui.text.font.FontFamily",
        "androidx.compose.ui.text.style.TextAlign",
        "androidx.compose.ui.unit.sp",
    )

    // The words in the box's style, and where a run (TextElement.runs) sets
    // its own, an AnnotatedString with that stretch styled the way the canvas
    // styles it. Range links (TextElement.links) are left behind, and their
    // words read the same without them.
    //
    // All caps and title case go over in the words, which is all they are on
    // the canvas too, so the runs let go of their casing. A run raised or
    // dropped goes over as a SpanStyle; the box's own baseline, small caps,
    // ligatures and vertical alignment are left behind, since Text takes none
    // of them as a parameter and the words read the same without them.
    val unshifted: List<TextRun> = box.runs.map { run ->
        run.copy(capitalization = null, baseline = run.baseline?.takeIf { it != TextBaseline.Normal })
    }
    val element: TextElement =
        box.copy(text = box.casedText(), runs = normalisedRuns(unshifted, box.text.length))
    val pieces: List<Pair<String, TextRun>> = element.displayPieces()
    block("Text(", ")") {
        if (pieces.all { (_, run) -> run.isPlain }) line("text = ${kotlinString(element.displayText())},")
        else {
            import(
                "androidx.compose.ui.text.SpanStyle",
                "androidx.compose.ui.text.buildAnnotatedString",
                "androidx.compose.ui.text.withStyle",
            )
            block("text = buildAnnotatedString {", "},") {
                for ((words, run) in pieces) {
                    if (run.isPlain) line("append(${kotlinString(words)})")
                    else block("withStyle(${spanStyle(element, run)}) {") {
                        line("append(${kotlinString(words)})")
                    }
                }
            }
        }
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
        line("lineHeight = ${element.drawnLineHeight().literal()}.sp,")
        if (element.letterSpacing != 0f) {
            line("letterSpacing = ${element.letterSpacing.literal()}.sp,")
        }
        line("fontFamily = FontFamily.${element.fontFamily.compose()},")
        line("modifier = Modifier.fillMaxWidth(),")
    }
}

/**
 * [displayText] cut where its style changes, each piece with the run it is set
 * in: a plain run for the box's own style, which a marker and the line breaks
 * always are.
 */
private fun TextElement.displayPieces(): List<Pair<String, TextRun>> {
    if (listStyle == ListStyle.None) {
        return runsOver(0, text.length).map { piece -> text.substring(piece.start, piece.end) to piece }
    }

    val markers: List<String> = listMarkers(text, listStyle)
    var at = 0
    return text.split("\n").flatMapIndexed { index, line ->
        val body: String = line.listBody()
        val start: Int = at + line.length - body.length
        at += line.length + 1

        val marker: String = markers.getOrElse(index) { "" }
        buildList {
            if (index > 0) add("\n" to TextRun())
            if (marker.isNotEmpty()) add("$marker " to TextRun())
            for (piece in runsOver(start, start + body.length)) {
                add(text.substring(piece.start, piece.end) to piece)
            }
        }
    }
}

/**
 * The SpanStyle a run draws in over the box's style: only what it sets, bar
 * the decorations, which are one property between them and so are written
 * whole once the run touches either.
 */
private fun SourceWriter.spanStyle(element: TextElement, run: TextRun): String {
    val drawn: TextRun = element.resolved(run)
    val fields: List<String> = buildList {
        run.color?.let { add("color = ${it.colorLiteral()}.argb()") }
        // A raised or dropped run draws smaller, the way the canvas draws it.
        val size: Float? =
            if (run.baseline != null) (drawn.fontSize ?: element.fontSize) * ShiftedBaselineScale
            else run.fontSize
        size?.let { add("fontSize = ${it.literal()}.sp") }
        run.baseline?.let {
            import("androidx.compose.ui.text.style.BaselineShift")
            add("baselineShift = BaselineShift.${it.name}")
        }
        run.fontWeight?.let {
            import("androidx.compose.ui.text.font.FontWeight")
            add("fontWeight = FontWeight($it)")
        }
        run.italic?.let {
            import("androidx.compose.ui.text.font.FontStyle")
            add("fontStyle = FontStyle.${if (it) "Italic" else "Normal"}")
        }
        if (run.underline != null || run.strikethrough != null) {
            import("androidx.compose.ui.text.style.TextDecoration")
            val decoration: String? = decoration(drawn.underline == true, drawn.strikethrough == true)
            add("textDecoration = ${decoration ?: "TextDecoration.None"}")
        }
        run.letterSpacing?.let { add("letterSpacing = ${it.literal()}.sp") }
        if (run.setsFont) add("fontFamily = FontFamily.${(drawn.fontFamily ?: element.fontFamily).compose()}")
    }
    return "SpanStyle(${fields.joinToString(", ")})"
}

/** How much smaller a raised or dropped run draws: the canvas's `ShiftedBaselineScale`. */
private const val ShiftedBaselineScale: Float = 2f / 3f

/**
 * [text] cased as the canvas draws it: by the box's casing, and by its own
 * wherever a run names one. A character for a character, so the runs' offsets
 * still hold over it.
 */
private fun TextElement.casedText(): String {
    val drawn: CharArray = text.capitalized(capitalization).toCharArray()
    for (run in runs) {
        val casing: TextCapitalization = run.capitalization ?: continue
        val from: Int = run.start.coerceIn(0, text.length)
        val to: Int = run.end.coerceIn(from, text.length)
        text.capitalized(casing).toCharArray().copyInto(drawn, from, from, to)
    }
    return drawn.concatToString()
}

/**
 * How far apart the lines draw, the way the canvas spaces them: the box's
 * spacing ([lineHeightPoints]), and its paragraph spacing in every line once
 * there is more than one paragraph, since Compose has no space between
 * paragraphs of its own.
 */
private fun TextElement.drawnLineHeight(): Float {
    if ('\n' !in text) return lineHeightPoints()
    return lineHeightPoints() + paragraphSpacingBefore + paragraphSpacingAfter
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

private fun TextElement.decoration(): String? = decoration(underline, strikethrough)

private fun decoration(underline: Boolean, strikethrough: Boolean): String? = when {
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
    TextAlign.Justify -> "Justify"
}

/** A named face exports as the generic family it falls back to: the exported project bundles no fonts. */
private fun TextFont.compose(): String = when (this) {
    TextFont.Sans -> "Default"
    TextFont.Serif -> "Serif"
    TextFont.Monospace -> "Monospace"
    else -> generic.compose()
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

    // A picture fill would need the bytes shipped beside the source, which this
    // writer doesn't do for any image: the gradient or solid under it stands in.
    if (element.imageFill != null) {
        line("// TODO(cupboard): the image fill is not exported yet, the fill under it stands in")
    }

    val shape: String = shapeLiteral(element)
    val size = "${element.frame.width.literal()}, ${element.frame.height.literal()}"
    val fill: String = element.gradient?.let { gradient ->
        val stops = "${gradient.start.colorLiteral()}, ${gradient.end.colorLiteral()}"
        when (gradient.kind) {
            GradientKind.Linear -> "gradientBrush($stops, ${gradient.angle.literal()}, $size)"
            GradientKind.Radial -> "radialGradientBrush($stops, ${gradient.centerX.literal()}, " +
                "${gradient.centerY.literal()}, ${gradient.radius.literal()}, $size)"
        }
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
 *
 * A step on a later version ([CodeStep.version]) exports that version's source,
 * so a morph exports as the text either side of it. The tween between the two is
 * not exported: the `when` hands over a new list of lines, and the exported deck
 * cuts where Cupboard morphs.
 */
private fun SourceWriter.code(slide: Slide, element: CodeElement) {
    val steps: Int = slide.stepCount()

    block("CodeBlock(", ")") {
        if (element.steps.isEmpty() || steps == 1) {
            codeLines(element, slide.codeStepFor(element, steps - 1), "lines = listOf(", "),")
        } else {
            block("lines = when (step) {", "},") {
                for (step in 0 until steps) {
                    val label: String = if (step == steps - 1) "else" else "$step"
                    codeLines(element, slide.codeStepFor(element, step), "$label -> listOf(", ")")
                }
            }
        }
        line("fontSize = ${element.fontSize.literal()},")
        line("theme = ${kotlinString(element.theme.name)},")
        line("showLineNumbers = ${element.showLineNumbers},")
    }
}

private fun SourceWriter.codeLines(
    element: CodeElement,
    step: CodeStep?,
    open: String,
    close: String,
) {
    val state: CodeStep = step ?: CodeStep()
    // The step's own version, since that is what its line ranges count in.
    val lines: List<String> = element.sourceAt(state.version).split("\n")
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

/**
 * A gallery exports as the one picture the editor is showing.
 *
 * Its steps are dropped rather than written out: the images have no bytes in the
 * export to cycle through, so a carousel of placeholders would say less than the
 * one frame the deck was authored on.
 */
private fun SourceWriter.gallery(element: GalleryElement) {
    line("// TODO(cupboard): a gallery exports as its current image, without its steps")
    image(element.currentImage())
}

/** The gallery as the single image it is showing, for anything that draws one picture. */
private fun GalleryElement.currentImage(): ImageElement {
    val at: Int = current.coerceIn(0, images.lastIndex.coerceAtLeast(0))
    val image: GalleryImage? = images.getOrNull(at)
    return ImageElement(
        id = id,
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        placeholder = "Gallery",
        assetId = image?.assetId,
        naturalWidth = image?.naturalWidth ?: 0,
        naturalHeight = image?.naturalHeight ?: 0,
        adjust = adjust,
        caption = if (showCaptions) image?.caption.orEmpty() else "",
    )
}

/**
 * A movie as the still it stands for. Nothing plays: the export is a Compose
 * source file, and playing is a platform player's job on the far side of
 * `MediaPlayerHost`, which an exported deck has none of.
 */
private fun SourceWriter.video(element: VideoElement) {
    line("// TODO(cupboard): a video exports as a still, and neither plays nor embeds")
    image(
        ImageElement(
            id = element.id,
            frame = element.frame,
            placeholder = element.title.ifBlank { element.webUrl ?: "Video" },
            assetId = element.posterAssetId,
        ),
    )
}

/** A sound as the box that says it is there, for [video]'s reason. */
private fun SourceWriter.audio(element: AudioElement) {
    line("// TODO(cupboard): an audio element exports as its title, and does not play")
    image(
        ImageElement(
            id = element.id,
            frame = element.frame,
            placeholder = element.title.ifBlank { "Audio" },
        ),
    )
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
