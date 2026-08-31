package io.github.xxfast.cupboard.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a shell sees through the bridge. The field's half is faked here: what is
 * being tested is the registration and the facts, not compose.
 */
class FieldMenuBridgeTest {

    private class FakeClipboard(var content: String? = null) : FieldClipboard {
        override fun read(): String? = content
        override fun write(text: String) { content = text }
    }

    /** A field, as the editors register one: a live read of its value. */
    private class FakeField(
        text: String,
        start: Int,
        end: Int = start,
        val clipboard: FakeClipboard = FakeClipboard(),
    ) {
        var value: TextFieldValue = TextFieldValue(text, TextRange(start, end))

        val verbs = FieldVerbs(
            value = { value },
            clipboard = clipboard,
            onValue = { value = it },
        )
    }

    @Test
    fun `an empty bridge offers nothing and does nothing`() {
        val bridge = FieldMenuBridge()

        assertFalse(bridge.isEditing)
        assertFalse(bridge.hasSelection)
        assertFalse(bridge.canPaste)
        assertFalse(bridge.canSelectAll)
        // Every verb is a no-op rather than a throw: a menu that should not have
        // popped must not take the app with it.
        bridge.cut()
        bridge.copy()
        bridge.paste()
        bridge.selectAll()
    }

    @Test
    fun `the facts follow the registered field`() {
        val bridge = FieldMenuBridge()
        val field = FakeField("hello", 1, 4, FakeClipboard("owd"))
        bridge.register(field.verbs)

        assertTrue(bridge.isEditing)
        assertTrue(bridge.hasSelection)
        assertTrue(bridge.canPaste)
        assertTrue(bridge.canSelectAll)
    }

    @Test
    fun `a bare caret has nothing to cut and an empty pasteboard nothing to paste`() {
        val bridge = FieldMenuBridge()
        bridge.register(FakeField("hello", 2).verbs)

        assertFalse(bridge.hasSelection)
        assertFalse(bridge.canPaste)
        assertTrue(bridge.canSelectAll)
    }

    /** The facts are read live: a selection made after registering counts. */
    @Test
    fun `the facts read the field as it stands`() {
        val bridge = FieldMenuBridge()
        val field = FakeField("hello", 2)
        bridge.register(field.verbs)
        assertFalse(bridge.hasSelection)

        field.value = TextFieldValue("hello", TextRange(0, 5))

        assertTrue(bridge.hasSelection)
        // All of it is selected now, so there is nothing left to select.
        assertFalse(bridge.canSelectAll)
    }

    @Test
    fun `the verbs edit the field they were registered from`() {
        val bridge = FieldMenuBridge()
        val field = FakeField("hello", 1, 4)
        bridge.register(field.verbs)

        bridge.cut()
        assertEquals("ell", field.clipboard.content)
        assertEquals("ho", field.value.text)

        bridge.paste()
        assertEquals("hello", field.value.text)

        bridge.selectAll()
        assertEquals(TextRange(0, 5), field.value.selection)

        bridge.copy()
        assertEquals("hello", field.clipboard.content)
    }

    @Test
    fun `clearing takes the field away`() {
        val bridge = FieldMenuBridge()
        val field = FakeField("hello", 1, 4)
        bridge.register(field.verbs)
        bridge.clear(field.verbs)

        assertFalse(bridge.isEditing)
        bridge.cut()
        assertNull(field.clipboard.content)
        assertEquals("hello", field.value.text)
    }

    /**
     * The caret moving from one element to the next: whichever way round compose
     * runs the dispose and the register, the field that is actually there wins.
     */
    @Test
    fun `a stale clear leaves the field that took over alone`() {
        val bridge = FieldMenuBridge()
        val gone = FakeField("hello", 0, 5)
        val next = FakeField("world", 0, 5)
        bridge.register(gone.verbs)
        bridge.register(next.verbs)

        bridge.clear(gone.verbs)

        assertTrue(bridge.isEditing)
        bridge.copy()
        assertEquals("world", next.clipboard.content)
    }
}
