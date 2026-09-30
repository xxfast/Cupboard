package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.document.TextFont.Canela
import io.github.xxfast.cupboard.document.TextFont.CanelaDeck
import io.github.xxfast.cupboard.document.TextFont.CanelaText
import io.github.xxfast.cupboard.document.TextFont.Graphik
import io.github.xxfast.cupboard.document.TextFont.HelveticaNeue

/*
 * The same five themes' 4:3 layouts: what a deck created at Standard is laid
 * out by, see [Theme.presetLayouts]. `BasicLayouts.kt` explains how the numbers
 * were read and measured; these are the same, out of Keynote's Standard theme
 * files instead of its Wide ones.
 *
 * Keynote draws its Standard masters on a 1024x768 slide and Standard here is
 * 1440x1080, so every frame and every size is Keynote's times 1440/1024. Layout
 * titles, element ids and their order match the 16:9 set one for one, so a deck
 * moving between the two carries every placeholder to its counterpart.
 */

/** Basic White's 4:3 layouts, as its Keynote Standard theme file lays them out at 1024x768, scaled to 1440x1080. */
internal fun basicWhiteStandardLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(HelveticaNeue, Weight.Bold, 115.3f, lineHeight = 0.977f, tracking = -0.02f)
    val subtitle: TextLook = look(HelveticaNeue, Weight.Bold, 53.4f, lineHeight = 1.221f)
    val heading: TextLook = look(HelveticaNeue, Weight.Bold, 33.8f, lineHeight = 1.221f)
    val titleSmall: TextLook = look(HelveticaNeue, Weight.Bold, 84.4f, lineHeight = 0.977f, tracking = -0.02f)
    val body: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        42.2f,
        lineHeight = 1.074f,
        list = ListStyle.Bullet,
    )
    val section: TextLook = look(HelveticaNeue, Weight.Medium, 115.3f, lineHeight = 0.977f, tracking = -0.02f)
    val agenda: TextLook = look(HelveticaNeue, Weight.Normal, 53.4f, lineHeight = 1.074f, tracking = -0.01f)
    val statement: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        115.3f,
        lineHeight = 0.977f,
        tracking = -0.02f,
        align = TextAlign.Center,
    )
    val fact: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        247.5f,
        lineHeight = 0.977f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        53.4f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val quote: TextLook = look(HelveticaNeue, Weight.Medium, 84.4f, lineHeight = 1.099f, tracking = -0.02f)

    listOf(
        layout(
            "basic-title",
            "Title",
            titleBox("basic-title-title", Frame(83f, 424.1f, 1274.2f, 141f), "Presentation Title", title),
            textBox("basic-title-subtitle", Frame(82.6f, 569.3f, 1274.5f, 151.7f), "Presentation Subtitle", subtitle),
            textBox("basic-title-caption", Frame(82.2f, 965.5f, 1274.9f, 42f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo",
            "Title and Photo",
            photo("basic-title-and-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
            titleBox("basic-title-and-photo-title", Frame(83f, 792.6f, 1274f, 141f), "Presentation Title", title),
            textBox(
                "basic-title-and-photo-subtitle",
                Frame(82.6f, 937.6f, 1274.5f, 66.9f),
                "Presentation Subtitle",
                subtitle,
            ),
            textBox("basic-title-and-photo-caption", Frame(84f, 67.7f, 1273.1f, 41f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo-alt",
            "Title and Photo Alt",
            titleBox("basic-title-and-photo-alt-title", Frame(83.8f, 453.9f, 553.3f, 104f), "Slide Title", titleSmall),
            textBox(
                "basic-title-and-photo-alt-subtitle",
                Frame(82f, 557.5f, 555.1f, 438.8f),
                "Slide Subtitle",
                subtitle,
            ),
            photo("basic-title-and-photo-alt-photo", Frame(720f, 76.6f, 641.5f, 925f)),
        ),
        layout(
            "basic-title-and-bullets",
            "Title and Bullets",
            titleBox("basic-title-and-bullets-title", Frame(83.8f, 53.4f, 1273.3f, 102.2f), "Slide Title", titleSmall),
            textBox("basic-title-and-bullets-subtitle", Frame(82f, 160.1f, 1275.1f, 65.2f), "Slide Subtitle", subtitle),
            textBox("basic-title-and-bullets-body", Frame(79.6f, 332.7f, 1277.5f, 664.3f), "Slide bullet text", body),
        ),
        layout(
            "basic-bullets",
            "Bullets",
            textBox("basic-bullets-body", Frame(79.6f, 332.7f, 1277.5f, 664.3f), "Slide bullet text", body),
        ),
        layout(
            "basic-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox(
                "basic-title-bullets-and-photo-title",
                Frame(83.8f, 53.8f, 553.3f, 102.3f),
                "Slide Title",
                titleSmall,
            ),
            textBox(
                "basic-title-bullets-and-photo-subtitle",
                Frame(82f, 160.1f, 555.1f, 65.2f),
                "Slide Subtitle",
                subtitle,
            ),
            textBox(
                "basic-title-bullets-and-photo-body",
                Frame(79.6f, 390.7f, 557.5f, 608.3f),
                "Slide bullet text",
                body,
            ),
            photo("basic-title-bullets-and-photo-photo", Frame(720f, 77.3f, 641.2f, 925.3f)),
        ),
        layout(
            "basic-section",
            "Section",
            titleBox("basic-section-title", Frame(82.6f, 468.4f, 1274.5f, 141f), "Section Title", section),
        ),
        layout(
            "basic-title-only",
            "Title Only",
            titleBox("basic-title-only-title", Frame(83.8f, 53.4f, 1273.3f, 102.2f), "Slide Title", titleSmall),
            textBox("basic-title-only-subtitle", Frame(82f, 160.1f, 1275.1f, 65.2f), "Slide Subtitle", subtitle),
        ),
        layout(
            "basic-agenda",
            "Agenda",
            titleBox("basic-agenda-title", Frame(82.2f, 52.6f, 1274.9f, 103.5f), "Agenda Title", titleSmall),
            textBox("basic-agenda-subtitle", Frame(82.2f, 159.7f, 1274.9f, 65.2f), "Agenda Subtitle", subtitle),
            textBox("basic-agenda-body", Frame(82.2f, 331.3f, 1274.9f, 665.7f), "Agenda Topics", agenda),
        ),
        layout(
            "basic-statement",
            "Statement",
            textBox("basic-statement-body", Frame(81f, 468.4f, 1274.1f, 141f), "Statement", statement),
        ),
        layout(
            "basic-big-fact",
            "Big Fact",
            textBox("basic-big-fact-body", Frame(81f, 381.7f, 1274.1f, 303f), "100%", fact),
            textBox("basic-big-fact-caption", Frame(83.4f, 692.1f, 1274.1f, 64.3f), "Fact information", subtitleAlt),
        ),
        layout(
            "basic-quote",
            "Quote",
            textBox("basic-quote-body", Frame(100.4f, 487.9f, 1252.5f, 104f), "“Notable Quote”", quote),
            textBox("basic-quote-caption", Frame(141.8f, 716.6f, 1211f, 40.4f), "Attribution", heading),
        ),
        layout(
            "basic-photo-3-up",
            "Photo - 3 Up",
            photo("basic-photo-3-up-photo", Frame(77.3f, 77.6f, 617.3f, 925.7f)),
            photo("basic-photo-3-up-photo-2", Frame(744.6f, 77.3f, 618.8f, 438.8f)),
            photo("basic-photo-3-up-photo-3", Frame(744.6f, 564.6f, 618.8f, 438.8f)),
        ),
        layout(
            "basic-photo",
            "Photo",
            photo("basic-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
        ),
        Slide(id = "basic-blank", title = "Blank"),
    )
}

/** Basic Black's 4:3 layouts, as its Keynote Standard theme file lays them out at 1024x768, scaled to 1440x1080. */
internal fun basicBlackStandardLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(HelveticaNeue, Weight.Bold, 115.3f, lineHeight = 0.977f, tracking = -0.02f)
    val subtitle: TextLook = look(HelveticaNeue, Weight.Bold, 53.4f, lineHeight = 1.221f)
    val heading: TextLook = look(HelveticaNeue, Weight.Bold, 33.8f, lineHeight = 1.221f)
    val titleSmall: TextLook = look(HelveticaNeue, Weight.Bold, 84.4f, lineHeight = 0.977f, tracking = -0.02f)
    val body: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        42.2f,
        lineHeight = 1.074f,
        list = ListStyle.Bullet,
    )
    val section: TextLook = look(HelveticaNeue, Weight.Medium, 115.3f, lineHeight = 0.977f, tracking = -0.02f)
    val agenda: TextLook = look(HelveticaNeue, Weight.Normal, 53.4f, lineHeight = 1.074f, tracking = -0.01f)
    val statement: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        115.3f,
        lineHeight = 0.977f,
        tracking = -0.02f,
        align = TextAlign.Center,
    )
    val fact: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        247.5f,
        lineHeight = 0.977f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Bold,
        53.4f,
        lineHeight = 1.221f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val quote: TextLook = look(HelveticaNeue, Weight.Medium, 84.4f, lineHeight = 1.099f, tracking = -0.02f)

    listOf(
        layout(
            "basic-title",
            "Title",
            titleBox("basic-title-title", Frame(83f, 425f, 1274.2f, 141f), "Presentation Title", title),
            textBox("basic-title-subtitle", Frame(82.6f, 569.3f, 1274.5f, 150f), "Presentation Subtitle", subtitle),
            textBox("basic-title-caption", Frame(82.2f, 962.1f, 1274.9f, 42f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo",
            "Title and Photo",
            photo("basic-title-and-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
            titleBox("basic-title-and-photo-title", Frame(83f, 792.6f, 1274f, 141f), "Presentation Title", title),
            textBox(
                "basic-title-and-photo-subtitle",
                Frame(82.6f, 937.6f, 1274.5f, 69.8f),
                "Presentation Subtitle",
                subtitle,
            ),
            textBox("basic-title-and-photo-caption", Frame(82.2f, 67.7f, 1274.9f, 41f), "Author and Date", heading),
        ),
        layout(
            "basic-title-and-photo-alt",
            "Title and Photo Alt",
            titleBox("basic-title-and-photo-alt-title", Frame(83.8f, 453.9f, 553.3f, 104f), "Slide Title", titleSmall),
            textBox(
                "basic-title-and-photo-alt-subtitle",
                Frame(82f, 557.5f, 555.1f, 438.8f),
                "Slide Subtitle",
                subtitle,
            ),
            photo("basic-title-and-photo-alt-photo", Frame(720f, 77.3f, 641.2f, 925.3f)),
        ),
        layout(
            "basic-title-and-bullets",
            "Title and Bullets",
            titleBox("basic-title-and-bullets-title", Frame(83.8f, 53.4f, 1273.3f, 102.2f), "Slide Title", titleSmall),
            textBox("basic-title-and-bullets-subtitle", Frame(82f, 160.1f, 1275.1f, 65.2f), "Slide Subtitle", subtitle),
            textBox("basic-title-and-bullets-body", Frame(79.6f, 332.4f, 1277.5f, 664.3f), "Slide bullet text", body),
        ),
        layout(
            "basic-bullets",
            "Bullets",
            textBox("basic-bullets-body", Frame(79.6f, 332.7f, 1277.5f, 664.3f), "Slide bullet text", body),
        ),
        layout(
            "basic-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox(
                "basic-title-bullets-and-photo-title",
                Frame(83.8f, 53.8f, 553.3f, 102.3f),
                "Slide Title",
                titleSmall,
            ),
            textBox(
                "basic-title-bullets-and-photo-subtitle",
                Frame(82f, 160.1f, 555.1f, 65.2f),
                "Slide Subtitle",
                subtitle,
            ),
            textBox(
                "basic-title-bullets-and-photo-body",
                Frame(79.6f, 390.7f, 557.5f, 607.8f),
                "Slide bullet text",
                body,
            ),
            photo("basic-title-bullets-and-photo-photo", Frame(720f, 77.3f, 641.2f, 925.3f)),
        ),
        layout(
            "basic-section",
            "Section",
            titleBox("basic-section-title", Frame(82.6f, 468.4f, 1274.5f, 141f), "Section Title", section),
        ),
        layout(
            "basic-title-only",
            "Title Only",
            titleBox("basic-title-only-title", Frame(83.8f, 53.4f, 1273.3f, 102.2f), "Slide Title", titleSmall),
            textBox("basic-title-only-subtitle", Frame(82f, 160.1f, 1275.1f, 65.2f), "Slide Subtitle", subtitle),
        ),
        layout(
            "basic-agenda",
            "Agenda",
            titleBox("basic-agenda-title", Frame(82.2f, 52.6f, 1274.9f, 103.5f), "Agenda Title", titleSmall),
            textBox("basic-agenda-subtitle", Frame(82.2f, 159.7f, 1274.9f, 65.2f), "Agenda Subtitle", subtitle),
            textBox("basic-agenda-body", Frame(82.2f, 331.3f, 1274.9f, 665.7f), "Agenda Topics", agenda),
        ),
        layout(
            "basic-statement",
            "Statement",
            textBox("basic-statement-body", Frame(81f, 468.4f, 1274.1f, 141f), "Statement", statement),
        ),
        layout(
            "basic-big-fact",
            "Big Fact",
            textBox("basic-big-fact-body", Frame(81f, 383.2f, 1274.1f, 303f), "100%", fact),
            textBox("basic-big-fact-caption", Frame(82.4f, 692.1f, 1274.1f, 64.3f), "Fact information", subtitleAlt),
        ),
        layout(
            "basic-quote",
            "Quote",
            textBox("basic-quote-body", Frame(100.4f, 487.9f, 1252.5f, 104f), "“Notable Quote”", quote),
            textBox("basic-quote-caption", Frame(140f, 716.6f, 1212.8f, 40.4f), "Attribution", heading),
        ),
        layout(
            "basic-photo-3-up",
            "Photo - 3 Up",
            photo("basic-photo-3-up-photo", Frame(77.3f, 77.3f, 616.5f, 926f)),
            photo("basic-photo-3-up-photo-2", Frame(745.3f, 77.3f, 618.8f, 438.8f)),
            photo("basic-photo-3-up-photo-3", Frame(745.3f, 564.8f, 618.8f, 438.8f)),
        ),
        layout(
            "basic-photo",
            "Photo",
            photo("basic-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
        ),
        Slide(id = "basic-blank", title = "Blank"),
    )
}

/** Classic White's 4:3 layouts, as its Keynote Standard theme file lays them out at 1024x768, scaled to 1440x1080. */
internal fun classicWhiteStandardLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(
        Canela,
        Weight.Bold,
        115.3f,
        lineHeight = 1.208f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitle: TextLook = look(
        Graphik,
        Weight.SemiBold,
        53.4f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val heading: TextLook = look(
        Graphik,
        Weight.Medium,
        28.1f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val titleAlt: TextLook = look(
        Canela,
        Weight.Bold,
        115.3f,
        lineHeight = 1.208f,
        tracking = -0.01f,
        align = TextAlign.Center,
        color = inverse,
    )
    val subtitleAlt: TextLook = look(
        Graphik,
        Weight.SemiBold,
        53.4f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
        color = inverse,
    )
    val headingAlt: TextLook = look(
        Graphik,
        Weight.Medium,
        28.1f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
        color = inverse,
    )
    val titleSmall: TextLook = look(
        Canela,
        Weight.Bold,
        81.6f,
        lineHeight = 1.057f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val subtitleSmall: TextLook = look(
        Graphik,
        Weight.SemiBold,
        45f,
        lineHeight = 1.33f,
        tracking = -0.01f,
        align = TextAlign.Center,
    )
    val body: TextLook = look(CanelaText, Weight.Normal, 42.2f, lineHeight = 1.359f, list = ListStyle.Bullet)
    val agenda: TextLook = look(CanelaDeck, Weight.Normal, 61.9f, lineHeight = 1.51f, tracking = -0.02f)
    val statement: TextLook = look(
        Canela,
        Weight.Normal,
        115.3f,
        lineHeight = 1.208f,
        align = TextAlign.Center,
    )
    val fact: TextLook = look(Canela, Weight.Normal, 222.2f, lineHeight = 1.208f, align = TextAlign.Center)
    val quote: TextLook = look(Canela, Weight.Bold, 81.6f, lineHeight = 1.208f, align = TextAlign.Center)

    listOf(
        layout(
            "classic-title",
            "Title",
            titleBox("classic-title-title", Frame(84.2f, 430.1f, 1271.2f, 175f), "Presentation Title", title),
            textBox("classic-title-subtitle", Frame(84.2f, 595.4f, 1271.2f, 150.7f), "Presentation Subtitle", subtitle),
            textBox("classic-title-caption", Frame(84.4f, 936f, 1271.2f, 37.3f), "Author and Date", heading),
        ),
        layout(
            "classic-title-and-photo",
            "Title and Photo",
            photo("classic-title-and-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
            titleBox(
                "classic-title-and-photo-title",
                Frame(84.2f, 430.1f, 1271.2f, 175f),
                "Presentation Title",
                titleAlt,
            ),
            textBox(
                "classic-title-and-photo-subtitle",
                Frame(84.2f, 596.4f, 1271.2f, 149.9f),
                "Presentation Subtitle",
                subtitleAlt,
            ),
            textBox("classic-title-and-photo-caption", Frame(84f, 934.8f, 1271.2f, 38f), "Author and Date", headingAlt),
        ),
        layout(
            "classic-title-and-photo-alt",
            "Title and Photo Alt",
            titleBox(
                "classic-title-and-photo-alt-title",
                Frame(82.6f, 417.3f, 548.9f, 124f),
                "Slide Title",
                titleSmall,
            ),
            textBox(
                "classic-title-and-photo-alt-subtitle",
                Frame(84f, 533.2f, 548.9f, 423f),
                "Slide Subtitle",
                subtitleSmall,
            ),
            photo("classic-title-and-photo-alt-photo", Frame(720f, 84.4f, 635.6f, 877.5f)),
        ),
        layout(
            "classic-title-and-bullets",
            "Title and Bullets",
            titleBox("classic-title-and-bullets-title", Frame(83.8f, 47.8f, 1271.2f, 120f), "Slide Title", titleSmall),
            textBox(
                "classic-title-and-bullets-subtitle",
                Frame(84.4f, 170.5f, 1271.2f, 60.4f),
                "Slide Subtitle",
                subtitleSmall,
            ),
            textBox("classic-title-and-bullets-body", Frame(81.6f, 336.5f, 1274f, 659.2f), "Slide bullet text", body),
        ),
        layout(
            "classic-bullets",
            "Bullets",
            textBox("classic-bullets-body", Frame(81.6f, 336.5f, 1274f, 659.2f), "Slide bullet text", body),
        ),
        layout(
            "classic-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox(
                "classic-title-bullets-and-photo-title",
                Frame(83.2f, 137.7f, 548.4f, 124f),
                "Slide Title",
                titleSmall,
            ),
            textBox(
                "classic-title-bullets-and-photo-subtitle",
                Frame(84.6f, 254.9f, 548.4f, 60f),
                "Slide Subtitle",
                subtitleSmall,
            ),
            textBox(
                "classic-title-bullets-and-photo-body",
                Frame(81.6f, 383.3f, 551.2f, 572.1f),
                "Slide bullet text",
                body,
            ),
            photo("classic-title-bullets-and-photo-photo", Frame(720f, 84.4f, 635.6f, 877.5f)),
        ),
        layout(
            "classic-section",
            "Section",
            titleBox("classic-section-title", Frame(82.8f, 429.2f, 1271.2f, 175f), "Section Title", title),
        ),
        layout(
            "classic-title-only",
            "Title Only",
            titleBox("classic-title-only-title", Frame(83.8f, 47.4f, 1271.2f, 124f), "Slide Title", titleSmall),
            textBox(
                "classic-title-only-subtitle",
                Frame(84.4f, 170.5f, 1271.2f, 60.4f),
                "Slide Subtitle",
                subtitleSmall,
            ),
        ),
        layout(
            "classic-agenda",
            "Agenda",
            titleBox("classic-agenda-title", Frame(84f, 47.4f, 1271.2f, 120f), "Agenda Title", titleSmall),
            textBox("classic-agenda-subtitle", Frame(84.4f, 170.5f, 1271.2f, 60.4f), "Agenda Subtitle", subtitleSmall),
            textBox("classic-agenda-body", Frame(84.2f, 336.3f, 1271.5f, 659.3f), "Agenda Topics", agenda),
        ),
        layout(
            "classic-statement",
            "Statement",
            textBox("classic-statement-body", Frame(84.2f, 428.8f, 1271.2f, 175f), "Statement", statement),
        ),
        layout(
            "classic-big-fact",
            "Big Fact",
            textBox("classic-big-fact-body", Frame(82f, 332.2f, 1271.2f, 336f), "100%", fact),
            textBox("classic-big-fact-caption", Frame(83.4f, 626f, 1271.2f, 61f), "Fact information", subtitleSmall),
        ),
        layout(
            "classic-quote",
            "Quote",
            textBox("classic-quote-body", Frame(84.4f, 442.8f, 1271.2f, 124f), "“Notable Quote”", quote),
            textBox("classic-quote-caption", Frame(84f, 800.2f, 1271.2f, 60f), "Attribution", subtitleSmall),
        ),
        layout(
            "classic-photo-3-up",
            "Photo - 3 Up",
            photo("classic-photo-3-up-photo", Frame(84.9f, 84.4f, 618.2f, 877.9f)),
            photo("classic-photo-3-up-photo-2", Frame(737.7f, 84.4f, 618.8f, 421.9f)),
            photo("classic-photo-3-up-photo-3", Frame(737.7f, 540.6f, 618.8f, 421.9f)),
        ),
        layout(
            "classic-photo",
            "Photo",
            photo("classic-photo-photo", Frame(84.4f, 84.4f, 1271.2f, 877.5f)),
        ),
        Slide(id = "classic-blank", title = "Blank"),
    )
}

/** White's 4:3 layouts, as its Keynote Standard theme file lays them out at 1024x768, scaled to 1440x1080. */
internal fun whiteStandardLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        112.5f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val subtitle: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        52f,
        lineHeight = 1.193f,
        align = TextAlign.Center,
    )
    val titleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        84.4f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val body: TextLook = look(HelveticaNeue, Weight.Normal, 45f, lineHeight = 1.193f, list = ListStyle.Bullet)
    val bodySmall: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        39.4f,
        lineHeight = 1.193f,
        list = ListStyle.Bullet,
    )
    val quote: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        47.8f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val attribution: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        33.8f,
        lineHeight = 1.198f,
        align = TextAlign.Center,
        italic = true,
    )

    listOf(
        layout(
            "plain-title",
            "Title",
            titleBox("plain-title-title", Frame(145.8f, 404f, 1147.5f, 138f), "Title Text", title),
            textBox("plain-title-subtitle", Frame(146.6f, 562.4f, 1147.5f, 115.5f), "Subtitle", subtitle),
        ),
        layout(
            "plain-photo-horizontal",
            "Photo - Horizontal",
            titleBox("plain-photo-horizontal-title", Frame(145.8f, 759.3f, 1147.5f, 138f), "Title Text", title),
            textBox("plain-photo-horizontal-subtitle", Frame(146.6f, 907f, 1147.5f, 115.4f), "Subtitle", subtitle),
            photo("plain-photo-horizontal-photo", Frame(180f, 74.5f, 1080f, 653.9f)),
        ),
        layout(
            "plain-title-centre",
            "Title - Centre",
            titleBox("plain-title-centre-title", Frame(145.8f, 471f, 1147.5f, 138f), "Title Text", title),
        ),
        layout(
            "plain-photo-vertical",
            "Photo - Vertical",
            titleBox("plain-photo-vertical-title", Frame(111.3f, 403.1f, 579.4f, 104f), "Title Text", titleAlt),
            textBox("plain-photo-vertical-subtitle", Frame(111.1f, 528f, 579.4f, 445.1f), "Subtitle", subtitle),
            photo("plain-photo-vertical-photo", Frame(743.9f, 70.3f, 590.6f, 909.8f)),
        ),
        layout(
            "plain-title-top",
            "Title - Top",
            titleBox("plain-title-top-title", Frame(110.7f, 78.4f, 1217.8f, 138f), "Title Text", title),
        ),
        layout(
            "plain-title-and-bullets",
            "Title and Bullets",
            titleBox("plain-title-and-bullets-title", Frame(110.7f, 78.4f, 1217.8f, 138f), "Title Text", title),
            textBox("plain-title-and-bullets-body", Frame(110.7f, 609f, 1218.2f, 54f), "Bullet text", body),
        ),
        layout(
            "plain-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("plain-title-bullets-and-photo-title", Frame(110.7f, 78.4f, 1217.8f, 138f), "Title Text", title),
            textBox("plain-title-bullets-and-photo-body", Frame(106.9f, 612.7f, 583.6f, 48f), "Bullet text", bodySmall),
            photo("plain-title-bullets-and-photo-photo", Frame(743.9f, 286.9f, 590.6f, 696.1f)),
        ),
        layout(
            "plain-bullets",
            "Bullets",
            textBox("plain-bullets-body", Frame(110.7f, 513.4f, 1218.2f, 54f), "Bullet text", body),
        ),
        layout(
            "plain-photo-3-up",
            "Photo - 3 Up",
            photo("plain-photo-3-up-photo", Frame(105.5f, 98.4f, 590.6f, 883.1f)),
            photo("plain-photo-3-up-photo-2", Frame(743.9f, 98.4f, 590.6f, 417.7f)),
            photo("plain-photo-3-up-photo-3", Frame(743.9f, 563.9f, 590.6f, 417.7f)),
        ),
        layout(
            "plain-quote",
            "Quote",
            textBox("plain-quote-body", Frame(146.2f, 477.7f, 1147.5f, 59f), "“Type a quote here.”", quote),
            textBox("plain-quote-caption", Frame(143.2f, 709.6f, 1147.5f, 39.9f), "–Johnny Appleseed", attribution),
        ),
        layout(
            "plain-photo",
            "Photo",
            photo("plain-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
        ),
        Slide(id = "plain-blank", title = "Blank"),
    )
}

/** Black's 4:3 layouts, as its Keynote Standard theme file lays them out at 1024x768, scaled to 1440x1080. */
internal fun blackStandardLayouts(defaults: ElementDefaults): List<Slide> = with(LayoutInk(defaults)) {
    val title: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        112.5f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val subtitle: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        52f,
        lineHeight = 1.193f,
        align = TextAlign.Center,
    )
    val titleAlt: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        84.4f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val body: TextLook = look(HelveticaNeue, Weight.Normal, 45f, lineHeight = 1.193f, list = ListStyle.Bullet)
    val bodySmall: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        39.4f,
        lineHeight = 1.193f,
        list = ListStyle.Bullet,
    )
    val quote: TextLook = look(
        HelveticaNeue,
        Weight.Medium,
        47.8f,
        lineHeight = 1.221f,
        align = TextAlign.Center,
    )
    val attribution: TextLook = look(
        HelveticaNeue,
        Weight.Normal,
        33.8f,
        lineHeight = 1.198f,
        align = TextAlign.Center,
        italic = true,
    )

    listOf(
        layout(
            "plain-title",
            "Title",
            titleBox("plain-title-title", Frame(145.8f, 404f, 1147.5f, 138f), "Title Text", title),
            textBox("plain-title-subtitle", Frame(146.6f, 561.1f, 1147.5f, 115.3f), "Subtitle", subtitle),
        ),
        layout(
            "plain-photo-horizontal",
            "Photo - Horizontal",
            titleBox("plain-photo-horizontal-title", Frame(145.8f, 753.1f, 1147.5f, 138f), "Title Text", title),
            textBox("plain-photo-horizontal-subtitle", Frame(146.6f, 907f, 1147.5f, 115.4f), "Subtitle", subtitle),
            photo("plain-photo-horizontal-photo", Frame(179.7f, 75.2f, 1080.4f, 653.9f)),
        ),
        layout(
            "plain-title-centre",
            "Title - Centre",
            titleBox("plain-title-centre-title", Frame(145.8f, 471f, 1147.5f, 138f), "Title Text", title),
        ),
        layout(
            "plain-photo-vertical",
            "Photo - Vertical",
            titleBox("plain-photo-vertical-title", Frame(111.3f, 403.1f, 579.4f, 104f), "Title Text", titleAlt),
            textBox("plain-photo-vertical-subtitle", Frame(111.1f, 528f, 579.4f, 445.1f), "Subtitle", subtitle),
            photo("plain-photo-vertical-photo", Frame(743.9f, 70.7f, 590.6f, 909.8f)),
        ),
        layout(
            "plain-title-top",
            "Title - Top",
            titleBox("plain-title-top-title", Frame(110.7f, 78.4f, 1217.8f, 138f), "Title Text", title),
        ),
        layout(
            "plain-title-and-bullets",
            "Title and Bullets",
            titleBox("plain-title-and-bullets-title", Frame(110.7f, 78.4f, 1217.8f, 138f), "Title Text", title),
            textBox("plain-title-and-bullets-body", Frame(110.7f, 607.1f, 1218.2f, 54f), "Bullet text", body),
        ),
        layout(
            "plain-title-bullets-and-photo",
            "Title, Bullets and Photo",
            titleBox("plain-title-bullets-and-photo-title", Frame(110.7f, 78.4f, 1217.8f, 138f), "Title Text", title),
            textBox("plain-title-bullets-and-photo-body", Frame(106.9f, 610.9f, 583.6f, 48f), "Bullet text", bodySmall),
            photo("plain-title-bullets-and-photo-photo", Frame(743.9f, 286.9f, 590.6f, 696.1f)),
        ),
        layout(
            "plain-bullets",
            "Bullets",
            textBox("plain-bullets-body", Frame(110.7f, 511.6f, 1218.2f, 54f), "Bullet text", body),
        ),
        layout(
            "plain-photo-3-up",
            "Photo - 3 Up",
            photo("plain-photo-3-up-photo", Frame(105.5f, 70.3f, 590.6f, 911.2f)),
            photo("plain-photo-3-up-photo-2", Frame(745.3f, 70.3f, 590.6f, 431.7f)),
            photo("plain-photo-3-up-photo-3", Frame(745.3f, 549.8f, 590.6f, 431.7f)),
        ),
        layout(
            "plain-quote",
            "Quote",
            textBox("plain-quote-body", Frame(146.2f, 481.7f, 1147.5f, 59f), "“Type a quote here.”", quote),
            textBox("plain-quote-caption", Frame(143.2f, 709.6f, 1147.5f, 39.9f), "–Johnny Appleseed", attribution),
        ),
        layout(
            "plain-photo",
            "Photo",
            photo("plain-photo-photo", Frame(0f, 0f, 1440f, 1080f)),
        ),
        Slide(id = "plain-blank", title = "Blank"),
    )
}
