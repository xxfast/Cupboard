package io.github.xxfast.cupboard.document

/** The line every type specimen on these slides is set in. */
private const val Specimen: String = "The quick brown fox jumps over the lazy dog"

/** A shorter one, for the tiles that have half a slide to say it in. */
private const val ShortSpecimen: String = "Sphinx of black quartz"

/** Everything a [TextElement] can say, one slide per property. */
internal fun textSlides(): List<Slide> = listOf(
    textSizesSlide(),
    textWeightsSlide(),
    textEmphasisSlide(),
    textSpacingSlide(),
    textAlignmentSlide(),
    textFontsSlide(),
    bulletListSlide(),
    numberedListSlide(),
    textColorsSlide(),
)

/**
 * The size ladder, laid out with a running y rather than on the grid: the whole
 * point is that the rows are different heights, so equal cells would hide it.
 */
private fun textSizesSlide(): Slide {
    val sizes: List<Float> = listOf(20f, 30f, 44f, 62f, 84f)
    var y: Float = ShowcaseStage.y

    // The short specimen, because the tallest line has to fit between the
    // margins: the ladder is about the sizes, not about how much text fits.
    val lines: List<Element> = sizes.map { size ->
        val height: Float = size * 1.5f
        val line = TextElement(
            frame = Frame(ShowcaseMargin, y, ShowcaseWidth, height),
            text = "${size.toInt()} pt  $ShortSpecimen",
            fontSize = size,
            color = ShowcaseDefaults.textColor,
        )
        y += height + 50f
        return@map line
    }

    return featureSlide(
        title = "Text Sizes",
        subtitle = "fontSize, in document units on a 1920 by 1080 slide",
        elements = lines,
        notes = "Look for: five lines, each visibly larger than the one above it, all " +
            "left-aligned on the same margin and none of them clipped by its box.",
    )
}

/** The weight ladder. Bold is a point on this scale, not a flag of its own. */
private fun textWeightsSlide(): Slide {
    val weights: List<Int> = listOf(300, RegularWeight, 500, 600, BoldWeight, 900)
    val lines: List<Element> = weights.mapIndexed { index, weight ->
        TextElement(
            frame = gridCell(columns = 1, rows = weights.size, index = index, gap = 12f),
            text = "$weight  $Specimen",
            fontSize = 44f,
            fontWeight = weight,
            color = ShowcaseDefaults.textColor,
        )
    }

    return featureSlide(
        title = "Text Weights",
        subtitle = "fontWeight, 300 to 900; anything from 600 up reads as bold",
        elements = lines,
        notes = "Look for: six lines getting heavier downwards. 300 should be visibly " +
            "lighter than 400, and 700 and 900 should differ from each other. If the " +
            "first three look identical the font has no light faces on this platform.",
    )
}

/** Italic, underline and strikethrough, alone and stacked. */
private fun textEmphasisSlide(): Slide {
    val cases: List<Pair<String, TextElement.() -> TextElement>> = listOf(
        "Plain" to { this },
        "Italic" to { copy(italic = true) },
        "Underline" to { copy(underline = true) },
        "Strikethrough" to { copy(strikethrough = true) },
        "All three" to { copy(italic = true, underline = true, strikethrough = true) },
    )

    val lines: List<Element> = cases.mapIndexed { index, (label, dress) ->
        TextElement(
            frame = gridCell(columns = 1, rows = cases.size, index = index, gap = 12f),
            text = "$label  $Specimen",
            fontSize = 46f,
            color = ShowcaseDefaults.textColor,
        ).dress()
    }

    return featureSlide(
        title = "Text Emphasis",
        subtitle = "italic, underline and strikethrough, alone and together",
        elements = lines,
        notes = "Look for: the second line slanted, the third with a rule under it, the " +
            "fourth struck through, and the last carrying all three at once. The rules " +
            "should run the width of the text and stop there, not the width of the box.",
    )
}

/**
 * Letter spacing down the left, line height down the right: two properties that
 * only read against a neighbour set the ordinary way.
 */
private fun textSpacingSlide(): Slide {
    val left = Frame(ShowcaseMargin, ShowcaseStage.y, 820f, ShowcaseStage.height)
    val right = Frame(ShowcaseMargin + 880f, ShowcaseStage.y, 820f, ShowcaseStage.height)
    val paragraph = "Three lines of body copy, set so the\n" +
        "space between them is the only thing\n" +
        "that changes from one box to the next."

    val spacings: List<Float> = listOf(-2f, 0f, 4f, 10f)
    val tracked: List<Element> = spacings.flatMapIndexed { index, spacing ->
        val cell: Frame =
            gridCell(left, columns = 1, rows = spacings.size, index = index, gap = 16f)
        listOf(
            TextElement(
                frame = cell.demoBox(),
                text = ShortSpecimen,
                fontSize = 40f,
                letterSpacing = spacing,
                color = ShowcaseDefaults.textColor,
            ),
            captionUnder(cell, "letterSpacing ${spacing.toInt()}", size = 22f),
        )
    }

    val heights: List<Float> = listOf(1f, 1.3f, 1.8f)
    val led: List<Element> = heights.flatMapIndexed { index, lineHeight ->
        val cell: Frame =
            gridCell(right, columns = 1, rows = heights.size, index = index, gap = 16f)
        listOf(
            TextElement(
                frame = cell.demoBox(),
                text = paragraph,
                fontSize = 26f,
                lineHeight = lineHeight,
                color = ShowcaseDefaults.textColor,
            ),
            captionUnder(cell, "lineHeight $lineHeight", size = 22f),
        )
    }

    return featureSlide(
        title = "Text Spacing",
        subtitle = "letterSpacing on the left, lineHeight on the right",
        elements = tracked + led,
        notes = "Look for: the left column's letters crowding at -2 and drifting apart at " +
            "10, on four lines of the same length otherwise. On the right, three " +
            "identical paragraphs whose lines sit closer or further apart. Nothing in " +
            "either column should overlap its caption.",
    )
}

/** Every [TextAlign], off `entries` so a new one shows up here by itself. */
private fun textAlignmentSlide(): Slide {
    val boxes: List<Element> = TextAlign.entries.flatMapIndexed { index, align ->
        val cell: Frame = gridCell(columns = 1, rows = TextAlign.entries.size, index = index)
        listOf(
            ShapeElement(
                frame = cell.demoBox(),
                fill = 0x14FFFFFF,
                strokeColor = 0x33FFFFFF,
                strokeWidth = 1f,
                cornerRadius = 12f,
            ),
            TextElement(
                frame = cell.demoBox(),
                text = "TextAlign.$align",
                fontSize = 46f,
                align = align,
                color = ShowcaseDefaults.textColor,
            ),
            captionUnder(cell, "the box behind shows where the line is free to sit"),
        )
    }

    return featureSlide(
        title = "Text Alignment",
        subtitle = "every TextAlign, each inside a box that shows its full width",
        elements = boxes,
        notes = "Look for: Start hard against the left edge of its box, Center on the box's " +
            "middle, End against the right edge. The three boxes are the same width, so " +
            "the three lines must not start in the same place.",
    )
}

/** Every [TextFont]. Generic families only, which is what makes them portable. */
private fun textFontsSlide(): Slide {
    val specimens: List<Element> = TextFont.entries.flatMapIndexed { index, font ->
        val cell: Frame = gridCell(columns = 1, rows = TextFont.entries.size, index = index)
        listOf(
            TextElement(
                frame = cell.demoBox(),
                text = "$font  $Specimen  0123456789",
                fontSize = 44f,
                fontFamily = font,
                color = ShowcaseDefaults.textColor,
            ),
            captionUnder(cell, "TextFont.$font"),
        )
    }

    return featureSlide(
        title = "Text Fonts",
        subtitle = "every TextFont: generic families, resolved by the platform",
        elements = specimens,
        notes = "Look for: three visibly different faces. Serif should have feet, " +
            "Monospace should have every glyph on the same advance (line the digits " +
            "up against the row above). If all three look the same the platform is " +
            "falling back to one family.",
    )
}

/** The nesting a bulleted list draws, spelled with leading tabs. */
private fun bulletListSlide(): Slide {
    val list = TextElement(
        frame = ShowcaseStage.copy(width = 1100f),
        // Nesting is the count of leading tabs, so the text stays one plain string.
        text = "Top level item\n" +
            "\tSecond level, one tab in\n" +
            "\t\tThird level, two tabs in\n" +
            "\tBack out to the second level\n" +
            "Top level again\n" +
            "\tAnd one more nested item",
        fontSize = 40f,
        lineHeight = 1.6f,
        listStyle = ListStyle.Bullet,
        color = ShowcaseDefaults.bodyColor,
    )

    return featureSlide(
        title = "Bulleted Lists",
        subtitle = "ListStyle.Bullet, nested by leading tabs",
        elements = listOf(list),
        notes = "Look for: six items. The glyph alternates between a filled bullet and a " +
            "hollow one as the list nests, and each level is indented further than its " +
            "parent. The third-level item starts over on the filled glyph.",
    )
}

/** The same nesting numbered: the marker cycles 1. then a. then i. as it goes in. */
private fun numberedListSlide(): Slide {
    val list = TextElement(
        frame = ShowcaseStage.copy(width = 1100f),
        text = "First top-level item\n" +
            "\tNested under the first\n" +
            "\tSecond nested item\n" +
            "\t\tDeeper still\n" +
            "Second top-level item\n" +
            "\tIts own nested list starts over",
        fontSize = 40f,
        lineHeight = 1.6f,
        listStyle = ListStyle.Numbered,
        color = ShowcaseDefaults.bodyColor,
    )

    return featureSlide(
        title = "Numbered Lists",
        subtitle = "ListStyle.Numbered: markers cycle 1. then a. then i. as they nest",
        elements = listOf(list),
        notes = "Look for: 1. and 2. at the top level, a. and b. under the first, i. under " +
            "those, and the nested list under item 2. starting back at a. rather than " +
            "carrying on from b.",
    )
}

/** Colour, which is the one place on these slides worth hardcoding. */
private fun textColorsSlide(): Slide {
    val swatches: List<Pair<String, Long>> = listOf(
        "textColor" to ShowcaseDefaults.textColor,
        "bodyColor" to ShowcaseDefaults.bodyColor,
        "accent" to ShowcaseDefaults.accent,
        "amber" to 0xFFFFE28A,
        "mint" to 0xFF5EF2C2,
        "half-alpha white" to 0x80FFFFFF,
    )

    val lines: List<Element> = swatches.flatMapIndexed { index, (label, color) ->
        val cell: Frame = gridCell(columns = 2, rows = 3, index = index)
        listOf(
            TextElement(
                frame = cell.demoBox(),
                text = ShortSpecimen,
                fontSize = 48f,
                color = color,
                align = TextAlign.Center,
            ),
            captionUnder(cell, label),
        )
    }

    return featureSlide(
        title = "Text Colours",
        subtitle = "color, packed ARGB; the last swatch is half transparent",
        elements = lines,
        notes = "Look for: six lines in six different inks, the last one visibly washed " +
            "out against the background rather than grey. The first three are the deck's " +
            "own defaults, so they change with the theme.",
    )
}
