package io.github.xxfast.cupboard.document

/** The facts that belong to a slide rather than to anything on it. */
internal fun slideSlides(layouts: List<Slide>): List<Slide> =
    listOf(
        backgroundColorSlide(),
        backgroundGradientSlide(),
        slideNumberSlide(),
        skippedSlide(),
    ) + layoutSlides(layouts)

/** [SlideBackground.Color]: one flat colour behind this slide and no other. */
private fun backgroundColorSlide(): Slide = featureSlide(
    title = "Slide Background: Colour",
    subtitle = "SlideBackground.Color, set on this slide alone",
    elements = listOf(
        shapeElement(
            ShapeKind.Rectangle,
            Frame(ShowcaseMargin, ShowcaseStage.y + 140f, ShowcaseWidth, 400f),
            ShowcaseDefaults,
        ).copy(label = "0xFF11333F", labelSize = 48f, cornerRadius = 20f),
    ),
    notes = "Look for: a flat teal field behind the whole slide, edge to edge, with none " +
        "of the deck's usual gradient showing through at the corners. The slide before " +
        "and after this one are on the deck's own background, so this one should stand " +
        "out in the navigator thumbnails too.",
).copy(background = SlideBackground.Color(0xFF11333F))

/** [SlideBackground.Gradient]: two stops along a line, in CSS degrees. */
private fun backgroundGradientSlide(): Slide = featureSlide(
    title = "Slide Background: Gradient",
    subtitle = "SlideBackground.Gradient, two stops at 140 degrees",
    elements = listOf(
        shapeElement(
            ShapeKind.Rectangle,
            Frame(ShowcaseMargin, ShowcaseStage.y + 140f, ShowcaseWidth, 400f),
            ShowcaseDefaults,
        ).copy(label = "0xFF3A1C71 to 0xFF00897B", labelSize = 44f, cornerRadius = 20f),
    ),
    notes = "Look for: the background running purple in the top-left to teal in the " +
        "bottom-right, smoothly, with no banding. 0 degrees would point the first stop " +
        "straight up, and this is 140, so the ramp runs down and to the right.",
).copy(
    background = SlideBackground.Gradient(start = 0xFF3A1C71, end = 0xFF00897B, angle = 140f),
)

/** [Slide.showsSlideNumber], Keynote's per-slide switch. */
private fun slideNumberSlide(): Slide = featureSlide(
    title = "Slide Numbers",
    subtitle = "showsSlideNumber, which is per slide rather than per deck",
    elements = listOf(
        TextElement(
            frame = Frame(ShowcaseMargin, ShowcaseStage.y + 200f, ShowcaseWidth, 200f),
            text = "This slide asks for its number.\nThe ones around it do not.",
            fontSize = 48f,
            lineHeight = 1.5f,
            color = ShowcaseDefaults.bodyColor,
        ),
    ),
    notes = "Look for: a number in the bottom corner of this slide and of no other. It " +
        "counts the slides that will actually be shown, so the skipped slide two along " +
        "from here does not add to it: compare this number against the navigator's row " +
        "count and expect them to differ by one.",
).copy(showsSlideNumber = true)

/** [Slide.skipped]: in the deck, out of the presentation. */
private fun skippedSlide(): Slide = featureSlide(
    title = "Skipped Slide",
    subtitle = "skipped, so play walks straight past it",
    elements = listOf(
        TextElement(
            frame = Frame(ShowcaseMargin, ShowcaseStage.y + 180f, ShowcaseWidth, 260f),
            text = "If you are reading this in play mode,\nskipping is broken.",
            fontSize = 60f,
            fontWeight = BoldWeight,
            lineHeight = 1.4f,
            align = TextAlign.Center,
            color = 0xFFF5C518,
        ),
    ),
    notes = "Look for: in the navigator this row is dimmed and carries no presentation " +
        "number, while still being editable like any other slide.\n\n" +
        "In play: this slide must never appear. Advancing off the slide before it should " +
        "land on the slide after it. Seeing this slide at all is the failure.",
).copy(skipped = true)

/** One slide per default layout, built the way New Slide builds one. */
private fun layoutSlides(layouts: List<Slide>): List<Slide> {
    val title: Slide = layouts.first { it.id == "layout-title" }
    val titleBody: Slide = layouts.first { it.id == "layout-title-body" }
    val code: Slide = layouts.first { it.id == "layout-code" }
    val blank: Slide = layouts.first { it.id == "layout-blank" }

    return listOf(
        Slide(title = "Layout: Title", depth = 1)
            .instantiating(title)
            .filling(
                title = "Layout: Title",
                body = "Both lines are placeholder instances, centred by the layout",
            )
            .copy(
                notes = "Look for: a centred heading and a centred subtitle, both sitting " +
                    "where the Title layout puts them rather than where this deck's own " +
                    "header goes. These are real placeholder instances: open the layout " +
                    "in the navigator, move the title, and this slide should follow when " +
                    "the layout is reapplied.",
            ),
        Slide(title = "Layout: Title and Body", depth = 1)
            .instantiating(titleBody)
            .filling(
                title = "Layout: Title & Body",
                body = "A bulleted body placeholder\n" +
                    "\tNested one level in\n" +
                    "Back out to the top level\n" +
                    "The layout decides the frame, the ink and the list style",
            )
            .copy(
                notes = "Look for: a left-aligned heading in the layout's title frame, and " +
                    "a bulleted body under it. The body placeholder carries " +
                    "ListStyle.Bullet from the layout, so the bullets are the layout's " +
                    "doing rather than this slide's.",
            ),
        Slide(title = "Layout: Code", depth = 1)
            .instantiating(code)
            .filling(
                title = "Layout: Code",
                body = """
                    fun main() {
                        val deck = showcaseDocument()
                        println(deck.slides.size)
                    }
                """.trimIndent(),
            )
            .copy(
                notes = "Look for: a heading and a code placeholder with its gutter on, " +
                    "in the deck's default code theme. The block's frame is the layout's, " +
                    "so it lines up with the body placeholder on the previous slide.",
            ),
        Slide(title = "Layout: Blank", depth = 1)
            .instantiating(blank)
            .copy(
                elements = listOf(
                    TextElement(
                        frame = Frame(ShowcaseMargin, 440f, ShowcaseWidth, 200f),
                        text = "The Blank layout has no placeholders.\n" +
                            "This text box is the slide's own.",
                        fontSize = 46f,
                        lineHeight = 1.5f,
                        align = TextAlign.Center,
                        color = ShowcaseDefaults.bodyColor,
                    ),
                ),
                notes = "Look for: nothing but the one text box. A slide on Blank starts " +
                    "empty, which is what makes it the layout to build something " +
                    "bespoke on. The slide is still on a layout: the inspector should " +
                    "say Blank rather than None.",
            ),
    )
}

/**
 * This slide's placeholder instances filled in: a title, and a body or a code
 * block depending on which role the layout gave it.
 */
private fun Slide.filling(title: String, body: String): Slide = copy(
    elements = elements.map { element ->
        when {
            element is TextElement && element.role == PlaceholderRole.Title ->
                element.copy(text = title)

            element is TextElement && element.role == PlaceholderRole.Body ->
                element.copy(text = body)

            element is CodeElement && element.role == PlaceholderRole.Code ->
                element.copy(code = body)

            else -> element
        }
    },
)

/**
 * The theme sampler: one slide that leans on everything [Document.defaults]
 * decides, so switching themes visibly changes all of it at once.
 *
 * A slide cannot carry a theme of its own, so this is the only way to demonstrate
 * one: change the deck's theme in the Document inspector and come back here.
 */
internal fun themeSlides(): List<Slide> = listOf(
    featureSlide(
        title = "Theme Sampler",
        subtitle = "everything the deck's defaults decide, on one slide",
        elements = listOf(
            TextElement(
                frame = Frame(ShowcaseMargin, ShowcaseStage.y, 820f, 90f),
                text = "Heading ink",
                fontSize = 66f,
                fontWeight = BoldWeight,
                color = ShowcaseDefaults.textColor,
                fontFamily = ShowcaseDefaults.textFont,
            ),
            TextElement(
                frame = Frame(ShowcaseMargin, ShowcaseStage.y + 110f, 820f, 220f),
                text = "Body ink, set at the deck's own size and line height.\n" +
                    "Two lines, so the leading is visible.",
                fontSize = ShowcaseDefaults.textSize,
                fontWeight = ShowcaseDefaults.textWeight,
                lineHeight = ShowcaseDefaults.textLineHeight,
                align = ShowcaseDefaults.textAlign,
                color = ShowcaseDefaults.bodyColor,
                fontFamily = ShowcaseDefaults.textFont,
            ),
            shapeElement(
                ShapeKind.Rectangle,
                Frame(ShowcaseMargin, ShowcaseStage.y + 360f, 380f, 180f),
                ShowcaseDefaults,
            ).copy(label = "shapeFill", labelSize = 30f, cornerRadius = 16f),
            shapeElement(
                ShapeKind.Rectangle,
                Frame(ShowcaseMargin + 430f, ShowcaseStage.y + 360f, 380f, 180f),
                ShowcaseDefaults,
            ).copy(
                fill = ShowcaseDefaults.accent,
                strokeColor = ShowcaseDefaults.accent,
                label = "accent",
                labelSize = 30f,
                cornerRadius = 16f,
            ),
            codeBoxElement(
                Frame(ShowcaseMargin + 900f, ShowcaseStage.y, 800f, 540f),
                ShowcaseDefaults,
            ).copy(fontSize = 28f, showLineNumbers = true),
            captionUnder(
                Frame(ShowcaseMargin + 900f, ShowcaseStage.y + 540f, 800f, CaptionHeight),
                "the deck's codeTheme",
            ),
        ),
        notes = "Look for: heading ink, body ink, the default shape fill, the accent, and " +
            "a code block in the deck's own syntax palette, all in one frame.\n\n" +
            "A slide cannot carry a theme, so switch the deck's theme in the Document " +
            "inspector and come back here. What follows a theme is the deck's background, " +
            "the layout slides' placeholder instances, and the six object styles. The " +
            "elements on this slide were written in the starting theme's colours and " +
            "stay in them, which is exactly the contrast to look at.",
    ),
)
