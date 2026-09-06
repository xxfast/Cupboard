// Pictures on a slide: where the bytes come from, and nothing about what
// happens to them after. Three ways in, one way out: an Open panel, a drop on
// the window, and the pasteboard all end at `insertImage`, which takes bytes
// and an extension. Everything AppKit knows about images stops in this file;
// Kotlin has never heard of NSImage or a pasteboard type.
import SwiftUI
import AppKit
import UniformTypeIdentifiers
import CupboardCanvas

enum Media {
    // MARK: Panels

    /// The picture the user picks, on the slide. The panel is modal, so this
    /// returns once the insert is on its way: the write and the decode are
    /// Kotlin's, off the main thread, and the element lands when they are done.
    static func insert(into host: EditorHost) {
        pick(prompt: "Insert") { bytes, extension_ in
            host.insertImage(bytes: bytes, extension: extension_)
        }
    }

    /// New bytes behind the selected image, its frame left where it is.
    static func replace(in host: EditorHost) {
        pick(prompt: "Replace") { bytes, extension_ in
            host.replaceImage(bytes: bytes, extension: extension_)
        }
    }

    /// Anything the system calls an image, which is what both panels filter on
    /// and what a dropped file has to be to land on the slide.
    private static func pick(prompt: String, _ deliver: (Data, String) -> Void) {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [.image]
        panel.prompt = prompt

        guard panel.runModal() == .OK, let url = panel.url else { return }
        // A file that will not read inserts nothing rather than an empty frame:
        // a picture the user picked and cannot see is worse than no picture.
        guard let bytes = try? Data(contentsOf: url) else { return }
        deliver(bytes, url.pathExtension)
    }

    // MARK: Drops

    /// Images dropped on the editor. A file lands as its own bytes, so a PNG
    /// dragged from Finder keeps its compression; a picture dragged out of
    /// another app arrives as an NSImage and becomes a PNG on the way in.
    ///
    /// Loading is asynchronous, which is why this says it took the drop before
    /// the bytes are here: the answer is whether the providers hold anything a
    /// slide can take, not whether it has landed yet.
    @discardableResult
    static func drop(_ providers: [NSItemProvider], into host: EditorHost) -> Bool {
        var taken = false
        for provider in providers {
            if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
                taken = true
                provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier) { item, _ in
                    guard
                        let raw = item as? Data,
                        let url = URL(dataRepresentation: raw, relativeTo: nil),
                        isImage(url),
                        let bytes = try? Data(contentsOf: url)
                    else { return }
                    let extension_ = url.pathExtension
                    // The host is main-thread only, and a provider calls back
                    // on whichever thread it finished on.
                    DispatchQueue.main.async {
                        host.insertImage(bytes: bytes, extension: extension_)
                    }
                }
                continue
            }

            guard provider.canLoadObject(ofClass: NSImage.self) else { continue }
            taken = true
            _ = provider.loadObject(ofClass: NSImage.self) { object, _ in
                guard let image = object as? NSImage, let bytes = png(of: image) else { return }
                DispatchQueue.main.async { host.insertImage(bytes: bytes, extension: "png") }
            }
        }
        return taken
    }

    // MARK: Pasteboard

    /// Whether Cmd+V has a picture to put on the slide. Cheap enough for a menu
    /// to ask every time it opens: it reads the types on the pasteboard, not
    /// the bytes behind them.
    ///
    /// Only ever consulted when the editor's own clipboard is empty. Copying an
    /// element inside Cupboard puts nothing on the system pasteboard, so the two
    /// never both have something and there is nothing to choose between.
    static func pasteboardHasImage() -> Bool {
        let pasteboard = NSPasteboard.general
        let types = [UTType.png.identifier, UTType.tiff.identifier]
        if pasteboard.canReadItem(withDataConformingToTypes: types) { return true }
        guard let urls = pasteboard.readObjects(forClasses: [NSURL.self]) as? [URL] else {
            return false
        }
        return urls.contains(where: isImage)
    }

    /// The pasteboard's picture on the slide, and whether there was one. A file
    /// copied in Finder keeps its own bytes; a screenshot arrives as TIFF and
    /// becomes a PNG, which is a third of the size and keeps its alpha.
    @discardableResult
    static func paste(into host: EditorHost) -> Bool {
        guard let (bytes, extension_) = pasteboardImage() else { return false }
        host.insertImage(bytes: bytes, extension: extension_)
        return true
    }

    private static func pasteboardImage() -> (Data, String)? {
        let pasteboard = NSPasteboard.general
        if
            let urls = pasteboard.readObjects(forClasses: [NSURL.self]) as? [URL],
            let url = urls.first(where: isImage),
            let bytes = try? Data(contentsOf: url)
        {
            return (bytes, url.pathExtension)
        }
        if let bytes = pasteboard.data(forType: .png) { return (bytes, "png") }
        if let tiff = pasteboard.data(forType: .tiff), let bytes = png(of: tiff) {
            return (bytes, "png")
        }
        return nil
    }

    // MARK: Bytes

    private static func isImage(_ url: URL) -> Bool {
        UTType(filenameExtension: url.pathExtension)?.conforms(to: .image) ?? false
    }

    private static func png(of image: NSImage) -> Data? {
        image.tiffRepresentation.flatMap(png(of:))
    }

    private static func png(of tiff: Data) -> Data? {
        NSBitmapImageRep(data: tiff)?.representation(using: .png, properties: [:])
    }
}
