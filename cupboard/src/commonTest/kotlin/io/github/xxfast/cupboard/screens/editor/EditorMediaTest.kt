package io.github.xxfast.cupboard.screens.editor

import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.DefaultAudioHeight
import io.github.xxfast.cupboard.document.DefaultAudioWidth
import io.github.xxfast.cupboard.document.DefaultVideoHeight
import io.github.xxfast.cupboard.document.DefaultVideoWidth
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.audioElement
import io.github.xxfast.cupboard.document.replacingMedia
import io.github.xxfast.cupboard.document.videoElement
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Movies and sounds through the editor loop: the inserts every shell makes, the
 * property edits the Movie and Audio segments send, and the clamps the loop puts
 * on both. The same two events an image goes through, `InsertElement` and
 * `UpdateElements`, so this is about what they do to media in particular.
 */
class EditorMediaTest {
    private fun document(): Document = Document(
        slides = listOf(
            Slide(
                id = "slide",
                elements = listOf(ShapeElement(id = "a", frame = Frame(0f, 0f, 100f, 100f))),
            ),
        ),
    )

    private fun EditorState.video(id: String): VideoElement =
        selectedSlide.elements.first { it.id == id } as VideoElement

    private fun EditorState.audio(id: String): AudioElement =
        selectedSlide.elements.first { it.id == id } as AudioElement

    @Test
    fun aMovieInsertsCentredAtItsOwnAspectAndSelectedAlone() = runTest {
        val viewModel = editor(document())
        val state: EditorState = viewModel.states.value

        val movie: VideoElement = videoElement(
            frame = state.insertionFrame(DefaultVideoWidth, DefaultVideoHeight),
            assetId = "talk.mov",
            posterAssetId = "talk.png",
            title = "talk.mov",
            naturalWidth = 1440,
            naturalHeight = 1080,
        )
        viewModel.onInsertElement(movie)

        val inserted: EditorState = viewModel.await { it.selectedSlide.elements.size == 2 }
        val video: VideoElement = inserted.video(movie.id)
        assertEquals(listOf(movie.id), inserted.selectedElementIds)
        assertEquals("talk.mov", video.assetId)
        assertEquals("talk.png", video.posterAssetId)
        assertEquals("talk.mov", video.title)
        // 4:3 inside the 16:9 box: full height, narrower, still in the middle.
        assertEquals(DefaultVideoHeight, video.frame.height)
        assertEquals(720f, video.frame.width)
        assertEquals(inserted.document.slideWidth / 2, video.frame.centerX)
        assertEquals(inserted.document.slideHeight / 2, video.frame.centerY)
        assertEquals(listOf(FormatSegment.Style, FormatSegment.Movie, FormatSegment.Arrange), inserted.formatSegments)
    }

    @Test
    fun aMovieNoPlayerProbedInsertsAsTheWidescreenBox() = runTest {
        val viewModel = editor(document())
        val frame: Frame = viewModel.states.value.insertionFrame(DefaultVideoWidth, DefaultVideoHeight)

        val movie: VideoElement = videoElement(frame, assetId = "talk.webm", title = "talk.webm")
        viewModel.onInsertElement(movie)

        val video: VideoElement = viewModel.await { it.selectedSlide.elements.size == 2 }.video(movie.id)
        assertEquals(frame, video.frame)
        assertNull(video.posterAssetId)
    }

    @Test
    fun aSoundInsertsAsAPillNamedForItsFile() = runTest {
        val viewModel = editor(document())
        val frame: Frame = viewModel.states.value.insertionFrame(DefaultAudioWidth, DefaultAudioHeight)

        val sound: AudioElement = audioElement(frame, assetId = "theme.m4a", title = "theme.m4a")
        viewModel.onInsertElement(sound)

        val inserted: EditorState = viewModel.await { it.selectedSlide.elements.size == 2 }
        val audio: AudioElement = inserted.audio(sound.id)
        assertEquals(listOf(sound.id), inserted.selectedElementIds)
        assertEquals(frame, audio.frame)
        assertEquals("theme.m4a", audio.assetId)
        assertEquals("theme.m4a", audio.title)
        assertEquals(listOf(FormatSegment.Style, FormatSegment.Audio, FormatSegment.Arrange), inserted.formatSegments)
    }

    @Test
    fun aWebVideoInsertsWithNoBytesBehindIt() = runTest {
        val viewModel = editor(document())
        val frame: Frame = viewModel.states.value.insertionFrame(DefaultVideoWidth, DefaultVideoHeight)

        val movie: VideoElement = videoElement(frame, webUrl = "https://example.com/talk")
        viewModel.onInsertElement(movie)

        val inserted: EditorState = viewModel.await { it.selectedSlide.elements.size == 2 }
        val video: VideoElement = inserted.video(movie.id)
        assertNull(video.assetId)
        assertEquals("https://example.com/talk", video.webUrl)
        assertEquals(frame, video.frame)
        assertEquals(listOf(movie.id), inserted.selectedElementIds)
    }

    @Test
    fun eachInsertIsOneUndoStep() = runTest {
        val viewModel = editor(document())
        val frame: Frame = viewModel.states.value.insertionFrame(DefaultVideoWidth, DefaultVideoHeight)

        viewModel.onInsertElement(videoElement(frame, assetId = "talk.mp4"))
        viewModel.await { it.selectedSlide.elements.size == 2 }
        viewModel.onInsertElement(audioElement(frame, assetId = "theme.m4a"))
        viewModel.await { it.selectedSlide.elements.size == 3 }

        viewModel.onUndo()
        viewModel.await { it.selectedSlide.elements.size == 2 }
        viewModel.onUndo()
        val undone: EditorState = viewModel.await { it.selectedSlide.elements.size == 1 }
        assertEquals(listOf("a"), undone.selectedSlide.elements.map { it.id })
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
    }

    @Test
    fun anInsertedMovieIsClampedOnTheWayIn() = runTest {
        val viewModel = editor(document())

        val movie = VideoElement(frame = Frame(0f, 0f, 160f, 90f), volume = 4f, trimStartMs = -10)
        viewModel.onInsertElement(movie)

        val video: VideoElement = viewModel.await { it.selectedSlide.elements.size == 2 }.video(movie.id)
        assertEquals(1f, video.volume)
        assertEquals(0, video.trimStartMs)
    }

    @Test
    fun propertyEditsAreClampedAndAreOneUndoStep() = runTest {
        val video = VideoElement(id = "v", frame = Frame(0f, 0f, 960f, 540f), assetId = "talk.mp4")
        val viewModel = editor(Document(slides = listOf(Slide(id = "slide", elements = listOf(video)))))

        viewModel.onUpdateElements(
            listOf(
                video.copy(
                    title = "Demo",
                    loop = true,
                    autoplay = true,
                    volume = 1.7f,
                    trimStartMs = 3_000,
                    trimEndMs = 1_000,
                ),
            ),
        )

        val edited: EditorState = viewModel.await { it.video("v").title == "Demo" }
        val settled: VideoElement = edited.video("v")
        assertEquals(1f, settled.volume)
        assertEquals(3_000, settled.trimStartMs)
        // Ending before it starts plays to the end rather than nothing at all.
        assertEquals(0, settled.trimEndMs)
        assertTrue(settled.loop && settled.autoplay)

        viewModel.onUndo()
        assertEquals(video, viewModel.await { !it.canUndo }.video("v"))
    }

    @Test
    fun aVolumeSliderPreviewIsClampedTooAndCancelsBack() = runTest {
        val audio = AudioElement(id = "s", frame = Frame(0f, 0f, 320f, 64f), assetId = "theme.m4a", volume = 0.5f)
        val viewModel = editor(Document(slides = listOf(Slide(id = "slide", elements = listOf(audio)))))

        viewModel.onPreviewElements(listOf(audio.copy(volume = -1f)))
        assertEquals(0f, viewModel.await { it.isPreviewing }.audio("s").volume)

        viewModel.onCancelPreview()
        assertEquals(0.5f, viewModel.await { !it.isPreviewing }.audio("s").volume)
    }

    @Test
    fun aPosterIsSetAndClearedThroughTheSameEdit() = runTest {
        val video = VideoElement(id = "v", frame = Frame(0f, 0f, 960f, 540f), assetId = "talk.mp4")
        val viewModel = editor(Document(slides = listOf(Slide(id = "slide", elements = listOf(video)))))

        viewModel.onUpdateElements(listOf(video.copy(posterAssetId = "still.png")))
        viewModel.await { it.video("v").posterAssetId == "still.png" }

        viewModel.onUpdateElements(listOf(video.copy(posterAssetId = null)))
        assertNull(viewModel.await { it.video("v").posterAssetId == null }.video("v").posterAssetId)
    }

    @Test
    fun replacingAMovieKeepsItsBoxAndSettingsAndDropsItsTrims() = runTest {
        val video = VideoElement(
            id = "v",
            frame = Frame(40f, 50f, 480f, 270f),
            assetId = "old.mp4",
            trimStartMs = 1_000,
            trimEndMs = 4_000,
            loop = true,
            volume = 0.3f,
            title = "Demo",
        )
        val viewModel = editor(Document(slides = listOf(Slide(id = "slide", elements = listOf(video)))))

        viewModel.onUpdateElements(listOf(video.replacingMedia("new.mov")))

        val replaced: VideoElement = viewModel.await { it.video("v").assetId == "new.mov" }.video("v")
        assertEquals(video.frame, replaced.frame)
        assertEquals(0, replaced.trimStartMs)
        assertEquals(0, replaced.trimEndMs)
        assertTrue(replaced.loop)
        assertEquals(0.3f, replaced.volume)
        assertEquals("Demo", replaced.title)
    }

    @Test
    fun aBatchEditReachesEveryMovieAndSkipsTheLockedOne() = runTest {
        val first = VideoElement(id = "v1", frame = Frame(0f, 0f, 160f, 90f), assetId = "a.mp4")
        val second = VideoElement(id = "v2", frame = Frame(200f, 0f, 160f, 90f), assetId = "b.mp4")
        val locked = VideoElement(id = "v3", frame = Frame(400f, 0f, 160f, 90f), assetId = "c.mp4", locked = true)
        val viewModel = editor(
            Document(slides = listOf(Slide(id = "slide", elements = listOf(first, second, locked)))),
        )

        viewModel.onUpdateElements(listOf(first, second, locked).map { it.copy(loop = true) })

        val state: EditorState = viewModel.await { it.video("v1").loop }
        assertTrue(state.video("v2").loop)
        assertFalse(state.video("v3").loop)
    }

    @Test
    fun filesAreSortedIntoImagesMoviesAndSounds() {
        assertEquals(MediaKind.Image, mediaKind("png"))
        assertEquals(MediaKind.Image, mediaKind("JPEG"))
        for (extension in listOf("mp4", "mov", "m4v", "webm", "mkv", "avi")) {
            assertEquals(MediaKind.Movie, mediaKind(extension), extension)
        }
        for (extension in listOf("mp3", "m4a", "aac", "wav", "aiff", "aif", "flac", "ogg")) {
            assertEquals(MediaKind.Sound, mediaKind(extension), extension)
        }
        // Case and a leading dot are forgiven: the platforms hand over both.
        assertEquals(MediaKind.Movie, mediaKind(".MOV"))
        assertNull(mediaKind("pdf"))
        assertNull(mediaKind(""))
    }
}
