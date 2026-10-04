package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ImageFill
import io.github.xxfast.cupboard.document.ImageFillMode
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeGradient
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.withFillMode
import io.github.xxfast.cupboard.document.withImageFill
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pictures through the loop: no events of their own, only the element and slide
 * updates every other fill and background already goes through, so each one is
 * one undo entry like the rest.
 */
class EditorImageFillTest {
    private val box = Frame(0f, 0f, 100f, 100f)
    private val gradient = ShapeGradient(start = 0xFF000000, end = 0xFFFFFFFF)

    private fun document(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(
                id = "one",
                title = "One",
                elements = listOf(
                    ShapeElement(id = "a", frame = box, gradient = gradient),
                    ShapeElement(id = "b", frame = box),
                ),
            ),
        ),
    )

    private fun EditorState.shape(id: String): ShapeElement =
        selectedSlide.elements.first { it.id == id } as ShapeElement

    @Test
    fun fillingShapesWithAPictureIsOneUndo() = runTest {
        val viewModel = editor(document())
        val shapes: List<ShapeElement> = listOf("a", "b").map { viewModel.states.value.shape(it) }

        viewModel.onUpdateElements(shapes.map { it.withImageFill("photo.png") })
        val filled = viewModel.await { it.shape("b").imageFill != null }

        assertEquals(ImageFill("photo.png"), filled.shape("a").imageFill)
        assertEquals(gradient, filled.shape("a").gradient, "the gradient stays stored under it")
        assertTrue(filled.canUndo)

        viewModel.onUndo()
        val back = viewModel.await { !it.canUndo }
        assertEquals(document().slides, back.document.slides)
    }

    @Test
    fun changingTheModeIsAnEditOfItsOwn() = runTest {
        val viewModel = editor(document())
        val shape: ShapeElement = viewModel.states.value.shape("b").withImageFill("photo.png")

        viewModel.onUpdateElements(listOf(shape))
        viewModel.await { it.shape("b").imageFill != null }
        viewModel.onUpdateElements(listOf(shape.withFillMode(ImageFillMode.Tile)))
        viewModel.await { it.shape("b").imageFill?.mode == ImageFillMode.Tile }

        viewModel.onUndo()
        val undone = viewModel.await { it.shape("b").imageFill?.mode == ImageFillMode.ScaleToFill }
        assertEquals("photo.png", undone.shape("b").imageFill?.assetId)
    }

    @Test
    fun aSlideAndTheDeckTakeAPictureThroughTheirOwnEdits() = runTest {
        val viewModel = editor(document())
        val slide: Slide = viewModel.states.value.selectedSlide
        val picture = SlideBackground.Image("slide.png", ImageFillMode.ScaleToFit)

        viewModel.onUpdateSlide(slide.copy(background = picture))
        viewModel.await { it.selectedSlide.background == picture }

        viewModel.onSetDocumentBackground(SlideBackground.Image("deck.png"))
        viewModel.await { it.document.background == SlideBackground.Image("deck.png") }

        viewModel.onUndo()
        val deckUndone = viewModel.await { it.document.background == null }
        assertEquals(picture, deckUndone.selectedSlide.background)

        viewModel.onUndo()
        val slideUndone = viewModel.await { !it.canUndo }
        assertNull(slideUndone.selectedSlide.background)
    }

    @Test
    fun aPastedStyleLeavesEachShapesPictureAlone() = runTest {
        val viewModel = editor(document())
        val a: ShapeElement = viewModel.states.value.shape("a").withImageFill("photo.png")

        viewModel.onUpdateElements(listOf(a))
        viewModel.await { it.shape("a").imageFill != null }

        viewModel.onCopyStyle("a")
        viewModel.await { it.canPasteStyle }
        viewModel.onPasteStyle(listOf("b"))
        val pasted = viewModel.await { it.shape("b").gradient == gradient }

        assertNull(pasted.shape("b").imageFill)
    }
}
