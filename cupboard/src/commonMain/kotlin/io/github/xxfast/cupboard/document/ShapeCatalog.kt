package io.github.xxfast.cupboard.document

import kotlin.math.min

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
 * A fresh [kind] filling [frame], dressed in [defaults]' shape colours.
 *
 * Rectangles come rounded, which is the shape the mock uses everywhere; the
 * catalog's plain "Rectangle" entry is the one that squares the corners back off.
 *
 * [defaults] is the deck's, `EditorState.defaults`, and defaulted here so a
 * caller with no document in hand still gets the app's own look. Every insert
 * factory below takes it the same way: what a fresh element is dressed in is the
 * theme's business, not the shell's.
 */
fun shapeElement(
    kind: ShapeKind,
    frame: Frame,
    defaults: ElementDefaults = ElementDefaults(),
): ShapeElement = ShapeElement(
    frame = frame,
    kind = kind,
    cornerRadius = RoundedCornerRadius,
    fill = defaults.shapeFill,
    strokeColor = defaults.shapeStroke,
    strokeWidth = if (kind == ShapeKind.Line) LineStrokeWidth else ShapeStrokeWidth,
    labelColor = defaults.shapeLabelColor,
)

/** The box a fresh code block inserts into. */
const val DefaultCodeBoxWidth: Float = 420f
const val DefaultCodeBoxHeight: Float = 160f

/**
 * A fresh text box filling [frame], with the placeholder a shell drops the caret
 * into and the deck's text defaults on it.
 *
 * Every one of the six is [defaults]', which is what makes Use As Default work: a
 * box set the way you like it writes itself back into the deck, and the next box
 * arrives already set that way.
 */
fun textBoxElement(frame: Frame, defaults: ElementDefaults = ElementDefaults()): TextElement =
    TextElement(
        frame = frame,
        text = "Text",
        fontSize = defaults.textSize,
        fontWeight = defaults.textWeight,
        lineHeight = defaults.textLineHeight,
        color = defaults.textColor,
        align = defaults.textAlign,
        fontFamily = defaults.textFont,
    )

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
fun codeBoxElement(frame: Frame, defaults: ElementDefaults = ElementDefaults()): CodeElement =
    CodeElement(
        frame = frame,
        code = """
            fun main() {
                println("Hello, Cupboard")
            }
        """.trimIndent(),
        language = "Kotlin",
        theme = defaults.codeTheme,
    )

/** The box a fresh terminal inserts into. */
const val DefaultTerminalWidth: Float = 520f
const val DefaultTerminalHeight: Float = 200f

/**
 * A fresh terminal filling [frame], with a command and its output to type over.
 *
 * [defaults] is taken and not read: a terminal draws its own chrome, prompt green
 * and output dim, and a themed one would stop looking like a terminal. The
 * parameter is here so every insert factory has one signature.
 */
@Suppress("UNUSED_PARAMETER")
fun terminalElement(
    frame: Frame,
    defaults: ElementDefaults = ElementDefaults(),
): TerminalElement = TerminalElement(
    frame = frame,
    text = "$ ./gradlew :cupboard:jvmTest\nBUILD SUCCESSFUL in 4s",
)

/** The box a fresh diagram inserts into. */
const val DefaultDiagramWidth: Float = 560f
const val DefaultDiagramHeight: Float = 320f

/**
 * A fresh diagram filling [frame], with a flowchart to rewrite.
 *
 * The starter source uses four of the five node shapes, so what the syntax buys
 * is on screen the moment the element lands rather than in a help page.
 */
fun diagramElement(
    frame: Frame,
    defaults: ElementDefaults = ElementDefaults(),
): DiagramElement = DiagramElement(
    frame = frame,
    source = "graph LR\n" +
        "  A[Edit] --> B[Render]\n" +
        "  B --> C{Ship?}\n" +
        "  C -->|yes| D((Play))\n" +
        "  C -->|no| A",
    // A node is a shape and an edge is body copy, which is the whole of why
    // [ElementDefaults] has no diagram colours of its own.
    nodeFill = defaults.shapeFill,
    nodeStroke = defaults.shapeStroke,
    nodeText = defaults.shapeLabelColor,
    edgeColor = defaults.bodyColor,
)

/** The box a fresh image is fitted into, before its own aspect narrows it. */
const val DefaultImageWidth: Float = 480f
const val DefaultImageHeight: Float = 320f

/**
 * A fresh image for [assetId], its natural aspect fitted inside [frame] and
 * centred there.
 *
 * Fitted rather than stretched because an image arrives with a shape of its own,
 * unlike every other element the catalog makes: [frame] is the room it is given
 * and the aspect is what it takes of it.
 *
 * [defaults] is taken and not read, the way [terminalElement] takes it: an image
 * has no colours to inherit, and one signature across the insert factories is
 * worth more than the parameter costs.
 */
@Suppress("UNUSED_PARAMETER")
fun imageElement(
    frame: Frame,
    assetId: String,
    naturalWidth: Int,
    naturalHeight: Int,
    defaults: ElementDefaults = ElementDefaults(),
): ImageElement = ImageElement(
    frame = fitInside(frame, naturalWidth.toFloat(), naturalHeight.toFloat()),
    assetId = assetId,
    naturalWidth = naturalWidth,
    naturalHeight = naturalHeight,
)

/**
 * A fresh gallery of [images], fitted inside [frame] like a single image is.
 *
 * The first picture's aspect is the gallery's: the box has to be one shape, and
 * the one the audience sees first is the one it is worth fitting. An empty list
 * takes [frame] as it is, which is the box an insert leaves behind when its files
 * all go missing.
 *
 * [defaults] is taken and not read, the way [imageElement] takes it: pictures
 * have no colours to inherit, and one signature across the insert factories is
 * worth more than the parameter costs.
 */
@Suppress("UNUSED_PARAMETER")
fun galleryElement(
    frame: Frame,
    images: List<GalleryImage>,
    defaults: ElementDefaults = ElementDefaults(),
): GalleryElement {
    val first: GalleryImage? = images.firstOrNull()
    return GalleryElement(
        frame =
            if (first == null) frame
            else fitInside(frame, first.naturalWidth.toFloat(), first.naturalHeight.toFloat()),
        images = images,
    )
}

/**
 * This image shrunk to fit inside [maxWidth] x [maxHeight], about its own centre.
 *
 * Already small enough, it comes back as it is: fitting never enlarges, so
 * dropping a thumbnail on a slide leaves a thumbnail rather than a blown-up one.
 */
fun ImageElement.fitted(maxWidth: Float, maxHeight: Float): ImageElement {
    if (frame.width <= 0f || frame.height <= 0f) return this
    val scale: Float = min(maxWidth / frame.width, maxHeight / frame.height)
    if (scale >= 1f) return this

    val width: Float = frame.width * scale
    val height: Float = frame.height * scale
    return copy(
        frame = Frame(
            x = frame.centerX - width / 2,
            y = frame.centerY - height / 2,
            width = width,
            height = height,
        ),
    )
}

/** [width] x [height]'s aspect scaled to fill as much of [frame] as it can, centred in it. */
private fun fitInside(frame: Frame, width: Float, height: Float): Frame {
    if (width <= 0f || height <= 0f) return frame
    val scale: Float = min(frame.width / width, frame.height / height)
    val fittedWidth: Float = width * scale
    val fittedHeight: Float = height * scale

    return Frame(
        x = frame.centerX - fittedWidth / 2,
        y = frame.centerY - fittedHeight / 2,
        width = fittedWidth,
        height = fittedHeight,
    )
}

/**
 * The box a fresh movie inserts into: 16:9 at half of a widescreen slide, which
 * is the shape all but a handful of movies already are.
 *
 * Not fitted to the movie's own aspect the way an image is, because nothing here
 * has decoded it: the natural size of a movie is a platform player's to report,
 * and the box is resized once it does rather than guessed at now.
 */
const val DefaultVideoWidth: Float = 960f
const val DefaultVideoHeight: Float = 540f

/**
 * A fresh movie filling [frame], playing [assetId]'s bytes or standing for the
 * page at [webUrl].
 *
 * Both are nullable and both may be given: the bytes are what plays and the url
 * is then only where they came from. Neither is a media frame with nothing behind
 * it yet, which is what an insert leaves when its file goes missing.
 *
 * [defaults] is taken and not read, the way [imageElement] takes it: a movie has
 * no colours to inherit, and one signature across the insert factories is worth
 * more than the parameter costs.
 */
@Suppress("UNUSED_PARAMETER")
fun videoElement(
    frame: Frame,
    assetId: String? = null,
    webUrl: String? = null,
    defaults: ElementDefaults = ElementDefaults(),
): VideoElement = VideoElement(frame = frame, assetId = assetId, webUrl = webUrl)

/** The pill a fresh sound inserts as: wide enough for a name, one line high. */
const val DefaultAudioWidth: Float = 320f
const val DefaultAudioHeight: Float = 64f

/**
 * A fresh sound filling [frame], playing [assetId]'s bytes under [title].
 *
 * The title is the whole of what the audience sees of a sound, so it is a
 * parameter rather than something set afterwards: an insert names the file it
 * came from and the pill reads as that from the moment it lands.
 *
 * [defaults] is taken and not read, for [videoElement]'s reason.
 */
@Suppress("UNUSED_PARAMETER")
fun audioElement(
    frame: Frame,
    assetId: String,
    title: String = "",
    defaults: ElementDefaults = ElementDefaults(),
): AudioElement = AudioElement(frame = frame, assetId = assetId, title = title)

/** The box a fresh equation inserts into. */
const val DefaultEquationWidth: Float = 480f
const val DefaultEquationHeight: Float = 140f

/**
 * A fresh equation filling [frame], with the identity everyone recognises.
 *
 * Euler rather than a placeholder: it uses a superscript, a greek letter, a
 * relation and an operator, so what the syntax buys is on screen the moment the
 * element lands rather than in a help page.
 */
fun equationElement(
    frame: Frame,
    defaults: ElementDefaults = ElementDefaults(),
): EquationElement = EquationElement(
    frame = frame,
    latex = "e^{i\\pi} + 1 = 0",
    color = defaults.textColor,
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

/** A fresh element for this entry, filling [frame] and dressed in [defaults]. */
fun ShapeCatalogEntry.element(
    frame: Frame,
    defaults: ElementDefaults = ElementDefaults(),
): ShapeElement = shapeElement(kind, frame, defaults).copy(cornerRadius = cornerRadius)

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
