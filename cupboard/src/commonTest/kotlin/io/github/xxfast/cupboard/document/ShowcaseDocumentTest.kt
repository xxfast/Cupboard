package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The showcase deck checked against itself.
 *
 * Two jobs. The first is the ordinary integrity of a document built by hand:
 * every build points at an element that is there, every step is in range, every
 * link and every layout resolves. The second is coverage: a deck whose whole
 * purpose is to show every feature has to fail the day a feature is added and
 * not shown, which is why the enum assertions are written off `entries` rather
 * than against a list of names someone would have to remember to grow.
 */
class ShowcaseDocumentTest {

    @Test
    fun roundTripsThroughTheDocumentSerializer() {
        val document: Document = showcaseDocument()
        val decoded: DocumentLoad = decodeDocument(document.encodeToString())

        val loaded: Document = (decoded as? DocumentLoad.Loaded)?.document
            ?: fail("the showcase deck did not decode: $decoded")
        assertEquals(document, loaded)
    }

    @Test
    fun everySlideTitleIsUnique() {
        val titles: List<String> = showcaseDocument().slides.map { it.title }
        val repeated: Set<String> = titles.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue(repeated.isEmpty(), "repeated slide titles: $repeated")
    }

    @Test
    fun everyBuildPointsAtAnElementOnItsOwnSlide() {
        for (slide in showcaseDocument().slides) {
            for (build in slide.builds) {
                assertTrue(
                    slide.elementById(build.elementId) != null,
                    "${slide.title} has a build for an element it does not hold",
                )
            }
        }
    }

    @Test
    fun everyElementStepIsInRangeForItsElement() {
        for (slide in showcaseDocument().slides) {
            for (build in slide.builds) {
                val step: Int = build.elementStep ?: continue
                val element: Element = slide.elementById(build.elementId) ?: continue
                val steps: Int = element.stepCount()
                assertTrue(
                    step in 0 until steps,
                    "${slide.title}: step $step is outside the $steps steps of $element",
                )
            }
        }
    }

    @Test
    fun everySlideLinkNamesASlideTheDeckHolds() {
        val document: Document = showcaseDocument()
        val ids: Set<String> = document.slides.mapTo(mutableSetOf()) { it.id }

        for (target in document.linkTargets()) {
            if (target !is LinkTarget.Slide) continue
            assertTrue(
                target.slideId in ids,
                "a link points at ${target.slideId}, which is no slide of this deck",
            )
        }
    }

    @Test
    fun everyLayoutIdNamesALayoutTheDeckHolds() {
        val document: Document = showcaseDocument()
        val ids: Set<String> = document.layouts.mapTo(mutableSetOf()) { it.id }

        for (slide in document.slides) {
            val layoutId: String = slide.layoutId ?: continue
            assertTrue(layoutId in ids, "${slide.title} is on layout $layoutId, which is not here")
        }
    }

    /**
     * The sections read as an outline: a depth-0 card, then the feature slides
     * under it. Anything deeper than one level is a slide that has drifted.
     */
    @Test
    fun theDeckIsTwoLevelsDeepAndOpensAtTheTop() {
        val slides: List<Slide> = showcaseDocument().slides
        assertEquals(0, slides.first().depth)
        assertTrue(slides.all { it.depth in 0..1 }, "the outline is more than two levels deep")
    }

    @Test
    fun everyShapeKindIsShown() = assertCovered(ShapeKind.entries) { document ->
        document.elements().flatMap { element ->
            when (element) {
                is ShapeElement -> listOf(element.kind)
                is ImageElement -> listOfNotNull(element.mask?.kind)
                else -> emptyList()
            }
        }
    }

    @Test
    fun everyCodeThemeIsShown() = assertCovered(CodeTheme.entries) { document ->
        document.elements().filterIsInstance<CodeElement>().map { it.theme }
    }

    @Test
    fun everyTextFontIsShown() = assertCovered(TextFont.entries) { document ->
        document.elements().filterIsInstance<TextElement>().map { it.fontFamily }
    }

    @Test
    fun everyListStyleIsShown() = assertCovered(ListStyle.entries) { document ->
        document.elements().filterIsInstance<TextElement>().map { it.listStyle }
    }

    @Test
    fun everyTextAlignIsShown() = assertCovered(TextAlign.entries) { document ->
        document.elements().filterIsInstance<TextElement>().map { it.align }
    }

    @Test
    fun everyBuildEffectIsShown() =
        assertCovered(BuildEffect.entries) { it.builds().map { build -> build.effect } }

    @Test
    fun everyBuildKindIsShown() =
        assertCovered(BuildKind.entries) { it.builds().map { build -> build.kind } }

    @Test
    fun everyBuildDeliveryIsShown() =
        assertCovered(BuildDelivery.entries) { it.builds().map { build -> build.delivery } }

    @Test
    fun everyBuildTriggerIsShown() =
        assertCovered(BuildTrigger.entries) { it.builds().map { build -> build.trigger } }

    @Test
    fun everyActionKindIsShown() =
        assertCovered(ActionKind.entries) { it.builds().mapNotNull { build -> build.action?.kind } }

    @Test
    fun everyTransitionKindIsShown() =
        assertCovered(TransitionKind.entries) { it.transitions().map { each -> each.kind } }

    @Test
    fun everyTransitionDirectionIsShown() = assertCovered(TransitionDirection.entries) { document ->
        document.transitions().map { it.direction }
    }

    @Test
    fun everyTransitionTriggerIsShown() =
        assertCovered(TransitionTrigger.entries) { it.transitions().map { each -> each.trigger } }

    /**
     * [LinkTarget] is a sealed hierarchy rather than an enum, so this one names
     * its cases: adding an eighth target breaks the `when` in [linkCase] at
     * compile time, which is the same guarantee `entries` gives the others.
     */
    @Test
    fun everyLinkTargetIsShown() {
        val shown: Set<String> =
            showcaseDocument().linkTargets().mapTo(mutableSetOf()) { it.linkCase() }
        val expected: Set<String> = setOf(
            "Slide", "Next", "Previous", "First", "Last", "Url", "ExitShow",
        )
        assertEquals(expected, shown, "the Links slide is missing a target")
    }

    /** Every value of [all] appears somewhere in the deck, per [shown]. */
    private fun <T> assertCovered(all: List<T>, shown: (Document) -> List<T>) {
        val found: Set<T> = shown(showcaseDocument()).toSet()
        val missing: List<T> = all.filterNot { it in found }
        assertTrue(missing.isEmpty(), "the showcase deck never shows: $missing")
    }
}

/** Every element of every slide, group children included. */
private fun Document.elements(): List<Element> = slides.flatMap { slide ->
    slide.elements.flatMap { it.subtree() }
}

private fun Element.subtree(): List<Element> =
    listOf(this) + ((this as? GroupElement)?.children?.flatMap { it.subtree() } ?: emptyList())

private fun Document.builds(): List<Build> = slides.flatMap { it.builds }

private fun Document.transitions(): List<SlideTransition> = slides.mapNotNull { it.transition }

/** Every link any element on the deck carries. */
private fun Document.linkTargets(): List<LinkTarget> = elements().mapNotNull { it.resolvedLink() }

/** How many states this element can be walked through, 0 for one that has none. */
private fun Element.stepCount(): Int = when (this) {
    is CodeElement -> steps.size
    is DiagramElement -> steps.size
    is GalleryElement -> images.size
    else -> 0
}

/** Which case of the sealed hierarchy this is, by name. */
private fun LinkTarget.linkCase(): String = when (this) {
    is LinkTarget.Slide -> "Slide"
    is LinkTarget.Next -> "Next"
    is LinkTarget.Previous -> "Previous"
    is LinkTarget.First -> "First"
    is LinkTarget.Last -> "Last"
    is LinkTarget.Url -> "Url"
    is LinkTarget.ExitShow -> "ExitShow"
}
