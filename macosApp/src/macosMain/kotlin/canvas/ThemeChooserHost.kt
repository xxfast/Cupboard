@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import io.github.xxfast.cupboard.Cupboard
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.preview
import io.github.xxfast.cupboard.newDocument
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserState
import io.github.xxfast.cupboard.screens.chooser.ThemeChooserViewModel
import io.github.xxfast.cupboard.themeChooser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.skia.EncodedImageFormat
import platform.AppKit.NSImage
import platform.Foundation.NSData
import platform.Foundation.NSMakeSize
import platform.Foundation.dataWithBytes

/**
 * The new-deck chooser's Swift-facing side: the view model's state as flat
 * accessors, its events as methods, and the thumbnails as `NSImage`s.
 *
 * Same shape as [EditorHost]: Swift can't observe a Kotlin StateFlow, so
 * [onChange] is the cue to re-read.
 */
class ThemeChooserHost {
    private val viewModel: ThemeChooserViewModel = Cupboard.themeChooser(Dispatchers.Main)
    private val scope = CoroutineScope(Dispatchers.Main)

    /** The last state seen, refreshed before every [onChange] callback fires. */
    private var state: ThemeChooserState = ThemeChooserState()

    private val thumbnails: HashMap<String, NSImage> = HashMap()

    /**
     * Registers [callback], fired whenever the pick changes, and returns the
     * unsubscribe. Collecting is also what starts the presenter: the state flow
     * is lazily shared, so nothing moves until the shell subscribes.
     */
    fun onChange(callback: () -> Unit): () -> Unit {
        val job = scope.launch {
            viewModel.states.collect {
                state = it
                callback()
            }
        }
        return { job.cancel() }
    }

    fun categoryNames(): List<String> = state.categories.map { it.name }

    /** The names on [category]'s shelf, in the order the grid draws them. */
    fun themeNames(category: String): List<String> =
        state.categories.firstOrNull { it.name == category }?.themes?.map { it.name }.orEmpty()

    fun category(): String = state.category

    fun theme(): String = state.theme

    /** Position in [SlideSizePreset.entries], which is what [selectSize] takes back. */
    fun size(): Int = SlideSizePreset.entries.indexOf(state.size)

    fun sizeTitles(): List<String> = SlideSizePreset.entries.map { it.title }

    /** The width over the height of a slide at the current size, for the grid's cells. */
    fun aspect(): Float = state.size.width / state.size.height

    fun selectCategory(name: String) = viewModel.onSelectCategory(name)

    fun selectTheme(name: String) = viewModel.onSelectTheme(name)

    fun selectSize(index: Int) = viewModel.onSelectSize(SlideSizePreset.entries[index])

    /**
     * [themeName]'s preview slide at the current size, [width] points across.
     * Cached per theme, size and width: the grid asks on every redraw.
     */
    fun thumbnail(themeName: String, width: Int): NSImage? {
        val size: SlideSizePreset = state.size
        val key = "$themeName|${size.name}|$width"
        thumbnails[key]?.let { return it }

        val theme: Theme = state.categories.flatMap { it.themes }.firstOrNull { it.name == themeName } ?: return null
        val deck: Document = theme.preview(size)
        val slide = deck.slides.firstOrNull() ?: return null
        val layout = deck.layouts.firstOrNull { it.id == slide.layoutId }

        val height = (width * deck.slideHeight / deck.slideWidth).toInt()
        val skiaImage = renderComposeScene(width * 2, height * 2) {
            SlideView(
                slide = slide,
                layout = layout,
                background = deck.background,
                slideWidth = deck.slideWidth,
                slideHeight = deck.slideHeight,
            )
        }
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val nsData = png.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), png.size.toULong())
        }
        val image = NSImage(data = nsData)?.apply {
            setSize(NSMakeSize(width.toDouble(), height.toDouble()))
        } ?: return null
        thumbnails[key] = image
        return image
    }

    /**
     * The chosen theme and size laid down as a new bundle, and where it went.
     * Open it like any other deck; nothing is opened here. Empty when the pick
     * holds no theme.
     */
    fun create(): String {
        val theme: Theme = state.selectedTheme ?: return ""
        return Cupboard.newDocument(theme = theme, size = state.size)
    }

    fun close() = viewModel.close()
}
