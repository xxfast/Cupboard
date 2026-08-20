import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Shared state snapshot

/// One read of the shared chrome state, taken per pass by whoever needs it.
/// Everything a click shows comes back through here, never from a local copy.
/// A value, so the window and the menus read the same editor the same way.
struct Chrome {
    let sidebarOpen: Bool
    let inspectorOpen: Bool
    let tab: InspectorTab
    let showNotes: Bool
    let notes: String
    /// The primary of the selection, nil when nothing is selected.
    let element: Selection?
    let selectionCount: Int
    let canGroup: Bool
    let canUngroup: Bool
    /// The selected slide's own properties, what the Document panel edits.
    let slide: SlideProps

    init(_ host: EditorHost) {
        sidebarOpen = host.sidebarOpen()
        inspectorOpen = host.inspectorOpen()
        tab = host.inspectorTab()
        showNotes = host.showNotes()
        notes = host.slideNotes()
        element = host.selectedElement().map(Selection.init)
        selectionCount = Int(host.selectionCount())
        canGroup = host.canGroup()
        canUngroup = host.canUngroup()
        slide = SlideProps(host)
    }

    /// Everything but the unlock needs something unlocked, the same rule the
    /// presenter applies: a live item is never a silently dropped event.
    var editable: Bool { element.map { !$0.locked } ?? false }
}

/// The selected slide's own properties as a Swift value: what the Document
/// panel shows, read the same way the element properties are.
struct SlideProps: Equatable {
    /// Whether the slide draws its place in the presentation on itself.
    let numberVisible: Bool
    /// 0 the deck's own background, 1 a flat colour, 2 a gradient.
    let backgroundKind: Int
    /// Packed ARGB. When the slide wears another kind these are what switching
    /// to this one would commit, so the controls never have to invent a value.
    let color: Int64
    let gradientStart: Int64
    let gradientEnd: Int64

    init(_ host: EditorHost) {
        numberVisible = host.slideNumberVisible()
        backgroundKind = Int(host.backgroundKind())
        color = host.backgroundColor()
        gradientStart = host.backgroundGradientStart()
        gradientEnd = host.backgroundGradientEnd()
    }
}

/// The primary selected element's properties as a Swift value. Kotlin hands back
/// a fresh object on every read, so a struct is what lets the inspector's fields
/// tell a real change from another pass over the same numbers.
struct Selection: Equatable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
    let opacity: Double
    let rotation: Double
    let flippedHorizontally: Bool
    let flippedVertically: Bool
    let locked: Bool
    let kind: String

    init(_ props: ElementProps) {
        x = Double(props.x)
        y = Double(props.y)
        width = Double(props.width)
        height = Double(props.height)
        opacity = Double(props.opacity)
        rotation = Double(props.rotation)
        flippedHorizontally = props.flippedHorizontally
        flippedVertically = props.flippedVertically
        locked = props.locked
        kind = props.kind
    }
}
