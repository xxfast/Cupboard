package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildAt
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.DiagramStep
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GalleryImage
import io.github.xxfast.cupboard.document.GalleryStepDuration
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageAdjust
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ImageFill
import io.github.xxfast.cupboard.document.ImageMask
import io.github.xxfast.cupboard.document.LineDraw
import io.github.xxfast.cupboard.document.LineDrawDirection
import io.github.xxfast.cupboard.document.LineElement
import io.github.xxfast.cupboard.document.LineEnd
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.PieceReveal
import io.github.xxfast.cupboard.document.Point
import io.github.xxfast.cupboard.document.ShadowKind
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapePath
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.StrokeStyle
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.colorMatrix
import io.github.xxfast.cupboard.document.dx
import io.github.xxfast.cupboard.document.dy
import io.github.xxfast.cupboard.document.imageSourceRect
import io.github.xxfast.cupboard.document.listBody
import io.github.xxfast.cupboard.document.listIndentLevel
import io.github.xxfast.cupboard.document.listMarkers
import io.github.xxfast.cupboard.document.pieces
import io.github.xxfast.cupboard.document.resolvedLink
import io.github.xxfast.cupboard.document.sourceRect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

fun Long.toComposeColor(): Color = Color(this)

/**
 * Positions [element] at its frame (1dp == 1 doc unit inside [SlideSurface]) and
 * renders it. Opacity, rotation and both flips ride one graphics layer around the
 * frame's center, so they cost the same as the opacity layer alone used to.
 *
 * [originX] and [originY] are the slide coordinates the offset is measured from:
 * the slide's own corner at the top level, the group's corner for a group's
 * children. Children are stored in absolute slide coordinates, so laying them out
 * inside their group's box would otherwise apply the group's offset twice.
 *
 * [codeStep] and [diagramStep] are the states a [CodeElement] and a
 * [DiagramElement] draw in, resolved by the caller: from the slide's build order
 * in play, and from the step row picked in the inspector on the editor canvas.
 * Null, the editor's case with no row picked, is the whole of either. Only a
 * top-level element gets one: a stepped element inside a group draws whole, since
 * a build names an element the slide holds.
 *
 * [codeStepBuild] is the build that put a [CodeElement] on [codeStep], resolved
 * by the caller the same way (`Slide.elementStepBuildAt`). A code block reads it
 * for [BuildEffect.Typewriter] alone, typing its way onto a step that build
 * moves forward onto. Null the same way [codeStep] is, and then a step change
 * plays as it always has.
 *
 * [entry] is the build that brings the element in, for the kinds that animate
 * themselves rather than being faded in from outside: [TerminalElement] reads it
 * for [BuildEffect.Typewriter], [LineElement] for [BuildEffect.LineDraw]. Null the
 * same way [codeStep] is: the editor, and anything nested in a group.
 *
 * [pieces] is how much of the element is out, for a build that hands it over a
 * piece at a time: a text box styles the pieces still to come out of sight, and
 * a terminal cuts its transcript to the lines that have arrived. A code block is
 * cut by [codeStep] instead, since a step is already what says which of its lines
 * are showing. Null is the whole element, which is the editor and every build
 * that delivers [io.github.xxfast.cupboard.document.BuildDelivery.All].
 *
 * [galleryIndex] is the picture a [GalleryElement] draws, resolved by the caller
 * from the build order (`Slide.galleryImageAt`). Null is the editor, which shows
 * the image being authored ([GalleryElement.current]) and none of the dots the
 * audience gets.
 *
 * [playing] is whether this is the show rather than the editor, and the whole of
 * what a movie and a sound need to know about which: only there do they autoplay,
 * answer a tap and stop themselves on the way out. The editor draws the same
 * poster and the same pill, at rest.
 *
 * [transform] overrides the element's own opacity and rotation and rides a
 * translation and a scale on top of its frame, for an element in flight: a Magic
 * Move between two slides is the one thing that draws one. Null is the element at
 * rest, which is everything else.
 *
 * [alignment] is where a text element's words sit in its box, overriding the
 * element's own: a Magic Move blends it between the two slides' alignments, since
 * there is no in-between of `Start` and `End` on the element itself. Null is the
 * element's own, which is everything but a text in flight.
 */
@Composable
fun ElementView(
    element: Element,
    modifier: Modifier = Modifier,
    originX: Float = 0f,
    originY: Float = 0f,
    codeStep: CodeStep? = null,
    codeStepBuild: BuildAt? = null,
    diagramStep: DiagramStep? = null,
    galleryIndex: Int? = null,
    entry: Build? = null,
    pieces: PieceReveal? = null,
    transform: ElementTransform? = null,
    alignment: Alignment? = null,
    playing: Boolean = false,
) {
    // A translucent layer composites offscreen, into a buffer the size of its own
    // box, so whatever the element paints past its frame is cut off for as long as
    // it is dimmed. The layer is grown by that much on every side, which keeps its
    // centre and so every transform, and the frame is padded back inside it. A
    // build's wrapper is a layer of its own and does the same (`SlideView`).
    val bleed: Dp = element.shadowBleed().dp

    Box(
        modifier = modifier
            .offset((element.frame.x - originX).dp, (element.frame.y - originY).dp)
            .size(element.frame.width.dp, element.frame.height.dp)
            .outset(bleed)
            .graphicsLayer {
                alpha = transform?.opacity ?: element.opacity
                rotationZ = transform?.rotation ?: element.rotation
                scaleX = (if (element.flippedHorizontally) -1f else 1f) * (transform?.scaleX ?: 1f)
                scaleY = (if (element.flippedVertically) -1f else 1f) * (transform?.scaleY ?: 1f)
                translationX = (transform?.translationX ?: 0f).dp.toPx()
                translationY = (transform?.translationY ?: 0f).dp.toPx()
                transformOrigin = TransformOrigin.Center
            }
            .padding(bleed)
    ) {
        when (element) {
            is TextElement -> TextElementView(element, pieces, alignment ?: element.alignment())
            is ShapeElement -> ShapeElementView(element)
            is LineElement -> LineElementView(element, entry)
            is ImageElement -> ImageElementView(element)
            is GalleryElement -> GalleryElementView(
                element = element,
                imageIndex = galleryIndex ?: element.current,
                // The dots are for an audience being walked through the pictures,
                // not for the one canvas that shows whichever is being edited.
                indicators = galleryIndex != null,
            )

            is VideoElement -> VideoElementView(element, playing)
            is AudioElement -> AudioElementView(element, playing)
            is CodeElement -> CodeElementView(element, codeStep, codeStepBuild)
            is TerminalElement -> TerminalElementView(element, entry, pieces?.linesShown())
            is DiagramElement -> DiagramElementView(element, diagramStep)
            is EquationElement -> EquationElementView(element)
            // The group draws nothing of its own: it is the box its transforms
            // hang off, and its children draw inside it. A nested group recurses
            // through here and re-bases its own children the same way.
            is GroupElement -> for (child in element.children) {
                if (child.hidden) continue
                ElementView(child, originX = element.frame.x, originY = element.frame.y)
            }
        }
    }
}

/**
 * Lays out what follows [by] bigger on every side, around the same centre, while
 * the parent still sees the box it asked for: an overhang rather than a resize,
 * so a slide never squeezes an element whose reach runs past its edge.
 */
internal fun Modifier.outset(by: Dp): Modifier = layout { measurable, constraints ->
    val reach: Int = by.roundToPx()
    val placeable: Placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth + reach * 2, constraints.maxHeight + reach * 2)
    )

    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-reach, -reach) }
}

/**
 * How far past its frame the element's shadow can reach, in document units: zero
 * for everything that casts none.
 *
 * A shadow reaches by its offset, by its blur's fall-off, and by whatever its
 * kind spreads past the outline (see [ShapeShadow.reach]). A group reaches as far
 * as the furthest of its children, since theirs is the shadow its own layer
 * would cut.
 *
 * A line's frame is as thin as 1pt and its stroke and heads hang off it on every
 * side, so it reaches by those on top of its shadow's.
 */
internal fun Element.shadowBleed(): Float = when (this) {
    is LineElement ->
        ceil(strokeWidth / 2 + lineHeadSize(strokeWidth) + (shadow?.reach(frame) ?: 0f))

    is ShapeElement -> shadow?.let { ceil(it.reach(frame)) } ?: 0f
    is GroupElement -> children.filterNot { it.hidden }.maxOfOrNull { it.shadowBleed() } ?: 0f
    else -> 0f
}

/**
 * How many blur radii out a blurred edge still shows. Compose's radius is about
 * 1.7 standard deviations, and three of those is where the tail is gone.
 */
private const val BlurReach: Float = 2.5f

/** How deep a [ShadowKind.Curved] shadow bows at full curve, against the shape's width. */
private const val CurveDepth: Float = 0.08f

/** Where a [ShadowKind.Contact] ellipse stops flattening, so it never stands taller than wide. */
private const val MaxPerspective: Float = 45f

/** How far past a [frame] this shadow paints, on any side. */
private fun ShapeShadow.reach(frame: Frame): Float {
    val spread: Float = offset + blur * BlurReach
    return when (kind) {
        ShadowKind.Drop -> spread
        ShadowKind.Contact -> spread + contactHeight(frame.width) / 2
        ShadowKind.Curved -> spread + abs(curveDepth(frame.width))
    }
}

/** How tall the ellipse a contact shadow lays under a [width]-wide base is. */
private fun ShapeShadow.contactHeight(width: Float): Float =
    width * tan(perspective.coerceIn(0f, MaxPerspective) * PI.toFloat() / 180f)

/** How far below the bottom edge a curved shadow's middle sits: negative is above it. */
private fun ShapeShadow.curveDepth(width: Float): Float =
    curve.coerceIn(-1f, 1f) * width * CurveDepth

/**
 * Where the shadow sits against the element, in the element's own space and
 * document units.
 *
 * The dial is the slide's, so the step is turned back by the element's rotation
 * and mirrored by its flips: drawn inside the element's layer, which turns and
 * flips it again, it lands where the dial says however the shape is turned. A
 * contact shadow lies on the shape's own base, so it drops straight down that.
 */
private fun Element.shadowStep(shadow: ShapeShadow): Offset {
    if (shadow.kind == ShadowKind.Contact) return Offset(0f, shadow.offset)

    val radians: Float = rotation * PI.toFloat() / 180f
    val x: Float = shadow.dx * cos(radians) + shadow.dy * sin(radians)
    val y: Float = -shadow.dx * sin(radians) + shadow.dy * cos(radians)
    return Offset(
        x = if (flippedHorizontally) -x else x,
        y = if (flippedVertically) -y else y,
    )
}

/**
 * [shadow] under the element whose box this sits in, on a layer of its own:
 * [draw] paints the shadow's shape in its colour into a box the element's size,
 * and the layer moves it by the [step], dims it by the opacity and blurs it.
 *
 * Its own layer because a blur is a layer's effect, and the element's would blur
 * the element with it. The layer overhangs the box by [reach] on every side, so
 * the blur has room to fall off before the layer's edge cuts it; the element's
 * own layer has made the same room (see [shadowBleed]).
 */
@Composable
private fun BoxScope.ShadowLayer(
    shadow: ShapeShadow,
    step: Offset,
    reach: Float,
    draw: DrawScope.(box: Size, color: Color) -> Unit,
) {
    Canvas(
        Modifier
            .matchParentSize()
            .outset(reach.dp)
            .graphicsLayer {
                alpha = shadow.opacity.coerceIn(0f, 1f)
                val radius: Float = shadow.blur.dp.toPx()
                // A zero radius is no blur rather than a filter that does nothing.
                renderEffect =
                    if (radius <= 0f) null else BlurEffect(radius, radius, TileMode.Decal)
            },
    ) {
        val inset: Float = reach.dp.toPx()
        val box = Size(size.width - inset * 2, size.height - inset * 2)
        translate(inset + step.x.dp.toPx(), inset + step.y.dp.toPx()) {
            draw(box, shadow.color.toComposeColor())
        }
    }
}

/**
 * A shape's shadow, by its kind: a [ShadowKind.Drop] is the outline itself, a
 * [ShadowKind.Contact] a flattened ellipse centred on the base, and a
 * [ShadowKind.Curved] the lower half of the box with its bottom edge bowed, so
 * inwards it peeks out at the two corners and outwards it bellies in the middle.
 */
@Composable
private fun BoxScope.ShapeShadowLayer(element: ShapeElement, shadow: ShapeShadow, shape: Shape) {
    ShadowLayer(shadow, element.shadowStep(shadow), element.shadowBleed()) { box, color ->
        when (shadow.kind) {
            ShadowKind.Drop -> drawOutline(shape.createOutline(box, layoutDirection, this), color)

            ShadowKind.Contact -> {
                val height: Float = shadow.contactHeight(element.frame.width).dp.toPx()
                drawOval(
                    color = color,
                    topLeft = Offset(0f, box.height - height / 2),
                    size = Size(box.width, height),
                )
            }

            ShadowKind.Curved -> {
                val depth: Float = shadow.curveDepth(element.frame.width).dp.toPx()
                val edge = Path().apply {
                    moveTo(0f, box.height / 2)
                    lineTo(0f, box.height)
                    // A quadratic's middle is halfway to its control point.
                    quadraticTo(box.width / 2, box.height + depth * 2, box.width, box.height)
                    lineTo(box.width, box.height / 2)
                    close()
                }
                drawPath(edge, color)
            }
        }
    }
}

/** The white a text box starts in, and the only colour a link is allowed to take over. */
internal const val DefaultTextColor: Long = 0xFFFFFFFF

/** The design's link swatch. */
internal const val LinkColor: Long = 0xFFA98FFF

/** How far one nesting level indents a list line, and how wide its marker sits. */
internal const val ListIndent: Float = 24f

/** Breathing room between a marker and its line, taken out of the marker's cell. */
private const val ListMarkerGap: Float = 6f

/**
 * The style the element's text draws in, its face resolved against [deckFonts]
 * ([LocalDeckFonts] at the call site; see [fontFamilyOf]).
 *
 * Internal rather than private: the editor's in-place text field styles itself
 * from this too, so the text under the caret is the same text that was there
 * before it, to the pixel.
 */
internal fun TextElement.textStyle(deckFonts: DeckFontFamilies): TextStyle {
    // Any target, not just the URL string: a box linked to a slide is as much a
    // link as one that opens a page.
    val linked: Boolean = resolvedLink() != null
    val decorations: List<TextDecoration> = buildList {
        if (underline || linked) add(TextDecoration.Underline)
        if (strikethrough) add(TextDecoration.LineThrough)
    }

    // A link recolours the box only where the box never said otherwise: a colour
    // that was chosen outlives the link, and the underline carries it instead.
    val drawn: Long = if (linked && color == DefaultTextColor) LinkColor else color

    return TextStyle(
        color = drawn.toComposeColor(),
        fontSize = fontSize.sp,
        fontWeight = FontWeight(fontWeight),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = fontFamilyOf(this, deckFonts),
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        lineHeight = (fontSize * lineHeight).sp,
        letterSpacing = letterSpacing.sp,
        textAlign = when (align) {
            TextAlign.Start -> androidx.compose.ui.text.style.TextAlign.Start
            TextAlign.Center -> androidx.compose.ui.text.style.TextAlign.Center
            TextAlign.End -> androidx.compose.ui.text.style.TextAlign.End
        },
    )
}

/** Where in its frame the text sits. Shared with the editor for the same reason. */
internal fun TextElement.alignment(): Alignment = BiasAlignment(align.bias(), -1f)

/** The alignment as a horizontal bias, -1 at the start edge and 1 at the end. */
internal fun TextAlign.bias(): Float = when (this) {
    TextAlign.Start -> -1f
    TextAlign.Center -> 0f
    TextAlign.End -> 1f
}

/**
 * The element's text, as much of it as [pieces] says is out.
 *
 * A delivery styles rather than shortens: what is still to come is drawn
 * transparent, so the box is laid out for the whole text from the first piece on
 * and nothing under a line moves as the rest of it arrives. The newest piece
 * fades in over the build's duration, which is the whole of what a piece landing
 * looks like.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.TextElementView(
    element: TextElement,
    pieces: PieceReveal? = null,
    alignment: Alignment = element.alignment(),
) {
    val style: TextStyle = element.textStyle(LocalDeckFonts.current)

    val ranges: List<IntRange> = remember(element.text, pieces?.build?.delivery) {
        pieces?.let { element.pieces(it.build.delivery) }.orEmpty()
    }

    // A fade per piece rather than per element: the count is what changes when a
    // click lands, so it is what the newest piece's alpha is keyed on.
    val shown: Int = pieces?.shown ?: 0
    val fade: Animatable<Float, AnimationVector1D> = remember(element.id) { Animatable(1f) }
    LaunchedEffect(element.id, shown) {
        if (ranges.isEmpty()) return@LaunchedEffect
        fade.snapTo(0f)
        fade.animateTo(1f, tween(pieces?.build?.durationMs ?: 0))
    }

    // The text as it is set, one run for plain text and one per line for a
    // list, and each run's share of the range links in its own offsets.
    val runs: List<TextRun> = remember(element.text, element.listStyle) {
        textRuns(element.text, element.listStyle)
    }
    val linkStyle: SpanStyle = element.linkSpanStyle()

    // Links first and the delivery over them, so words still to come stay
    // hidden however they are linked.
    fun set(body: String, at: Int, spans: List<LinkSpan>): AnnotatedString {
        val linked: AnnotatedString = linkedText(body, spans, linkStyle)
        return if (ranges.isEmpty()) linked
        else deliveredText(linked, at, ranges, shown, fade.value, style.color)
    }

    // Plain text is one Text, exactly as it always was: only a list pays for the
    // row-per-line layout its markers need.
    if (element.listStyle == ListStyle.None) {
        val run: TextRun = runs.single()
        val spans: List<LinkSpan> = run.spans(element.links)
        RunText(
            text = set(run.body, run.start, spans),
            spans = spans,
            style = style,
            modifier = Modifier.align(alignment),
        )
        return
    }

    val lines: List<String> = element.text.split("\n")
    val markers: List<String> = listMarkers(element.text, element.listStyle)

    Column(modifier = Modifier.align(alignment).fillMaxWidth()) {
        for ((index, line) in lines.withIndex()) {
            val indent: Float = line.listIndentLevel() * ListIndent
            // Where this line's own text begins, which is what both its marker
            // and its body are styled against: a marker arrives with its line.
            val run: TextRun = runs[index]
            val spans: List<LinkSpan> = run.spans(element.links)
            Row(modifier = Modifier.fillMaxWidth().padding(start = indent.dp)) {
                Text(
                    text =
                        if (ranges.isEmpty()) AnnotatedString(markers[index])
                        else deliveredText(markers[index], run.start, ranges, shown, fade.value, style.color),
                    style = style.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                    modifier = Modifier.width(ListIndent.dp).padding(end = ListMarkerGap.dp),
                )
                RunText(
                    text = set(run.body, run.start, spans),
                    spans = spans,
                    style = style,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One run of a text box, set, and clickable where it is linked.
 *
 * A press over a linked character is the link's: it is taken before anything
 * under it sees it, and its release fires the link through [LocalLinkHandler].
 * A press anywhere else is left alone, so it falls through to the box's whole
 * link when there is one and to the show's own click when there isn't. Nothing
 * provides a handler in the editor, and nothing reaches this there either: the
 * canvas's input overlay sits over every element it draws.
 *
 * The layout is kept for the pointer handler to read and for nothing else, so
 * nothing on screen is drawn from what a handler sees.
 */
@Composable
private fun RunText(
    text: AnnotatedString,
    spans: List<LinkSpan>,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val links: (LinkTarget) -> Unit = LocalLinkHandler.current
    val laidOut: LaidOutText = remember { LaidOutText() }

    Text(
        text = text,
        style = style,
        onTextLayout = { laidOut.result = it },
        modifier = modifier.then(
            if (spans.isEmpty()) Modifier
            else Modifier.pointerInput(spans, links) {
                awaitEachGesture {
                    val down: PointerInputChange = awaitFirstDown()
                    val span: LinkSpan = laidOut.result?.linkSpanAt(down.position, spans)
                        ?: return@awaitEachGesture
                    down.consume()
                    val up: PointerInputChange = waitForUpOrCancellation() ?: return@awaitEachGesture
                    up.consume()
                    links(span.target)
                }
            },
        ),
    )
}

/** Where a run's last layout is kept for its pointer handler. Plain, not state: nothing draws from it. */
private class LaidOutText {
    var result: TextLayoutResult? = null
}

/** The fill a shape paints: its gradient when it has one, its solid colour otherwise. */
private fun ShapeElement.brush(): Brush = gradient?.brush() ?: SolidColor(fill.toComposeColor())

/**
 * A shape: its shadow under it, then its outline filled and bordered and its
 * label in the middle.
 *
 * A picture fill paints over the solid rather than in place of it, clipped to the
 * outline: the solid is what shows around a fit, and the gradient or solid is
 * what shows in its place until the bytes land, or for good when they never do.
 */
@Composable
private fun ShapeElementView(element: ShapeElement) {
    val shape: Shape = element.shape()
    val imageFill: ImageFill? = element.imageFill
    val image: ImageBitmap? = rememberAssetImage(imageFill?.assetId)
    val brush: Brush =
        if (image != null) SolidColor(element.fill.toComposeColor()) else element.brush()

    val shadow: ShapeShadow? = element.shadow

    Box(Modifier.size(element.frame.width.dp, element.frame.height.dp)) {
        if (shadow != null) ShapeShadowLayer(element, shadow, shape)
        Box(
            modifier = Modifier
                .size(element.frame.width.dp, element.frame.height.dp)
                .background(brush, shape)
                .let { base ->
                    if (image == null || imageFill == null) return@let base
                    base.drawBehind {
                        val outline = Path().apply {
                            addOutline(shape.createOutline(size, layoutDirection, this@drawBehind))
                        }
                        clipPath(outline) { drawImageFill(image, imageFill.mode) }
                    }
                }
                .let { base ->
                    // Compose insets a border and draws none on an open path, while an
                    // SVG stroke is centred on the outline and stroke-only icons are
                    // common: a path strokes its own outline instead.
                    val path: ShapePath? = element.path.takeIf { element.kind == ShapeKind.Path }
                    val style: StrokeStyle = element.strokeStyle
                    if (path == null && style == StrokeStyle.Solid) return@let base.border(
                        element.strokeWidth.dp,
                        element.strokeColor.toComposeColor(),
                        shape,
                    )
                    if (element.strokeWidth <= 0f) return@let base
                    // A border can't dash, so a patterned one is drawn by hand, inset
                    // the way the border would have been.
                    if (path == null) return@let base.drawBehind {
                        drawStyledOutline(
                            outline = { inner ->
                                Path().apply {
                                    addOutline(shape.createOutline(inner, layoutDirection, this@drawBehind))
                                }
                            },
                            style = style,
                            color = element.strokeColor.toComposeColor(),
                            weight = element.strokeWidth.dp.toPx(),
                        )
                    }
                    base.drawBehind {
                        val weight: Float = element.strokeWidth.dp.toPx()
                        drawPath(
                            path = path.toComposePath(size),
                            color = element.strokeColor.toComposeColor(),
                            style = Stroke(
                                width = weight,
                                cap = style.cap,
                                pathEffect = style.pathEffect(weight),
                            ),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ShapeLabel(element)
        }
    }
}

/** How far an arrowhead reaches back from its tip, against the line's weight. */
private const val ArrowHeadScale: Float = 4f

/**
 * A line from its start to its end, drawn in the frame's own space: the points
 * come off the frame's origin, and the stroke and heads spill past a frame that
 * may be 1pt thin (see [shadowBleed], which makes room for them). A curved one is
 * its quadratic, and its heads sit on the curve's own tangents at the ends.
 *
 * The stroke stops short under each head ([LineEnd.inset]), measured along the
 * path, so a dash never pokes through a filled head and a hollow one stays
 * hollow. Its shadow is the same stroke and heads cast under it, offset, dimmed
 * and blurred.
 *
 * [entry] is read for [BuildEffect.LineDraw] alone: the stroke then grows over
 * the build's duration, per [Build.lineDraw]. A head at a growing tip rides it
 * when the build includes end points and otherwise lands once the line is whole;
 * a head at an end that never moves is there from the first frame.
 */
@Composable
private fun LineElementView(element: LineElement, entry: Build? = null) {
    // The one effect a line plays itself, the way a terminal plays Typewriter.
    val drawing: Build? = entry?.takeIf { it.effect == BuildEffect.LineDraw }
    val options: LineDraw = drawing?.lineDraw ?: LineDraw()
    val progress: Animatable<Float, AnimationVector1D> =
        remember(element.id) { Animatable(if (drawing == null) 1f else 0f) }

    LaunchedEffect(element.id) {
        if (drawing == null) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(drawing.durationMs, easing = LinearEasing),
        )
    }

    val shadow: ShapeShadow? = element.shadow

    Box(Modifier.size(element.frame.width.dp, element.frame.height.dp)) {
        // The shadow is the stroke and heads themselves, cast: a line has no
        // outline to fill, so whatever kind it carries falls as a drop shadow.
        if (shadow != null) {
            ShadowLayer(shadow, element.shadowStep(shadow), element.shadowBleed()) { _, color ->
                drawLineStroke(element, progress.value, options, color)
            }
        }
        Canvas(Modifier.matchParentSize()) {
            drawLineStroke(element, progress.value, options, element.strokeColor.toComposeColor())
        }
    }
}

/**
 * [element]'s stroke and heads in [color], [shown] of the way along under its
 * [options], drawn in the frame's own space. What [LineElementView] draws, once
 * for the line and once more for its shadow.
 */
private fun DrawScope.drawLineStroke(
    element: LineElement,
    shown: Float,
    options: LineDraw,
    color: Color,
) {
    val frame: Frame = element.frame
    val weight: Float = element.strokeWidth.dp.toPx()
    val head: Float = lineHeadSize(element.strokeWidth).dp.toPx()
    fun placed(point: Point): Offset =
        Offset((point.x - frame.x).dp.toPx(), (point.y - frame.y).dp.toPx())

    val start: Offset = placed(element.start)
    val end: Offset = placed(element.end)
    val control: Offset? = element.control?.let { placed(it) }
    val path: Path = Path().apply {
        moveTo(start.x, start.y)
        if (control == null) lineTo(end.x, end.y)
        else quadraticTo(control.x, control.y, end.x, end.y)
    }
    val measure: PathMeasure = PathMeasure().apply { setPath(path, forceClosed = false) }
    val length: Float = measure.length
    if (length == 0f) return

    // The stretch of the path showing, in distance along it, and which of its
    // two ends are still on the move.
    val whole: Boolean = shown >= 1f
    val (from: Float, to: Float) = when {
        whole -> 0f to length
        options.direction == LineDrawDirection.StartToEnd -> 0f to shown * length
        options.direction == LineDrawDirection.EndToStart -> (1 - shown) * length to length
        else -> (length - shown * length) / 2 to (length + shown * length) / 2
    }
    val startMoving: Boolean = !whole && options.direction != LineDrawDirection.StartToEnd
    val endMoving: Boolean = !whole && options.direction != LineDrawDirection.EndToStart
    val startHead: LineEnd =
        if (startMoving && !options.includeEnds) LineEnd.None else element.startEnd
    val endHead: LineEnd =
        if (endMoving && !options.includeEnds) LineEnd.None else element.endEnd

    // Heads longer than the line between them leave no stroke to draw.
    val strokeFrom: Float = from + startHead.inset(head, weight)
    val strokeTo: Float = to - endHead.inset(head, weight)
    if (strokeFrom < strokeTo) {
        val stroke = Path()
        measure.getSegment(strokeFrom, strokeTo, stroke, startWithMoveTo = true)
        drawPath(
            path = stroke,
            color = color,
            style = Stroke(
                width = weight,
                cap = element.strokeStyle.cap,
                pathEffect = element.strokeStyle.pathEffect(weight),
            ),
        )
    }

    // Each head on the path's own tangent where it sits, so a curve's heads
    // lean the way the curve leaves its ends.
    val startTip: Offset = measure.getPosition(from)
    val endTip: Offset = measure.getPosition(to)
    val startFrom: Offset = startTip + measure.getTangent(from)
    val endFrom: Offset = endTip - measure.getTangent(to)
    drawLineEnd(startHead, color, startTip, startFrom, weight, head)
    drawLineEnd(endHead, color, endTip, endFrom, weight, head)
}

/**
 * A filled triangle pointing at [tip], away from [from].
 *
 * Internal rather than private: a diagram's edges cap themselves with the same
 * head a line does, and two arrowheads that differ by a few degrees on one slide
 * would be a bug nobody could name.
 */
internal fun DrawScope.drawArrowHead(color: Color, tip: Offset, from: Offset, weight: Float) {
    val length: Float = hypot(tip.x - from.x, tip.y - from.y)
    if (length == 0f) return

    val reach: Float = ArrowHeadScale * weight
    val dx: Float = (tip.x - from.x) / length
    val dy: Float = (tip.y - from.y) / length
    val baseX: Float = tip.x - dx * reach
    val baseY: Float = tip.y - dy * reach

    // The base's two ends, one half-reach either side of the line's own direction.
    val head: Path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX - dy * reach / 2, baseY + dx * reach / 2)
        lineTo(baseX + dy * reach / 2, baseY - dx * reach / 2)
        close()
    }
    drawPath(head, color)
}

@Composable
private fun ShapeLabel(element: ShapeElement) {
    if (element.label.isEmpty()) return

    Text(
        text = element.label,
        color = element.labelColor.toComposeColor(),
        fontSize = element.labelSize.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** The room a caption takes under an image, and the size it is set at. */
private const val CaptionHeight: Float = 24f
private const val CaptionSize: Float = 16f

/**
 * An image: its masked window drawn into its box, adjusted on the way, with its
 * caption under it.
 *
 * With no bytes behind it, and while those bytes are being read, it draws the
 * design's drop placeholder instead. A caption is drawn either way, and the
 * picture gives up the room it takes rather than sitting under it: the element's
 * frame is the whole of what it occupies on the slide, caption included.
 */
@Composable
private fun ImageElementView(element: ImageElement) {
    val image: ImageBitmap? = rememberAssetImage(element.assetId)
    val reserved: Float = if (element.caption.isEmpty()) 0f else CaptionHeight
    val height: Float = (element.frame.height - reserved).coerceAtLeast(0f)

    Box(modifier = Modifier.size(element.frame.width.dp, element.frame.height.dp)) {
        if (image == null) ImagePlaceholder(element.placeholder, element.frame.width, height)
        else AssetImageContent(
            image = image,
            adjust = element.adjust,
            mask = element.mask,
            naturalWidth = element.naturalWidth,
            naturalHeight = element.naturalHeight,
            width = element.frame.width,
            height = height,
        )

        if (element.caption.isNotEmpty()) {
            Caption(element.caption, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** An image's caption, wherever it is drawn: under a single picture or under a gallery's. */
@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = LocalSlideInk.current.copy(alpha = 0.7f),
        fontSize = CaptionSize.sp,
        fontFamily = FontFamily.SansSerif,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Decoded bytes drawn into a [width] x [height] box: the masked window of them,
 * stretched to fill it, corrected on the way and clipped to the mask's outline.
 *
 * The whole of what drawing a picture is, kept apart from the element that owns
 * it: an image and a gallery draw the same pixels the same way, and only differ
 * in where the bytes and the mask come from.
 */
@Composable
private fun AssetImageContent(
    image: ImageBitmap,
    adjust: ImageAdjust,
    mask: ImageMask?,
    naturalWidth: Int,
    naturalHeight: Int,
    width: Float,
    height: Float,
) {
    val kind: ShapeKind = mask?.kind ?: ShapeKind.Rectangle

    val filter: ColorFilter = remember(adjust) {
        ColorFilter.colorMatrix(ColorMatrix(adjust.colorMatrix()))
    }

    // The window in the bitmap's own pixels. Measured against the bitmap rather
    // than the document when the document has no natural size to offer: an
    // element written before its bytes were decoded still draws all of them.
    val source: Frame = remember(mask, naturalWidth, naturalHeight, image) {
        if (naturalWidth > 0 && naturalHeight > 0) {
            imageSourceRect(mask, naturalWidth, naturalHeight)
        } else imageSourceRect(mask, image.width, image.height)
    }

    Canvas(
        modifier = Modifier
            .size(width.dp, height.dp)
            .let { if (kind == ShapeKind.Rectangle) it else it.clip(kind.shape()) },
    ) {
        val left: Int = source.x.roundToInt().coerceIn(0, (image.width - 1).coerceAtLeast(0))
        val top: Int = source.y.roundToInt().coerceIn(0, (image.height - 1).coerceAtLeast(0))

        drawImage(
            image = image,
            srcOffset = IntOffset(left, top),
            srcSize = IntSize(
                width = source.width.roundToInt().coerceIn(1, image.width - left),
                height = source.height.roundToInt().coerceIn(1, image.height - top),
            ),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            colorFilter = filter,
        )
    }
}

@Composable
private fun ImagePlaceholder(text: String, width: Float, height: Float) {
    val ink: Color = LocalSlideInk.current

    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .drawBehind {
                drawRoundRect(
                    color = ink.copy(alpha = 0.3f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    ),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = ink.copy(alpha = 0.45f),
            fontSize = 13.sp,
        )
    }
}

/** The dots' size and spacing, and how far off the picture's bottom edge the row sits. */
private const val GalleryDotSize: Float = 7f
private const val GalleryDotGap: Float = 7f
private const val GalleryDotInset: Float = 10f

/**
 * A gallery: the picture at [imageIndex], its caption under it, and the dots that
 * say where in the carousel the audience is.
 *
 * A step change crossfades: the picture being left and the one arriving are both
 * composed for the length of it, one fading out under the other, and only the one
 * that has arrived is left composed once it settles. Both layers are laid out in
 * the same box, so a taller picture doesn't move the caption under it.
 *
 * [indicators] is off in the editor, where there is no walk to be part way
 * through, and a gallery of one picture draws none either way: a single dot says
 * nothing a picture doesn't.
 */
@Composable
private fun GalleryElementView(element: GalleryElement, imageIndex: Int, indicators: Boolean) {
    val index: Int =
        if (element.images.isEmpty()) 0 else imageIndex.coerceIn(element.images.indices)

    var from: Int by remember { mutableStateOf(index) }
    var to: Int by remember { mutableStateOf(index) }
    val progress: Animatable<Float, AnimationVector1D> = remember { Animatable(1f) }

    // The first composition has nothing to come from, so it draws its picture at
    // rest. Both ends are held here rather than read from the parameter, so the
    // frame between a step landing and this effect still draws what was there.
    LaunchedEffect(index) {
        if (index == to) return@LaunchedEffect
        from = to
        to = index
        progress.snapTo(0f)
        progress.animateTo(1f, tween(GalleryStepDuration))
        from = to
    }

    // Reserved for every picture or for none, so the box doesn't jump when a
    // picture with a caption dissolves into one without.
    val captioned: Boolean = element.showCaptions && element.images.any { it.caption.isNotEmpty() }
    val height: Float =
        (element.frame.height - if (captioned) CaptionHeight else 0f).coerceAtLeast(0f)

    Box(modifier = Modifier.size(element.frame.width.dp, element.frame.height.dp)) {
        if (from != to) {
            GalleryImageLayer(element, from, element.frame.width, height, 1f - progress.value)
        }
        GalleryImageLayer(element, to, element.frame.width, height, progress.value)

        // The dots sit on the bottom of the picture, in the box above the caption's band,
        // so they never reach the caption.
        if (indicators && element.images.size > 1) {
            Box(modifier = Modifier.size(element.frame.width.dp, height.dp)) {
                GalleryDots(
                    count = element.images.size,
                    current = to,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = GalleryDotInset.dp),
                )
            }
        }
    }
}

/** One picture of a gallery and its caption, drawn at [alpha]: half of a crossfade. */
@Composable
private fun GalleryImageLayer(
    element: GalleryElement,
    index: Int,
    width: Float,
    height: Float,
    alpha: Float,
) {
    val image: GalleryImage? = element.images.getOrNull(index)
    val bitmap: ImageBitmap? = rememberAssetImage(image?.assetId)

    Box(
        modifier = Modifier
            .size(width.dp, element.frame.height.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        if (image == null || bitmap == null) ImagePlaceholder(GalleryPlaceholder, width, height)
        else AssetImageContent(
            image = bitmap,
            adjust = element.adjust,
            mask = null,
            naturalWidth = image.naturalWidth,
            naturalHeight = image.naturalHeight,
            width = width,
            height = height,
        )

        if (element.showCaptions && image != null && image.caption.isNotEmpty()) {
            Caption(image.caption, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** What an empty gallery, and a gallery whose bytes have gone missing, prompts with. */
private const val GalleryPlaceholder: String = "Drop images here"

/** One dot per picture, the one showing filled and the rest of them dimmed. */
@Composable
private fun GalleryDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val width: Float = count * GalleryDotSize + (count - 1) * GalleryDotGap

    Canvas(modifier = modifier.size(width.dp, GalleryDotSize.dp)) {
        val radius: Float = GalleryDotSize.dp.toPx() / 2
        val stride: Float = (GalleryDotSize + GalleryDotGap).dp.toPx()
        for (dot in 0 until count) {
            drawCircle(
                color = Color.White.copy(alpha = if (dot == current) 0.9f else 0.35f),
                radius = radius,
                center = Offset(radius + dot * stride, radius),
            )
        }
    }
}

/**
 * The near-black a movie box and an audio pill are drawn on: darker than the
 * slide, so a media element reads as a hole in it rather than as a panel on it.
 */
private const val MediaSurface: Long = 0xFF0E0F13

/** The play badge's diameter, and how far a movie's title sits off the bottom edge. */
private const val PlayGlyphSize: Float = 64f
private const val MediaTitleInset: Float = 10f

/** The pill's speaker, and the room between it and the title beside it. */
private const val SpeakerGlyphSize: Float = 22f
private const val AudioPillPadding: Float = 16f
private const val AudioGlyphGap: Float = 12f
private const val AudioTitleSize: Float = 16f
private const val AudioTrackHeight: Float = 3f

/**
 * A movie at rest: its poster frame, the badge that says it plays, and what it
 * is called.
 *
 * Decoding a movie is a platform player's job, so [playing] mode hands the
 * element to [LocalMediaPlayer] and asks it every frame for the picture, which
 * is drawn here in the poster's place. Here, so it is drawn inside everything the
 * slide does to this box: the element's rotation and opacity, a build, a slide
 * transition. The editor's canvas never plays, and draws the poster alone.
 *
 * A tap plays, except on a movie that is only a [VideoElement.webUrl]: there is
 * nothing to play, so it opens the page through [LocalLinkHandler] instead, the
 * way any other link on a slide is opened.
 */
@Composable
private fun VideoElementView(element: VideoElement, playing: Boolean) {
    val poster: ImageBitmap? = rememberAssetImage(element.posterAssetId)
    val player: MediaPlayerHost = LocalMediaPlayer.current
    val links: (LinkTarget) -> Unit = LocalLinkHandler.current
    val caption: String = element.title.ifBlank { element.webUrl.orEmpty() }

    var picture: MediaPicture? by remember(element.id, player) { mutableStateOf(null) }

    // Leaving the slide is the composition going away: an element a build takes
    // back out, a step that cuts to the next slide and the show ending all read
    // the same way here, which is what keeps a movie from playing on over them.
    if (playing) {
        DisposableEffect(element.id, player) { onDispose { player.stop(element.id) } }

        LaunchedEffect(element.id, player, element.autoplay) {
            if (element.autoplay) player.play(element.mediaRequest())
        }

        // Every frame, inside the frame itself: a picture that lands here is
        // drawn on this frame rather than the next. Asked for whether or not
        // anything is playing yet, since a tap starts a movie from outside.
        // Never of [NoMediaPlayer]: it has none to give, and asking every frame
        // would keep the window drawing for nothing.
        LaunchedEffect(element.id, player) {
            if (player === NoMediaPlayer) return@LaunchedEffect
            while (true) withFrameNanos { picture = player.picture(element.id) }
        }
    }

    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .then(
                if (!playing) Modifier
                else Modifier.pointerInput(element, player, links) {
                    detectTapGestures {
                        val web: String? = element.webUrl
                        if (element.assetId == null && web != null) links(LinkTarget.Url(web))
                        else player.play(element.mediaRequest())
                    }
                },
            ),
    ) {
        val showing: MediaPicture? = picture
        if (showing != null) {
            MoviePicture(showing, element.frame.width, element.frame.height)
        } else {
            if (poster == null) {
                Box(
                    modifier = Modifier
                        .size(element.frame.width.dp, element.frame.height.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MediaSurface.toComposeColor()),
                )
            } else {
                AssetImageContent(
                    image = poster,
                    adjust = ImageAdjust(),
                    mask = null,
                    // Not recorded on the element: a poster is a still of the movie
                    // rather than a picture in its own right, so it is measured off
                    // the bitmap the way an image written before decode is.
                    naturalWidth = 0,
                    naturalHeight = 0,
                    width = element.frame.width,
                    height = element.frame.height,
                )
            }

            PlayGlyph(Modifier.align(Alignment.Center))

            if (caption.isNotEmpty()) {
                Caption(
                    text = caption,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = MediaTitleInset.dp),
                )
            }
        }
    }
}

/**
 * A playing movie's [picture], fitted inside its [width] by [height] box and
 * centred, on the dark a movie box is drawn on: letterboxed the way a player
 * shows a movie whose shape is not its box's, with no badge or title over it.
 */
@Composable
private fun MoviePicture(picture: MediaPicture, width: Float, height: Float) {
    Canvas(
        modifier = Modifier
            .size(width.dp, height.dp)
            .background(MediaSurface.toComposeColor()),
    ) {
        // Read in the draw, so a new frame redraws this and nothing else.
        picture.frame
        val image: ImageBitmap = picture.image
        if (image.width <= 0 || image.height <= 0) return@Canvas

        val scale: Float = minOf(size.width / image.width, size.height / image.height)
        val drawn = IntSize(
            width = (image.width * scale).roundToInt(),
            height = (image.height * scale).roundToInt(),
        )
        drawImage(
            image = image,
            dstOffset = IntOffset(
                ((size.width - drawn.width) / 2).roundToInt(),
                ((size.height - drawn.height) / 2).roundToInt(),
            ),
            dstSize = drawn,
        )
    }
}

/** The badge over a movie: a translucent disc with a triangle in it. */
@Composable
private fun PlayGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(PlayGlyphSize.dp)) {
        val radius: Float = size.minDimension / 2
        drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = radius)
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = radius - 1.dp.toPx(),
            style = Stroke(width = 2.dp.toPx()),
        )

        // Nudged right of centre by an eighth of the radius: a triangle balances
        // on its area rather than on its box, and centred by its box it reads as
        // leaning backwards.
        val half: Float = radius * 0.42f
        val nose: Float = center.x + radius * 0.48f
        drawPath(
            path = Path().apply {
                moveTo(center.x - radius * 0.24f, center.y - half)
                lineTo(center.x - radius * 0.24f, center.y + half)
                lineTo(nose, center.y)
                close()
            },
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}

/**
 * A sound: the pill that says one is on the slide, its name, and a track under it.
 *
 * The track is drawn and never moves. What a sound is doing is the host player's,
 * and nothing the host knows comes back through the document, so a bar that
 * looked live would be lying; what it is here for is to say the pill is a
 * transport rather than a label. See [MediaPlayerHost].
 */
@Composable
private fun AudioElementView(element: AudioElement, playing: Boolean) {
    val player: MediaPlayerHost = LocalMediaPlayer.current

    if (playing) {
        DisposableEffect(element.id, player) { onDispose { player.stop(element.id) } }

        LaunchedEffect(element.id, player, element.autoplay) {
            if (element.autoplay) player.play(element.mediaRequest())
        }
    }

    Row(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MediaSurface.toComposeColor())
            .then(
                if (!playing) Modifier
                else Modifier.pointerInput(element, player) {
                    detectTapGestures { player.play(element.mediaRequest()) }
                },
            )
            .padding(horizontal = AudioPillPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeakerGlyph()

        Column(modifier = Modifier.weight(1f).padding(start = AudioGlyphGap.dp)) {
            Text(
                text = element.title.ifBlank { "Audio" },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = AudioTitleSize.sp,
                fontFamily = FontFamily.SansSerif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(AudioTrackHeight.dp)) {
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** The pill's speaker: a cone and two arcs, drawn rather than set in a font. */
@Composable
private fun SpeakerGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(SpeakerGlyphSize.dp)) {
        val ink: Color = Color.White.copy(alpha = 0.8f)
        val unit: Float = size.minDimension / 22f

        drawPath(
            path = Path().apply {
                moveTo(3 * unit, 8 * unit)
                lineTo(7 * unit, 8 * unit)
                lineTo(12 * unit, 3 * unit)
                lineTo(12 * unit, 19 * unit)
                lineTo(7 * unit, 14 * unit)
                lineTo(3 * unit, 14 * unit)
                close()
            },
            color = ink,
        )

        for (arc in 1..2) {
            drawCircle(
                color = ink.copy(alpha = 0.8f / arc),
                radius = (2 + arc * 2.5f) * unit,
                center = Offset(13 * unit, 11 * unit),
                style = Stroke(width = 1.5f * unit),
            )
        }
    }
}

/**
 * Code is set looser than prose, and the gutter has to be set to the same.
 *
 * These three are internal rather than private for the same reason
 * [TextElement.textStyle] is: the editor's in-place code field builds its own
 * chrome from them, so the block under the caret is the block that was there
 * before it, to the pixel.
 */
internal const val CodeLineHeight: Float = 1.5f

/** Between the last digit and the first character of its line. */
internal val CodeGutterGap = 10.dp

internal val CodeCorner = RoundedCornerShape(8.dp)

/** The block's inset, shared with the field so the first character sits still. */
internal val CodePadding = 12.dp

/** The block code sits on: its theme's fill, its outline if it keeps one, and its inset. */
internal fun Modifier.codeChrome(element: CodeElement): Modifier {
    val chrome: CodeChrome = element.theme.chrome
    val outline: Modifier =
        if (element.showBorder) Modifier.border(1.dp, chrome.border, CodeCorner)
        else Modifier

    return background(chrome.background, CodeCorner)
        .then(outline)
        .clip(CodeCorner)
        .padding(CodePadding)
}

/**
 * A code block: its theme's chrome, its numbers, and its highlighted text.
 *
 * [step] is the state to draw, or null for the whole block, which is the editor's
 * case until a step row is picked. A step that hides lines reflows the ones that
 * are left, and the gutter keeps drawing each one's original number, so a reveal
 * reads as a block filling in rather than as a block being renumbered. One with
 * more code than the block holds scrolls it ([codeScroll]), under the padding and
 * out through the clip [codeChrome] already makes at the block's edge.
 *
 * The whole block is two [Text]s, the gutter and the code: one line per line, so
 * they line up, and the numbers stay out of anything the code is selected or
 * exported into. It costs the gutter drifting from its lines when
 * [CodeElement.wrap] is on and a line reflows, which is a fair trade for a block
 * nobody is stepping through. A step draws through [AnimatedCodeLines] instead,
 * which lays each line out as its own row: that is what it takes to move a line
 * from one row to another, and it keeps the column honest under wrap for free.
 *
 * [landing] is the build that put the block on [step], passed through for
 * [AnimatedCodeLines] to type its way onto a [BuildEffect.Typewriter] step.
 */
@Composable
private fun CodeElementView(
    element: CodeElement,
    step: CodeStep? = null,
    landing: BuildAt? = null,
) {
    val chrome: CodeChrome = element.theme.chrome
    Box(
        modifier = Modifier
            .size(element.frame.width.dp, element.frame.height.dp)
            .codeChrome(element),
    ) {
        if (step != null) {
            AnimatedCodeLines(element, step, landing)
        } else {
            val stepped: SteppedCode = remember(element.code, element.language, element.theme) {
                steppedCode(element.code, element.language, element.theme, step = null)
            }

            Row {
                if (element.showLineNumbers) {
                    Text(
                        text = stepped.numbers,
                        color = chrome.gutter,
                        fontSize = element.fontSize.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = (element.fontSize * CodeLineHeight).sp,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.padding(end = CodeGutterGap),
                    )
                }

                Text(
                    text = stepped.text,
                    color = chrome.text,
                    fontSize = element.fontSize.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = (element.fontSize * CodeLineHeight).sp,
                    // Off, a long line runs to the edge of the block and is cut there
                    // by the clip above rather than reflowing under itself.
                    softWrap = element.wrap,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}
