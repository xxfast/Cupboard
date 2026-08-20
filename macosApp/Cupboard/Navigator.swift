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

/// A navigator row on the move, and the gap it is over. SwiftUI state, unlike
/// the Compose canvas's gestures: this tree redraws off `@State` the way it is
/// built to, and nothing but the drop is the document's business.
struct SlideDrag: Equatable {
    let slideId: String
    /// The row the drop lands after, nil for the gap above the first row.
    let afterId: String?
    /// Over `afterId`'s own row rather than the gap under it: the drop nests.
    let nest: Bool
    /// Where the drop line draws, in [NavigatorSpace]. Unused when nesting.
    let lineY: CGFloat
    /// How far the pointer has carried the row: it travels with the cursor.
    let translationY: CGFloat
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
        let dragged = draggedRun(in: rows)
        return ScrollView {
            // Rows are identified by their slide, so collapsing a group reads as
            // those rows leaving and everything after sliding up, and a reorder
            // as one row moving, not as every row changing in place.
            LazyVStack(alignment: .leading, spacing: 2) {
                ForEach(rows, id: \.slideId) { row in
                    NavigatorRow(
                        row: row,
                        selected: row.slideIndex == selected,
                        dragging: dragged.contains(row.slideId),
                        nestTarget: slideDrag?.nest == true && slideDrag?.afterId == row.slideId,
                        liftY: dragged.contains(row.slideId) ? slideDrag?.translationY ?? 0 : 0,
                        palette: palette,
                        host: host,
                        onDrag: { point, translation in dragSlide(row, to: point, by: translation, rows: rows) },
                        onDrop: dropSlide
                    )
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .padding(EdgeInsets(top: 2, leading: 8, bottom: 16, trailing: 8))
            .frame(maxWidth: .infinity, alignment: .leading)
            .coordinateSpace(name: NavigatorSpace.name)
            .onPreferenceChange(RowFrames.self) { frames in rowFrames = frames }
            .overlay(alignment: .topLeading) { dropLine }
            .animation(.easeInOut(duration: 0.22), value: rows.map(\.slideId))
        }
        .scrollContentBackground(.hidden)
    }

    /// The dragged row and every row nested under it: a parent drags as a group
    /// (`moveSlide` lands it as one), so the whole run steps back together.
    func draggedRun(in rows: [OutlineRow]) -> Set<String> {
        guard let drag = slideDrag else { return [] }
        return draggedRun(in: rows, from: drag.slideId)
    }

    func draggedRun(in rows: [OutlineRow], from slideId: String) -> Set<String> {
        guard let start = rows.firstIndex(where: { $0.slideId == slideId }) else { return [] }
        let depth = rows[start].depth
        var ids: Set<String> = [slideId]
        for row in rows[(start + 1)...] {
            if row.depth <= depth { break }
            ids.insert(row.slideId)
        }
        return ids
    }

    /// The spot the drag is over. The middle half of a row that is not itself
    /// on the move nests the drop under that row. Otherwise a gap: above the
    /// first row's midpoint is the front of the deck, else after the last row
    /// whose midpoint the pointer has passed.
    func dragSlide(_ row: OutlineRow, to point: CGPoint, by translation: CGSize, rows: [OutlineRow]) {
        let placed: [(row: OutlineRow, frame: CGRect)] = rows.compactMap { candidate in
            rowFrames[candidate.slideId].map { (candidate, $0) }
        }
        guard let first = placed.first else { return }
        let dragged = draggedRun(in: rows, from: row.slideId)

        var afterId: String?
        // Half the 2pt row gap above the first row, so the line sits in the gap
        // rather than on a row's edge.
        var lineY: CGFloat = first.frame.minY - 1
        for (candidate, frame) in placed {
            let quarter = frame.height / 4
            if !dragged.contains(candidate.slideId),
               (frame.minY + quarter...frame.maxY - quarter).contains(point.y) {
                slideDrag = SlideDrag(
                    slideId: row.slideId, afterId: candidate.slideId, nest: true, lineY: 0,
                    translationY: translation.height
                )
                return
            }
            if frame.midY >= point.y { break }
            afterId = candidate.slideId
            lineY = frame.maxY + 1
        }
        slideDrag = SlideDrag(
            slideId: row.slideId, afterId: afterId, nest: false, lineY: lineY,
            translationY: translation.height
        )
    }

    /// The core no-ops a drop back into the row's own gap, so every drop is sent.
    /// The lift and the reorder settle under one animation, so the row glides
    /// from under the pointer straight into its new slot.
    func dropSlide() {
        guard let drag = slideDrag else { return }
        withAnimation(.easeInOut(duration: 0.22)) {
            slideDrag = nil
            host.moveSlide(id: drag.slideId, afterId: drag.afterId, nest: drag.nest)
        }
    }

    @ViewBuilder var dropLine: some View {
        if let drag = slideDrag, !drag.nest {
            palette.accent
                .frame(height: 2)
                .padding(.horizontal, 8)
                .offset(y: drag.lineY - 1)
        }
    }

    /// Keynote's row: a fixed leading gutter, then the thumbnail, both inside a
    /// selection capsule that hugs them. The gutter runs the thumbnail's height
    /// and carries the disclosure chevron centred in it plus the slide number
    /// tucked to its bottom, so every thumbnail starts at the same offset from
    /// its own row whether or not the slide has children. Depth indents the
    /// whole capsule and takes the same step off the thumbnail's width.
    private struct NavigatorRow: View {
        let row: OutlineRow
        let selected: Bool
        /// This row is the one being dragged, so it steps back while it travels.
        let dragging: Bool
        /// The drag is over this row's body: dropping nests under it.
        let nestTarget: Bool
        /// How far this row has been carried by the drag, 0 when it hasn't.
        let liftY: CGFloat
        let palette: Palette
        let host: EditorHost
        /// A drag sample, in [NavigatorSpace] plus its travel, and the release
        /// that drops it.
        let onDrag: (CGPoint, CGSize) -> Void
        let onDrop: () -> Void

        @State private var hovering = false
        @State private var chevronHovering = false

        private var thumbWidth: CGFloat { Layout.thumbnail - 12 * CGFloat(min(row.depth, 3)) }

        var body: some View {
            HStack(spacing: 4) {
                gutter
                // Skipped is a slide out of the presentation, not out of the
                // deck: the gutter keeps its width, the slide reads back.
                thumbnail.opacity(row.skipped ? 0.4 : 1)
            }
            .padding(.vertical, 5)
            .padding(.horizontal, 6)
            .background { capsule }
            .overlay {
                if nestTarget {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .inset(by: 1)
                        .stroke(palette.accent, lineWidth: 2)
                }
            }
            // Lifted: a touch translucent to show the rows it passes over, and
            // above them while it travels.
            .opacity(dragging ? 0.85 : 1)
            .offset(y: liftY)
            .zIndex(dragging ? 1 : 0)
            .contentShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .onTapGesture { host.selectSlide(index: row.slideIndex) }
            // Enough slop that a click is still a click: the drag only takes
            // over once the pointer has actually travelled.
            .gesture(
                DragGesture(minimumDistance: 6, coordinateSpace: .named(NavigatorSpace.name))
                    .onChanged { value in onDrag(value.location, value.translation) }
                    .onEnded { _ in onDrop() }
            )
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
                // (new, duplicate, delete, cut, paste) settle it in the core.
                MenuEntries(entries: slideEntries(host, slideId: row.slideId, includePaste: true))
            }
            .onHover { hovering = $0 }
            .padding(.leading, CGFloat(row.depth) * 12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }

        /// Sized off the thumbnail, not the row, so the number sits on the
        /// thumbnail's lower edge and the chevron on its middle.
        private var gutter: some View {
            ZStack {
                if row.hasChildren { chevron }
            }
            .frame(width: 15, height: thumbWidth * 9 / 16)
            .overlay(alignment: .bottomTrailing) {
                // The presentation number, which a skipped slide has none of.
                Text(row.numberLabel)
                    .font(.system(size: 11))
                    .foregroundStyle(palette.faint)
                    .padding(.bottom, 1)
            }
        }

        private var chevron: some View {
            Button { host.toggleCollapsed(index: row.slideIndex) } label: {
                ChevronGlyph()
                    .stroke(
                        palette.icon,
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

        @ViewBuilder private var capsule: some View {
            let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
            if selected {
                shape
                    .fill(palette.selection)
                    .overlay {
                        shape
                            .inset(by: 0.5)
                            .stroke(palette.selectionEdge, lineWidth: 1)
                            .mask(
                                LinearGradient(
                                    colors: [.white, .clear],
                                    startPoint: .top,
                                    endPoint: .center
                                )
                            )
                    }
            } else if hovering {
                shape.fill(palette.hover)
            }
        }

        /// Rendered by the shared Compose renderer, so a thumbnail is the slide.
        /// No accent ring and no shadow: the capsule alone marks selection.
        @ViewBuilder private var thumbnail: some View {
            let shape = RoundedRectangle(cornerRadius: 4, style: .continuous)
            if let image = host.thumbnail(index: row.slideIndex, width: Int32(thumbWidth)) {
                Image(nsImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: thumbWidth)
                    .clipShape(shape)
                    .overlay { shape.inset(by: 0.5).stroke(palette.thumbEdge, lineWidth: 1) }
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
