package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Guide
import io.github.xxfast.cupboard.document.GuideAxis
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.editor.SnapLine
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rulers, user guides and the snap settings, driven the way a shell drives them.
 * Two elements far apart, so an object snap line is never also a slide one.
 */
class EditorGuidesTest {
    // Fixed ids, so a freshly built document compares equal to the opened one.
    private fun document(guides: List<Guide> = emptyList()): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "slide",
                title = "Guides",
                elements = listOf(
                    ShapeElement(id = "a", frame = Frame(100f, 100f, 200f, 100f)),
                    ShapeElement(id = "b", frame = Frame(600f, 400f, 200f, 100f)),
                ),
            ),
        ),
        guides = guides,
    )

    /** Whether the list offers that line, floats compared the way floats must be. */
    private fun List<SnapLine>.offers(axis: GuideAxis, position: Float, kind: SnapKind): Boolean =
        any { it.axis == axis && it.kind == kind && abs(it.position - position) < 0.01f }

    @Test
    fun rulersAndGuidesAreViewToggles() = runTest {
        val editor = editor(document())
        val opened: EditorState = editor.await { true }
        assertFalse(opened.showRulers)
        assertTrue(opened.showGuides)

        editor.onToggleRulers()
        assertTrue(editor.await { it.showRulers }.showRulers)

        editor.onToggleGuides()
        assertFalse(editor.await { !it.showGuides }.showGuides)
    }

    @Test
    fun eachSnapRuleTurnsOffOnItsOwn() = runTest {
        val editor = editor(document())
        editor.onSetSnap(SnapKind.Objects, false)

        val state: EditorState = editor.await { !it.snapToObjects }
        assertTrue(state.snapToCenter)
        assertTrue(state.snapToEdges)
        assertTrue(state.snapToGuides)
    }

    @Test
    fun previewingAGuideDrawsItWithoutTouchingTheDocument() = runTest {
        val editor = editor(document())
        editor.onPreviewGuide(null, GuideAxis.Vertical, 480f)

        val state: EditorState = editor.await { it.guideDrag != null }
        assertEquals(GuideDrag(null, GuideAxis.Vertical, 480f), state.guideDrag)
        assertTrue(state.document.guides.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun committingANewGuideAddsItAsOneUndoStep() = runTest {
        val editor = editor(document())
        editor.onPreviewGuide(null, GuideAxis.Vertical, 300f)
        editor.await { it.guideDrag != null }
        editor.onPreviewGuide(null, GuideAxis.Vertical, 480f)
        editor.onCommitGuide(null, GuideAxis.Vertical, 480f)

        val added: EditorState = editor.await { it.document.guides.isNotEmpty() }
        val guide: Guide = added.document.guides.single()
        assertEquals(GuideAxis.Vertical, guide.axis)
        assertEquals(480f, guide.position)
        assertNull(added.guideDrag)
        assertTrue(added.canUndo)

        // One entry for the whole drag, previews included: undo empties the deck
        // of guides rather than stepping back through the samples.
        editor.onUndo()
        val undone: EditorState = editor.await { it.document.guides.isEmpty() }
        assertFalse(undone.canUndo)
    }

    @Test
    fun committingAnIdMovesThatGuide() = runTest {
        val editor = editor(document(listOf(Guide("g", GuideAxis.Horizontal, 200f))))
        editor.onCommitGuide("g", GuideAxis.Horizontal, 540f)

        val moved: EditorState = editor.await { it.document.guides.single().position == 540f }
        assertEquals("g", moved.document.guides.single().id)
        assertNull(moved.guideDrag)
    }

    @Test
    fun removingAGuideTakesItAwayAndUndoBringsItBack() = runTest {
        val editor = editor(document(listOf(Guide("g", GuideAxis.Vertical, 960f))))
        editor.onPreviewGuide("g", GuideAxis.Vertical, 2400f)
        editor.await { it.guideDrag != null }
        editor.onRemoveGuide("g")

        val gone: EditorState = editor.await { it.document.guides.isEmpty() }
        assertNull(gone.guideDrag)
        assertTrue(gone.canUndo)

        editor.onUndo()
        val back: EditorState = editor.await { it.document.guides.isNotEmpty() }
        assertEquals("g", back.document.guides.single().id)
    }

    @Test
    fun endingAGuideDragCommitsNothing() = runTest {
        val editor = editor(document())
        editor.onPreviewGuide(null, GuideAxis.Horizontal, 540f)
        editor.await { it.guideDrag != null }
        editor.onEndGuideDrag()

        val ended: EditorState = editor.await { it.guideDrag == null }
        assertTrue(ended.document.guides.isEmpty())
        assertFalse(ended.canUndo)
    }

    @Test
    fun snapTargetsFollowTheSettingsAndSkipWhatIsBeingDragged() {
        val state = EditorState(
            document = document(listOf(Guide("g", GuideAxis.Vertical, 500f))),
            selectedSlideId = "slide",
        )
        val lines: List<SnapLine> = state.snapTargets(exclude = setOf("a"))

        // The slide's own layout guides: its center lines and its 5% margins.
        assertTrue(lines.offers(GuideAxis.Vertical, 960f, SnapKind.Center))
        assertTrue(lines.offers(GuideAxis.Horizontal, 540f, SnapKind.Center))
        assertTrue(lines.offers(GuideAxis.Vertical, 0f, SnapKind.Edges))
        assertTrue(lines.offers(GuideAxis.Vertical, 96f, SnapKind.Edges))
        assertTrue(lines.offers(GuideAxis.Horizontal, 1026f, SnapKind.Edges))

        // The element left behind offers all six of its lines; the dragged one
        // offers none, or the selection would stick to where it started.
        assertTrue(lines.offers(GuideAxis.Vertical, 700f, SnapKind.Objects))
        assertTrue(lines.offers(GuideAxis.Horizontal, 450f, SnapKind.Objects))
        assertFalse(lines.offers(GuideAxis.Vertical, 200f, SnapKind.Objects))

        assertTrue(lines.offers(GuideAxis.Vertical, 500f, SnapKind.Guides))

        assertTrue(state.copy(snapToCenter = false).snapTargets().none { it.kind == SnapKind.Center })
        assertTrue(state.copy(snapToEdges = false).snapTargets().none { it.kind == SnapKind.Edges })
        assertTrue(
            state.copy(snapToObjects = false).snapTargets().none { it.kind == SnapKind.Objects },
        )
        assertTrue(state.copy(snapToGuides = false).snapTargets().none { it.kind == SnapKind.Guides })
    }
}
