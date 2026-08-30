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
    /// Whether the canvas draws its rulers, and whether the user's guides show
    /// at all. View toggles like `showNotes`, and read the same way.
    let showRulers: Bool
    let showGuides: Bool
    /// One switch per `SnapKind`, in the order `snapTitles` names them. An array
    /// rather than four fields: the submenu renders it as one loop.
    let snap: [Bool]
    /// The primary of the selection, nil when nothing is selected.
    let element: Selection?
    /// The primary's text style, nil unless the primary is a text box.
    let text: TextFormat?
    /// The primary's shape style, nil unless the primary is a shape.
    let shape: ShapeFormat?
    /// The primary's code style, nil unless the primary is a code block.
    let code: CodeFormat?
    let selectionCount: Int
    let canGroup: Bool
    let canUngroup: Bool
    /// Whether the Format menu has anything to act on. Not `text != nil`: the
    /// primary may be a shape sitting in front of the text box the menu would
    /// format, and a locked primary greys nothing the rest of the selection can
    /// still take.
    let canFormatText: Bool
    /// The selected slide's own properties, what the Document panel edits.
    let slide: SlideProps

    init(_ host: EditorHost) {
        sidebarOpen = host.sidebarOpen()
        inspectorOpen = host.inspectorOpen()
        tab = host.inspectorTab()
        showNotes = host.showNotes()
        notes = host.slideNotes()
        showRulers = host.showRulers()
        showGuides = host.showGuides()
        snap = snapTitles.indices.map { host.snapEnabled(kind: Int32($0)) }
        element = host.selectedElement().map(Selection.init)
        text = host.selectedText().map(TextFormat.init)
        shape = host.selectedShape().map(ShapeFormat.init)
        code = host.selectedCode().map(CodeFormat.init)
        selectionCount = Int(host.selectionCount())
        canGroup = host.canGroup()
        canUngroup = host.canUngroup()
        canFormatText = host.canFormatText()
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

/// The primary selected element's text style as a Swift value: what the Text
/// section of the Format panel shows. Read off the primary, written to the whole
/// selection, the way every other Format control works.
///
/// `ListStyle` is spelled out: SwiftUI has one of its own, and the document
/// model's is the one meant here.
struct TextFormat {
    let font: TextFont
    /// The raw weight the popup marks. `isBold` is the same number read as a flag.
    let weight: Int
    let isBold: Bool
    let italic: Bool
    let underline: Bool
    let strikethrough: Bool
    let size: Double
    /// Packed ARGB, the document model's colour format.
    let color: Int64
    let align: TextAlign
    /// A multiple of the font size, not points.
    let lineHeight: Double
    let list: CupboardCanvas.ListStyle
    /// "" is no link: the field has no null to spell, and blank commits nil.
    let link: String

    init(_ props: TextProps) {
        font = props.fontFamily
        weight = Int(props.weightValue)
        isBold = props.isBold
        italic = props.italic
        underline = props.underline
        strikethrough = props.strikethrough
        size = Double(props.size)
        color = props.color
        align = props.align
        lineHeight = Double(props.lineHeight)
        list = props.listStyle
        link = props.link ?? ""
    }
}

/// The primary selected element's shape style as a Swift value: what the Shape
/// section of the Format panel shows. Read off the primary, written to the whole
/// selection, like every other Format control.
///
/// A shape with no gradient and no shadow still carries both sets of numbers:
/// they are what turning one on would commit, so a segment or a checkbox never
/// has to invent a colour. Same trick the slide background uses.
struct ShapeFormat {
    /// What the shape is, the way the document model spells it.
    let kind: String
    /// Only a rectangle rounds, and only a line caps its ends.
    let isRectangle: Bool
    let isLine: Bool
    let cornerRadius: Double
    /// Packed ARGB, the document model's colour format. So is every colour below.
    let fill: Int64
    /// Whether the gradient paints. The fill is what paints when it does not.
    let hasGradient: Bool
    let gradientStart: Int64
    let gradientEnd: Int64
    /// CSS degrees: 0 points up and the angle turns clockwise.
    let gradientAngle: Double
    let strokeColor: Int64
    let strokeWidth: Double
    let hasShadow: Bool
    let shadowColor: Int64
    let shadowBlur: Double
    let startArrow: Bool
    let endArrow: Bool
    /// "" is no label: the field has no null to spell, the way the link field has none.
    let label: String
    let labelSize: Double

    init(_ props: ShapeProps) {
        kind = props.kindName
        isRectangle = props.isRectangle
        isLine = props.isLine
        cornerRadius = Double(props.cornerRadius)
        fill = props.fill
        hasGradient = props.hasGradient
        gradientStart = props.gradientStart
        gradientEnd = props.gradientEnd
        gradientAngle = Double(props.gradientAngle)
        strokeColor = props.strokeColor
        strokeWidth = Double(props.strokeWidth)
        hasShadow = props.hasShadow
        shadowColor = props.shadowColor
        shadowBlur = Double(props.shadowBlur)
        startArrow = props.startArrow
        endArrow = props.endArrow
        label = props.label
        labelSize = Double(props.labelSize)
    }
}

/// The primary selected element's code style as a Swift value: what the Code
/// section of the Format panel shows. Read off the primary, written to the whole
/// selection, like every other Format control.
///
/// The theme arrives as its name rather than as the enum, the way the shape kind
/// does, and goes back the same way: the picker draws `codeThemes()` and hands
/// one of its own strings back.
struct CodeFormat {
    /// Free-form on the model and resolved case-insensitively, so this is not
    /// always one of `codeLanguages()` exactly.
    let language: String
    let theme: String
    let size: Double
    let showLineNumbers: Bool
    let wrap: Bool

    init(_ props: CodeProps) {
        language = props.language
        theme = props.theme
        size = Double(props.fontSize)
        showLineNumbers = props.showLineNumbers
        wrap = props.wrap
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
