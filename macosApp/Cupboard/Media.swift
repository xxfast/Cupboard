// Pictures, movies and sounds on a slide: where the bytes come from, and
// nothing about what happens to them after. Three ways in: an Open panel, a
// drop on the window, and the pasteboard, all sorted by the core's `mediaKind`
// and ending at `insertImage`, which takes bytes and an extension, or at
// `insertMovie` or `insertAudio`, which take the file's path and copy it into
// the deck themselves: a recording is too big to hand over as bytes.
// An SVG goes through `insertImage` too, and comes out a shape. Everything AppKit and AVFoundation know about media stops
// in this file; Kotlin has never heard of NSImage, AVAsset or a pasteboard type.
import SwiftUI
import AppKit
import AVFoundation
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

    /// The movie the user picks, on the slide, with a poster grabbed from it.
    static func insertMovie(into host: EditorHost) {
        guard let url = pickFile(prompt: "Insert", types: types(host.movieExtensions())) else { return }
        insertMovie(url, into: host)
    }

    /// The sound the user picks, on the slide as a pill named after the file.
    static func insertAudio(into host: EditorHost) {
        guard let url = pickFile(prompt: "Insert", types: types(host.soundExtensions())) else { return }
        insertAudio(url, into: host)
    }

    /// A movie that lives on the web: an address in a small alert, and a box on
    /// the slide that opens it when clicked in the show.
    static func insertWebVideo(into host: EditorHost) {
        let alert = NSAlert()
        alert.messageText = "Insert Web Video"
        alert.informativeText = "The address of a video online. It opens in the browser when clicked during the show."
        let field = NSTextField(frame: NSRect(x: 0, y: 0, width: 300, height: 22))
        field.placeholderString = "https://"
        alert.accessoryView = field
        alert.addButton(withTitle: "Insert")
        alert.addButton(withTitle: "Cancel")
        alert.window.initialFirstResponder = field

        guard alert.runModal() == .alertFirstButtonReturn else { return }
        host.insertWebVideo(url: field.stringValue)
    }

    /// A new file behind the selected movie or sound, its box left where it is.
    /// Handed over as a path: Kotlin copies it into the deck, file to file.
    /// A movie gets a fresh poster off the new file, since the old still was a
    /// picture of something else.
    static func replaceMedia(in host: EditorHost, movie: Bool) {
        let extensions = movie ? host.movieExtensions() : host.soundExtensions()
        guard let url = pickFile(prompt: "Replace", types: types(extensions)) else { return }
        guard movie else {
            host.replaceMedia(path: url.path, extension: url.pathExtension, poster: nil)
            return
        }
        Task { @MainActor in
            let peek = await MoviePeek.of(url)
            host.replaceMedia(path: url.path, extension: url.pathExtension, poster: peek.poster)
        }
    }

    /// A picture of the user's choosing as the selected movie's poster.
    static func choosePoster(in host: EditorHost) {
        pick(prompt: "Choose") { bytes, extension_ in
            host.setMoviePoster(bytes: bytes, extension: extension_)
        }
    }

    /// A picture inside every selected shape. A cancelled panel leaves the fill
    /// as it was, which is what makes picking the Image segment safe to undo by Esc.
    static func chooseShapeFill(in host: EditorHost) {
        pick(prompt: "Choose") { bytes, extension_ in
            host.setSelectedShapeImageFill(bytes: bytes, extension: extension_)
        }
    }

    /// A picture behind the selected slide, or the layout being edited.
    static func chooseBackground(in host: EditorHost) {
        pick(prompt: "Choose") { bytes, extension_ in
            host.setBackgroundImage(bytes: bytes, extension: extension_)
        }
    }

    /// A picture behind every slide that asks for no background of its own.
    static func chooseDeckBackground(in host: EditorHost) {
        pick(prompt: "Choose") { bytes, extension_ in
            host.setDeckBackgroundImage(bytes: bytes, extension: extension_)
        }
    }

    /// [url]'s movie on the slide. The poster and the size come off the file
    /// first, which takes AVFoundation a moment, so the insert lands after.
    /// A file AVFoundation cannot read still inserts, as the dark box.
    ///
    /// Only the path crosses over. The movie itself is never read here: Kotlin
    /// copies it into the deck file to file, and one it cannot copy inserts
    /// nothing.
    private static func insertMovie(_ url: URL, into host: EditorHost) {
        let extension_ = url.pathExtension
        let title = url.deletingPathExtension().lastPathComponent
        Task { @MainActor in
            let peek = await MoviePeek.of(url)
            host.insertMovie(
                path: url.path,
                extension: extension_,
                title: title,
                poster: peek.poster,
                naturalWidth: Int32(peek.width),
                naturalHeight: Int32(peek.height)
            )
        }
    }

    /// [url]'s sound on the slide, by its path, for `insertMovie`'s reason.
    private static func insertAudio(_ url: URL, into host: EditorHost) {
        host.insertAudio(
            path: url.path,
            extension: url.pathExtension,
            title: url.deletingPathExtension().lastPathComponent
        )
    }

    /// One file of [types], or nil if the panel was cancelled.
    private static func pickFile(prompt: String, types: [UTType]) -> URL? {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.canChooseFiles = true
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = types
        panel.prompt = prompt
        guard panel.runModal() == .OK else { return nil }
        return panel.url
    }

    /// The core's extension list as content types. An extension the system has
    /// never heard of still gets a dynamic type, so the panel still offers it.
    private static func types(_ extensions: [String]) -> [UTType] {
        extensions.compactMap { UTType(filenameExtension: $0) }
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

    /// Files and images dropped on the editor. A file lands as its own bytes,
    /// so a PNG dragged from Finder keeps its compression; a picture dragged out
    /// of another app arrives as an NSImage and becomes a PNG on the way in.
    ///
    /// Movies and sounds are sorted out by `mediaKind` and land one element
    /// each, as they finish loading: a movie and a voice memo dropped together
    /// are two things on the slide, not a gallery.
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
                        let url = URL(dataRepresentation: raw, relativeTo: nil)
                    else { return }
                    if isMovieOrSound(url, host) {
                        DispatchQueue.main.async { insertMovieOrSound(url, into: host) }
                        return
                    }
                    guard isImage(url), let bytes = try? Data(contentsOf: url) else { return }
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
    /// ends at once its bytes are here. An SVG is a shape, not a picture, so it
    /// never joins a gallery: each goes in on its own.
    private static func insert(_ loaded: [(Data, String)], into host: EditorHost) {
        let isVector: ((Data, String)) -> Bool = { host.mediaKindOf(extension: $0.1) == "Vector" }
        for vector in loaded.filter(isVector) {
            host.insertImage(bytes: vector.0, extension: vector.1)
        }
        let loaded = loaded.filter { !isVector($0) }
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
    ///
    /// A movie or a sound copied in Finder counts too: it pastes the way it
    /// drops.
    static func pasteboardHasMedia(_ host: EditorHost) -> Bool {
        let pasteboard = NSPasteboard.general
        let types = [UTType.png.identifier, UTType.tiff.identifier]
        if pasteboard.canReadItem(withDataConformingToTypes: types) { return true }
        guard let urls = pasteboard.readObjects(forClasses: [NSURL.self]) as? [URL] else {
            return false
        }
        return urls.contains { isImage($0) || isMovieOrSound($0, host) }
    }

    /// The pasteboard's picture, movie or sound on the slide, and whether there
    /// was one. A file copied in Finder keeps its own bytes; a screenshot
    /// arrives as TIFF and becomes a PNG, which is a third of the size and
    /// keeps its alpha.
    @discardableResult
    static func paste(into host: EditorHost) -> Bool {
        if
            let urls = NSPasteboard.general.readObjects(forClasses: [NSURL.self]) as? [URL],
            let url = urls.first(where: { isMovieOrSound($0, host) })
        {
            insertMovieOrSound(url, into: host)
            return true
        }
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

    /// Whether [url] is a file the core inserts as a movie or a sound.
    private static func isMovieOrSound(_ url: URL, _ host: EditorHost) -> Bool {
        let kind = host.mediaKindOf(extension: url.pathExtension)
        return kind == "Movie" || kind == "Sound"
    }

    /// [url] on the slide as whichever of the two it is.
    private static func insertMovieOrSound(_ url: URL, into host: EditorHost) {
        switch host.mediaKindOf(extension: url.pathExtension) {
        case "Movie": insertMovie(url, into: host)
        case "Sound": insertAudio(url, into: host)
        default: break
        }
    }

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

/// What a movie file says about itself before it is on a slide: a still from
/// near its start, PNG encoded, and the size its picture plays at. Either may be
/// missing (a format AVFoundation cannot open, a file with no video track), and
/// the insert goes ahead without it.
struct MoviePeek {
    var poster: Data? = nil
    var width = 0
    var height = 0

    static func of(_ url: URL) async -> MoviePeek {
        let asset = AVURLAsset(url: url)
        guard
            let track = try? await asset.loadTracks(withMediaType: .video).first,
            let (size, transform) = try? await track.load(.naturalSize, .preferredTransform)
        else { return MoviePeek() }

        // A phone's portrait movie is stored landscape and turned on playback,
        // so the size it plays at is its natural size through that turn.
        let turned = CGRect(origin: .zero, size: size).applying(transform)
        var peek = MoviePeek(
            width: Int(abs(turned.width).rounded()),
            height: Int(abs(turned.height).rounded())
        )

        // A second in, or a tenth of the way through a short clip: frame zero
        // is so often black from a fade that it makes a poor poster.
        let duration = (try? await asset.load(.duration)).map(CMTimeGetSeconds) ?? 0
        let seconds = duration.isFinite && duration > 0 ? min(1, duration * 0.1) : 0
        let generator = AVAssetImageGenerator(asset: asset)
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 1920, height: 1920)
        if let (image, _) = try? await generator.image(at: CMTime(seconds: seconds, preferredTimescale: 600)) {
            peek.poster = NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])
        }
        return peek
    }
}
