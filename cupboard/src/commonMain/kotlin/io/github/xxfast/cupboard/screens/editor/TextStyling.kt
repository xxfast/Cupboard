package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.ParagraphStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextRangeStyle
import io.github.xxfast.cupboard.document.TextRun
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.styleOf
import io.github.xxfast.cupboard.document.withBoxStyle
import io.github.xxfast.cupboard.document.withParagraphStyle

/**
 * A character style, from the Text inspector or the Format menu, put where it
 * goes: onto the words selected in the box being edited
 * ([EditorState.styleSelection]) as one [EditorEvent.StyleTextRange], so the
 * caret stays on them, and with none selected onto the whole of every unlocked
 * text box in the selection (`withBoxStyle`) as one [EditorEvent.UpdateElements].
 *
 * [change] is handed what the text it lands on is set in now, so a toggle can
 * read whether to turn on or off: the selected words as one, or each box on its
 * own. A change that changes nothing sends nothing.
 *
 * Here rather than in each shell because the choice is the same for every one
 * of them, and it reads the current state the way [insertSvg] does.
 */
fun EditorViewModel.onStyleText(change: (TextRangeStyle) -> TextRun) {
    val state: EditorState = states.value
    val selection: TextSelection? = state.styleSelection
    val editing: TextElement? = state.editingElement as? TextElement
    if (selection != null && editing != null) {
        val style: TextRun = change(editing.styleOf(selection.start, selection.end))
        return onStyleTextRange(selection.elementId, selection.start, selection.end, style)
    }

    val formatted: List<Element> = state.selectedElements.formatText { box ->
        box.withBoxStyle(change(box.styleOf(0, box.text.length)))
    }
    if (formatted.isNotEmpty()) onUpdateElements(formatted)
}

/**
 * Every unlocked text box in the selection put in the paragraph style [role]
 * names, whole (`withParagraphStyle`), as one [EditorEvent.UpdateElements]. A
 * role the slide offers no style for, or a pick that changes nothing, sends
 * nothing.
 *
 * Here beside [onStyleText] for the same reason: the styles are read off the
 * current state, and every shell picks the same way.
 */
fun EditorViewModel.onSetParagraphStyle(role: PlaceholderRole) {
    val state: EditorState = states.value
    val style: ParagraphStyle = state.paragraphStyles.firstOrNull { it.role == role } ?: return
    val formatted: List<Element> = state.selectedElements.formatText { it.withParagraphStyle(style) }
    if (formatted.isNotEmpty()) onUpdateElements(formatted)
}
