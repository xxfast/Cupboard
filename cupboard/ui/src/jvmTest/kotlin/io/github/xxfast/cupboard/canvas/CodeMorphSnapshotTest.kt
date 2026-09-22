package io.github.xxfast.cupboard.canvas

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeStep
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * The token morph, frozen at three points of one version change, for a human to
 * look at: the two versions at rest and the frame halfway between them.
 *
 * Not an assertion. What a morph has to be is legible, and the one way to know
 * whether the tokens are travelling rather than piling up on each other is to
 * look at the middle frame. The two ends are here to be compared against the
 * static render the line path draws at rest, which is what
 * `RenderSnapshotTest.renderEveryStepOfTheBuiltSlideToPng` writes for the same
 * slide.
 */
class CodeMorphSnapshotTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderTheCodeMorphAtRestAndMidFlight() {
        val directory = File(System.getProperty("snapshots.out") ?: "build/snapshots", "morph")
        directory.mkdirs()

        val slide: Slide = sampleDocument().allSlides().first { it.title == "Magic Move for Code" }
        val element: CodeElement = slide.elements.filterIsInstance<CodeElement>().single()
        val from: CodeStep = element.steps[0]
        val to: CodeStep = element.steps[1]
        val chrome: CodeChrome = element.theme.chrome

        for (fraction in listOf(0f, 0.5f, 1f)) {
            val image = renderComposeScene(1920, 1080) {
                SlideSurface(slideBackground = slide.background) {
                    Box(
                        modifier = Modifier
                            .offset(element.frame.x.dp, element.frame.y.dp)
                            .size(element.frame.width.dp, element.frame.height.dp)
                            .background(chrome.background, CodeCorner)
                            .border(1.dp, chrome.border, CodeCorner)
                            .clip(CodeCorner)
                            .padding(CodePadding),
                    ) {
                        MorphedCodeLines(
                            element = element,
                            from = from,
                            to = to,
                            progress = remember { Animatable(fraction) },
                        )
                    }
                }
            }

            val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            val name = "morph-${(fraction * 100).toInt().toString().padStart(3, '0')}.png"
            val out = File(directory, name)
            out.writeBytes(png)
            println("snapshot: ${out.absolutePath}")
        }
    }

    /**
     * The morph at either end is the line path at rest, pixel for pixel.
     *
     * The two are drawn by different code (composed `Text` rows against measured
     * runs on a canvas), so any difference in how they set type (letter spacing,
     * weight, line height) shows as the block changing size the instant a morph
     * starts and again when it settles.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun morphEndsMatchTheLinePathAtRest() {
        val slide: Slide = sampleDocument().allSlides().first { it.title == "Magic Move for Code" }
        val element: CodeElement = slide.elements.filterIsInstance<CodeElement>().single()
        val from: CodeStep = element.steps[0]
        val to: CodeStep = element.steps[1]

        fun render(content: @Composable () -> Unit): ByteArray = renderComposeScene(1920, 1080) {
            SlideSurface(slideBackground = slide.background) {
                Box(
                    modifier = Modifier
                        .offset(element.frame.x.dp, element.frame.y.dp)
                        .size(element.frame.width.dp, element.frame.height.dp)
                        .padding(CodePadding),
                ) {
                    content()
                }
            }
        }.encodeToData(EncodedImageFormat.PNG)!!.bytes

        val restFrom: ByteArray = render { AnimatedCodeLines(element, from) }
        val restTo: ByteArray = render { AnimatedCodeLines(element, to) }
        val morphStart: ByteArray = render {
            MorphedCodeLines(element, from, to, progress = remember { Animatable(0f) })
        }
        val morphEnd: ByteArray = render {
            MorphedCodeLines(element, from, to, progress = remember { Animatable(1f) })
        }

        assertContentEquals(restFrom, morphStart, "morph at 0 differs from the line path at rest")
        assertContentEquals(restTo, morphEnd, "morph at 1 differs from the line path at rest")
    }
}
