package io.github.xxfast.cupboard.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import io.github.xxfast.cupboard.canvas.LocalAssetStore
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.CupboardBundle
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.DocumentLoad
import io.github.xxfast.cupboard.document.InMemoryAssetStore
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.sampleDocument
import io.github.xxfast.cupboard.document.decodeDocument
import io.github.xxfast.cupboard.document.stepCount
import kotlinx.io.files.Path
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * The show walked through the real player a step at a time, every frame it
 * draws timed: what catches a slide that has got expensive to draw (#44's
 * timeline, re-diffed on every lookup) before a presenter feels it.
 *
 * The sample deck by default. `CUPBOARD_DECK=/path/to/Deck.cupboard` walks a
 * real one instead, e.g.
 * `CUPBOARD_DECK=~/Desktop/Talk.cupboard ./gradlew :cupboard:ui:jvmTest --tests '*FrameBudgetTest' --rerun -i`,
 * and the slowest places print either way.
 *
 * This is the time the slide takes to compose, lay out and draw on the JVM, not
 * the display's vsync: a budget for the work, which is what regresses. Walked
 * twice, the first pass only to get past the JIT.
 */
class FrameBudgetTest {

    /** Any one frame past this is a slide gone pathological, not a slow machine. */
    private val budgetMs: Long = 250

    /** Frames drawn at each step: long enough for a build or a transition to finish. */
    private val framesPerStep: Int = 40

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun everyStepOfTheShowDrawsWithinBudget() {
        val deck: String? = System.getenv("CUPBOARD_DECK")
        // Decoded the way the app opens one, migrations and all, rather than
        // through the bare store, which an older deck fails.
        val document: Document = deck
            ?.let { decodeDocument(File(it, "document.json").readText()) }
            ?.let { load -> (load as? DocumentLoad.Loaded)?.document ?: error("$deck: $load") }
            ?: sampleDocument()
        val assets: AssetStore = deck?.let { CupboardBundle.assetStore(Path(it)) } ?: InMemoryAssetStore()
        val order: List<Slide> = document.playOrder()

        lateinit var controller: PlayerController
        val places = mutableListOf<Place>()

        ImageComposeScene(944, 531).use { scene ->
            scene.setContent {
                controller = rememberPlayerController()
                CompositionLocalProvider(LocalAssetStore provides assets) {
                    PresentationPlayer(document = document, controller = controller)
                }
            }

            var time = 0L
            fun frame(): Long = measureTime {
                time += 16_666_667L
                scene.render(time)
            }.inWholeMilliseconds

            repeat(2) { pass ->
                val measuring: Boolean = pass == 1
                controller.goTo(0, 0)
                frame()
                for ((index, slide) in order.withIndex()) {
                    for (step in 0 until slide.stepCount()) {
                        if (index > 0 || step > 0) controller.next()
                        val times: List<Long> = List(if (measuring) framesPerStep else 2) { frame() }
                        if (measuring) places += Place(index + 1, slide.title, step, times)
                    }
                }
            }
        }

        val slowest: List<Place> = places.sortedByDescending { it.worst }.take(10)
        println("FRAMES ${places.size} places, ${places.sumOf { it.times.size }} frames")
        for (place in slowest) println("FRAMES $place")

        val over: List<Place> = places.filter { it.worst > budgetMs }
        assertTrue(over.isEmpty(), "frames over ${budgetMs}ms at:\n${over.joinToString("\n")}")
    }

    private class Place(val slide: Int, val title: String, val step: Int, val times: List<Long>) {
        val worst: Long = times.max()
        private val median: Long = times.sorted()[times.size / 2]
        override fun toString(): String = "slide $slide \"$title\" step $step: worst ${worst}ms, median ${median}ms"
    }
}
