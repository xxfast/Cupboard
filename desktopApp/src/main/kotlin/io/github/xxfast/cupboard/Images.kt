package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.screens.editor.ImageExtensions
import io.github.xxfast.cupboard.screens.editor.VectorExtensions
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * A picture on its way into a deck: the bytes, and the extension they should be
 * filed under.
 *
 * A class rather than a data class because the bytes are the point: equality on
 * a megabyte array is nothing any caller here wants, and a copy of one is a
 * copy nobody asked for.
 */
internal class PickedImage(val bytes: ByteArray, val extension: String)

/** Whether this file is one of the kinds an insert takes, by its name alone. */
private fun File.isImage(): Boolean = extension.lowercase() in ImageExtensions

/** Whether this file is a vector drawing, which the deck draws rather than decodes. */
private fun File.isVector(): Boolean = extension.lowercase() in VectorExtensions

/**
 * Whether this picked picture is a vector drawing. Those become shapes, so they
 * never join a gallery, and a slot that holds one picture (a replace, a poster,
 * a fill) has nothing to do with one.
 */
internal val PickedImage.isVector: Boolean get() = extension in VectorExtensions

/**
 * [file]'s bytes, or null if it is not a readable image of a kind we take.
 *
 * A vector file counts: a drop, a pasted file and the Insert panel can all hand
 * one over. A shell whose slot takes pixels only filters it out after.
 */
internal fun readImage(file: File): PickedImage? {
    if (!file.isFile || !(file.isImage() || file.isVector())) return null

    val bytes: ByteArray = runCatching { file.readBytes() }.getOrNull() ?: return null
    return PickedImage(bytes, file.extension.lowercase())
}

/**
 * A native open panel filtered to images, and what was picked in it. Vector
 * files are offered too when [vectors] is set, which only an insert wants: every
 * other caller is filling a slot that takes pixels.
 *
 * AWT's panel rather than Swing's, like the Save As one: this is picking a file
 * that exists, which is the case macOS has a real panel for. The filter is
 * honoured on macOS and Linux; Windows reads the wildcard list in the file
 * field instead, so both are set.
 */
internal fun chooseImage(owner: Frame, vectors: Boolean = false): PickedImage? {
    val extensions: List<String> =
        if (vectors) ImageExtensions + VectorExtensions else ImageExtensions
    val dialog = FileDialog(owner, "Insert Image", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> File(name).extension.lowercase() in extensions }
    dialog.file = extensions.joinToString(";") { "*.$it" }
    dialog.isVisible = true

    val directory: String = dialog.directory ?: return null
    val name: String = dialog.file ?: return null
    val picked: PickedImage = readImage(File(directory, name)) ?: return null
    return picked.takeIf { vectors || !it.isVector }
}

/**
 * The same panel with several files allowed, and every image picked in it.
 *
 * A gallery is several pictures in one box, so the verb that inserts one asks
 * for several in one visit rather than making the user come back per file. What
 * comes back is in the panel's own order, which is the order they will be
 * stepped through.
 *
 * Empty is a cancel, and empty is also a pick that turned out to hold nothing we
 * read: both are nothing inserted, which is the same answer.
 */
internal fun chooseImages(owner: Frame): List<PickedImage> {
    val dialog = FileDialog(owner, "Insert Image Gallery", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.setFilenameFilter { _, name -> File(name).isImage() }
    dialog.file = ImageExtensions.joinToString(";") { "*.$it" }
    dialog.isVisible = true

    return dialog.files.orEmpty().mapNotNull(::readImage).filterNot { it.isVector }
}

/**
 * The picture on the system clipboard as PNG bytes, or null if there is none.
 *
 * PNG whatever it came in as: the clipboard hands over decoded pixels rather
 * than a file, so there is no original format left to preserve, and PNG is the
 * one that keeps the alpha a screenshot arrives with.
 */
internal fun clipboardImage(): PickedImage? {
    val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null

    val image: Image = runCatching { clipboard.getData(DataFlavor.imageFlavor) }
        .getOrNull() as? Image ?: return null
    val buffered: BufferedImage = image.asBuffered() ?: return null

    val bytes = ByteArrayOutputStream()
    if (!ImageIO.write(buffered, "png", bytes)) return null
    return PickedImage(bytes.toByteArray(), "png")
}

/**
 * This image as one the encoder can write: itself where it already is one, and
 * a fresh ARGB copy where it is some other flavour of [Image].
 *
 * Null for an image with no size yet. Everything on a clipboard is fully loaded,
 * so that is a corrupt transfer rather than one still arriving.
 */
private fun Image.asBuffered(): BufferedImage? {
    if (this is BufferedImage) return this

    val width: Int = getWidth(null)
    val height: Int = getHeight(null)
    if (width <= 0 || height <= 0) return null

    val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = buffered.createGraphics()
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()
    return buffered
}
