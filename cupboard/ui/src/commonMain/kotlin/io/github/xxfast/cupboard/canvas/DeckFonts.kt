package io.github.xxfast.cupboard.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.DeckFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The deck's own families, by name, as the canvas draws them: what a
 * [io.github.xxfast.cupboard.document.TextElement.fontName] resolves to first.
 *
 * Only the cuts that have finished loading are in here, so a family whose bytes
 * are still being read is missing and its text draws in the fallback until they
 * land. Names match case aside, the way a font manager matches them.
 */
class DeckFontFamilies(families: Map<String, FontFamily>) {
    private val families: Map<String, FontFamily> =
        families.mapKeys { (name, _) -> name.lowercase() }

    operator fun get(name: String): FontFamily? = families[name.lowercase()]

    override fun equals(other: Any?): Boolean =
        other is DeckFontFamilies && other.families == families

    override fun hashCode(): Int = families.hashCode()

    companion object {
        val None: DeckFontFamilies = DeckFontFamilies(emptyMap())
    }
}

/**
 * The deck's families for every text renderer under it, provided by
 * [ProvideDeckFonts]. A composition local for [LocalAssetStore]'s reason: a box
 * deep in a group needs them as much as a top-level one, and nothing between has
 * any business knowing about fonts. None outside a provider, so a preview or a
 * test with no deck draws every box in its system face or its fallback.
 */
val LocalDeckFonts = compositionLocalOf { DeckFontFamilies.None }

/**
 * Loaded deck font cuts, by asset id: [AssetImageCache]'s counterpart for fonts.
 *
 * Snapshot state, so a cut landing redraws every box set in its family. Unbounded,
 * unlike the image cache: a deck carries a handful of fonts, and each is small
 * next to one photo.
 *
 * [Shared] is what [LocalDeckFontCache] defaults to, process-wide on purpose: a
 * one-frame render (a thumbnail, an export frame) never gets the frame a load
 * would land in, so it draws with whatever the canvas has already loaded. Hand
 * one to [warm] first to have them all regardless.
 */
class DeckFontCache {
    private val fonts: MutableMap<String, Font> = mutableStateMapOf()

    fun get(assetId: String): Font? = fonts[assetId]

    fun put(assetId: String, font: Font) {
        fonts[assetId] = font
    }

    /**
     * [deck]'s families out of what has loaded, grouped by family so a Light,
     * a Bold and an Italic under one name are one [FontFamily] whose cuts the
     * text's own weight and slant pick between.
     */
    fun families(deck: List<DeckFont>): DeckFontFamilies {
        val loaded: Map<String, FontFamily> = deck
            .groupBy { it.family.lowercase() }
            .mapNotNull { (family, cuts) ->
                val ready: List<Font> = cuts.mapNotNull { get(it.assetId) }
                if (ready.isEmpty()) null else family to FontFamily(ready)
            }
            .toMap()
        return if (loaded.isEmpty()) DeckFontFamilies.None else DeckFontFamilies(loaded)
    }

    /**
     * Every one of [deck]'s cuts read out of [assets] and loaded, the ones already
     * held skipped. What an export calls before its first frame, and what
     * [ProvideDeckFonts] runs in the background.
     *
     * A file that is missing, or that this platform cannot load (any of them, on
     * Android), is left out: its text draws in the fallback, which is what the
     * canvas shows for it too.
     */
    suspend fun warm(deck: List<DeckFont>, assets: AssetStore) {
        for (font in deck) {
            if (get(font.assetId) != null) continue
            val loaded: Font = withContext(Dispatchers.Default) {
                runCatching { assets.read(font.assetId) }.getOrNull()
                    ?.let { bytes -> deckFontOf(font, bytes) }
            } ?: continue
            put(font.assetId, loaded)
        }
    }

    companion object {
        val Shared: DeckFontCache = DeckFontCache()
    }
}

/** Where [ProvideDeckFonts] keeps what it loads. See [DeckFontCache.Shared]. */
val LocalDeckFontCache = staticCompositionLocalOf { DeckFontCache.Shared }

/**
 * [content] with [fonts], the deck's `Document.fonts`, provided as
 * [LocalDeckFonts] the way [rememberDeckFonts] loads them, out of
 * [LocalAssetStore].
 *
 * Wrapped around whatever draws a whole deck: the show, the presenter display,
 * the rasterizer. A shell that provides [LocalAssetStore] in the same breath
 * provides [LocalDeckFonts] from [rememberDeckFonts] beside it instead.
 */
@Composable
fun ProvideDeckFonts(fonts: List<DeckFont>, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDeckFonts provides rememberDeckFonts(fonts, LocalAssetStore.current),
        content = content,
    )
}

/**
 * [fonts], the deck's `Document.fonts`, as the families [LocalDeckFonts] wants:
 * read out of [assets] in the background, and handed back as they land, so text
 * set in one of them redraws once it is there.
 *
 * A cut already loaded is there on the first frame, which is what keeps a slide
 * from flashing its fallback on the way back to a face it just drew in.
 */
@Composable
fun rememberDeckFonts(fonts: List<DeckFont>, assets: AssetStore): DeckFontFamilies {
    val cache: DeckFontCache = LocalDeckFontCache.current

    LaunchedEffect(fonts, assets, cache) { cache.warm(fonts, assets) }

    val families: State<DeckFontFamilies> = remember(fonts, cache) {
        derivedStateOf { cache.families(fonts) }
    }
    return families.value
}

/**
 * [font]'s cut made out of [bytes] for the text engine, null where this platform
 * cannot load a font from bytes (Android) or the bytes are no font at all.
 */
internal expect fun deckFontOf(font: DeckFont, bytes: ByteArray): Font?
