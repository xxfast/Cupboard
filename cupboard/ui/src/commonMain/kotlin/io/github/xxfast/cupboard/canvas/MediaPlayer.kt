package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.VideoElement

/**
 * Everything a host needs to play one element, and nothing about how it plays it.
 *
 * Deliberately flat and toolkit-free: this crosses to SwiftUI and to WinUI as
 * well as to Compose, so it carries the asset id rather than bytes (the shell
 * resolves it through its own store, `FileAssetStore.pathOf`) and the frame in
 * slide units rather than pixels (the shell knows its own canvas scale, the
 * document does not).
 *
 * [assetId] and [webUrl] are the two routes a [VideoElement] holds; a request
 * with neither is an element with nothing behind it, which a host plays as
 * nothing. [isAudio] is what tells a host it has no picture to put anywhere, so
 * a sound plays without a surface being made for it.
 */
data class MediaRequest(
    val elementId: String,
    val assetId: String?,
    val webUrl: String?,
    /** The element's box on the slide, in document units. */
    val frame: Frame,
    val trimStartMs: Int,
    /** 0 plays to the end. See [VideoElement.trimEndMs]. */
    val trimEndMs: Int,
    val loop: Boolean,
    val volume: Float,
    val isAudio: Boolean,
)

/**
 * What plays a slide's movies and sounds, which is nothing the shared canvas can
 * do: decoding media is AVFoundation's, MediaFoundation's and Skiko-less
 * territory, and every shell owns a different one.
 *
 * So the canvas draws the still and says when to play, and the host does the
 * playing. [stop] takes an element id because that is all a canvas knows about a
 * player it never made; [stopAll] is what a shell calls when the show leaves a
 * slide with several of them on it, or ends.
 *
 * The picture comes back the other way, through [picture]: the host decodes, and
 * the canvas draws what it decoded in the movie's place, under every rotation,
 * fade, build and transition the slide puts the movie through. A surface laid
 * over the canvas could only ever follow those from outside, a frame late.
 */
interface MediaPlayerHost {
    /** Starts [request], replacing whatever was already playing for its element. */
    fun play(request: MediaRequest)

    /** Stops the element with this id. A no-op if nothing of its is playing. */
    fun stop(elementId: String)

    /** Stops everything this host is playing. */
    fun stopAll()

    /**
     * What [elementId]'s movie is showing, asked once a frame by every canvas
     * drawing it, so a host may decode its next picture here. Null draws the
     * poster: a sound, a movie still opening, one that was stopped, and every
     * host that has no pictures to give.
     */
    fun picture(elementId: String): MediaPicture? = null
}

/**
 * A playing movie's picture: one [image] the host draws each decoded frame into,
 * and [frame], which counts them. Redrawn in place rather than handed over anew,
 * so a movie costs one bitmap for as long as it plays rather than one a frame.
 *
 * [frame] is snapshot state, and reading it is what tells a canvas its picture
 * changed: the [image] it holds is the same object either way. Written by the
 * host from inside [MediaPlayerHost.picture], on the frame that asked.
 */
@Stable
class MediaPicture(val image: ImageBitmap) {
    var frame: Int by mutableIntStateOf(0)
}

/**
 * The host that plays nothing, which is what the editor, a thumbnail, a preview
 * and every test get: a slide draws its posters and its pills the same either
 * way, and nothing has to check whether there is a player before asking for one.
 */
object NoMediaPlayer : MediaPlayerHost {
    override fun play(request: MediaRequest) = Unit
    override fun stop(elementId: String) = Unit
    override fun stopAll() = Unit
}

/**
 * [host]'s pictures, and nothing else of it: what a second view of a show is
 * given, such as the presenter display's current slide. It draws the movie the
 * audience is watching, and its own autoplay, taps and teardown start and stop
 * nothing, so there is still only the one copy playing.
 */
class MediaPicturesOf(private val host: MediaPlayerHost) : MediaPlayerHost {
    override fun play(request: MediaRequest) = Unit
    override fun stop(elementId: String) = Unit
    override fun stopAll() = Unit
    override fun picture(elementId: String): MediaPicture? = host.picture(elementId)
}

/**
 * Where the canvas asks for media to be played. [NoMediaPlayer] unless a shell
 * provides one, for [LocalLinkHandler]'s reason: the tap happens several layers
 * below anything that can answer it.
 */
val LocalMediaPlayer = compositionLocalOf<MediaPlayerHost> { NoMediaPlayer }

/** This movie as the request that plays it. */
fun VideoElement.mediaRequest(): MediaRequest = MediaRequest(
    elementId = id,
    assetId = assetId,
    // The bytes win where a movie has both: the url is then only where it came
    // from, and a host that was handed both would have to pick anyway.
    webUrl = if (assetId == null) webUrl else null,
    frame = frame,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    loop = loop,
    volume = volume,
    isAudio = false,
)

/** This sound as the request that plays it. */
fun AudioElement.mediaRequest(): MediaRequest = MediaRequest(
    elementId = id,
    assetId = assetId,
    webUrl = null,
    frame = frame,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    loop = loop,
    volume = volume,
    isAudio = true,
)
