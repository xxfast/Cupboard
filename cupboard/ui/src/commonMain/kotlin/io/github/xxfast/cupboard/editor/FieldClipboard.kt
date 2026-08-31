package io.github.xxfast.cupboard.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Cut, Copy and Paste inside an edit session, shared by the text box and the
 * code block.
 *
 * Mid-edit the app's own verbs grey out: the element clipboard has no business
 * firing while a caret is in something, so the keys are the field's. Nothing
 * hands them to it though, so the field's own handling is here, worked out
 * against the value the editors already hold and handed back as an ordinary
 * edit, which is what keeps one session one undo entry and one preview stream.
 *
 * The three below are the whole of it, pure so they can be tested without a
 * pasteboard anywhere near them. Each answers null when there is nothing to do,
 * which is the caller's cue to leave the field alone but still swallow the key:
 * a no-op Copy must not fall through to a menu item that would take the element.
 */

/**
 * The system pasteboard, as much of it as a field needs.
 *
 * An interface over compose's own, for two reasons: it keeps the one
 * deprecated call in the module behind a door, and it lets the key handling
 * below be tested without a pasteboard on the machine running the test.
 *
 * Compose's clipboard is the real thing on both shells: it hangs off the owner
 * rather than the platform context, so the macOS canvas gets an
 * `NSPasteboard`-backed one even though it hands the scene a
 * `PlatformContext.Empty`, and the desktop one is `Toolkit`'s.
 *
 * The deprecated one, deliberately. Its replacement is suspending, and a paste
 * that lands a keystroke or two after the key that asked for it is a worse bug
 * than a warning: the caret has moved on by then, and the value the edit was
 * computed against is stale.
 */
internal interface FieldClipboard {
    /** The pasteboard's text, null when it holds none, or none worth pasting. */
    fun read(): String?

    fun write(text: String)
}

/** The pasteboard compose is holding for whatever surface this is composed in. */
@Composable
internal fun rememberFieldClipboard(): FieldClipboard {
    val manager = LocalClipboardManager.current
    return remember(manager) {
        object : FieldClipboard {
            override fun read(): String? = manager.getText()?.text?.takeIf { it.isNotEmpty() }
            override fun write(text: String) = manager.setText(AnnotatedString(text))
        }
    }
}

/** What Copy and Cut take, null when the selection is empty. */
internal fun TextFieldValue.selectedText(): String? =
    text.substring(selection.min.clamp(text), selection.max.clamp(text))
        .takeIf { it.isNotEmpty() }

/**
 * This value with the selection taken out and the caret left where it started,
 * exactly where a field leaves it. Null when nothing is selected.
 */
internal fun TextFieldValue.withSelectionCut(): TextFieldValue? {
    val start: Int = selection.min.clamp(text)
    val end: Int = selection.max.clamp(text)
    if (start == end) return null
    return TextFieldValue(
        text = text.replaceRange(start, end, ""),
        selection = TextRange(start),
    )
}

/**
 * This value with [pasted] in place of the selection, the caret after it, which
 * is where a paste leaves it whether or not it replaced anything. Null when
 * there is nothing on the clipboard to put in.
 */
internal fun TextFieldValue.withPaste(pasted: String): TextFieldValue? {
    if (pasted.isEmpty()) return null
    val start: Int = selection.min.clamp(text)
    val end: Int = selection.max.clamp(text)
    return TextFieldValue(
        text = text.replaceRange(start, end, pasted),
        selection = TextRange(start + pasted.length),
    )
}

/** Offsets come from the field, but a clamp costs nothing and keeps a stale one honest. */
private fun Int.clamp(text: String): Int = coerceIn(0, text.length)

/**
 * Whether this key is the clipboard chord: Cmd on a mac, Ctrl everywhere else.
 *
 * Either counts, rather than a platform switch. This module has none, and buying
 * one for a boolean means an actual in five source sets, which is a lot of file
 * for very little answer (the core makes the same trade in its `Cupboard.jvm`
 * and `Cupboard.native` factories). The two never collide in practice: the mac
 * has no Ctrl+C, and the Super key nobody presses with C is the window
 * manager's on Linux long before it is ours. Both shells send the modifier the
 * platform actually uses, so the chord a user knows is the chord that works.
 */
private val KeyEvent.isClipboardModifierPressed: Boolean
    get() = isMetaPressed || isCtrlPressed

/**
 * The field's answer to [key], true when it was one of ours and is now spent.
 *
 * [onValue] is the editors' own value-change path, so a cut or a paste lands the
 * same way typing does: the caret moves here, the text goes back through the
 * loop as an element preview.
 *
 * Only the platform's own clipboard chord counts, and never with Option held:
 * Cmd+Opt+C is Copy Style, which is the app's verb and stays the app's.
 */
internal fun handleClipboardKey(
    key: KeyEvent,
    value: TextFieldValue,
    clipboard: FieldClipboard,
    onValue: (TextFieldValue) -> Unit,
): Boolean {
    if (!key.isClipboardModifierPressed || key.isAltPressed) return false

    when (key.key) {
        Key.C -> value.selectedText()?.let(clipboard::write)

        Key.X -> value.selectedText()?.let { taken ->
            clipboard.write(taken)
            value.withSelectionCut()?.let(onValue)
        }

        Key.V -> clipboard.read()?.let { pasted ->
            value.withPaste(pasted)?.let(onValue)
        }

        else -> return false
    }

    return true
}
