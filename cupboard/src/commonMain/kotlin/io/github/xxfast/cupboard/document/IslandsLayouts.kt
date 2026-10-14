package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.document.TextFont.JetBrainsMono

/*
 * JetBrains Islands Light: the look of the "Kotlin to C#" deck, lifted out of
 * it and named after the JetBrains IDE theme it borrows from. See `Theme.kt`.
 *
 * The idea is the IDE's: a pale, softly graded backdrop, and on it white
 * "islands" (rounded 34, a faint drop shadow) that each hold one thing, a project
 * tree, an editor, a run pane, a page of notes. The file name sits in a tab chip
 * above its editor, and verdicts ("It works", "Requires Unsafe") are small white
 * chips with a green, amber or red border. Everything is set in JetBrains Mono.
 *
 * Every number here is the deck's, read out of its own file (keynote-parser; see
 * `docs/keynote/theme-chooser/JETBRAINS_ISLANDS_LIGHT.md` for the table): frames
 * in the 1920x1080 slide space, the island's radius and shadow, the chip strokes,
 * the gradient stops, the sizes. The deck set its title card on Keynote's own
 * master, in Helvetica; here it is JetBrains Mono like the rest, which is what
 * the deck was reaching for everywhere it touched.
 *
 * The islands themselves carry no role, so a slide inherits them and can't nudge
 * them: a layout decides where the islands are, a slide decides what is in them.
 * The project tree is a picture placeholder, because that is what it was in the
 * deck (screenshots of the IDE's own), and a code island is a code placeholder
 * inset by the island's 46 of padding.
 */

/** Islands Light's layouts: the deck's recurring slides, one layout each. */
internal fun jetBrainsIslandsLightLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(JetBrainsMono, Weight.Bold, 116f, lineHeight = 1.06f, color = White)
    val subtitle: TextLook = look(JetBrainsMono, Weight.Bold, 55f, lineHeight = 1.19f, color = Black)
    val author: TextLook = look(JetBrainsMono, Weight.Bold, 36f, lineHeight = 1.19f, color = Black)
    val footnote: TextLook = look(JetBrainsMono, Weight.Bold, 40f, lineHeight = 1.19f, color = Black)
    val statement: TextLook = look(
        JetBrainsMono,
        Weight.Medium,
        116f,
        lineHeight = 1.06f,
        align = TextAlign.Center,
        color = White,
    )
    val fact: TextLook = look(
        JetBrainsMono,
        Weight.Bold,
        150f,
        lineHeight = 1.06f,
        align = TextAlign.Center,
        color = KotlinPurple,
    )
    val heading: TextLook = look(JetBrainsMono, Weight.Bold, 42f, lineHeight = 1.19f, color = Black)
    val tab: TextLook = look(JetBrainsMono, Weight.Normal, 42f, lineHeight = 1.19f, color = Black)
    val notes: TextLook = look(JetBrainsMono, Weight.Normal, 41f, lineHeight = 1.32f, color = defaults.textColor)
    val caption: TextLook = look(JetBrainsMono, Weight.Normal, 30f, lineHeight = 1.19f, color = Black)

    /**
     * [name] is what a Magic Move tells one island from the next by, since a blank
     * panel says nothing: an editor island travels to the next layout's editor
     * island, and the tree's has nowhere to go and fades (see `magicMovePairs`).
     */
    fun island(id: String, frame: Frame, name: String? = null): ShapeElement = ShapeElement(
        id = id,
        frame = frame,
        name = name,
        cornerRadius = IslandRadius,
        fill = White,
        strokeColor = Transparent,
        strokeWidth = 0f,
        shadow = IslandShadow,
    )

    fun tabChip(id: String, frame: Frame, fill: Long = TabFill, stroke: Long = TabStroke): ShapeElement =
        ShapeElement(
            id = id,
            frame = frame,
            name = "tab",
            cornerRadius = TabRadius,
            fill = fill,
            strokeColor = stroke,
            strokeWidth = TabStrokeWidth,
        )

    fun tree(id: String, frame: Frame): ImageElement =
        ImageElement(id = id, frame = frame, placeholder = "Project tree", role = PlaceholderRole.Media)

    fun code(id: String, frame: Frame, language: String): CodeElement = CodeElement(
        id = id,
        frame = frame,
        code = "// code",
        language = language,
        fontSize = 42f,
        theme = defaults.codeTheme,
        role = PlaceholderRole.Code,
    )

    /** The Kotlin mark, in the gap the [project] heading leaves to its left. */
    val kotlinLogo: ShapeElement = ShapeElement(
        id = "islands-kotlin-logo",
        frame = Frame(115f, 156f, 38f, 38f),
        name = "kotlin logo",
        kind = ShapeKind.Path,
        cornerRadius = 0f,
        fill = KotlinLogoStart,
        gradient = ShapeGradient(start = KotlinLogoStart, end = KotlinLogoEnd, angle = 225f),
        strokeColor = Black,
        strokeWidth = 0f,
        path = ShapePath(
            data = "M112.484 112.484 L15.516 112.484 L15.516 15.516 L112.484 15.516 L64 64 Z",
            bounds = Frame(15.516f, 15.516f, 96.968f, 96.968f),
        ),
    )

    /** The Swift mark, in the same gap as the Kotlin one. */
    val swiftLogo: ShapeElement = ShapeElement(
        id = "islands-swift-logo",
        frame = Frame(115f, 158.02573f, 38f, 33.92856f),
        name = "swift logo",
        kind = ShapeKind.Path,
        cornerRadius = 0f,
        fill = SwiftLogoStart,
        gradient = ShapeGradient(start = SwiftLogoStart, end = SwiftLogoEnd, angle = 180f),
        strokeColor = Black,
        strokeWidth = 0f,
        path = ShapePath(
            data = "M22.1356 25.2715 C18.835 27.17 14.2967 27.3651 9.7308 25.4166 C6.0339 23.8505 2.9664 21.109 1 17.9767 C1.9439 18.7598 3.045 19.3865 4.2248 19.9344 C8.9401 22.135 13.6544 21.9843 16.972 19.94 C12.2526 16.3374 8.2363 11.6326 5.2475 7.7955 C4.618 7.1689 4.146 6.3857 3.6741 5.6808 C7.2923 8.9701 13.0343 13.1207 15.0789 14.296 C10.7535 9.7532 6.8991 4.1147 7.0561 4.271 C13.8993 11.163 20.2707 15.079 20.2707 15.079 C20.4815 15.1974 20.6442 15.2959 20.775 15.384 C20.9129 15.0347 21.0338 14.672 21.1357 14.296 C22.2368 10.3018 20.9787 5.759 18.2254 2 C24.5962 5.8375 28.3721 13.0425 26.7985 19.0732 C26.7575 19.2359 26.7129 19.3965 26.6648 19.5542 C29.8106 23.4703 29.0008 27.6884 28.6076 26.9053 C26.901 23.5801 23.7418 24.5969 22.1356 25.2715 Z",
            bounds = Frame(1f, 2f, 27.999992f, 24.999983f),
        ),
    )

    /** The [project] tree, tab chip and editor island of the two editor layouts, [logo] before its heading. */
    fun editor(
        id: String,
        title: String,
        project: String,
        language: String,
        file: String,
        background: SlideBackground,
        logo: ShapeElement? = null,
        tabFill: Long = TabFill,
        tabStroke: Long = TabStroke,
    ): Slide = layout(
            id,
            title,
            *listOfNotNull(
                logo,
                textBox("$id-project", Frame(177f, 150f, 420f, 60f), project, heading, role = null),
                island("$id-tree-island", Frame(89f, 249f, 518f, 698f), "tree island"),
                tree("$id-tree", Frame(112f, 272f, 472f, 652f)),
                tabChip("$id-tab", Frame(666f, 124f, 513f, 87f), tabFill, tabStroke),
                titleBox("$id-file", Frame(690f, 140f, 470f, 55f), file, tab),
                island("$id-editor-island", Frame(662f, 239f, 1186f, 717f), "editor island"),
                code("$id-code", Frame(708f, 285f, 1094f, 625f), language),
            ).toTypedArray(),
            background = background,
        )

    /** An editor beside its "Run" pane, the tree slid off to the left as the deck did it. */
    fun run(
        id: String,
        title: String,
        language: String,
        file: String,
        background: SlideBackground,
        tabFill: Long = TabFill,
        tabStroke: Long = TabStroke,
    ): Slide = layout(
            id,
            title,
            tabChip("$id-tab", Frame(41f, 132f, 438f, 91f), tabFill, tabStroke),
            titleBox("$id-file", Frame(65f, 150f, 400f, 55f), file, tab),
            island("$id-editor-island", Frame(60f, 249f, 1142f, 698f), "editor island"),
            code("$id-code", Frame(106f, 295f, 1050f, 606f), language),
            textBox("$id-run", Frame(1247f, 150f, 300f, 60f), "⏵ Run", heading, role = null),
            island("$id-run-island", Frame(1234f, 249f, 628f, 698f), "run island"),
            textBox("$id-output", Frame(1280f, 295f, 536f, 606f), "Output", notes),
            background = background,
        )

    listOf(
        layout(
            "islands-title",
            "Title",
            titleBox("islands-title-title", Frame(99f, 423.8f, 1722f, 142f), "Presentation Title", title),
            textBox("islands-title-subtitle", Frame(98.6f, 572.8f, 1722f, 142f), "Presentation Subtitle", subtitle),
            island("islands-title-island", Frame(75f, 893f, 1770f, 457f)),
            textBox("islands-title-author", Frame(239f, 967f, 1230f, 50f), "Author and Date", author),
            background = Pink,
        ),
        layout(
            "islands-statement",
            "Statement",
            textBox("islands-statement-body", Frame(97f, 468.5f, 1722f, 142f), "Statement", statement),
            island("islands-statement-island", Frame(79f, 896f, 1770f, 457f)),
            textBox("islands-statement-footnote", Frame(145f, 960f, 1629f, 50f), "Footnote", footnote),
            background = Pink,
        ),
        layout(
            "islands-big-fact",
            "Big Fact",
            textBox("islands-big-fact-body", Frame(98f, 480f, 1722f, 190f), "100%", fact),
            background = Mist,
        ),
        layout(
            "islands-notes",
            "Notes",
            titleBox("islands-notes-title", Frame(190f, 150f, 1500f, 60f), "Notes.md", heading),
            island("islands-notes-island", Frame(82f, 223f, 1311f, 757f)),
            textBox("islands-notes-body", Frame(128f, 269f, 1219f, 665f), "Notes", notes),
        ),
        editor("islands-kotlin", "Kotlin Editor", "Project", "kotlin", "File.kt", Lavender, kotlinLogo),
        run("islands-kotlin-run", "Kotlin Editor and Run", "kotlin", "File.kt", Lavender),
        editor(
            "islands-swift", "Swift Editor", "Project", "swift", "File.swift", Peach, swiftLogo,
            SwiftTabFill, SwiftTabStroke,
        ),
        run("islands-swift-run", "Swift Editor and Run", "swift", "File.swift", Peach, SwiftTabFill, SwiftTabStroke),
        editor("islands-csharp", "C# Editor", "C# Project", "csharp", "File.cs", Sky),
        run("islands-csharp-run", "C# Editor and Run", "csharp", "File.cs", Sky),
        layout(
            "islands-photos",
            "Photos",
            titleBox("islands-photos-title", Frame(190f, 150f, 1500f, 60f), "Photos.jpg", heading),
            photo("islands-photos-photo", Frame(96f, 255f, 842f, 629f)),
            photo("islands-photos-photo-2", Frame(971f, 255f, 856f, 629f)),
            island("islands-photos-island", Frame(92f, 916f, 1735f, 457f)),
            textBox("islands-photos-caption", Frame(207f, 955f, 1459f, 76f), "Caption", caption),
        ),
        Slide(id = "islands-blank", title = "Blank"),
    )
}

/**
 * The shape looks the deck built its slides out of, on top of the six every
 * theme gets: the island, the tab chip, and the three verdict chips.
 *
 * "Warning" takes the slot of the default amber one, so a deck on this theme
 * has one Warning rather than two.
 */
internal fun jetBrainsIslandsLightStyles(defaults: ElementDefaults): List<ObjectStyle> =
    defaultObjectStyles(defaults).filterNot { it.id == "warning" } + listOf(
        ObjectStyle(
            id = "island",
            name = "Island",
            fill = White,
            strokeColor = Transparent,
            strokeWidth = 0f,
            shadow = IslandShadow,
            cornerRadius = IslandRadius,
        ),
        ObjectStyle(
            id = "tab",
            name = "Tab",
            fill = TabFill,
            strokeColor = TabStroke,
            strokeWidth = TabStrokeWidth,
            cornerRadius = TabRadius,
        ),
        verdict("success", "Success", 0xFF1DB100),
        verdict("warning", "Warning", 0xFFFEAE00),
        verdict("error", "Error", 0xFFEE220C),
    )

/** A verdict chip: white, a 4-wide border in [stroke], rounded 20, a sharper shadow than an island's. */
private fun verdict(id: String, name: String, stroke: Long): ObjectStyle = ObjectStyle(
    id = id,
    name = name,
    fill = White,
    strokeColor = stroke,
    strokeWidth = 4f,
    shadow = ShapeShadow(color = 0x6B000000, blur = 11f, dy = 2f),
    cornerRadius = 20f,
)

/** The deck's backdrops: white into a tint, top-left to bottom-right. */
internal val IslandsGrey: SlideBackground = SlideBackground.Gradient(start = White, end = 0xFFD5D5D5, angle = 135f)
private val Lavender: SlideBackground = SlideBackground.Gradient(start = White, end = 0xFFABA3ED, angle = 135f)
private val Sky: SlideBackground = SlideBackground.Gradient(start = White, end = 0xFF56C1FF, angle = 135f)

/** Swift's: white into the orange of its logo. */
private val Peach: SlideBackground = SlideBackground.Gradient(start = White, end = 0xFFF8AE8C, angle = 135f)

/** Big Fact's: flat white in the deck, graded here like the rest so the card doesn't read as unthemed. */
private val Mist: SlideBackground = SlideBackground.Gradient(start = White, end = 0xFFECEEF2, angle = 135f)

/** Keynote's Dynamic Colour motion background, as the title and statement cards showed it. */
private val Pink: SlideBackground = SlideBackground.Color(0xFFDC4065)

/** An island: Keynote's drop shadow at 11% black, blur 22, 2 down. */
private val IslandShadow: ShapeShadow = ShapeShadow(color = 0x1B000000, blur = 22f, dy = 2f)

private const val IslandRadius: Float = 34f
private const val TabRadius: Float = 24f
private const val TabFill: Long = 0xFFE3EAFD
private const val TabStroke: Long = 0xFFA7BDF3
private const val SwiftTabFill: Long = 0xFFFDEBE1
private const val SwiftTabStroke: Long = 0xFFF3BDA7
private const val TabStrokeWidth: Float = 5f
private const val KotlinPurple: Long = 0xFF7C51E6
private const val KotlinLogoStart: Long = 0xFFE44857
private const val KotlinLogoEnd: Long = 0xFF7F52FF
private const val SwiftLogoStart: Long = 0xFFF88A36
private const val SwiftLogoEnd: Long = 0xFFFD2020
private const val White: Long = 0xFFFFFFFF
private const val Black: Long = 0xFF000000
private const val Transparent: Long = 0x00000000
