package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentTest {
    @Test
    fun serializationRoundTripsTheSampleDocument() {
        val document = sampleDocument()
        val decoded = decodeDocument(document.encodeToString())
        assertEquals(document, decoded)
    }

    @Test
    fun serializationRoundTripsDepthAndCollapsed() {
        val document = Document(
            slides = listOf(
                Slide(title = "Parent", collapsed = true),
                Slide(title = "Child", depth = 1),
            ),
        )
        val decoded = decodeDocument(document.encodeToString())
        assertEquals(document, decoded)
        assertTrue(decoded.slides[0].collapsed)
        assertEquals(1, decoded.slides[1].depth)
    }

    /**
     * The transform fields arrived after the first documents were written, so a
     * file without them has to keep opening. Hand-written JSON on purpose: a
     * round trip through the current encoder would write the fields and prove
     * nothing about the files already on disk.
     */
    @Test
    fun anElementWrittenBeforeTheTransformFieldsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "name": "Old",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "shape",
                      "id": "shape",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val element = decodeDocument(json).slides.single().elements.single()
        assertEquals(1f, element.opacity)
        assertEquals(0f, element.rotation)
        assertFalse(element.flippedHorizontally)
        assertFalse(element.flippedVertically)
        assertFalse(element.locked)
    }

    @Test
    fun updateElementReplacesByIdAndIgnoresAnUnknownOne() {
        val slide = Slide(
            elements = listOf(
                ShapeElement(id = "a", frame = Frame(0f, 0f, 10f, 10f)),
                ShapeElement(id = "b", frame = Frame(0f, 0f, 10f, 10f)),
            ),
        )
        val updated = slide.updateElement(slide.elements[1].update(locked = true))
        assertFalse(updated.elements[0].locked)
        assertTrue(updated.elements[1].locked)

        val untouched = slide.updateElement(ShapeElement(id = "gone", frame = Frame(0f, 0f, 1f, 1f)))
        assertEquals(slide.elements, untouched.elements)
    }

    @Test
    fun reorderElementClampsAndReturnsTheSameSlideWhenNothingMoves() {
        val slide = Slide(
            elements = listOf(
                ShapeElement(id = "a", frame = Frame(0f, 0f, 10f, 10f)),
                ShapeElement(id = "b", frame = Frame(0f, 0f, 10f, 10f)),
                ShapeElement(id = "c", frame = Frame(0f, 0f, 10f, 10f)),
            ),
        )
        fun Slide.ids(): List<String> = elements.map { it.id }

        assertEquals(listOf("b", "a", "c"), slide.reorderElement("a", ZOrderMove.Forward).ids())
        assertEquals(listOf("a", "c", "b"), slide.reorderElement("c", ZOrderMove.Backward).ids())
        assertEquals(listOf("b", "c", "a"), slide.reorderElement("a", ZOrderMove.ToFront).ids())
        assertEquals(listOf("c", "a", "b"), slide.reorderElement("c", ZOrderMove.ToBack).ids())

        // Identity is the signal callers use to skip the history entry.
        assertTrue(slide === slide.reorderElement("a", ZOrderMove.Backward))
        assertTrue(slide === slide.reorderElement("a", ZOrderMove.ToBack))
        assertTrue(slide === slide.reorderElement("c", ZOrderMove.Forward))
        assertTrue(slide === slide.reorderElement("c", ZOrderMove.ToFront))
        assertTrue(slide === slide.reorderElement("gone", ZOrderMove.ToFront))
    }

    private fun slideOf(vararg elements: Element): Slide = Slide(elements = elements.toList())

    private fun Slide.ids(): List<String> = elements.map { it.id }

    private fun shape(id: String, x: Float, y: Float, size: Float = 100f): ShapeElement =
        ShapeElement(id = id, frame = Frame(x, y, size, size))

    @Test
    fun updateElementsReplacesEveryIdItCarries() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f), shape("c", 400f, 0f))
        val updated = slide.updateElements(
            listOf(
                slide.elements[0].update(opacity = 0.5f),
                slide.elements[2].update(opacity = 0.25f),
                ShapeElement(id = "gone", frame = Frame(0f, 0f, 1f, 1f)),
            ),
        )
        assertEquals(listOf("a", "b", "c"), updated.ids())
        assertEquals(0.5f, updated.elements[0].opacity)
        assertEquals(1f, updated.elements[1].opacity)
        assertEquals(0.25f, updated.elements[2].opacity)
    }

    @Test
    fun groupingLandsAtTheTopmostMemberAndKeepsChildOrder() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f), shape("c", 400f, 0f))
        // Grouped bottom-up on purpose: children come back in z-order, not in the
        // order the ids were passed.
        val grouped = slide.groupElements(listOf("c", "a"), groupId = "group")
        assertEquals(listOf("b", "group"), grouped.ids())

        val group = grouped.elements.last() as GroupElement
        assertEquals(listOf("a", "c"), group.children.map { it.id })
        assertEquals(Frame(0f, 0f, 500f, 100f), group.frame)
    }

    @Test
    fun groupingBoxesRotatedMembersWhereTheyAreDrawn() {
        // 200x100 at (200,0) rotated 90 draws as 100x200 around center (300,50):
        // x 250..350, y -50..150. The group frame hugs that, not the raw frame.
        val rotated = shape("b", 200f, 0f)
            .update(frame = Frame(200f, 0f, 200f, 100f), rotation = 90f)
        val slide = slideOf(shape("a", 0f, 0f)).copy(elements = listOf(shape("a", 0f, 0f), rotated))

        val group = slide.groupElements(listOf("a", "b"), groupId = "group")
            .elements.single() as GroupElement
        assertEquals(0f, group.frame.x, absoluteTolerance = 0.001f)
        assertEquals(-50f, group.frame.y, absoluteTolerance = 0.001f)
        assertEquals(350f, group.frame.width, absoluteTolerance = 0.001f)
        assertEquals(200f, group.frame.height, absoluteTolerance = 0.001f)
    }

    @Test
    fun groupingNeedsTwoUnlockedMembers() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f))
        assertTrue(slide === slide.groupElements(listOf("a")))
        assertTrue(slide === slide.groupElements(listOf("a", "gone")))
        assertTrue(slide === slide.groupElements(emptyList()))

        // A locked member is left where it is, which leaves nothing to group.
        val locked = slide.updateElement(slide.elements[1].update(locked = true))
        assertTrue(locked === locked.groupElements(listOf("a", "b")))
    }

    @Test
    fun aMovedGroupMovesItsChildrenWithIt() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f))
            .groupElements(listOf("a", "b"), groupId = "group")
        val group = slide.elements.single() as GroupElement

        val moved = group.update(frame = group.frame.translate(50f, 30f)) as GroupElement
        assertEquals(Frame(50f, 30f, 100f, 100f), moved.children[0].frame)
        assertEquals(Frame(250f, 30f, 100f, 100f), moved.children[1].frame)
    }

    @Test
    fun aResizedGroupScalesItsChildren() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f))
            .groupElements(listOf("a", "b"), groupId = "group")
        val group = slide.elements.single() as GroupElement
        assertEquals(Frame(0f, 0f, 300f, 100f), group.frame)

        val resized = group.update(frame = Frame(0f, 0f, 600f, 50f)) as GroupElement
        assertEquals(Frame(0f, 0f, 200f, 50f), resized.children[0].frame)
        assertEquals(Frame(400f, 0f, 200f, 50f), resized.children[1].frame)
    }

    @Test
    fun ungroupingSplicesTheChildrenBackWhereTheGroupSat() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f), shape("c", 400f, 0f))
            .groupElements(listOf("a", "b"), groupId = "group")
        assertEquals(listOf("group", "c"), slide.ids())

        val ungrouped = slide.ungroupElement("group")
        assertEquals(listOf("a", "b", "c"), ungrouped.ids())
        assertEquals(Frame(0f, 0f, 100f, 100f), ungrouped.elements[0].frame)

        assertTrue(slide === slide.ungroupElement("c"))
        assertTrue(slide === slide.ungroupElement("gone"))
    }

    @Test
    fun ungroupingBakesTheGroupsTransformsIntoTheChildren() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f))
            .groupElements(listOf("a", "b"), groupId = "group")
        val group = slide.elements.single() as GroupElement
        // Bounds (0,0,300,100), center (150,50). Turned a quarter turn, child "a"
        // (center (50,50), i.e. 100 to the left of the group's center) swings to
        // 100 above it.
        val turned = group.update(rotation = 90f, opacity = 0.5f)

        val freed = slide.updateElement(turned).ungroupElement("group").elements
        assertEquals(90f, freed[0].rotation)
        assertEquals(0.5f, freed[0].opacity)
        assertEquals(150f, freed[0].frame.centerX, 0.001f)
        assertEquals(-50f, freed[0].frame.centerY, 0.001f)
        assertEquals(150f, freed[1].frame.centerX, 0.001f)
        assertEquals(150f, freed[1].frame.centerY, 0.001f)
    }

    @Test
    fun aFlippedGroupMirrorsItsChildrenAcrossItsCenter() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f))
            .groupElements(listOf("a", "b"), groupId = "group")
        val group = slide.elements.single() as GroupElement
        val flipped = group.update(flippedHorizontally = true)

        val freed = slide.updateElement(flipped).ungroupElement("group").elements
        assertEquals(250f, freed[0].frame.centerX, 0.001f)
        assertEquals(50f, freed[0].frame.centerY, 0.001f)
        assertTrue(freed[0].flippedHorizontally)
        assertEquals(50f, freed[1].frame.centerX, 0.001f)
    }

    @Test
    fun groupsNestAndMoveThroughEachOther() {
        val inner = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f), shape("c", 400f, 0f))
            .groupElements(listOf("a", "b"), groupId = "inner")
        val outer = inner.groupElements(listOf("inner", "c"), groupId = "outer")

        val group = outer.elements.single() as GroupElement
        assertEquals(Frame(0f, 0f, 500f, 100f), group.frame)
        assertEquals(listOf("inner", "c"), group.children.map { it.id })

        val moved = group.update(frame = group.frame.translate(10f, 0f)) as GroupElement
        val movedInner = moved.children[0] as GroupElement
        assertEquals(Frame(10f, 0f, 300f, 100f), movedInner.frame)
        assertEquals(Frame(10f, 0f, 100f, 100f), movedInner.children[0].frame)
    }

    @Test
    fun reorderingABlockKeepsItsRelativeOrder() {
        val slide = slideOf(
            shape("a", 0f, 0f), shape("b", 0f, 0f), shape("c", 0f, 0f), shape("d", 0f, 0f),
        )
        assertEquals(
            listOf("b", "d", "a", "c"),
            slide.reorderElements(listOf("c", "a"), ZOrderMove.ToFront).ids(),
        )
        assertEquals(
            listOf("b", "d", "a", "c"),
            slide.reorderElements(listOf("b", "d"), ZOrderMove.ToBack).ids(),
        )
    }

    @Test
    fun steppingABlockNeverLetsMembersLeapfrogEachOther() {
        val slide = slideOf(
            shape("a", 0f, 0f), shape("b", 0f, 0f), shape("c", 0f, 0f), shape("d", 0f, 0f),
        )
        // a and b each step one forward, keeping their order and the gap to c.
        assertEquals(
            listOf("c", "a", "b", "d"),
            slide.reorderElements(listOf("a", "b"), ZOrderMove.Forward).ids(),
        )
        assertEquals(
            listOf("a", "c", "d", "b"),
            slide.reorderElements(listOf("c", "d"), ZOrderMove.Backward).ids(),
        )

        // Against the ceiling: the top member can't move, so neither can the one
        // behind it, and the whole move is a no-op.
        assertTrue(slide === slide.reorderElements(listOf("c", "d"), ZOrderMove.Forward))
        assertTrue(slide === slide.reorderElements(listOf("a", "b"), ZOrderMove.Backward))
        assertTrue(slide === slide.reorderElements(listOf("a", "b"), ZOrderMove.ToBack))
        assertTrue(slide === slide.reorderElements(emptyList(), ZOrderMove.ToFront))
    }

    @Test
    fun serializationRoundTripsANestedGroup() {
        val slide = slideOf(shape("a", 0f, 0f), shape("b", 200f, 0f), shape("c", 400f, 0f))
            .groupElements(listOf("a", "b"), groupId = "inner")
        val document = Document(
            slides = listOf(slide.groupElements(listOf("inner", "c"), groupId = "outer")),
        )

        val decoded = decodeDocument(document.encodeToString())
        assertEquals(document, decoded)
        val outer = decoded.slides.single().elements.single() as GroupElement
        assertEquals("inner", (outer.children.first() as GroupElement).id)
    }

    /** Files written before groups existed have no "children" anywhere: still ours. */
    @Test
    fun aDocumentWithoutGroupsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "slides": [
                {
                  "id": "slide",
                  "elements": [
                    {
                      "type": "text",
                      "id": "text",
                      "frame": { "x": 0.0, "y": 0.0, "width": 10.0, "height": 10.0 },
                      "text": "Hello"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val element = decodeDocument(json).slides.single().elements.single()
        assertEquals("Hello", (element as TextElement).text)
    }

    @Test
    fun allSlidesIsTheFlatPresentationOrder() {
        val document = sampleDocument()
        assertEquals(
            listOf("Cupboard", "Agenda", "Why KMP", "Rendering Pipeline", "Scene Graph", "Slides as Data", "Native Interop", "Benchmarks", "Roadmap"),
            document.allSlides().map { it.title },
        )
    }

    @Test
    fun updateSlideReplacesById() {
        val document = sampleDocument()
        val target = document.allSlides().first { it.title == "Rendering Pipeline" }
        val renamed = target.copy(title = "Pipeline, Renamed")
        val updated = document.updateSlide(renamed)
        assertTrue(updated.allSlides().any { it.title == "Pipeline, Renamed" })
        assertFalse(updated.allSlides().any { it.title == "Rendering Pipeline" })
    }

    @Test
    fun hasChildrenLooksAtTheNextSlideDepth() {
        val document = sampleDocument()
        val whyKmp = document.slides.indexOfFirst { it.title == "Why KMP" }
        assertTrue(document.hasChildren(whyKmp))
        assertFalse(document.hasChildren(0))
        assertFalse(document.hasChildren(document.slides.lastIndex))
    }

    @Test
    fun collapsingHidesTheDeeperRunButNeverRenumbers() {
        val document = sampleDocument()
        assertEquals(document.slides.indices.toList(), document.visibleIndices())

        val whyKmp = document.slides.first { it.title == "Why KMP" }
        val collapsed = document.toggleCollapsed(whyKmp.id)
        // Indices 3 (Rendering Pipeline), 4 (Scene Graph) and 5 (Slides as Data) hidden;
        // the rest keep their absolute indices, so numbering (index + 1) is unchanged.
        assertEquals(listOf(0, 1, 2, 6, 7, 8), collapsed.visibleIndices())

        val expanded = collapsed.toggleCollapsed(whyKmp.id)
        assertEquals(document.slides.indices.toList(), expanded.visibleIndices())
    }

    @Test
    fun nestedCollapseStaysHiddenInsideACollapsedParent() {
        val document = Document(
            slides = listOf(
                Slide(title = "A", collapsed = true),
                Slide(title = "A.1", depth = 1, collapsed = true),
                Slide(title = "A.1.a", depth = 2),
                Slide(title = "A.2", depth = 1),
                Slide(title = "B"),
            ),
        )
        // A collapsed hides its whole deeper run, including the collapsed A.1.
        assertEquals(listOf(0, 4), document.visibleIndices())

        // Expanding A reveals A.1 and A.2, but A.1 keeps its own children hidden.
        val expanded = document.toggleCollapsed(document.slides[0].id)
        assertEquals(listOf(0, 1, 3, 4), expanded.visibleIndices())
    }

    @Test
    fun stepCountCountsOnClickBuildsOnly() {
        val slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        // 4 OnClick stage builds; WithPrevious arrows don't add steps
        assertEquals(5, slide.stepCount())
    }

    @Test
    fun withPreviousJoinsThePrecedingStep() {
        val slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        val steps = slide.buildSteps()
        val stageIds = slide.elements.filterIsInstance<ShapeElement>().map { it.id }
        val arrowIds = slide.elements.filterIsInstance<TextElement>().filter { it.text == "→" }.map { it.id }
        assertEquals(1, steps[stageIds[0]])
        assertEquals(1, steps[arrowIds[0]])
        assertEquals(2, steps[stageIds[1]])
        assertEquals(4, steps[stageIds[3]])
    }

    /**
     * A parent with two children between two leaves: enough for a move to have a
     * run to carry, a run to let out, and a gap between a parent and its first
     * child to land in.
     */
    private fun deck(): Document = Document(
        id = "doc",
        slides = listOf(
            Slide(id = "a", title = "A"),
            Slide(id = "b", title = "B"),
            Slide(id = "b1", title = "B.1", depth = 1),
            Slide(id = "b2", title = "B.2", depth = 1),
            Slide(id = "c", title = "C"),
        ),
    )

    private fun Document.slideIds(): List<String> = slides.map { it.id }

    private fun Document.depths(): List<Int> = slides.map { it.depth }

    @Test
    fun movingALeafDropsItUnderTheAnchorAtTheAnchorsDepth() {
        val moved = deck().moveSlide("c", "a")
        assertEquals(listOf("a", "c", "b", "b1", "b2"), moved.slideIds())
        assertEquals(listOf(0, 0, 0, 1, 1), moved.depths())
    }

    @Test
    fun aNullAnchorMeansTheGapAboveTheFirstRow() {
        val moved = deck().moveSlide("b1", null)
        assertEquals(listOf("b1", "a", "b", "b2", "c"), moved.slideIds())
        // Top of the deck is depth 0, whatever depth the row came from.
        assertEquals(listOf(0, 0, 0, 1, 0), moved.depths())
    }

    @Test
    fun aCollapsedParentTravelsWithTheRunItHides() {
        val collapsed = deck().toggleCollapsed("b")
        val moved = collapsed.moveSlide("b", null)
        assertEquals(listOf("b", "b1", "b2", "a", "c"), moved.slideIds())
        // The children keep their distance from the parent, so the group lands whole.
        assertEquals(listOf(0, 1, 1, 0, 0), moved.depths())
    }

    @Test
    fun anExpandedParentMovesAloneAndItsChildrenOutdent() {
        val moved = deck().moveSlide("b", "c")
        assertEquals(listOf("a", "b1", "b2", "c", "b"), moved.slideIds())
        assertEquals(listOf(0, 0, 0, 0, 0), moved.depths())
    }

    @Test
    fun droppingBetweenAParentAndItsFirstChildJoinsTheChildRun() {
        val moved = deck().moveSlide("c", "b")
        assertEquals(listOf("a", "b", "c", "b1", "b2"), moved.slideIds())
        assertEquals(listOf(0, 0, 1, 1, 1), moved.depths())
    }

    @Test
    fun aMoveThatChangesNothingReturnsTheSameDocument() {
        val deck = deck()
        // Identity is the signal callers use to skip the history entry.
        assertTrue(deck === deck.moveSlide("a", null))
        assertTrue(deck === deck.moveSlide("b1", "b"))
        assertTrue(deck === deck.moveSlide("nowhere", "a"))
        assertTrue(deck === deck.moveSlide("a", "nowhere"))
        // Dropped on its own row, and on a row inside it.
        assertTrue(deck === deck.moveSlide("a", "a"))
        val collapsed = deck.toggleCollapsed("b")
        assertTrue(collapsed === collapsed.moveSlide("b", "b1"))
    }

    @Test
    fun skippingRenumbersThePresentationAndCollapsingDoesNot() {
        val deck = deck()
        assertEquals(listOf(1, 2, 3, 4, 5), deck.presentationNumbers())
        assertEquals(listOf(1, 2, 3, 4, 5), deck.toggleCollapsed("b").presentationNumbers())

        val skipped = deck.setSlideSkipped("b", true).setSlideSkipped("b2", true)
        assertEquals(listOf(1, null, 2, null, 3), skipped.presentationNumbers())
    }

    @Test
    fun skippingIsPerSlideAndAnswersNoOpsWithTheSameDocument() {
        val deck = deck().toggleCollapsed("b")
        val skipped = deck.setSlideSkipped("b", true)
        assertTrue(skipped.slides[1].skipped)
        // The hidden run does not follow the row it hides behind.
        assertFalse(skipped.slides[2].skipped)

        assertTrue(skipped === skipped.setSlideSkipped("b", true))
        assertTrue(deck === deck.setSlideSkipped("b", false))
        assertTrue(deck === deck.setSlideSkipped("nowhere", true))
    }

    @Test
    fun serializationRoundTripsSkipNumberingAndBackground() {
        val document = Document(
            slides = listOf(
                Slide(title = "Skipped", skipped = true),
                Slide(title = "Numbered", showsSlideNumber = true),
                Slide(title = "Flat", background = SlideBackground.Color(0xFF102030)),
                Slide(
                    title = "Graded",
                    background = SlideBackground.Gradient(0xFF2A2452, 0xFF101223, angle = 90f),
                ),
            ),
        )
        assertEquals(document, decodeDocument(document.encodeToString()))
    }

    /** Slide management arrived last, so a file written before it has none of it. */
    @Test
    fun aSlideWrittenBeforeSkipAndBackgroundsStillDecodes() {
        val json = """
            {
              "id": "doc",
              "slides": [{ "id": "slide", "title": "Old" }]
            }
        """.trimIndent()

        val slide = decodeDocument(json).slides.single()
        assertFalse(slide.skipped)
        assertFalse(slide.showsSlideNumber)
        assertEquals(null, slide.background)
    }

    @Test
    fun elementsWithoutBuildsAreAlwaysVisible() {
        val slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        val titleId = slide.elements.first().id
        assertTrue(slide.isVisibleAt(titleId, step = 0))
        val firstStageId = slide.elements.filterIsInstance<ShapeElement>().first().id
        assertFalse(slide.isVisibleAt(firstStageId, step = 0))
        assertTrue(slide.isVisibleAt(firstStageId, step = 1))
    }
}
