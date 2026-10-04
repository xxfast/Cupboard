package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.DefaultAudioHeight
import io.github.xxfast.cupboard.document.DefaultAudioWidth
import io.github.xxfast.cupboard.document.DefaultVideoHeight
import io.github.xxfast.cupboard.document.DefaultVideoWidth
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.VideoElement
import io.github.xxfast.cupboard.document.audioElement
import io.github.xxfast.cupboard.document.newId
import io.github.xxfast.cupboard.document.replacingMedia
import io.github.xxfast.cupboard.document.videoElement
import io.github.xxfast.cupboard.theme.ChromeTheme
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LinuxChrome
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import io.github.xxfast.cupboard.theme.toColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [bytes] written into the deck and put on the selected slide as a movie called
 * [title], which is the name of the file it came from.
 *
 * [insertImage]'s twin with the decode left out: nothing shared can open a movie,
 * so there is no natural size to fit to and no poster to cut, and the movie lands
 * as the default 16:9 box over the dark media surface. A shell whose player can
 * tell it more is free to build the element itself.
 */
suspend fun EditorViewModel.insertMovie(bytes: ByteArray, extension: String, title: String) {
    val assetId: String = writeMedia(bytes, extension)
    val state: EditorState = states.value
    onInsertElement(
        videoElement(
            frame = state.insertionFrame(DefaultVideoWidth, DefaultVideoHeight),
            assetId = assetId,
            title = title,
            defaults = state.defaults,
        ),
    )
}

/** [insertMovie] for a sound: the bytes written in, the pill on the slide under [title]. */
suspend fun EditorViewModel.insertSound(bytes: ByteArray, extension: String, title: String) {
    val assetId: String = writeMedia(bytes, extension)
    val state: EditorState = states.value
    onInsertElement(
        audioElement(
            frame = state.insertionFrame(DefaultAudioWidth, DefaultAudioHeight),
            assetId = assetId,
            title = title,
            defaults = state.defaults,
        ),
    )
}

/**
 * A movie that is only a page: [url] on the slide in the default box, with no
 * bytes behind it. Play opens the page, the way a link does.
 */
fun EditorViewModel.insertWebVideo(url: String) {
    val state: EditorState = states.value
    onInsertElement(
        videoElement(
            frame = state.insertionFrame(DefaultVideoWidth, DefaultVideoHeight),
            webUrl = url,
            defaults = state.defaults,
        ),
    )
}

/**
 * [element] playing [bytes] instead, written in as a new asset: the inspector's
 * Replace. What stays and what goes is `replacingMedia`'s call. False for an
 * element that is neither a movie nor a sound, which writes nothing.
 */
suspend fun EditorViewModel.replaceMedia(
    element: Element,
    bytes: ByteArray,
    extension: String,
): Boolean {
    val replacing: (String) -> Element = when (element) {
        is VideoElement -> element::replacingMedia
        is AudioElement -> element::replacingMedia
        else -> return false
    }

    onUpdateElements(listOf(replacing(writeMedia(bytes, extension))))
    return true
}

/**
 * [bytes] as [element]'s poster frame, written in as a new asset.
 *
 * Decoded first, for [insertImage]'s reason: a poster is drawn by the canvas, so
 * a file it cannot draw is nothing to point a movie at. False is that file, and
 * writes nothing.
 */
suspend fun EditorViewModel.setPoster(
    element: VideoElement,
    bytes: ByteArray,
    extension: String,
): Boolean {
    decodeImage(bytes) ?: return false
    val assetId: String = "${newId()}.${extension.lowercase()}"
    assets.write(assetId, bytes)

    onUpdateElements(listOf(element.copy(posterAssetId = assetId)))
    return true
}

/**
 * [bytes] written under a fresh id with [extension] kept, off the caller's
 * thread: a movie is hundreds of megabytes where a picture is a few.
 */
private suspend fun EditorViewModel.writeMedia(bytes: ByteArray, extension: String): String {
    val assetId: String = "${newId()}.${extension.removePrefix(".").lowercase()}"
    withContext(Dispatchers.Default) { assets.write(assetId, bytes) }
    return assetId
}

/**
 * Insert > Web Video's prompt: one field for the address, committed by Insert or
 * by Enter. A blank address inserts nothing, and one typed without a scheme is
 * taken as https, since that is what a browser would do with it.
 *
 * Public because the shell owns when it shows: the menu bar asks for it as well
 * as the toolbar, and only the shell has both. Which is also why it puts on the
 * editor's [theme] itself, since the shell hosts it outside the editor's tree.
 */
@Composable
fun WebVideoDialog(
    onDismiss: () -> Unit,
    onInsert: (url: String) -> Unit,
    theme: ChromeTheme = LinuxChrome,
) {
    val dark: Boolean = isSystemInDarkTheme()
    val tokens: ChromeTokens = if (dark) theme.dark else theme.light

    CompositionLocalProvider(LocalChromeTokens provides tokens) {
        MaterialTheme(colorScheme = tokens.toColorScheme(dark)) {
            NameDialog(
                title = "Insert Web Video",
                confirmLabel = "Insert",
                name = "",
                onDismiss = onDismiss,
                onCommit = { url -> onInsert(if ("://" in url) url else "https://$url") },
            )
        }
    }
}
