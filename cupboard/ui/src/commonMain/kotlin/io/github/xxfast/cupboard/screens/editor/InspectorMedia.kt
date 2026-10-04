package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.document.AudioElement
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.VideoElement
import kotlin.math.roundToInt

/**
 * The Movie and Audio segments, which are one body: a sound is a movie with no
 * picture, so it is this list with the Web URL and the Poster taken out.
 *
 * Multi-selection follows the image body. What describes how a movie plays (the
 * title, the trims, the volume, loop and autoplay) dresses every selected element
 * of the primary's kind, through [formatMedia]. What says which bytes are behind
 * it (Replace, the url, the poster) is the primary's alone, the way Replace Image
 * is.
 *
 * The trims are typed in seconds to one decimal and kept in milliseconds, and an
 * end of 0 is "to the end", which the field reads as empty. Whatever is typed goes
 * through as typed: the presenter clamps it, so a window that runs backwards
 * comes back as no trim rather than being refused here.
 */
@Composable
internal fun MediaBody(
    primary: Element,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
    onReplaceMedia: ((Element) -> Unit)?,
    onChoosePoster: ((VideoElement) -> Unit)?,
) {
    val settings: MediaSettings = primary.mediaSettings ?: return
    val enabled: Boolean = !primary.locked
    val video: VideoElement? = primary as? VideoElement
    val assetId: String? = video?.assetId ?: (primary as? AudioElement)?.assetId

    fun format(transform: (MediaSettings) -> MediaSettings): Boolean {
        val formatted: List<Element> = elements.formatMedia(primary, transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    fun preview(transform: (MediaSettings) -> MediaSettings) {
        val formatted: List<Element> = elements.formatMedia(primary, transform)
        if (formatted.isNotEmpty()) onPreview(formatted)
    }

    fun edit(edited: Element): Boolean {
        if (!enabled || edited == primary) return false

        onUpdate(listOf(edited))
        return true
    }

    EntryField(
        label = "Title",
        display = settings.title,
        enabled = enabled,
        monospace = false,
    ) { text ->
        if (text == settings.title) return@EntryField false
        format { it.copy(title = text) }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedField(
            label = "File",
            value = assetId?.let { id -> "${id.substringAfterLast('.', "").uppercase()} file" }
                ?: "None",
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Replace...",
            enabled = enabled && onReplaceMedia != null,
            onClick = { onReplaceMedia?.invoke(primary) },
            modifier = Modifier.weight(1f),
        )
    }

    // Only where there are no bytes: a movie with both plays the bytes, and the
    // url is then a note about where they came from rather than a setting.
    if (video != null && video.assetId == null) EntryField(
        label = "Web URL",
        display = video.webUrl.orEmpty(),
        enabled = enabled,
        monospace = false,
        placeholder = "https://",
    ) { text ->
        edit(video.copy(webUrl = text.trim().ifEmpty { null }))
    }

    if (video != null) {
        SwatchLabel("Poster")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TonalButton(
                label = "Choose...",
                enabled = enabled && onChoosePoster != null,
                onClick = { onChoosePoster?.invoke(video) },
                modifier = Modifier.weight(1f),
            )
            TonalButton(
                label = "Clear",
                enabled = enabled && video.posterAssetId != null,
                onClick = { edit(video.copy(posterAssetId = null)) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val start: String = settings.trimStartMs.asSeconds()
        EntryField(
            label = "Trim Start",
            display = start,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            unit = Units.Seconds,
        ) { text ->
            // The field shows tenths, so committing what it shows would round a
            // millisecond trim nobody touched.
            if (text.trim() == start) return@EntryField false
            val millis: Int = text.toMillisOrNull() ?: return@EntryField false
            format { it.copy(trimStartMs = millis) }
        }

        val end: String = if (settings.trimEndMs == 0) "" else settings.trimEndMs.asSeconds()
        EntryField(
            label = "Trim End",
            display = end,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            unit = if (settings.trimEndMs == 0) Units.None else Units.Seconds,
            placeholder = "End",
        ) { text ->
            if (text.trim() == end) return@EntryField false
            val millis: Int =
                if (text.isBlank()) 0
                else text.toMillisOrNull() ?: return@EntryField false
            format { it.copy(trimEndMs = millis) }
        }
    }

    SwatchLabel("Volume")
    SliderRow(
        fraction = settings.volume,
        valueLabel = "${(settings.volume * 100).roundToInt()}%",
        enabled = enabled,
        onDrag = { value -> preview { it.copy(volume = value) } },
        onRelease = { value -> format { it.copy(volume = value) } },
    )

    AppearanceRow(
        label = "Loop",
        checked = settings.loop,
        onToggle = if (!enabled) null else ({ on -> format { it.copy(loop = on) } }),
    )
    AppearanceRow(
        label = "Start on Reveal",
        checked = settings.autoplay,
        onToggle = if (!enabled) null else ({ on -> format { it.copy(autoplay = on) } }),
    )
}

/**
 * The settings a movie and a sound share, lifted out of either so the body edits
 * one shape: [VideoElement] and [AudioElement] carry the same six fields without
 * a type in common to read them through.
 */
private data class MediaSettings(
    val title: String,
    val trimStartMs: Int,
    val trimEndMs: Int,
    val volume: Float,
    val loop: Boolean,
    val autoplay: Boolean,
)

private val Element.mediaSettings: MediaSettings?
    get() = when (this) {
        is VideoElement -> MediaSettings(title, trimStartMs, trimEndMs, volume, loop, autoplay)
        is AudioElement -> MediaSettings(title, trimStartMs, trimEndMs, volume, loop, autoplay)
        else -> null
    }

private fun Element.withMediaSettings(settings: MediaSettings): Element = when (this) {
    is VideoElement -> copy(
        title = settings.title,
        trimStartMs = settings.trimStartMs,
        trimEndMs = settings.trimEndMs,
        volume = settings.volume,
        loop = settings.loop,
        autoplay = settings.autoplay,
    )

    is AudioElement -> copy(
        title = settings.title,
        trimStartMs = settings.trimStartMs,
        trimEndMs = settings.trimEndMs,
        volume = settings.volume,
        loop = settings.loop,
        autoplay = settings.autoplay,
    )

    else -> this
}

/**
 * The unlocked elements of [primary]'s kind with [transform] applied, dropping
 * the ones it left as they were: the image body's `formatImages`, over movies or
 * over sounds. A movie and a sound selected together format apart, since a
 * segment speaks for one kind.
 */
private fun List<Element>.formatMedia(
    primary: Element,
    transform: (MediaSettings) -> MediaSettings,
): List<Element> = mapNotNull { element ->
    if (element::class != primary::class || element.locked) return@mapNotNull null
    val settings: MediaSettings = element.mediaSettings ?: return@mapNotNull null
    val formatted: Element = element.withMediaSettings(transform(settings))
    return@mapNotNull if (formatted == element) null else formatted
}

/** Milliseconds as seconds to one decimal, rounded to the nearest tenth. */
private fun Int.asSeconds(): String {
    val tenths: Int = (this + 50) / 100
    return "${tenths / 10}.${tenths % 10}"
}

/** Typed seconds as milliseconds, or null for anything that is not a time. */
private fun String.toMillisOrNull(): Int? = trim()
    .removeSuffix(Units.Seconds)
    .trim()
    .toFloatOrNull()
    ?.takeIf { it >= 0f }
    ?.let { seconds -> (seconds * 1000).roundToInt() }
