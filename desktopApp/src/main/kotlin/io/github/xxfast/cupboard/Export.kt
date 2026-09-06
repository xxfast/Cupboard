package io.github.xxfast.cupboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.xxfast.cupboard.canvas.AssetImageCache
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.stepCount
import io.github.xxfast.cupboard.export.ExportedBytes
import io.github.xxfast.cupboard.export.ExportedFile
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
import io.github.xxfast.cupboard.export.toCupProject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JOptionPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/**
 * The generator names the project, we only have to read it back: the slug it
 * builds from the deck's name is internal to `:cupboard`, so the emitted
 * settings file is what tells us which folder to write into.
 */
private val RootProjectName = Regex("""rootProject\.name\s*=\s*"([^"]+)"""")

private const val DialogTitle: String = "Export as CuP Project"

/**
 * Asks where the project should go, writes it there, and says where it landed.
 *
 * Blocking, on the AWT thread the menu action already runs on: a directory
 * chooser is modal anyway, and seven small files are not worth a coroutine. An
 * export is fresh every time, so anything already at those paths is replaced.
 *
 * The report is Swing's rather than a Compose dialog: this shell doesn't carry
 * material3 on its own classpath, and an alert isn't worth a dependency.
 */
fun exportCupProject(document: Document, owner: Frame) {
    val parent: File = chooseDirectory(owner, DialogTitle, "Export") ?: return
    val files: List<ExportedFile> = document.toCupProject()
    val root: Path = parent.toPath().resolve(files.projectName())

    for (file in files) {
        val target: Path = root.resolve(file.path)
        Files.createDirectories(target.parent)
        Files.writeString(target, file.contents, StandardCharsets.UTF_8)
    }

    JOptionPane.showMessageDialog(
        owner,
        "Exported to $root.\nRun `./gradlew run` there.",
        "Exported",
        JOptionPane.INFORMATION_MESSAGE,
    )
}

/** The `rootProject.name` the export settled on, which names the folder. */
private fun List<ExportedFile>.projectName(): String {
    val settings: ExportedFile? = firstOrNull { it.path == "settings.gradle.kts" }
    val match = settings?.let { RootProjectName.find(it.contents) }
    return match?.groupValues?.get(1) ?: "presentation"
}

/**
 * What File > Export offers, one entry per menu item.
 *
 * [Print] rides along even though it lives outside that submenu: it is a PDF
 * drawn the same way, down the same path, and only the last step differs. The
 * three formats with nothing to choose ([Html], [Pptx], [Print]) go straight to
 * the file panel, which is what [hasOptions] says.
 */
internal enum class ExportFormat(val title: String) {
    Pdf("Export as PDF"),
    Png("Export as Images"),
    Gif("Export as GIF"),
    Html("Export as HTML Player"),
    Pptx("Export as PowerPoint"),
    Print("Print"),
}

/** Whether picking this one has anything to ask before the file panel. */
internal val ExportFormat.hasOptions: Boolean
    get() = this == ExportFormat.Pdf || this == ExportFormat.Png || this == ExportFormat.Gif

/**
 * One export, settled: the format and whatever its dialog decided.
 *
 * The formats with no options are objects, so a menu pick is the whole request.
 */
internal sealed interface ExportRequest {
    val format: ExportFormat

    data class Pdf(val options: PdfOptions) : ExportRequest {
        override val format: ExportFormat get() = ExportFormat.Pdf
    }

    data class Png(val everyBuild: Boolean) : ExportRequest {
        override val format: ExportFormat get() = ExportFormat.Png
    }

    data class Gif(val options: GifOptions) : ExportRequest {
        override val format: ExportFormat get() = ExportFormat.Gif
    }

    data object Html : ExportRequest {
        override val format: ExportFormat get() = ExportFormat.Html
    }

    data object Pptx : ExportRequest {
        override val format: ExportFormat get() = ExportFormat.Pptx
    }

    /** A PDF nobody keeps: the OS gets it, and the file was temporary. */
    data object Print : ExportRequest {
        override val format: ExportFormat get() = ExportFormat.Print
    }
}

/**
 * How far a running export has got, for the modal that sits over it.
 *
 * The counter is atomic and read a frame at a time rather than snapshot state
 * written from the worker: the increments come off [Dispatchers.Default], and
 * anything a dialog shows is better pulled on the frame than pushed from a
 * thread that isn't drawing. [total] is worked out up front from the same shape
 * the writer will walk, so the bar is honest from the first frame.
 */
internal class ExportProgress(val title: String, val total: Int) {
    private val counter = AtomicInteger()

    /** One more frame drawn. Called off the UI thread, by [counted]. */
    fun drew() {
        counter.incrementAndGet()
    }

    val done: Int get() = counter.get()
}

/**
 * The dialogs File > Export puts up, as state one window owns.
 *
 * Two of them, never both: the options for the format that was picked, then the
 * modal that says how far the drawing has got. A menu item only sets [asking];
 * everything after that is [ExportDialogs]' business.
 */
internal class Exports {
    /** The format whose options are being asked for, or null for none up. */
    var asking: ExportFormat? by mutableStateOf(null)

    /** The export being drawn, or null when nothing is running. */
    var progress: ExportProgress? by mutableStateOf(null)

    fun ask(format: ExportFormat) {
        asking = format
    }

    fun dismiss() {
        asking = null
    }
}

/**
 * Picks the file, draws the deck, writes the bytes, and says where they went.
 *
 * The panel is first and blocking, on the UI thread the menu click arrived on:
 * it is modal anyway, and a cancel there should cost nothing. Everything after
 * it is on [Dispatchers.Default] behind the progress modal, because a deck of
 * forty slides at 1920 wide is seconds of Skia, and seconds of a frozen window
 * is a hung app.
 *
 * A failure is reported rather than thrown: there is no caller left to catch it
 * by then, and a full disk is a sentence rather than a stack trace.
 */
internal fun startExport(
    request: ExportRequest,
    document: Document,
    name: String,
    assets: AssetStore,
    window: Frame,
    scope: CoroutineScope,
    onProgress: (ExportProgress?) -> Unit,
) {
    val target: File = request.target(window, name) ?: return
    val progress = ExportProgress(request.format.title, request.frameCount(document))
    onProgress(progress)

    scope.launch {
        val failure: Throwable? = runCatching {
            withContext(Dispatchers.Default) {
                val images: AssetImageCache = warmedImages(assets)
                val rasterizer: Rasterizer = SlideRasterizer(
                    document = document,
                    assets = assets,
                    width = request.rasterWidth,
                    images = images,
                ).counted(progress)

                request.write(document, rasterizer, target)
            }
        }.exceptionOrNull()

        onProgress(null)
        if (failure != null) report(window, request.format.title, failure) else request.finish(window, target)
    }
}

/**
 * Every asset decoded before a single frame is drawn.
 *
 * A rasterised frame is one composition and one draw, so an image that is not
 * already in the cache renders as its placeholder: `rememberAssetImage` reads
 * the bytes in a `LaunchedEffect` that never gets a second frame to land in.
 * Reading the whole store rather than walking the elements keeps this out of the
 * document model, and a deck's store holds the deck's pictures either way.
 *
 * The capacity is the store's own size, so warming it cannot evict what it just
 * put in. An asset that will not decode is left out: it draws as a placeholder,
 * which is what the canvas shows for it too.
 */
private suspend fun warmedImages(assets: AssetStore): AssetImageCache {
    val ids: List<String> = runCatching { assets.ids() }.getOrDefault(emptyList())
    val images = AssetImageCache(capacity = maxOf(1, ids.size))

    for (id in ids) {
        val bytes: ByteArray = runCatching { assets.read(id) }.getOrNull() ?: continue
        val image: ImageBitmap = runCatching {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull() ?: continue
        images.put(id, image)
    }

    return images
}

/** This rasterizer, ticking [progress] on every frame it hands back. */
private fun Rasterizer.counted(progress: ExportProgress): Rasterizer = object : Rasterizer {
    override fun frame(slideIndex: Int, step: Int): RasterFrame =
        this@counted.frame(slideIndex, step).also { progress.drew() }
}

/**
 * Where this export is going, or null if the panel was dismissed.
 *
 * PNGs are several files, so they get a folder chooser and a folder of their own
 * inside what was picked: a numbered run of stills loose in Documents is a mess
 * nobody asked for. Print is going nowhere anyone will look, so it gets a temp
 * file and no panel at all.
 */
private fun ExportRequest.target(owner: Frame, name: String): File? = when (this) {
    is ExportRequest.Png -> chooseDirectory(owner, format.title, "Export")
        ?.let { File(it, name.asFileName()) }

    // The spool copy outlives the print job and nothing else, so it goes when
    // the app does rather than sitting in temp until the OS sweeps it.
    ExportRequest.Print -> Files.createTempFile("cupboard-", ".pdf").toFile()
        .apply { deleteOnExit() }

    else -> chooseSaveFile(owner, format.title, "${name.asFileName()}.$extension")
}

/** The extension the format writes. Meaningless for PNGs, which write a folder. */
private val ExportRequest.extension: String
    get() = when (this) {
        is ExportRequest.Pdf, ExportRequest.Print -> "pdf"
        is ExportRequest.Png -> ""
        is ExportRequest.Gif -> "gif"
        ExportRequest.Html -> "html"
        ExportRequest.Pptx -> "pptx"
    }

/**
 * How wide the frames are drawn.
 *
 * 1920 for the formats that carry a picture to be looked at full size, and less
 * for the two that carry many: the player embeds every frame as base64 in one
 * file, and a GIF is scaled down by its own encoder anyway, from a frame big
 * enough for the box filter to have something to average.
 */
private val ExportRequest.rasterWidth: Int
    get() = when (this) {
        ExportRequest.Html -> 1280
        else -> 1920
    }

/** How many frames this export will draw, which is what the bar counts up to. */
private fun ExportRequest.frameCount(document: Document): Int = when (this) {
    is ExportRequest.Pdf -> document.frameCount(options.everyBuild, options.skippedSlides)
    is ExportRequest.Png -> document.frameCount(everyBuild)
    is ExportRequest.Gif -> document.frameCount(options.everyBuild)
    ExportRequest.Html -> document.frameCount(everyBuild = true)
    ExportRequest.Pptx, ExportRequest.Print -> document.frameCount(everyBuild = false)
}

/**
 * The same count `exportFrames` will produce, which this shell cannot call: it
 * is internal to `:cupboard`, and a progress bar is not worth widening it.
 *
 * The empty-deck exception is the writers' own, see `exportedSlideIndices`: a
 * deck with every slide skipped exports whole rather than empty.
 */
private fun Document.frameCount(everyBuild: Boolean, includeSkipped: Boolean = false): Int {
    val exported: List<Slide> =
        if (includeSkipped) slides else slides.filterNot { it.skipped }.ifEmpty { slides }
    return if (!everyBuild) exported.size else exported.sumOf { it.stepCount() }
}

/** The deck drawn and written to [target]. On [Dispatchers.Default], never the UI. */
private fun ExportRequest.write(document: Document, rasterizer: Rasterizer, target: File) {
    val path: Path = target.toPath()
    when (this) {
        is ExportRequest.Pdf -> Files.write(path, exportPdf(document, rasterizer, options))

        ExportRequest.Print ->
            Files.write(path, exportPdf(document, rasterizer, PdfOptions(layout = HandoutLayout.OnePerPage)))

        is ExportRequest.Gif -> Files.write(path, exportGif(document, rasterizer, options))

        ExportRequest.Pptx -> Files.write(path, exportPptx(document, rasterizer))

        ExportRequest.Html ->
            Files.writeString(path, exportHtmlPlayer(document, rasterizer), StandardCharsets.UTF_8)

        is ExportRequest.Png -> {
            Files.createDirectories(path)
            for (file: ExportedBytes in exportPngs(document, rasterizer, everyBuild)) {
                Files.write(path.resolve(file.path), file.bytes)
            }
        }
    }
}

/** What happens once the bytes are on disk: the printer, or a word about it. */
private fun ExportRequest.finish(owner: Frame, target: File) {
    if (this == ExportRequest.Print) print(target, owner)
    else JOptionPane.showMessageDialog(
        owner,
        "Exported to $target.",
        "Exported",
        JOptionPane.INFORMATION_MESSAGE,
    )
}

/**
 * The PDF handed to whatever this OS prints with.
 *
 * `Desktop.print` opens the platform's own print dialog, which is the one the
 * user's printers and page setup are in: there is no case for drawing our own.
 * Where it isn't supported (most Linux desktops) the file is opened instead, and
 * the viewer that comes up has a Print in its own menu.
 */
private fun print(file: File, owner: Frame) {
    val desktop: Desktop? = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
    when {
        desktop == null -> report(owner, "Print", "This desktop can't print.")
        desktop.isSupported(Desktop.Action.PRINT) -> desktop.print(file)
        desktop.isSupported(Desktop.Action.OPEN) -> desktop.open(file)
        else -> report(owner, "Print", "This desktop can't print.")
    }
}

/**
 * A save panel with [name] filled in, and the file picked in it.
 *
 * AWT's rather than Swing's, the same call Save As makes: this is naming a file
 * that doesn't exist yet, and only the save panel has a name field. Whatever the
 * user types is taken as it stands, extension and all.
 */
private fun chooseSaveFile(owner: Frame, title: String, name: String): File? {
    val dialog = FileDialog(owner, title, FileDialog.SAVE)
    dialog.file = name
    dialog.isVisible = true

    val directory: String = dialog.directory ?: return null
    val file: String = dialog.file ?: return null
    return File(directory, file)
}

/** A deck's name with the characters a path can't carry taken out. */
private fun String.asFileName(): String =
    filterNot { it in "/\\:*?\"<>|" }.trim().ifEmpty { "Deck" }

private fun report(owner: Frame, title: String, failure: Throwable) {
    report(owner, title, failure.message ?: failure.toString())
}

private fun report(owner: Frame, title: String, reason: String) {
    JOptionPane.showMessageDialog(owner, reason, title, JOptionPane.ERROR_MESSAGE)
}
