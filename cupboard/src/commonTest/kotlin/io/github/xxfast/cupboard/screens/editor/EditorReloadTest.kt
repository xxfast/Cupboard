package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.BundleChange
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.encodeToString
import io.github.xxfast.cupboard.document.updateSlide
import io.github.xxfast.kstore.Codec
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storeOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The deck changing on disk under an open editor, as the watcher reports it:
 * [BundleChange]s pushed straight in, so what is tested is the editor's half,
 * the reload, and not the filesystem's (see `BundleWatcherTest` for that).
 *
 * Slide "one" carries a text element and two shapes; "two" and "three" are bare,
 * so the selection has somewhere to re-anchor to.
 */
class EditorReloadTest {
    // Fixed ids, so a deck built twice compares equal, the way one off disk does.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    TextElement(id = "text", frame = Frame(0f, 0f, 200f, 40f), text = "Hello"),
                    ShapeElement(id = "shape", frame = Frame(0f, 120f, 100f, 100f)),
                    ShapeElement(id = "other", frame = Frame(200f, 120f, 100f, 100f)),
                ),
            ),
            Slide(id = "two", title = "Two"),
            Slide(id = "three", title = "Three"),
        ),
    )

    /** [document] with slide [id] renamed: the smallest edit another tool could make. */
    private fun Document.retitled(id: String, title: String): Document =
        updateSlide(slides.first { it.id == id }.copy(title = title))

    /** What reading this deck back off disk would hand the editor: equal, not the same. */
    private fun Document.roundTripped(): Document =
        (decodeDocument(encodeToString()) as DocumentLoad.Loaded).document

    /**
     * A shared flow drops what is emitted before anything collects it, and the
     * presenter only subscribes once it is running.
     */
    private suspend fun MutableSharedFlow<BundleChange>.awaitWatched() =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { subscriptionCount.first { it > 0 } }
        }

    private fun watched(): MutableSharedFlow<BundleChange> = MutableSharedFlow()

    @Test
    fun aReloadReplacesTheDocument() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()

        val external: Document = document().retitled("one", "From disk")
        changes.emit(BundleChange.Deck(external))

        val reloaded = viewModel.await { it.document == external }
        assertEquals("From disk", reloaded.selectedSlide.title)
    }

    @Test
    fun theSelectedSlideIsKeptWhenItIsStillThere() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        val external: Document = document().retitled("one", "From disk")
        changes.emit(BundleChange.Deck(external))

        assertEquals("two", viewModel.await { it.document == external }.selectedSlideId)
    }

    @Test
    fun aSelectedSlideThatIsGoneReAnchorsByIndex() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onSelectSlide("two")
        viewModel.await { it.selectedSlideId == "two" }

        val external: Document = document().let { deck ->
            deck.copy(slides = deck.slides.filterNot { it.id == "two" })
        }
        changes.emit(BundleChange.Deck(external))

        // Index 1, where "two" sat, is "three" now.
        assertEquals("three", viewModel.await { it.document == external }.selectedSlideId)
    }

    @Test
    fun theElementSelectionIsPrunedToWhatTheSlideStillHas() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onSelectElements(listOf("shape", "other"))
        viewModel.await { it.selectedElementIds == listOf("shape", "other") }

        val external: Document = document().let { deck ->
            val one: Slide = deck.slides.first()
            deck.updateSlide(one.copy(elements = one.elements.filterNot { it.id == "shape" }))
        }
        changes.emit(BundleChange.Deck(external))

        assertEquals(listOf("other"), viewModel.await { it.document == external }.selectedElementIds)
    }

    @Test
    fun aReloadClearsHistory() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Edited"))
        viewModel.await { it.canUndo }
        viewModel.onUndo()
        viewModel.await { it.canRedo }
        viewModel.onRedo()
        viewModel.await { it.canUndo && it.selectedSlide.title == "Edited" }

        val external: Document = document().retitled("two", "From disk")
        changes.emit(BundleChange.Deck(external))

        val reloaded = viewModel.await { it.document == external }
        assertFalse(reloaded.canUndo)
        assertFalse(reloaded.canRedo)

        // Undo is the channel's, and the sidebar after it on the same one says
        // when it has been through.
        viewModel.onUndo()
        viewModel.onToggleSidebar()
        val after = viewModel.await { it.sidebarOpen != reloaded.sidebarOpen }
        assertEquals(external, after.document)
    }

    @Test
    fun aReloadOfWhatIsShowingIsANoOp() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Edited"))
        val edited = viewModel.await { it.canUndo }

        changes.emit(BundleChange.Deck(edited.document.roundTripped()))
        // Same flow as the reload, so once this one has landed so has that.
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertSame(edited.document, after.document)
        assertTrue(after.canUndo)
    }

    /**
     * The editor's own write coming back round. Equal to what was written, but
     * not to what is showing: the edit after it has not been written yet, and
     * taking the write back would throw that edit away.
     */
    @Test
    fun aReloadOfWhatAutosaveWroteIsIgnored() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Written"))
        viewModel.await { it.selectedSlide.title == "Written" && it.savePending }
        val written: Document = viewModel.await { !it.savePending }.document

        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Pending"))
        viewModel.await { it.selectedSlide.title == "Pending" }
        changes.emit(BundleChange.Deck(written.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertEquals("Pending", after.selectedSlide.title)
        assertTrue(after.canUndo)
    }

    @Test
    fun aReloadIsNotPendingASave() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Edited"))
        viewModel.await { it.savePending }

        val external: Document = document().retitled("one", "From disk")
        changes.emit(BundleChange.Deck(external))

        assertFalse(viewModel.await { it.document == external }.savePending)
    }

    @Test
    fun anAssetChangeBumpsTheGenerationAndNothingElse() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onSelectElement("shape")
        val before = viewModel.await { it.selectedElementIds == listOf("shape") }

        changes.emit(BundleChange.Assets)
        val once = viewModel.await { it.assetGeneration == 1 }
        assertEquals(before, once.copy(assetGeneration = 0))

        changes.emit(BundleChange.Assets)
        assertEquals(2, viewModel.await { it.assetGeneration == 2 }.assetGeneration)
    }

    @Test
    fun aReloadMidTextEditEndsTheSessionAndLands() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onBeginTextEdit("text")
        val editing = viewModel.await { it.isEditingText }
        val text = editing.selectedSlide.elements.first { it.id == "text" } as TextElement
        viewModel.onPreviewElements(listOf(text.copy(text = "Hello, wor")))
        viewModel.await { it.isPreviewing }

        val external: Document = document().retitled("one", "From disk")
        changes.emit(BundleChange.Deck(external))

        val reloaded = viewModel.await { it.document == external }
        assertNull(reloaded.editingElementId)
        assertFalse(reloaded.isPreviewing)
        assertFalse(reloaded.canUndo)
    }

    @Test
    fun aReloadOfWhatIsShowingKeepsTheTextEditOpen() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onBeginTextEdit("text")
        val editing = viewModel.await { it.isEditingText }
        val text = editing.selectedSlide.elements.first { it.id == "text" } as TextElement
        viewModel.onPreviewElements(listOf(text.copy(text = "Hello, wor")))
        val previewing = viewModel.await { it.isPreviewing }

        changes.emit(BundleChange.Deck(previewing.document.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertEquals("text", after.editingElementId)
        assertTrue(after.isPreviewing)
    }

    /**
     * Notes are written as they are typed, so autosave lands mid-session and the
     * watcher hands it straight back. The caret has to still be there after.
     */
    @Test
    fun aReloadOfWhatAutosaveWroteKeepsTheNotesEditOpen() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onBeginNotesEdit()
        viewModel.onEditNotes("Written")
        viewModel.await { it.selectedSlide.notes == "Written" && it.savePending }
        val written: Document = viewModel.await { !it.savePending }.document

        viewModel.onEditNotes("Written, then more")
        viewModel.await { it.selectedSlide.notes == "Written, then more" }
        changes.emit(BundleChange.Deck(written.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertTrue(after.isEditingNotes)
        assertTrue(after.savePending)
        assertEquals("Written, then more", after.selectedSlide.notes)
    }

    /**
     * The watcher reads late, so a second autosave can land before the first
     * one's echo does. That echo is still ours, not a deck to go back to.
     */
    @Test
    fun anEchoThatComesBackAfterTheNextWriteIsIgnored() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onBeginNotesEdit()
        viewModel.onEditNotes("First")
        viewModel.await { it.selectedSlide.notes == "First" && it.savePending }
        val first: Document = viewModel.await { !it.savePending }.document
        viewModel.onEditNotes("First, second")
        viewModel.await { it.selectedSlide.notes == "First, second" && it.savePending }
        val second: Document = viewModel.await { !it.savePending }.document

        viewModel.onEditNotes("First, second, third")
        viewModel.await { it.selectedSlide.notes == "First, second, third" }
        changes.emit(BundleChange.Deck(first.roundTripped()))
        changes.emit(BundleChange.Deck(second.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertTrue(after.isEditingNotes)
        assertEquals("First, second, third", after.selectedSlide.notes)
        assertTrue(after.savePending)
    }

    /**
     * The store writes a temporary file and moves it over the deck, and the
     * folder watch fires on the first of those. Read before the move, the file
     * still holds the deck opened on: ours, not somebody else's.
     */
    @Test
    fun theOpenedDeckReadBackMidWriteKeepsTheNotesEditOpen() = runTest {
        val opened: Document = document()
        val changes = watched()
        val viewModel = editor(opened, changes)
        changes.awaitWatched()
        viewModel.onBeginNotesEdit()
        viewModel.onEditNotes("First")
        viewModel.await { it.selectedSlide.notes == "First" && it.savePending }

        changes.emit(BundleChange.Deck(opened.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertTrue(after.isEditingNotes)
        assertEquals("First", after.selectedSlide.notes)
    }

    /**
     * The same early read on a later write: the file still holds the write heard
     * before it, which is ours too.
     */
    @Test
    fun theLastHeardWriteReadBackMidWriteIsIgnored() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onBeginNotesEdit()
        viewModel.onEditNotes("First")
        viewModel.await { it.selectedSlide.notes == "First" && it.savePending }
        val first: Document = viewModel.await { !it.savePending }.document
        changes.emit(BundleChange.Deck(first.roundTripped()))
        viewModel.onEditNotes("First, second")
        viewModel.await { it.selectedSlide.notes == "First, second" && it.savePending }

        changes.emit(BundleChange.Deck(first.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertTrue(after.isEditingNotes)
        assertEquals("First, second", after.selectedSlide.notes)
        assertTrue(after.savePending)
    }

    /**
     * Autosave restarts on the next keystroke, and a write under way when it does
     * still reaches the file. The watcher hands that write back, often before
     * the store has returned: ours, with the typing since it kept.
     */
    @Test
    fun aWriteStillUnderWayIsOursWhenItComesBack() = runTest {
        val changes = watched()
        val store = HeldStore(document())
        val viewModel = editor(document(), changes, store.store)
        changes.awaitWatched()
        viewModel.onBeginNotesEdit()
        viewModel.onEditNotes("First")
        val written: Document = store.awaitWrite()
        viewModel.onEditNotes("First, second")
        viewModel.await { it.selectedSlide.notes == "First, second" }

        changes.emit(BundleChange.Deck(written.roundTripped()))
        changes.emit(BundleChange.Assets)

        val after = viewModel.await { it.assetGeneration == 1 }
        assertTrue(after.isEditingNotes)
        assertEquals("First, second", after.selectedSlide.notes)
        assertTrue(after.savePending)
    }

    /**
     * Once a write has been heard back, the file is past it: the same deck
     * turning up again is somebody putting it back, a checkout say.
     */
    @Test
    fun aWriteThatWasHeardIsNewsWhenItComesBackAgain() = runTest {
        val changes = watched()
        val viewModel = editor(document(), changes)
        changes.awaitWatched()
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "First"))
        viewModel.await { it.selectedSlide.title == "First" && it.savePending }
        val first: Document = viewModel.await { !it.savePending }.document
        changes.emit(BundleChange.Deck(first.roundTripped()))
        viewModel.onUpdateSlide(viewModel.states.value.selectedSlide.copy(title = "Second"))
        viewModel.await { it.selectedSlide.title == "Second" && it.savePending }
        val second: Document = viewModel.await { !it.savePending }.document
        changes.emit(BundleChange.Deck(second.roundTripped()))

        changes.emit(BundleChange.Deck(first.roundTripped()))

        val reloaded = viewModel.await { it.document == first }
        assertEquals("First", reloaded.selectedSlide.title)
        assertFalse(reloaded.canUndo)
    }
}

/**
 * A store whose write never finishes: what is handed to it is the write under
 * way, the way a file half written is.
 */
private class HeldStore(initial: Document) {
    private val writing = CompletableDeferred<Document>()
    private val codec = object : Codec<Document> {
        private var stored: Document? = initial
        override suspend fun encode(value: Document?) {
            writing.complete(value!!)
            awaitCancellation()
        }

        override suspend fun decode(): Document? = stored
    }
    val store: KStore<Document> = storeOf(codec = codec, default = initial)

    suspend fun awaitWrite(): Document = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { writing.await() }
    }
}
