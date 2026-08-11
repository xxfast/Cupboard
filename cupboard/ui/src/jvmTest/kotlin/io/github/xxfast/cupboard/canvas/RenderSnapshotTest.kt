package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.sampleDocument
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

class RenderSnapshotTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun renderSampleSlideToPng() {
        val slide = sampleDocument().allSlides().first { it.builds.isNotEmpty() }
        val image = renderComposeScene(1920, 1080) {
            SlideView(slide)
        }
        val png = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
        val out = File(System.getProperty("snapshot.out") ?: "build/snapshot.png")
        out.parentFile.mkdirs()
        out.writeBytes(png)
        println("snapshot: ${out.absolutePath}")
    }
}
