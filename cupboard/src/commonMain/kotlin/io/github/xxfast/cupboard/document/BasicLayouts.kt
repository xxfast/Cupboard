package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.document.TextFont.Canela
import io.github.xxfast.cupboard.document.TextFont.CanelaDeck
import io.github.xxfast.cupboard.document.TextFont.CanelaText
import io.github.xxfast.cupboard.document.TextFont.Graphik
import io.github.xxfast.cupboard.document.TextFont.HelveticaNeue

/*
 * Keynote's own layouts for its five Basic themes, one function a theme: what
 * Basic White, Basic Black, Classic White, White and Black are laid out by. See
 * `Theme.kt`.
 *
 * Every number here is Keynote's, read out of the theme files themselves
 * (`docs/keynote/theme-chooser/BASIC_THEMES.md` has the table they came from):
 * the frames in the 1920x1080 slide space, the faces, sizes, tracking and line
 * spacing of each paragraph style, and the photo frames as their masks show them.
 * The looks are named after Keynote's paragraph styles, so a look here and a row
 * there are the same thing.
 *
 * Two translations, both measured rather than guessed. Keynote sets text inside a
 * 4pt inset and pins some of it to the bottom or the middle of a tall box; a text
 * box here has neither, so each frame is where Keynote's first line of ink
 * actually lands, rendered side by side against Keynote's own export. Keynote's
 * line spacing is a multiple of the face's natural line height (ascent, descent
 * and leading); [TextElement.lineHeight] is a multiple of the size, so each look
 * carries the product.
 *
 * Layouts that carry more than one placeholder of a role (a subtitle above the
 * bullets, the author line, three photos) are matched by their place among that
 * role, see [PlaceholderSlot]. So text reads title, subtitle, body, then the
 * small line under it, in every theme: switching between them carries a subtitle
 * to the subtitle. A photo the text sits on comes first, so it draws behind.
 *
 * Keynote's two Live Video layouts are left out: there is no live video here.
 */

/** A paragraph style as Keynote names one: a face, a size and how it is set. */
internal data class TextLook(
    val font: TextFont,
    val weight: Int,
    val size: Float,
    val lineHeight: Float,
    val tracking: Float,
    val align: TextAlign,
    val list: ListStyle,
    val italic: Boolean,
    val color: Long,
)

/** The weights the Basic themes set in, as [TextElement.fontWeight] points. */
internal object Weight {
    const val Normal: Int = 400
    const val Medium: Int = 500
    const val SemiBold: Int = 600
    const val Bold: Int = 700
}

/**
 * What a theme's layout function writes with: its looks, its placeholders and its
 * layouts, all in [defaults]' ink. [inverse] is the other of black and white, for
 * the looks Keynote sets over a photo.
 */
internal class LayoutInk(private val defaults: ElementDefaults) {
    val inverse: Long = if (defaults.textColor == Black) White else Black

    fun look(
        font: TextFont,
        weight: Int,
        size: Float,
        lineHeight: Float,
        tracking: Float = 0f,
        align: TextAlign = TextAlign.Start,
        list: ListStyle = ListStyle.None,
        italic: Boolean = false,
        color: Long = defaults.textColor,
    ): TextLook = TextLook(font, weight, size, lineHeight, tracking, align, list, italic, color)

    /** A body placeholder; Keynote's tracking is a fraction of the size, ours is points. */
    fun textBox(
        id: String,
        frame: Frame,
        text: String,
        look: TextLook,
        role: PlaceholderRole = PlaceholderRole.Body,
    ): TextElement = TextElement(
        id = id,
        frame = frame,
        text = text,
        fontSize = look.size,
        fontWeight = look.weight,
        lineHeight = look.lineHeight,
        letterSpacing = look.tracking * look.size,
        color = look.color,
        align = look.align,
        fontFamily = look.font,
        italic = look.italic,
        listStyle = look.list,
        role = role,
    )

    fun titleBox(id: String, frame: Frame, text: String, look: TextLook): TextElement =
        textBox(id, frame, text, look, PlaceholderRole.Title)

    fun photo(id: String, frame: Frame): ImageElement =
        ImageElement(id = id, frame = frame, placeholder = "Photo", role = PlaceholderRole.Media)

    fun layout(id: String, title: String, vararg elements: Element): Slide =
        Slide(id = id, title = title, elements = elements.toList())
}

private const val Black: Long = 0xFF000000
private const val White: Long = 0xFFFFFFFF

/** Basic White's layouts, as its Keynote theme file lays them out. */
internal fun basicWhiteLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(HelveticaNeue, Weight.Bold, 116f, lineHeight = 0.977f, tracking = -0.02f)
    val subtitle: TextLook = look(HelveticaNeue, Weight.Bold, 55f, lineHeight = 1.221f)
    val heading: TextLook = look(HelveticaNeue, Weight.Bold, 36f, lineHeight = 1.221f)
    val titleSmall: TextLook = look(HelveticaNeue, Weight.Bold, 85f, lineHeight = 0.977f, tracking = -0.02f)
    val body: TextLook = look(HelveticaNeue, Weight.Normal, 48f, lineHeight = 1.074f, list = ListStyle.Bullet)
    val section: TextLook = look(HelveticaNeue, Weight.Medium, 116f, lineHeight = 0.977f, tracking = -0.02f)
    val agenda: TextLook = look(HelveticaNeue, Weight.Normal, 55f, lineHeight = 1.193f, tracking = -0.01f)
    val statement: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        116f,
        lineHeight = 0.977f,
        tracking = -0.02f,
        align = TextAlign.Center,
    )
    val fact: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        250f,
        lineHeight = 0.977f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        55f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val quote: TextLook = look(HelveticaNeue, Weight.Medium, 85f, lineHeight = 1.099f, tracking = -0.02f)

    listOf(
        layout(
            "basic-title",
            "Title",
            titleBox("basic-title-title", Frame(99f, 423.8f, 1722f, 142f), "Presentation Title", title),
            textBox("basic-title-subtitle", Frame(98.6f, 572.8f, 1722f, 142f), "Presentation Subtitle", subtitle),
            textBox("basic-title-caption", Frame(98.6f, 935.8f, 1722f, 44.2f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo",
            "Title and Photo",
            photo("basic-title-and-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
            titleBox("basic-title-and-photo-title", Frame(99f, 783f, 1722f, 142f), "Presentation Title", title),
            textBox(
                "basic-title-and-photo-subtitle",
                Frame(99f, 918.2f, 1722f, 79.9f),
                "Presentation Subtitle",
                subtitle,
            ),
            textBox("basic-title-and-photo-caption", Frame(99.1f, 89.1f, 1721.8f, 44.2f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo-alt",
            "Title and Photo Alt",
            titleBox("basic-title-and-photo-alt-title", Frame(98f, 456f, 763f, 104f), "Slide Title", titleSmall),
            textBox("basic-title-and-photo-alt-subtitle", Frame(99f, 560f, 762f, 416f), "Slide Subtitle", subtitle),
            photo("basic-title-and-photo-alt-photo", Frame(960f, 100f, 860f, 880f)),
        ),
        layout(
            "basic-title-and-bullets",
            "Title and Bullets",
            titleBox("basic-title-and-bullets-title", Frame(98f, 87f, 1723f, 106.8f), "Slide Title", titleSmall),
            textBox("basic-title-and-bullets-subtitle", Frame(98f, 190.8f, 1723f, 65.6f), "Slide Subtitle", subtitle),
            textBox("basic-title-and-bullets-body", Frame(99f, 337.5f, 1722f, 643.1f), "Slide bullet text", body),
        ),
        layout(
            "basic-bullets",
            "Bullets",
            textBox("basic-bullets-body", Frame(99f, 337.5f, 1722f, 643.1f), "Slide bullet text", body),
        ),
        layout(
            "basic-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("basic-title-bullets-and-photo-title", Frame(98f, 87f, 763f, 107f), "Slide Title", titleSmall),
            textBox(
                "basic-title-bullets-and-photo-subtitle",
                Frame(98f, 190.8f, 763f, 65.6f),
                "Slide Subtitle",
                subtitle,
            ),
            textBox("basic-title-bullets-and-photo-body", Frame(99f, 337.5f, 762f, 643.1f), "Slide bullet text", body),
            photo("basic-title-bullets-and-photo-photo", Frame(960f, 99.5f, 859.6f, 881f)),
        ),
        layout(
            "basic-section",
            "Section",
            titleBox("basic-section-title", Frame(99f, 469f, 1722f, 142f), "Section Title", section),
        ),
        layout(
            "basic-title-only",
            "Title Only",
            titleBox("basic-title-only-title", Frame(98f, 87f, 1723f, 107f), "Slide Title", titleSmall),
            textBox("basic-title-only-subtitle", Frame(98f, 190.8f, 1723f, 65.6f), "Slide Subtitle", subtitle),
        ),
        layout(
            "basic-agenda",
            "Agenda",
            titleBox("basic-agenda-title", Frame(99f, 87f, 1722f, 107f), "Agenda Title", titleSmall),
            textBox("basic-agenda-subtitle", Frame(98f, 190.8f, 1723f, 65.6f), "Agenda Subtitle", subtitle),
            textBox("basic-agenda-body", Frame(99f, 337.5f, 1722f, 643.1f), "Agenda Topics", agenda),
        ),
        layout(
            "basic-statement",
            "Statement",
            textBox("basic-statement-body", Frame(97f, 468.5f, 1722f, 142f), "Statement", statement),
        ),
        layout(
            "basic-big-fact",
            "Big Fact",
            textBox("basic-big-fact-body", Frame(98f, 348.7f, 1722f, 306f), "100%", fact),
            textBox("basic-big-fact-caption", Frame(99f, 653.6f, 1722f, 66.6f), "Fact information", subtitleAlt),
        ),
        layout(
            "basic-quote",
            "Quote",
            textBox("basic-quote-body", Frame(155.1f, 391f, 1622.8f, 296.1f), "“Notable Quote”", quote),
            textBox("basic-quote-caption", Frame(195.3f, 842.6f, 1582.6f, 44.2f), "Attribution", heading),
        ),
        layout(
            "basic-photo-3-up",
            "Photo - 3 Up",
            photo("basic-photo-3-up-photo", Frame(95.4f, 100f, 1115.6f, 885.3f)),
            photo("basic-photo-3-up-photo-2", Frame(1241f, 100f, 584.5f, 426f)),
            photo("basic-photo-3-up-photo-3", Frame(1241f, 559f, 584.5f, 426.4f)),
        ),
        layout(
            "basic-photo",
            "Photo",
            photo("basic-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
        ),
        Slide(id = "basic-blank", title = "Blank"),
    )
}

/** Basic Black's layouts, as its Keynote theme file lays them out. */
internal fun basicBlackLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(HelveticaNeue, Weight.Bold, 116f, lineHeight = 0.977f, tracking = -0.02f)
    val subtitle: TextLook = look(HelveticaNeue, Weight.Bold, 55f, lineHeight = 1.221f)
    val heading: TextLook = look(HelveticaNeue, Weight.Bold, 36f, lineHeight = 1.221f)
    val titleSmall: TextLook = look(HelveticaNeue, Weight.Bold, 85f, lineHeight = 0.977f, tracking = -0.02f)
    val body: TextLook = look(HelveticaNeue, Weight.Normal, 48f, lineHeight = 1.074f, list = ListStyle.Bullet)
    val section: TextLook = look(HelveticaNeue, Weight.Medium, 116f, lineHeight = 0.977f, tracking = -0.02f)
    val agenda: TextLook = look(HelveticaNeue, Weight.Normal, 55f, lineHeight = 1.193f, tracking = -0.01f)
    val statement: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        116f,
        lineHeight = 0.977f,
        tracking = -0.02f,
        align = TextAlign.Center,
    )
    val fact: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        250f,
        lineHeight = 0.977f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        55f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val quote: TextLook = look(HelveticaNeue, Weight.Medium, 85f, lineHeight = 1.099f, tracking = -0.02f)

    listOf(
        layout(
            "basic-title",
            "Title",
            titleBox("basic-title-title", Frame(99f, 423.8f, 1722f, 142f), "Presentation Title", title),
            textBox("basic-title-subtitle", Frame(99f, 570.7f, 1722f, 142f), "Presentation Subtitle", subtitle),
            textBox("basic-title-caption", Frame(99f, 934.2f, 1722f, 44f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo",
            "Title and Photo",
            photo("basic-title-and-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
            titleBox("basic-title-and-photo-title", Frame(99f, 783f, 1722f, 142f), "Presentation Title", title),
            textBox(
                "basic-title-and-photo-subtitle",
                Frame(99f, 918.2f, 1722f, 82.1f),
                "Presentation Subtitle",
                subtitle,
            ),
            textBox("basic-title-and-photo-caption", Frame(99.1f, 89.1f, 1721.8f, 44.2f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo-alt",
            "Title and Photo Alt",
            titleBox("basic-title-and-photo-alt-title", Frame(99f, 456f, 762f, 104f), "Slide Title", titleSmall),
            textBox("basic-title-and-photo-alt-subtitle", Frame(99f, 560f, 762f, 415.8f), "Slide Subtitle", subtitle),
            photo("basic-title-and-photo-alt-photo", Frame(960f, 100f, 860f, 881f)),
        ),
        layout(
            "basic-title-and-bullets",
            "Title and Bullets",
            titleBox("basic-title-and-bullets-title", Frame(99f, 77f, 1722f, 106.8f), "Slide Title", titleSmall),
            textBox("basic-title-and-bullets-subtitle", Frame(98f, 180.8f, 1723f, 65.6f), "Slide Subtitle", subtitle),
            textBox("basic-title-and-bullets-body", Frame(99f, 337.5f, 1722f, 643.1f), "Slide bullet text", body),
        ),
        layout(
            "basic-bullets",
            "Bullets",
            textBox("basic-bullets-body", Frame(99f, 337.5f, 1722f, 643.1f), "Slide bullet text", body),
        ),
        layout(
            "basic-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("basic-title-bullets-and-photo-title", Frame(99f, 77f, 762f, 107f), "Slide Title", titleSmall),
            textBox(
                "basic-title-bullets-and-photo-subtitle",
                Frame(98f, 180.8f, 763f, 65.6f),
                "Slide Subtitle",
                subtitle,
            ),
            textBox("basic-title-bullets-and-photo-body", Frame(99f, 337.5f, 762f, 643.1f), "Slide bullet text", body),
            photo("basic-title-bullets-and-photo-photo", Frame(960f, 99.5f, 860f, 881f)),
        ),
        layout(
            "basic-section",
            "Section",
            titleBox("basic-section-title", Frame(99f, 469f, 1722f, 142f), "Section Title", section),
        ),
        layout(
            "basic-title-only",
            "Title Only",
            titleBox("basic-title-only-title", Frame(99f, 77f, 1722f, 107f), "Slide Title", titleSmall),
            textBox("basic-title-only-subtitle", Frame(98f, 180.8f, 1723f, 65.6f), "Slide Subtitle", subtitle),
        ),
        layout(
            "basic-agenda",
            "Agenda",
            titleBox("basic-agenda-title", Frame(99f, 77f, 1722f, 107f), "Agenda Title", titleSmall),
            textBox("basic-agenda-subtitle", Frame(98f, 180.8f, 1723f, 65.6f), "Agenda Subtitle", subtitle),
            textBox("basic-agenda-body", Frame(99f, 337.5f, 1722f, 643.1f), "Agenda Topics", agenda),
        ),
        layout(
            "basic-statement",
            "Statement",
            textBox("basic-statement-body", Frame(97f, 468.5f, 1722f, 142f), "Statement", statement),
        ),
        layout(
            "basic-big-fact",
            "Big Fact",
            textBox("basic-big-fact-body", Frame(98f, 346.6f, 1722f, 306f), "100%", fact),
            textBox("basic-big-fact-caption", Frame(99f, 653.6f, 1722f, 66.6f), "Fact information", subtitleAlt),
        ),
        layout(
            "basic-quote",
            "Quote",
            textBox("basic-quote-body", Frame(156.1f, 488f, 1621.8f, 104f), "“Notable Quote”", quote),
            textBox("basic-quote-caption", Frame(199.3f, 842.6f, 1578.6f, 44.2f), "Attribution", heading),
        ),
        layout(
            "basic-photo-3-up",
            "Photo - 3 Up",
            photo("basic-photo-3-up-photo", Frame(95.4f, 100f, 1115.6f, 884.1f)),
            photo("basic-photo-3-up-photo-2", Frame(1245f, 100f, 580f, 426f)),
            photo("basic-photo-3-up-photo-3", Frame(1245f, 557.9f, 580f, 426f)),
        ),
        layout(
            "basic-photo",
            "Photo",
            photo("basic-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
        ),
        Slide(id = "basic-blank", title = "Blank"),
    )
}

/** Classic White's layouts, as its Keynote theme file lays them out. */
internal fun classicWhiteLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(
        Canela,
        Weight.Bold,
        128f,
        lineHeight = 1.208f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitle: TextLook = look(
        Graphik,
        Weight.SemiBold,
        60f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val heading: TextLook = look(
        Graphik,
        Weight.Medium,
        30f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val titleAlt: TextLook = look(
        Canela,
        Weight.Bold,
        128f,
        lineHeight = 1.208f,
        tracking = -0.01f,
        align = TextAlign.Center,
        color = inverse,
    )
    val subtitleAlt: TextLook = look(
        Graphik,
        Weight.SemiBold,
        60f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
        color = inverse,
    )
    val headingAlt: TextLook = look(
        Graphik,
        Weight.Medium,
        30f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
        color = inverse,
    )
    val titleSmall: TextLook = look(
        Canela,
        Weight.Bold,
        84f,
        lineHeight = 1.208f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitleSmall: TextLook = look(
        Graphik,
        Weight.SemiBold,
        44f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val body: TextLook = look(CanelaText, Weight.Normal, 44f, lineHeight = 1.359f, list = ListStyle.Bullet)
    val section: TextLook = look(Canela, Weight.Bold, 128f, lineHeight = 1.208f, align = TextAlign.Center)
    val agenda: TextLook = look(CanelaDeck, Weight.Normal, 68f, lineHeight = 1.51f, tracking = -0.02f)
    val statement: TextLook = look(Canela, Weight.Normal, 128f, lineHeight = 1.208f, align = TextAlign.Center)
    val fact: TextLook = look(Canela, Weight.Bold, 224f, lineHeight = 1.208f, align = TextAlign.Center)
    val quote: TextLook = look(Canela, Weight.Bold, 84f, lineHeight = 1.208f, align = TextAlign.Center)

    listOf(
        layout(
            "classic-title",
            "Title",
            titleBox("classic-title-title", Frame(99f, 419f, 1720f, 194f), "Presentation Title", title),
            textBox("classic-title-subtitle", Frame(100f, 598.9f, 1720f, 170.2f), "Presentation Subtitle", subtitle),
            textBox("classic-title-caption", Frame(99f, 947.8f, 1720f, 39.7f), "Author and Date", heading),
        ),
        layout(
            "classic-title-and-photo",
            "Title and Photo",
            photo("classic-title-and-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
            titleBox("classic-title-and-photo-title", Frame(99f, 419f, 1720f, 194f), "Presentation Title", titleAlt),
            textBox(
                "classic-title-and-photo-subtitle",
                Frame(99f, 601f, 1720f, 168.3f),
                "Presentation Subtitle",
                subtitleAlt,
            ),
            textBox("classic-title-and-photo-caption", Frame(99f, 948f, 1720f, 39.7f), "Author and Date", headingAlt),
        ),
        layout(
            "classic-title-and-photo-alt",
            "Title and Photo Alt",
            titleBox("classic-title-and-photo-alt-title", Frame(98.7f, 431f, 760.3f, 127f), "Slide Title", titleSmall),
            textBox(
                "classic-title-and-photo-alt-subtitle",
                Frame(99f, 554.5f, 760f, 420.5f),
                "Slide Subtitle",
                subtitleSmall,
            ),
            photo("classic-title-and-photo-alt-photo", Frame(960f, 100f, 860f, 880f)),
        ),
        layout(
            "classic-title-and-bullets",
            "Title and Bullets",
            titleBox("classic-title-and-bullets-title", Frame(99f, 65f, 1720f, 128f), "Slide Title", titleSmall),
            textBox(
                "classic-title-and-bullets-subtitle",
                Frame(99f, 189.8f, 1720f, 59.6f),
                "Slide Subtitle",
                subtitleSmall,
            ),
            textBox("classic-title-and-bullets-body", Frame(97f, 319f, 1723.2f, 661f), "Slide bullet text", body),
        ),
        layout(
            "classic-bullets",
            "Bullets",
            textBox("classic-bullets-body", Frame(97f, 319f, 1723f, 661.3f), "Slide bullet text", body),
        ),
        layout(
            "classic-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("classic-title-bullets-and-photo-title", Frame(99f, 62f, 760f, 121f), "Slide Title", titleSmall),
            textBox(
                "classic-title-bullets-and-photo-subtitle",
                Frame(99f, 191f, 760.3f, 58.6f),
                "Slide Subtitle",
                subtitleSmall,
            ),
            textBox(
                "classic-title-bullets-and-photo-body",
                Frame(97f, 319.8f, 763.3f, 653.2f),
                "Slide bullet text",
                body,
            ),
            photo("classic-title-bullets-and-photo-photo", Frame(960.1f, 100f, 860f, 880f)),
        ),
        layout(
            "classic-section",
            "Section",
            titleBox("classic-section-title", Frame(100f, 418.3f, 1720f, 194f), "Section Title", section),
        ),
        layout(
            "classic-title-only",
            "Title Only",
            titleBox("classic-title-only-title", Frame(99f, 65f, 1720f, 128f), "Slide Title", titleSmall),
            textBox("classic-title-only-subtitle", Frame(99f, 189.8f, 1720f, 59.6f), "Slide Subtitle", subtitleSmall),
        ),
        layout(
            "classic-agenda",
            "Agenda",
            titleBox("classic-agenda-title", Frame(99f, 65f, 1720f, 128f), "Agenda Title", titleSmall),
            textBox("classic-agenda-subtitle", Frame(99f, 190f, 1720f, 59.6f), "Agenda Subtitle", subtitleSmall),
            textBox("classic-agenda-body", Frame(100f, 320f, 1720f, 652.3f), "Agenda Topics", agenda),
        ),
        layout(
            "classic-statement",
            "Statement",
            textBox("classic-statement-body", Frame(100f, 419f, 1720f, 194f), "Statement", statement),
        ),
        layout(
            "classic-big-fact",
            "Big Fact",
            textBox("classic-big-fact-body", Frame(99f, 333.8f, 1720f, 339f), "100%", fact),
            textBox("classic-big-fact-caption", Frame(100f, 669.3f, 1720f, 58.6f), "Fact information", subtitleSmall),
        ),
        layout(
            "classic-quote",
            "Quote",
            textBox("classic-quote-body", Frame(100f, 440f, 1720f, 127f), "“Notable Quote”", quote),
            textBox("classic-quote-caption", Frame(99f, 877f, 1720f, 59f), "Attribution", subtitleSmall),
        ),
        layout(
            "classic-photo-3-up",
            "Photo - 3 Up",
            photo("classic-photo-3-up-photo", Frame(100.4f, 100f, 1110.6f, 879.4f)),
            photo("classic-photo-3-up-photo-2", Frame(1239.8f, 100f, 580f, 426f)),
            photo("classic-photo-3-up-photo-3", Frame(1239.8f, 553.5f, 580f, 426f)),
        ),
        layout(
            "classic-photo",
            "Photo",
            photo("classic-photo-photo", Frame(100f, 100f, 1720f, 880f)),
        ),
        Slide(id = "classic-blank", title = "Blank"),
    )
}

/** White's layouts, as its Keynote theme file lays them out. */
internal fun whiteLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        112f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val subtitle: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        54f,
        lineHeight = 1.193f,
        align = TextAlign.Center,
    )
    val titleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        84f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val body: TextLook = look(HelveticaNeue, Weight.Normal, 48f, lineHeight = 1.193f, list = ListStyle.Bullet)
    val bodySmall: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        38f,
        lineHeight = 1.193f,
        list = ListStyle.Bullet,
    )
    val quote: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        48f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val attribution: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        32f,
        lineHeight = 1.198f,
        align = TextAlign.Center,
        italic = true,
    )

    listOf(
        layout(
            "plain-title",
            "Title",
            titleBox("plain-title-title", Frame(144f, 407f, 1632f, 137f), "Title Text", title),
            textBox("plain-title-subtitle", Frame(143f, 561f, 1632f, 117f), "Subtitle", subtitle),
        ),
        layout(
            "plain-photo-horizontal",
            "Photo - Horizontal",
            titleBox("plain-photo-horizontal-title", Frame(54f, 767f, 1812f, 137f), "Title Text", title),
            textBox("plain-photo-horizontal-subtitle", Frame(53f, 905f, 1812f, 117f), "Subtitle", subtitle),
            photo("plain-photo-horizontal-photo", Frame(246.1f, 53f, 1428f, 688f)),
        ),
        layout(
            "plain-title-centre",
            "Title - Centre",
            titleBox("plain-title-centre-title", Frame(144f, 471f, 1632f, 137f), "Title Text", title),
        ),
        layout(
            "plain-photo-vertical",
            "Photo - Vertical",
            titleBox("plain-photo-vertical-title", Frame(134f, 406f, 797f, 103f), "Title Text", titleAlt),
            textBox("plain-photo-vertical-subtitle", Frame(134f, 518f, 797f, 443f), "Subtitle", subtitle),
            photo("plain-photo-vertical-photo", Frame(1036.7f, 75f, 750f, 903f)),
        ),
        layout(
            "plain-title-top",
            "Title - Top",
            titleBox("plain-title-top-title", Frame(137f, 49f, 1646f, 137f), "Title Text", title),
        ),
        layout(
            "plain-title-and-bullets",
            "Title and Bullets",
            titleBox("plain-title-and-bullets-title", Frame(137f, 49f, 1646f, 137f), "Title Text", title),
            textBox("plain-title-and-bullets-body", Frame(137f, 585f, 1646f, 58f), "Bullet text", body),
        ),
        layout(
            "plain-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("plain-title-bullets-and-photo-title", Frame(137f, 49f, 1646f, 137f), "Title Text", title),
            textBox("plain-title-bullets-and-photo-body", Frame(133f, 591f, 801f, 46f), "Bullet text", bodySmall),
            photo("plain-title-bullets-and-photo-photo", Frame(1037f, 248f, 750f, 732f)),
        ),
        layout(
            "plain-bullets",
            "Bullets",
            textBox("plain-bullets-body", Frame(137f, 511f, 1646f, 58f), "Bullet text", body),
        ),
        layout(
            "plain-photo-3-up",
            "Photo - 3 Up",
            photo("plain-photo-3-up-photo", Frame(95f, 89f, 1116f, 903f)),
            photo("plain-photo-3-up-photo-2", Frame(1241f, 89f, 583f, 437f)),
            photo("plain-photo-3-up-photo-3", Frame(1241f, 555f, 583f, 437f)),
        ),
        layout(
            "plain-quote",
            "Quote",
            textBox("plain-quote-body", Frame(191f, 481f, 1537f, 59f), "“Type a quote here.”", quote),
            textBox("plain-quote-caption", Frame(188f, 709f, 1537f, 38f), "–Johnny Appleseed", attribution),
        ),
        layout(
            "plain-photo",
            "Photo",
            photo("plain-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
        ),
        Slide(id = "plain-blank", title = "Blank"),
    )
}

/** Black's layouts, as its Keynote theme file lays them out. */
internal fun blackLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        112f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val subtitle: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        54f,
        lineHeight = 1.193f,
        align = TextAlign.Center,
    )
    val titleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        84f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val body: TextLook = look(HelveticaNeue, Weight.Normal, 48f, lineHeight = 1.193f, list = ListStyle.Bullet)
    val bodySmall: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        38f,
        lineHeight = 1.193f,
        list = ListStyle.Bullet,
    )
    val quote: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        48f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val attribution: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        32f,
        lineHeight = 1.198f,
        align = TextAlign.Center,
        italic = true,
    )

    listOf(
        layout(
            "plain-title",
            "Title",
            titleBox("plain-title-title", Frame(144f, 407f, 1632f, 137f), "Title Text", title),
            textBox("plain-title-subtitle", Frame(143f, 561f, 1632f, 117f), "Subtitle", subtitle),
        ),
        layout(
            "plain-photo-horizontal",
            "Photo - Horizontal",
            titleBox("plain-photo-horizontal-title", Frame(54f, 759f, 1812f, 137f), "Title Text", title),
            textBox("plain-photo-horizontal-subtitle", Frame(53f, 905f, 1812f, 117f), "Subtitle", subtitle),
            photo("plain-photo-horizontal-photo", Frame(246.1f, 53f, 1428f, 688f)),
        ),
        layout(
            "plain-title-centre",
            "Title - Centre",
            titleBox("plain-title-centre-title", Frame(144f, 471f, 1632f, 137f), "Title Text", title),
        ),
        layout(
            "plain-photo-vertical",
            "Photo - Vertical",
            titleBox("plain-photo-vertical-title", Frame(134f, 406f, 797f, 103f), "Title Text", titleAlt),
            textBox("plain-photo-vertical-subtitle", Frame(134f, 518f, 797f, 443f), "Subtitle", subtitle),
            photo("plain-photo-vertical-photo", Frame(1037f, 75f, 750f, 903f)),
        ),
        layout(
            "plain-title-top",
            "Title - Top",
            titleBox("plain-title-top-title", Frame(137f, 49f, 1646f, 137f), "Title Text", title),
        ),
        layout(
            "plain-title-and-bullets",
            "Title and Bullets",
            titleBox("plain-title-and-bullets-title", Frame(137f, 49f, 1646f, 137f), "Title Text", title),
            textBox("plain-title-and-bullets-body", Frame(137f, 585f, 1646f, 58f), "Bullet text", body),
        ),
        layout(
            "plain-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("plain-title-bullets-and-photo-title", Frame(137f, 49f, 1646f, 137f), "Title Text", title),
            textBox("plain-title-bullets-and-photo-body", Frame(133f, 591f, 801f, 46f), "Bullet text", bodySmall),
            photo("plain-title-bullets-and-photo-photo", Frame(1037f, 248f, 750f, 732f)),
        ),
        layout(
            "plain-bullets",
            "Bullets",
            textBox("plain-bullets-body", Frame(137f, 511f, 1646f, 58f), "Bullet text", body),
        ),
        layout(
            "plain-photo-3-up",
            "Photo - 3 Up",
            photo("plain-photo-3-up-photo", Frame(95f, 75f, 1116f, 903f)),
            photo("plain-photo-3-up-photo-2", Frame(1241f, 75f, 583f, 437f)),
            photo("plain-photo-3-up-photo-3", Frame(1241f, 541f, 583f, 437f)),
        ),
        layout(
            "plain-quote",
            "Quote",
            textBox("plain-quote-body", Frame(191f, 481f, 1537f, 59f), "“Type a quote here.”", quote),
            textBox("plain-quote-caption", Frame(188f, 709f, 1537f, 38f), "–Johnny Appleseed", attribution),
        ),
        layout(
            "plain-photo",
            "Photo",
            photo("plain-photo-photo", Frame(0f, 0f, 1920f, 1080f)),
        ),
        Slide(id = "plain-blank", title = "Blank"),
    )
}
