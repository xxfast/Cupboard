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
    /// The deck's saved shape looks, what the Shape section's style strip shows.
    /// The whole library whatever is selected: one of them may be marked as the
    /// one the primary shape is wearing, but none of them go away.
    let objectStyles: [ObjectStyleChoice]
    /// The primary's code style, nil unless the primary is a code block.
    let code: CodeFormat?
    /// The primary's terminal style, nil unless the primary is a terminal.
    let terminal: TerminalFormat?
    /// The primary's diagram style, nil unless the primary is a diagram.
    let diagram: DiagramFormat?
    /// The primary's equation style, nil unless the primary is an equation.
    let equation: EquationFormat?
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
    /// Whether the navigator is showing layouts rather than slides. Kotlin's, not
    /// a flag of the shell's: layout mode is the selection sitting on a layout,
    /// and that lives in `states` like every other piece of chrome here.
    let editingLayouts: Bool
    /// The deck's layouts, for the Document panel's popup. Ids and names paired
    /// off one read, so the menu shows a name and hands back the id beside it.
    let layouts: [LayoutChoice]
    /// The layout the selected slide is on, nil when it is on none.
    let slideLayoutId: String?
    /// Whether Reapply Layout has a layout to put back.
    let canReapplyLayout: Bool
    /// The selected slide's, or layout's, name: what the layout panel renames.
    let slideTitle: String
    /// What each placeholder button is called, in the order Kotlin lists the
    /// roles. The position is what goes back to `addPlaceholder`.
    let placeholderRoles: [String]
    /// The looks the deck can be put on, in picker order: the built-ins, then
    /// the user's. Names alone, the way a layout travels as an id and a name.
    let themeNames: [String]
    /// The subset of the above the user saved, and so the only ones deletable.
    let userThemeNames: [String]
    /// What the deck is wearing. Not always one of `themeNames`: a deck edited
    /// away from a theme keeps the name it was saved under.
    let themeName: String
    /// The deck's own background, what the Document panel's deck controls edit.
    let deck: DeckProps
    /// The shape every slide in the deck is cut to, and the shapes it can be
    /// put on. Deck-wide like the theme, whatever the control is called.
    let slideSize: SlideSize
    /// The selected slide's transition, what the Animate panel edits.
    let transition: TransitionFormat
    /// The transitions the popup offers and the ways one may run, in Kotlin's
    /// order. Positions are what go back, the way a placeholder role does.
    let transitionKinds: [String]
    let transitionDirections: [String]
    /// The selected slide's build order, in the order it plays: what the Animate
    /// panel's list draws and what its editor writes back.
    let builds: [BuildEntry]
    /// What a build may play, what may start it, and what an action may do, in
    /// Kotlin's order. Positions are what go back, like the transition kinds.
    let buildEffects: [String]
    let buildTriggers: [String]
    let actionKinds: [String]

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
        objectStyles = host.objectStyles().map(ObjectStyleChoice.init)
        code = host.selectedCode().map(CodeFormat.init)
        terminal = host.selectedTerminal().map(TerminalFormat.init)
        diagram = host.selectedDiagram().map(DiagramFormat.init)
        equation = host.selectedEquation().map(EquationFormat.init)
        selectionCount = Int(host.selectionCount())
        canGroup = host.canGroup()
        canUngroup = host.canUngroup()
        canFormatText = host.canFormatText()
        slide = SlideProps(host)
        editingLayouts = host.isEditingLayouts()
        layouts = LayoutChoice.all(host)
        slideLayoutId = host.selectedSlideLayoutId()
        canReapplyLayout = host.canReapplyLayout()
        slideTitle = host.selectedSlideTitle()
        placeholderRoles = host.placeholderRoles()
        themeNames = host.themeNames()
        userThemeNames = host.userThemeNames()
        themeName = host.currentThemeName()
        deck = DeckProps(host)
        slideSize = SlideSize(host)
        transition = TransitionFormat(host.selectedTransition())
        transitionKinds = host.transitionKinds()
        transitionDirections = host.transitionDirections()
        builds = host.buildRows().map(BuildEntry.init)
        buildEffects = host.buildEffectTitles()
        buildTriggers = host.buildTriggerTitles()
        actionKinds = host.actionKindTitles()
    }

    /// Everything but the unlock needs something unlocked, the same rule the
    /// presenter applies: a live item is never a silently dropped event.
    var editable: Bool { element.map { !$0.locked } ?? false }
}

/// One layout in the Document panel's popup. Kotlin hands the ids and the names
/// back as two parallel lists, which is the ObjC-friendly shape; they are zipped
/// here so a menu row cannot show one layout's name over another's id.
struct LayoutChoice: Identifiable, Equatable {
    let id: String
    let name: String

    static func all(_ host: EditorHost) -> [LayoutChoice] {
        let ids = host.layoutIds()
        let names = host.layoutNames()
        return zip(ids, names).map { LayoutChoice(id: $0, name: $1) }
    }
}

/// The slide every slide in the deck is cut to: the named shapes the picker
/// offers, which of them the deck is on, and the measurements behind it.
///
/// The sizes themselves never travel: a preset goes back as its position, the
/// way a shape does, so the only numbers here are the deck's own, which is what
/// the custom fields start at.
struct SlideSize: Equatable {
    /// The named shapes, in picker order. The position is what goes back.
    let presetTitles: [String]
    /// Which of them the deck is on, nil for a size none of them names.
    let presetIndex: Int?
    let width: Double
    let height: Double

    init(_ host: EditorHost) {
        presetTitles = host.slideSizePresetTitles()
        let index = Int(host.slideSizePresetIndex())
        presetIndex = index >= 0 ? index : nil
        width = Double(host.slideWidth())
        height = Double(host.slideHeight())
    }

    /// What the popup reads: the preset's name, or the measurements when the
    /// deck is on a size no preset names.
    var title: String {
        guard let index = presetIndex, presetTitles.indices.contains(index) else {
            return "\(Int(width.rounded())) × \(Int(height.rounded()))"
        }
        return presetTitles[index]
    }
}

/// The deck's own properties as a Swift value. Only the background so far, and
/// shaped exactly like the slide's below so one set of controls drives either.
struct DeckProps: Equatable {
    /// 0 the app's own dark gradient, 1 a flat colour, 2 a gradient.
    let backgroundKind: Int
    /// Packed ARGB. When the deck wears another kind these are what switching to
    /// this one would commit, the same trick the slide background plays.
    let color: Int64
    let gradientStart: Int64
    let gradientEnd: Int64

    init(_ host: EditorHost) {
        backgroundKind = Int(host.deckBackgroundKind())
        color = host.deckBackgroundColor()
        gradientStart = host.deckBackgroundGradientStart()
        gradientEnd = host.deckBackgroundGradientEnd()
    }
}

/// The selected slide's transition as a Swift value: what the Animate panel
/// shows. Read and written the way the background is, down to the values a
/// slide wearing no transition still carries, so switching off Default never
/// has to invent a duration.
///
/// Seconds here, milliseconds on the model: the panel is a slider and a field,
/// and both of them read in the unit people say out loud.
struct TransitionFormat: Equatable {
    /// A place in `transitionKinds`, or -1 for the deck's default.
    let kindIndex: Int
    /// A place in `transitionDirections`. Only the kinds that travel show it.
    let directionIndex: Int
    let duration: Double
    /// Whether the slide leaves on its own rather than waiting for a click.
    let automatic: Bool
    let delay: Double

    init(_ props: TransitionProps) {
        kindIndex = Int(props.kindIndex)
        directionIndex = Int(props.directionIndex)
        duration = Double(props.durationMs) / 1000
        automatic = props.automatic
        delay = Double(props.delayMs) / 1000
    }
}

/// One build in the slide's order as a Swift value: what a row says, and every
/// number the editor under the list sends back. `TransitionFormat`'s neighbour,
/// read the same way and written back the same way, whole.
///
/// The enums travel as positions: `kind` in `BuildKind`, `effectIndex` in
/// `buildEffects`, `triggerIndex` in `buildTriggers`, `actionKindIndex` in
/// `actionKinds` and nil for a build with no action, and `deliveryIndex` in this
/// row's own `deliveryTitles`, which is what its element allows.
///
/// Seconds here, milliseconds on the model, the way the transition reads: the
/// panel is a slider and a field, and both read in the unit people say out loud.
struct BuildEntry: Identifiable, Equatable {
    let id: Int
    /// The build's place in the order, which is also its badge number less one.
    var index: Int { id }
    let title: String
    let meta: String
    let elementId: String
    /// The build's element is selected on the canvas: the row draws active.
    let active: Bool
    /// 0 brings the element on, 1 takes it away, 2 animates it where it is.
    let kind: Int
    let effectIndex: Int
    let deliveryIndex: Int
    let triggerIndex: Int
    let duration: Double
    let delay: Double
    /// The element's own step this build moves to, nil when it moves to none.
    let elementStep: Int?
    /// nil when the build carries no action, which is every build that is not one.
    let actionKindIndex: Int?
    /// What an action changes, filled in whatever the kind so a switched action
    /// never has to invent a number.
    let dx: Double
    let dy: Double
    let opacity: Double
    let rotation: Double
    let scale: Double
    /// The element has steps of its own, so the row has a step to point at.
    let hasStepTarget: Bool
    /// The deliveries this build's element has pieces for, in menu order.
    let deliveryTitles: [String]

    /// Whether this build brings its element on or takes it away, and so shows
    /// an effect and a delivery rather than an action's own controls.
    var isAction: Bool { kind == 2 }

    init(_ row: BuildRow) {
        id = Int(row.index)
        title = row.title
        meta = row.meta
        elementId = row.elementId
        active = row.active
        kind = Int(row.kindIndex)
        effectIndex = Int(row.effectIndex)
        deliveryIndex = Int(row.deliveryIndex)
        triggerIndex = Int(row.triggerIndex)
        duration = Double(row.durationMs) / 1000
        delay = Double(row.delayMs) / 1000
        elementStep = row.elementStep >= 0 ? Int(row.elementStep) : nil
        actionKindIndex = row.actionKindIndex >= 0 ? Int(row.actionKindIndex) : nil
        dx = Double(row.dx)
        dy = Double(row.dy)
        opacity = Double(row.opacity)
        rotation = Double(row.rotation)
        scale = Double(row.scale)
        hasStepTarget = row.hasStepTarget
        deliveryTitles = row.deliveryTitles
    }
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

/// One saved shape look as a Swift value: a swatch in the Shape section's style
/// strip paints itself from these, and the id is the whole of what goes back
/// when it is clicked, renamed or dropped.
///
/// Only what a swatch draws, not what a shape does: no gradient angle, no shadow
/// colour or blur. A 28pt square is not a shape preview, it is a look at a
/// glance, and the numbers it cannot show would only be numbers to keep in sync.
struct ObjectStyleChoice: Identifiable {
    let id: String
    let name: String
    /// Packed ARGB, the document model's colour format. So is every colour below.
    let fill: Int64
    /// Whether the gradient paints. The fill is what paints when it does not.
    let hasGradient: Bool
    let gradientStart: Int64
    let gradientEnd: Int64
    let strokeColor: Int64
    let strokeWidth: Double
    let hasShadow: Bool
    let cornerRadius: Double
    /// Whether the primary shape is already wearing exactly this look. Kotlin's
    /// answer, measured against the whole appearance rather than a stored id.
    let isCurrent: Bool

    init(_ props: ObjectStyleProps) {
        id = props.id
        name = props.name
        fill = props.fill
        hasGradient = props.hasGradient
        gradientStart = props.gradientStart
        gradientEnd = props.gradientEnd
        strokeColor = props.strokeColor
        strokeWidth = Double(props.strokeWidth)
        hasShadow = props.hasShadow
        cornerRadius = Double(props.cornerRadius)
        isCurrent = props.current
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

/// The primary selected element's terminal style as a Swift value: what the
/// Terminal section of the Format panel shows. Read off the primary like
/// `CodeFormat`, and written back the same way, except the title, which is
/// content and so goes to the primary alone.
struct TerminalFormat {
    let title: String
    let prompt: String
    let size: Double
    let showTitleBar: Bool

    init(_ props: TerminalProps) {
        title = props.title
        prompt = props.prompt
        size = Double(props.fontSize)
        showTitleBar = props.showTitleBar
    }
}

/// The primary selected element's diagram style as a Swift value: what the
/// Diagram section of the Format panel shows. Read off the primary like
/// `TerminalFormat`, and written back the same way. The source is not here: a
/// diagram's text is content, and content is edited on the canvas.
struct DiagramFormat {
    let size: Double
    let nodeFill: Int64
    let nodeStroke: Int64
    let nodeText: Int64
    let edgeColor: Int64

    init(_ props: DiagramProps) {
        size = Double(props.fontSize)
        nodeFill = props.nodeFill
        nodeStroke = props.nodeStroke
        nodeText = props.nodeText
        edgeColor = props.edgeColor
    }
}

/// The primary selected element's equation style as a Swift value: what the
/// Equation section of the Format panel shows. Read off the primary like
/// `DiagramFormat`, and written back the same way. The latex is not here: an
/// equation's source is content, and content is edited on the canvas.
struct EquationFormat {
    let size: Double
    let color: Int64

    init(_ props: EquationProps) {
        size = Double(props.fontSize)
        color = props.color
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
