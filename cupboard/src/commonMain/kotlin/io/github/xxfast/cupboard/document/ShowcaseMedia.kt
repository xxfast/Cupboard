package io.github.xxfast.cupboard.document

/** The whole of the image, in the normalised units a mask is measured in. */
private val WholeImage = Frame(0f, 0f, 1f, 1f)

/**
 * Images, sound and movies. The pictures and the tone are [showcaseAssets],
 * generated when the showcase is laid down; no movie ships, so the one video
 * with a picture on it is a poster with nothing behind it.
 */
internal fun mediaSlides(): List<Slide> = listOf(
    imagesSlide(),
    picturesSlide(),
    imageMasksSlide(),
    mediaSlide(),
)

/**
 * The empty media frame, plain and captioned: what an image with a null
 * `assetId` draws. A feature worth seeing on its own, before [picturesSlide]
 * puts bytes behind the same element.
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
            "Drop a picture on any of them and it takes the frame's place. The next " +
            "slide is the same element with bytes behind it.",
    )
}

/**
 * The showcase picture as an [ImageElement], the same bytes masked, and a
 * [GalleryElement] walking from the picture to its night version.
 *
 * Every frame is the shape of what it shows: an image's frame draws its window
 * stretched to fit, so a 4:3 picture in anything but a 4:3 box would be pulled
 * out of shape, and that is a different slide's point.
 */
private fun picturesSlide(): Slide {
    val width = 520f
    val height: Float = width * ShowcasePictureHeight / ShowcasePictureWidth

    val plainCell: Frame = gridCell(stage(470f), columns = 3, rows = 1, index = 0)
    val maskedCell: Frame = gridCell(stage(470f), columns = 3, rows = 1, index = 1)
    val galleryCell: Frame = gridCell(stage(470f), columns = 3, rows = 1, index = 2)

    fun centred(cell: Frame, boxWidth: Float, boxHeight: Float): Frame =
        Frame(cell.x + (cell.width - boxWidth) / 2, cell.y, boxWidth, boxHeight)

    val plain = ImageElement(
        frame = centred(plainCell, width, height),
        assetId = ShowcasePictureId,
        naturalWidth = ShowcasePictureWidth,
        naturalHeight = ShowcasePictureHeight,
    )

    // The middle three quarters of the picture's width is a square, so the
    // window is a square and so is the frame showing it, seen through a circle.
    val masked: ImageElement = plain.copy(
        id = newId(),
        frame = centred(maskedCell, height, height),
        mask = ImageMask(kind = ShapeKind.Ellipse, frame = Frame(0.125f, 0f, 0.75f, 1f)),
    )

    // The gallery's captions take a strip under the picture, so its frame is
    // that much taller than the picture is.
    val gallery = GalleryElement(
        frame = centred(galleryCell, width, height + 24f),
        images = listOf(
            GalleryImage(
                assetId = ShowcasePictureId,
                naturalWidth = ShowcasePictureWidth,
                naturalHeight = ShowcasePictureHeight,
                caption = "By day",
            ),
            GalleryImage(
                assetId = ShowcaseNightId,
                naturalWidth = ShowcasePictureWidth,
                naturalHeight = ShowcasePictureHeight,
                caption = "By night",
            ),
        ),
    )

    val slide: Slide = featureSlide(
        title = "Pictures",
        subtitle = "ImageElement with bytes behind it, masked, and a GalleryElement",
        elements = listOf(
            plain,
            captionUnder(plainCell, "ImageElement"),
            masked,
            captionUnder(maskedCell, "an Ellipse mask over a square window"),
            gallery,
            captionUnder(galleryCell, "GalleryElement, two pictures"),
        ),
        notes = "Look for: the showcase picture whole on the left, border and all. In the " +
            "middle the same bytes seen through a circle: the window is the middle " +
            "square of the picture, so the circle is not squashed, the sides of the " +
            "picture are gone and its corners are cut away. On the right the gallery " +
            "on its first picture, captioned By day.\n\n" +
            "In play: click once and the gallery dissolves into the night version, " +
            "captioned By night, and its dots move along one.\n\n" +
            "The pictures are generated when the showcase is laid down. A showcase from " +
            "before they shipped has none until Help > Feature Showcase > Reset, and " +
            "all three draw their placeholders instead.",
    )
    return slide.copy(builds = slide.gallerySteps(gallery.id))
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

/**
 * Sound and movies. The tone plays; the movies don't, because no movie ships:
 * one lives on a site, and the other is the showcase picture as a poster with
 * nothing behind it.
 */
private fun mediaSlide(): Slide {
    val movie: Frame = Frame(ShowcaseStage.x, ShowcaseStage.y, 960f, 540f)
    // 4:3, the poster's own shape: a poster is drawn stretched to its box.
    val poster: Frame = Frame(ShowcaseStage.x + 1080f, ShowcaseStage.y, 560f, 420f)
    val sounds: Float = ShowcaseStage.x + 1020f

    return featureSlide(
        title = "Audio and Video",
        subtitle = "a sound that plays, a web-hosted movie, and a poster with no movie behind it",
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
            VideoElement(frame = poster, posterAssetId = ShowcasePictureId),
            captionUnder(
                Frame(sounds, poster.y + poster.height, 680f, CaptionHeight),
                "VideoElement(posterAssetId = ...), no movie",
            ),
            AudioElement(
                frame = Frame(sounds, ShowcaseStage.y + 500f, 680f, 72f),
                assetId = ShowcaseToneId,
                title = "Showcase tone.wav",
            ),
            AudioElement(
                frame = Frame(sounds, ShowcaseStage.y + 600f, 680f, 72f),
            ),
            captionUnder(
                Frame(sounds, ShowcaseStage.y + 680f, 680f, CaptionHeight),
                "AudioElement, with bytes and without",
            ),
        ),
        notes = "Look for: on the left a dark 16:9 media box with the movie's title on it. " +
            "On the right the showcase picture as a movie's poster with the play badge " +
            "over it, then two sound pills: the first reads Showcase tone.wav, the " +
            "second names itself.\n\n" +
            "In play: click the tone's pill and a second and a half of soft A plays, " +
            "fading in and out with no click (the desktop app plays wav itself, macOS " +
            "through AVPlayer). Click the web movie and the host opens its page. The " +
            "poster and the empty pill have nothing behind them to play.\n\n" +
            "Deliberately not covered: an embedded movie. A movie cannot be generated in " +
            "shared code and none ships, so Insert > Movie to put your own on a slide " +
            "and watch it play. The picture and the tone are generated when the " +
            "showcase is laid down; one from before they shipped has neither until " +
            "Help > Feature Showcase > Reset, and the poster then draws the dark media " +
            "box.",
    )
}
