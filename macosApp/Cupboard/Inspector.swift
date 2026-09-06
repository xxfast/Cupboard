import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Inspector

/// A SwiftUI colour as the document model's packed ARGB, the other way round
/// from `Color(argb:)`. Through sRGB on the way, since that is the space the
/// stored number means and a picked colour may arrive in another.
func packedArgb(_ color: Color) -> Int64 {
    let srgb = NSColor(color).usingColorSpace(.sRGB) ?? .white
    func channel(_ value: CGFloat) -> Int64 { Int64((value * 255).rounded()) }
    return channel(srgb.alphaComponent) << 24
        | channel(srgb.redComponent) << 16
        | channel(srgb.greenComponent) << 8
        | channel(srgb.blueComponent)
}

enum BuildSpace {
    static let name = "buildOrder"
}

/// Where each build row sits in [BuildSpace], by its place in the order. Keyed
/// by index rather than by element: a build is not its element, and one element
/// may hold several of them.
struct BuildRowFrames: PreferenceKey {
    static let defaultValue: [Int: CGRect] = [:]

    static func reduce(value: inout [Int: CGRect], nextValue: () -> [Int: CGRect]) {
        value.merge(nextValue()) { _, latest in latest }
    }
}

/// A build row on the move, and the gap it is over. SwiftUI state, like the
/// navigator's drag: nothing but the drop is the document's business.
struct BuildDrag: Equatable {
    /// The row being carried, by its place in the order.
    let index: Int
    /// The gap the drop lands in, 0 for the one above the first row.
    let gap: Int
    /// Where the drop line draws, in [BuildSpace].
    let lineY: CGFloat
    /// How far the pointer has carried the row: it travels with the cursor.
    let translationY: CGFloat

    /// The place the build ends up at, which is one back from the gap whenever
    /// the row has left a hole above it.
    var destination: Int { gap > index ? gap - 1 : gap }
}

extension EditorView {
    /// Header is bare: Share and the tabs moved to the toolbar, which floats
    /// over this glass, so the panel only owns the title under them.
    func inspector(_ ui: Chrome) -> some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: Layout.header)

            VStack(spacing: 1) {
                Text(inspectorTitle(ui))
                    .font(.system(size: 13))
                    .foregroundStyle(palette.subtle)
                // The title names the primary element, so with more than one
                // selected it has to say what else the controls are editing.
                if ui.tab == InspectorTab.format && ui.selectionCount > 1 {
                    Text("\(ui.selectionCount) selected")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.faint)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 4)
            .padding(.horizontal, Layout.panelPadding)
            .padding(.bottom, 12)

            if ui.tab == InspectorTab.document {
                documentPanel(ui)
            } else if ui.tab == InspectorTab.format {
                formatPanel(ui)
            } else {
                animatePanel(ui)
            }
        }
        .frame(width: Layout.inspector)
        .frame(maxHeight: .infinity)
        .glass(.sidebar, edge: .leading, palette: palette)
    }

    /// Format names what it is formatting, so the title follows the selection.
    func inspectorTitle(_ ui: Chrome) -> String {
        if ui.tab == InspectorTab.animate { return "Build" }
        // In layout mode the Document panel is about the layout being edited,
        // and the title is the first thing that has to say so.
        if ui.tab == InspectorTab.document { return ui.editingLayouts ? "Layout" : "Slide" }
        return ui.element?.kind ?? "Text"
    }

    // MARK: Format panel

    /// The selected element's shared properties. Everything shown here comes
    /// back through `states`, so a typed value, a canvas drag and an undo all
    /// land in the same place.
    @ViewBuilder func formatPanel(_ ui: Chrome) -> some View {
        if let element = ui.element {
            VStack(alignment: .leading, spacing: Layout.panelPadding) {
                VStack(alignment: .leading, spacing: Layout.panelPadding) {
                    // Only a text box has these, so the section is here or it is
                    // not; everything below it belongs to every element.
                    if let text = ui.text {
                        textSection(text)
                        palette.divider.frame(height: 1)
                    }
                    // Same rule for the shape's own: a shape has these and
                    // nothing else does.
                    if let shape = ui.shape {
                        shapeSection(shape, styles: ui.objectStyles)
                        palette.divider.frame(height: 1)
                    }
                    // And the code block's, by the same rule.
                    if let code = ui.code {
                        codeSection(code)
                        palette.divider.frame(height: 1)
                    }
                    // And the terminal's.
                    if let terminal = ui.terminal {
                        terminalSection(terminal)
                        palette.divider.frame(height: 1)
                    }
                    // And the diagram's.
                    if let diagram = ui.diagram {
                        diagramSection(diagram)
                        palette.divider.frame(height: 1)
                    }
                    // And the equation's.
                    if let equation = ui.equation {
                        equationSection(equation)
                        palette.divider.frame(height: 1)
                    }
                    positionSection(element)
                    palette.divider.frame(height: 1)
                    rotateSection(element)
                    palette.divider.frame(height: 1)
                    opacitySection(element)
                    palette.divider.frame(height: 1)
                    arrangeSection
                }
                // A locked element ignores every edit but the button below, so
                // the panel says so rather than swallowing them silently.
                .disabled(element.locked)
                .opacity(element.locked ? 0.45 : 1)

                Spacer(minLength: 0)

                // Two unlocked elements make a group; a lone group comes apart
                // again. Neither button is here when it has nothing to do.
                if ui.canGroup {
                    panelButton("Group", symbol: "square.on.square") { host.groupSelection() }
                }
                if ui.canUngroup {
                    panelButton("Ungroup", symbol: "square.split.2x2") { host.ungroupSelection() }
                }

                lockButton(element)
            }
            .padding(Layout.panelPadding)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else {
            Text("Select an element to edit it")
                .font(.system(size: 12))
                .foregroundStyle(palette.faint)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .padding(Layout.panelPadding)
        }
    }

    // MARK: Text

    /// The text box's own style, above Position & Size. Every control writes to
    /// the whole selection and reads back off the primary, so what it shows is
    /// what the document holds and never a local copy.
    ///
    /// Whole-box, all of it: a list, a weight and a colour are properties of the
    /// element, not of a range, which is what the document model says and what
    /// keeps the caret's text one plain string.
    func textSection(_ text: TextFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Text")

            stylePopup(Self.fontTitles, selected: Self.fontIndex(text.font)) { index in
                host.setSelectedTextFont(font: Self.fonts[index])
            }

            HStack(spacing: 8) {
                stylePopup(Self.weightTitles, selected: Self.weightIndex(text.weight)) { index in
                    host.setSelectedTextWeight(weight: Int32(Self.weights[index]))
                }

                ValueField(label: "", value: text.size, palette: palette, unit: "pt") {
                    host.setSelectedTextSize(size: Float($0))
                }
                .frame(width: 78)
            }

            HStack(spacing: 6) {
                styleToggle("bold", on: text.isBold, help: "Bold") {
                    host.toggleSelectedTextBold()
                }
                styleToggle("italic", on: text.italic, help: "Italic") {
                    host.toggleSelectedTextItalic()
                }
                styleToggle("underline", on: text.underline, help: "Underline") {
                    host.toggleSelectedTextUnderline()
                }
                styleToggle("strikethrough", on: text.strikethrough, help: "Strikethrough") {
                    host.toggleSelectedTextStrikethrough()
                }

                // The system colour panel is live: it commits on every sample it
                // sends, so a slow drag through it spends an undo entry per
                // sample. Same-colour writes cost nothing (the core drops an edit
                // that changes nothing), which takes the worst of it off.
                ColorPicker(
                    "",
                    selection: Binding(
                        get: { Color(argb: text.color) },
                        set: { host.setSelectedTextColor(argb: packedArgb($0)) }
                    ),
                    supportsOpacity: true
                )
                .labelsHidden()
                .controlSize(.small)
                .frame(width: 40)
                .help("Text Colour")
            }

            HStack(spacing: 8) {
                HStack(spacing: 2) {
                    alignSegment("text.alignleft", on: text.align, is: TextAlign.start)
                    alignSegment("text.aligncenter", on: text.align, is: TextAlign.center)
                    alignSegment("text.alignright", on: text.align, is: TextAlign.end)
                }
                .padding(2)
                .frame(height: 26)
                .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

                ValueField(
                    label: "\u{2195}",
                    value: text.lineHeight,
                    palette: palette,
                    decimals: 2
                ) { host.setSelectedTextLineHeight(lineHeight: Float($0)) }
                .help("Line Spacing")
            }

            stylePopup(Self.listTitles, selected: Self.listIndex(text.list)) { index in
                host.setSelectedTextList(style: Self.lists[index])
            }

            StringField(placeholder: "Link", value: text.link, palette: palette) {
                host.setSelectedTextLink(link: $0.isEmpty ? nil : $0)
            }
        }
    }

    /// One alignment icon. The row it sits in is the same raised well the
    /// background segments use, so the two read as the same control.
    func alignSegment(_ symbol: String, on: TextAlign, is value: TextAlign) -> some View {
        let selected = on == value
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { host.setSelectedTextAlign(align: value) } label: {
            Image(systemName: symbol)
                .font(.system(size: 11))
                .foregroundStyle(selected ? palette.accentText : palette.subtle)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    selected ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                    in: shape
                )
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// B/I/U/S: a raised square that fills with the accent while it is on.
    func styleToggle(
        _ symbol: String,
        on: Bool,
        help: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 11.5))
                .foregroundStyle(on ? palette.accentText : palette.ctrlText)
                .frame(maxWidth: .infinity)
                .frame(height: 22)
                .background(
                    on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(palette.ctrl),
                    in: shape
                )
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(help)
    }

    /// An AppKit popup button over a fixed list. Indices rather than the Kotlin
    /// enums: a tag has to be Hashable, and what these pick from is a list this
    /// file states anyway.
    func stylePopup(
        _ titles: [String],
        selected: Int,
        onPick: @escaping (Int) -> Void
    ) -> some View {
        Picker("", selection: Binding(get: { selected }, set: onPick)) {
            ForEach(Array(titles.enumerated()), id: \.offset) { index, title in
                Text(title).tag(index)
            }
        }
        .labelsHidden()
        .pickerStyle(.menu)
        .controlSize(.small)
        .tint(palette.accent)
    }

    /// Generic families only, the document model's: nothing bundles font files
    /// yet, so a named face would render as one thing here and another wherever
    /// it is missing.
    static let fonts: [TextFont] = [TextFont.sans, TextFont.serif, TextFont.monospace]
    static let fontTitles = ["Sans", "Serif", "Monospace"]

    static func fontIndex(_ font: TextFont) -> Int {
        fonts.firstIndex { $0 == font } ?? 0
    }

    /// The five the popup offers. Bold is a point on this scale, so the B toggle
    /// and this popup are two views of one number.
    static let weights = [300, 400, 500, 600, 700]
    static let weightTitles = ["Light", "Regular", "Medium", "Semibold", "Bold"]

    /// The nearest offered weight, so a 450 imported from elsewhere marks
    /// something rather than leaving the popup blank.
    static func weightIndex(_ weight: Int) -> Int {
        weights.indices.min { abs(weights[$0] - weight) < abs(weights[$1] - weight) } ?? 1
    }

    static let lists: [CupboardCanvas.ListStyle] = [
        CupboardCanvas.ListStyle.none,
        CupboardCanvas.ListStyle.bullet,
        CupboardCanvas.ListStyle.numbered,
    ]
    static let listTitles = ["No List", "Bullet List", "Numbered List"]

    static func listIndex(_ style: CupboardCanvas.ListStyle) -> Int {
        lists.firstIndex { $0 == style } ?? 0
    }

    // MARK: Shape

    /// The shape's own look, above Position & Size: what paints it, what outlines
    /// it, and the parts only some kinds have. Same contract as the Text section,
    /// read off the primary and written to every unlocked shape in the selection.
    ///
    /// The colour wells are the system panel, and it is live: it commits every
    /// sample it sends, so a slow drag through it spends an undo entry per
    /// sample. The text colour takes the same deal, with the same mitigation:
    /// the core drops a write that changes nothing.
    @ViewBuilder func shapeSection(
        _ shape: ShapeFormat,
        styles: [ObjectStyleChoice]
    ) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            styleStrip(styles)

            palette.divider.frame(height: 1)

            sectionLabel("Shape")

            // Switching to a kind the shape is not wearing commits it there and
            // then, off the values it came back with, so the wells below always
            // have something to show and never have to invent a colour.
            HStack(spacing: 2) {
                segment("Color", on: !shape.hasGradient) { host.clearSelectedShapeGradient() }
                segment("Gradient", on: shape.hasGradient) {
                    setGradient(shape)
                }
            }
            .padding(2)
            .frame(height: 26)
            .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

            if shape.hasGradient {
                HStack(spacing: 8) {
                    colorWell("Start", argb: shape.gradientStart) { setGradient(shape, start: $0) }
                    colorWell("End", argb: shape.gradientEnd) { setGradient(shape, end: $0) }
                    Spacer(minLength: 0)
                    ValueField(label: "\u{00B0}", value: shape.gradientAngle, palette: palette) {
                        setGradient(shape, angle: $0)
                    }
                    .frame(width: 74)
                    .help("Gradient Angle")
                }
            } else {
                colorWell("Fill", argb: shape.fill) { host.setSelectedShapeFill(argb: $0) }
            }

            HStack(spacing: 8) {
                colorWell("Border", argb: shape.strokeColor) {
                    host.setSelectedShapeStroke(color: $0, width: Float(shape.strokeWidth))
                }
                Spacer(minLength: 0)
                ValueField(
                    label: "",
                    value: shape.strokeWidth,
                    palette: palette,
                    unit: "pt",
                    decimals: 1
                ) { host.setSelectedShapeStroke(color: shape.strokeColor, width: Float($0)) }
                .frame(width: 74)
                .help("Border Width")
            }

            checkRow("Shadow", on: shape.hasShadow) {
                host.setSelectedShapeShadow(
                    enabled: !shape.hasShadow,
                    color: shape.shadowColor,
                    blur: Float(shape.shadowBlur)
                )
            }

            if shape.hasShadow {
                HStack(spacing: 8) {
                    colorWell("Colour", argb: shape.shadowColor) {
                        host.setSelectedShapeShadow(
                            enabled: true,
                            color: $0,
                            blur: Float(shape.shadowBlur)
                        )
                    }
                    Spacer(minLength: 0)
                    ValueField(label: "", value: shape.shadowBlur, palette: palette, unit: "pt") {
                        host.setSelectedShapeShadow(
                            enabled: true,
                            color: shape.shadowColor,
                            blur: Float($0)
                        )
                    }
                    .frame(width: 74)
                    .help("Blur")
                }
            }

            // A corner radius means nothing to any other kind, so the field is
            // not there to be typed into rather than there and inert.
            if shape.isRectangle {
                HStack(spacing: 8) {
                    Text("Corner")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.subtle)
                    Spacer(minLength: 0)
                    ValueField(label: "", value: shape.cornerRadius, palette: palette, unit: "pt") {
                        host.setSelectedShapeCornerRadius(radius: Float($0))
                    }
                    .frame(width: 74)
                }
            }

            // Only a line has ends to cap, and a line has no label: it is all
            // stroke, with nothing to write on.
            if shape.isLine {
                HStack(spacing: 8) {
                    checkRow("Start", on: shape.startArrow) {
                        host.setSelectedShapeArrows(start: !shape.startArrow, end: shape.endArrow)
                    }
                    checkRow("End", on: shape.endArrow) {
                        host.setSelectedShapeArrows(start: shape.startArrow, end: !shape.endArrow)
                    }
                }
            } else {
                StringField(placeholder: "Label", value: shape.label, palette: palette) {
                    host.setSelectedShapeLabel(label: $0)
                }

                HStack(spacing: 8) {
                    Text("Label Size")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.subtle)
                    Spacer(minLength: 0)
                    ValueField(label: "", value: shape.labelSize, palette: palette, unit: "pt") {
                        host.setSelectedShapeLabelSize(size: Float($0))
                    }
                    .frame(width: 74)
                }
            }
        }
    }

    /// The deck's saved shape looks, as a wrapping row of swatches. A click
    /// dresses the whole selection in one, a right-click renames or drops one,
    /// and the button under them lifts the primary shape's look into the library.
    ///
    /// The ring marks the look the primary is already wearing, which Kotlin works
    /// out by appearance rather than by a stored id: a shape edited away from a
    /// style is wearing none, and the strip says so by ringing nothing.
    func styleStrip(_ styles: [ObjectStyleChoice]) -> some View {
        let column = GridItem(.adaptive(minimum: 28, maximum: 28), spacing: 8, alignment: .leading)
        return VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Styles")

            LazyVGrid(columns: [column], alignment: .leading, spacing: 8) {
                ForEach(styles) { style in
                    styleSwatch(style)
                }
            }

            panelButton("Save Style...", symbol: "square.and.arrow.down") {
                saveStyleText = ""
                savingStyle = true
            }
        }
        // Both verbs that need a name take an alert, the way Save Theme does.
        // Rename carries the id it was opened on: the menu it came from is gone
        // by the time the field is typed into.
        .alert("Save Style", isPresented: $savingStyle) {
            TextField("Name", text: $saveStyleText)
            Button("Save") {
                let typed = saveStyleText.trimmingCharacters(in: .whitespaces)
                guard !typed.isEmpty else { return }
                host.saveObjectStyle(name: typed)
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Rename Style", isPresented: $renamingStyle) {
            TextField("Name", text: $renameStyleText)
            Button("Rename") {
                let typed = renameStyleText.trimmingCharacters(in: .whitespaces)
                guard !typed.isEmpty else { return }
                host.renameObjectStyle(styleId: renameStyleId, name: typed)
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// One saved look, painted: the fill or its gradient, the border over it, and
    /// a soft drop shadow when the style carries one. The name is the tooltip,
    /// since a 28pt square has nowhere to write it.
    ///
    /// The corner is clamped rather than scaled: a swatch has no shape's width to
    /// scale a radius against, and square against rounded is the part of it worth
    /// showing at this size.
    func styleSwatch(_ style: ObjectStyleChoice) -> some View {
        let shape = RoundedRectangle(cornerRadius: min(style.cornerRadius, 8), style: .continuous)
        return Button { host.applyObjectStyle(styleId: style.id) } label: {
            shape
                .fill(styleFill(style))
                .frame(width: 28, height: 28)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .overlay {
                    shape.inset(by: 0.5).stroke(
                        Color(argb: style.strokeColor),
                        lineWidth: max(1, min(style.strokeWidth, 3))
                    )
                }
                .shadow(color: style.hasShadow ? .black.opacity(0.5) : .clear, radius: 3, y: 1)
                .overlay {
                    if style.isCurrent { shape.inset(by: -2.5).stroke(palette.accent, lineWidth: 2) }
                }
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(style.name)
        .contextMenu {
            Button("Rename...") {
                renameStyleId = style.id
                renameStyleText = style.name
                renamingStyle = true
            }
            Button("Delete") { host.deleteObjectStyle(styleId: style.id) }
        }
    }

    /// What a swatch paints with: the style's gradient when it carries one, its
    /// flat fill when it does not. No angle at this size, so the stops run down.
    func styleFill(_ style: ObjectStyleChoice) -> AnyShapeStyle {
        guard style.hasGradient else { return AnyShapeStyle(Color(argb: style.fill)) }
        return AnyShapeStyle(
            LinearGradient(
                colors: [Color(argb: style.gradientStart), Color(argb: style.gradientEnd)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }

    /// A gradient commits whole, so a control that edits one of its three sends
    /// the other two back as they stand. No argument at all is the segment
    /// turning one on, which commits what the shape came back carrying.
    func setGradient(
        _ shape: ShapeFormat,
        start: Int64? = nil,
        end: Int64? = nil,
        angle: Double? = nil
    ) {
        host.setSelectedShapeGradient(
            start: start ?? shape.gradientStart,
            end: end ?? shape.gradientEnd,
            angle: Float(angle ?? shape.gradientAngle)
        )
    }

    /// A labelled colour well, the system picker at small size.
    func colorWell(
        _ label: String,
        argb: Int64,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            ColorPicker(
                "",
                selection: Binding(
                    get: { Color(argb: argb) },
                    set: { onPick(packedArgb($0)) }
                ),
                supportsOpacity: true
            )
            .labelsHidden()
            .controlSize(.small)
        }
    }

    // MARK: Code

    /// The code block's own style: what it is highlighted as, what palette it
    /// wears, and the two switches for the gutter and the long lines. Same
    /// contract as the Text and Shape sections, read off the primary and written
    /// to every unlocked code block in the selection.
    ///
    /// Both lists come off the host rather than being restated here: what
    /// highlights, what a palette is called, and what order they come in are the
    /// document's to say, the way the shape catalog's rows are.
    func codeSection(_ code: CodeFormat) -> some View {
        let languages = host.codeLanguages()
        let themes = host.codeThemes()
        return VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Code")

            stylePopup(languages, selected: Self.languageIndex(code.language, in: languages)) {
                index in host.setCodeLanguage(language: languages[index])
            }

            HStack(spacing: 8) {
                stylePopup(themes, selected: themes.firstIndex(of: code.theme) ?? 0) { index in
                    host.setCodeTheme(theme: themes[index])
                }

                ValueField(label: "", value: code.size, palette: palette, unit: "pt") {
                    host.setCodeFontSize(size: Float($0))
                }
                .frame(width: 78)
            }

            checkRow("Line Numbers", on: code.showLineNumbers) {
                host.setCodeLineNumbers(enabled: !code.showLineNumbers)
            }

            checkRow("Wrap", on: code.wrap) {
                host.setCodeWrap(enabled: !code.wrap)
            }
        }
    }

    /// The row a language marks. Case-insensitive, because the model stores what
    /// it was given and documents on disk carry lowercase names. One the list
    /// doesn't have marks Plain, which is what it highlights as anyway.
    static func languageIndex(_ language: String, in languages: [String]) -> Int {
        languages.firstIndex { $0.caseInsensitiveCompare(language) == .orderedSame }
            ?? languages.firstIndex(of: "Plain")
            ?? 0
    }

    // MARK: Terminal

    /// The terminal's own style: what its title bar is called, what prefixes a
    /// line of input, the type size, and whether the bar is drawn at all. Same
    /// contract as the Code section, except the title, which is content and so
    /// goes to the primary alone.
    func terminalSection(_ terminal: TerminalFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Terminal")

            StringField(placeholder: "Title", value: terminal.title, palette: palette) {
                host.setTerminalTitle(title: $0)
            }

            HStack(spacing: 8) {
                StringField(placeholder: "Prompt", value: terminal.prompt, palette: palette) {
                    host.setTerminalPrompt(prompt: $0)
                }

                ValueField(label: "", value: terminal.size, palette: palette, unit: "pt") {
                    host.setTerminalFontSize(size: Float($0))
                }
                .frame(width: 78)
            }

            checkRow("Show Title Bar", on: terminal.showTitleBar) {
                host.setTerminalTitleBar(enabled: !terminal.showTitleBar)
            }
        }
    }

    // MARK: Diagram

    /// The diagram's own style: the type size the labels are set in, and the
    /// four colours the layout paints with. Same contract as the Terminal
    /// section. Nothing here touches the source, which is content and is typed
    /// on the canvas.
    func diagramSection(_ diagram: DiagramFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Diagram")

            HStack(spacing: 8) {
                ValueField(label: "", value: diagram.size, palette: palette, unit: "pt") {
                    host.setDiagramFontSize(size: Float($0))
                }
                .frame(width: 78)
                Spacer(minLength: 0)
            }

            HStack(spacing: 8) {
                colorWell("Node Fill", argb: diagram.nodeFill) {
                    host.setDiagramNodeFill(argb: $0)
                }
                Spacer(minLength: 0)
                colorWell("Node Stroke", argb: diagram.nodeStroke) {
                    host.setDiagramNodeStroke(argb: $0)
                }
            }

            HStack(spacing: 8) {
                colorWell("Node Text", argb: diagram.nodeText) {
                    host.setDiagramNodeText(argb: $0)
                }
                Spacer(minLength: 0)
                colorWell("Edge", argb: diagram.edgeColor) {
                    host.setDiagramEdgeColor(argb: $0)
                }
            }
        }
    }

    // MARK: Equation

    /// The equation's own style: the size the expression is set at, and the
    /// colour it is drawn in. Same contract as the Diagram section. Nothing
    /// here touches the latex, which is content and is typed on the canvas.
    func equationSection(_ equation: EquationFormat) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Equation")

            HStack(spacing: 8) {
                ValueField(label: "", value: equation.size, palette: palette, unit: "pt") {
                    host.setEquationFontSize(size: Float($0))
                }
                .frame(width: 78)
                Spacer(minLength: 0)
                colorWell("Color", argb: equation.color) {
                    host.setEquationColor(argb: $0)
                }
            }
        }
    }

    func positionSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Position & Size")

            HStack(spacing: 8) {
                ValueField(label: "X", value: element.x, palette: palette) {
                    setFrame(element, x: $0)
                }
                ValueField(label: "Y", value: element.y, palette: palette) {
                    setFrame(element, y: $0)
                }
            }

            HStack(spacing: 8) {
                ValueField(label: "W", value: element.width, palette: palette) {
                    setFrame(element, width: $0)
                }
                ValueField(label: "H", value: element.height, palette: palette) {
                    setFrame(element, height: $0)
                }
            }
        }
    }

    /// A frame commits whole, so a field that edits one number sends the other
    /// three back as they stand.
    func setFrame(
        _ element: Selection,
        x: Double? = nil,
        y: Double? = nil,
        width: Double? = nil,
        height: Double? = nil
    ) {
        host.setSelectedElementFrame(
            x: Float(x ?? element.x),
            y: Float(y ?? element.y),
            width: Float(width ?? element.width),
            height: Float(height ?? element.height)
        )
    }

    func rotateSection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Rotate")

            HStack(spacing: 8) {
                ValueField(label: "\u{00B0}", value: element.rotation, palette: palette) {
                    host.setSelectedElementRotation(degrees: Float($0))
                }
                .frame(width: 104)

                Spacer(minLength: 0)

                flipButton(
                    "arrow.left.and.right.righttriangle.left.righttriangle.right",
                    help: "Flip Horizontally"
                ) { host.flipSelectedElement(axis: FlipAxis.horizontal) }

                flipButton(
                    "arrow.up.and.down.righttriangle.up.righttriangle.down",
                    help: "Flip Vertically"
                ) { host.flipSelectedElement(axis: FlipAxis.vertical) }
            }
        }
    }

    func flipButton(
        _ symbol: String,
        help: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12))
                .foregroundStyle(palette.ctrlText)
                .frame(width: 32, height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
        .help(help)
    }

    func opacitySection(_ element: Selection) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Opacity")

            HStack(spacing: 10) {
                Slider(
                    value: Binding(
                        get: { element.opacity },
                        set: { host.setSelectedElementOpacity(opacity: Float($0), commit: false) }
                    ),
                    in: 0...1,
                    onEditingChanged: { editing in
                        // The release commits whatever the previews left in the
                        // document, so the whole drag is one undo entry. Read it
                        // back rather than trusting this pass's snapshot.
                        guard !editing, let live = host.selectedElement() else { return }
                        host.setSelectedElementOpacity(opacity: live.opacity, commit: true)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                Text("\(Int((element.opacity * 100).rounded()))%")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .frame(width: 40, alignment: .trailing)
            }
        }
    }

    var arrangeSection: some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Arrange")

            HStack(spacing: 8) {
                arrangeButton("Bring Forward", ZOrderMove.forward)
                arrangeButton("Send Backward", ZOrderMove.backward)
            }

            HStack(spacing: 8) {
                arrangeButton("Bring to Front", ZOrderMove.tofront)
                arrangeButton("Send to Back", ZOrderMove.toback)
            }
        }
    }

    func arrangeButton(_ label: String, _ move: ZOrderMove) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { host.reorderSelectedElement(move: move) } label: {
            Text(label)
                .font(.system(size: 11.5))
                .foregroundStyle(palette.ctrlText)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// Full width and always live: it is the only way back into a locked element.
    func lockButton(_ element: Selection) -> some View {
        panelButton(
            element.locked ? "Unlock" : "Lock",
            symbol: element.locked ? "lock.fill" : "lock.open"
        ) { host.toggleSelectedElementLock() }
    }

    /// The panel's raised full-width button: the lock, and the group pair above it.
    func panelButton(
        _ label: String,
        symbol: String,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 11))
                Text(label)
                    .font(.system(size: 12.5))
            }
            .foregroundStyle(palette.ctrlText)
            .frame(maxWidth: .infinity)
            .frame(height: 26)
            .background(palette.buttonFill, in: shape)
            .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// A small bordered value field: whole document units, mono, committed on
    /// Enter or on losing focus. Anything that is not a number reverts to what
    /// the document holds, so a half-typed field cannot push nonsense in.
    private struct ValueField: View {
        let label: String
        let value: Double
        let palette: Palette
        /// Drawn after the field, for a number that means something ("pt").
        var unit: String? = nil
        /// 0 is the document-unit default: whole numbers, the way a frame reads.
        /// Line spacing is a multiplier, so it keeps its fraction.
        var decimals: Int = 0
        let onCommit: (Double) -> Void

        @State private var text: String = ""
        @FocusState private var focused: Bool

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
        }

        var body: some View {
            HStack(spacing: 6) {
                if !label.isEmpty {
                    Text(label)
                        .font(.system(size: 11))
                        .foregroundStyle(palette.subtle)
                        .frame(width: 11, alignment: .leading)
                }

                TextField("", text: $text)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .multilineTextAlignment(.trailing)
                    .focused($focused)
                    .padding(.horizontal, 7)
                    .frame(height: 22)
                    .background(palette.ctrl, in: shape)
                    .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                    .onSubmit { commit() }
                    .onChange(of: focused) { _, now in if !now { commit() } }

                if let unit {
                    Text(unit)
                        .font(.system(size: 11))
                        .foregroundStyle(palette.subtle)
                }
            }
            .onAppear { text = formatted(value) }
            // A canvas drag or an undo moves the element under the field. The
            // one being typed in is left alone until it loses focus.
            .onChange(of: value) { _, latest in if !focused { text = formatted(latest) } }
        }

        private func commit() {
            guard let typed = Double(text.trimmingCharacters(in: .whitespaces)) else {
                text = formatted(value)
                return
            }
            text = formatted(typed)
            onCommit(typed)
        }

        private func formatted(_ value: Double) -> String {
            if decimals == 0 { return String(Int(value.rounded())) }
            return String(format: "%.\(decimals)f", value)
        }
    }

    /// A line of text the document holds as one string: the text box's link, the
    /// shape's label. Committed on Enter or on losing focus, like the value
    /// fields; empty commits empty, and what that means is the setter's to say.
    private struct StringField: View {
        let placeholder: String
        let value: String
        let palette: Palette
        let onCommit: (String) -> Void

        @State private var text: String = ""
        @FocusState private var focused: Bool

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 5, style: .continuous)
        }

        var body: some View {
            TextField(placeholder, text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 12))
                .foregroundStyle(palette.ctrlText)
                .focused($focused)
                .padding(.horizontal, 7)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .onSubmit { commit() }
                .onChange(of: focused) { _, now in if !now { commit() } }
                .onAppear { text = value }
                .onChange(of: value) { _, latest in if !focused { text = latest } }
        }

        private func commit() {
            let typed = text.trimmingCharacters(in: .whitespaces)
            text = typed
            onCommit(typed)
        }
    }

    /// How long the transition takes, in seconds, with the number beside it.
    /// The drag rides on this view's own value and the release is what commits,
    /// so the whole drag is one edit and one undo entry, the way the opacity
    /// slider's release is. A transition has no preview event to stream through
    /// the loop the way an element's opacity does, which is the one difference.
    ///
    /// The dragged value is cleared by the state coming back rather than by the
    /// release, so the thumb never flicks back to where it started for the frame
    /// between the two.
    private struct DurationSlider: View {
        let value: Double
        let palette: Palette
        let onCommit: (Double) -> Void

        @State private var dragged: Double? = nil

        var body: some View {
            HStack(spacing: 10) {
                Slider(
                    value: Binding(get: { dragged ?? value }, set: { dragged = $0 }),
                    in: 0.1...3,
                    onEditingChanged: { editing in
                        guard !editing, let latest = dragged else { return }
                        onCommit(latest)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                Text(String(format: "%.1fs", dragged ?? value))
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .frame(width: 40, alignment: .trailing)
            }
            .onChange(of: value) { _, _ in dragged = nil }
        }
    }

    // MARK: Animate panel

    /// The slide's build order, and under it the transition, which by Keynote's
    /// convention is the one that plays on the way *out* of the slide.
    ///
    /// Layout mode shows none of it: a layout is a template for what a slide
    /// draws, and nothing on it is ever played.
    @ViewBuilder func animatePanel(_ ui: Chrome) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Layout.panelPadding) {
                if ui.editingLayouts {
                    Text("Layouts have no builds or transitions.")
                        .font(.system(size: 12))
                        .foregroundStyle(palette.faint)
                } else {
                    buildSection(ui)
                    palette.divider.frame(height: 1)
                    transitionSection(ui)
                }
            }
            .padding(Layout.panelPadding)
            .frame(maxWidth: .infinity, alignment: .top)
        }
        .scrollContentBackground(.hidden)
    }

    // MARK: Build order

    /// The order the slide's builds play in, the three ways to add one, and the
    /// editor for whichever row is picked. The picked row is this view's own
    /// state: a build is not a thing the document can be "on", so which one is
    /// being edited is the panel's business alone.
    @ViewBuilder func buildSection(_ ui: Chrome) -> some View {
        let slideIndex = host.selectedSlideIndex()

        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Build Order")

            // Above the list rather than under it: what the builds do is the
            // question the panel opens with, and playing the slide answers it.
            panelButton("Preview", symbol: "play.rectangle") { startPreview() }

            if ui.builds.isEmpty {
                Text("Nothing builds on this slide.")
                    .font(.system(size: 11.5))
                    .foregroundStyle(palette.faint)
            } else {
                buildList(ui)
            }

            addBuildButtons(ui)

            if let entry = ui.builds.first(where: { $0.index == selectedBuild }) {
                palette.divider.frame(height: 1)
                buildEditor(entry, ui)
            }
        }
        // The picked row is a place in this slide's order, so it means nothing
        // on the next slide.
        .onChange(of: slideIndex) { _, _ in selectedBuild = nil }
    }

    func buildList(_ ui: Chrome) -> some View {
        VStack(spacing: 4) {
            ForEach(ui.builds) { entry in
                BuildOrderRow(
                    entry: entry,
                    picked: entry.index == selectedBuild,
                    dragging: buildDrag?.index == entry.index,
                    liftY: buildDrag?.index == entry.index ? buildDrag?.translationY ?? 0 : 0,
                    palette: palette,
                    onTap: {
                        selectedBuild = entry.index
                        host.selectBuildElement(index: Int32(entry.index))
                    },
                    onDrag: { point, translation in
                        dragBuild(entry, to: point, by: translation, in: ui.builds)
                    },
                    onDrop: dropBuild
                )
            }
        }
        .coordinateSpace(name: BuildSpace.name)
        .onPreferenceChange(BuildRowFrames.self) { frames in buildRowFrames = frames }
        .overlay(alignment: .topLeading) { buildDropLine }
        .animation(.easeInOut(duration: 0.2), value: ui.builds.map(\.index))
    }

    /// The gap the drag is over: after the last row whose midpoint the pointer
    /// has passed, and above the first row until it passes one.
    func dragBuild(_ entry: BuildEntry, to point: CGPoint, by translation: CGSize, in builds: [BuildEntry]) {
        let placed: [(row: BuildEntry, frame: CGRect)] = builds.compactMap { row in
            buildRowFrames[row.index].map { (row, $0) }
        }
        guard let first = placed.first else { return }

        var gap = 0
        // Half the 4pt row gap above the first row, so the line sits in the gap
        // rather than on a row's edge.
        var lineY: CGFloat = first.frame.minY - 2
        for (row, frame) in placed {
            if frame.midY >= point.y { break }
            gap = row.index + 1
            lineY = frame.maxY + 2
        }

        buildDrag = BuildDrag(
            index: entry.index, gap: gap, lineY: lineY, translationY: translation.height
        )
    }

    /// A drop back into the row's own gap moves nothing, so it is not sent. A
    /// reorder renumbers the rows around it, so the pick follows the row it was
    /// on and is dropped when it was on another one.
    func dropBuild() {
        guard let drag = buildDrag else { return }
        let destination = drag.destination
        buildDrag = nil
        guard destination != drag.index else { return }

        selectedBuild = selectedBuild == drag.index ? destination : nil
        withAnimation(.easeInOut(duration: 0.2)) {
            host.moveBuild(from: Int32(drag.index), to: Int32(destination))
        }
    }

    @ViewBuilder var buildDropLine: some View {
        if let drag = buildDrag {
            palette.accent
                .frame(height: 2)
                .offset(y: drag.lineY - 1)
        }
    }

    /// The three ways a build starts life, on the primary element. Dead with
    /// nothing selected: a build is always about some element.
    func addBuildButtons(_ ui: Chrome) -> some View {
        HStack(spacing: 6) {
            addBuildButton("Build In", next: ui.builds.count) { host.addBuildIn() }
            addBuildButton("Build Out", next: ui.builds.count) { host.addBuildOut() }
            addBuildButton("Action", next: ui.builds.count) { host.addAction() }
        }
        .disabled(ui.element == nil)
        .opacity(ui.element == nil ? 0.45 : 1)
    }

    /// A new build lands at the end of the order, so [next] is the row the panel
    /// opens the editor on. It shows up next pass; picking it now is what makes
    /// adding one and editing it a single gesture.
    func addBuildButton(
        _ label: String,
        next: Int,
        action: @escaping () -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button {
            selectedBuild = next
            action()
        } label: {
            Text(label)
                .font(.system(size: 11.5))
                .foregroundStyle(palette.ctrlText)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// What the picked build plays, how long it takes and what starts it. An
    /// action shows what it does to the element instead of an effect, since it
    /// is not bringing anything on or taking it away.
    @ViewBuilder func buildEditor(_ entry: BuildEntry, _ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel(entry.isAction ? "Action" : "Effect")

            if entry.isAction {
                stylePopup(ui.actionKinds, selected: entry.actionKindIndex ?? 0) {
                    commitBuild(entry, actionKindIndex: $0)
                }

                actionFields(entry)
            } else {
                stylePopup(ui.buildEffects, selected: entry.effectIndex) {
                    commitBuild(entry, effectIndex: $0)
                }

                sectionLabel("Delivery")

                // The element's own list, not the model's: what a build can be
                // handed over in is whatever it has pieces of.
                stylePopup(entry.deliveryTitles, selected: entry.deliveryIndex) {
                    commitBuild(entry, deliveryIndex: $0)
                }
            }

            sectionLabel("Duration")

            DurationSlider(value: entry.duration, palette: palette) {
                commitBuild(entry, duration: $0)
            }

            sectionLabel("Trigger")

            HStack(spacing: 2) {
                ForEach(Array(ui.buildTriggers.enumerated()), id: \.offset) { index, name in
                    segment(name, on: index == entry.triggerIndex) {
                        commitBuild(entry, triggerIndex: index)
                    }
                }
            }
            .padding(2)
            .frame(height: 26)
            .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

            // Only a build that waits out the one before it has a wait to set.
            if entry.triggerIndex == 2 {
                sectionLabel("Delay")

                ValueField(
                    label: "",
                    value: entry.delay,
                    palette: palette,
                    unit: "s",
                    decimals: 1
                ) {
                    commitBuild(entry, delay: $0)
                }
                .frame(width: 104)
            }

            // Only an element with steps of its own has one to move to.
            if entry.hasStepTarget {
                sectionLabel("Step")

                ValueField(label: "", value: Double(entry.elementStep ?? 0), palette: palette) {
                    commitBuild(entry, elementStep: Int($0.rounded()))
                }
                .frame(width: 104)
            }

            panelButton("Remove Build", symbol: "minus.circle") {
                selectedBuild = nil
                host.removeBuild(index: Int32(entry.index))
            }
        }
    }

    /// What the picked action changes: only the fields its kind reads, the way
    /// the model reads only those and leaves the rest where they are.
    @ViewBuilder func actionFields(_ entry: BuildEntry) -> some View {
        switch entry.actionKindIndex ?? 0 {
        case 0:
            HStack(spacing: 8) {
                ValueField(label: "X", value: entry.dx, palette: palette) {
                    commitBuild(entry, dx: $0)
                }
                ValueField(label: "Y", value: entry.dy, palette: palette) {
                    commitBuild(entry, dy: $0)
                }
            }

        case 1:
            RatioSlider(value: entry.opacity, palette: palette) {
                commitBuild(entry, opacity: $0)
            }

        case 2:
            ValueField(label: "", value: entry.rotation, palette: palette, unit: "°") {
                commitBuild(entry, rotation: $0)
            }
            .frame(width: 104)

        default:
            ValueField(
                label: "",
                value: entry.scale,
                palette: palette,
                unit: "×",
                decimals: 2
            ) {
                commitBuild(entry, scale: $0)
            }
            .frame(width: 104)
        }
    }

    /// A build commits whole, so a control that changes one thing sends the rest
    /// back as they stand. The same deal `commitTransition` takes.
    func commitBuild(
        _ entry: BuildEntry,
        effectIndex: Int? = nil,
        deliveryIndex: Int? = nil,
        triggerIndex: Int? = nil,
        duration: Double? = nil,
        delay: Double? = nil,
        elementStep: Int? = nil,
        actionKindIndex: Int? = nil,
        dx: Double? = nil,
        dy: Double? = nil,
        opacity: Double? = nil,
        rotation: Double? = nil,
        scale: Double? = nil
    ) {
        host.updateBuild(
            index: Int32(entry.index),
            effectIndex: Int32(effectIndex ?? entry.effectIndex),
            deliveryIndex: Int32(deliveryIndex ?? entry.deliveryIndex),
            triggerIndex: Int32(triggerIndex ?? entry.triggerIndex),
            durationMs: Int32(((duration ?? entry.duration) * 1000).rounded()),
            delayMs: Int32(((delay ?? entry.delay) * 1000).rounded()),
            elementStep: Int32(elementStep ?? entry.elementStep ?? -1),
            actionKindIndex: Int32(actionKindIndex ?? entry.actionKindIndex ?? -1),
            dx: Float(dx ?? entry.dx),
            dy: Float(dy ?? entry.dy),
            opacity: Float(opacity ?? entry.opacity),
            rotation: Float(rotation ?? entry.rotation),
            scale: Float(scale ?? entry.scale)
        )
    }

    /// One row of the build order: the badge, what it plays, and the grab glyph.
    /// The row under the editor wears the accent fill and border; a row whose
    /// element is merely selected on the canvas takes the accent badge alone, so
    /// the other builds on that element are visible without looking edited.
    private struct BuildOrderRow: View {
        let entry: BuildEntry
        /// This is the row the editor below the list is about.
        let picked: Bool
        /// This row is the one being dragged, so it steps back while it travels.
        let dragging: Bool
        /// How far this row has been carried by the drag, 0 when it hasn't.
        let liftY: CGFloat
        let palette: Palette
        let onTap: () -> Void
        /// A drag sample, in [BuildSpace] plus its travel, and the release.
        let onDrag: (CGPoint, CGSize) -> Void
        let onDrop: () -> Void

        @State private var hovering = false

        private var shape: RoundedRectangle {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
        }

        var body: some View {
            HStack(spacing: 9) {
                Text("\(entry.index + 1)")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundStyle(
                        picked || entry.active ? palette.accentText : palette.badgeOffText
                    )
                    .frame(width: 17, height: 17)
                    .background(picked || entry.active ? palette.accent : palette.badgeOff, in: Circle())

                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.title)
                        .font(.system(size: 12))
                        .foregroundStyle(palette.text)
                        .lineLimit(1)
                        .truncationMode(.middle)
                    Text(entry.meta)
                        .font(.system(size: 10.5))
                        .foregroundStyle(palette.subtle)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Text("⠿")
                    .font(.system(size: 11.5))
                    .foregroundStyle(palette.subtle)
            }
            .padding(EdgeInsets(top: 7, leading: 9, bottom: 7, trailing: 9))
            .background(fill, in: shape)
            .overlay {
                if picked { shape.inset(by: 0.5).stroke(palette.accent, lineWidth: 1) }
            }
            // Lifted: a touch translucent to show the rows it passes over, and
            // above them while it travels.
            .opacity(dragging ? 0.85 : 1)
            .offset(y: liftY)
            .zIndex(dragging ? 1 : 0)
            .contentShape(shape)
            .onTapGesture(perform: onTap)
            // Enough slop that a click is still a click, like the navigator's.
            .gesture(
                DragGesture(minimumDistance: 6, coordinateSpace: .named(BuildSpace.name))
                    .onChanged { value in onDrag(value.location, value.translation) }
                    .onEnded { _ in onDrop() }
            )
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: BuildRowFrames.self,
                        value: [entry.index: proxy.frame(in: .named(BuildSpace.name))]
                    )
                }
            }
            .onHover { hovering = $0 }
        }

        /// The design's active fill is the accent at 22%, which is what the
        /// picked row wears; everything else is the row fill, lifted on hover.
        private var fill: Color {
            if picked { return palette.accent.opacity(0.22) }
            return hovering ? palette.rowHov : palette.rowBg
        }
    }

    /// A fraction the element draws at, 0 to 1 with a percentage beside it. The
    /// drag rides on this view's own value and the release commits, so the whole
    /// drag is one edit, exactly like `DurationSlider`.
    private struct RatioSlider: View {
        let value: Double
        let palette: Palette
        let onCommit: (Double) -> Void

        @State private var dragged: Double? = nil

        var body: some View {
            HStack(spacing: 10) {
                Slider(
                    value: Binding(get: { dragged ?? value }, set: { dragged = $0 }),
                    in: 0...1,
                    onEditingChanged: { editing in
                        guard !editing, let latest = dragged else { return }
                        onCommit(latest)
                    }
                )
                .controlSize(.small)
                .tint(palette.accent)

                Text("\(Int(((dragged ?? value) * 100).rounded()))%")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(palette.ctrlText)
                    .frame(width: 40, alignment: .trailing)
            }
            .onChange(of: value) { _, _ in dragged = nil }
        }
    }

    /// Default first, then the kinds. What a control shows below depends on
    /// which is picked: only the kinds that travel have a direction, and only an
    /// automatic slide has a delay to sit through.
    @ViewBuilder func transitionSection(_ ui: Chrome) -> some View {
        let transition = ui.transition

        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Transition")

            // Default is the head of the list rather than a segment of its own,
            // so the whole choice is one popup: -1 and the kinds, off by one.
            stylePopup(["Default"] + ui.transitionKinds, selected: transition.kindIndex + 1) {
                commitTransition(transition, kindIndex: $0 - 1)
            }

            if Self.directionalKinds.contains(transitionKindTitle(ui)) {
                HStack(spacing: 2) {
                    ForEach(Array(ui.transitionDirections.enumerated()), id: \.offset) { index, name in
                        segment(name, on: index == transition.directionIndex) {
                            commitTransition(transition, directionIndex: index)
                        }
                    }
                }
                .padding(2)
                .frame(height: 26)
                .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
            }

            if transition.kindIndex >= 0 {
                sectionLabel("Duration")

                DurationSlider(value: transition.duration, palette: palette) {
                    commitTransition(transition, duration: $0)
                }

                sectionLabel("Trigger")

                HStack(spacing: 2) {
                    segment("On Click", on: !transition.automatic) {
                        commitTransition(transition, automatic: false)
                    }
                    segment("Automatically", on: transition.automatic) {
                        commitTransition(transition, automatic: true)
                    }
                }
                .padding(2)
                .frame(height: 26)
                .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

                if transition.automatic {
                    ValueField(
                        label: "",
                        value: transition.delay,
                        palette: palette,
                        unit: "s",
                        decimals: 1
                    ) {
                        commitTransition(transition, delay: $0)
                    }
                    .frame(width: 104)
                }
            }
        }
    }

    /// What the popup is set to, "" while the slide is on the deck's default.
    func transitionKindTitle(_ ui: Chrome) -> String {
        let index = ui.transition.kindIndex
        guard ui.transitionKinds.indices.contains(index) else { return "" }
        return ui.transitionKinds[index]
    }

    /// The kinds that travel, and so the only ones with a direction to pick. By
    /// name rather than by position: an ordinal from the other side of the
    /// boundary is not something this file should be spelling.
    static let directionalKinds: Set<String> = ["Push", "Move In", "Wipe"]

    /// A transition commits whole, so a control that changes one thing sends the
    /// other four back as they stand. The same deal `setFrame` takes.
    func commitTransition(
        _ transition: TransitionFormat,
        kindIndex: Int? = nil,
        directionIndex: Int? = nil,
        duration: Double? = nil,
        automatic: Bool? = nil,
        delay: Double? = nil
    ) {
        host.setTransition(
            kindIndex: Int32(kindIndex ?? transition.kindIndex),
            directionIndex: Int32(directionIndex ?? transition.directionIndex),
            durationMs: Int32(((duration ?? transition.duration) * 1000).rounded()),
            automatic: automatic ?? transition.automatic,
            delayMs: Int32(((delay ?? transition.delay) * 1000).rounded())
        )
    }

    // MARK: Document panel

    /// The selected slide: the layout it is on, the number switch and the
    /// background, all riding through `states`.
    ///
    /// In layout mode the panel is about the layout being edited instead, so it
    /// shows what a layout has (a name, its placeholders) and drops what only a
    /// slide has (the layout card, the appearance switches).
    @ViewBuilder func documentPanel(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            if ui.editingLayouts {
                layoutNameSection(ui)
                palette.divider.frame(height: 1)
                placeholdersSection(ui)
                palette.divider.frame(height: 1)
                backgroundSection(ui)
                Spacer(minLength: 0)
                panelButton("Done", symbol: "checkmark") { host.exitSlideLayouts() }
            } else {
                themeSection(ui)
                palette.divider.frame(height: 1)
                slideSizeSection(ui)
                palette.divider.frame(height: 1)
                deckBackgroundSection(ui)
                palette.divider.frame(height: 1)
                slideLayoutCard(ui)
                reapplyLayoutButton(ui)
                appearanceSection(ui)
                palette.divider.frame(height: 1)
                backgroundSection(ui)
                Spacer(minLength: 0)
                editLayoutButton
            }
        }
        .padding(Layout.panelPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }

    /// The layout the slide is on, and the pick that moves it to another. "None"
    /// is a real choice, not an empty state: a slide on no layout keeps
    /// everything it has and simply inherits nothing.
    func slideLayoutCard(_ ui: Chrome) -> some View {
        Menu {
            Button("None") { host.applyLayout(layoutId: nil) }
            Divider()
            ForEach(ui.layouts) { layout in
                Button(layout.name) { host.applyLayout(layoutId: layout.id) }
            }
        } label: {
            HStack(spacing: 12) {
                layoutPreview(ui)
                VStack(alignment: .leading, spacing: 0) {
                    Text("Slide Layout")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.subtle)
                    Text(currentLayoutName(ui))
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(palette.text)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Text("\u{2304}")
                    .font(.system(size: 10))
                    .foregroundStyle(palette.subtle)
            }
            .padding(10)
            .background(palette.ctrl, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
        }
        .menuStyle(.borderlessButton)
        .menuIndicator(.hidden)
    }

    func currentLayoutName(_ ui: Chrome) -> String {
        ui.layouts.first { $0.id == ui.slideLayoutId }?.name ?? "None"
    }

    /// Every placeholder back where the layout puts it. Off while the slide is
    /// on no layout: there is nothing to put it back to.
    func reapplyLayoutButton(_ ui: Chrome) -> some View {
        panelButton("Reapply Layout", symbol: "arrow.clockwise") { host.reapplyLayout() }
            .disabled(!ui.canReapplyLayout)
            .opacity(ui.canReapplyLayout ? 1 : 0.45)
    }

    /// The layout the slide is on, rendered by the shared Compose renderer the
    /// way a navigator thumbnail is. The drawn bars are the fallback, for a slide
    /// on no layout and for the moment before a render lands.
    @ViewBuilder func layoutPreview(_ ui: Chrome) -> some View {
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
        if let id = ui.slideLayoutId, let image = host.layoutThumbnail(layoutId: id, width: 62) {
            Image(nsImage: image)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 62)
                .clipShape(shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.thumbEdge, lineWidth: 1) }
        } else {
            layoutPreviewBars
        }
    }

    /// A slide the way a layout picker draws one: white paper, three grey bars.
    var layoutPreviewBars: some View {
        let shape = RoundedRectangle(cornerRadius: 3, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) {
            previewBar(width: 34, height: 4, color: Color(rgb: 0x2A2630))
            previewBar(width: 23, height: 3, color: Color(rgb: 0x9A958D))
                .padding(.top, 3)
            previewBar(width: 17, height: 2.5, color: Color(rgb: 0xC9C4BD))
                .padding(.top, 10)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .frame(width: 62, height: 35, alignment: .topLeading)
        .background(Color.white, in: shape)
        .overlay { shape.inset(by: 0.5).stroke(Color.black.opacity(0.12), lineWidth: 1) }
    }

    /// The layout's name, which is what the navigator row and the slide's layout
    /// popup both show. Committed like every other string field here.
    func layoutNameSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Name")
            StringField(
                placeholder: "Layout",
                value: ui.slideTitle,
                palette: palette
            ) { typed in
                guard !typed.isEmpty else { return }
                host.renameSelectedSlide(title: typed)
            }
        }
    }

    /// What a slide on this layout fills in. The buttons are the roles Kotlin
    /// lists, in its order: the index is what goes back, so a role added to the
    /// document model shows up here with nothing to change but its glyph.
    func placeholdersSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Placeholders")
            ForEach(Array(ui.placeholderRoles.enumerated()), id: \.offset) { index, role in
                panelButton(role, symbol: Self.placeholderSymbol(role)) {
                    host.addPlaceholder(role: Int32(index))
                }
            }
        }
    }

    /// The glyph for a role, by name. An unknown one still draws a button: the
    /// list is the document model's to grow, and a missing glyph is not a reason
    /// to hide the role it belongs to.
    static func placeholderSymbol(_ role: String) -> String {
        switch role {
        case "Title": return "textformat"
        case "Body": return "text.alignleft"
        case "Media": return "photo"
        case "Code": return "chevron.left.forwardslash.chevron.right"
        default: return "square.dashed"
        }
    }

    func previewBar(width: CGFloat, height: CGFloat, color: Color) -> some View {
        RoundedRectangle(cornerRadius: 1).fill(color).frame(width: width, height: height)
    }

    /// Title and Body are still inert: they switch a layout's placeholders off
    /// for this one slide, which the document model has no word for yet. The
    /// number is the slide's own.
    func appearanceSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Appearance")
            checkRow("Title", on: true)
            checkRow("Body", on: true)
            checkRow("Slide Number", on: ui.slide.numberVisible) {
                host.setSlideNumberVisible(visible: !ui.slide.numberVisible)
            }
        }
    }

    func sectionLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(palette.subtle)
    }

    /// [action] nil is a row that only reports: the ones whose fact the document
    /// model does not hold yet stay untouchable rather than lying about a toggle.
    @ViewBuilder func checkRow(
        _ label: String,
        on: Bool,
        action: (() -> Void)? = nil
    ) -> some View {
        let row = HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(on ? palette.accent : palette.track)
                .frame(width: 15, height: 15)
                .overlay {
                    if on {
                        Image(systemName: "checkmark")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(palette.accentText)
                    }
                }
            Text(label)
                .font(.system(size: 13))
                .foregroundStyle(palette.text)
            Spacer(minLength: 0)
        }

        if let action {
            Button(action: action) { row.contentShape(Rectangle()) }
                .buttonStyle(.plain)
        } else {
            row
        }
    }

    /// What the slide paints behind its elements: the deck's own, a flat colour,
    /// or a two-stop gradient. Switching to a kind the slide is not wearing
    /// commits it there and then, so the swatches below always have something to
    /// mark, and every pick is one edit and one undo entry.
    func backgroundSection(_ ui: Chrome) -> some View {
        let slide = ui.slide
        return backgroundControls(
            "Background",
            kind: slide.backgroundKind,
            color: slide.color,
            gradientStart: slide.gradientStart,
            gradientEnd: slide.gradientEnd,
            inherits: "The deck's own background.",
            onDefault: { host.setBackgroundDefault() },
            onColor: { host.setBackgroundColor(argb: $0) },
            onGradient: { host.setBackgroundGradient(start: $0, end: $1) }
        )
    }

    /// The same three kinds, one level down: what a slide falls back to when it
    /// carries none of its own. The controls are the slide's, handed the deck's
    /// four numbers and the deck's setters, because a background is a background
    /// wherever it hangs.
    func deckBackgroundSection(_ ui: Chrome) -> some View {
        let deck = ui.deck
        return backgroundControls(
            "Deck Background",
            kind: deck.backgroundKind,
            color: deck.color,
            gradientStart: deck.gradientStart,
            gradientEnd: deck.gradientEnd,
            inherits: "The app's own dark gradient.",
            onDefault: { host.setDeckBackgroundDefault() },
            onColor: { host.setDeckBackgroundColor(argb: $0) },
            onGradient: { host.setDeckBackgroundGradient(start: $0, end: $1) }
        )
    }

    /// [inherits] is what the Default segment means here: the thing behind is a
    /// different thing for a slide than for the deck, and it is the only word
    /// that changes between the two.
    @ViewBuilder func backgroundControls(
        _ title: String,
        kind: Int,
        color: Int64,
        gradientStart: Int64,
        gradientEnd: Int64,
        inherits: String,
        onDefault: @escaping () -> Void,
        onColor: @escaping (Int64) -> Void,
        onGradient: @escaping (Int64, Int64) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel(title)

            HStack(spacing: 2) {
                segment("Default", on: kind == 0, action: onDefault)
                segment("Color", on: kind == 1) { onColor(color) }
                segment("Gradient", on: kind == 2) { onGradient(gradientStart, gradientEnd) }
            }
            .padding(2)
            .frame(height: 26)
            .background(palette.segBg, in: RoundedRectangle(cornerRadius: 6, style: .continuous))

            switch kind {
            case 1:
                swatchGrid(selected: color, onPick: onColor)
            case 2:
                stopRow("Start", selected: gradientStart) { onGradient($0, gradientEnd) }
                stopRow("End", selected: gradientEnd) { onGradient(gradientStart, $0) }
            default:
                Text(inherits)
                    .font(.system(size: 12))
                    .foregroundStyle(palette.faint)
            }
        }
    }

    /// The look the whole deck is on: the pick that swaps it, and the two verbs
    /// that make one of the user's own out of what the deck is already wearing.
    ///
    /// Delete is only offered for a theme the user saved: a built-in is not the
    /// library's to drop, and the popup would come back one short next launch.
    @ViewBuilder func themeSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Theme")

            Menu {
                ForEach(ui.themeNames, id: \.self) { name in
                    Button(name) { host.changeTheme(name: name) }
                }
            } label: {
                popupLabel(ui.themeName)
            }
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)

            panelButton("Save Theme...", symbol: "square.and.arrow.down") {
                saveThemeText = ui.themeName
                savingTheme = true
            }

            if ui.userThemeNames.contains(ui.themeName) {
                panelButton("Delete Theme", symbol: "trash") {
                    host.deleteUserTheme(name: ui.themeName)
                }
            }
        }
        // Saving is the one verb here with something to ask, so it takes a
        // sheet, the way renaming a layout does in the navigator. Prefilled with
        // the current name: saving over your own theme is the common case.
        .alert("Save Theme", isPresented: $savingTheme) {
            TextField("Name", text: $saveThemeText)
            Button("Save") {
                let typed = saveThemeText.trimmingCharacters(in: .whitespaces)
                guard !typed.isEmpty else { return }
                host.saveAsTheme(name: typed)
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    /// A panel-wide popup's face: what it is set to, and the chevron. Shared by
    /// the two deck-wide pickers so they cannot drift apart.
    func popupLabel(_ title: String) -> some View {
        let shape = RoundedRectangle(cornerRadius: 9, style: .continuous)
        return HStack(spacing: 8) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(palette.text)
                .lineLimit(1)
            Spacer(minLength: 0)
            Text("\u{2304}")
                .font(.system(size: 10))
                .foregroundStyle(palette.subtle)
        }
        .padding(.horizontal, 10)
        .frame(height: 30)
        .background(palette.ctrl, in: shape)
        .contentShape(shape)
    }

    /// The shape every slide in the deck is cut to. Deck-wide, so it sits with
    /// the theme rather than with the slide's own background below.
    ///
    /// Either pick asks whether the content comes with the slide before it
    /// moves: both answers are ones people want, and a deck resized the wrong
    /// way is not something the eye can put back.
    @ViewBuilder func slideSizeSection(_ ui: Chrome) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            sectionLabel("Slide Size")

            Menu {
                ForEach(Array(ui.slideSize.presetTitles.enumerated()), id: \.offset) { index, title in
                    Button(title) { pendingSizePreset = index }
                }
                Divider()
                Button("Custom...") {
                    customWidthText = String(Int(ui.slideSize.width.rounded()))
                    customHeightText = String(Int(ui.slideSize.height.rounded()))
                    customSize = true
                }
            } label: {
                popupLabel(ui.slideSize.title)
            }
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)
        }
        // The picked preset is the question's subject, so it presents the alert
        // and clears when it closes: no second flag to fall out of step with.
        .alert(
            "Scale content to fit the new size?",
            isPresented: Binding(
                get: { pendingSizePreset != nil },
                set: { shown in if !shown { pendingSizePreset = nil } }
            ),
            presenting: pendingSizePreset
        ) { index in
            Button("Scale") { host.setSlideSizePreset(index: Int32(index), scaleContent: true) }
            Button("Don't Scale") { host.setSlideSizePreset(index: Int32(index), scaleContent: false) }
            Button("Cancel", role: .cancel) {}
        }
        // A sheet rather than an alert, because the answer here is a checkbox
        // beside two fields, and an alert holds nothing but fields and buttons.
        .sheet(isPresented: $customSize) { customSizeSheet }
    }

    /// Two measurements and the same scale question the presets ask, answered
    /// before the resize. Nonsense in either field is no resize: the sheet
    /// closes and the deck keeps the slide it had.
    var customSizeSheet: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Custom Slide Size")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(palette.text)

            HStack(spacing: 10) {
                sizeField("Width", text: $customWidthText)
                sizeField("Height", text: $customHeightText)
            }

            Toggle("Scale content", isOn: $customScaleContent)
                .toggleStyle(.checkbox)
                .font(.system(size: 12))
                .foregroundStyle(palette.text)

            HStack(spacing: 8) {
                Spacer(minLength: 0)
                Button("Cancel") { customSize = false }
                    .keyboardShortcut(.cancelAction)
                Button("OK") { commitCustomSize() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(16)
        .frame(width: 268)
    }

    /// One measurement of the custom sheet, in document units.
    func sizeField(_ label: String, text: Binding<String>) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return VStack(alignment: .leading, spacing: 5) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            TextField("", text: text)
                .textFieldStyle(.plain)
                .font(.system(size: 12, design: .monospaced))
                .foregroundStyle(palette.ctrlText)
                .multilineTextAlignment(.trailing)
                .padding(.horizontal, 7)
                .frame(height: 22)
                .background(palette.ctrl, in: shape)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .onSubmit { commitCustomSize() }
        }
    }

    func commitCustomSize() {
        customSize = false
        let width = Double(customWidthText.trimmingCharacters(in: .whitespaces))
        let height = Double(customHeightText.trimmingCharacters(in: .whitespaces))
        guard let width, let height else { return }
        host.setSlideSize(
            width: Float(width),
            height: Float(height),
            scaleContent: customScaleContent
        )
    }

    /// One end of the gradient: the same swatches, said whose they are.
    func stopRow(
        _ label: String,
        selected: Int64,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(palette.subtle)
            swatchGrid(selected: selected, onPick: onPick)
        }
    }

    /// The palette, six to a row. No colour panel yet: one tap is one colour and
    /// one undo entry, which a live picker would spend a hundred entries on.
    func swatchGrid(selected: Int64, onPick: @escaping (Int64) -> Void) -> some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 6),
            spacing: 6
        ) {
            ForEach(Self.backgroundSwatches, id: \.self) { argb in
                swatch(argb, selected: argb == selected, onPick: onPick)
            }
        }
    }

    func swatch(
        _ argb: Int64,
        selected: Bool,
        onPick: @escaping (Int64) -> Void
    ) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Button { onPick(argb) } label: {
            shape
                .fill(Color(argb: argb))
                .frame(height: 22)
                .overlay { shape.inset(by: 0.5).stroke(palette.hairline, lineWidth: 1) }
                .overlay {
                    if selected { shape.inset(by: -2.5).stroke(palette.accent, lineWidth: 2) }
                }
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }

    /// The deck's inks first, then the accents, then two paper tones. Packed
    /// ARGB, the document model's colour format.
    private static let backgroundSwatches: [Int64] = [
        0xFF000000, 0xFF17181C, 0xFF23262E, 0xFF101223, 0xFF2A2452, 0xFF4C2FA8,
        0xFF0F3B39, 0xFF10391F, 0xFF58151D, 0xFF6B4A0E, 0xFFD7D9DE, 0xFFFFFFFF,
    ]

    func segment(_ label: String, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: on ? .semibold : .regular))
                .foregroundStyle(on ? palette.accentText : palette.subtle)
                // Three of them across the panel is a tight fit, so a long name
                // shrinks rather than truncating to nothing readable.
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    on ? AnyShapeStyle(palette.accent) : AnyShapeStyle(Color.clear),
                    in: RoundedRectangle(cornerRadius: 5, style: .continuous)
                )
                .contentShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    /// Into layout mode, on the layout this slide is already on. No symbol, the
    /// way the design draws it: a bare label across the foot of the panel.
    var editLayoutButton: some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return Button { host.editSlideLayouts() } label: {
            Text("Edit Slide Layout")
                .font(.system(size: 12.5))
                .foregroundStyle(palette.ctrlText)
                .frame(maxWidth: .infinity)
                .frame(height: 26)
                .background(palette.buttonFill, in: shape)
                .contentShape(shape)
        }
        .buttonStyle(.plain)
    }
}
