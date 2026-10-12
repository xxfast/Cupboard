import SwiftUI
import AppKit
import CupboardCanvas

private enum ObjectListMetrics {
    static let row: CGFloat = 34
    static let indent: CGFloat = 16
    static let listPadding: CGFloat = 8
    static let rowPadding: CGFloat = 6
    static let radius: CGFloat = 6
}

enum ObjectListSpace {
    static let name = "objectList"
}

/// Where each Object List row sits in [ObjectListSpace], by element id: the
/// navigator's [RowFrames], for the drop maths of a row drag.
struct ObjectRowFrames: PreferenceKey {
    static let defaultValue: [String: CGRect] = [:]

    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { _, latest in latest }
    }
}

/// A place a dragged row could land: immediately before [beforeId]'s row, nil
/// being after the last of its siblings, at [y] in [ObjectListSpace].
private struct ObjectGap {
    let beforeId: String?
    let y: CGFloat
}

// MARK: - Object List

extension EditorView {
    /// Keynote's Object List: the selected slide's elements front to back, in a
    /// card of its own beside the navigator's, with the search pinned under it.
    var objectListCard: some View {
        VStack(spacing: 0) {
            objectList
            palette.divider.frame(height: 1)
            ObjectListSearch(filter: host.objectListFilter(), palette: palette, host: host)
        }
        .frame(maxHeight: .infinity)
        .glassCard(palette: palette)
    }

    var objectList: some View {
        // Reading generation is what subscribes the rows to store changes.
        let _ = model.generation
        let rows = host.objectRows()
        let filtering = !host.objectListFilter().isEmpty
        let drag = host.objectDrag()
        return ScrollView {
            // Identified by element, so a group opening reads as its children
            // arriving rather than every row below changing in place. Eager,
            // like the navigator's: a lazy stack ignores zIndex, and the row
            // on the move has to ride over the ones after it. A slide's objects
            // are few enough that laziness buys nothing.
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    let dragged = drag?.id == row.id
                    ObjectRowView(row: row, lifted: dragged, palette: palette, host: host)
                        .overlay(alignment: .top) {
                            if index > 0 && !dragged {
                                palette.divider
                                    .frame(height: 1)
                                    .padding(.horizontal, ObjectListMetrics.rowPadding)
                            }
                        }
                        .background {
                            if dragged {
                                RoundedRectangle(cornerRadius: ObjectListMetrics.radius, style: .continuous)
                                    .fill(palette.panel)
                                    .shadow(color: .black.opacity(0.3), radius: 6, y: 3)
                            }
                        }
                        .offset(y: dragged ? CGFloat(drag?.translationY ?? 0) : 0)
                        // Outside the offset, which a frame read inside it
                        // follows: this is the row's resting place, so the
                        // gaps hold still while it travels.
                        .background {
                            GeometryReader { proxy in
                                Color.clear.preference(
                                    key: ObjectRowFrames.self,
                                    value: [row.id: proxy.frame(in: .named(ObjectListSpace.name))]
                                )
                            }
                        }
                        .zIndex(dragged ? 1 : 0)
                }
            }
            .padding(ObjectListMetrics.listPadding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .coordinateSpace(name: ObjectListSpace.name)
            .onPreferenceChange(ObjectRowFrames.self) { frames in objectRowFrames = frames }
            .overlay(alignment: .topLeading) { objectInsertionLine(drag, rows: rows) }
            .animation(.easeInOut(duration: 0.22), value: rows.map(\.id))
            // On the list rather than on a row, like the navigator's, with the
            // same slop so a click is still a click (and a double click a rename).
            .gesture(
                DragGesture(minimumDistance: 6, coordinateSpace: .named(ObjectListSpace.name))
                    .onChanged { value in dragObject(value, rows: rows) }
                    .onEnded { value in dropObject(value, rows: rows) }
            )
        }
        .scrollContentBackground(.hidden)
        .overlay {
            if rows.isEmpty {
                Text(filtering ? "No Matches" : "No Objects")
                    .font(.system(size: 12))
                    .foregroundStyle(palette.faint)
            }
        }
    }

    /// The row under the pointer when it went down, and the nearest gap it could
    /// land in from where the pointer is now. Nil while filtering, where rows
    /// from different groups sit side by side with no parent to tell them
    /// apart, for a drag that started off every row, and for a locked one,
    /// which the core would refuse to move anyway.
    private func objectDropTarget(
        _ value: DragGesture.Value,
        rows: [ObjectListRow]
    ) -> (row: ObjectListRow, beforeId: String?)? {
        guard host.objectListFilter().isEmpty else { return nil }
        let parents = objectParents(rows)
        guard let index = rows.firstIndex(where: {
            objectRowFrames[$0.id]?.contains(value.startLocation) == true
        }) else { return nil }
        let row = rows[index]
        let parentLocked = parents[index].flatMap { id in rows.first { $0.id == id } }?.locked == true
        guard !row.locked && !parentLocked else { return nil }
        let pointer = value.location.y
        let nearest = objectGaps(for: index, in: rows, parents: parents)
            .min { abs($0.y - pointer) < abs($1.y - pointer) }
        guard let nearest else { return nil }
        return (row, nearest.beforeId)
    }

    /// The group each row sits in, nil at the top level. Rows only nest one
    /// level, so it is the nearest top-level row above.
    private func objectParents(_ rows: [ObjectListRow]) -> [String?] {
        var group: String?
        return rows.map { row in
            if row.depth == 0 {
                group = row.id
                return nil
            }
            return group
        }
    }

    /// Where the row at [index] could land: before each of its siblings, and
    /// after the last of them, below whatever that one holds open. A drag never
    /// takes an element in or out of a group, so nothing else is a candidate.
    private func objectGaps(for index: Int, in rows: [ObjectListRow], parents: [String?]) -> [ObjectGap] {
        let picked = rows[index]
        let siblings = rows.indices.filter {
            rows[$0].depth == picked.depth && parents[$0] == parents[index]
        }
        var gaps = siblings.compactMap { at in
            objectRowFrames[rows[at].id].map { ObjectGap(beforeId: rows[at].id, y: $0.minY) }
        }
        guard let last = siblings.last else { return gaps }
        let end = rows[(last + 1)...].firstIndex { $0.depth <= rows[last].depth } ?? rows.endIndex
        if let bottom = objectRowFrames[rows[end - 1].id] {
            gaps.append(ObjectGap(beforeId: nil, y: bottom.maxY))
        }
        return gaps
    }

    /// Keynote selects on the way down, so the row that travels is the
    /// selection. Every move goes through the core, which hands back what the
    /// list draws.
    func dragObject(_ value: DragGesture.Value, rows: [ObjectListRow]) {
        guard let target = objectDropTarget(value, rows: rows) else { return }
        if !target.row.selected && host.objectDrag() == nil { host.selectObject(id: target.row.id) }
        host.previewObjectDrag(
            id: target.row.id,
            beforeId: target.beforeId,
            translationY: Float(value.translation.height)
        )
    }

    /// Worked out from the gesture rather than read back from `objectDrag()`,
    /// which may not have caught up with a quick flick. The core no-ops a drop
    /// back where the row was, and ends the drag either way.
    func dropObject(_ value: DragGesture.Value, rows: [ObjectListRow]) {
        guard let target = objectDropTarget(value, rows: rows) else {
            host.endObjectDrag()
            return
        }
        host.moveObject(id: target.row.id, beforeId: target.beforeId)
    }

    /// The accent line in the gap the drag would land in, indented to the
    /// dragged row's level.
    @ViewBuilder private func objectInsertionLine(_ drag: ObjectDragState?, rows: [ObjectListRow]) -> some View {
        if let drag,
           let index = rows.firstIndex(where: { $0.id == drag.id }),
           let frame = objectRowFrames[drag.id],
           let gap = objectGaps(for: index, in: rows, parents: objectParents(rows))
               .first(where: { $0.beforeId == drag.beforeId }) {
            let inset = ObjectListMetrics.rowPadding + ObjectListMetrics.indent * CGFloat(rows[index].depth)
            Capsule()
                .fill(palette.accent)
                .frame(width: max(0, frame.width - inset - ObjectListMetrics.rowPadding), height: 2)
                .offset(x: frame.minX + inset, y: gap.y - 1)
                .allowsHitTesting(false)
        }
    }

    /// One element: disclosure for a group, the kind's glyph, the name, and the
    /// lock and eye at the trailing edge. The two buttons stay out until hovered
    /// unless they have something to say, which is a locked or hidden element.
    private struct ObjectRowView: View {
        let row: ObjectListRow
        /// On the move. The pointer holds it by its middle, so the gap it would
        /// land in always sits under it: it rides as a plain card rather than
        /// the accent fill, which would swallow the accent insertion line.
        let lifted: Bool
        let palette: Palette
        let host: EditorHost

        @Environment(\.controlActiveState) private var activeState
        @State private var hovering = false
        /// Inline rename: the field stands in for the label until return,
        /// escape, or a click elsewhere. Field state, not document state: the
        /// name only becomes the document's on commit.
        @State private var renaming = false
        @State private var renameText = ""
        @FocusState private var renameFocused: Bool

        /// On the accent fill, where the row's own ink has to turn white.
        private var onAccent: Bool { row.selected && !lifted && activeState == .key }
        private var ink: Color { onAccent ? .white : palette.text }
        private var iconInk: Color { onAccent ? .white : palette.icon }

        var body: some View {
            HStack(spacing: 6) {
                disclosure
                Image(systemName: Self.symbol(row.kind))
                    .font(.system(size: 12))
                    .foregroundStyle(iconInk)
                    .frame(width: 18)
                    .opacity(row.hidden ? 0.4 : 1)
                label
                    .frame(maxWidth: .infinity, alignment: .leading)
                if row.locked || hovering {
                    trailingButton(
                        row.locked ? "lock" : "lock.open",
                        help: row.locked ? "Unlock" : "Lock"
                    ) {
                        host.setObjectLocked(id: row.id, locked: !row.locked)
                    }
                }
                if row.hidden || hovering {
                    trailingButton(
                        row.hidden ? "eye.slash" : "eye",
                        help: row.hidden ? "Show" : "Hide"
                    ) {
                        host.setObjectHidden(id: row.id, hidden: !row.hidden)
                    }
                }
            }
            .padding(.leading, ObjectListMetrics.rowPadding + ObjectListMetrics.indent * CGFloat(row.depth))
            .padding(.trailing, ObjectListMetrics.rowPadding)
            .frame(height: ObjectListMetrics.row)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background { selection }
            .contentShape(Rectangle())
            .onTapGesture { tap() }
            .onHover { hovering = $0 }
            // Over the row, so it hears a right-click before the buttons do,
            // and lets every other click through to them.
            .overlay {
                RightClickCatcher {
                    host.contextClickObject(id: row.id)
                    popCanvasMenu(host: host, elementId: row.id)
                }
            }
        }

        @ViewBuilder private var label: some View {
            if renaming {
                TextField("", text: $renameText)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12))
                    .foregroundStyle(palette.text)
                    .padding(.horizontal, 4)
                    .frame(height: 20)
                    .background(palette.ctrl, in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                    .focused($renameFocused)
                    .onSubmit { commitRename() }
                    .onExitCommand { renaming = false }
                    .onChange(of: renameFocused) { _, now in if !now { commitRename() } }
                    // The field has to be on screen before it can take focus.
                    .onAppear { DispatchQueue.main.async { renameFocused = true } }
            } else {
                Text(row.name)
                    .font(.system(size: 12))
                    .foregroundStyle(ink)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .opacity(row.hidden ? 0.4 : 1)
            }
        }

        /// The space is kept on rows without one, so every glyph lines up.
        @ViewBuilder private var disclosure: some View {
            if row.isGroup {
                Button { host.toggleObjectExpanded(id: row.id) } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(iconInk)
                        .rotationEffect(.degrees(row.expanded ? 90 : 0))
                        .animation(.easeInOut(duration: 0.14), value: row.expanded)
                        .frame(width: 12, height: 20)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .help(row.expanded ? "Collapse" : "Expand")
            } else {
                Color.clear.frame(width: 12, height: 20)
            }
        }

        private func trailingButton(_ symbol: String, help: String, action: @escaping () -> Void) -> some View {
            Button(action: action) {
                Image(systemName: symbol)
                    .font(.system(size: 11))
                    .foregroundStyle(iconInk)
                    .frame(width: 18, height: 20)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .help(help)
        }

        /// The navigator's fill: accent with the window key, grey without.
        @ViewBuilder private var selection: some View {
            if row.selected && !lifted {
                let shape = RoundedRectangle(cornerRadius: ObjectListMetrics.radius, style: .continuous)
                if activeState == .key {
                    shape.fill(palette.accent)
                } else {
                    shape.fill(Color.primary.opacity(0.12))
                }
            }
        }

        /// The click, by what it came with: a second click renames, command
        /// takes the row in or out of the selection, a plain one makes it the
        /// selection. Off the event rather than a count-2 gesture, which would
        /// hold every single click back for the double-click interval.
        private func tap() {
            guard !renaming else { return }
            let event = NSApp.currentEvent
            let modifiers = (event?.modifierFlags ?? []).intersection(.deviceIndependentFlagsMask)
            if modifiers.contains(.command) {
                host.toggleObjectSelection(id: row.id)
            } else if (event?.clickCount ?? 1) >= 2 {
                renameText = row.name
                renaming = true
            } else {
                host.selectObject(id: row.id)
            }
        }

        /// Empty goes back to the name read off the content. An untouched field
        /// sends nothing: pinning the shown name would stop it following the
        /// content.
        private func commitRename() {
            guard renaming else { return }
            renaming = false
            let typed = renameText.trimmingCharacters(in: .whitespaces)
            guard typed != row.name else { return }
            host.renameObject(id: row.id, name: typed.isEmpty ? nil : typed)
        }

        static func symbol(_ kind: String) -> String {
            switch kind {
            case "Text": return "textformat"
            case "Shape": return "square.on.circle"
            case "Image": return "photo"
            case "Group": return "square.on.square"
            case "Code": return "chevron.left.forwardslash.chevron.right"
            case "Terminal": return "terminal"
            case "Video": return "film"
            case "Audio": return "waveform"
            case "Diagram": return "point.3.connected.trianglepath.dotted"
            case "Equation": return "function"
            case "Gallery": return "photo.on.rectangle"
            default: return "square.dashed"
            }
        }
    }
}

/// The search under the list. Live, every keystroke narrows the rows; local
/// text that follows the core's while not being typed in, like the inspector's
/// string fields, since the core answers a turn of the main queue later.
private struct ObjectListSearch: View {
    let filter: String
    let palette: Palette
    let host: EditorHost

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 11))
                .foregroundStyle(palette.faint)
            TextField("Search", text: $text)
                .textFieldStyle(.plain)
                .font(.system(size: 12))
                .foregroundStyle(palette.ctrlText)
                .focused($focused)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 11))
                        .foregroundStyle(palette.faint)
                }
                .buttonStyle(.plain)
                .help("Clear")
            }
        }
        .padding(.horizontal, 7)
        .frame(height: 24)
        .background(palette.ctrl, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
        .padding(ObjectListMetrics.listPadding)
        .onAppear { text = filter }
        .onChange(of: text) { _, typed in if typed != filter { host.setObjectListFilter(text: typed) } }
        .onChange(of: filter) { _, latest in if !focused { text = latest } }
    }
}

/// Hears a right-click (or a control-click) and nothing else: every other event
/// falls through to the SwiftUI row under it, which is why the hit test asks
/// what the event is rather than where it landed.
private struct RightClickCatcher: NSViewRepresentable {
    let action: () -> Void

    func makeNSView(context: Context) -> CatcherView { CatcherView() }

    func updateNSView(_ view: CatcherView, context: Context) { view.action = action }

    final class CatcherView: NSView {
        var action: (() -> Void)?

        override func hitTest(_ point: NSPoint) -> NSView? {
            guard let event = NSApp.currentEvent else { return nil }
            let control = event.type == .leftMouseDown && event.modifierFlags.contains(.control)
            guard event.type == .rightMouseDown || control else { return nil }
            return super.hitTest(point)
        }

        override func rightMouseDown(with event: NSEvent) { action?() }

        override func mouseDown(with event: NSEvent) { action?() }
    }
}
