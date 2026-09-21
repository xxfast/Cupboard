import SwiftUI
import AppKit
import CupboardCanvas

enum NavigatorSpace {
    static let name = "navigator"
}

/// Where each row sits in [NavigatorSpace], keyed by slide id rather than by
/// index: a reorder moves rows between indices, and the drag has to keep
/// following the row it picked up.
struct RowFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]

    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { _, latest in latest }
    }
}

/// A navigator drag in flight, Keynote's: the rows leave the list, a bare
/// thumbnail rides the pointer, and a gap with a bar at the landing level opens
/// where they would drop. SwiftUI state, unlike the Compose canvas's gestures:
/// this tree redraws off `@State` the way it is built to, and nothing but the
/// drop is the document's business.
struct SlideDrag: Equatable {
    /// The row the pointer picked up. The rest of the selection travels with it
    /// when it is part of one, which the core works out from this id.
    let slideId: String
    /// The row the drop lands after, nil for the gap above the first row.
    let afterId: String?
    /// The level the drop lands at: sideways travel picks it, the gap clamps it.
    let depth: Int
    /// The pointer, in [NavigatorSpace], for the thumbnail riding it.
    let location: CGPoint
    /// From the pointer to the middle of the thumbnail it went down on, so the
    /// thumbnail rides from where it was grabbed instead of jumping to the pointer.
    let grab: CGSize
}

/// What the list draws: the rows, and during a drag the gap standing in for
/// the drop.
private enum NavigatorItem: Identifiable {
    case row(OutlineRow)
    case gap(depth: Int)

    var id: String {
        switch self {
        case .row(let row): return row.slideId
        case .gap: return "navigator-gap"
        }
    }
}

/// Where a selected row sits in its run of selected rows: the tint behind a run
/// is one block, rounded only where the run ends.
struct SelectionRun: Equatable {
    let joinsAbove: Bool
    let joinsBelow: Bool
}

private enum NavigatorMetrics {
    static let indent: CGFloat = 12
    static let gutter: CGFloat = 15
    static let gutterGap: CGFloat = 4
    static let rowPadding: CGFloat = 6
    static let listPadding: CGFloat = 8
    static let radius: CGFloat = 10

    static func thumbWidth(depth: Int) -> CGFloat {
        Layout.thumbnail - indent * CGFloat(min(depth, 3))
    }

    /// The thumbnail's leading edge at [depth], from the list's own edge: where
    /// the drop bar stands.
    static func thumbLeading(depth: Int) -> CGFloat {
        listPadding + rowPadding + indent * CGFloat(depth) + gutter + gutterGap
    }
}

// MARK: - Navigator

extension EditorView {
    var navigatorCard: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                // The window's own buttons sit here, moved into place by
                // TrafficLights; this reserves the room they take.
                Spacer(minLength: Layout.trafficLightsInset + Layout.trafficLightsWidth)
                sidebarToggle
            }
            .padding(.trailing, Layout.edge)
            .frame(height: Layout.header)

            navigator
        }
        .frame(maxHeight: .infinity)
        .glassCard(palette: palette)
        .background { NavigatorKeys(host: host) }
    }

    var sidebarToggle: some View {
        Button { host.toggleSidebar() } label: {
            Image(systemName: "sidebar.left")
                .font(.system(size: 15, weight: .regular))
                .foregroundStyle(palette.icon)
                .frame(width: 26, height: 24)
                .contentShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
        .help("Hide sidebar")
    }

    var navigator: some View {
        // Reading generation is what subscribes the rows to store changes.
        let _ = model.generation
        let selected = host.selectedSlideIndex()
        let rows = host.outline()
        let layoutMode = host.isEditingLayouts()
        return VStack(spacing: 0) {
            if layoutMode { layoutsHeader }
            navigatorRows(rows, selected: selected, layoutMode: layoutMode)
        }
    }

    /// Says what the rows are, since in layout mode they are not slides, and
    /// carries the way out. Only in layout mode: with slides showing there is
    /// nothing to say and nothing to be done.
    var layoutsHeader: some View {
        HStack(spacing: 8) {
            Text("SLIDE LAYOUTS")
                .font(.system(size: 11, weight: .semibold))
                .tracking(1.2)
                .foregroundStyle(palette.subtle)
            Spacer(minLength: 0)
            Button { host.exitSlideLayouts() } label: {
                Text("Done")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(palette.ctrlText)
                    .padding(.horizontal, 10)
                    .frame(height: 20)
                    .background(palette.ctrl, in: Capsule())
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .help("Back to the slides")
        }
        .padding(.horizontal, 14)
        .padding(.bottom, 8)
    }

    func navigatorRows(_ rows: [OutlineRow], selected: Int32, layoutMode: Bool) -> some View {
        // Off the list while they travel, so the rows left close up and the gap
        // is the only place the drop can be read from.
        let dragged = draggedRows(in: rows)
        let staying = rows.filter { !dragged.contains($0.slideId) }
        let items = navigatorItems(staying)
        let focused = host.navigatorFocused()
        let selectedId = rows.first { $0.slideIndex == selected }?.slideId
        return ScrollViewReader { scroller in
            ScrollView {
                // Rows are identified by their slide, so collapsing a group reads
                // as those rows leaving and everything after sliding up, and a
                // reorder as one row moving, not as every row changing in place.
                // No spacing: selected neighbours have to meet to read as a run.
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                        switch item {
                        case .gap(let depth):
                            dropGap(depth: depth, dragged: slideDrag?.slideId, rows: rows)
                        case .row(let row):
                            NavigatorRow(
                                row: row,
                                primary: row.slideIndex == selected,
                                focused: focused,
                                run: selectionRun(at: index, in: items),
                                layoutMode: layoutMode,
                                palette: palette,
                                host: host
                            )
                            .transition(.move(edge: .top).combined(with: .opacity))
                        }
                    }
                }
                .padding(EdgeInsets(
                    top: 2,
                    leading: NavigatorMetrics.listPadding,
                    bottom: 16,
                    trailing: NavigatorMetrics.listPadding
                ))
                .frame(maxWidth: .infinity, alignment: .leading)
                .coordinateSpace(name: NavigatorSpace.name)
                .onPreferenceChange(RowFrames.self) { frames in rowFrames = frames }
                .overlay(alignment: .topLeading) { dragPreview(rows: rows) }
                .animation(.easeInOut(duration: 0.22), value: items.map(\.id))
                // On the list rather than on a row: the dragged rows leave the
                // list, and a gesture cannot outlive the view it hangs off. With
                // enough slop that a click is still a click.
                .gesture(
                    DragGesture(minimumDistance: 6, coordinateSpace: .named(NavigatorSpace.name))
                        .onChanged { value in dragSlide(value, rows: rows) }
                        .onEnded { _ in dropSlide() }
                )
            }
            .scrollContentBackground(.hidden)
            // The arrow keys can walk the selection out of sight.
            .onChange(of: selectedId) { _, id in
                guard let id else { return }
                withAnimation(.easeInOut(duration: 0.15)) { scroller.scrollTo(id) }
            }
        }
    }

    private func navigatorItems(_ staying: [OutlineRow]) -> [NavigatorItem] {
        var items = staying.map(NavigatorItem.row)
        guard let drag = slideDrag else { return items }
        let at = drag.afterId.flatMap { id in staying.firstIndex { $0.slideId == id } }.map { $0 + 1 } ?? 0
        items.insert(.gap(depth: drag.depth), at: at)
        return items
    }

    private func selectionRun(at index: Int, in items: [NavigatorItem]) -> SelectionRun {
        func selected(_ at: Int) -> Bool {
            guard items.indices.contains(at), case .row(let row) = items[at] else { return false }
            return row.selected
        }
        return SelectionRun(joinsAbove: selected(index - 1), joinsBelow: selected(index + 1))
    }

    /// What travels with a drag: the picked-up row, the rest of the selection
    /// when it is part of one, and every row nested under any of them, since a
    /// parent drags as a group (`moveSlide` lands it as one).
    func draggedRows(in rows: [OutlineRow]) -> Set<String> {
        guard let drag = slideDrag else { return [] }
        return draggedRows(in: rows, from: drag.slideId)
    }

    func draggedRows(in rows: [OutlineRow], from slideId: String) -> Set<String> {
        guard let picked = rows.first(where: { $0.slideId == slideId }) else { return [] }
        var ids: Set<String> = []
        var under: Int32?
        for row in rows {
            if let depth = under, row.depth > depth {
                ids.insert(row.slideId)
                continue
            }
            let travels = row.slideId == slideId || (picked.selected && row.selected)
            under = travels ? row.depth : nil
            if travels { ids.insert(row.slideId) }
        }
        return ids
    }

    /// The gap the drag is over: after the last row whose midpoint the pointer
    /// has passed, the front of the deck when it has passed none. Sideways travel
    /// asks for a level, a step per indent, and the document says how much of
    /// that the gap allows.
    func dragSlide(_ value: DragGesture.Value, rows: [OutlineRow]) {
        let slideId: String
        let grab: CGSize
        if let drag = slideDrag {
            slideId = drag.slideId
            grab = drag.grab
        } else {
            // The row under the pointer when it went down. None is a drag that
            // started on empty list, which moves nothing.
            guard let picked = rows.first(where: { rowFrames[$0.slideId]?.contains(value.startLocation) == true })
            else { return }
            slideId = picked.slideId
            let frame = rowFrames[slideId] ?? .zero
            let width = NavigatorMetrics.thumbWidth(depth: Int(picked.depth))
            grab = CGSize(
                width: frame.maxX - NavigatorMetrics.rowPadding - width / 2 - value.startLocation.x,
                height: frame.midY - value.startLocation.y
            )
            // Keynote selects on the way down, so what is dragged is always the
            // selection: a row outside it becomes it, one inside brings the rest.
            if !picked.selected { host.selectSlide(index: picked.slideIndex) }
        }
        guard let picked = rows.first(where: { $0.slideId == slideId }) else { return }

        let dragged = draggedRows(in: rows, from: slideId)
        var afterId: String?
        for row in rows where !dragged.contains(row.slideId) {
            guard let frame = rowFrames[row.slideId] else { continue }
            if frame.midY >= value.location.y { break }
            afterId = row.slideId
        }

        let steps = Int32((value.translation.width / NavigatorMetrics.indent).rounded())
        let depth = host.landingDepth(id: slideId, afterId: afterId, depth: max(0, picked.depth + steps))
        let drag = SlideDrag(
            slideId: slideId, afterId: afterId, depth: Int(depth), location: value.location, grab: grab
        )
        if slideDrag == nil || slideDrag?.afterId != afterId || slideDrag?.depth != Int(depth) {
            withAnimation(.easeInOut(duration: 0.18)) { slideDrag = drag }
        } else {
            slideDrag = drag
        }
    }

    /// The core no-ops a drop back into the row's own gap, so every drop is sent.
    /// The gap closing and the rows arriving settle under one animation.
    func dropSlide() {
        guard let drag = slideDrag else { return }
        withAnimation(.easeInOut(duration: 0.22)) {
            slideDrag = nil
            host.moveSlideTo(id: drag.slideId, afterId: drag.afterId, depth: Int32(drag.depth))
        }
    }

    /// Keynote's drop marker: room for the row, and an accent bar standing at
    /// the leading edge the thumbnail will take, so the level reads before the
    /// drop does.
    private func dropGap(depth: Int, dragged: String?, rows: [OutlineRow]) -> some View {
        let width = NavigatorMetrics.thumbWidth(depth: depth)
        return HStack(spacing: 0) {
            Spacer()
                .frame(width: NavigatorMetrics.thumbLeading(depth: depth) - NavigatorMetrics.listPadding - 4)
            Capsule()
                .fill(palette.accent)
                .frame(width: 2)
            Spacer(minLength: 0)
        }
        .frame(height: width * 9 / 16 + 10)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The thumbnail riding the pointer, with a count when it speaks for more
    /// rows than the one.
    @ViewBuilder private func dragPreview(rows: [OutlineRow]) -> some View {
        if let drag = slideDrag, let row = rows.first(where: { $0.slideId == drag.slideId }) {
            let width = NavigatorMetrics.thumbWidth(depth: Int(row.depth))
            let count = rows.filter { row.selected && $0.selected }.count
            let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
            Group {
                if let image = host.thumbnail(index: row.slideIndex, width: Int32(width)) {
                    Image(nsImage: image).resizable().aspectRatio(contentMode: .fit)
                } else {
                    shape.fill(Color.black.opacity(0.2))
                }
            }
            .frame(width: width, height: width * 9 / 16)
            .clipShape(shape)
            .shadow(color: .black.opacity(0.35), radius: 8, y: 4)
            .overlay(alignment: .topTrailing) {
                if count > 1 {
                    Text("\(count)")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 6)
                        .frame(minWidth: 18, minHeight: 18)
                        .background(Color.red, in: Capsule())
                        .offset(x: 6, y: -6)
                }
            }
            .position(x: drag.location.x + drag.grab.width, y: drag.location.y + drag.grab.height)
            .allowsHitTesting(false)
        }
    }

    /// Keynote's row: the selection runs the navigator's full width whatever the
    /// level, and the level shows inside it: a leading gutter that steps in, then
    /// a thumbnail that gives the same step up, so every thumbnail ends on the
    /// same trailing edge. The gutter runs the thumbnail's height and carries the
    /// disclosure chevron centred in it plus the slide number tucked to its
    /// bottom.
    private struct NavigatorRow: View {
        let row: OutlineRow
        /// The slide on the canvas. Alongside it, [OutlineRow.selected] rows are
        /// the rest of the selection.
        let primary: Bool
        /// The navigator holds the keyboard focus. With the window active that
        /// is what makes the selection accent rather than grey.
        let focused: Bool
        let run: SelectionRun
        /// This row is a layout, so it carries its name and the layout verbs.
        let layoutMode: Bool
        let palette: Palette
        let host: EditorHost

        @Environment(\.controlActiveState) private var activeState
        @State private var chevronHovering = false
        /// The rename sheet, and what is being typed into it. Sheet state, not
        /// document state: the name only becomes the document's on Rename.
        @State private var renaming = false
        @State private var renameText = ""

        private var thumbWidth: CGFloat { NavigatorMetrics.thumbWidth(depth: Int(row.depth)) }
        private var emphasized: Bool { focused && activeState == .key }
        /// On the accent fill, where the row's own ink has to turn white.
        private var onAccent: Bool { primary && emphasized }

        var body: some View {
            HStack(spacing: NavigatorMetrics.gutterGap) {
                gutter
                // Skipped is a slide out of the presentation, not out of the
                // deck: the gutter keeps its width, the slide reads back.
                VStack(alignment: .leading, spacing: 3) {
                    thumbnail.opacity(row.skipped ? 0.4 : 1)
                    // A layout is picked by name, so the row says its name. A
                    // slide's title is its own content, and the thumbnail
                    // already shows it.
                    if layoutMode {
                        Text(row.title)
                            .font(.system(size: 11))
                            .foregroundStyle(onAccent ? Color.white : palette.dim)
                            .lineLimit(1)
                            .frame(width: thumbWidth, alignment: .leading)
                    }
                }
            }
            .padding(.vertical, 5)
            .padding(.leading, NavigatorMetrics.rowPadding + NavigatorMetrics.indent * CGFloat(row.depth))
            .padding(.trailing, NavigatorMetrics.rowPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background { selection }
            .contentShape(Rectangle())
            .onTapGesture { select() }
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: RowFrames.self,
                        value: [row.slideId: proxy.frame(in: .named(NavigatorSpace.name))]
                    )
                }
            }
            .contextMenu {
                // No selecting the row first, unlike the canvas menu: every entry
                // carries this row's id, and the ones that end in a selection
                // (new, duplicate, delete, cut, paste) settle it in the core,
                // which also widens a verb on a selected row to the selection.
                if layoutMode {
                    MenuEntries(entries: layoutEntries(
                        host,
                        index: row.slideIndex,
                        layoutId: row.slideId,
                        onRename: {
                            renameText = row.title
                            renaming = true
                        }
                    ))
                } else {
                    MenuEntries(entries: slideEntries(host, slideId: row.slideId, includePaste: true))
                }
            }
            // Renaming is the one layout verb with something to ask, so it is
            // the one that takes a sheet. The select goes first for the same
            // reason `pasteAfterSlide` sends one: the rename acts on the
            // selection, and the events flow serializes the two.
            .alert("Rename Layout", isPresented: $renaming) {
                TextField("Name", text: $renameText)
                Button("Rename") {
                    host.selectSlide(index: row.slideIndex)
                    host.renameSelectedSlide(title: renameText)
                }
                Button("Cancel", role: .cancel) {}
            }
        }

        /// The click, by the modifiers it came with: shift takes the range from
        /// the slide on the canvas, command takes this row in or out, and a plain
        /// one makes it the selection.
        private func select() {
            let modifiers = NSEvent.modifierFlags.intersection(.deviceIndependentFlagsMask)
            if modifiers.contains(.shift) {
                host.extendSlideSelection(id: row.slideId)
            } else if modifiers.contains(.command) {
                host.toggleSlideSelection(id: row.slideId)
            } else {
                host.selectSlide(index: row.slideIndex)
            }
        }

        /// Sized off the thumbnail, not the row, so the number sits on the
        /// thumbnail's lower edge and the chevron on its middle.
        private var gutter: some View {
            ZStack {
                if row.hasChildren { chevron }
            }
            .frame(width: NavigatorMetrics.gutter, height: thumbWidth * 9 / 16)
            .overlay(alignment: .bottomTrailing) {
                // The presentation number, which a skipped slide has none of.
                Text(row.numberLabel)
                    .font(.system(size: 11, weight: onAccent ? .semibold : .regular))
                    .foregroundStyle(onAccent ? Color.white : palette.faint)
                    .padding(.bottom, 1)
            }
        }

        private var chevron: some View {
            Button { host.toggleCollapsed(index: row.slideIndex) } label: {
                ChevronGlyph()
                    .stroke(
                        onAccent ? Color.white : palette.icon,
                        style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                    )
                    .frame(width: 9, height: 9)
                    .rotationEffect(.degrees(row.collapsed ? 0 : 90))
                    .animation(.easeInOut(duration: 0.14), value: row.collapsed)
                    .frame(width: 16, height: 20)
                    .background {
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .fill(chevronHovering ? palette.hover2 : .clear)
                    }
                    .contentShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
            }
            .buttonStyle(.plain)
            .onHover { chevronHovering = $0 }
            .help(row.collapsed ? "Expand" : "Collapse")
        }

        /// Keynote's selection. With the navigator focused: the slide on the
        /// canvas in solid accent, the rest of the selection in an accent tint
        /// that runs unbroken behind neighbouring rows. Without: one grey for all
        /// of it, since nothing typed would land here. No hover state, Keynote's
        /// rows have none.
        @ViewBuilder private var selection: some View {
            if row.selected || primary {
                let radius = NavigatorMetrics.radius
                let run = UnevenRoundedRectangle(
                    topLeadingRadius: self.run.joinsAbove ? 0 : radius,
                    bottomLeadingRadius: self.run.joinsBelow ? 0 : radius,
                    bottomTrailingRadius: self.run.joinsBelow ? 0 : radius,
                    topTrailingRadius: self.run.joinsAbove ? 0 : radius,
                    style: .continuous
                )
                if emphasized {
                    run.fill(palette.accent.opacity(0.25))
                    if primary {
                        RoundedRectangle(cornerRadius: radius, style: .continuous).fill(palette.accent)
                    }
                } else {
                    run.fill(Color.primary.opacity(0.12))
                }
            }
        }

        /// Rendered by the shared Compose renderer, so a thumbnail is the slide.
        /// A soft shadow lifts it off the row the way Keynote's sit, and no ring:
        /// the fill behind the row alone marks selection.
        @ViewBuilder private var thumbnail: some View {
            let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
            if let image = host.thumbnail(index: row.slideIndex, width: Int32(thumbWidth)) {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: thumbWidth)
                    .clipShape(shape)
                    .overlay { shape.inset(by: 0.5).stroke(palette.thumbEdge, lineWidth: 1) }
                    .shadow(color: .black.opacity(0.18), radius: 1.5, y: 1)
            } else {
                shape
                    .fill(Color.black.opacity(0.2))
                    .frame(width: thumbWidth, height: thumbWidth * 9 / 16)
            }
        }
    }

    /// The disclosure glyph: a stroked chevron on a 9x9 box, pointing right.
    /// A path, not a text character, so it reads as a control at any size.
    private struct ChevronGlyph: Shape {
        func path(in rect: CGRect) -> Path {
            let unit = min(rect.width, rect.height) / 9
            var path = Path()
            path.move(to: CGPoint(x: rect.minX + 2.6 * unit, y: rect.minY + 1.1 * unit))
            path.addLine(to: CGPoint(x: rect.minX + 6.4 * unit, y: rect.minY + 4.5 * unit))
            path.addLine(to: CGPoint(x: rect.minX + 2.6 * unit, y: rect.minY + 7.9 * unit))
            return path
        }
    }
}

// MARK: - Keyboard

/// The navigator's keys, Keynote's: arrows walk the rows (shift extends), left
/// and right fold and open, tab and shift-tab change the level, return adds a
/// slide, command-A takes them all.
///
/// A window-level monitor rather than a focusable view: the Compose canvas holds
/// first responder whichever pane the editor says is focused, so the pane is
/// asked of the editor and the keys are taken ahead of the responder chain, only
/// while the navigator has them and nothing is being typed into.
private struct NavigatorKeys: NSViewRepresentable {
    let host: EditorHost

    func makeNSView(context: Context) -> MonitorView { MonitorView() }

    func updateNSView(_ view: MonitorView, context: Context) { view.host = host }

    static func dismantleNSView(_ view: MonitorView, coordinator: ()) { view.stop() }

    final class MonitorView: NSView {
        var host: EditorHost?
        private var monitor: Any?

        override func viewDidMoveToWindow() {
            super.viewDidMoveToWindow()
            stop()
            guard window != nil else { return }
            monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
                self?.handle(event) == true ? nil : event
            }
        }

        func stop() {
            if let monitor { NSEvent.removeMonitor(monitor) }
            monitor = nil
        }

        private func handle(_ event: NSEvent) -> Bool {
            guard let host, let window, event.window === window, window.attachedSheet == nil else { return false }
            guard host.navigatorFocused(), !(window.firstResponder is NSText) else { return false }

            let modifiers = event.modifierFlags.intersection([.shift, .command, .option, .control])
            let shift = modifiers == .shift
            guard modifiers.isEmpty || shift || modifiers == .command else { return false }

            switch (event.keyCode, modifiers) {
            case (126, []), (126, .shift): host.stepSlideSelection(delta: -1, extend: shift)
            case (125, []), (125, .shift): host.stepSlideSelection(delta: 1, extend: shift)
            case (123, []): host.setSelectedSlidesCollapsed(collapsed: true)
            case (124, []): host.setSelectedSlidesCollapsed(collapsed: false)
            case (48, []): host.indentSlides(delta: 1)
            case (48, .shift): host.indentSlides(delta: -1)
            case (36, []): host.addSlideAfterSelection()
            case (0, .command): host.selectAllSlides()
            default: return false
            }
            return true
        }
    }
}
