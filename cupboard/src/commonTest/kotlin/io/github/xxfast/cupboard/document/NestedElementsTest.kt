package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The slide helpers that reach into groups: a group's child is updated, deleted,
 * reordered and moved where it sits rather than through its group.
 */
class NestedElementsTest {
    private fun shape(id: String, x: Float, y: Float = 0f, locked: Boolean = false): ShapeElement =
        ShapeElement(id = id, frame = Frame(x, y, 100f, 100f), locked = locked)

    /**
     * "under" at the back, then a group of three side by side, then "over" on
     * top. The group's frame is its children's bounds, the way grouping fits it.
     */
    private fun slide(group: GroupElement = group()): Slide = Slide(
        id = "slide",
        elements = listOf(shape("under", 0f, 300f), group, shape("over", 0f, 500f)),
    )

    private fun group(
        children: List<Element> = listOf(shape("a", 0f), shape("b", 200f), shape("c", 400f)),
        locked: Boolean = false,
    ): GroupElement = GroupElement(
        id = "group",
        frame = boundingFrame(children.map { it.drawnBounds() }),
        locked = locked,
        children = children,
    )

    private fun Slide.ids(): List<String> = elements.map { it.id }

    private fun Slide.childIds(id: String = "group"): List<String> =
        (elementById(id) as GroupElement).children.map { it.id }

    @Test
    fun ancestorsAreListedOutermostFirst() {
        val inner = GroupElement(
            id = "inner",
            frame = Frame(0f, 0f, 100f, 100f),
            children = listOf(shape("a", 0f)),
        )
        val slide = slide(group(children = listOf(inner, shape("b", 200f))))

        assertEquals(listOf("group", "inner"), slide.ancestorIds("a"))
        assertEquals(listOf("group"), slide.ancestorIds("inner"))
        assertEquals(emptyList(), slide.ancestorIds("over"))
        assertEquals(emptyList(), slide.ancestorIds("gone"))
    }

    @Test
    fun aLockedGroupLocksEverythingInIt() {
        val slide = slide(group(locked = true))

        assertTrue(slide.isLocked("group"))
        assertTrue(slide.isLocked("a"))
        assertFalse(slide.isLocked("over"))
        assertFalse(slide.isLocked("gone"))
        assertTrue(slide(group(children = listOf(shape("a", 0f, locked = true)))).isLocked("a"))
    }

    @Test
    fun movingAChildRefitsItsGroup() {
        val slide = slide()
        val moved: Element = slide.elementById("c")!!.update(frame = Frame(400f, 200f, 100f, 100f))

        val updated = slide.updateElement(moved)
        assertEquals(Frame(400f, 200f, 100f, 100f), updated.elementById("c")!!.frame)
        assertEquals(Frame(0f, 0f, 500f, 300f), updated.elementById("group")!!.frame)
        // The other children stay where they were: the group follows them, not
        // the other way around.
        assertEquals(Frame(0f, 0f, 100f, 100f), updated.elementById("a")!!.frame)
    }

    @Test
    fun renamingAChildLeavesItsGroupAlone() {
        val slide = slide(group().copy(frame = Frame(-10f, -10f, 520f, 120f)))

        val renamed = slide.updateElement(slide.elementById("b")!!.update(name = "Middle"))
        assertEquals("Middle", renamed.elementById("b")!!.name)
        // Not refitted, even though the frame isn't quite the children's bounds.
        assertEquals(Frame(-10f, -10f, 520f, 120f), renamed.elementById("group")!!.frame)
        assertTrue(slide === slide.updateElement(shape("gone", 0f)))
    }

    @Test
    fun deletingAChildRefitsTheGroupAroundWhatIsLeft() {
        val slide = slide().copy(builds = listOf(Build(elementId = "c"), Build(elementId = "a")))

        val trimmed = slide.removeElements(setOf("c"))
        assertEquals(listOf("a", "b"), trimmed.childIds())
        assertEquals(Frame(0f, 0f, 300f, 100f), trimmed.elementById("group")!!.frame)
        assertEquals(listOf("a"), trimmed.builds.map { it.elementId })
    }

    @Test
    fun aGroupLeftWithOneChildGivesWayToItWithItsTransformBakedIn() {
        // Bounds (0,0,300,100), center x 150: flipped, "a" (center 50) lands at 250.
        val flipped = group(children = listOf(shape("a", 0f), shape("b", 200f)))
            .copy(flippedHorizontally = true, opacity = 0.5f)
        val slide = slide(flipped)
            .copy(builds = listOf(Build(elementId = "group"), Build(elementId = "b")))

        val trimmed = slide.removeElements(setOf("b"))
        assertEquals(listOf("under", "a", "over"), trimmed.ids())

        val survivor: Element = trimmed.elementById("a")!!
        assertEquals(Frame(200f, 0f, 100f, 100f), survivor.frame)
        assertTrue(survivor.flippedHorizontally)
        assertEquals(0.5f, survivor.opacity)
        // The group's build goes with the group, the child's with the child.
        assertEquals(emptyList(), trimmed.builds)
    }

    @Test
    fun aGroupLeftEmptyGoes() {
        val slide = slide(group(children = listOf(shape("a", 0f), shape("b", 200f))))

        assertEquals(listOf("under", "over"), slide.removeElements(setOf("a", "b")).ids())
        assertTrue(slide === slide.removeElements(setOf("gone")))
    }

    @Test
    fun reorderingAChildStaysInsideItsGroup() {
        val slide = slide()

        val reordered = slide.reorderElements(listOf("a"), ZOrderMove.ToFront)
        assertEquals(listOf("b", "c", "a"), reordered.childIds())
        assertEquals(listOf("under", "group", "over"), reordered.ids())
        assertTrue(slide === slide.reorderElements(listOf("c"), ZOrderMove.Forward))
    }

    @Test
    fun idsUnderDifferentParentsEachMoveWithinTheirOwn() {
        val reordered = slide().reorderElements(listOf("under", "c"), ZOrderMove.ToBack)

        assertEquals(listOf("c", "a", "b"), reordered.childIds())
        // Already at the back of its own list.
        assertEquals(listOf("under", "group", "over"), reordered.ids())
    }

    @Test
    fun movingAnElementPutsItInFrontOfTheOneNamed() {
        val slide = slide()

        assertEquals(listOf("group", "over", "under"), slide.moveElement("under", "over").ids())
        assertEquals(listOf("over", "under", "group"), slide.moveElement("over", null).ids())
        assertEquals(listOf("b", "a", "c"), slide.moveElement("a", "b").childIds())
        assertEquals(listOf("c", "a", "b"), slide.moveElement("c", null).childIds())
    }

    @Test
    fun aMoveThatCantOrDoesntChangeAnythingIsTheSameSlide() {
        val slide = slide()

        // Not siblings: in or out of a group is not a reorder.
        assertTrue(slide === slide.moveElement("a", "over"))
        assertTrue(slide === slide.moveElement("over", "a"))
        assertTrue(slide === slide.moveElement("a", "a"))
        assertTrue(slide === slide.moveElement("a", "gone"))
        assertTrue(slide === slide.moveElement("gone", null))
        // Already directly in front of "a", and already at the back.
        assertTrue(slide === slide.moveElement("b", "a"))
        assertTrue(slide === slide.moveElement("a", null))

        val locked = slide(group(locked = true))
        assertTrue(locked === locked.moveElement("a", "b"))
        assertTrue(locked === locked.moveElement("group", "over"))
    }

    @Test
    fun aNestedGroupComesApartWithinItsParent() {
        val inner = GroupElement(
            id = "inner",
            frame = Frame(200f, 0f, 300f, 100f),
            children = listOf(shape("b", 200f), shape("c", 400f)),
        )
        val slide = slide(group(children = listOf(shape("a", 0f), inner)))

        val ungrouped = slide.ungroupElement("inner")
        assertEquals(listOf("a", "b", "c"), ungrouped.childIds())
        assertEquals(listOf("under", "group", "over"), ungrouped.ids())
    }
}
