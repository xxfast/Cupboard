package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.compositionLocalOf
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
 */
interface MediaPlayerHost {
    /** Starts [request], replacing whatever was already playing for its element. */
    fun play(request: MediaRequest)

    /** Stops the element with this id. A no-op if nothing of its is playing. */
    fun stop(elementId: String)

    /** Stops everything this host is playing. */
    fun stopAll()
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
