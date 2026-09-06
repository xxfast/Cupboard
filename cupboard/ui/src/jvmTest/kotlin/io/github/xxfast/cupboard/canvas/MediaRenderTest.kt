package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.VideoElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a movie and a sound look like before anything plays them, and when the
 * canvas asks for one to be played.
 *
 * The pixels are not asserted: a poster box and a pill are chrome rather than
 * content, and pinning their colours here would be pinning the design. What is
 * worth holding is that both compose at all on a target with no media stack
 * behind them, and that an autoplay movie reaches [MediaPlayerHost] exactly when
 * the build order brings it on screen.
 */
class MediaRenderTest {
    private val video = VideoElement(
        id = "v",
        frame = Frame(20f, 20f, 160f, 90f),
        assetId = "talk.mp4",
        trimStartMs = 1_000,
        trimEndMs = 4_000,
        loop = true,
        volume = 0.5f,
        title = "The demo",
    )

    private val audio = AudioElement(
        id = "a",
        frame = Frame(20f, 140f, 160f, 40f),
        assetId = "theme.m4a",
        title = "Intro theme",
    )

    @Test
    fun aMovieAndASoundDrawWithNothingBehindThem() {
        // No poster, no bytes, no player: the placeholder box and the pill.
        render { slide { ElementView(video.copy(assetId = null)) } }
        render { slide { ElementView(audio) } }
        // A web video with neither poster nor asset is the other empty case.
        render { slide { ElementView(VideoElement(frame = video.frame, webUrl = "https://x.dev")) } }
    }

    @Test
    fun anAutoplayMovieAsksToBePlayedWhenItsStepRevealsIt() {
        val player = RecordingPlayer()
        val deck = Slide(
            id = "one",
            elements = listOf(video.copy(autoplay = true)),
            // Not on screen at step 0: the build brings it in on the first click.
            builds = listOf(Build("v")),
        )

        render(player) { SlideView(deck, step = 0, slideWidth = 200f, slideHeight = 200f) }
        assertEquals(emptyList(), player.played, "nothing has revealed it yet")

        render(player) { SlideView(deck, step = 1, slideWidth = 200f, slideHeight = 200f) }
        val request: MediaRequest = player.played.single()
        assertEquals("v", request.elementId)
        assertEquals("talk.mp4", request.assetId)
        assertEquals(1_000, request.trimStartMs)
        assertEquals(4_000, request.trimEndMs)
        assertEquals(0.5f, request.volume)
        assertTrue(request.loop)
        assertTrue(!request.isAudio)
        assertEquals(video.frame, request.frame, "the box in slide units, for the host to cover")
        // The scene going away is the slide being left, which is what stops it.
        assertEquals(listOf("v"), player.stopped)
    }

    @Test
    fun aMovieThatWasNotAskedToAutoplayIsLeftAlone() {
        val player = RecordingPlayer()
        val deck = Slide(id = "one", elements = listOf(video))

        render(player) { SlideView(deck, step = 0, slideWidth = 200f, slideHeight = 200f) }
        assertEquals(emptyList(), player.played)
    }

    @Test
    fun theEditorNeverPlaysAnything() {
        val player = RecordingPlayer()
        val deck = Slide(id = "one", elements = listOf(video.copy(autoplay = true)))

        // No step at all is the editor's canvas, which draws the poster at rest.
        render(player) { SlideView(deck, slideWidth = 200f, slideHeight = 200f) }
        assertEquals(emptyList(), player.played)
    }

    /** A host that plays nothing and remembers what it was asked to play. */
    private class RecordingPlayer : MediaPlayerHost {
        val played: MutableList<MediaRequest> = mutableListOf()
        val stopped: MutableList<String> = mutableListOf()

        override fun play(request: MediaRequest) {
            played += request
        }

        override fun stop(elementId: String) {
            stopped += elementId
        }

        override fun stopAll() = Unit
    }

    /**
     * [content] on a 200x200 slide at native size, rendered once, with [player]
     * as the host the canvas asks to play things.
     *
     * One frame is all it takes: what is being asserted is what the composition
     * asks for, and an autoplay effect runs on the way to the first frame rather
     * than a frame later.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        player: MediaPlayerHost = NoMediaPlayer,
        content: @Composable () -> Unit,
    ) {
        renderComposeScene(200, 200, density = Density(1f)) {
            CompositionLocalProvider(LocalMediaPlayer provides player) { content() }
        }
    }

    /** A 200x200 slide surface, for the elements rendered without a [SlideView]. */
    @Composable
    private fun slide(content: @Composable () -> Unit) {
        SlideSurface(zoom = 1f, slideWidth = 200f, slideHeight = 200f) { content() }
    }
}
