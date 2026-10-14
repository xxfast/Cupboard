package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.BuiltInThemes
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.effectiveBackground
import io.github.xxfast.cupboard.document.instantiating
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

/**
 * A Magic Move between two layouts, frozen as it starts, halfway and as it lands,
 * for a human to look at: Islands Light's "Kotlin Editor" turning into "Kotlin
 * Editor and Run".
 *
 * Not an assertion. What the move has to be is one editor sliding over to make
 * room for its run pane, and the one way to know the islands are travelling with
 * what they hold, rather than cutting to the new layout under it, is to look at
 * the middle frame.
 */
class MagicMoveSnapshotTest {
    @Test
    fun renderAnEditorTurningIntoItsRunLayout() {
        val directory = File(System.getProperty("snapshots.out") ?: "build/snapshots", "magic-move")
        directory.mkdirs()

        val theme: Theme = BuiltInThemes.JetBrainsIslandsLight
        val editor: Slide = theme.layouts.first { it.title == "Kotlin Editor" }
        val run: Slide = theme.layouts.first { it.title == "Kotlin Editor and Run" }
        val from: Slide = Slide(id = "from").instantiating(editor).written()
        val to: Slide = Slide(id = "to").instantiating(run).written()

        val play = PlayTransition(
            fromSlide = from,
            toSlide = to,
            forward = true,
            transition = SlideTransition(kind = TransitionKind.MagicMove, durationMs = DURATION_MS),
            fromBackground = from.effectiveBackground(editor, theme.background),
            fromLayout = editor,
        )

        val scene = ImageComposeScene(width = 1920, height = 1080, density = Density(1f))
        scene.setContent {
            CompositionLocalProvider(LocalPlayTransition provides play) {
                SlideView(slide = to, layout = run, step = 0, background = theme.background)
            }
        }

        try {
            // Frame by frame, the way a display would ask: an animation's clock
            // starts on the first frame it sees, so one long jump lands nowhere.
            for (frame in 0..DURATION_MS / FRAME_MS + 2) {
                val image = scene.render(frame * FRAME_MS * 1_000_000L)
                val at: Int = frame * FRAME_MS
                if (at != 0 && at != DURATION_MS / 2 && frame != DURATION_MS / FRAME_MS + 2) continue

                val out = File(directory, "magic-move-${at.toString().padStart(3, '0')}.png")
                out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                println("snapshot: ${out.absolutePath}")
            }
        } finally {
            scene.close()
        }
    }

    /** The same file and the same code on both slides, so both travel. */
    private fun Slide.written(): Slide = copy(
        elements = elements.map { element ->
            when (element) {
                is CodeElement -> element.copy(code = "fun main() {\n    println(\"Hello, islands\")\n}")
                is TextElement -> if (element.text == "File.kt") element.copy(text = "Main.kt") else element
                else -> element
            }
        },
    )

    private companion object {
        const val DURATION_MS: Int = 600
        const val FRAME_MS: Int = 20
    }
}
