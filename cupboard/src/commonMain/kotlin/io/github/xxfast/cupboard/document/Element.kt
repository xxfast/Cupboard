package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Serializable
data class Frame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    fun translate(dx: Float, dy: Float): Frame = copy(x = x + dx, y = y + dy)

    /**
     * Whether the two frames share any area. Touching edges don't, so a marquee
     * dragged along an element's edge doesn't sweep it up. Both frames are
     * expected to have positive width and height.
     */
    fun overlaps(other: Frame): Boolean =
        x < other.x + other.width && other.x < x + width &&
            y < other.y + other.height && other.y < y + height
}

/** A position on the slide, in document units. */
@Serializable
data class Point(val x: Float, val y: Float)

/** The smallest frame containing every one of [frames]. Nothing in, nothing out. */
fun boundingFrame(frames: List<Frame>): Frame {
    if (frames.isEmpty()) return Frame(0f, 0f, 0f, 0f)
    val left: Float = frames.minOf { it.x }
    val top: Float = frames.minOf { it.y }
    val right: Float = frames.maxOf { it.x + it.width }
    val bottom: Float = frames.maxOf { it.y + it.height }
    return Frame(left, top, right - left, bottom - top)
}

/**
 * The axis-aligned box the element actually occupies on the slide: its frame,
 * widened by its rotation about the frame center. Grouping and marquee sweeps
 * measure against this, so a rotated element is boxed where it is drawn rather
 * than where it would sit unrotated. Flips leave the footprint alone.
 */
fun Element.drawnBounds(): Frame {
    if (rotation == 0f) return frame
    val radians: Float = rotation * PI.toFloat() / 180f
    val cos: Float = abs(cos(radians))
    val sin: Float = abs(sin(radians))
    val width: Float = frame.width * cos + frame.height * sin
    val height: Float = frame.width * sin + frame.height * cos
    return Frame(frame.centerX - width / 2, frame.centerY - height / 2, width, height)
}

/**
 * The vector ([dx], [dy]) rotated by [degrees], clockwise-positive like the canvas.
 *
 * Here rather than with the editor's geometry because the document needs it too:
 * baking a group's rotation into its children happens without a canvas in sight.
 */
internal fun rotateVector(dx: Float, dy: Float, degrees: Float): Pair<Float, Float> {
    val radians: Float = degrees * PI.toFloat() / 180f
    val cos: Float = cos(radians)
    val sin: Float = sin(radians)
    return (dx * cos - dy * sin) to (dx * sin + dy * cos)
}

@Serializable
sealed interface Element {
    val id: String
    val frame: Frame
    val opacity: Float
    /** Degrees clockwise, applied around the frame's center. */
    val rotation: Float
    val flippedHorizontally: Boolean
    val flippedVertically: Boolean
    /**
     * A locked element still selects and still renders, but nothing moves or
     * restyles it until it unlocks. Locking, hiding and renaming still apply.
     */
    val locked: Boolean
    /**
     * The user's name for the row in the Object List; null means the default,
     * derived from the content (see `displayName`).
     */
    val name: String?
    /**
     * A hidden element stays in the document and in the Object List but does
     * not render, hit-test, play or export.
     */
    val hidden: Boolean

    /**
     * The one mutator every element implements: copies with whichever of the
     * shared properties were passed and keeps the element's own type and fields.
     * Defaulted against `this`, so a caller names only what it is changing.
     */
    fun update(
        frame: Frame = this.frame,
        opacity: Float = this.opacity,
        rotation: Float = this.rotation,
        flippedHorizontally: Boolean = this.flippedHorizontally,
        flippedVertically: Boolean = this.flippedVertically,
        locked: Boolean = this.locked,
        name: String? = this.name,
        hidden: Boolean = this.hidden,
    ): Element
}

/**
 * What a placeholder stands for, and so what an instance of it is: with its
 * place among the others of its role, what apply and reapply map a slide's
 * elements to a layout's by (see [PlaceholderSlot]).
 *
 * Only the kinds that can hold slide content carry one ([TextElement],
 * [ImageElement], [CodeElement]): a shape or a diagram on a layout is decoration,
 * which every slide inherits as it is rather than filling in.
 */
@Serializable
enum class PlaceholderRole { Title, Body, Media, Code }

/**
 * The role this element answers for, null for anything that can't hold one.
 *
 * On a layout a role makes the element a placeholder; on a slide it marks the
 * instance that placeholder produced. Named apart from the fields it reads so an
 * `Element` and a `TextElement` are never two different `role`s at one call site.
 */
val Element.placeholderRole: PlaceholderRole?
    get() = when (this) {
        is TextElement -> role
        is ImageElement -> role
        is CodeElement -> role
        else -> null
    }

enum class TextAlign { Start, Center, End }

/**
 * The face a text box is set in: three generic families, and the handful of
 * named ones Keynote's Basic themes are set in.
 *
 * Named families are a closed list rather than a free string, and each carries
 * the generic one it stands in for: the canvas draws on every target from the
 * same document, and a face missing on one of them (Linux, the web, Android, a
 * Mac that never downloaded Canela) has to fall back to something that still
 * reads as the same kind of type. [familyName] is what the system knows the face
 * as; [generic] is what draws when it doesn't.
 *
 * Any other face is [TextElement.fontName], a free string beside this rather
 * than instead of it: the name is what the box asks for, and this stays what it
 * falls back to, so a deck set in a face only one machine has still reads as
 * the right kind of type everywhere else.
 *
 * Weight is not part of the face: Light, Regular, Medium and Bold are
 * [TextElement.fontWeight] at 300, 400, 500 and 700.
 */
enum class TextFont {
    Sans,
    Serif,
    Monospace,
    HelveticaNeue,
    Canela,
    CanelaText,
    CanelaDeck,
    Graphik,
    JetBrainsMono,
    ;

    /** The system's name for this face; null for the three generic families. */
    val familyName: String?
        get() = when (this) {
            Sans, Serif, Monospace -> null
            HelveticaNeue -> "Helvetica Neue"
            Canela -> "Canela"
            CanelaText -> "Canela Text"
            CanelaDeck -> "Canela Deck"
            Graphik -> "Graphik"
            JetBrainsMono -> "JetBrains Mono"
        }

    /** What a picker calls this face: the system's name, or the generic family's own. */
    val displayName: String
        get() = familyName ?: name

    /** The generic family this one falls back to where [familyName] isn't installed. */
    val generic: TextFont
        get() = when (this) {
            Sans, Serif, Monospace -> this
            HelveticaNeue, Graphik -> Sans
            Canela, CanelaText, CanelaDeck -> Serif
            JetBrainsMono -> Monospace
        }
}

/**
 * A list is a property of the whole box, not of a range: every line of
 * [TextElement.text] is one item, and its nesting is the count of leading tabs.
 * That keeps the text one plain string, so typing, undo and the file format all
 * stay as they were.
 */
enum class ListStyle { None, Bullet, Numbered }

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val text: String = "",
    val fontSize: Float = 15f,
    /** Bold is a point on this scale rather than a flag of its own; see `isBold`. */
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.3f,
    val letterSpacing: Float = 0f,
    val color: Long = 0xFFFFFFFF,
    val align: TextAlign = TextAlign.Start,
    val fontFamily: TextFont = TextFont.Sans,
    /**
     * A family by the name the system or the deck knows it as (see
     * `Document.fonts`), for every face [TextFont] has no entry for. Set, it wins
     * over [fontFamily], which stays what draws where the name resolves nowhere,
     * as its [TextFont.generic]. Null is every box set in [fontFamily] alone,
     * which is every deck written before this existed.
     *
     * See `withFont` and `withGenericFont` for how a pick sets the two.
     */
    val fontName: String? = null,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val listStyle: ListStyle = ListStyle.None,
    /**
     * The whole box as one hyperlink, as a bare URL. A run of words inside the
     * text links through [links] instead; this is the box as a whole, which is
     * what every link was before ranges existed.
     *
     * Kept alongside [linkTarget] rather than folded into it because every deck
     * written before targets existed carries this one. [resolvedLink] reads both.
     */
    val link: String? = null,
    /**
     * Where the box points, for the five destinations a URL cannot name. Wins
     * over [link] when both are set; the editor keeps the two in step, writing
     * the string only for a [LinkTarget.Url].
     */
    val linkTarget: LinkTarget? = null,
    /**
     * The runs of [text] that point somewhere of their own, in order and never
     * overlapping (see [TextLink] and `normalisedLinks`). Inside one, its target
     * wins over the whole-box [resolvedLink]; everywhere else the box answers as
     * it always has.
     *
     * Anchored to [text], so whatever rewrites the text carries these along with
     * it through `withText`.
     */
    val links: List<TextLink> = emptyList(),
    /** See [PlaceholderRole]: set, this is a placeholder on a layout or an instance on a slide. */
    val role: PlaceholderRole? = null,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * The dozen shapes the catalog offers, drawn inside the element's frame.
 *
 * [Rectangle] and [Ellipse] keep their names because documents on disk already
 * carry them. "Rounded rectangle" is no kind of its own: it is a [Rectangle]
 * with a corner radius, which is also why [ShapeElement.cornerRadius] means
 * nothing to any other kind.
 *
 * [Triangle] points up, [Arrow] points right, [Star] has five points and
 * [Polygon] is a regular hexagon. [QuoteBubble] and [Callout] are rounded boxes
 * with a tail, bottom-left and bottom-center. A line is no shape at all: see
 * [LineElement].
 *
 * [Path] is the one kind the catalog doesn't offer, because it has no outline of
 * its own: it draws whatever [ShapeElement.path] carries, which is what an
 * imported SVG and the pieces of a broken-apart shape are. With no path to draw
 * by it reads as the rectangle it is boxed by.
 */
enum class ShapeKind {
    Rectangle,
    Ellipse,
    Triangle,
    Arrow,
    Diamond,
    Star,
    Polygon,
    QuoteBubble,
    Callout,
    Path,
}

/**
 * How a stroke is drawn along its length, Keynote's first five in its order:
 * solid, then the four dot and dash patterns. Every pattern is measured in
 * stroke widths, so a heavier line has longer dashes. Keynote's five brush
 * strokes are not here.
 */
enum class StrokeStyle { Solid, SquareDots, ShortDash, LongDash, RoundDots }

/**
 * What caps one end of a [LineElement], in Keynote's End points order. The tip
 * of every head sits on the endpoint, and every head grows with the stroke.
 * [ReversedArrow] points back along the line; [Bar] is a tick across it.
 */
enum class LineEnd {
    None,
    Arrow,
    Circle,
    Diamond,
    OpenArrow,
    SweptArrow,
    Square,
    OpenSquare,
    OpenCircle,
    ReversedArrow,
    Bar,
}

/** How a [ShapeGradient] runs: along a line, or out from a centre. */
enum class GradientKind { Linear, Radial }

/**
 * A two-stop fill, [start] to [end].
 *
 * [GradientKind.Linear] runs along a line at [angle], in CSS degrees like
 * [SlideBackground.Gradient]: 0 points up and the angle turns clockwise.
 * [GradientKind.Radial] runs from [start] at the centre out to [end] at the
 * edge of a circle: [centerX] and [centerY] are fractions of the frame from its
 * top left, and [radius] a fraction of its longer side, so the defaults put the
 * centre in the middle and reach the edges the long way. Each kind ignores the
 * other's numbers and keeps them, so switching back finds them where they were.
 *
 * Set on a shape it paints instead of [ShapeElement.fill] rather than over it,
 * so switching back to a solid is a matter of dropping this rather than of
 * remembering what the solid used to be.
 */
@Serializable
data class ShapeGradient(
    val start: Long,
    val end: Long,
    val angle: Float = 140f,
    val kind: GradientKind = GradientKind.Linear,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radius: Float = 0.5f,
)

/**
 * Keynote's three shadows. "No Shadow" is no kind of its own: it is a null
 * [ShapeShadow], the one way the model spells it.
 *
 * [Drop] is the outline itself, cast along an angle. [Contact] is a flattened
 * ellipse under the base, as if the shape stood on a surface seen at
 * [ShapeShadow.perspective] degrees. [Curved] is the bottom edge bowed by
 * [ShapeShadow.curve], the shadow a sheet casts when its middle lifts or sags.
 */
enum class ShadowKind { Drop, Contact, Curved }

/**
 * The shadow under a shape or a line, Keynote's: a [kind] and the dials that
 * kind reads. Every distance is in document units.
 *
 * [angle] is Keynote's dial, in degrees: 0 points right, 90 up and 270 straight
 * down, which is where the shadow falls rather than where the light is. It
 * stays on the slide's dial whichever way the shape turns, the way a light that
 * doesn't move with the shape would.
 *
 * [opacity] is kept apart from [color] because Keynote edits it apart: the
 * editor leaves the colour's own alpha at FF and dims through this. A colour with
 * alpha of its own still draws, both multiplied.
 *
 * Every kind carries every number, so switching back and forth is the editor's
 * choice of defaults (see [ShadowKind.shadow]) rather than the model's. [Drop]
 * reads [blur], [offset], [angle] and [opacity]. [Contact] reads [blur], [offset]
 * (straight down), [opacity] and [perspective]. [Curved] reads [curve], from -1
 * inwards to 1 outwards, [blur], [offset], [angle] and [opacity].
 *
 * Format version 2 and earlier stored `dx` and `dy` and folded the opacity into
 * the colour; reading one rewrites it (see [migrateLegacyShadows]).
 */
@Serializable
data class ShapeShadow(
    val kind: ShadowKind = ShadowKind.Drop,
    val color: Long = 0xFF000000,
    val blur: Float = 5f,
    val offset: Float = 2f,
    val angle: Float = 270f,
    val opacity: Float = 0.5f,
    val perspective: Float = 10f,
    val curve: Float = 0f,
)

/**
 * A fresh shadow of this kind in [color], on Keynote's defaults for it: what
 * picking the kind off the popup lands on.
 *
 * Keynote's curved shadow has no blur control of its own, so it takes a soft
 * one that is not on any dial.
 */
fun ShadowKind.shadow(color: Long = 0xFF000000): ShapeShadow = when (this) {
    ShadowKind.Drop -> ShapeShadow(color = color)

    ShadowKind.Contact -> ShapeShadow(
        kind = this,
        color = color,
        blur = 28f,
        offset = 0f,
        opacity = 0.75f,
        perspective = 10f,
    )

    ShadowKind.Curved -> ShapeShadow(
        kind = this,
        color = color,
        blur = 4f,
        offset = 1f,
        angle = 270f,
        opacity = 1f,
    )
}

/**
 * This shadow as [kind], what the kind popup does in either shell: null drops
 * it, the kind it already is keeps every number, and any other kind lands on
 * that kind's defaults in the colour it had.
 */
fun ShapeShadow?.withKind(kind: ShadowKind?): ShapeShadow? = when {
    kind == null -> null
    this?.kind == kind -> this
    else -> kind.shadow(this?.color ?: 0xFF000000)
}

/** [ShapeShadow.offset] along [ShapeShadow.angle], across the slide: positive is right. */
val ShapeShadow.dx: Float get() = offset * cos(angle * PI.toFloat() / 180f)

/** [ShapeShadow.offset] along [ShapeShadow.angle], down the slide: positive is down. */
val ShapeShadow.dy: Float get() = -offset * sin(angle * PI.toFloat() / 180f)

/**
 * The offset and the angle, in that order, that step [dx] right and [dy] down
 * the slide: what a format 2 shadow and an SVG filter both store. No step at all
 * keeps Keynote's straight down, so the dial has somewhere to start from.
 */
internal fun shadowOffsetAngle(dx: Float, dy: Float): Pair<Float, Float> {
    val offset: Float = hypot(dx, dy)
    if (offset == 0f) return 0f to 270f

    // The dial turns counterclockwise from right with y up, so the screen's
    // downward y goes in negated.
    val degrees: Float = atan2(-dy, dx) * 180f / PI.toFloat()
    return offset to (degrees + 360f) % 360f
}

/**
 * A shape on a slide.
 *
 * Its inside paints one of three ways, nearest first: [imageFill], [gradient],
 * then the solid [fill]. The ones underneath stay stored, so dropping a picture or
 * a gradient goes back to what was there rather than to a default. See
 * [fillKind] for which one is showing.
 */
@Serializable
@SerialName("shape")
data class ShapeElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val kind: ShapeKind = ShapeKind.Rectangle,
    /** Only [ShapeKind.Rectangle] rounds by this; every other kind ignores it. */
    val cornerRadius: Float = 10f,
    val fill: Long = 0x387F52FF,
    /** Painted instead of [fill] when set, and under [imageFill] when that is. */
    val gradient: ShapeGradient? = null,
    /**
     * A picture painted instead of [gradient] and [fill], clipped to the shape's
     * own outline. The stroke, the shadow and the label draw over and around it as
     * they would over a colour.
     *
     * Bytes the store can't read draw whatever is under it instead, and so does
     * the part of the box a [ImageFillMode.ScaleToFit] picture leaves uncovered,
     * which is the solid [fill].
     */
    val imageFill: ImageFill? = null,
    val strokeColor: Long = 0xB3A98FFF,
    val strokeWidth: Float = 1.5f,
    /** How the border is drawn along its length; a [ShapeKind.Path] outline too. */
    val strokeStyle: StrokeStyle = StrokeStyle.Solid,
    val shadow: ShapeShadow? = null,
    val label: String = "",
    val labelSize: Float = 15f,
    val labelColor: Long = 0xFFD9CFFF,
    /** Where clicking the shape takes the show. See [LinkTarget]. */
    val link: LinkTarget? = null,
    /**
     * Only [ShapeKind.Path] draws by this; every other kind ignores it. The
     * outline stretches with the frame, so a resize never rewrites it. See
     * [ShapePath].
     */
    val path: ShapePath? = null,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * A line from [start] to [end], Keynote's: the two points are the whole of its
 * geometry, so the angle is the endpoints and there is no box to turn.
 *
 * [bend] curves it: the line is then one quadratic Bezier that passes through
 * [bend] halfway along (t = 0.5), Keynote's green handle. Its [control] point is
 * derived and never shown. Null is a straight line.
 *
 * Nothing else is stored. [frame] is derived, the bounding box of the two points
 * with an axis that would be flat widened to 1pt centred on the line, because
 * everything that measures frames (overlap, marquee, grouping) wants a box with
 * some area. [rotation] is always 0 and both flips are always false.
 *
 * [update] reads the shared properties as edits to the points (and the [bend],
 * which goes wherever they go) instead:
 * - a new frame maps both points from the current frame into it by ratio, which
 *   is how moves, nudges, align, distribute and a group's resize reach a line;
 * - a rotation turns both points about the frame's centre by that many degrees
 *   clockwise, and since the stored rotation is always 0, absolute and delta are
 *   the same number;
 * - a horizontal flip mirrors the x's about the centre (swaps them), a vertical
 *   one the y's. The flags stay false, so flipping again flips back. Given both,
 *   the flip goes first and the turn after, the way a layer draws them.
 *
 * Never zero-length: [withStart] and [withEnd] keep the ends at least
 * [MinLineLength] apart, the way Keynote refuses to collapse a line.
 */
@Serializable
@SerialName("line")
data class LineElement(
    override val id: String = newId(),
    val start: Point,
    val end: Point,
    val strokeColor: Long,
    val strokeWidth: Float = 3f,
    val strokeStyle: StrokeStyle = StrokeStyle.Solid,
    val startEnd: LineEnd = LineEnd.None,
    val endEnd: LineEnd = LineEnd.None,
    val shadow: ShapeShadow? = null,
    override val opacity: Float = 1f,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    /** Where clicking the line takes the show. See [LinkTarget]. */
    val link: LinkTarget? = null,
    /** The point a curved line passes through halfway along, null for a straight one. */
    val bend: Point? = null,
) : Element {
    /**
     * The box the line covers: the two points, or for a curve the tight box of
     * the quadratic, its extremes being where either axis turns back. An axis
     * that would be flat is widened to 1pt about the line.
     */
    override val frame: Frame
        get() {
            val control: Point? = control
            fun span(a: Float, c: Float?, b: Float): Pair<Float, Float> {
                var low: Float = min(a, b)
                var high: Float = max(a, b)
                val turn: Float = if (c == null) 0f else a - 2 * c + b
                if (c != null && turn != 0f) {
                    val t: Float = ((a - c) / turn).coerceIn(0f, 1f)
                    val extreme: Float = (1 - t) * (1 - t) * a + 2 * (1 - t) * t * c + t * t * b
                    low = min(low, extreme)
                    high = max(high, extreme)
                }

                if (high - low >= MinLineLength) return low to high - low
                return (low + high) / 2 - MinLineLength / 2 to MinLineLength
            }

            val (x: Float, width: Float) = span(start.x, control?.x, end.x)
            val (y: Float, height: Float) = span(start.y, control?.y, end.y)
            return Frame(x, y, width, height)
        }

    /**
     * The quadratic's control point, `2 * bend - (start + end) / 2`, so the curve
     * passes through [bend] at t = 0.5. Null for a straight line.
     */
    val control: Point?
        get() {
            val bend: Point = bend ?: return null
            return Point(2 * bend.x - (start.x + end.x) / 2, 2 * bend.y - (start.y + end.y) / 2)
        }

    /** Where the line is at [t], 0 its start and 1 its end, along the curve when it has one. */
    fun pointAt(t: Float): Point {
        val control: Point = control
            ?: return Point(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)

        val u: Float = 1 - t
        return Point(
            x = u * u * start.x + 2 * u * t * control.x + t * t * end.x,
            y = u * u * start.y + 2 * u * t * control.y + t * t * end.y,
        )
    }

    override val rotation: Float get() = 0f
    override val flippedHorizontally: Boolean get() = false
    override val flippedVertically: Boolean get() = false

    /**
     * The start-to-end direction in degrees, counterclockwise from pointing right
     * with y up, 0 until 360: 90 is straight up, 225 down and to the left.
     */
    val angle: Float
        get() {
            val degrees: Float =
                atan2(start.y - end.y, end.x - start.x) * 180f / PI.toFloat()
            return degrees.mod(360f)
        }

    val length: Float get() = hypot(end.x - start.x, end.y - start.y)

    val midpoint: Point get() = Point((start.x + end.x) / 2, (start.y + end.y) / 2)

    /** This line turned about its [midpoint] to point at [degrees], its length kept. */
    fun withAngle(degrees: Float): LineElement {
        val radians: Float = degrees * PI.toFloat() / 180f
        val half: Float = length / 2
        val dx: Float = cos(radians) * half
        // Slide space is y down, the angle is y up.
        val dy: Float = -sin(radians) * half
        val middle: Point = midpoint
        return rechorded(
            start = Point(middle.x - dx, middle.y - dy),
            end = Point(middle.x + dx, middle.y + dy),
        )
    }

    /** This line with its start at [point], or [MinLineLength] short of the end. */
    fun withStart(point: Point): LineElement =
        rechorded(start = apart(point, from = end, was = start), end = end)

    /** This line with its end at [point], or [MinLineLength] short of the start. */
    fun withEnd(point: Point): LineElement =
        rechorded(start = start, end = apart(point, from = start, was = end))

    /**
     * This line moved onto [start] and [end], its [bend] carried along the way
     * Keynote does: the bend's offset along the chord scales with the chord, and
     * its distance off the chord stays as many points as it was, so dragging an
     * end stretches a curve without deepening it. A turn is the same rule with
     * the chord's length kept.
     */
    private fun rechorded(start: Point, end: Point): LineElement {
        val bend: Point = bend ?: return copy(start = start, end = end)
        val chord: Float = length
        val stretched: Float = hypot(end.x - start.x, end.y - start.y)
        if (chord == 0f || stretched == 0f) return copy(start = start, end = end)

        // Unit vectors along the old chord and the new one; across is along turned
        // a quarter, (-y, x), the same way on both.
        val alongX: Float = (this.end.x - this.start.x) / chord
        val alongY: Float = (this.end.y - this.start.y) / chord
        val bendX: Float = bend.x - this.start.x
        val bendY: Float = bend.y - this.start.y
        val fraction: Float = (bendX * alongX + bendY * alongY) / chord
        val depth: Float = bendX * -alongY + bendY * alongX

        val newAlongX: Float = (end.x - start.x) / stretched
        val newAlongY: Float = (end.y - start.y) / stretched
        val rebent = Point(
            x = start.x + fraction * (end.x - start.x) - depth * newAlongY,
            y = start.y + fraction * (end.y - start.y) + depth * newAlongX,
        )
        return copy(start = start, end = end, bend = rebent)
    }

    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element {
        val from: Frame = this.frame
        fun mapped(point: Point): Point {
            if (frame == from) return point
            return Point(
                x = frame.x + (point.x - from.x) / from.width * frame.width,
                y = frame.y + (point.y - from.y) / from.height * frame.height,
            )
        }

        fun turned(point: Point): Point {
            if (rotation == 0f) return point
            val (dx: Float, dy: Float) =
                rotateVector(point.x - frame.centerX, point.y - frame.centerY, rotation)
            return Point(frame.centerX + dx, frame.centerY + dy)
        }

        fun flipped(point: Point): Point = Point(
            x = if (flippedHorizontally) 2 * frame.centerX - point.x else point.x,
            y = if (flippedVertically) 2 * frame.centerY - point.y else point.y,
        )

        // Flipped before turned, the order a graphics layer applies them in, so a
        // line baked out of a flipped and rotated group lands where it was drawn.
        return copy(
            start = turned(flipped(mapped(start))),
            end = turned(flipped(mapped(end))),
            bend = bend?.let { turned(flipped(mapped(it))) },
            opacity = opacity,
            locked = locked,
            name = name,
            hidden = hidden,
        )
    }
}

/** The shortest a [LineElement] gets, in document units: Keynote's 1pt. */
const val MinLineLength: Float = 1f

/**
 * [point], or the point [MinLineLength] from [from] in its direction when it
 * lands closer than that. A point right on top of [from] has no direction of its
 * own and keeps the one it came from, [was]'s.
 */
private fun apart(point: Point, from: Point, was: Point): Point {
    val distance: Float = hypot(point.x - from.x, point.y - from.y)
    if (distance >= MinLineLength) return point

    val toward: Point = if (distance > 0f) point else was
    val reach: Float = hypot(toward.x - from.x, toward.y - from.y)
    // Nothing to go by at all: a line already collapsed. Lay it flat.
    if (reach == 0f) return Point(from.x + MinLineLength, from.y)
    return Point(
        x = from.x + (toward.x - from.x) / reach * MinLineLength,
        y = from.y + (toward.y - from.y) / reach * MinLineLength,
    )
}

/**
 * The window of an image that is shown, and the outline it is shown through.
 *
 * [frame] is in image-normalised units: x, y, width and height are all fractions
 * of the natural image. That is what makes the mask non-destructive and what
 * keeps it meaningful when the element is resized: widening the window brings the
 * hidden pixels back rather than re-cropping anything, and nothing about the mask
 * is measured in the element's own coordinates.
 *
 * The element's frame draws exactly that window, stretched to fill it, which is
 * Keynote's rule: the image behind a mask can be larger than the box showing it.
 *
 * [kind] is the outline the window is seen through. [ShapeKind.Rectangle] is the
 * plain crop and clips nothing; every other kind clips the box to that shape's
 * path.
 */
@Serializable
data class ImageMask(
    val kind: ShapeKind = ShapeKind.Rectangle,
    val frame: Frame,
)

/**
 * The three corrections an image is drawn through, never written back into its
 * bytes.
 *
 * The defaults are the identity, deliberately: an image nobody has adjusted costs
 * no colour filter at all, and an adjustment is undone by putting the numbers
 * back rather than by keeping a copy of the original around.
 */
@Serializable
data class ImageAdjust(
    /** -1 (black) to 1 (blown out); 0 is the image as it was decoded. */
    val exposure: Float = 0f,
    /** 0 (greyscale) to 2 (twice as vivid). */
    val saturation: Float = 1f,
    /** 0 (flat grey) to 2 (twice the separation), pivoting around mid grey. */
    val contrast: Float = 1f,
)

/**
 * An image on a slide: an id the asset store resolves to bytes, the window of
 * those bytes that shows, and how they are corrected on the way to the canvas.
 *
 * [assetId] null is an element with no bytes behind it yet, which draws the
 * design's drop placeholder. That is what a media placeholder on a layout is, and
 * what an insert leaves behind if its file goes missing.
 *
 * [naturalWidth] and [naturalHeight] are recorded when the image is inserted, so
 * a mask, a fit and a layout are all arithmetic on the document rather than
 * something that has to wait on a decode.
 */
@Serializable
@SerialName("image")
data class ImageElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val placeholder: String = "Drop frame capture here",
    /** What the asset store resolves to this image's bytes. Null draws [placeholder]. */
    val assetId: String? = null,
    /** The decoded size of [assetId]'s bytes, in pixels. Zero until an image lands. */
    val naturalWidth: Int = 0,
    val naturalHeight: Int = 0,
    /** Null shows the whole image. See [ImageMask]. */
    val mask: ImageMask? = null,
    val adjust: ImageAdjust = ImageAdjust(),
    /** Drawn under the image, inside the element's own box. Empty draws nothing. */
    val caption: String = "",
    /** See [PlaceholderRole]. Not [placeholder], which is the prompt the empty frame draws. */
    val role: PlaceholderRole? = null,
    /** Where clicking the image takes the show. See [LinkTarget]. */
    val link: LinkTarget? = null,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * One picture in a gallery: an id the asset store resolves to bytes, the size
 * those bytes decoded to, and what the slide says about them.
 *
 * The same three facts an [ImageElement] carries, minus everything that is the
 * element's rather than the picture's: where it sits, how it is corrected and
 * what it is masked to are the gallery's, and are the same for every image in it.
 */
@Serializable
data class GalleryImage(
    val assetId: String,
    /** The decoded size of [assetId]'s bytes, in pixels. Zero until an image lands. */
    val naturalWidth: Int = 0,
    val naturalHeight: Int = 0,
    /** Drawn under this picture while the gallery is showing it. Empty draws nothing. */
    val caption: String = "",
)

/**
 * Several pictures in one box, shown one at a time.
 *
 * A gallery's own steps are its images: `Slide.galleryImageAt` reads the
 * [Build.elementStep] the build order has reached and that is the picture on
 * screen, so a carousel is cycled by the mechanism a code block and a diagram are
 * already walked by rather than by one of its own. `Slide.gallerySteps` is what
 * writes those builds.
 *
 * [current] is the editor's alone: it is the image being authored, and play mode
 * never reads it. Kept on the element rather than in the editor's state so
 * picking an image to caption survives a save, a reopen and an undo, the way
 * every other authoring choice does.
 *
 * The adjustment is the whole gallery's, not the picture's: a carousel of frame
 * captures reads as one object, and correcting them one at a time is a different
 * feature to this one.
 */
@Serializable
@SerialName("gallery")
data class GalleryElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val images: List<GalleryImage> = emptyList(),
    /** The image the editor canvas shows. Out of range draws the nearest one there is. */
    val current: Int = 0,
    val showCaptions: Boolean = true,
    val adjust: ImageAdjust = ImageAdjust(),
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * Elements moved, resized and transformed as one.
 *
 * [children] are stored in absolute slide coordinates, not relative to the
 * group: every renderer, hit test and inspector keeps reading a frame the same
 * way whether the element sits in a group or not. The price is [update], which
 * has to rewrite the children whenever the group's own frame changes.
 *
 * Rotation, flips, opacity and lock stay layered on the group instead, and are
 * baked into the children only when the group comes apart. See
 * `Slide.ungroupElement`.
 */
@Serializable
@SerialName("group")
data class GroupElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val children: List<Element> = emptyList(),
) : Element {
    /**
     * A new [frame] maps every child through the old-to-new affine, so children
     * follow a move or a resize as it happens rather than at draw time. A nested
     * group recurses through its own [update] and stays consistent with itself.
     *
     * Text and font sizes are not part of that mapping: a resized group scales
     * its children's boxes, not their type. Known simplification.
     */
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element {
        // `frame` is the new one, `this.frame` the one the children were laid out in.
        val moved: List<Element> = if (frame == this.frame) children else {
            val scaleX: Float = if (this.frame.width == 0f) 1f else frame.width / this.frame.width
            val scaleY: Float = if (this.frame.height == 0f) 1f else frame.height / this.frame.height
            children.map { child ->
                child.update(
                    frame = Frame(
                        x = frame.x + (child.frame.x - this.frame.x) * scaleX,
                        y = frame.y + (child.frame.y - this.frame.y) * scaleY,
                        width = child.frame.width * scaleX,
                        height = child.frame.height * scaleY,
                    ),
                )
            }
        }

        return copy(
            frame = frame,
            opacity = opacity,
            rotation = rotation,
            flippedHorizontally = flippedHorizontally,
            flippedVertically = flippedVertically,
            locked = locked,
            name = name,
            hidden = hidden,
            children = moved,
        )
    }
}

/**
 * The syntax palettes the highlighter ships, and the whole of what a code box
 * can be dressed in. Named after the editors they come from rather than after
 * their colours, because that is what someone picking one is looking for.
 *
 * [Atom] is where every code box starts, and where every document written before
 * this enum existed lands when it is opened. [Notepad] and [IntelliJLight] are
 * the light palettes: their blocks are drawn pale so their ink is legible, on a
 * slide that may stay dark.
 *
 * [IntelliJLight] and [IntelliJDark] are ours rather than the highlighter's: the
 * editor schemes the IDE's Islands Light and Islands Dark themes set, where
 * [Darcula] is the scheme it shipped before 2023.
 */
@Serializable
enum class CodeTheme { Atom, Darcula, Monokai, Pastel, Matrix, Notepad, IntelliJLight, IntelliJDark }

/**
 * A terminal window on a slide: a command, what it printed, and the chrome around
 * both.
 *
 * A line that starts with [prompt] and a space, or that is the prompt on its own,
 * is a command: it draws its prompt green and its own text bright. Every other
 * line is output and draws dim. That keeps [text] one plain string, so typing,
 * undo and the file format all stay as they were, the same argument [ListStyle]
 * makes for its lines.
 *
 * [title] is what the title bar says and so is content, not style: it names the
 * shell this session is, which travels with the text rather than with the look.
 */
@Serializable
@SerialName("terminal")
data class TerminalElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val text: String = "",
    val prompt: String = "$",
    val title: String = "zsh",
    val fontSize: Float = 14f,
    val showTitleBar: Boolean = true,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/** Whether a double click puts a caret in it: the kinds edited in place on the canvas. */
val Element.takesCaret: Boolean
    get() = this is TextElement || this is CodeElement || this is TerminalElement ||
        this is DiagramElement || this is EquationElement

@Serializable
@SerialName("code")
data class CodeElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val code: String = "",
    /**
     * The later versions of the same block, for a code morph: [code] is version
     * 0 and `versions[i]` is version i + 1, each one the previous one rewritten.
     *
     * Versions rather than one string per step, because a morph is a diff: a
     * [CodeStep] names the version it shows, and the renderer tokenizes both and
     * tweens the tokens they have in common (see `diffCode`). Empty, and so every
     * document written before this field, is a block with one version and nothing
     * to morph into. `CodeVersions.kt` holds the edits, which keep the steps
     * pointing at the text they pointed at.
     */
    val versions: List<String> = emptyList(),
    /**
     * Free-form, and resolved case-insensitively by the renderer: documents on
     * disk already carry lowercase names, and an unknown one highlights as plain
     * text rather than failing to open. `CodeLanguages` is what a picker offers.
     */
    val language: String = "kotlin",
    val fontSize: Float = 14f,
    val theme: CodeTheme = CodeTheme.Atom,
    /** The theme's 1pt outline. On, and so every document written before this field, keeps it. */
    val showBorder: Boolean = true,
    /** Gutter numbers count physical lines, so they match what was typed. */
    val showLineNumbers: Boolean = false,
    /** Off, and a long line runs out of the box rather than reflowing under itself. */
    val wrap: Boolean = false,
    /**
     * The states the block is walked through in play mode, if any. Empty (and so
     * every document written before this field) is a block that always shows all
     * of its code.
     *
     * A stepped block draws `steps[0]` from the moment it is visible, and builds
     * carrying a [Build.elementStep] move it along from there. Only play mode steps:
     * the editor canvas shows the whole block, because what is being edited is
     * the code rather than the walk through it.
     */
    val steps: List<CodeStep> = emptyList(),
    /** See [PlaceholderRole]: set, this is a placeholder on a layout or an instance on a slide. */
    val role: PlaceholderRole? = null,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * A diagram written as text: a mermaid-flowchart subset in [source], parsed and
 * laid out by the renderer rather than stored as boxes and arrows.
 *
 * Text rather than a node list because that is the point of the kind: a diagram
 * you edit by typing is one you can rewrite mid-talk, and one that diffs. The
 * layout is a pure function of the source (`parseDiagram` then `layoutDiagram`),
 * so it is the same on every target and nothing about a position is stored.
 *
 * A source that doesn't parse is not an error: unrecognised lines are dropped
 * one at a time, so a typo costs an edge rather than the slide.
 *
 * The four colours are the whole of the look, deliberately: a diagram reads as
 * one object, and per-node styling is a different feature to this one.
 */
@Serializable
@SerialName("diagram")
data class DiagramElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val source: String = "",
    val fontSize: Float = 16f,
    val nodeFill: Long = 0x387F52FF,
    val nodeStroke: Long = 0xB3A98FFF,
    val nodeText: Long = 0xFFD9CFFF,
    val edgeColor: Long = 0xFFA9A0D8,
    /**
     * The states the diagram is walked through in play mode, if any. Empty is a
     * diagram that always shows all of itself, which is every document written
     * before this field.
     */
    val steps: List<DiagramStep> = emptyList(),
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * An equation written as LaTeX: a subset of the language in [latex], parsed and
 * laid out by the renderer rather than stored as boxes and glyphs.
 *
 * Text rather than a glyph tree for the same reason a diagram is text: an
 * equation you edit by typing is one you can correct mid-talk, and one that
 * diffs. The layout is a pure function of the source (`parseMath` then
 * `layoutMath`), so it is the same on every target and nothing about a position
 * is stored.
 *
 * A source that doesn't parse is not an error: an unknown command draws as
 * itself, so a typo costs a glyph rather than the slide.
 *
 * [fontSize] is the size of the equation's own body, in document units; scripts,
 * fractions and limits are all set against it. One colour is the whole of the
 * look, deliberately: an equation reads as one object, and colouring a subterm
 * is a different feature to this one.
 */
@Serializable
@SerialName("equation")
data class EquationElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    val latex: String = "",
    val fontSize: Float = 40f,
    val color: Long = 0xFFFFFFFF,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * A movie on a slide: either bytes the deck owns or a page somewhere else.
 *
 * [assetId] is the embedded route, an id the asset store resolves the way an
 * image's is, and it is what a player is handed. [webUrl] is the other one: a
 * video that lives on a site, which nothing here downloads and no canvas draws.
 * Both null is an element with nothing behind it yet, which draws the placeholder
 * a media frame does.
 *
 * Set together, the bytes win: an embedded movie is the one that plays offline,
 * and the url is then only where it came from. That is why a tap on a web-only
 * video opens the page instead of playing anything.
 *
 * [posterAssetId] is the still the slide shows while nothing is playing, and the
 * whole of what the shared canvas can draw of a movie: decoding frames is a
 * platform player's job, and this element is what it is asked with. See
 * `MediaPlayerHost`.
 *
 * [trimStartMs] and [trimEndMs] are the window of the movie that plays, in
 * milliseconds from its own start. [trimEndMs] 0 means to the end, so a movie
 * nobody has trimmed carries no duration it would have to be kept in step with.
 *
 * [volume] is 0 to 1 of the host's own level, and [title] is what the slide says
 * the movie is: content rather than style, which is why a pasted style leaves it.
 */
@Serializable
@SerialName("video")
data class VideoElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    /** What the asset store resolves to this movie's bytes. Null is a web or empty video. */
    val assetId: String? = null,
    /** A video hosted elsewhere, opened rather than played. Ignored when [assetId] is set. */
    val webUrl: String? = null,
    /** The still the canvas draws. Null draws the dark media box instead. */
    val posterAssetId: String? = null,
    val trimStartMs: Int = 0,
    /** 0 plays to the end, so an untrimmed movie carries no duration of its own. */
    val trimEndMs: Int = 0,
    val loop: Boolean = false,
    /** 0 to 1 of whatever the host is playing at. */
    val volume: Float = 1f,
    /** Plays as soon as the build order brings the element on screen. */
    val autoplay: Boolean = false,
    val title: String = "",
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}

/**
 * A sound on a slide: bytes the deck owns, drawn as the pill that says they are
 * there.
 *
 * The same trim, loop, volume and autoplay a [VideoElement] carries, and for the
 * same reason: what a host is asked to play is one set of facts whether or not
 * there are pixels in it. There is no web route and no poster, because neither
 * means anything without a picture: a sound is either in the bundle or it is
 * nothing the slide can play.
 *
 * [title] is what the pill reads, which is the whole of what the audience sees of
 * a sound. Empty is a pill that names itself.
 */
@Serializable
@SerialName("audio")
data class AudioElement(
    override val id: String = newId(),
    override val frame: Frame,
    override val opacity: Float = 1f,
    override val rotation: Float = 0f,
    override val flippedHorizontally: Boolean = false,
    override val flippedVertically: Boolean = false,
    override val locked: Boolean = false,
    override val name: String? = null,
    override val hidden: Boolean = false,
    /** What the asset store resolves to this sound's bytes. Null is an empty pill. */
    val assetId: String? = null,
    val trimStartMs: Int = 0,
    /** 0 plays to the end. See [VideoElement.trimEndMs]. */
    val trimEndMs: Int = 0,
    val loop: Boolean = false,
    val volume: Float = 1f,
    val autoplay: Boolean = false,
    val title: String = "",
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
        name: String?,
        hidden: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
        name = name,
        hidden = hidden,
    )
}
