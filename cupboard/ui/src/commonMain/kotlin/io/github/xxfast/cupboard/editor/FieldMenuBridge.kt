package io.github.xxfast.cupboard.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.input.TextFieldValue

/**
 * The way a native shell reaches the caret's own Cut, Copy, Paste and Select All.
 *
 * Mid-edit the field sits above the canvas input overlay and keeps its clicks, so
 * a right-click there never reaches the loop's context-click path and compose pops
 * its own drawn menu. That is right on the compose shells and wrong on a mac,
 * where every other menu is an `NSMenu`. A shell that wants the native one hands
 * the canvas one of these: the field registers its verbs while the caret is in it
 * and clears them when it leaves, and the shell asks this what to enable and what
 * to run.
 *
 * The verbs are the [FieldClipboard] ones, called exactly as the Cmd chords call
 * them, so a menu pick and a keystroke are the same act to the loop: one preview
 * out per edit, one undo entry for the session.
 *
 * Not state anything draws from: nothing here is read during composition, only
 * from a menu the shell is building or an item the user just picked. The plain
 * `var` is deliberate.
 */
class FieldMenuBridge {
    private var active: FieldVerbs? = null

    /** True while a caret is in something, i.e. while there is a menu to pop at all. */
    val isEditing: Boolean get() = active != null

    /** What Cut and Copy need: a selection to take. */
    val hasSelection: Boolean get() = active?.let { it.value().selectedText() != null } == true

    /**
     * Whether the pasteboard is holding text. Read on the spot rather than cached:
     * the read is one synchronous pasteboard call and the answer is only wanted
     * when a menu is being built, which is once per right-click.
     */
    val canPaste: Boolean get() = active?.clipboard?.read() != null

    /** Nothing to select in an empty field, and nothing to do in a fully selected one. */
    val canSelectAll: Boolean get() = active?.let { it.value().withAllSelected() != null } == true

    fun cut() {
        val verbs: FieldVerbs = active ?: return
        cutField(verbs.value(), verbs.clipboard, verbs.onValue)
    }

    fun copy() {
        val verbs: FieldVerbs = active ?: return
        copyField(verbs.value(), verbs.clipboard)
    }

    fun paste() {
        val verbs: FieldVerbs = active ?: return
        pasteField(verbs.value(), verbs.clipboard, verbs.onValue)
    }

    fun selectAll() {
        val verbs: FieldVerbs = active ?: return
        selectAllField(verbs.value(), verbs.onValue)
    }

    internal fun register(verbs: FieldVerbs) {
        active = verbs
    }

    /**
     * Clears [verbs] if they are still the registered ones. The identity check is
     * what keeps a caret moving from one element to the next honest whichever way
     * round compose runs the dispose and the register.
     */
    internal fun clear(verbs: FieldVerbs) {
        if (active === verbs) active = null
    }
}

/**
 * One field's half of the bridge: where its value is right now, its pasteboard,
 * and the one way its value changes. [value] is a read rather than a copy, so a
 * menu item acts on the text as it stands when it is picked, not as it stood when
 * the field was composed.
 */
internal class FieldVerbs(
    val value: () -> TextFieldValue,
    val clipboard: FieldClipboard,
    val onValue: (TextFieldValue) -> Unit,
)

/**
 * Registers this field's verbs with [bridge] for as long as the caret is in
 * [elementId], and takes them away when it leaves. A no-op without a bridge,
 * which is every shell that draws its own menu.
 */
@Composable
internal fun RegisterFieldMenu(
    bridge: FieldMenuBridge?,
    elementId: String,
    value: () -> TextFieldValue,
    clipboard: FieldClipboard,
    onValue: (TextFieldValue) -> Unit,
) {
    if (bridge == null) return
    // The registration outlives the recomposition that made it, so the lambdas it
    // holds are the latest ones rather than the ones the effect happened to see.
    val currentValue by rememberUpdatedState(value)
    val currentOnValue by rememberUpdatedState(onValue)
    DisposableEffect(bridge, elementId) {
        val verbs = FieldVerbs(
            value = { currentValue() },
            clipboard = clipboard,
            onValue = { currentOnValue(it) },
        )
        bridge.register(verbs)
        onDispose { bridge.clear(verbs) }
    }
}
