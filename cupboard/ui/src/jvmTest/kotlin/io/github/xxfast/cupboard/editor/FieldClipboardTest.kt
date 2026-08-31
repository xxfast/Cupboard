package io.github.xxfast.cupboard.editor

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What Cut, Copy and Paste do to a field mid-edit. The pasteboard itself is the
 * platform's business and untested here; everything above it is this.
 */
class FieldClipboardTest {

    private class FakeClipboard(var content: String? = null) : FieldClipboard {
        override fun read(): String? = content
        override fun write(text: String) { content = text }
    }

    private fun field(text: String, start: Int, end: Int = start): TextFieldValue =
        TextFieldValue(text, TextRange(start, end))

    /** The chord as a shell sends it: meta, which is what a mac's Cmd arrives as. */
    @OptIn(InternalComposeUiApi::class)
    private fun chord(
        key: Key,
        meta: Boolean = true,
        ctrl: Boolean = false,
        alt: Boolean = false,
    ): KeyEvent = KeyEvent(
        key = key,
        type = KeyEventType.KeyDown,
        isMetaPressed = meta,
        isCtrlPressed = ctrl,
        isAltPressed = alt,
    )

    @Test
    fun `copy takes the selection`() {
        assertEquals("ell", field("hello", 1, 4).selectedText())
    }

    @Test
    fun `copy takes nothing from a bare caret`() {
        assertNull(field("hello", 2).selectedText())
    }

    @Test
    fun `cut removes the selection and leaves the caret where it opened`() {
        val cut: TextFieldValue = requireNotNull(field("hello", 1, 4).withSelectionCut())
        assertEquals("ho", cut.text)
        assertEquals(TextRange(1), cut.selection)
    }

    @Test
    fun `cut does nothing to a bare caret`() {
        assertNull(field("hello", 2).withSelectionCut())
    }

    @Test
    fun `paste replaces the selection and lands the caret after it`() {
        val pasted: TextFieldValue = requireNotNull(field("hello", 1, 4).withPaste("owd"))
        assertEquals("howdo", pasted.text)
        assertEquals(TextRange(4), pasted.selection)
    }

    @Test
    fun `paste at a bare caret inserts`() {
        val pasted: TextFieldValue = requireNotNull(field("hello", 2).withPaste("XY"))
        assertEquals("heXYllo", pasted.text)
        assertEquals(TextRange(4), pasted.selection)
    }

    @Test
    fun `paste of many lines lands the caret after the last of them`() {
        val pasted: TextFieldValue = requireNotNull(field("ab", 1).withPaste("1\n2"))
        assertEquals("a1\n2b", pasted.text)
        assertEquals(TextRange(4), pasted.selection)
    }

    @Test
    fun `paste of nothing is nothing`() {
        assertNull(field("hello", 2).withPaste(""))
    }

    @Test
    fun `copy writes the selection and leaves the field alone`() {
        val clipboard = FakeClipboard()
        var edited: TextFieldValue? = null

        val consumed: Boolean =
            handleClipboardKey(chord(Key.C), field("hello", 1, 4), clipboard) { edited = it }

        assertTrue(consumed)
        assertEquals("ell", clipboard.content)
        assertNull(edited)
    }

    @Test
    fun `cut writes the selection and takes it out`() {
        val clipboard = FakeClipboard()
        var edited: TextFieldValue? = null

        val consumed: Boolean =
            handleClipboardKey(chord(Key.X), field("hello", 1, 4), clipboard) { edited = it }

        assertTrue(consumed)
        assertEquals("ell", clipboard.content)
        assertEquals("ho", edited?.text)
        assertEquals(TextRange(1), edited?.selection)
    }

    @Test
    fun `paste puts the clipboard in`() {
        val clipboard = FakeClipboard("owd")
        var edited: TextFieldValue? = null

        val consumed: Boolean =
            handleClipboardKey(chord(Key.V), field("hello", 1, 4), clipboard) { edited = it }

        assertTrue(consumed)
        assertEquals("howdo", edited?.text)
        assertEquals(TextRange(4), edited?.selection)
    }

    /** Nothing to do, but the key is still spent: it must not reach a menu item. */
    @Test
    fun `an empty selection and an empty clipboard still swallow the chord`() {
        val clipboard = FakeClipboard()
        var edited: TextFieldValue? = null
        val caret: TextFieldValue = field("hello", 2)

        assertTrue(handleClipboardKey(chord(Key.C), caret, clipboard) { edited = it })
        assertTrue(handleClipboardKey(chord(Key.X), caret, clipboard) { edited = it })
        assertTrue(handleClipboardKey(chord(Key.V), caret, clipboard) { edited = it })
        assertNull(clipboard.content)
        assertNull(edited)
    }

    /** Ctrl is the same chord off a mac, and the helper takes either. */
    @Test
    fun `the control chord copies too`() {
        val clipboard = FakeClipboard()
        val control: KeyEvent = chord(Key.C, meta = false, ctrl = true)

        assertTrue(handleClipboardKey(control, field("hello", 0, 5), clipboard) {})
        assertEquals("hello", clipboard.content)
    }

    /** Cmd+Opt+C is Copy Style, which is the app's verb even mid-edit. */
    @Test
    fun `the style chord is left to the app`() {
        val clipboard = FakeClipboard()

        assertFalse(
            handleClipboardKey(chord(Key.C, alt = true), field("hello", 0, 5), clipboard) {}
        )
        assertNull(clipboard.content)
    }

    @Test
    fun `an unmodified letter is just typing`() {
        val clipboard = FakeClipboard()

        assertFalse(
            handleClipboardKey(chord(Key.C, meta = false), field("hello", 0, 5), clipboard) {}
        )
        assertNull(clipboard.content)
    }

    @Test
    fun `a chord that is not ours is left alone`() {
        val clipboard = FakeClipboard("owd")
        var edited: TextFieldValue? = null

        assertFalse(handleClipboardKey(chord(Key.B), field("hello", 0, 5), clipboard) { edited = it })
        assertNull(edited)
    }
}
