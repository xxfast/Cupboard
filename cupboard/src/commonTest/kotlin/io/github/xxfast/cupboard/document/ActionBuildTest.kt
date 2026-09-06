package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Action builds: what an element that is already on the slide does when one
 * lands, and everything they deliberately leave alone.
 */
class ActionBuildTest {
    private val frame = Frame(0f, 0f, 200f, 100f)

    private val box = ShapeElement(id = "box", frame = frame)
    private val caption = TextElement(id = "caption", frame = frame, text = "Hi")

    private fun slide(vararg builds: Build): Slide =
        Slide(id = "slide", elements = listOf(box, caption), builds = builds.toList())

    private fun move(dx: Float, dy: Float) = BuildAction(ActionKind.Move, dx = dx, dy = dy)

    @Test
    fun anElementWithNoActionsIsAtRest() {
        val slide = slide(Build("box"))
        assertEquals(ActionState(), slide.actionStateAt("box", step = 3))
        assertNull(slide.actionBuildAt("box", step = 3))
    }

    @Test
    fun movesAndRotationsAddUp() {
        val slide = slide(
            Build.action("box", move(dx = 40f, dy = 10f)),
            Build.action("box", move(dx = 20f, dy = -30f)),
            Build.action("box", BuildAction(ActionKind.Rotate, rotation = 15f)),
            Build.action("box", BuildAction(ActionKind.Rotate, rotation = 30f)),
        )

        // One click each, so each step sees one more of them landed.
        assertEquals(ActionState(dx = 40f, dy = 10f), slide.actionStateAt("box", 1))
        assertEquals(ActionState(dx = 60f, dy = -20f), slide.actionStateAt("box", 2))
        assertEquals(ActionState(dx = 60f, dy = -20f, rotation = 15f), slide.actionStateAt("box", 3))
        assertEquals(ActionState(dx = 60f, dy = -20f, rotation = 45f), slide.actionStateAt("box", 4))
    }

    @Test
    fun scalesMultiplyAndOpacityIsTheLastOneSet() {
        val slide = slide(
            Build.action("box", BuildAction(ActionKind.Scale, scale = 2f)),
            Build.action("box", BuildAction(ActionKind.Opacity, opacity = 0.5f)),
            Build.action("box", BuildAction(ActionKind.Scale, scale = 1.5f)),
            Build.action("box", BuildAction(ActionKind.Opacity, opacity = 0.2f)),
        )

        assertEquals(ActionState(scale = 2f), slide.actionStateAt("box", 1))
        assertEquals(ActionState(scale = 2f, opacity = 0.5f), slide.actionStateAt("box", 2))
        assertEquals(ActionState(scale = 3f, opacity = 0.5f), slide.actionStateAt("box", 3))
        assertEquals(ActionState(scale = 3f, opacity = 0.2f), slide.actionStateAt("box", 4))
    }

    @Test
    fun actionsSharingAStepComposeIntoOneState() {
        val slide = slide(
            Build.action("box", BuildAction(ActionKind.Scale, scale = 1.15f)),
            Build.action(
                elementId = "box",
                action = BuildAction(ActionKind.Scale, scale = 1f / 1.15f),
                trigger = BuildTrigger.AfterPrevious,
            ),
        )

        // Both land on the one click, so the pair is the identity: a settle that
        // is to be seen has to take a step of its own.
        assertEquals(1f, slide.actionStateAt("box", 1).scale, absoluteTolerance = 1e-5f)
    }

    @Test
    fun eachElementComposesOnlyItsOwnActions() {
        val slide = slide(
            Build.action("box", move(dx = 10f, dy = 0f)),
            Build.action("caption", move(dx = 0f, dy = 100f)),
        )

        assertEquals(ActionState(dx = 10f), slide.actionStateAt("box", 2))
        assertEquals(ActionState(dy = 100f), slide.actionStateAt("caption", 2))
    }

    @Test
    fun actionsRideEveryTriggerTheOtherBuildsDo() {
        val slide = slide(
            Build.action("box", move(dx = 10f, dy = 0f)),
            Build.action("box", move(dx = 10f, dy = 0f), trigger = BuildTrigger.WithPrevious),
            Build.action("box", move(dx = 10f, dy = 0f), trigger = BuildTrigger.AfterPrevious),
            Build.action("box", move(dx = 10f, dy = 0f)),
        )

        // Three of them share the first click, and only the fourth takes one.
        assertEquals(ActionState(dx = 30f), slide.actionStateAt("box", 1))
        assertEquals(ActionState(dx = 40f), slide.actionStateAt("box", 2))
        assertEquals(3, slide.stepCount())

        // The chained one waits out the one ahead of it before it starts.
        val chained: BuildAt = slide.buildTimeline()[2]
        assertEquals(600, chained.delayMs)
    }

    @Test
    fun anActionIsOnePieceWhateverItsDeliveryClaims() {
        val slide = slide(
            Build.action("caption", move(dx = 5f, dy = 0f)).copy(delivery = BuildDelivery.ByCharacter),
        )

        assertEquals(1, slide.builds.single().pieceCount(slide))
        assertEquals(2, slide.stepCount())
    }

    @Test
    fun stepCountCountsAnOnClickActionLikeAnyOtherBuild() {
        val plain = slide(Build("box"), Build("caption"))
        assertEquals(3, plain.stepCount())

        val acted = slide(Build("box"), Build("caption"), Build.action("box", move(dx = 8f, dy = 0f)))
        assertEquals(4, acted.stepCount())
    }

    @Test
    fun actionsSayNothingAboutVisibility() {
        val slide = slide(
            Build("box"),
            Build.action("box", move(dx = 40f, dy = 0f)),
            Build("box", kind = BuildKind.Out),
        )

        // Off until its In lands, on across the action, and gone on its Out.
        assertTrue(!slide.isVisibleAt("box", 0))
        assertTrue(slide.isVisibleAt("box", 1))
        assertTrue(slide.isVisibleAt("box", 2))
        assertTrue(!slide.isVisibleAt("box", 3))

        // And an element with nothing but actions was there from the start.
        val only = slide(Build.action("caption", move(dx = 1f, dy = 0f)))
        assertTrue(only.isVisibleAt("caption", 0))
        assertTrue(only.isVisibleAt("caption", 1))
    }

    @Test
    fun anActionIsNeitherAnEntryNorAnExit() {
        val slide = slide(Build.action("box", move(dx = 40f, dy = 0f)))

        assertNull(slide.entryBuild("box"))
        assertNull(slide.exitBuild("box"))
        assertTrue(slide.buildSteps().isEmpty())
    }

    @Test
    fun anActionBuildWithNoActionIsANoOp() {
        val slide = slide(Build("box", kind = BuildKind.Action))

        assertEquals(ActionState(), slide.actionStateAt("box", 1))
        // It still holds a step, because it is still a click in the build order.
        assertEquals(2, slide.stepCount())
    }

    @Test
    fun theDrivingBuildIsTheLastToLandAndTheFirstOnTheWayBack() {
        val slide = slide(
            Build.action("box", move(dx = 10f, dy = 0f), durationMs = 200),
            Build.action("box", move(dx = 10f, dy = 0f), durationMs = 900),
        )

        // Before either has landed, the way back is the first one's own tween.
        assertEquals(200, slide.actionBuildAt("box", 0)?.build?.durationMs)
        assertEquals(200, slide.actionBuildAt("box", 1)?.build?.durationMs)
        assertEquals(900, slide.actionBuildAt("box", 2)?.build?.durationMs)
    }

    @Test
    fun aDeckWrittenBeforeActionsStillOpens() {
        val json = """
            {
              "id": "doc",
              "slides": [
                {
                  "id": "slide",
                  "title": "One",
                  "elements": [
                    { "type": "shape", "id": "box", "frame": { "x": 0.0, "y": 0.0, "width": 200.0, "height": 100.0 } }
                  ],
                  "builds": [ { "elementId": "box", "kind": "In", "effect": "Pop" } ]
                }
              ]
            }
        """.trimIndent()

        val build: Build = decodeDocument(json).slides.single().builds.single()
        assertEquals(BuildKind.In, build.kind)
        assertEquals(BuildEffect.Pop, build.effect)
        assertNull(build.action)
    }

    @Test
    fun anActionSurvivesARoundTrip() {
        val document = Document(
            id = "doc",
            slides = listOf(slide(Build.action("box", BuildAction(ActionKind.Move, dx = 12f, dy = -4f)))),
        )

        assertEquals(document, decodeDocument(document.encodeToString()))
    }

    @Test
    fun theSampleDeckEmphasisesItsDrawStage() {
        val slide: Slide = sampleDocument().allSlides().first { it.title == "Rendering Pipeline" }
        val draw: ShapeElement = slide.elements.filterIsInstance<ShapeElement>().single {
            it.label == "Draw"
        }

        // Swollen on its own click, and back to rest on the same one.
        assertEquals(1.15f, slide.actionStateAt(draw.id, 8).scale, absoluteTolerance = 1e-5f)
        assertEquals(1f, slide.actionStateAt(draw.id, 9).scale, absoluteTolerance = 1e-5f)
    }
}
