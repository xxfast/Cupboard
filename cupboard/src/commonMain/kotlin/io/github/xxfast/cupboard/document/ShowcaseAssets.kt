package io.github.xxfast.cupboard.document

import io.github.xxfast.cupboard.export.RasterFrame
import io.github.xxfast.cupboard.export.stillGif
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** The showcase's picture: bands, a circle and a diagonal, so every fill mode reads differently. */
const val ShowcasePictureId: String = "showcase-picture.gif"

/** The same picture after dark, so the gallery has a second picture to walk to. */
const val ShowcaseNightId: String = "showcase-night.gif"

/** A small repeating motif, for [ImageFillMode.Tile]. */
const val ShowcaseTileId: String = "showcase-tile.gif"

/** A second and a half of soft sine, for the sound pill that actually plays. */
const val ShowcaseToneId: String = "showcase-tone.wav"

/**
 * The picture's natural size, in pixels. 4:3, and smaller than the boxes the
 * Image Fills slide puts it in, so `Original` draws it at its own size with room
 * round it rather than looking like `ScaleToFit` with a different name.
 */
internal const val ShowcasePictureWidth: Int = 240
internal const val ShowcasePictureHeight: Int = 180

/** The tile's natural size, in pixels. */
internal const val ShowcaseTileSize: Int = 48

/**
 * The bytes the showcase deck points at, by the ids it points at them with.
 *
 * Generated rather than shipped: the deck is built in shared code with no file
 * on the side, so its pictures are drawn a pixel at a time here and its sound is
 * written a sample at a time. Deterministic, so two lay-downs of the showcase
 * hold the same bytes, and small (well under 200 KB) because it runs once each
 * time a showcase bundle is laid down and is never cached.
 *
 * No movie and no font: neither can be generated in shared code, and a binary
 * blob in source is not something this deck will carry. The slides that would
 * use them say how to insert your own instead.
 */
fun showcaseAssets(): Map<String, ByteArray> = mapOf(
    ShowcasePictureId to stillGif(picture(DayPalette)),
    ShowcaseNightId to stillGif(picture(NightPalette)),
    ShowcaseTileId to stillGif(tile()),
    ShowcaseToneId to wav(tone(), ToneSampleRate),
)

/**
 * The colours one version of the picture is painted in.
 *
 * Every one sits on the GIF encoder's fixed cube, so it comes out flat rather
 * than dithered: red and blue on a multiple of 51, green on 0, 85, 170 or 255.
 * The cube's other three greens sit a fraction under a level, and the dither
 * scatters those.
 */
private class PicturePalette(
    val bands: List<Int>,
    val circle: Int,
    val diagonal: Int,
    val border: Int,
)

/** Purple at the top running to teal at the bottom, in the deck's own colours. */
private val DayPalette = PicturePalette(
    bands = listOf(
        rgb(51, 0, 102),
        rgb(102, 0, 153),
        rgb(102, 0, 204),
        rgb(102, 85, 255),
        rgb(51, 85, 204),
        rgb(0, 85, 204),
        rgb(0, 170, 204),
        rgb(0, 170, 153),
    ),
    circle = rgb(255, 170, 0),
    diagonal = rgb(255, 255, 255),
    border = rgb(255, 255, 255),
)

/** Navy to black, a white moon and an amber diagonal. */
private val NightPalette = PicturePalette(
    bands = listOf(
        rgb(0, 0, 51),
        rgb(0, 0, 102),
        rgb(51, 0, 102),
        rgb(0, 85, 153),
        rgb(0, 0, 153),
        rgb(0, 0, 102),
        rgb(0, 0, 51),
        rgb(0, 0, 0),
    ),
    circle = rgb(255, 255, 255),
    diagonal = rgb(255, 170, 0),
    border = rgb(102, 85, 255),
)

/**
 * The picture: horizontal bands top to bottom, a circle left of centre, a
 * diagonal from the bottom-left corner to the top-right, and a border.
 *
 * Each of those says something about a fill mode at a glance. The border says
 * whether an edge was cropped, the circle whether the aspect was kept, and the
 * diagonal whether the picture was pulled out of shape.
 */
private fun picture(palette: PicturePalette): RasterFrame {
    val width: Int = ShowcasePictureWidth
    val height: Int = ShowcasePictureHeight
    val border = 5
    val centreX: Float = width * 0.32f
    val centreY: Float = height * 0.42f
    val radius: Float = height * 0.24f
    val diagonalLength: Float = sqrt((width * width + height * height).toFloat())

    val argb = IntArray(width * height) { at ->
        val x: Int = at % width
        val y: Int = at / width
        val dx: Float = x + 0.5f - centreX
        val dy: Float = y + 0.5f - centreY
        // Distance from the line through (0, height) and (width, 0).
        val offDiagonal: Float = abs(x * height + y * width - width * height) / diagonalLength

        when {
            x < border || y < border || x >= width - border || y >= height - border -> palette.border
            offDiagonal < 4f -> palette.diagonal
            dx * dx + dy * dy < radius * radius -> palette.circle
            else -> palette.bands[y * palette.bands.size / height]
        }
    }
    return RasterFrame(width, height, argb)
}

/** A diamond with a dot in it, which repeats into a lattice. */
private fun tile(): RasterFrame {
    val size: Int = ShowcaseTileSize
    val middle: Int = size / 2

    val argb = IntArray(size * size) { at ->
        val dx: Int = at % size - middle
        val dy: Int = at / size - middle
        when {
            dx * dx + dy * dy < 25 -> rgb(0, 170, 153)
            abs(dx) + abs(dy) < 18 -> rgb(102, 85, 255)
            else -> rgb(51, 0, 102)
        }
    }
    return RasterFrame(size, size, argb)
}

/** An opaque colour, packed ARGB like the rest of the document. */
private fun rgb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private const val ToneSampleRate: Int = 22050
private const val ToneSeconds: Float = 1.5f
private const val ToneFrequency: Float = 440f

/** A quarter of full scale: a tone to hear, not to be startled by. */
private const val ToneLevel: Float = 0.25f

private const val FadeInSeconds: Float = 0.04f
private const val FadeOutSeconds: Float = 0.3f

/**
 * A soft A, as signed 16-bit samples: one sine, eased in and out along a half
 * cosine so it starts and stops at silence rather than with a click.
 */
private fun tone(): ShortArray {
    val count: Int = (ToneSampleRate * ToneSeconds).roundToInt()
    val fadeIn: Int = (ToneSampleRate * FadeInSeconds).roundToInt()
    val fadeOut: Int = (ToneSampleRate * FadeOutSeconds).roundToInt()

    return ShortArray(count) { index ->
        val envelope: Double = when {
            index < fadeIn -> ease(index.toDouble() / fadeIn)
            index >= count - fadeOut -> ease((count - 1 - index).toDouble() / fadeOut)
            else -> 1.0
        }
        val phase: Double = 2 * PI * ToneFrequency * index / ToneSampleRate
        (sin(phase) * envelope * ToneLevel * Short.MAX_VALUE).roundToInt().toShort()
    }
}

/** 0 to 1 along a half cosine: flat at both ends. */
private fun ease(t: Double): Double = (1 - cos(PI * t.coerceIn(0.0, 1.0))) / 2

/**
 * [samples] as a mono 16-bit PCM WAV at [sampleRate]: the 44-byte RIFF header,
 * then the samples little-endian. The plainest sound file there is, and the one
 * every player reads.
 */
internal fun wav(samples: ShortArray, sampleRate: Int): ByteArray {
    val dataLength: Int = samples.size * 2
    val out = ByteArray(44 + dataLength)
    var at = 0

    fun text(value: String) {
        for (char in value) out[at++] = char.code.toByte()
    }

    fun short(value: Int) {
        out[at++] = value.toByte()
        out[at++] = (value shr 8).toByte()
    }

    fun int(value: Int) {
        short(value and 0xFFFF)
        short(value ushr 16)
    }

    text("RIFF")
    int(36 + dataLength)
    text("WAVE")
    text("fmt ")
    int(16)
    short(1) // PCM
    short(1) // mono
    int(sampleRate)
    int(sampleRate * 2) // bytes a second
    short(2) // bytes a frame
    short(16) // bits a sample
    text("data")
    int(dataLength)
    for (sample in samples) short(sample.toInt())
    return out
}
