package io.github.xxfast.cupboard.document

/** The whole of the image, in the normalised units a mask is measured in. */
private val WholeImage = Frame(0f, 0f, 1f, 1f)

/** Images, sound and movies. No bundled bytes, so every one of these is a frame. */
internal fun mediaSlides(): List<Slide> = listOf(
    imagesSlide(),
    imageMasksSlide(),
    mediaSlide(),
)

/**
 * The empty media frame, plain and captioned.
 *
 * Nothing here has bytes behind it: the deck is built in shared code with no
 * asset store and no network, so every image on it is the placeholder an element
 * with a null `assetId` draws. That is a feature worth seeing, and the alternative
 * would be a deck that cannot be built without a file on the side.
 */
private fun imagesSlide(): Slide {
    val plain: Frame = gridCell(columns = 3, rows = 1, index = 0)
    val captioned: Frame = gridCell(columns = 3, rows = 1, index = 1)
    val prompted: Frame = gridCell(columns = 3, rows = 1, index = 2)

    return featureSlide(
        title = "Images",
        subtitle = "ImageElement with no bytes behind it: the drop placeholder",
        elements = listOf(
            ImageElement(frame = plain.demoBox()),
            captionUnder(plain, "assetId = null"),
            ImageElement(frame = captioned.demoBox(), caption = "A caption, drawn inside the box"),
            captionUnder(captioned, "with a caption"),
            ImageElement(frame = prompted.demoBox(), placeholder = "Drop a screenshot here"),
            captionUnder(prompted, "with its own placeholder text"),
        ),
        notes = "Look for: three dashed drop frames of the same size. The middle one has " +
            "its caption drawn inside its own box, under the frame rather than over it. " +
            "The third says what its own placeholder string says.\n\n" +
            "Deliberately not covered: a real photo, and GalleryElement. Both need image " +
            "bytes, and this deck is built in shared code with no asset store to put " +
            "them in. A gallery with no images has nothing to draw and no steps to walk.",
    )
}

/** [ImageMask] outlines, which clip the box even when there is nothing inside it. */
private fun imageMasksSlide(): Slide {
    val kinds: List<ShapeKind> = listOf(
        ShapeKind.Rectangle,
        ShapeKind.Ellipse,
        ShapeKind.Diamond,
        ShapeKind.Star,
        ShapeKind.Polygon,
    )

    val masked: List<Element> = kinds.flatMapIndexed { index, kind ->
        val cell: Frame = gridCell(stage(520f), columns = 5, rows = 1, index = index)
        tile(cell, "ImageMask($kind)") { box ->
            ImageElement(
                frame = box,
                placeholder = "",
                mask = ImageMask(kind = kind, frame = WholeImage),
            )
        }
    }

    return featureSlide(
        title = "Image Masks",
        subtitle = "ImageMask: the outline the image is seen through",
        elements = masked,
        notes = "Look for: five frames clipped to the shape their caption names. Rectangle " +
            "is the plain crop and clips nothing. With no bytes behind them the mask has " +
            "only the placeholder to cut, so what shows is the outline itself: if all " +
            "five are rectangles the mask is not reaching the empty frame, which is " +
            "worth reporting rather than fixing here.",
    )
}

/** Sound and movies, both of which draw without a player behind them. */
private fun mediaSlide(): Slide {
    val movie: Frame = Frame(ShowcaseStage.x, ShowcaseStage.y, 960f, 540f)
    val titled: Frame = Frame(ShowcaseStage.x + 1020f, ShowcaseStage.y, 680f, 383f)

    return featureSlide(
        title = "Audio and Video",
        subtitle = "a web-hosted movie and two sound pills, neither one embedded",
        elements = listOf(
            VideoElement(
                frame = movie,
                webUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "A movie that lives on a site",
            ),
            captionUnder(
                Frame(movie.x, movie.y + movie.height, movie.width, CaptionHeight),
                "VideoElement(webUrl = ...)",
            ),
            VideoElement(frame = titled, loop = true, autoplay = true),
            captionUnder(
                Frame(titled.x, titled.y + titled.height, titled.width, CaptionHeight),
                "VideoElement with nothing behind it",
            ),
            AudioElement(
                frame = Frame(ShowcaseStage.x + 1020f, ShowcaseStage.y + 500f, 680f, 72f),
                title = "Intro sting.m4a",
            ),
            AudioElement(
                frame = Frame(ShowcaseStage.x + 1020f, ShowcaseStage.y + 600f, 680f, 72f),
            ),
            captionUnder(
                Frame(ShowcaseStage.x + 1020f, ShowcaseStage.y + 680f, 680f, CaptionHeight),
                "AudioElement, named and unnamed",
            ),
        ),
        notes = "Look for: a dark 16:9 media box with the movie's title on it, a second " +
            "smaller one with no title, and two sound pills. The first pill reads its own " +
            "title, the second names itself. Nothing plays and nothing is downloaded: " +
            "shared code has no player and no network, so what is on the slide is the " +
            "whole of what the canvas draws for media.",
    )
}
