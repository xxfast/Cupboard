import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Animate segments

/// The Animate tab's body: the primary element's builds of one kind, or the
/// slide's transition when nothing is selected, with the build order a press
/// away at the foot of the panel.
extension EditorView {
    /// Layout mode shows none of it: a layout is a template for what a slide
    /// draws, and nothing on it is ever played.
    @ViewBuilder func animatePanel(_ ui: Chrome) -> some View {
        if ui.editingLayouts {
            panelTitle("Layout")
            Text("Layouts have no builds or transitions.")
                .font(.system(size: 12))
                .foregroundStyle(palette.faint)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(Layout.panelPadding)
        } else {
            BuildOrderSwitch(
                palette: palette,
                segments: { animateSegments(ui) },
                order: { buildOrderPanel(ui) }
            )
        }
    }

    /// The three segments and whichever one is on, or the transition when there
    /// is no element for a build to be about.
    @ViewBuilder func animateSegments(_ ui: Chrome) -> some View {
        if ui.element == nil {
            panelTitle("Transitions")
            ScrollView {
                transitionSection(ui)
                    .padding(Layout.panelPadding)
                    .frame(maxWidth: .infinity, alignment: .top)
            }
            .scrollContentBackground(.hidden)
        } else {
            panelSegments(Self.animateSegmentNames, selected: Self.animateSegmentNames[ui.animateSegment]) {
                guard let index = Self.animateSegmentNames.firstIndex(of: $0) else { return }
                host.selectAnimateSegment(index: Int32(index))
            }

            ScrollView {
                animateSegmentBody(ui)
                    .padding(Layout.panelPadding)
                    .frame(maxWidth: .infinity, alignment: .top)
            }
            .scrollContentBackground(.hidden)
        }
    }

    /// `AnimateSegment`'s names, in its order: what the segmented control draws
    /// and what an index back to Kotlin counts in.
    static let animateSegmentNames = ["BuildIn", "Action", "BuildOut"]

    /// The primary element's builds of the segment's kind. Several is normal
    /// for an action and possible for the other two, so they all read as a list
    /// with an Add under it rather than as one editor.
    @ViewBuilder func animateSegmentBody(_ ui: Chrome) -> some View {
        let kind = Self.buildKind(ui.animateSegment)
        let mine = ui.builds.filter { $0.elementId == ui.selectedElementId && $0.kind == kind }

        VStack(alignment: .leading, spacing: Layout.panelPadding) {
            if mine.isEmpty {
                effectEmptyState(
                    "No \(Self.segmentLabel(Self.animateSegmentNames[ui.animateSegment])) Effect",
                    entries: effectChoices(ui, kind: kind)
                )
            } else {
                ForEach(mine) { entry in
                    VStack(alignment: .leading, spacing: 9) {
                        effectHeading(
                            Self.effectName(entry, ui),
                            entries: changeChoices(entry, ui)
                        )

                        buildControls(entry, ui, withPickers: false)
                    }

                    palette.divider.frame(height: 1)
                }

                // An action may hold several, and so may the other two: adding
                // another is a click rather than a trip through the order.
                addEffectButton(effectChoices(ui, kind: kind))
            }
        }
    }

    /// Which `BuildKind` a segment shows: Build In and Build Out are the model's
    /// first two, and Action is its third, so the two lists are not in the same
    /// order and the mapping is spelled out here.
    static func buildKind(_ segment: Int) -> Int {
        switch segment {
        case 1: return 2
        case 2: return 1
        default: return 0
        }
    }

    /// What a build is called in the heading: its action for an action, its
    /// effect otherwise.
    static func effectName(_ entry: BuildEntry, _ ui: Chrome) -> String {
        if entry.isAction {
            guard let index = entry.actionKindIndex, ui.actionKinds.indices.contains(index) else {
                return "Action"
            }
            return ui.actionKinds[index]
        }
        guard ui.buildEffects.indices.contains(entry.effectIndex) else { return "Effect" }
        return ui.buildEffects[entry.effectIndex]
    }

    /// The catalog a segment adds from: the action kinds for Action, the build
    /// effects for the other two. One pick adds the build wearing it, which is
    /// one edit and one undo entry.
    func effectChoices(_ ui: Chrome, kind: Int) -> [MenuEntry] {
        let titles = kind == 2 ? ui.actionKinds : ui.buildEffects
        return titles.enumerated().map { index, name in
            MenuEntry(title: name) {
                selectedBuild = nil
                host.addBuildWithEffect(kindIndex: Int32(kind), effectIndex: Int32(index))
            }
        }
    }

    /// The same catalog, pointed at a build that already exists: picking one
    /// dresses it rather than adding another.
    func changeChoices(_ entry: BuildEntry, _ ui: Chrome) -> [MenuEntry] {
        if entry.isAction {
            return ui.actionKinds.enumerated().map { index, name in
                MenuEntry(title: name) { commitBuild(entry, actionKindIndex: index) }
            }
        }
        return ui.buildEffects.enumerated().map { index, name in
            MenuEntry(title: name) { commitBuild(entry, effectIndex: index) }
        }
    }

    /// Nothing plays yet: what would play said in grey, and the one button that
    /// starts it, in the accent.
    func effectEmptyState(_ label: String, entries: [MenuEntry]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(label)
                .font(.system(size: 17))
                .foregroundStyle(palette.faint)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 2)

            addEffectButton(entries)
        }
    }

    /// What plays, and the two things to do about it: pick another, or watch it.
    func effectHeading(_ name: String, entries: [MenuEntry]) -> some View {
        HStack(alignment: .top, spacing: 12) {
            // Keynote animates a little square here. Ours is a still of the
            // accent at the weight the thumbnail reads at, since nothing in the
            // core renders an effect preview yet.
            RoundedRectangle(cornerRadius: 4, style: .continuous)
                .fill(palette.accent.opacity(0.35))
                .frame(width: 38, height: 38)
                .overlay {
                    RoundedRectangle(cornerRadius: 4, style: .continuous)
                        .inset(by: 0.5)
                        .stroke(palette.accent.opacity(0.6), lineWidth: 1)
                }

            VStack(alignment: .leading, spacing: 6) {
                Text(name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(palette.text)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    PopUpButton(entries: entries) { smallButtonLabel("Change") }
                    Button { startPreview() } label: {
                        smallButtonLabel("Preview \u{25B6}")
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// A pop-up drawn as one of the small raised buttons beside it, so Change
    /// and Preview read as a pair rather than as a popup next to a button.
    func smallButtonLabel(_ title: String) -> some View {
        let shape = RoundedRectangle(cornerRadius: 5, style: .continuous)
        return Text(title)
            .font(.system(size: 11.5))
            .foregroundStyle(palette.ctrlText)
            .frame(maxWidth: .infinity)
            .frame(height: 24)
            .background(palette.inspectorControl, in: shape)
            .contentShape(shape)
    }

    /// The panel's one prominent button: full width, in the accent, the way
    /// Keynote draws Add an Effect. The list drops out of it as an AppKit menu,
    /// so the fill and the centred label survive being a popup.
    func addEffectButton(_ entries: [MenuEntry]) -> some View {
        let shape = RoundedRectangle(cornerRadius: 6, style: .continuous)
        return PopUpButton(entries: entries) {
            Text("Add an Effect")
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(palette.accentText)
                .frame(maxWidth: .infinity)
                .frame(height: 30)
                .background(palette.accent, in: shape)
                .contentShape(shape)
        }
    }

    /// The build order, with the way back at its foot. The list itself is the
    /// same one the panel has always drawn.
    func buildOrderPanel(_ ui: Chrome) -> some View {
        VStack(spacing: 0) {
            panelTitle("Build Order")

            GeometryReader { proxy in
                ScrollView {
                    buildOrderSection(ui)
                        .padding(Layout.panelPadding)
                        .frame(maxWidth: .infinity, minHeight: proxy.size.height, alignment: .top)
                }
                .scrollContentBackground(.hidden)
            }
        }
    }
}

/// Which of the Animate tab's two faces is showing: the segments, or the build
/// order. View-local, and its own view because the panel's state lives in the
/// window's `EditorView` and this flag is nobody's business but this tab's.
///
/// The foot button is outside both scroll areas, so it stays put however long
/// the body above it runs.
struct BuildOrderSwitch<Segments: View, Order: View>: View {
    let palette: Palette
    @ViewBuilder let segments: () -> Segments
    @ViewBuilder let order: () -> Order

    @State private var showingOrder = false

    var body: some View {
        VStack(spacing: 0) {
            if showingOrder { order() } else { segments() }

            palette.divider.frame(height: 1)

            Button { showingOrder.toggle() } label: {
                let shape = RoundedRectangle(cornerRadius: Inspect.radius, style: .continuous)
                Text(showingOrder ? "Done" : "Build Order")
                    .font(.system(size: Inspect.text))
                    .foregroundStyle(palette.text)
                    .frame(maxWidth: .infinity)
                    .frame(height: Inspect.button)
                    .background(palette.inspectorControl, in: shape)
                    .contentShape(shape)
            }
            .buttonStyle(.plain)
            .padding(Inspect.side)
        }
    }
}
