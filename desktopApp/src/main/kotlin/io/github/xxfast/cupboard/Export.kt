package io.github.xxfast.cupboard

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.export.ExportedFile
import io.github.xxfast.cupboard.export.toCupProject
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane

/**
 * The generator names the project, we only have to read it back: the slug it
 * builds from the deck's name is internal to `:cupboard`, so the emitted
 * settings file is what tells us which folder to write into.
 */
private val RootProjectName = Regex("""rootProject\.name\s*=\s*"([^"]+)"""")

/** The AWT property that turns the mac's open panel into a directory chooser. */
private const val MacDirectoryDialog: String = "apple.awt.fileDialogForDirectories"

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
    val parent: File = chooseDirectory(owner) ?: return
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
 * A native directory chooser. macOS has no Swing panel worth showing, so the
 * AWT one is coaxed into picking folders; everywhere else Swing's chooser is
 * the one that can do it at all.
 */
private fun chooseDirectory(owner: Frame): File? =
    if (isMacOs) macDirectory(owner) else swingDirectory(owner)

private fun macDirectory(owner: Frame): File? {
    val previous: String? = System.getProperty(MacDirectoryDialog)
    System.setProperty(MacDirectoryDialog, "true")
    try {
        val dialog = FileDialog(owner, DialogTitle, FileDialog.LOAD)
        dialog.isVisible = true
        val directory: String = dialog.directory ?: return null
        val file: String = dialog.file ?: return null
        return File(directory, file)
    } finally {
        if (previous == null) System.clearProperty(MacDirectoryDialog)
        else System.setProperty(MacDirectoryDialog, previous)
    }
}

private fun swingDirectory(owner: Frame): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = DialogTitle
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    val picked: Int = chooser.showDialog(owner, "Export")
    return if (picked == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
