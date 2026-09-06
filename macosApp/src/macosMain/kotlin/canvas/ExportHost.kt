@file:OptIn(ExperimentalForeignApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.stepCount
import io.github.xxfast.cupboard.export.ExportedBytes
import io.github.xxfast.cupboard.export.GifOptions
import io.github.xxfast.cupboard.export.HandoutLayout
import io.github.xxfast.cupboard.export.PdfOptions
import io.github.xxfast.cupboard.export.RasterFrame
import io.github.xxfast.cupboard.export.Rasterizer
import io.github.xxfast.cupboard.export.SlideRasterizer
import io.github.xxfast.cupboard.export.exportGif
import io.github.xxfast.cupboard.export.exportHtmlPlayer
import io.github.xxfast.cupboard.export.exportPdf
import io.github.xxfast.cupboard.export.exportPngs
import io.github.xxfast.cupboard.export.exportPptx
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

/**
 * How wide an exported slide is drawn, in pixels. 1920 is the deck at 2x its own
 * 960-point width, which is what a PDF page or a PowerPoint slide is scaled from:
 * enough that type stays type when the page is printed, and not so much that a
 * fifty-slide deck takes a minute.
 */
private const val EXPORT_WIDTH: Int = 1920

/** The smallest a GIF is rendered before it is scaled down to its own width. */
private const val GIF_SOURCE_MIN: Int = 640

/**
 * What the handout layouts are called in the PDF options, in [HandoutLayout]'s
 * own order, so a place in this list is an ordinal. Spelled here rather than on
 * the enum for the reason the transition kinds are: a menu says "4 Slides Per
 * Page" and code says `FourPerPage`.
 */
private val HandoutLayoutTitles: List<String> = listOf(
    "1 Slide Per Page",
    "2 Slides Per Page",
    "4 Slides Per Page",
    "6 Slides Per Page",
    "1 Slide With Notes",
)

/**
 * One exported file on its way to disk: what it is called, and what is in it.
 *
 * The core's `ExportedBytes` flattened for ObjC, the way [ExportFile] flattens a
 * generated source file: a data class carrying a `ByteArray` does not cross the
 * boundary, and the shell only ever reads these on its way to a save panel.
 */
class ExportBytes(val name: String, val data: NSData)

/**
 * The deck in every format the core can write, for the AppKit side to put on
 * disk.
 *
 * [EditorHost]'s companion rather than part of it: nothing here draws the editor
 * or answers a panel, it only turns the document the host is on into bytes. The
 * host is what it is constructed from because the document lives behind it, and
 * an exporter that took a [Document] would be an exporter of a snapshot taken
 * whenever Swift happened to make one.
 *
 * Every method is synchronous and every one of them is slow: rasterizing a deck
 * is a Compose composition and a Skia draw per frame. The shell calls them from
 * a background queue and shows a sheet, which is why none of them is a
 * `suspend fun`: a callback into Swift per frame ([progress]) says more than an
 * awaited result would.
 */
class DeckExporter(private val editor: EditorHost) {
    /**
     * How far along an export is: frames drawn, and frames to draw. Fires on
     * whatever thread the export was started on, so a shell updating a progress
     * bar hops to the main queue itself.
     */
    var progress: (Int, Int) -> Unit = { _, _ -> }

    /** What the PDF options offer, in order. A place in this list is a layout. */
    fun handoutLayoutTitles(): List<String> = HandoutLayoutTitles

    /**
     * The deck as a PDF. [layoutIndex] is a place in [handoutLayoutTitles], and
     * anything outside it falls back to one slide per page, since the number
     * crosses a language boundary on the way in.
     */
    fun pdf(
        layoutIndex: Int,
        everyBuild: Boolean,
        includeSkipped: Boolean,
        includeNotes: Boolean,
    ): NSData {
        val layout: HandoutLayout =
            HandoutLayout.entries.getOrElse(layoutIndex) { HandoutLayout.OnePerPage }
        val options = PdfOptions(
            everyBuild = everyBuild,
            layout = layout,
            includeNotes = includeNotes,
            skippedSlides = includeSkipped,
        )
        val document: Document = document()
        val rasterizer: Rasterizer = counted(
            rasterizer(document, EXPORT_WIDTH),
            document.frameCount(everyBuild, includeSkipped),
        )
        return exportPdf(document, rasterizer, options).toNSData()
    }

    /** Every slide as its own PNG, named the way the core names them. */
    fun pngs(everyBuild: Boolean): List<ExportBytes> {
        val document: Document = document()
        val rasterizer: Rasterizer = counted(
            rasterizer(document, EXPORT_WIDTH),
            document.frameCount(everyBuild, includeSkipped = false),
        )
        return exportPngs(document, rasterizer, everyBuild)
            .map { file: ExportedBytes -> ExportBytes(file.path, file.bytes.toNSData()) }
    }

    /**
     * The deck as an animated GIF, [width] pixels across, one frame per step.
     *
     * Drawn at twice the target and scaled down rather than drawn at size: a GIF
     * is 256 colours, and downsampling anti-aliased type is what keeps it
     * readable once it has been quantised.
     */
    fun gif(width: Int, secondsPerFrame: Float, loop: Boolean): NSData {
        val document: Document = document()
        val source: Int = (width * 2).coerceIn(GIF_SOURCE_MIN, EXPORT_WIDTH)
        val rasterizer: Rasterizer = counted(
            rasterizer(document, source),
            document.frameCount(everyBuild = true, includeSkipped = false),
        )
        val options = GifOptions(
            width = width,
            frameDelayMs = (secondsPerFrame * 1000).roundToInt().coerceAtLeast(20),
            everyBuild = true,
            loop = loop,
        )
        return exportGif(document, rasterizer, options).toNSData()
    }

    /** The deck as one self-contained HTML page, frames and notes carried in it. */
    fun html(): String {
        val document: Document = document()
        val rasterizer: Rasterizer = counted(
            rasterizer(document, EXPORT_WIDTH),
            document.frameCount(everyBuild = true, includeSkipped = false),
        )
        return exportHtmlPlayer(document, rasterizer)
    }

    /** The deck as a .pptx: one picture-backed slide each, with the notes on it. */
    fun pptx(): NSData {
        val document: Document = document()
        val rasterizer: Rasterizer = counted(
            rasterizer(document, EXPORT_WIDTH),
            document.frameCount(everyBuild = false, includeSkipped = false),
        )
        return exportPptx(document, rasterizer).toNSData()
    }

    /** The deck as it stands, which is what an export is always of. */
    private fun document(): Document = editor.viewModel.states.value.document

    /**
     * A rasterizer with every one of the deck's pictures already decoded.
     *
     * A frame is one composition and one draw, so an image the cache has not got
     * renders as its placeholder: warming it first is the difference between an
     * export with the photos in it and an export of grey boxes. Done once per
     * export rather than once per frame, and the cache is sized to hold the lot
     * so nothing it just decoded gets evicted by the next slide.
     */
    private fun rasterizer(document: Document, width: Int): SlideRasterizer {
        val assets: AssetStore = editor.viewModel.assets
        val images: AssetImageCache = runBlocking {
            val ids: List<String> = assets.ids()
            val cache = AssetImageCache(capacity = maxOf(1, ids.size))
            for (id in ids) {
                val bytes: ByteArray = assets.read(id) ?: continue
                val decoded: ImageBitmap = runCatching {
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }.getOrNull() ?: continue
                cache.put(id, decoded)
            }
            cache
        }
        return SlideRasterizer(document, assets, width, images)
    }

    /** [rasterizer] reporting each frame it draws to [progress]. */
    private fun counted(rasterizer: Rasterizer, total: Int): Rasterizer =
        CountingRasterizer(rasterizer, total) { done, all -> progress(done, all) }
}

/**
 * A rasterizer that says how far it has got.
 *
 * The core's writers take a [Rasterizer] and nothing else, which is what keeps
 * them pure functions of a document. Progress is therefore counted here, at the
 * one place every export passes through: a frame drawn is a frame done, whatever
 * the format is doing with it.
 */
private class CountingRasterizer(
    private val rasterizer: Rasterizer,
    private val total: Int,
    private val report: (Int, Int) -> Unit,
) : Rasterizer {
    private var done: Int = 0

    override fun frame(slideIndex: Int, step: Int): RasterFrame {
        val raster: RasterFrame = rasterizer.frame(slideIndex, step)
        done++
        report(done, total)
        return raster
    }
}

/**
 * How many frames an export of this deck draws, which is what a progress bar
 * counts against.
 *
 * The core works this out too, in `exportFrames`, but that is internal to
 * `:cupboard` and deliberately so: it decides what an export covers, and a shell
 * has no business being able to change it. This is the same arithmetic read-only,
 * and the one thing it has to keep in step with is the empty-deck exception: a
 * deck with every slide skipped exports whole rather than exporting nothing.
 */
private fun Document.frameCount(everyBuild: Boolean, includeSkipped: Boolean): Int {
    val kept: List<Int> =
        if (includeSkipped) slides.indices.toList()
        else slides.indices.filterNot { slides[it].skipped }.ifEmpty { slides.indices.toList() }
    return if (!everyBuild) kept.size else kept.sumOf { slides[it].stepCount() }
}

/** These bytes as Foundation's, which is what an AppKit shell writes to disk. */
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), size.toULong()) }
}
