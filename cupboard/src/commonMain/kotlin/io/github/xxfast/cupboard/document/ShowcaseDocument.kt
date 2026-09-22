package io.github.xxfast.cupboard.document

/**
 * A deck with one slide per feature the document model can express, so a human
 * can see the whole of what a Cupboard document is by walking it.
 *
 * Every feature slide is the same shape: a header naming the feature, the demo
 * itself over the stage under it, and notes written as a verification checklist.
 * That regularity is the point. A slide that looks different to its neighbours is
 * a slide whose renderer is doing something the others aren't.
 *
 * Sections are navigator outline groups: a depth-0 title card, then the feature
 * slides at depth 1 under it. Anything enumerable is built off `entries` rather
 * than a hand-written list, so a new [ShapeKind] or [BuildEffect] shows up on the
 * slide the day it is added instead of the day someone remembers this file.
 */
fun showcaseDocument(): Document {
    val layouts: List<Slide> = defaultLayouts()

    return Document(
        name = "Feature Showcase",
        layouts = layouts,
        slides = listOf(coverSlide()) +
            sectionSlide("Text", "Everything a text box can say, and how") +
            textSlides() +
            sectionSlide("Shapes", "The catalog, its dress, and the deck's styles") +
            shapeSlides() +
            sectionSlide("Elements", "The properties every element carries") +
            elementSlides() +
            sectionSlide("Media", "Images, sound and movies, without any bytes") +
            mediaSlides() +
            sectionSlide("Code", "Palettes, gutters, steps and morphs") +
            codeSlides() +
            sectionSlide("Terminal", "A shell session as an element") +
            terminalSlides() +
            sectionSlide("Diagrams", "Flowcharts written as text") +
            diagramSlides() +
            sectionSlide("Equations", "LaTeX laid out on the canvas") +
            equationSlides() +
            sectionSlide("Builds", "How an element arrives, moves and leaves") +
            buildSlides() +
            sectionSlide("Transitions", "How one slide gives way to the next") +
            transitionSlides() +
            sectionSlide("Links", "Where clicking an element takes the show") +
            linkSlides() +
            sectionSlide("Slides", "Backgrounds, numbering, skipping and layouts") +
            slideSlides(layouts) +
            sectionSlide("Themes", "One look, applied to the whole deck") +
            themeSlides(),
    )
}

/**
 * The same deck put on [theme]: backgrounds, defaults and layouts all swapped,
 * which is what the Theme sampler slide asks the viewer to do by hand.
 *
 * What a theme reaches is placeholder instances and the deck's own furniture, so
 * most of the showcase keeps the colours it was written in. That is deliberate:
 * a slide demonstrating a gradient has to keep its gradient.
 */
fun showcaseDocument(theme: Theme): Document = showcaseDocument().applyingTheme(theme)

/** The cover's id, fixed so [LinkTarget.Slide] on the Links slide can name it. */
internal const val ShowcaseCoverId: String = "showcase-cover"

/** The dress every showcase slide is built in: the deck's own, untouched, defaults. */
internal val ShowcaseDefaults: ElementDefaults = ElementDefaults()

/** The margin every showcase slide is laid out inside, in document units. */
internal const val ShowcaseMargin: Float = 110f

/** The width of everything between the margins. */
internal const val ShowcaseWidth: Float = Document.SLIDE_WIDTH - 2 * ShowcaseMargin

/** The band the demo itself gets: everything under the header, down to the foot. */
internal val ShowcaseStage: Frame = Frame(ShowcaseMargin, 250f, ShowcaseWidth, 760f)

/** How much of a grid cell the caption under it takes. */
internal const val CaptionHeight: Float = 48f

/**
 * The stage shortened to [height], for a grid whose demos need less room than
 * the slide has: a tall cell leaves its caption stranded far below what it names.
 */
internal fun stage(height: Float): Frame = ShowcaseStage.copy(height = height)

/** The cover: what this deck is, and how to read it. */
private fun coverSlide(): Slide = Slide(
    id = ShowcaseCoverId,
    title = "Feature Showcase",
    elements = listOf(
        TextElement(
            frame = Frame(ShowcaseMargin, 380f, ShowcaseWidth, 160f),
            text = "Feature Showcase",
            fontSize = 120f,
            fontWeight = BoldWeight,
            letterSpacing = -3f,
            color = ShowcaseDefaults.textColor,
        ),
        TextElement(
            frame = Frame(ShowcaseMargin, 560f, ShowcaseWidth, 140f),
            text = "One slide per feature the document model can express.\n" +
                "Every slide's notes say what to look for, and what to click.",
            fontSize = 38f,
            lineHeight = 1.5f,
            color = ShowcaseDefaults.bodyColor,
        ),
    ),
    notes = "Look for: the title and the two-line standfirst, nothing else.\n\n" +
        "Walk the deck top to bottom. Each section opens with a title card, and " +
        "every slide under it demonstrates one feature.",
)

/**
 * A section's own row: the depth-0 title card its feature slides sit under.
 *
 * A list of one rather than a bare slide, so the assembly above reads as one
 * chain of concatenations instead of alternating between two operators.
 */
internal fun sectionSlide(title: String, subtitle: String): List<Slide> = listOf(
    Slide(
        title = title,
        elements = listOf(
            TextElement(
                frame = Frame(ShowcaseMargin, 430f, ShowcaseWidth, 140f),
                text = title,
                fontSize = 104f,
                fontWeight = BoldWeight,
                letterSpacing = -2f,
                color = ShowcaseDefaults.textColor,
            ),
            TextElement(
                frame = Frame(ShowcaseMargin, 590f, ShowcaseWidth, 60f),
                text = subtitle,
                fontSize = 36f,
                color = ShowcaseDefaults.bodyColor,
            ),
        ),
        notes = "Look for: the section title and its one-line summary, nothing else. " +
            "Collapse this row in the navigator and the whole section folds under it.",
    ),
)

/**
 * One feature slide: the shared header, then [elements] over the stage, and the
 * verification checklist in [notes].
 *
 * Returned at depth 1, so it lands inside the section card above it. Callers that
 * want a background or a transition copy it on, which keeps this to the shape
 * every slide shares rather than to a parameter per field a slide has.
 */
internal fun featureSlide(
    title: String,
    subtitle: String,
    notes: String,
    elements: List<Element>,
    builds: List<Build> = emptyList(),
): Slide = Slide(
    title = title,
    depth = 1,
    elements = listOf(
        TextElement(
            frame = Frame(ShowcaseMargin, 70f, ShowcaseWidth, 80f),
            text = title,
            fontSize = 58f,
            fontWeight = BoldWeight,
            letterSpacing = -1f,
            color = ShowcaseDefaults.textColor,
        ),
        TextElement(
            frame = Frame(ShowcaseMargin, 162f, ShowcaseWidth, 52f),
            text = subtitle,
            fontSize = 30f,
            color = ShowcaseDefaults.bodyColor,
        ),
    ) + elements,
    builds = builds,
    notes = notes,
)

/**
 * The [index]th cell of a [columns] by [rows] grid filling [area], with [gap]
 * between neighbours. Cells run left to right, then top to bottom.
 */
internal fun gridCell(
    area: Frame = ShowcaseStage,
    columns: Int,
    rows: Int,
    index: Int,
    gap: Float = 28f,
): Frame {
    val width: Float = (area.width - gap * (columns - 1)) / columns
    val height: Float = (area.height - gap * (rows - 1)) / rows
    return Frame(
        x = area.x + (index % columns) * (width + gap),
        y = area.y + (index / columns) * (height + gap),
        width = width,
        height = height,
    )
}

/** This cell with the room its caption needs taken off the bottom. */
internal fun Frame.demoBox(): Frame = copy(height = height - CaptionHeight)

/** The caption under [cell]: centred, in body ink, in the room [demoBox] left it. */
internal fun captionUnder(cell: Frame, text: String, size: Float = 24f): TextElement = TextElement(
    frame = Frame(
        x = cell.x,
        y = cell.y + cell.height - CaptionHeight + 8f,
        width = cell.width,
        height = CaptionHeight - 8f,
    ),
    text = text,
    fontSize = size,
    color = ShowcaseDefaults.bodyColor,
    align = TextAlign.Center,
)

/**
 * A labelled demo tile: whatever [demo] draws in the cell's upper box, with
 * [label] written under it.
 *
 * Every "every X" grid in the deck is built out of this, so the tiles line up
 * across slides and a reader comparing two of them is comparing the demos rather
 * than two different layouts.
 */
internal fun tile(cell: Frame, label: String, demo: (Frame) -> Element): List<Element> =
    listOf(demo(cell.demoBox()), captionUnder(cell, label))
