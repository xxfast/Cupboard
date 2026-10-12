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
import io.github.xxfast.cupboard.document.webAddress
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
    insertMovie(extension, title) { assetId -> assets.write(assetId, bytes) }
}

/**
 * [insertMovie] for a movie the caller would rather not hold as bytes: [write]
 * is handed the fresh asset id and puts the movie under it however it likes.
 *
 * What a shell with a file to hand uses, so a long recording is copied into the
 * deck rather than read into memory first. A lambda rather than a path, because
 * nothing here knows what a file is. A [write] that throws inserts nothing.
 */
suspend fun EditorViewModel.insertMovie(
    extension: String,
    title: String,
    write: suspend (assetId: String) -> Unit,
) {
    val assetId: String = writeMedia(extension, write)
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
    insertSound(extension, title) { assetId -> assets.write(assetId, bytes) }
}

/** [insertSound] with the writing left to [write], for [insertMovie]'s reason. */
suspend fun EditorViewModel.insertSound(
    extension: String,
    title: String,
    write: suspend (assetId: String) -> Unit,
) {
    val assetId: String = writeMedia(extension, write)
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
): Boolean = replaceMedia(element, extension) { assetId -> assets.write(assetId, bytes) }

/** [replaceMedia] with the writing left to [write], for [insertMovie]'s reason. */
suspend fun EditorViewModel.replaceMedia(
    element: Element,
    extension: String,
    write: suspend (assetId: String) -> Unit,
): Boolean {
    val replacing: (String) -> Element = when (element) {
        is VideoElement -> element::replacingMedia
        is AudioElement -> element::replacingMedia
        else -> return false
    }

    onUpdateElements(listOf(replacing(writeMedia(extension, write))))
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
 * A fresh id with [extension] kept, and [write] run on it off the caller's
 * thread: a movie is hundreds of megabytes where a picture is a few.
 */
private suspend fun writeMedia(
    extension: String,
    write: suspend (assetId: String) -> Unit,
): String {
    val assetId: String = "${newId()}.${extension.removePrefix(".").lowercase()}"
    withContext(Dispatchers.Default) { write(assetId) }
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
                onCommit = { url -> webAddress(url)?.let(onInsert) },
            )
        }
    }
}
