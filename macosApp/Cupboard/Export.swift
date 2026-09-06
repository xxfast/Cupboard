import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Export

/// File > Export as CuP Project: the deck as a standalone Gradle project the
/// user can open and run. Kotlin generates the files, this writes them.
///
/// The chosen directory is where the project lands, not the project itself: a
/// folder named after the deck goes inside it, the way Xcode and Keynote both
/// export. Finder opens on it afterwards, which is the only confirmation an
/// export of files needs.
func exportCupProject(host: EditorHost) {
    let panel = NSOpenPanel()
    panel.canChooseDirectories = true
    panel.canChooseFiles = false
    panel.canCreateDirectories = true
    panel.allowsMultipleSelection = false
    panel.prompt = "Export"
    panel.message = "Choose where to put the exported project."

    guard panel.runModal() == .OK, let directory = panel.url else { return }
    let root = directory.appendingPathComponent(host.cupProjectName(), isDirectory: true)

    do {
        try writeProject(host.cupProject(), into: root)
    } catch {
        presentExportFailure(error)
        return
    }
    NSWorkspace.shared.activateFileViewerSelecting([root])
}

/// Writes every generated file under [root], making the directories on the way
/// down. Overwrites: an export is the document as it stands right now, so a
/// second one onto the same folder should say what the first would have.
private func writeProject(_ files: [ExportFile], into root: URL) throws {
    let manager = FileManager.default
    try manager.createDirectory(at: root, withIntermediateDirectories: true)
    for file in files {
        let url = root.appendingPathComponent(file.path)
        try manager.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try file.contents.write(to: url, atomically: true, encoding: .utf8)
    }
}

/// A half-written project is still on disk when this shows, deliberately: the
/// user picked the folder, so what did get there is theirs to look at.
private func presentExportFailure(_ error: Error) {
    let alert = NSAlert()
    alert.alertStyle = .warning
    alert.messageText = "Could not export the project."
    alert.informativeText = error.localizedDescription
    alert.runModal()
}
