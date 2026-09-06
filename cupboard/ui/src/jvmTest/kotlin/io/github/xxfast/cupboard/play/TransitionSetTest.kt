package io.github.xxfast.cupboard.play

import androidx.compose.ui.unit.LayoutDirection
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.sampleDocument
import net.kodein.cup.Slide as CupSlide
import net.kodein.cup.isSpecified
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransitionSetTest {

    @Test
    fun everyKindCompilesToASetCupWillAnimate() {
        for (kind in TransitionKind.entries) {
            for (direction in TransitionDirection.entries) {
                val transition = SlideTransition(kind = kind, direction = direction)
                assertTrue(
                    transition.toTransitionSet(LayoutDirection.Ltr).isSpecified,
                    "$kind $direction produced an unspecified set",
                )
            }
        }
    }

    /** A cut is a transition like any other: CuP is handed a set, not a null. */
    @Test
    fun noneIsASetOfItsOwnRatherThanNothing() {
        val none = SlideTransition(kind = TransitionKind.None).toTransitionSet(LayoutDirection.Ltr)
        assertTrue(none.isSpecified)
    }

    @Test
    fun aDeckOnTheDefaultStillGetsTheMoveItAlwaysHad() {
        assertTrue(null.toTransitionSet(LayoutDirection.Ltr).isSpecified)
        assertTrue(null.toTransitionSet(LayoutDirection.Rtl).isSpecified)
    }

    /**
     * A change is one animation, so both slides in it have to be handed the same
     * set: the leaving slide's own is the arriving slide's start.
     */
    @Test
    fun aSlideStartsOnThePreviousSlidesTransitionAndEndsOnItsOwn() {
        val document = Document(
            slides = listOf(
                Slide(id = "one", transition = SlideTransition(kind = TransitionKind.Wipe)),
                Slide(id = "two", transition = SlideTransition(kind = TransitionKind.Push)),
                Slide(id = "three"),
            ),
        )

        val slides: List<CupSlide> = document.toCupSlides()
        for (slide in slides) assertNotNull(slide.specs)

        assertSame(slides[0].specs!!.endTransitions, slides[1].specs!!.startTransitions)
        assertSame(slides[1].specs!!.endTransitions, slides[2].specs!!.startTransitions)
        // The first slide has no previous one, so it opens on the deck's default.
        assertTrue(slides[0].specs!!.startTransitions.isSpecified)
    }

    /** Skipped slides are not in the deck play walks, so they are not in the chain either. */
    @Test
    fun theChainIsOverTheSlidesThatActuallyPlay() {
        val document = sampleDocument()
        val played: List<Slide> = document.playedSlides()
        val slides: List<CupSlide> = document.toCupSlides()

        assertTrue(played.any { it.transition != null })
        for ((index, slide) in slides.withIndex()) {
            if (index == 0) continue
            assertSame(slides[index - 1].specs!!.endTransitions, slide.specs!!.startTransitions)
        }
    }
}
