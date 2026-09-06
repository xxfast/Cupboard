import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Menus

/// One row of a menu: a command, a submenu, or a separator. The Arrange items
/// are built once as these and rendered twice, as SwiftUI Buttons in the menu
/// bar and as NSMenuItems in the canvas menu, so the two can only differ in the
/// facts they were built from.
struct MenuEntry: Identifiable {
    let id = UUID()
    /// Empty is a separator: nothing else in a menu has no title.
    let title: String
    var enabled = true
    /// Menu bar only. A context menu advertising shortcuts would be repeating
    /// what the bar above it already says.
    var shortcut: KeyboardShortcut? = nil
    var children: [MenuEntry]? = nil
    var action: (() -> Void)? = nil

    var isSeparator: Bool { title.isEmpty }

    static func separator() -> MenuEntry { MenuEntry(title: "") }
}

/// What the Arrange items grey themselves out by: either the live selection, or
/// the effective selection of the right-click opening a menu right now. One
/// shape, so one builder serves both.
struct ArrangeFacts {
    /// Z-order, flip and align: one unlocked element is enough for all three.
    let canArrange: Bool
    let canGroup: Bool
    let canUngroup: Bool
    let canDistribute: Bool
    let canLock: Bool
    let lockLabel: String

    init(_ ui: Chrome) {
        canArrange = ui.editable
        canGroup = ui.canGroup
        canUngroup = ui.canUngroup
        // Two elements have no gap between them to equalize.
        canDistribute = ui.editable && ui.selectionCount >= 3
        canLock = ui.element != nil
        lockLabel = ui.element?.locked == true ? "Unlock" : "Lock"
    }

    /// Kotlin's, computed against the selection a right-click settles on rather
    /// than the one in `states`, which that click has not reached yet.
    init(_ facts: ContextFacts) {
        canArrange = facts.canArrange
        canGroup = facts.canGroup
        canUngroup = facts.canUngroup
        canDistribute = facts.canDistribute
        canLock = facts.canLock
        lockLabel = facts.lockLabel
    }
}

/// The Arrange menu, stated once. The labels follow the primary element, the
/// actions carry the whole selection: the host's setters batch, so one pick is
/// one undo entry however many elements it moved.
func arrangeEntries(_ facts: ArrangeFacts, _ host: EditorHost) -> [MenuEntry] {
    func command(
        _ title: String,
        _ enabled: Bool,
        shortcut: KeyboardShortcut? = nil,
        _ action: @escaping () -> Void
    ) -> MenuEntry {
        MenuEntry(title: title, enabled: enabled, shortcut: shortcut, action: action)
    }

    func reorder(_ title: String, _ move: ZOrderMove) -> MenuEntry {
        command(title, facts.canArrange) { host.reorderSelectedElement(move: move) }
    }

    return [
        reorder("Bring Forward", ZOrderMove.forward),
        reorder("Send Backward", ZOrderMove.backward),
        reorder("Bring to Front", ZOrderMove.tofront),
        reorder("Send to Back", ZOrderMove.toback),

        .separator(),

        command("Flip Horizontally", facts.canArrange) {
            host.flipSelectedElement(axis: FlipAxis.horizontal)
        },
        command("Flip Vertically", facts.canArrange) {
            host.flipSelectedElement(axis: FlipAxis.vertical)
        },

        .separator(),

        command(
            "Group",
            facts.canGroup,
            shortcut: KeyboardShortcut("g", modifiers: [.command, .option])
        ) { host.groupSelection() },
        command(
            "Ungroup",
            facts.canUngroup,
            shortcut: KeyboardShortcut("g", modifiers: [.command, .option, .shift])
        ) { host.ungroupSelection() },

        .separator(),

        // A lone element aligns to the slide, so one is enough.
        MenuEntry(
            title: "Align Objects",
            enabled: facts.canArrange,
            children: [
                command("Left", true) { host.alignSelection(edge: AlignEdge.left) },
                command("Center", true) { host.alignSelection(edge: AlignEdge.centerx) },
                command("Right", true) { host.alignSelection(edge: AlignEdge.right) },
                command("Top", true) { host.alignSelection(edge: AlignEdge.top) },
                command("Middle", true) { host.alignSelection(edge: AlignEdge.centery) },
                command("Bottom", true) { host.alignSelection(edge: AlignEdge.bottom) },
            ]
        ),
        MenuEntry(
            title: "Distribute Objects",
            enabled: facts.canDistribute,
            children: [
                command("Horizontally", true) { host.distributeSelection(axis: Axis.horizontal) },
                command("Vertically", true) { host.distributeSelection(axis: Axis.vertical) },
            ]
        ),

        .separator(),

        command(facts.lockLabel, facts.canLock) { host.toggleSelectedElementLock() },
    ]
}

/// The Format menu. Whole-box formatting, so every item is live while the caret
/// is up in a text box too: the menu bar gets key equivalents before the
/// responder chain, so Cmd+B reaches here rather than the in-place editor.
///
/// [enabled] is one fact for the lot: the host's setters each walk the selection
/// themselves and drop what cannot take the change, so there is nothing finer to
/// grey out per item.
func formatEntries(_ host: EditorHost, enabled: Bool) -> [MenuEntry] {
    func command(
        _ title: String,
        shortcut: KeyboardShortcut? = nil,
        _ action: @escaping () -> Void
    ) -> MenuEntry {
        MenuEntry(title: title, enabled: enabled, shortcut: shortcut, action: action)
    }

    func align(_ title: String, _ value: TextAlign) -> MenuEntry {
        command(title) { host.setSelectedTextAlign(align: value) }
    }

    func list(_ title: String, _ value: CupboardCanvas.ListStyle) -> MenuEntry {
        command(title) { host.setSelectedTextList(style: value) }
    }

    return [
        command("Bold", shortcut: KeyboardShortcut("b", modifiers: .command)) {
            host.toggleSelectedTextBold()
        },
        command("Italic", shortcut: KeyboardShortcut("i", modifiers: .command)) {
            host.toggleSelectedTextItalic()
        },
        command("Underline", shortcut: KeyboardShortcut("u", modifiers: .command)) {
            host.toggleSelectedTextUnderline()
        },
        command("Strikethrough") { host.toggleSelectedTextStrikethrough() },

        .separator(),

        align("Align Left", TextAlign.start),
        align("Align Center", TextAlign.center),
        align("Align Right", TextAlign.end),

        .separator(),

        list("Bullet List", CupboardCanvas.ListStyle.bullet),
        list("Numbered List", CupboardCanvas.ListStyle.numbered),
        list("No List", CupboardCanvas.ListStyle.none),

        .separator(),

        // The two that write the deck's defaults rather than the selection, and
        // so the two with their own enabled state: each needs the primary to be
        // the kind it takes a look off, which [enabled] above says nothing about.
        MenuEntry(
            title: "Use as Default Text Box Appearance",
            enabled: host.canUseAsDefaultTextStyle(),
            action: { host.useAsDefaultTextStyle() }
        ),
        MenuEntry(
            title: "Use as Default Shape Style",
            enabled: host.canUseAsDefaultShapeStyle(),
            action: { host.useAsDefaultShapeStyle() }
        ),
    ]
}

/// The shape catalog as menu rows, in the order Kotlin lists it. The index is
/// what goes back, so what a shape is called and what it inserts as are both
/// the document's to say and neither is restated here.
func shapeEntries(_ host: EditorHost) -> [MenuEntry] {
    host.shapeCatalog().enumerated().map { index, title in
        MenuEntry(title: title, action: { host.insertShape(index: Int32(index)) })
    }
}

/// The Insert menu: what can go on a slide. Always live, and nothing to grey
/// out, since an insertion needs no selection and the document is never without
/// a slide to take one.
func insertEntries(_ host: EditorHost) -> [MenuEntry] {
    [
        MenuEntry(title: "Text Box", action: { host.insertTextBox() }),
        MenuEntry(title: "Code", action: { host.insertCodeBox() }),
        MenuEntry(title: "Terminal", action: { host.insertTerminal() }),
        MenuEntry(title: "Diagram", action: { host.insertDiagram() }),
        MenuEntry(title: "Equation", action: { host.insertEquation() }),
        MenuEntry(title: "Shape", children: shapeEntries(host)),
        // The one row that opens a panel: an image is bytes, and there is
        // nothing to insert until the user has said which.
        MenuEntry(title: "Image...", action: { Media.insert(into: host) }),
        // Its plural: several pictures in one box, shown one at a time. Same
        // panel with multiple selection on.
        MenuEntry(title: "Image Gallery...", action: { Media.insertGallery(into: host) }),
    ]
}

/// The slide verbs, stated once. [slideId] nil is the Slide menu, which has no
/// row to point at and drives the selected-slide methods instead; a navigator
/// row passes its own id, so the verb acts on that row whatever is selected.
/// [includePaste] is the context menu's: in the bar, Edit > Paste owns it.
func slideEntries(
    _ host: EditorHost,
    slideId: String?,
    includePaste: Bool
) -> [MenuEntry] {
    // Everything but Paste is always live: the core keeps the document
    // non-empty, so cutting or deleting the last slide leaves a blank one.
    func command(
        _ title: String,
        _ byId: @escaping (String) -> Void,
        _ bySelection: @escaping () -> Void
    ) -> MenuEntry {
        MenuEntry(title: title, action: {
            if let slideId { byId(slideId) } else { bySelection() }
        })
    }

    let skipped = slideId.map { host.isSlideSkipped(id: $0) } ?? host.isSelectedSlideSkipped()

    // doCopySlide is the exporter's doing: copy is a reserved ObjC method
    // family, so the host's copySlide arrives here renamed, the way every other
    // copyX on it does.
    var entries: [MenuEntry] = [
        command("New Slide", host.addSlideAfter(id:), host.addSlideAfterSelection),
        command("Duplicate Slide", host.duplicateSlide(id:), host.duplicateSelectedSlide),

        .separator(),

        command("Cut Slide", host.cutSlide(id:), host.cutSelectedSlide),
        command("Copy Slide", host.doCopySlide(id:), host.doCopySelectedSlide),
    ]

    if includePaste {
        entries.append(
            MenuEntry(title: "Paste", enabled: host.canPaste(), action: {
                if let slideId { host.pasteAfterSlide(id: slideId) } else { host.paste() }
            })
        )
    }

    entries += [
        .separator(),

        command("Delete Slide", host.deleteSlide(id:), host.deleteSelectedSlide),

        .separator(),

        // Keynote's titles, following the slide the menu is about: the row's own
        // when a row opened it, the selected one in the bar.
        MenuEntry(title: skipped ? "Don't Skip Slide" : "Skip Slide", action: {
            if let slideId {
                host.setSlideSkipped(id: slideId, skipped: !skipped)
            } else {
                host.toggleSelectedSlideSkipped()
            }
        }),
    ]
    return entries
}

/// The layout verbs: what a navigator row offers while the navigator is showing
/// layouts. Fewer than a slide's, and deliberately so: a layout has no place in
/// a presentation, so nothing here skips it, and the clipboard carries slides.
///
/// New Layout lands after the selected layout, so the row is selected first, the
/// way `pasteAfterSlide` sends a select ahead of its paste. Delete and Duplicate
/// carry the row's own id: the core reads it out of the layout list itself.
func layoutEntries(
    _ host: EditorHost,
    index: Int32,
    layoutId: String,
    onRename: @escaping () -> Void
) -> [MenuEntry] {
    [
        MenuEntry(title: "New Layout", action: {
            host.selectSlide(index: index)
            host.addLayout()
        }),
        MenuEntry(title: "Duplicate", action: { host.duplicateSlide(id: layoutId) }),
        MenuEntry(title: "Delete", action: { host.deleteSlide(id: layoutId) }),

        .separator(),

        MenuEntry(title: "Rename...", action: onRename),
    ]
}

/// What each snap switch is called, in the order Kotlin's `SnapKind` lists them.
/// The position is the whole protocol: an index goes back to `setSnap`, so this
/// list is also what says how many switches there are.
let snapTitles = ["Center", "Edges", "Objects", "Guides"]

/// The snap switches as a submenu, checkmarked from the snapshot they were built
/// with. Toggles rather than `MenuEntry` rows: a `MenuEntry` renders as a plain
/// Button, and these are settings, so they show their state.
struct SnapToggles: View {
    let host: EditorHost
    let snap: [Bool]

    var body: some View {
        ForEach(Array(snapTitles.enumerated()), id: \.offset) { index, title in
            Toggle(title, isOn: Binding(
                get: { snap.indices.contains(index) ? snap[index] : false },
                set: { host.setSnap(kind: Int32(index), enabled: $0) }
            ))
        }
    }
}

/// The entries as SwiftUI. The NSMenu builder walks the same list.
struct MenuEntries: View {
    let entries: [MenuEntry]

    var body: some View {
        ForEach(entries) { entry in
            if entry.isSeparator {
                Divider()
            } else if let children = entry.children {
                Menu(entry.title) { MenuEntries(entries: children) }
                    .disabled(!entry.enabled)
            } else {
                Button(entry.title) { entry.action?() }
                    .keyboardShortcut(entry.shortcut)
                    .disabled(!entry.enabled)
            }
        }
    }
}

/// NSMenuItem calls a selector on a target it does not retain, so the closure
/// rides as the item's represented object, which it does.
final class MenuAction: NSObject {
    private let run: () -> Void

    init(_ run: @escaping () -> Void) { self.run = run }

    @objc func fire() { run() }
}

/// The entries as an NSMenu. Nothing autoenables: AppKit would ask a responder
/// chain that knows nothing about the Kotlin selection, and what these were
/// built from is already a step ahead of it.
func nsMenu(_ entries: [MenuEntry]) -> NSMenu {
    let menu = NSMenu()
    menu.autoenablesItems = false
    for entry in entries {
        guard !entry.isSeparator else {
            menu.addItem(.separator())
            continue
        }
        let item = NSMenuItem(title: entry.title, action: nil, keyEquivalent: "")
        item.isEnabled = entry.enabled
        if let children = entry.children {
            item.submenu = nsMenu(children)
        } else if let action = entry.action {
            let handler = MenuAction(action)
            item.target = handler
            item.action = #selector(MenuAction.fire)
            item.representedObject = handler
        }
        menu.addItem(item)
    }
    return menu
}

/// The canvas context menu: the clipboard four, then Arrange. Built per click
/// from that click's own facts, and acting through the selection-based methods,
/// which have long settled by the time a human picks an item.
func popCanvasMenu(host: EditorHost, elementId: String?) {
    let facts = host.contextFacts(elementId: elementId)
    let entries: [MenuEntry] = [
        MenuEntry(title: "Cut", enabled: facts.canCut, action: { host.cutSelection() }),
        MenuEntry(title: "Copy", enabled: facts.canCopy, action: { host.doCopySelection() }),
        MenuEntry(title: "Paste", enabled: facts.canPaste, action: { host.paste() }),
        MenuEntry(title: "Delete", enabled: facts.canDelete, action: { host.deleteSelection() }),
        .separator(),
    ] + arrangeEntries(ArrangeFacts(facts), host)

    let menu = nsMenu(entries)
    let view = host.view
    guard let window = view.window else { return }

    // Where the click landed, off the event itself; the pointer, converted, for
    // the case where there is no event to read.
    var inWindow = window.convertPoint(fromScreen: NSEvent.mouseLocation)
    if let event = NSApp.currentEvent, event.window === window {
        inWindow = event.locationInWindow
    }
    let location = view.convert(inWindow, from: nil)

    // Compose is still handling the click, so the menu's tracking loop waits for
    // the next turn of the main queue rather than running from inside it.
    DispatchQueue.main.async {
        menu.popUp(positioning: nil, at: location, in: view)
    }
}

/// The menu for a right-click inside an edit session: the caret's verbs, not the
/// selection's. This is the whole reason the canvas hands the shell a field
/// bridge, so that mid-edit a mac gets an NSMenu like it does everywhere else
/// instead of the one compose draws for its own text fields.
///
/// [x] and [y] are where the click landed in the canvas layer, in compose pixels
/// with a top-left origin: the anchor of last resort, for the case where there is
/// no event left to read.
func popFieldMenu(host: EditorHost, x: Double, y: Double) {
    let facts = host.fieldMenuFacts()
    let entries: [MenuEntry] = [
        MenuEntry(title: "Cut", enabled: facts.canCut, action: { host.fieldCut() }),
        MenuEntry(title: "Copy", enabled: facts.canCopy, action: { host.fieldCopy() }),
        MenuEntry(title: "Paste", enabled: facts.canPaste, action: { host.fieldPaste() }),
        .separator(),
        MenuEntry(
            title: "Select All",
            enabled: facts.canSelectAll,
            action: { host.fieldSelectAll() }
        ),
    ]

    let menu = nsMenu(entries)
    let view = host.view
    guard let window = view.window else { return }

    // The click's own event first, exactly as the canvas menu takes it. Compose
    // pixels convert by the backing scale, and the view is not flipped, so the
    // fallback flips y as the canvas's own mouse mapping does.
    var location = NSPoint(
        x: x / window.backingScaleFactor,
        y: view.bounds.height - y / window.backingScaleFactor
    )
    if let event = NSApp.currentEvent, event.window === window {
        location = view.convert(event.locationInWindow, from: nil)
    }

    DispatchQueue.main.async {
        menu.popUp(positioning: nil, at: location, in: view)
    }
}
