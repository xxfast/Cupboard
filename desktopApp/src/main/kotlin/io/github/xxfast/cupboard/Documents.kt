package io.github.xxfast.cupboard

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.awt.ComposeWindow
import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import kotlinx.io.files.Path

/**
 * The decks this app has open, one window each.
 *
 * A holder rather than loose state in `application`, because every File verb is
 * about the set rather than about one deck: a second Open of a deck already up
 * raises its window instead of opening it twice, and a Save As swaps one entry
 * for another. The window whose menu was used is only ever the dialog's owner.
 *
 * Nothing here asks whether a deck is dirty. Autosave means closing a window
 * loses nothing, so a close is a close.
 */
internal class Documents(first: EditorViewModel) {
    /** The open editors, in the order their windows appeared. */
    val editors: SnapshotStateList<EditorViewModel> = mutableStateListOf(first)

    /**
     * Each editor's window, so an Open of a deck that is already up can raise
     * it. Keyed by the view model rather than by [EditorViewModel.location]:
     * the list is keyed that way too, and a Save As replaces the object.
     */
    private val frames: MutableMap<EditorViewModel, ComposeWindow> = mutableMapOf()

    fun register(viewModel: EditorViewModel, frame: ComposeWindow) {
        frames[viewModel] = frame
    }

    /** A fresh deck in Cupboard's own folder, opened like any other. */
    fun new(owner: Frame) {
        open(Cupboard.newDocument(), owner)
    }

    /**
     * The deck at [bundle] in a window: a new one, or the one already showing it.
     *
     * A failure is the core's own sentence in an alert. This shell has no
     * business rewording it, see [OpenResult.Failed].
     */
    fun open(bundle: Path, owner: Frame) {
        val showing: EditorViewModel? = editors.firstOrNull { it.location == bundle.toString() }
        if (showing != null) {
            frames[showing]?.apply { toFront(); requestFocus() }
            return
        }

        when (val result: OpenResult = Cupboard.openDocument(bundle)) {
            is OpenResult.Failed -> JOptionPane.showMessageDialog(
                owner,
                result.reason,
                "Can't Open Deck",
                JOptionPane.ERROR_MESSAGE,
            )

            is OpenResult.Opened -> editors += result.viewModel
        }
    }

    /**
     * [viewModel]'s deck copied to [target], with its window carrying on over
     * the copy.
     *
     * The editor that comes back is a new one, since a window's deck is fixed
     * for its life, so it goes into the same slot and the old one is closed:
     * one window, still where it was, now on the new bundle.
     */
    fun saveAs(viewModel: EditorViewModel, target: Path) {
        val index: Int = editors.indexOf(viewModel)
        if (index < 0) return

        editors[index] = Cupboard.saveAs(viewModel, target)
        frames -= viewModel
        viewModel.close()
    }

    /** Puts this deck's window away, and says whether that was the last of them. */
    fun close(viewModel: EditorViewModel): Boolean {
        editors -= viewModel
        frames -= viewModel
        viewModel.close()
        return editors.isEmpty()
    }
}

/** What a bundle is called in a menu: its folder name, without the extension. */
internal fun deckName(location: String): String =
    Path(location).name.removeSuffix(".${CupboardBundle.EXTENSION}")

/** A `.cupboard` to open. A bundle is a folder, so this is a folder chooser. */
internal fun chooseBundle(owner: Frame): Path? =
    chooseDirectory(owner, "Open Deck", "Open")?.let { Path(it.path) }

/**
 * Where a Save As should go, as a name in a folder.
 *
 * AWT's save panel rather than the folder chooser Open uses: this one is naming
 * a bundle that doesn't exist yet, and only the save panel has a name field.
 * The extension is offered but not enforced here, `Cupboard.saveAs` puts it back
 * on whatever comes out.
 */
internal fun chooseSaveBundle(owner: Frame, name: String): Path? {
    val dialog = FileDialog(owner, "Save As", FileDialog.SAVE)
    dialog.file = "$name.${CupboardBundle.EXTENSION}"
    dialog.isVisible = true

    val directory: String = dialog.directory ?: return null
    val file: String = dialog.file ?: return null
    return Path(File(directory, file).path)
}

/** The AWT property that turns the mac's open panel into a directory chooser. */
private const val MacDirectoryDialog: String = "apple.awt.fileDialogForDirectories"

/**
 * A native directory chooser. macOS has no Swing panel worth showing, so the
 * AWT one is coaxed into picking folders; everywhere else Swing's chooser is
 * the one that can do it at all.
 */
internal fun chooseDirectory(owner: Frame, title: String, approve: String): File? =
    if (isMacOs) macDirectory(owner, title) else swingDirectory(owner, title, approve)

private fun macDirectory(owner: Frame, title: String): File? {
    val previous: String? = System.getProperty(MacDirectoryDialog)
    System.setProperty(MacDirectoryDialog, "true")
    try {
        val dialog = FileDialog(owner, title, FileDialog.LOAD)
        dialog.isVisible = true
        val directory: String = dialog.directory ?: return null
        val file: String = dialog.file ?: return null
        return File(directory, file)
    } finally {
        if (previous == null) System.clearProperty(MacDirectoryDialog)
        else System.setProperty(MacDirectoryDialog, previous)
    }
}

private fun swingDirectory(owner: Frame, title: String, approve: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    val picked: Int = chooser.showDialog(owner, approve)
    return if (picked == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/**
 * A new name for the deck, or null if the dialog was dismissed.
 *
 * Swing's input box rather than a Compose dialog, the same call this shell's
 * export report makes: one line of text is not worth a material3 dependency.
 */
internal fun askDocumentName(owner: Frame, current: String): String? {
    val name: String? = JOptionPane.showInputDialog(
        owner,
        "Name:",
        "Rename Deck",
        JOptionPane.PLAIN_MESSAGE,
        null,
        null,
        current,
    ) as String?

    return name?.trim()?.takeIf { it.isNotEmpty() && it != current }
}
