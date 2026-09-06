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

    /// The pictures the user picks, as one gallery: several in one box, shown
    /// one at a time. The same panel with multiple selection on, so picking one
    /// file makes a gallery of one rather than being refused.
    static func insertGallery(into host: EditorHost) {
        pickMany(prompt: "Insert") { files, extensions in
            host.insertGallery(files: files, extensions: extensions)
        }
    }

    /// More pictures on the end of the selected gallery, in the order picked.
    static func addToGallery(in host: EditorHost) {
        pickMany(prompt: "Add") { files, extensions in
            host.addGalleryImages(files: files, extensions: extensions)
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

    /// `pick`'s plural: the same panel, several files, and two parallel arrays
    /// on the way out because that is the shape Kotlin takes them in. Files that
    /// will not read are left out rather than passed on as gaps.
    private static func pickMany(prompt: String, _ deliver: ([Data], [String]) -> Void) {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.allowsMultipleSelection = true
        panel.allowedContentTypes = [.image]
        panel.prompt = prompt

        guard panel.runModal() == .OK else { return }
        let loaded: [(Data, String)] = panel.urls.compactMap { url in
            guard let bytes = try? Data(contentsOf: url) else { return nil }
            return (bytes, url.pathExtension)
        }
        guard !loaded.isEmpty else { return }
        deliver(loaded.map(\.0), loaded.map(\.1))
    }

    // MARK: Drops

    /// Images dropped on the editor. A file lands as its own bytes, so a PNG
    /// dragged from Finder keeps its compression; a picture dragged out of
    /// another app arrives as an NSImage and becomes a PNG on the way in.
    ///
    /// One picture is an image, two or more at once are a gallery: dropping a
    /// folder of frame captures means one box you click through, not a dozen
    /// elements stacked in the middle of the slide. So the whole drop is
    /// gathered before anything lands, which is what `Basket` is for.
    ///
    /// Loading is asynchronous, which is why this says it took the drop before
    /// the bytes are here: the answer is whether the providers hold anything a
    /// slide can take, not whether it has landed yet.
    @discardableResult
    static func drop(_ providers: [NSItemProvider], into host: EditorHost) -> Bool {
        let basket = Basket(count: providers.count)
        let group = DispatchGroup()
        var taken = false

        for (index, provider) in providers.enumerated() {
            if provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
                taken = true
                group.enter()
                provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier) { item, _ in
                    defer { group.leave() }
                    guard
                        let raw = item as? Data,
                        let url = URL(dataRepresentation: raw, relativeTo: nil),
                        isImage(url),
                        let bytes = try? Data(contentsOf: url)
                    else { return }
                    basket.put(index, bytes, url.pathExtension)
                }
                continue
            }

            guard provider.canLoadObject(ofClass: NSImage.self) else { continue }
            taken = true
            group.enter()
            _ = provider.loadObject(ofClass: NSImage.self) { object, _ in
                defer { group.leave() }
                guard let image = object as? NSImage, let bytes = png(of: image) else { return }
                basket.put(index, bytes, "png")
            }
        }

        guard taken else { return false }
        // The host is main-thread only, and a provider calls back on whichever
        // thread it finished on.
        group.notify(queue: .main) { insert(basket.loaded, into: host) }
        return true
    }

    /// One picture on the slide, or several as one gallery. What every way in
    /// ends at once its bytes are here.
    private static func insert(_ loaded: [(Data, String)], into host: EditorHost) {
        guard let first = loaded.first else { return }
        guard loaded.count > 1 else {
            host.insertImage(bytes: first.0, extension: first.1)
            return
        }
        host.insertGallery(files: loaded.map(\.0), extensions: loaded.map(\.1))
    }

    /// What a drop is gathered into: one slot per provider, filled in whatever
    /// order they finish, read in the order they were dropped. A class rather
    /// than a captured array because the closures that fill it escape onto
    /// whichever thread each provider ended up on.
    private final class Basket {
        private let lock = NSLock()
        private var slots: [(Data, String)?]

        init(count: Int) {
            slots = Array(repeating: nil, count: count)
        }

        func put(_ index: Int, _ bytes: Data, _ extension_: String) {
            lock.lock()
            defer { lock.unlock() }
            guard slots.indices.contains(index) else { return }
            slots[index] = (bytes, extension_)
        }

        /// The ones that loaded, in drop order. The providers that held nothing
        /// a slide can take are simply not here.
        var loaded: [(Data, String)] {
            lock.lock()
            defer { lock.unlock() }
            return slots.compactMap { $0 }
        }
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
