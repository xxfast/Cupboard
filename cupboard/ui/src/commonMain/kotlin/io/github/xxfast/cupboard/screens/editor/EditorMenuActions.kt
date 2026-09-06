package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.DefaultCodeBoxHeight
import io.github.xxfast.cupboard.document.DefaultCodeBoxWidth
import io.github.xxfast.cupboard.document.DefaultDiagramHeight
import io.github.xxfast.cupboard.document.DefaultDiagramWidth
import io.github.xxfast.cupboard.document.DefaultEquationHeight
import io.github.xxfast.cupboard.document.DefaultEquationWidth
import io.github.xxfast.cupboard.document.DefaultTerminalHeight
import io.github.xxfast.cupboard.document.DefaultTerminalWidth
import io.github.xxfast.cupboard.document.DefaultTextBoxHeight
import io.github.xxfast.cupboard.document.DefaultTextBoxWidth
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.ShapeCatalog
import io.github.xxfast.cupboard.document.ShapeCatalogEntry
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.ZOrderMove.Backward
import io.github.xxfast.cupboard.document.ZOrderMove.Forward
import io.github.xxfast.cupboard.document.ZOrderMove.ToBack
import io.github.xxfast.cupboard.document.ZOrderMove.ToFront
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.codeBoxElement
import io.github.xxfast.cupboard.document.diagramElement
import io.github.xxfast.cupboard.document.element
import io.github.xxfast.cupboard.document.equationElement
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.terminalElement
import io.github.xxfast.cupboard.document.textBoxElement
import io.github.xxfast.cupboard.document.toggleBold
import io.github.xxfast.cupboard.document.toggleItalic
import io.github.xxfast.cupboard.document.toggleStrikethrough
import io.github.xxfast.cupboard.document.toggleUnderline
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis

/**
 * One menu entry as data: the label, whether it is live, and what picking it
 * sends at the view model.
 *
 * The Compose shell shows the same verbs in two places, the menu bar and the
 * canvas context menu, and a rule written twice is a rule that drifts: a greyed
 * item in one and a live one in the other are the same bug the presenter's
 * guards exist to prevent. So the enablement lives here once and both renderers
 * read it.
 *
 * [children] non-empty makes this a submenu: the parent picks nothing itself,
 * and its own [enabled] is the gate for the whole branch, so children carry it
 * too for renderers that have no disabled parent to grey.
 */
data class EditorMenuItem(
    val label: String,
    val enabled: Boolean,
    val children: List<EditorMenuItem> = emptyList(),
    /** True for entries only the menu bar carries, dropped from the context menu. */
    val menuBarOnly: Boolean = false,
    val onPick: () -> Unit = {},
)

/** A run of items between two separators. */
data class EditorMenuSection(val items: List<EditorMenuItem>)

/**
 * Cut / Copy / Paste / Delete, on the rules the Edit menu applies: copy takes a
 * locked element like any other, everything that moves or removes wants
 * something unlocked, and paste follows the clipboard rather than the selection.
 *
 * The menu bar's Edit menu keeps its own hand-written items: it carries
 * accelerators, and half of what it holds (undo, the style clipboard, Clear All)
 * is nothing a canvas right-click offers.
 */
fun editSection(state: EditorState, viewModel: EditorViewModel): EditorMenuSection {
    val ids: List<String> = state.selectedElementIds
    val editable: Boolean = state.selectedElements.any { !it.locked }

    return EditorMenuSection(
        listOf(
            EditorMenuItem("Cut", editable) { viewModel.onCutElements(ids) },
            EditorMenuItem("Copy", ids.isNotEmpty()) { viewModel.onCopyElements(ids) },
            EditorMenuItem("Paste", state.canPaste) { viewModel.onPaste() },
            EditorMenuItem("Delete", editable) { viewModel.onDeleteElements(ids) },
        ),
    )
}

/**
 * The Arrange verbs, separator by separator: z-order, the flips, group/ungroup
 * with the lock, then the two submenus. The labels follow the primary element,
 * the events carry the whole selection.
 */
fun arrangeSections(state: EditorState, viewModel: EditorViewModel): List<EditorMenuSection> {
    val primary: Element? = state.primaryElement
    val ids: List<String> = state.selectedElementIds
    val unlocked: List<Element> = state.selectedElements.filter { !it.locked }
    val editable: Boolean = unlocked.isNotEmpty()
    // Ungrouping is a single-group act: two groups selected is a batch nothing
    // else in the app does.
    val group: GroupElement? = (primary as? GroupElement)?.takeIf { ids.size == 1 && !it.locked }
    // Two elements have no gap between them to equalize.
    val distributable: Boolean = unlocked.size >= 3

    return listOf(
        EditorMenuSection(
            listOf(
                EditorMenuItem("Bring Forward", editable) {
                    viewModel.onReorderElements(ids, Forward)
                },
                EditorMenuItem("Send Backward", editable) {
                    viewModel.onReorderElements(ids, Backward)
                },
                EditorMenuItem("Bring to Front", editable) {
                    viewModel.onReorderElements(ids, ToFront)
                },
                EditorMenuItem("Send to Back", editable) {
                    viewModel.onReorderElements(ids, ToBack)
                },
            ),
        ),
        // Flipping is a menu-bar verb: it has no place in a right-click menu
        // that already runs long, and no other editor puts it there.
        EditorMenuSection(
            listOf(
                EditorMenuItem("Flip Horizontally", editable, menuBarOnly = true) {
                    viewModel.onFlipElements(ids, FlipAxis.Horizontal)
                },
                EditorMenuItem("Flip Vertically", editable, menuBarOnly = true) {
                    viewModel.onFlipElements(ids, FlipAxis.Vertical)
                },
            ),
        ),
        EditorMenuSection(
            listOf(
                EditorMenuItem("Group", unlocked.size >= 2) { viewModel.onGroupElements(ids) },
                EditorMenuItem("Ungroup", group != null) {
                    group?.let { viewModel.onUngroupElements(it.id) }
                },
                EditorMenuItem(
                    label = if (primary?.locked == true) "Unlock" else "Lock",
                    enabled = primary != null,
                ) { viewModel.onSetElementsLocked(ids, primary?.locked != true) },
            ),
        ),
        EditorMenuSection(
            listOf(
                // A lone element aligns to the slide, so one is enough.
                EditorMenuItem(
                    label = "Align Objects",
                    enabled = editable,
                    children = listOf(
                        alignItem("Left", AlignEdge.Left, editable, viewModel),
                        alignItem("Center", AlignEdge.CenterX, editable, viewModel),
                        alignItem("Right", AlignEdge.Right, editable, viewModel),
                        alignItem("Top", AlignEdge.Top, editable, viewModel),
                        alignItem("Middle", AlignEdge.CenterY, editable, viewModel),
                        alignItem("Bottom", AlignEdge.Bottom, editable, viewModel),
                    ),
                ),
                EditorMenuItem(
                    label = "Distribute Objects",
                    enabled = distributable,
                    children = listOf(
                        EditorMenuItem("Horizontally", distributable) {
                            viewModel.onDistributeElements(Axis.Horizontal)
                        },
                        EditorMenuItem("Vertically", distributable) {
                            viewModel.onDistributeElements(Axis.Vertical)
                        },
                    ),
                ),
            ),
        ),
    )
}

private fun alignItem(
    label: String,
    edge: AlignEdge,
    enabled: Boolean,
    viewModel: EditorViewModel,
): EditorMenuItem = EditorMenuItem(label, enabled) { viewModel.onAlignElements(edge) }

/**
 * The text verbs, separator by separator: the four style toggles, the three
 * alignments, then the list styles.
 *
 * Formatting is a property of the whole box, so these apply to the selection's
 * text elements and to nothing else in it: `formatText` drops the shapes, the
 * images and anything locked, and an empty result is an edit that changed
 * nothing, which is no event and no history entry. That is also why a caret
 * sitting in the text makes no difference to what any of these do.
 *
 * Nothing carries a checkmark. The state these would show is the primary box's,
 * which is a lie the moment two boxes disagree, and neither renderer has a mixed
 * mark to draw instead.
 */
fun formatSections(state: EditorState, viewModel: EditorViewModel): List<EditorMenuSection> {
    val elements: List<Element> = state.selectedElements
    val formattable: Boolean = elements.any { it is TextElement && !it.locked }

    fun format(transform: (TextElement) -> TextElement): () -> Unit = {
        val formatted: List<Element> = elements.formatText(transform)
        if (formatted.isNotEmpty()) viewModel.onUpdateElements(formatted)
    }

    return listOf(
        EditorMenuSection(
            listOf(
                EditorMenuItem("Bold", formattable, onPick = format { it.toggleBold() }),
                EditorMenuItem("Italic", formattable, onPick = format { it.toggleItalic() }),
                EditorMenuItem("Underline", formattable, onPick = format { it.toggleUnderline() }),
                EditorMenuItem(
                    label = "Strikethrough",
                    enabled = formattable,
                    onPick = format { it.toggleStrikethrough() },
                ),
            ),
        ),
        EditorMenuSection(
            listOf(
                TextAlign.Start to "Align Left",
                TextAlign.Center to "Align Center",
                TextAlign.End to "Align Right",
            ).map { (align, label) ->
                EditorMenuItem(label, formattable, onPick = format { it.copy(align = align) })
            },
        ),
        EditorMenuSection(
            listOf(
                ListStyle.Bullet to "Bullet List",
                ListStyle.Numbered to "Numbered List",
                ListStyle.None to "No List",
            ).map { (style, label) ->
                EditorMenuItem(label, formattable, onPick = format { it.copy(listStyle = style) })
            },
        ),
    )
}

/**
 * The Insert verbs: a text box, a code block, a terminal, a diagram, an
 * equation, then the shape catalog as one submenu.
 *
 * Nothing is ever greyed. An insertion asks nothing of the selection, and a
 * locked element on the slide is no reason not to add another one next to it.
 *
 * Where an insertion lands is [EditorState.insertionFrame]'s business and how
 * big it starts is the catalog's, so a shell renders this without knowing
 * either the slide's size or that a line wants a different box to a rectangle.
 */
fun insertSections(state: EditorState, viewModel: EditorViewModel): List<EditorMenuSection> {
    fun insert(entry: ShapeCatalogEntry): EditorMenuItem =
        EditorMenuItem(entry.title, enabled = true) {
            val frame: Frame = state.insertionFrame(entry.width, entry.height)
            viewModel.onInsertElement(entry.element(frame))
        }

    return listOf(
        EditorMenuSection(
            listOf(
                EditorMenuItem("Text Box", enabled = true) {
                    val frame: Frame =
                        state.insertionFrame(DefaultTextBoxWidth, DefaultTextBoxHeight)
                    viewModel.onInsertElement(textBoxElement(frame))
                },
                EditorMenuItem("Code", enabled = true) {
                    val frame: Frame =
                        state.insertionFrame(DefaultCodeBoxWidth, DefaultCodeBoxHeight)
                    viewModel.onInsertElement(codeBoxElement(frame))
                },
                EditorMenuItem("Terminal", enabled = true) {
                    val frame: Frame =
                        state.insertionFrame(DefaultTerminalWidth, DefaultTerminalHeight)
                    viewModel.onInsertElement(terminalElement(frame))
                },
                EditorMenuItem("Diagram", enabled = true) {
                    val frame: Frame =
                        state.insertionFrame(DefaultDiagramWidth, DefaultDiagramHeight)
                    viewModel.onInsertElement(diagramElement(frame))
                },
                EditorMenuItem("Equation", enabled = true) {
                    val frame: Frame =
                        state.insertionFrame(DefaultEquationWidth, DefaultEquationHeight)
                    viewModel.onInsertElement(equationElement(frame))
                },
                EditorMenuItem(
                    label = "Shape",
                    enabled = true,
                    children = ShapeCatalog.entries.map(::insert),
                ),
            ),
        ),
    )
}

/**
 * The slide verbs, section by section: [New Slide, Duplicate Slide], then the
 * clipboard run, then Delete Slide, then the skip toggle. Both the menu bar's
 * Slide menu and the navigator's context menu render these.
 *
 * Every action carries [slideId] rather than reading the selection, so the row
 * the menu opened on is the row it acts on, whatever the selection does while
 * the menu sits open. That is also why nothing here is greyed: the presenter
 * keeps the document non-empty and re-anchors the selection, so there is no
 * last slide to protect from, and an id-carrying verb has no selection to be
 * out of step with. Paste is the one gate, and it asks the clipboard, not the
 * selection.
 *
 * Paste selects [slideId] before it pastes: two events, in that order, and the
 * events flow serializes them, so the paste lands on the clicked row rather
 * than wherever the selection happened to be.
 *
 * [includePaste] false drops that verb, for the menu bar, where Edit > Paste
 * already owns it.
 */
fun slideSections(
    state: EditorState,
    viewModel: EditorViewModel,
    slideId: String,
    includePaste: Boolean,
): List<EditorMenuSection> = listOf(
    EditorMenuSection(
        listOf(
            EditorMenuItem("New Slide", enabled = true) { viewModel.onAddSlide(slideId) },
            EditorMenuItem("Duplicate Slide", enabled = true) {
                viewModel.onDuplicateSlide(slideId)
            },
        ),
    ),
    EditorMenuSection(
        listOfNotNull(
            EditorMenuItem("Cut Slide", enabled = true) { viewModel.onCutSlide(slideId) },
            EditorMenuItem("Copy Slide", enabled = true) { viewModel.onCopySlide(slideId) },
            EditorMenuItem("Paste", state.canPaste) {
                viewModel.onSelectSlide(slideId)
                viewModel.onPaste()
            }.takeIf { includePaste },
        ),
    ),
    EditorMenuSection(
        listOf(
            EditorMenuItem("Delete Slide", enabled = true) { viewModel.onDeleteSlide(slideId) },
        ),
    ),
    // Keynote's two titles for the one verb, read off the row the menu opened on
    // rather than the selection, like everything else here.
    EditorMenuSection(
        listOf(
            EditorMenuItem(
                label = if (state.isSkipped(slideId)) "Don't Skip Slide" else "Skip Slide",
                enabled = true,
            ) { viewModel.onSetSlideSkipped(slideId, !state.isSkipped(slideId)) },
        ),
    ),
)

/** Whether the slide [slideId] names is out of the presentation. An id this
 * document doesn't hold reads as in it, which keeps the verb's title sane. */
private fun EditorState.isSkipped(slideId: String): Boolean =
    document.allSlides().firstOrNull { it.id == slideId }?.skipped == true

/**
 * What a right-click on the canvas opens: [editSection] then [arrangeSections],
 * without the entries that belong to the menu bar alone.
 */
fun canvasMenuSections(state: EditorState, viewModel: EditorViewModel): List<EditorMenuSection> =
    (listOf(editSection(state, viewModel)) + arrangeSections(state, viewModel))
        .map { section -> EditorMenuSection(section.items.filter { !it.menuBarOnly }) }
        .filter { section -> section.items.isNotEmpty() }
