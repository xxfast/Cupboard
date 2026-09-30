package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.preview
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test

/**
 * Each Basic theme's chooser preview beside Keynote's own, for a human to hold
 * up against each other: ours on the left, Keynote's on the right, both at the
 * pixel size Keynote ships its thumbnail at.
 *
 * Keynote's thumbnails come out of the installed app, so on a machine without it
 * (Linux, CI) there is nothing to compare against and the theme is skipped
 * rather than failed. Like [RenderSnapshotTest], this asserts nothing: whether
 * two pictures match is a thing to look at.
 */
class ChooserParityTest {
    @Test
    fun renderEveryBasicPreviewBesideKeynotes() {
        val directory = File(System.getProperty("snapshots.out") ?: "build/snapshots", "chooser")
        directory.mkdirs()

        for (theme in BuiltInThemes.basic) {
            val folder: String = KeynoteFolders.getValue(theme.name)
            for ((size, file) in listOf(SlideSizePreset.Widescreen to "Wide", SlideSizePreset.Standard to "Standard")) {
                val keynote = File("$KeynotePreviews/$folder/en.lproj/$file@2x.jpg")
                if (!keynote.exists()) continue

                val theirs: Image = Image.makeFromEncoded(keynote.readBytes())
                val ours: Image = render(theme, size, theirs.width, theirs.height)
                val out = File(directory, "${theme.name.lowercase().replace(' ', '-')}-${file.lowercase()}.png")
                out.writeBytes(besideEachOther(ours, theirs))
                println("parity: ${out.absolutePath}")
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(theme: Theme, size: SlideSizePreset, width: Int, height: Int): Image {
        val deck: Document = theme.preview(size)
        return renderComposeScene(width, height) {
            SlideView(
                slide = deck.slides.first(),
                layout = deck.layouts.firstOrNull { it.id == deck.slides.first().layoutId },
                background = deck.background,
                slideWidth = deck.slideWidth,
                slideHeight = deck.slideHeight,
            )
        }
    }

    private fun besideEachOther(left: Image, right: Image): ByteArray {
        val surface: Surface = Surface.makeRasterN32Premul(left.width + Gap + right.width, left.height)
        surface.canvas.clear(Color.makeRGB(128, 128, 128))
        surface.canvas.drawImage(left, 0f, 0f)
        surface.canvas.drawImage(right, (left.width + Gap).toFloat(), 0f)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}

private const val Gap: Int = 8

private const val KeynotePreviews: String =
    "/Applications/Keynote Creator Studio.app/Contents/SharedSupport/Template Previews"

/** Where Keynote keeps each Basic theme's thumbnails, by the theme's name. */
private val KeynoteFolders: Map<String, String> = mapOf(
    "Basic White" to "21_BasicWhite",
    "Basic Black" to "20_BasicBlack",
    "Classic White" to "23_ClassicWhite",
    "White" to "White",
    "Black" to "Black",
)
