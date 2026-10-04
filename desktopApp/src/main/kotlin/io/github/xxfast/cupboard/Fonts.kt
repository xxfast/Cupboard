package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.addDeckFont
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The font files Add Font File offers: TrueType, OpenType and collections of either. */
private val FontExtensions: List<String> = listOf("ttf", "otf", "ttc")

/**
 * A native open panel filtered to font files, several allowed, and every file
 * picked in it. [chooseImages]' panel otherwise: a family arrives as a handful
 * of cuts, and one visit should take all of them. Empty is a cancel.
 */
internal fun chooseFonts(owner: Frame): List<File> {
    val dialog = FileDialog(owner, "Add Font File", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.setFilenameFilter { _, name -> File(name).extension.lowercase() in FontExtensions }
    dialog.file = FontExtensions.joinToString(";") { "*.$it" }
    dialog.isVisible = true

    return dialog.files.orEmpty().filter { it.isFile }
}

/**
 * Each of [files] added to the deck, and the ones that weren't readable fonts
 * named in one report rather than one per file: a family dragged in whole with
 * a stray licence file in it is one mistake, not five.
 */
internal suspend fun EditorViewModel.addFontFiles(files: List<File>, owner: Frame) {
    val unread: List<File> = files.filterNot { file ->
        val bytes: ByteArray? = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
        bytes != null && addDeckFont(bytes, file.extension)
    }
    if (unread.isEmpty()) return

    JOptionPane.showMessageDialog(
        owner,
        unread.joinToString("\n") { file -> "Couldn't read ${file.name} as a font." },
        "Add Font File",
        JOptionPane.ERROR_MESSAGE,
    )
}
