package io.github.xxfast.cupboard.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Movies and sounds are document data: what is behind them, where they are cut,
 * how loudly they play and what travels when one is copied or restyled. All pure,
 * so the renderer, the shells and whatever plays them read one interpretation.
 * What the poster and the pill look like is `MediaRenderTest`'s job.
 */
class MediaElementTest {
    private val box = Frame(0f, 0f, 960f, 540f)
    private val pill = Frame(0f, 0f, 320f, 64f)

    @Test
    fun serializationRoundTripsBothKinds() {
        val document = Document(
            slides = listOf(
                Slide(
                    elements = listOf(
                        VideoElement(
                            frame = box,
                            assetId = "talk.mp4",
                            webUrl = "https://example.com/talk",
                            posterAssetId = "poster.png",
                            trimStartMs = 1_500,
                            trimEndMs = 9_000,
                            loop = true,
                            volume = 0.4f,
                            autoplay = true,
                            title = "The demo",
                        ),
                        AudioElement(
                            frame = pill,
                            assetId = "theme.m4a",
                            trimStartMs = 250,
                            trimEndMs = 4_000,
                            loop = true,
                            volume = 0.2f,
                            autoplay = true,
                            title = "Intro theme",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(document, loadedDocument(document.encodeToString()))
    }

    @Test
    fun mediaWithNothingBehindItIsTheEmptyElementItReadsAs() {
        val video = VideoElement(frame = box)
        val audio = AudioElement(frame = pill)

        assertNull(video.assetId)
        assertNull(video.webUrl)
        assertNull(video.posterAssetId)
        // Untrimmed, so nothing here has to be kept in step with a duration the
        // document never learns.
        assertEquals(0, video.trimStartMs)
        assertEquals(0, video.trimEndMs)
        assertEquals(1f, video.volume)
        assertTrue(!video.loop && !video.autoplay)
        assertEquals("", video.title)

        assertNull(audio.assetId)
        assertEquals(0, audio.trimEndMs)
        assertEquals(1f, audio.volume)
        assertTrue(!audio.loop && !audio.autoplay)
    }

    @Test
    fun aPastedStyleCarriesHowItPlaysAndLeavesWhatItPlays() {
        val source = VideoElement(
            frame = box,
            assetId = "source.mp4",
            trimStartMs = 500,
            loop = true,
            volume = 0.3f,
            autoplay = true,
            title = "Source",
        )
        val target = VideoElement(frame = box, assetId = "target.mp4", title = "Target")

        val styled = target.applyingStyle(source) as VideoElement

        assertTrue(styled.loop)
        assertEquals(0.3f, styled.volume)
        assertEquals("target.mp4", styled.assetId)
        assertEquals("Target", styled.title)
        assertEquals(0, styled.trimStartMs, "where a movie is cut is what it says")
        assertTrue(!styled.autoplay, "and so is whether it starts on its own")
    }

    @Test
    fun aPastedStyleCarriesTheSameTwoFactsForASound() {
        val source = AudioElement(frame = pill, assetId = "a.m4a", loop = true, volume = 0.6f)
        val target = AudioElement(frame = pill, assetId = "b.m4a", title = "Outro")

        val styled = target.applyingStyle(source) as AudioElement

        assertTrue(styled.loop)
        assertEquals(0.6f, styled.volume)
        assertEquals("b.m4a", styled.assetId)
        assertEquals("Outro", styled.title)
    }

    @Test
    fun aStyleOnlyCrossesBetweenTheSameKind() {
        val video = VideoElement(frame = box, opacity = 0.5f, loop = true, volume = 0.1f)
        val audio = AudioElement(frame = pill, assetId = "a.m4a")

        val styled = audio.applyingStyle(video) as AudioElement

        assertEquals(0.5f, styled.opacity, "opacity is all any two kinds share")
        assertTrue(!styled.loop)
        assertEquals(1f, styled.volume)
    }

    @Test
    fun aCopyKeepsWhatItPlaysUnderAFreshId() {
        val video = VideoElement(
            id = "v",
            frame = box,
            assetId = "talk.mp4",
            posterAssetId = "poster.png",
            trimEndMs = 8_000,
            volume = 0.7f,
        )
        val audio = AudioElement(id = "a", frame = pill, assetId = "theme.m4a", loop = true)

        val videoCopy = video.withNewIds() as VideoElement
        val audioCopy = audio.withNewIds() as AudioElement

        assertNotEquals(video.id, videoCopy.id)
        assertEquals(video.copy(id = videoCopy.id), videoCopy)
        assertNotEquals(audio.id, audioCopy.id)
        assertEquals(audio.copy(id = audioCopy.id), audioCopy)
    }

    @Test
    fun theFactoriesInsertWhatTheyAreHandedAndNothingElse() {
        val embedded: VideoElement = videoElement(box, assetId = "talk.mp4")
        val web: VideoElement = videoElement(box, webUrl = "https://example.com/talk")
        val empty: VideoElement = videoElement(box)
        val sound: AudioElement = audioElement(pill, assetId = "theme.m4a", title = "Theme")

        assertEquals(box, embedded.frame)
        assertEquals("talk.mp4", embedded.assetId)
        assertNull(embedded.webUrl)
        assertEquals("https://example.com/talk", web.webUrl)
        assertNull(web.assetId)
        assertTrue(empty.assetId == null && empty.webUrl == null)
        // A movie inserts at rest: nothing autoplays, nothing loops, full volume.
        assertTrue(!embedded.autoplay && !embedded.loop)
        assertEquals(1f, embedded.volume)

        assertEquals(pill, sound.frame)
        assertEquals("theme.m4a", sound.assetId)
        assertEquals("Theme", sound.title)

        assertEquals(960f, DefaultVideoWidth)
        assertEquals(540f, DefaultVideoHeight)
        assertEquals(320f, DefaultAudioWidth)
        assertEquals(64f, DefaultAudioHeight)
    }

    @Test
    fun aResizedDeckMovesTheBoxAndLeavesEverythingAboutThePlaying() {
        val video = VideoElement(
            id = "v",
            frame = Frame(100f, 100f, 960f, 540f),
            assetId = "talk.mp4",
            trimStartMs = 1_000,
            trimEndMs = 5_000,
            volume = 0.5f,
        )
        val audio = AudioElement(id = "a", frame = Frame(0f, 0f, 320f, 64f), assetId = "t.m4a")
        val document = Document(slides = listOf(Slide(elements = listOf(video, audio))))

        // Widescreen to standard, which narrows the slide and leaves its height.
        val resized: Document = document.resized(1440f, 1080f, scaleContent = true)
        val elements: List<Element> = resized.slides.single().elements
        val movedVideo = elements[0] as VideoElement
        val movedAudio = elements[1] as AudioElement

        assertEquals(Frame(75f, 100f, 720f, 540f), movedVideo.frame)
        assertEquals(1_000, movedVideo.trimStartMs)
        assertEquals(5_000, movedVideo.trimEndMs)
        assertEquals(0.5f, movedVideo.volume)
        assertEquals("talk.mp4", movedVideo.assetId)
        assertEquals(240f, movedAudio.frame.width)
        assertEquals(64f, movedAudio.frame.height)
    }

    @Test
    fun mediaTravelsAcrossACutOnWhatItPlaysRatherThanHowItPlaysIt() {
        val here = VideoElement(frame = box, assetId = "talk.mp4", title = "Here")
        val there = VideoElement(
            frame = Frame(400f, 300f, 480f, 270f),
            assetId = "talk.mp4",
            trimStartMs = 4_000,
            loop = true,
            title = "There",
        )

        assertEquals(here.matchKey(), there.matchKey())
        assertNotEquals(here.matchKey(), VideoElement(frame = box, assetId = "other.mp4").matchKey())

        // Bytes before url, and a web-only movie is matched by the page it is.
        val web = VideoElement(frame = box, webUrl = "https://example.com/talk")
        assertEquals(web.matchKey(), VideoElement(frame = pill, webUrl = "https://example.com/talk").matchKey())
        assertNotEquals(web.matchKey(), here.matchKey())

        val sound = AudioElement(frame = pill, assetId = "theme.m4a")
        assertEquals(sound.matchKey(), AudioElement(frame = box, assetId = "theme.m4a", loop = true).matchKey())
        // A movie and a sound are never the same object, whatever they are of.
        assertNotEquals(sound.matchKey(), VideoElement(frame = box, assetId = "theme.m4a").matchKey())
    }

    @Test
    fun neitherKindTakesACaret() {
        assertTrue(!VideoElement(frame = box).takesCaret)
        assertTrue(!AudioElement(frame = pill).takesCaret)
    }
}
