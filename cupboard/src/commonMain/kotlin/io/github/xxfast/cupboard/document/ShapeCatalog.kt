package io.github.xxfast.cupboard.document

/** The radius a rounded rectangle inserts with, and what "rounded" means here. */
private const val RoundedCornerRadius: Float = 10f

/** The stroke every shape but a line inserts with, per the design's shape boxes. */
private const val ShapeStrokeWidth: Float = 1.5f

/** A line is nothing but its stroke, so it starts heavier than a shape's outline. */
private const val LineStrokeWidth: Float = 3f

/** The box a fresh shape inserts into. */
const val DefaultShapeWidth: Float = 240f
const val DefaultShapeHeight: Float = 160f

/**
 * A line's box. Not flat: a zero-height frame has no handles to grab and no
 * second diagonal to flip to, so a line starts as the diagonal of a shallow box.
 */
const val DefaultLineWidth: Float = 320f
const val DefaultLineHeight: Float = 120f

/** The box a fresh text box inserts into. */
const val DefaultTextBoxWidth: Float = 400f
const val DefaultTextBoxHeight: Float = 60f

/**
 * A fresh [kind] filling [frame], dressed in the design's shape colours.
 *
 * Rectangles come rounded, which is the shape the mock uses everywhere; the
 * catalog's plain "Rectangle" entry is the one that squares the corners back off.
 */
fun shapeElement(kind: ShapeKind, frame: Frame): ShapeElement = ShapeElement(
    frame = frame,
    kind = kind,
    cornerRadius = RoundedCornerRadius,
    strokeWidth = if (kind == ShapeKind.Line) LineStrokeWidth else ShapeStrokeWidth,
)

/** The box a fresh code block inserts into. */
const val DefaultCodeBoxWidth: Float = 420f
const val DefaultCodeBoxHeight: Float = 160f

/** A fresh text box filling [frame], with the placeholder a shell drops the caret into. */
fun textBoxElement(frame: Frame): TextElement =
    TextElement(frame = frame, text = "Text", fontSize = 32f)

/**
 * The languages a picker offers, in menu order.
 *
 * Display names rather than the engine's keys: every one of these resolves
 * through the renderer's case-insensitive mapping, and "Plain" is the one that
 * deliberately doesn't, landing on no highlighting at all. The highlighter knows
 * a few more, but a picker is a short list or it is no help.
 */
val CodeLanguages: List<String> = listOf(
    "Kotlin",
    "Swift",
    "Java",
    "JavaScript",
    "TypeScript",
    "Python",
    "Rust",
    "C",
    "C++",
    "C#",
    "Go",
    "Dart",
    "PHP",
    "Ruby",
    "Shell",
    "Plain",
)

/** A fresh code block filling [frame], with a snippet to type over. */
fun codeBoxElement(frame: Frame): CodeElement = CodeElement(
    frame = frame,
    code = """
        fun main() {
            println("Hello, Cupboard")
        }
    """.trimIndent(),
    language = "Kotlin",
)

/**
 * One entry of the shape menu: what to insert, what to call it, and how round.
 *
 * [cornerRadius] is the entry's own rather than the kind's, which is what lets
 * the plain and the rounded rectangle be two entries over one [ShapeKind]. It
 * means nothing to any other kind, and every other entry leaves it at 0.
 *
 * [width] and [height] are the box the entry inserts into, so a shell can size
 * an insertion without knowing that a line wants a different box to a rectangle.
 */
data class ShapeCatalogEntry(
    val kind: ShapeKind,
    val title: String,
    val cornerRadius: Float = 0f,
    val width: Float = DefaultShapeWidth,
    val height: Float = DefaultShapeHeight,
)

/** A fresh element for this entry, filling [frame]. */
fun ShapeCatalogEntry.element(frame: Frame): ShapeElement =
    shapeElement(kind, frame).copy(cornerRadius = cornerRadius)

/**
 * The shapes a shell offers, in menu order.
 *
 * An object rather than a bare list so the catalog has somewhere to grow: the
 * bundle format brings image fills, and lines want their own dash styles.
 */
object ShapeCatalog {
    val entries: List<ShapeCatalogEntry> = listOf(
        ShapeCatalogEntry(ShapeKind.Rectangle, "Rectangle"),
        ShapeCatalogEntry(ShapeKind.Rectangle, "Rounded Rectangle", RoundedCornerRadius),
        ShapeCatalogEntry(ShapeKind.Ellipse, "Oval"),
        ShapeCatalogEntry(ShapeKind.Triangle, "Triangle"),
        ShapeCatalogEntry(ShapeKind.Arrow, "Arrow"),
        ShapeCatalogEntry(ShapeKind.Diamond, "Diamond"),
        ShapeCatalogEntry(ShapeKind.Star, "Star"),
        ShapeCatalogEntry(ShapeKind.Polygon, "Hexagon"),
        ShapeCatalogEntry(ShapeKind.QuoteBubble, "Quote Bubble"),
        ShapeCatalogEntry(ShapeKind.Callout, "Callout"),
        ShapeCatalogEntry(
            kind = ShapeKind.Line,
            title = "Line",
            width = DefaultLineWidth,
            height = DefaultLineHeight,
        ),
    )
}
