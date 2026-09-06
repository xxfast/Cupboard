package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    /** A locked element still selects and still renders, but nothing edits it until it unlocks. */
    val locked: Boolean

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
    ): Element
}

enum class TextAlign { Start, Center, End }

/**
 * Generic families only, and deliberately so: the canvas draws on every target
 * from the same document, and nothing bundles or resolves font files yet. A
 * document that named "Inter" would render as one thing on macOS and another
 * wherever the face is missing.
 */
enum class TextFont { Sans, Serif, Monospace }

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
    val text: String = "",
    val fontSize: Float = 15f,
    /** Bold is a point on this scale rather than a flag of its own; see `isBold`. */
    val fontWeight: Int = 400,
    val lineHeight: Float = 1.3f,
    val letterSpacing: Float = 0f,
    val color: Long = 0xFFFFFFFF,
    val align: TextAlign = TextAlign.Start,
    val fontFamily: TextFont = TextFont.Sans,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val listStyle: ListStyle = ListStyle.None,
    /**
     * The whole box as one hyperlink. Ranges inside the text come with the
     * attributed-string work; until then a link is a property of the element,
     * which is enough for the shape it takes on a slide. Nothing opens it yet.
     */
    val link: String? = null,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
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
 * with a tail, bottom-left and bottom-center. [Line] is the odd one out: it runs
 * corner to corner across its frame and draws no fill, only its stroke and
 * whichever arrowheads are on.
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
    Line,
}

/**
 * A two-stop fill along a line at [angle], in CSS degrees like
 * [SlideBackground.Gradient]: 0 points up and the angle turns clockwise.
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
)

/**
 * The drop shadow under a shape. Colors are packed ARGB like everywhere else,
 * and [blur], [dx] and [dy] are document units.
 *
 * [dx] and [dy] are stored whether or not a given renderer honours them: Compose
 * draws an elevation shadow, which has an offset of its own, so the document
 * keeps the intent and the canvas gets as close to it as its toolkit allows.
 */
@Serializable
data class ShapeShadow(
    val color: Long = 0x80000000,
    val blur: Float = 12f,
    val dx: Float = 0f,
    val dy: Float = 6f,
)

/**
 * A shape on a slide.
 *
 * There is no image fill: image bytes live on the side, and bytes on the side
 * arrive with the bundle format, the same wait [ImageElement] and
 * [SlideBackground] are in.
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
    val kind: ShapeKind = ShapeKind.Rectangle,
    /** Only [ShapeKind.Rectangle] rounds by this; every other kind ignores it. */
    val cornerRadius: Float = 10f,
    val fill: Long = 0x387F52FF,
    /** Painted instead of [fill] when set. */
    val gradient: ShapeGradient? = null,
    val strokeColor: Long = 0xB3A98FFF,
    val strokeWidth: Float = 1.5f,
    val shadow: ShapeShadow? = null,
    /** Only [ShapeKind.Line] draws arrowheads; every other kind ignores both. */
    val startArrow: Boolean = false,
    val endArrow: Boolean = true,
    val label: String = "",
    val labelSize: Float = 15f,
    val labelColor: Long = 0xFFD9CFFF,
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
    )
}

/**
 * Image content ships with the file format work; until then an image element
 * renders as the design's drop placeholder.
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
    val placeholder: String = "Drop frame capture here",
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
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
 * this enum existed lands when it is opened. [Notepad] is the one light palette:
 * its block is drawn pale so its ink is legible, on a slide that stays dark.
 */
@Serializable
enum class CodeTheme { Atom, Darcula, Monokai, Pastel, Matrix, Notepad }

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
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
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
    val code: String = "",
    /**
     * Free-form, and resolved case-insensitively by the renderer: documents on
     * disk already carry lowercase names, and an unknown one highlights as plain
     * text rather than failing to open. `CodeLanguages` is what a picker offers.
     */
    val language: String = "kotlin",
    val fontSize: Float = 14f,
    val theme: CodeTheme = CodeTheme.Atom,
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
) : Element {
    override fun update(
        frame: Frame,
        opacity: Float,
        rotation: Float,
        flippedHorizontally: Boolean,
        flippedVertically: Boolean,
        locked: Boolean,
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
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
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
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
    ): Element = copy(
        frame = frame,
        opacity = opacity,
        rotation = rotation,
        flippedHorizontally = flippedHorizontally,
        flippedVertically = flippedVertically,
        locked = locked,
    )
}
