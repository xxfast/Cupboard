// Design v3 layered window: the Compose canvas is full-bleed edge to edge and
// the navigator card, notes strip, inspector and toolbar float over it. Layer
// order back to front: canvas, speaker notes, panels, toolbar, collapsed chrome.
// Only the canvas is Compose, bridged through EditorHost from CupboardCanvas.
// This file is the app entry and the EditorView shell; its panels live in
// extensions next door (Navigator, Toolbar, Inspector), the chrome tokens in
// Theme, the AppKit window work in Window, the menus in Menus.
// Build/run: ./macosApp/run.sh
import SwiftUI
import AppKit
import Observation
import UniformTypeIdentifiers
import CupboardCanvas

// MARK: - App

/// Activates the app once it finishes launching, takes the decks Finder hands
/// over, and decides when the theme chooser is the window to show.
///
/// Activation: launched by exec'ing the binary, which is what run.sh does to
/// keep logs on the terminal, LaunchServices never activates the process, and
/// SwiftUI holds the WindowGroup's window back until the first activation
/// (instrumented: zero windows ever exist before it), so the window only
/// appeared after a dock click sent activate + reopen. A Finder or `open` launch
/// never needed this; it makes the dev loop behave like one.
///
/// Opening: a double-clicked `.cupboard` arrives here because the bundle claims
/// the type. The window that shows it is SwiftUI's to open, and `openWindow` is
/// an environment value a delegate cannot read, so the paths go to
/// [OpenRequests], which holds the app's own `openWindow` and opens them once
/// launching is done.
///
/// The chooser: it is the first scene, so it is the window SwiftUI opens for a
/// launch with nothing to open, Keynote's way. AppKit delivers a launch's files
/// between will- and did-finish-launching, and only asks about an untitled
/// window when there were none, so a deck launch never gets a chooser. Reopen
/// and `--showcase` are the two cases that need a word from here.
final class ActivationDelegate: NSObject, NSApplicationDelegate {
    /// `--showcase` launches on the showcase alone, exactly as if Finder had
    /// handed it over. Without the registration, window restoration brings back
    /// the decks from last time alongside it. Registered rather than set, so it
    /// lasts this launch and never reaches the defaults.
    func applicationWillFinishLaunching(_ notification: Notification) {
        guard CommandLine.arguments.contains("--showcase") else { return }
        UserDefaults.standard.register(defaults: ["ApplePersistenceIgnoreState": true])
        OpenRequests.shared.request([Documents.shared.resetShowcase()])
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        OpenRequests.shared.finishLaunching()
        NSApp.activate()
    }

    /// AppKit asks this for a launch that brought no files, which `--showcase`
    /// is as far as it knows: the deck it opens is ours, not Finder's.
    func applicationShouldOpenUntitledFile(_ sender: NSApplication) -> Bool {
        !OpenRequests.shared.launchedOnDecks
    }

    /// A Dock click with nothing on screen is a click to make a deck. Minimised
    /// decks are AppKit's to bring back, so only an app with none open gets the
    /// chooser.
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        guard !flag, DocumentStore.shared.isEmpty else { return true }
        OpenRequests.shared.showChooser()
        return false
    }

    func application(_ application: NSApplication, open urls: [URL]) {
        OpenRequests.shared.request(urls.map(\.path))
    }
}

@main
struct CupboardHostApp: App {
    @NSApplicationDelegateAdaptor(ActivationDelegate.self) private var activation
    /// The window the menu bar is about. Nil with no deck in front (only the
    /// chooser up, or every window closed), which greys the editor's commands
    /// the way Keynote's go with no document open.
    @FocusedValue(\.editor) private var focusedEditor: EditorModel?
    @Environment(\.openWindow) var openWindow
    /// Whether the Play menu wants a presenter display. Remembered across
    /// launches: a lectern setup is not something to re-pick every show.
    @AppStorage("showPresenterDisplay") private var showPresenter = true
    /// In Full Screen or In Window, which Play Slideshow and the toolbar's Play
    /// both follow. Sticky for the same reason.
    @AppStorage("playInWindow") private var playInWindow = false

    var model: EditorModel? { focusedEditor }

    private var host: EditorHost? { model?.host }

    var body: some Scene {
        // The app's own openWindow, for the delegate's opens: it works with no
        // window up, which a window's would not.
        let _ = OpenRequests.shared.attach(openWindow)
        // File > New's theme chooser: one window, raised again by a second Cmd+N.
        // First, so it is the window a bare launch opens: there is no default
        // deck, so a launch with nothing to open is a launch to make one.
        Window("New Presentation", id: ThemeChooserView.windowId) {
            ThemeChooserView(onCreate: { openDocument(at: $0) })
        }
        .windowStyle(.hiddenTitleBar)
        .defaultSize(width: 1024, height: 768)
        // One window per open deck.
        WindowGroup("Cupboard", for: DocumentRef.self) { $ref in
            DocumentWindow(ref: $ref)
        }
        .windowStyle(.hiddenTitleBar)
        .commands {
            fileCommands
            exportCommands
            // Ours, not AppKit's: the history lives in Kotlin, so the system
            // undo manager has nothing to say about it.
            CommandGroup(replacing: .undoRedo) {
                // Reading generation is what keeps these enabled states fresh.
                let _ = model?.generation
                Button("Undo") { host?.undo() }
                    .keyboardShortcut("z", modifiers: .command)
                    .disabled(host?.canUndo() != true)
                Button("Redo") { host?.redo() }
                    .keyboardShortcut("z", modifiers: [.command, .shift])
                    .disabled(host?.canRedo() != true)
            }
            // Same menu, below Undo/Redo. Backspace is the key equivalent, which
            // is also what makes it fire for a selection made on the canvas.
            CommandGroup(after: .undoRedo) {
                let _ = model?.generation
                Divider()
                Button("Delete") { host?.deleteSelection() }
                    .keyboardShortcut(.delete, modifiers: [])
                    .disabled(host?.canDelete() != true)
                Button("Clear All") { host?.clearAll() }
                    .disabled(host?.canClearAll() != true)
            }
            // Replacing rather than adding: the system Cut/Copy/Paste items own
            // Cmd+X/C/V and would shadow ours. The clipboard is the app's own,
            // so nothing here has any business talking to NSPasteboard.
            // doCopy... is the exporter's doing: copy is a reserved ObjC method
            // family, so every copyX on the host arrives here as doCopyX.
            CommandGroup(replacing: .pasteboard) {
                let _ = model?.generation
                Button("Cut") { host?.cutSelection() }
                    .keyboardShortcut("x", modifiers: .command)
                    .disabled(host?.canCut() != true)
                Button("Copy") { host?.doCopySelection() }
                    .keyboardShortcut("c", modifiers: .command)
                    .disabled(host?.canCopy() != true)
                // The one exception to the clipboard being the app's own: with
                // nothing of ours to paste, Cmd+V takes a picture off the system
                // pasteboard. The two can never both have something, since
                // copying an element here puts nothing on NSPasteboard.
                Button("Paste") {
                    guard let host else { return }
                    if host.canPaste() { host.paste() } else { Media.paste(into: host) }
                }
                .keyboardShortcut("v", modifiers: .command)
                .disabled(host.map { !$0.canPaste() && !Media.pasteboardHasMedia($0) } ?? true)
                Button("Duplicate") { host?.duplicateSelection() }
                    .keyboardShortcut("d", modifiers: .command)
                    .disabled(host?.canDuplicate() != true)

                Divider()

                Button("Copy Style") { host?.doCopyStyle() }
                    .keyboardShortcut("c", modifiers: [.command, .option])
                    .disabled(host?.canCopyStyle() != true)
                Button("Paste Style") { host?.pasteStyle() }
                    .keyboardShortcut("v", modifiers: [.command, .option])
                    .disabled(host?.canPasteStyle() != true)
            }
            // The View menu, below the system sidebar item: what the chrome
            // shows, then what the canvas draws over the slide. Reading
            // generation is what keeps the checkmarks fresh; one snapshot below
            // it, so every item in the menu is answering about the same state.
            // With no deck in front there is nothing to show, so only the
            // system's items are left.
            CommandGroup(after: .sidebar) {
                let _ = model?.generation
                if let host {
                    let ui = Chrome(host)
                    Toggle("Show Speaker Notes", isOn: Binding(
                        get: { ui.showNotes },
                        set: { _ in host.toggleNotes() }
                    ))
                    Toggle("Show Object List", isOn: Binding(
                        get: { ui.objectListOpen },
                        set: { _ in host.toggleObjectList() }
                    ))

                    Divider()

                    Toggle("Show Rulers", isOn: Binding(
                        get: { ui.showRulers },
                        set: { _ in host.toggleRulers() }
                    ))
                    .keyboardShortcut("r", modifiers: .command)
                    Toggle("Show Guides", isOn: Binding(
                        get: { ui.showGuides },
                        set: { _ in host.toggleGuides() }
                    ))
                    Menu("Snap to") {
                        SnapToggles(host: host, snap: ui.snap)
                    }
                    // Keynote's order and shortcuts. The pill reads the same zoom.
                    Menu("Zoom") {
                        Button("Zoom In") { host.zoomIn() }
                            .keyboardShortcut(".", modifiers: [.command, .shift])
                        Button("Zoom Out") { host.zoomOut() }
                            .keyboardShortcut(",", modifiers: [.command, .shift])
                        Button("Actual Size") { host.setZoom(percent: 100) }
                            .keyboardShortcut("0", modifiers: .command)
                        Divider()
                        Button("Fit Slide") { host.setZoom(percent: 0) }
                            .keyboardShortcut("0", modifiers: [.command, .option])
                        Divider()
                        Button("Zoom to Selection") { host.zoomToSelection() }
                            .keyboardShortcut("0", modifiers: [.command, .shift])
                            .disabled(!ui.canZoomToSelection)
                        Button("Zoom to Fit Content") { host.zoomToFitContent() }
                            .keyboardShortcut("0", modifiers: [.command, .option, .shift])
                    }

                    Divider()

                    // One item, titled by which way it goes: layout mode is a place
                    // the editor is in, not a setting, so this is a verb rather than
                    // a checkmark.
                    Button(ui.editingLayouts ? "Exit Slide Layouts" : "Edit Slide Layouts") {
                        if ui.editingLayouts { host.exitSlideLayouts() } else { host.editSlideLayouts() }
                    }
                }
            }
            // Grouped because CommandsBuilder takes ten statements and these
            // are more: each Group is one of them again. Play comes straight
            // after View, where Keynote has it.
            Group {
                playMenu
                insertMenu
            }
            slideMenu
            formatMenu
            Group {
                arrangeMenu
                helpCommands
            }
        }
    }

    /// What can go on a slide. The shape submenu is the catalog's, in its order:
    /// the list is stated in Kotlin and only rendered here, so a shape added to
    /// the document model shows up with nothing to change. Nothing reads
    /// generation: an insertion needs no selection, and every item is always live.
    private var insertMenu: some Commands {
        CommandMenu("Insert") {
            if let host { MenuEntries(entries: insertEntries(host)) }
        }
    }

    /// Text style, for whatever text is selected. Cmd+B/I/U work with the caret
    /// up as well: formatting is a property of the whole box, and the menu bar
    /// sees key equivalents before the in-place editor does.
    private var formatMenu: some Commands {
        CommandMenu("Format") {
            // Reading generation is what keeps these enabled states fresh.
            let _ = model?.generation
            if let host { MenuEntries(entries: formatEntries(host, enabled: host.canFormatText())) }
        }
    }

    /// Acts on the slide as a whole rather than what is on it. No key equivalents:
    /// Cmd+X/C/V belong to the elements on the canvas, and a slide-level clipboard
    /// stealing them would make the common case unreachable.
    private var slideMenu: some Commands {
        CommandMenu("Slide") {
            // Reading generation is what keeps these acting on the slide that is
            // selected now. Off the same list the navigator rows render, minus
            // Paste: in the bar that verb is Edit > Paste's.
            let _ = model?.generation
            if let host {
                MenuEntries(entries: slideEntries(host, slideId: nil, includePaste: false))

                Divider()
            }

            // Only in the bar: it acts on the selected slide, so a row menu
            // offering it would be pointing at one slide and moving another.
            Button("Reapply Layout") { host?.reapplyLayout() }
                .disabled(host?.canReapplyLayout() != true)

            // No key equivalent: Play owns the presentation gesture, and a
            // preview is the deliberate one you go to the menu for.
            Button("Preview Slide") {
                guard let window = model else { return }
                window.show = .preview(window.host.startPreview(onExit: { window.show = nil }))
            }
            .disabled(model == nil)
        }
    }

    /// Keynote's Play menu: the show, where it plays, the rehearsal, and the
    /// presenter display, which both places honour.
    private var playMenu: some Commands {
        CommandMenu("Play") {
            // Reading generation is what keeps the enabled states fresh.
            let _ = model?.generation
            // The toolbar button's rule: a layout is not a slide of the talk.
            // And one show at a time, since the key equivalent still reaches
            // the bar from a show in the window.
            Button("Play Slideshow") {
                guard let window = model else { return }
                window.show = .play(
                    window.host.startPlay(onExit: { window.show = nil }),
                    inWindow: playInWindow
                )
            }
            .keyboardShortcut("p", modifiers: [.command, .option])
            .disabled(model.map { Chrome($0.host).editingLayouts || $0.show != nil } ?? true)

            Divider()

            // A radio pair: which one is ticked is where the next show goes.
            Picker("Play", selection: $playInWindow) {
                Text("In Full Screen").tag(false)
                Text("In Window").tag(true)
            }
            .pickerStyle(.inline)
            .labelsHidden()

            Divider()

            // The show, presenter display only, from the selected slide. No key
            // equivalent, for the same reason the preview has none.
            Button("Rehearse Slideshow") {
                guard let window = model else { return }
                window.show = .rehearse(window.host.startRehearsal(onExit: { window.show = nil }))
            }
            .disabled(model == nil)

            Divider()

            // Ours rather than the document's: whether there is a presenter
            // display is a lectern preference, not a fact about the deck.
            Toggle("Show Presenter Display", isOn: $showPresenter)
            // The X key does this too, from either play window. No key
            // equivalent here: a bare letter in the bar would shadow typing
            // in the editor, and the shows are where it is wanted. Only a full
            // screen show across two displays has anything to swap.
            Button("Swap Displays") { model?.displays.swapDisplays() }
                .disabled(
                    model?.show?.stage != .screens
                        || model?.show?.rehearsal == true
                        || NSScreen.screens.count < 2
                )
        }
    }

    /// The Compose shell's Arrange menu, natively, off the same entry list the
    /// canvas context menu renders. One statement of the items, two renderings:
    /// the two menus cannot drift apart, only be built from different facts.
    private var arrangeMenu: some Commands {
        CommandMenu("Arrange") {
            // Reading generation is what keeps these enabled states fresh.
            let _ = model?.generation
            if let host { MenuEntries(entries: arrangeEntries(ArrangeFacts(Chrome(host)), host)) }
        }
    }
}

// MARK: - Window

/// One open deck, and everything a window rather than the app owns: which editor
/// it is on, what it is playing, and which bundle its value says it is.
///
/// The editor comes from [DocumentStore] rather than being made here: SwiftUI
/// re-inits a window's root view freely, and an editor per init would put several
/// autosaving view models on one bundle.
struct DocumentWindow: View {
    @Binding var ref: DocumentRef?
    /// Nil for a window with no deck to be on, which nothing in this build
    /// opens: a restored window from a build that still had a default deck, or
    /// one restored onto a bundle that no longer reads.
    @State private var model: EditorModel?
    @AppStorage("showPresenterDisplay") private var showPresenter = true
    @Environment(\.dismissWindow) private var dismissWindow

    init(ref: Binding<DocumentRef?>) {
        _ref = ref
        _model = State(initialValue: ref.wrappedValue.flatMap { DocumentStore.shared.model(for: $0) })
    }

    var body: some View {
        if let model {
            deck(model)
        } else {
            Color.clear.onAppear { abandon() }
        }
    }

    /// Goes, and leaves the chooser up if that leaves the app with nothing on
    /// screen. A turn later, so the decks restored alongside this window have
    /// had their chance to come up first.
    private func abandon() {
        dismissWindow()
        DispatchQueue.main.async {
            guard DocumentStore.shared.isEmpty else { return }
            OpenRequests.shared.showChooser()
        }
    }

    private func deck(_ model: EditorModel) -> some View {
        @Bindable var window = model
        return Group {
            // A show on the displays leaves this window alone: the editor stays
            // up behind it, the way it does under a full-screen Keynote. One in
            // the window takes it over, frame and all, until it ends.
            if let show = model.show, show.stage == .window {
                PlayCanvas(session: show.session)
                    .background(Color.black)
                    .background(PlayFrame().frame(width: 0, height: 0))
            } else {
                EditorView(model: model, show: $window.show)
            }
        }
        .ignoresSafeArea()
        .navigationTitle(titleOf(model))
        .background(WindowConfigurator(lights: model.lights).frame(width: 0, height: 0))
        .background(
            ShowBridge(
                show: model.show,
                presenterEnabled: showPresenter,
                displays: model.displays
            )
            .frame(width: 0, height: 0)
        )
        // Every menu command acts on the window with focus, and this is what
        // says which one that is.
        .focusedSceneValue(\.editor, model)
        // Rename, Move To and Save As each leave this window on another bundle.
        // The window's value follows it there, so a later Open of that deck
        // raises this window and a relaunch restores the deck where it is now.
        .onChange(of: locationOf(model)) { _, path in
            let moved = DocumentRef(path: path)
            guard let old = ref, moved != old else { return }
            DocumentStore.shared.rekey(old, to: moved)
            ref = moved
        }
        // The window is gone, so the editor behind it should stop: autosave runs
        // off the view model's scope, and one nobody is looking at still writes.
        .onDisappear {
            guard let ref else { return }
            DocumentStore.shared.release(ref)
        }
    }

    /// The deck's name for the Window menu. The title bar is hidden, so this is
    /// the only place it shows outside the toolbar.
    private func titleOf(_ model: EditorModel) -> String {
        let _ = model.generation
        return model.host.title()
    }

    private func locationOf(_ model: EditorModel) -> String {
        let _ = model.generation
        return model.host.location()
    }
}

// MARK: - Editor

/// The navigator's scroll content, as a coordinate space: row frames, the drag
/// and the drop line are all measured in it, so they cannot disagree.

struct EditorView: View {
    let model: EditorModel
    @Binding var show: Show?

    @Environment(\.colorScheme) var colorScheme
    /// The row being dragged, and where each row sits for the gap maths.
    @State var slideDrag: SlideDrag?
    @State var rowFrames: [String: CGRect] = [:]
    /// Where each Object List row sits, for its drag's gap maths. The drag
    /// itself is the core's (`host.objectDrag()`), so only the geometry is here.
    @State var objectRowFrames: [String: CGRect] = [:]
    /// Which build the Animate panel's editor is about, by its place in the
    /// slide's order, and the row being dragged with where each one sits. All
    /// panel state: a build is not something the document can be "on".
    @State var selectedBuild: Int?
    @State var buildDrag: BuildDrag?
    @State var buildRowFrames: [Int: CGRect] = [:]
    /// Save Theme's sheet, and the name being typed into it. Presentation only:
    /// the library itself is Kotlin's, and the name goes there on Save.
    @State var savingTheme = false
    @State var saveThemeText = ""
    /// Save Style's alert, and Rename's, which also holds the style it is about:
    /// the strip's context menu points at a swatch, and the id has to outlive the
    /// menu for the alert to know whose name is being typed.
    @State var savingStyle = false
    @State var saveStyleText = ""
    @State var renamingStyle = false
    @State var renameStyleId = ""
    @State var renameStyleText = ""
    /// The Slide Size dialogs. The picked preset waits here while the scale
    /// question is up, and the typed pair while the custom sheet is. Nothing the
    /// document knows about: the deck resizes when one of them is answered.
    @State var pendingSizePreset: Int?
    @State var customSize = false
    @State var customWidthText = ""
    @State var customHeightText = ""
    @State var customScaleContent = true
    /// How far Remove Background reaches from the corner pixel. Panel state, and
    /// deliberately not the document's: it is a knob on one run of the tool, not
    /// something an image wears.
    @State var backgroundTolerance: Double = 0.25
    /// The Play menu's In Full Screen / In Window, which the toolbar's Play
    /// follows as well.
    @AppStorage("playInWindow") var playInWindow = false

    var host: EditorHost { model.host }
    var palette: Palette { Palette.of(colorScheme) }

    /// Touching `generation` is what subscribes this view to store changes.
    var chrome: Chrome {
        let _ = model.generation
        return Chrome(host)
    }

    /// Canvas at the back, edge to edge; the notes strip over it; the two panels
    /// over that (the notes pass visibly behind the navigator card); the toolbar
    /// over the panels, so its right-hand controls float on the inspector glass.
    var body: some View {
        let ui = chrome
        return ZStack {
            ComposeCanvas(host: host)
                .background { NudgeKeys(host: host) }

            if ui.showNotes {
                notesStrip(ui)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .transition(.move(edge: .bottom))
            }

            panels(ui)

            toolbar(ui)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)

            if !ui.sidebarOpen {
                collapsedChrome
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        // Wide enough that the toolbar, at its fixed ~1094pt, still leaves the
        // deck name a hundred points to truncate into.
        .frame(minWidth: 1200, minHeight: 640)
        // The whole window takes a picture, panels included: a drop is aimed at
        // the deck rather than at a point on the slide, and it lands centred
        // wherever it was let go.
        .onDrop(of: [.image, .fileURL], isTargeted: nil) { providers in
            Media.drop(providers, into: host)
        }
        .background {
            TrafficLightTarget(lights: model.lights, sidebarOpen: ui.sidebarOpen)
                .frame(width: 0, height: 0)
        }
        .animation(.easeInOut(duration: 0.2), value: ui.sidebarOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.objectListOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.inspectorOpen)
        .animation(.easeInOut(duration: 0.2), value: ui.showNotes)
        // The well is painted Kotlin-side, so the appearance has to be pushed in.
        .onAppear { host.setDarkChrome(dark: colorScheme == .dark) }
        .onChange(of: colorScheme) { _, scheme in host.setDarkChrome(dark: scheme == .dark) }
    }

    func panels(_ ui: Chrome) -> some View {
        HStack(spacing: 0) {
            if ui.sidebarOpen {
                navigatorCard
                    .frame(width: Layout.sidebar)
                    .padding(.leading, Layout.cardLeading)
                    .padding(.top, Layout.cardTop)
                    .padding(.bottom, Layout.cardBottom)
                    .transition(.move(edge: .leading))
            }
            if ui.objectListOpen {
                // Below the toolbar row rather than beside the traffic lights,
                // Keynote's place for it: the toolbar keeps its leading edge,
                // and with the navigator hidden it clears the collapsed chrome.
                objectListCard
                    .frame(width: Layout.objectList)
                    .padding(.leading, Layout.cardLeading)
                    .padding(.top, Layout.header)
                    .padding(.bottom, Layout.cardBottom)
                    .transition(.move(edge: .leading).combined(with: .opacity))
            }
            Spacer(minLength: 0)
            if ui.inspectorOpen {
                inspector(ui).transition(.move(edge: .trailing))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The show, over the displays or in this window, whichever the Play menu
    /// has ticked.
    func startPlay() {
        // Kotlin calls onExit on the main thread, so touching @State is safe.
        show = .play(host.startPlay(onExit: { show = nil }), inWindow: playInWindow)
    }

    /// Plays the selected slide on its own, in the same full-screen player Play
    /// uses. Escape comes back to the editor, exactly as it does from a show.
    func startPreview() {
        show = .preview(host.startPreview(onExit: { show = nil }))
    }
}