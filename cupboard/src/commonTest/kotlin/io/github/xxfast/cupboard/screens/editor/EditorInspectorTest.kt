package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Arrange
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Code
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Diagram
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Equation
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Gallery
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Image
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Style
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Terminal
import io.github.xxfast.cupboard.screens.editor.FormatSegment.Text
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The inspector's chrome: which Format segments a selection offers, what the
 * remembered segment falls back to, and the section disclosures. None of it
 * touches the document, which half these tests are about.
 *
 * One slide holding one of every kind, in a known z-order: "shape" is backmost
 * and "audio" frontmost, which is what the Arrange enablement reads.
 */
class EditorInspectorTest {
    // Fixed id, so a freshly built document compares equal to the opened one.
    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "slide",
                title = "Everything",
                elements = listOf(
                    ShapeElement(id = "shape", frame = Frame(0f, 0f, 100f, 100f)),
                    ShapeElement(id = "line", frame = Frame(0f, 0f, 100f, 1f), kind = ShapeKind.Line),
                    TextElement(id = "text", frame = Frame(0f, 0f, 100f, 100f)),
                    ImageElement(id = "image", frame = Frame(0f, 0f, 100f, 100f)),
                    CodeElement(id = "code", frame = Frame(0f, 0f, 100f, 100f)),
                    TerminalElement(id = "terminal", frame = Frame(0f, 0f, 100f, 100f)),
                    DiagramElement(id = "diagram", frame = Frame(0f, 0f, 100f, 100f)),
                    EquationElement(id = "equation", frame = Frame(0f, 0f, 100f, 100f)),
                    GalleryElement(id = "gallery", frame = Frame(0f, 0f, 100f, 100f)),
                    GroupElement(id = "group", frame = Frame(0f, 0f, 100f, 100f)),
                    VideoElement(id = "video", frame = Frame(0f, 0f, 100f, 100f)),
                    AudioElement(id = "audio", frame = Frame(0f, 0f, 100f, 100f)),
                ),
            ),
        ),
    )

    private suspend fun EditorViewModel.select(vararg ids: String): EditorState {
        onSelectElements(ids.toList())
        return await { it.selectedElementIds == ids.toList() }
    }

    @Test
    fun everyKindOffersItsOwnSegments() = runTest {
        val viewModel = editor(document())

        // Nothing selected offers none: the shells show slide formatting instead.
        assertEquals(emptyList(), viewModel.states.value.formatSegments)
        assertNull(viewModel.states.value.activeFormatSegment)

        val expected: List<Pair<String, List<FormatSegment>>> = listOf(
            "shape" to listOf(Style, Text, Arrange),
            "line" to listOf(Style, Arrange),
            "text" to listOf(Style, Text, Arrange),
            "image" to listOf(Style, Image, Arrange),
            "code" to listOf(Style, Code, Arrange),
            "terminal" to listOf(Style, Terminal, Arrange),
            "diagram" to listOf(Style, Diagram, Arrange),
            "equation" to listOf(Style, Equation, Arrange),
            "gallery" to listOf(Style, Gallery, Arrange),
            "group" to listOf(Style, Arrange),
            "video" to listOf(Style, Arrange),
            "audio" to listOf(Style, Arrange),
        )

        for ((id, segments) in expected) {
            assertEquals(segments, viewModel.select(id).formatSegments, id)
        }
    }

    @Test
    fun multiSelectionKeepsWhatEveryMemberOffers() = runTest {
        val viewModel = editor(document())

        // Two of a kind agree, so their segments stand.
        assertEquals(listOf(Style, Text, Arrange), viewModel.select("shape", "text").formatSegments)

        // Kinds that disagree fall back to what they share.
        assertEquals(listOf(Style, Arrange), viewModel.select("text", "image").formatSegments)
        assertEquals(listOf(Style, Arrange), viewModel.select("shape", "line").formatSegments)
    }

    @Test
    fun theSegmentIsRememberedAcrossSelectionsThatLackIt() = runTest {
        val viewModel = editor(document())
        viewModel.select("text")

        viewModel.onSelectFormatSegment(Text)
        assertEquals(Text, viewModel.await { it.formatSegment == Text }.activeFormatSegment)

        // An image has no Text segment: the panel falls back, the preference doesn't move.
        val onImage = viewModel.select("image")
        assertEquals(Text, onImage.formatSegment)
        assertEquals(Style, onImage.activeFormatSegment)

        // Back on something that offers it, and it is showing again.
        assertEquals(Text, viewModel.select("text").activeFormatSegment)
    }

    @Test
    fun aSegmentThisSelectionDoesNotOfferIsIgnored() = runTest {
        val viewModel = editor(document())
        viewModel.select("image")

        viewModel.onSelectFormatSegment(Text)
        // A later event through the same flow, to wait on: the ignored one
        // leaves nothing of its own to await.
        viewModel.onToggleInspectorSection(InspectorSection.Fill)
        val state = viewModel.await { InspectorSection.Fill in it.expandedSections }

        assertEquals(Style, state.formatSegment)
        assertEquals(Style, state.activeFormatSegment)
    }

    @Test
    fun sectionsStayOpenAcrossSelections() = runTest {
        val viewModel = editor(document())
        viewModel.select("shape")

        viewModel.onToggleInspectorSection(InspectorSection.Fill)
        viewModel.onToggleInspectorSection(InspectorSection.Border)
        viewModel.await { it.expandedSections == setOf(InspectorSection.Fill, InspectorSection.Border) }

        val onText = viewModel.select("text")
        assertEquals(setOf(InspectorSection.Fill, InspectorSection.Border), onText.expandedSections)

        viewModel.onToggleInspectorSection(InspectorSection.Fill)
        val closed = viewModel.await { InspectorSection.Fill !in it.expandedSections }
        assertEquals(setOf(InspectorSection.Border), closed.expandedSections)
    }

    @Test
    fun animateSegmentIsPickedOutright() = runTest {
        val viewModel = editor(document())
        assertEquals(AnimateSegment.BuildIn, viewModel.states.value.animateSegment)

        viewModel.onSelectAnimateSegment(AnimateSegment.Action)
        assertEquals(AnimateSegment.Action, viewModel.await { it.animateSegment == AnimateSegment.Action }.animateSegment)

        // Remembered across selections like the rest of the chrome.
        assertEquals(AnimateSegment.Action, viewModel.select("shape").animateSegment)
    }

    @Test
    fun noneOfTheChromeEventsEditsTheDocument() = runTest {
        val viewModel = editor(document())
        viewModel.select("shape")

        viewModel.onSelectFormatSegment(Arrange)
        viewModel.onSelectAnimateSegment(AnimateSegment.BuildOut)
        viewModel.onToggleInspectorSection(InspectorSection.Shadow)
        val state = viewModel.await { InspectorSection.Shadow in it.expandedSections }

        assertEquals(Arrange, state.formatSegment)
        assertEquals(AnimateSegment.BuildOut, state.animateSegment)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertFalse(state.savePending)
        assertEquals(document(), state.document)

        // The inspector is neither opened nor closed by any of them.
        viewModel.onCloseInspector()
        val hidden = viewModel.await { !it.inspectorOpen }
        viewModel.onSelectFormatSegment(Style)
        viewModel.onToggleInspectorSection(InspectorSection.Shadow)
        assertFalse(viewModel.await { InspectorSection.Shadow !in it.expandedSections }.inspectorOpen)
        assertFalse(hidden.inspectorOpen)
    }

    @Test
    fun zOrderEnablementFollowsTheSelectionsPlaceInTheStack() = runTest {
        val viewModel = editor(document())

        // Nothing selected has nowhere to go.
        assertFalse(viewModel.states.value.canBringForward)
        assertFalse(viewModel.states.value.canSendBackward)

        val backmost = viewModel.select("shape")
        assertTrue(backmost.canBringForward)
        assertFalse(backmost.canSendBackward)

        val frontmost = viewModel.select("audio")
        assertFalse(frontmost.canBringForward)
        assertTrue(frontmost.canSendBackward)

        val middle = viewModel.select("image")
        assertTrue(middle.canBringForward)
        assertTrue(middle.canSendBackward)

        // The whole stack is already where it is, either way.
        val all = viewModel.select(*document().slides.single().elements.map { it.id }.toTypedArray())
        assertFalse(all.canBringForward)
        assertFalse(all.canSendBackward)
    }

    @Test
    fun lockedElementsHaveNowhereToGo() = runTest {
        val viewModel = editor(document())
        viewModel.onSetElementsLocked(listOf("shape"), true)
        viewModel.await { it.selectedSlide.elements.first().locked }

        val locked = viewModel.select("shape")
        assertFalse(locked.canBringForward)
        assertFalse(locked.canSendBackward)
    }
}
