@file:OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)

package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.Cupboard
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeLanguages
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.DefaultCodeBoxHeight
import io.github.xxfast.cupboard.document.DefaultCodeBoxWidth
import io.github.xxfast.cupboard.document.DefaultTextBoxHeight
import io.github.xxfast.cupboard.document.DefaultTextBoxWidth
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.ShapeCatalog
import io.github.xxfast.cupboard.document.ShapeCatalogEntry
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeGradient
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.codeBoxElement
import io.github.xxfast.cupboard.document.element
import io.github.xxfast.cupboard.document.formatCode
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.isBold
import io.github.xxfast.cupboard.document.textBoxElement
import io.github.xxfast.cupboard.document.toggleBold
import io.github.xxfast.cupboard.document.toggleItalic
import io.github.xxfast.cupboard.document.toggleStrikethrough
import io.github.xxfast.cupboard.document.toggleUnderline
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.editor.EditorCanvas
import io.github.xxfast.cupboard.editor.FieldMenuBridge
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.editor.SnapKind
import io.github.xxfast.cupboard.editor
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.screens.editor.EditorState
import io.github.xxfast.cupboard.screens.editor.EditorViewModel
import io.github.xxfast.cupboard.screens.editor.FlipAxis
import io.github.xxfast.cupboard.screens.editor.InspectorTab
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import platform.AppKit.NSCursor
import platform.AppKit.NSCursorFrameResizeDirectionsAll
import platform.AppKit.NSCursorFrameResizePosition
import platform.AppKit.NSCursorFrameResizePositionBottomRight
import platform.AppKit.NSCursorFrameResizePositionTopRight
import platform.AppKit.NSImage
import platform.AppKit.NSView
import platform.Foundation.NSData
import platform.Foundation.NSMakeSize
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithBytes

/**
 * The chrome the shell floats over the canvas, in canvas coordinates. Fit has to
 * agree with the shell about them, so they are stated once, here.
 */
private val SIDEBAR = 212.dp
private val INSPECTOR = 282.dp
private val TOOLBAR = 52.dp
private val NOTES = 122.dp

/**
 * What a background control commits when the slide is not wearing that kind of
 * background yet: the deck's own ink, so switching kinds shows something
 * deliberate rather than the first swatch of a palette.
 */
private const val DEFAULT_BACKGROUND_COLOR: Long = 0xFF101223
private const val DEFAULT_GRADIENT_START: Long = 0xFF2A2452
private const val DEFAULT_GRADIENT_END: Long = 0xFF101223

/** macOS 15 is where the frame-resize cursors landed; the app still runs on 14. */
private val macOsMajorVersion: Long =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }

private fun resizeCursor(direction: ResizeDirection?): NSCursor = when (direction) {
    ResizeDirection.Horizontal -> NSCursor.resizeLeftRightCursor
    ResizeDirection.Vertical -> NSCursor.resizeUpDownCursor
    // Named for a corner, not an axis: BottomRight draws "\", TopRight "/".
    ResizeDirection.DiagonalDown -> frameResizeCursor(NSCursorFrameResizePositionBottomRight)
    ResizeDirection.DiagonalUp -> frameResizeCursor(NSCursorFrameResizePositionTopRight)
    null -> NSCursor.arrowCursor
}

private fun frameResizeCursor(position: NSCursorFrameResizePosition): NSCursor =
    if (macOsMajorVersion < 15) NSCursor.crosshairCursor
    else NSCursor.frameResizeCursorFromPosition(position, NSCursorFrameResizeDirectionsAll)

/**
 * Resize cursors set straight on AppKit. Compose's macOS backend can only show
 * its own internal cursor type, so a `PointerIcon` built out here would resolve
 * to a plain arrow; [NSCursor] is the way in. Putting the arrow back on dispose
 * is what covers the pointer leaving a handle, since that arrives as a new
 * direction (null) and disposes the old effect.
 */
private val AppKitResizeCursors = ResizeCursors { direction ->
    DisposableEffect(direction) {
        resizeCursor(direction).set()
        onDispose { NSCursor.arrowCursor.set() }
    }
    Modifier
}

/** One row of the navigator outline, for the native (SwiftUI) sidebar. */
class OutlineRow(
    /** What the row's own context menu acts on, whatever the selection is. */
    val slideId: String,
    val title: String,
    val depth: Int,
    val slideIndex: Int,
    val hasChildren: Boolean,
    val collapsed: Boolean,
    /**
     * The row's place in the presentation as the gutter draws it, empty for a
     * skipped slide: it is in the deck but has no place in the presentation.
     * Text rather than a number, so the shell has no null to spell.
     */
    val numberLabel: String,
    /** Kept in the deck, left out of the presentation. Drawn dimmed, Keynote-style. */
    val skipped: Boolean,
)

/**
 * The primary selected element's shared properties, flattened for the native
 * inspector: the first of a selection that may hold many, which is what every
 * single-element control speaks for. The setters it feeds edit the whole
 * selection, so this is what the inspector shows, not what it edits.
 *
 * A value, not a handle: the shell re-reads it whenever [EditorHost.onChange]
 * fires and sends edits back through the setters, so nothing here can drift out
 * of step with the document. [Element] itself never crosses the boundary; a
 * shell holding one could edit its way around a lock.
 */
class ElementProps(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val opacity: Float,
    /** Degrees clockwise, applied around the frame's center. */
    val rotation: Float,
    val flippedHorizontally: Boolean,
    val flippedVertically: Boolean,
    val locked: Boolean,
    /** "Text", "Shape", "Image", "Code" or "Group": what the inspector titles itself. */
    val kind: String,
)

/**
 * The primary selected element's text style, flattened for the native inspector,
 * and null unless that element is a [TextElement]. [ElementProps]'s companion:
 * the same "what the panel shows" contract, for the controls that only a text
 * box has.
 *
 * The setters it feeds edit every unlocked text box in the selection, so a mixed
 * selection shows the primary's style and formats all of them.
 */
class TextProps(
    val fontFamily: TextFont,
    /** The raw weight, for the popup. [isBold] is the same number read as a flag. */
    val weightValue: Int,
    val isBold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val size: Float,
    /** Packed ARGB, the document model's color format. */
    val color: Long,
    val align: TextAlign,
    /** A multiple of the font size, not points. */
    val lineHeight: Float,
    val listStyle: ListStyle,
    val link: String?,
)

/**
 * The primary selected element's shape style, flattened for the native
 * inspector, and null unless that element is a [ShapeElement]. [TextProps]'s
 * opposite number, with the same contract: what the panel shows, never a handle
 * onto the document.
 *
 * The optional parts of the model arrive as a flag plus the values a shell would
 * commit if it turned them on, the way the slide background does: [hasGradient]
 * false still carries stops, so switching to a gradient never has to invent one.
 *
 * [isRectangle] and [isLine] are what the panel hides controls by: a corner
 * radius means nothing to any kind but a rectangle, and only a line draws
 * arrowheads.
 */
class ShapeProps(
    /** What the shape is, spelled the way [ShapeKind] spells it. */
    val kindName: String,
    val isRectangle: Boolean,
    val isLine: Boolean,
    val cornerRadius: Float,
    /** Packed ARGB, the document model's color format. So is every color below. */
    val fill: Long,
    /** Whether the gradient is what paints, [fill] being what paints when it is not. */
    val hasGradient: Boolean,
    val gradientStart: Long,
    val gradientEnd: Long,
    /** CSS degrees: 0 points up and the angle turns clockwise. */
    val gradientAngle: Float,
    val strokeColor: Long,
    val strokeWidth: Float,
    val hasShadow: Boolean,
    val shadowColor: Long,
    val shadowBlur: Float,
    val startArrow: Boolean,
    val endArrow: Boolean,
    val label: String,
    val labelSize: Float,
)

/**
 * The primary selected element's code style, flattened for the native inspector,
 * and null unless that element is a [CodeElement]. The third of [TextProps]'s
 * family, same contract: what the panel shows, never a handle onto the document.
 *
 * [theme] is the enum's name rather than the enum, the way [ShapeProps.kindName]
 * spells its kind: the picker draws `codeThemes()` and hands a name back, so no
 * Kotlin enum has to cross into ObjC. [language] is already a plain string in
 * the model, and `codeLanguages()` is what a picker offers of it.
 */
class CodeProps(
    val language: String,
    /** Spelled the way [CodeTheme] spells it, one of `codeThemes()`. */
    val theme: String,
    val fontSize: Float,
    val showLineNumbers: Boolean,
    val wrap: Boolean,
)

/**
 * What a canvas context menu may offer, decided against the selection the click
 * that opened it settles on rather than the one in `states`.
 *
 * The click sends its event and calls the host back in the same breath, so by
 * the time the shell builds the menu the reduction may not have roundtripped
 * yet, and every selection-based can-fact is one step behind. These are the same
 * facts computed one step ahead, so the menu is never built against the
 * selection the user just left behind. The items themselves still act through
 * the selection-based methods: a human picking one is thousands of frames later.
 *
 * Plain vals, like [ElementProps]: nothing here is a handle onto the document.
 */
class ContextFacts(
    val canCut: Boolean,
    val canCopy: Boolean,
    val canPaste: Boolean,
    val canDelete: Boolean,
    /** Z-order, flip and align: one unlocked element is enough for all three. */
    val canArrange: Boolean,
    val canGroup: Boolean,
    val canUngroup: Boolean,
    val canDistribute: Boolean,
    val canLock: Boolean,
    /** "Lock" or "Unlock", following the primary the way the Arrange menu does. */
    val lockLabel: String,
)

/**
 * What a menu opened by a right-click inside an edit session may offer.
 * [ContextFacts]'s smaller cousin: these are the caret's verbs, not the
 * selection's, so they answer for the text in the field rather than the elements
 * on the slide. Read at pop time, like the other one.
 */
class FieldMenuFacts(
    val canCut: Boolean,
    val canCopy: Boolean,
    val canPaste: Boolean,
    val canSelectAll: Boolean,
)

/**
 * A running presentation: a Compose view playing a snapshot of the document.
 * The host shows [view] full screen and calls [dispose] when it tears it down.
 * Playback keys are the player's own business; it only calls back on exit.
 */
class PlaySession internal constructor(
    document: Document,
    startIndex: Int,
    onExit: () -> Unit,
) {
    private val composeView = ComposeNSView {
        PresentationPlayer(
            document = document,
            startIndex = startIndex,
            modifier = Modifier.fillMaxSize(),
            onExit = onExit,
        )
    }

    val view: NSView = composeView

    fun dispose() {
        composeView.dispose()
    }
}

/**
 * Adapter between a native macOS shell and the shared [EditorViewModel]: exposes
 * the Compose canvas as an NSView, and gives the SwiftUI sidebar its outline,
 * selection and change-notification API in ObjC-friendly shapes.
 * Only the canvas is Compose; the chrome around it is the host's business.
 * No state lives here, it all belongs to the view model.
 */
class EditorHost {
    // Private: the framework only exports this file's types, so the view model
    // stays a Kotlin-side detail. Swift talks to it through the methods below.
    // The factory owns the store and the path, which is deliberately the one the
    // Compose Desktop shell uses: two front ends onto one document, not two apps.
    private val viewModel = Cupboard.editor()

    /** The state the sidebar reads right now. Never stale: the canvas and this
     * are the same flow, so an edit made in Compose shows up here too. */
    private val state: EditorState get() = viewModel.states.value

    // The number is part of the cache key, not just the slide: skipping an
    // earlier slide renumbers every row after it without touching one of them.
    private class Thumbnail(val slide: Slide, val number: Int?, val width: Int, val image: NSImage)

    private val thumbnails = mutableMapOf<String, Thumbnail>()

    private val scope = CoroutineScope(Dispatchers.Main)

    /** View-local, not document state: null is Fit, otherwise a scale factor. */
    private val zoom = MutableStateFlow<Float?>(null)

    /** The well follows the host's appearance; slide content never does. */
    private val darkChrome = MutableStateFlow(true)

    /** Where a right-click on the canvas goes once the loop has been told. */
    private var contextClick: ((String?) -> Unit)? = null

    /**
     * The caret's own Cut/Copy/Paste/Select All, for the menu that pops inside an
     * edit session. Handing this to the canvas is also what stops compose from
     * drawing its own text menu there.
     */
    private val fieldMenu = FieldMenuBridge()

    /** Where a right-click inside an edit session goes. Canvas pixels, x then y. */
    private var fieldMenuClick: ((Double, Double) -> Unit)? = null

    /** The full-bleed content layer: the shell floats its glass panels over this. */
    val view: NSView = ComposeNSView {
        val state: EditorState by viewModel.states.collectAsState()
        val scale: Float? by zoom.collectAsState()
        val dark: Boolean by darkChrome.collectAsState()

        // Paint the canvas well ourselves: unpainted scene regions are undefined
        // (white) instead of showing the SwiftUI background through.
        val well = if (dark) Color(0xFF17181C) else Color(0xFFDCDCDA)
        // Fit measures against the space the slide may actually occupy, so the
        // gutters are the panels that are open right now, not a constant. The
        // canvas layer is still the whole window: at any fixed zoom the slide is
        // window-centred and runs under the glass, which is what sells it.
        val gutters = PaddingValues(
            start = if (state.sidebarOpen) SIDEBAR else 0.dp,
            top = TOOLBAR,
            end = if (state.inspectorOpen) INSPECTOR else 0.dp,
            bottom = if (state.showNotes) NOTES else 0.dp,
        )
        CompositionLocalProvider(LocalResizeCursors provides AppKitResizeCursors) {
            Box(Modifier.fillMaxSize().background(well), contentAlignment = Alignment.Center) {
                EditorCanvas(
                    slide = state.selectedSlide,
                    selectedElementIds = state.selectedElementIds,
                    marquee = state.marquee,
                    onSelectElement = viewModel::onSelectElement,
                    onToggleElementSelection = viewModel::onToggleElementSelection,
                    // The loop first, the shell second: the event is what settles
                    // the selection, and the menu the shell opens is only ever a
                    // reader of it. The position goes no further, the shell pops
                    // at the mouse event it is already holding.
                    onContextClick = { elementId, _ ->
                        viewModel.onContextClick(elementId)
                        contextClick?.invoke(elementId)
                    },
                    onPreviewMarquee = viewModel::onPreviewMarquee,
                    onEndMarquee = viewModel::onEndMarquee,
                    onUpdateElements = viewModel::onUpdateElements,
                    onPreviewElements = viewModel::onPreviewElements,
                    onPreviewCancel = viewModel::onCancelPreview,
                    editingElementId = state.editingElementId,
                    onBeginTextEdit = viewModel::onBeginTextEdit,
                    onEndTextEdit = viewModel::onEndTextEdit,
                    fieldMenuBridge = fieldMenu,
                    // Nothing to tell the loop: the caret has not moved and the
                    // document has not changed. Straight out to the shell, which
                    // pops the field menu at the event it is already holding.
                    onFieldContextClick = { position ->
                        fieldMenuClick?.invoke(position.x.toDouble(), position.y.toDouble())
                    },
                    modifier = if (scale == null) Modifier.fillMaxSize().padding(gutters)
                    else Modifier.fillMaxSize(),
                    zoom = scale,
                    // What the slide draws on itself, when it asks to: its place
                    // in the presentation is the document's to work out, not the
                    // canvas's.
                    number = state.slideNumber(state.selectedSlide.id),
                )
            }
        }
    }

    /** Zoom as a whole percentage, 0 meaning Fit. Kept ObjC-friendly on purpose. */
    fun zoomPercent(): Int = zoom.value?.let { (it * 100).roundToInt() } ?: 0

    fun setZoomPercent(percent: Int) {
        zoom.value = if (percent <= 0) null else percent / 100f
    }

    /** Follows the host's appearance. Chrome only: slide content stays dark. */
    fun setDarkChrome(dark: Boolean) {
        darkChrome.value = dark
    }

    fun outline(): List<OutlineRow> = state.outline().map { entry ->
        OutlineRow(
            slideId = entry.slideId,
            title = entry.title,
            depth = entry.depth,
            slideIndex = entry.slideIndex,
            hasChildren = entry.hasChildren,
            collapsed = entry.collapsed,
            numberLabel = entry.number?.toString() ?: "",
            skipped = entry.skipped,
        )
    }

    fun toggleCollapsed(index: Int) {
        val slide = state.document.slides.getOrNull(index) ?: return
        viewModel.onToggleCollapsed(slide.id)
    }

    fun undo() {
        viewModel.onUndo()
    }

    fun redo() {
        viewModel.onRedo()
    }

    /** What the Edit menu greys out. Fresh whenever [onChange] has just fired. */
    fun canUndo(): Boolean = state.canUndo

    fun canRedo(): Boolean = state.canRedo

    fun selectedSlideIndex(): Int = state.selectedSlideIndex()

    fun selectSlide(index: Int) {
        viewModel.onSelectSlideAt(index)
    }

    /**
     * Panel visibility, straight off the shared state. The shell reads these
     * rather than keeping its own copies: chrome that a menu item, a click and
     * the canvas can all move needs one owner, and it is the view model.
     */
    fun sidebarOpen(): Boolean = state.sidebarOpen

    fun inspectorOpen(): Boolean = state.inspectorOpen

    fun inspectorTab(): InspectorTab = state.inspectorTab

    fun showNotes(): Boolean = state.showNotes

    /** Notes for the selected slide, what the speaker-notes strip shows. */
    fun slideNotes(): String = state.selectedSlide.notes

    fun toggleSidebar() {
        viewModel.onToggleSidebar()
    }

    fun toggleNotes() {
        viewModel.onToggleNotes()
    }

    /** Canvas overlays, read the same way the panel toggles are: one owner. */
    fun showRulers(): Boolean = state.showRulers

    fun showGuides(): Boolean = state.showGuides

    fun toggleRulers() {
        viewModel.onToggleRulers()
    }

    fun toggleGuides() {
        viewModel.onToggleGuides()
    }

    /**
     * The four snap switches by [SnapKind] ordinal: 0 Center, 1 Edges, 2 Objects,
     * 3 Guides. An Int rather than the enum keeps the ObjC surface plain and lets
     * the menu render the lot as one loop, the way the shape catalog's index does.
     * A kind the enum doesn't have reads as off and writes nothing, since the
     * number crosses a language boundary on the way back.
     */
    fun snapEnabled(kind: Int): Boolean = when (SnapKind.entries.getOrNull(kind)) {
        SnapKind.Center -> state.snapToCenter
        SnapKind.Edges -> state.snapToEdges
        SnapKind.Objects -> state.snapToObjects
        SnapKind.Guides -> state.snapToGuides
        null -> false
    }

    fun setSnap(kind: Int, enabled: Boolean) {
        val snap: SnapKind = SnapKind.entries.getOrNull(kind) ?: return
        viewModel.onSetSnap(snap, enabled)
    }

    fun selectInspectorTab(tab: InspectorTab) {
        viewModel.onSelectInspectorTab(tab)
    }

    fun closeInspector() {
        viewModel.onCloseInspector()
    }

    /**
     * The shapes the insert menus offer, in the catalog's order. Titles alone:
     * what the shell needs is a list to draw and an index to hand back, and the
     * kinds themselves are the document's business.
     */
    fun shapeCatalog(): List<String> = ShapeCatalog.entries.map { it.title }

    /**
     * Puts the [index]th catalog shape on the selected slide, in the middle of
     * it, sized the way that entry inserts. An index the catalog doesn't have is
     * no insertion rather than a crash: the list came from [shapeCatalog], but it
     * crosses a language boundary on the way back.
     */
    fun insertShape(index: Int) {
        val entry: ShapeCatalogEntry = ShapeCatalog.entries.getOrNull(index) ?: return
        viewModel.onInsertElement(entry.element(state.insertionFrame(entry.width, entry.height)))
    }

    /** A text box in the middle of the slide, carrying the placeholder to type over. */
    fun insertTextBox() {
        val frame: Frame = state.insertionFrame(DefaultTextBoxWidth, DefaultTextBoxHeight)
        viewModel.onInsertElement(textBoxElement(frame))
    }

    /** A code block in the middle of the slide, carrying a snippet to type over. */
    fun insertCodeBox() {
        val frame: Frame = state.insertionFrame(DefaultCodeBoxWidth, DefaultCodeBoxHeight)
        viewModel.onInsertElement(codeBoxElement(frame))
    }

    /**
     * What the Format inspector shows, or null when nothing is selected: the
     * primary element, with the rest of the selection behind it.
     *
     * Every setter below resolves the selection the same way, at call time, so a
     * click that lands after the selection moved edits nothing rather than the
     * wrong elements.
     */
    fun selectedElement(): ElementProps? = state.primaryElement?.let { element ->
        ElementProps(
            x = element.frame.x,
            y = element.frame.y,
            width = element.frame.width,
            height = element.frame.height,
            opacity = element.opacity,
            rotation = element.rotation,
            flippedHorizontally = element.flippedHorizontally,
            flippedVertically = element.flippedVertically,
            locked = element.locked,
            kind = when (element) {
                is TextElement -> "Text"
                is ShapeElement -> "Shape"
                is ImageElement -> "Image"
                is CodeElement -> "Code"
                is GroupElement -> "Group"
            },
        )
    }

    /** How many elements are selected, for the inspector's "N selected" line. */
    fun selectionCount(): Int = state.selectedElements.size

    /**
     * The text style the Format inspector shows, null when the primary element
     * is not a text box. Read off the primary alone; the setters below write to
     * the whole selection.
     */
    fun selectedText(): TextProps? = (state.primaryElement as? TextElement)?.let { text ->
        TextProps(
            fontFamily = text.fontFamily,
            weightValue = text.fontWeight,
            isBold = text.isBold,
            italic = text.italic,
            underline = text.underline,
            strikethrough = text.strikethrough,
            size = text.fontSize,
            color = text.color,
            align = text.align,
            lineHeight = text.lineHeight,
            listStyle = text.listStyle,
            link = text.link,
        )
    }

    /**
     * Whether the Format menu has anything to act on: one unlocked text box in
     * the selection is enough, even one whose caret is up. Formatting is a
     * property of the whole box, so it applies mid-edit the same as it does
     * from the canvas, which is why this doesn't consult `isEditingText` the
     * way the clipboard verbs do.
     */
    fun canFormatText(): Boolean =
        state.selectedElements.any { it is TextElement && !it.locked }

    fun setSelectedTextFont(font: TextFont) {
        formatSelection { it.copy(fontFamily = font) }
    }

    /** The raw weight the popup picked. Bold is a point on this scale, not a flag. */
    fun setSelectedTextWeight(weight: Int) {
        formatSelection { it.copy(fontWeight = weight.coerceIn(100, 900)) }
    }

    /** Floors at a point: text with no size has nothing left to click back into. */
    fun setSelectedTextSize(size: Float) {
        formatSelection { it.copy(fontSize = size.coerceIn(1f, 400f)) }
    }

    fun toggleSelectedTextBold() {
        formatSelection { it.toggleBold() }
    }

    fun toggleSelectedTextItalic() {
        formatSelection { it.toggleItalic() }
    }

    fun toggleSelectedTextUnderline() {
        formatSelection { it.toggleUnderline() }
    }

    fun toggleSelectedTextStrikethrough() {
        formatSelection { it.toggleStrikethrough() }
    }

    fun setSelectedTextColor(argb: Long) {
        formatSelection { it.copy(color = argb) }
    }

    fun setSelectedTextAlign(align: TextAlign) {
        formatSelection { it.copy(align = align) }
    }

    /** A multiple of the font size. Floors where the lines stop being readable. */
    fun setSelectedTextLineHeight(lineHeight: Float) {
        formatSelection { it.copy(lineHeight = lineHeight.coerceIn(0.5f, 5f)) }
    }

    fun setSelectedTextList(style: ListStyle) {
        formatSelection { it.copy(listStyle = style) }
    }

    /** Blank is no link at all, so an emptied field clears it rather than storing "". */
    fun setSelectedTextLink(link: String?) {
        formatSelection { it.copy(link = link?.takeIf { url -> url.isNotBlank() }) }
    }

    // Every text setter goes through here: the core decides which of the
    // selection can take the change, and hands back only what actually moved.
    // Nothing back is nothing to commit, and so no history entry for a control
    // that was set to what it already said.
    private fun formatSelection(transform: (TextElement) -> TextElement) {
        val edits: List<Element> = state.selectedElements.formatText(transform)
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * The shape style the Format inspector shows, null when the primary element
     * is not a shape. Read off the primary alone; the setters below write to
     * every unlocked shape in the selection, the way the text ones do.
     *
     * A shape wearing no gradient and no shadow still answers with both, filled
     * in with what turning them on would commit: the stops start where the solid
     * fill is, so switching kinds changes nothing the eye can see until a stop
     * moves.
     */
    fun selectedShape(): ShapeProps? = (state.primaryElement as? ShapeElement)?.let { shape ->
        val gradient: ShapeGradient = shape.gradient ?: ShapeGradient(shape.fill, shape.fill)
        val shadow: ShapeShadow = shape.shadow ?: ShapeShadow()
        ShapeProps(
            kindName = shape.kind.name,
            isRectangle = shape.kind == ShapeKind.Rectangle,
            isLine = shape.kind == ShapeKind.Line,
            cornerRadius = shape.cornerRadius,
            fill = shape.fill,
            hasGradient = shape.gradient != null,
            gradientStart = gradient.start,
            gradientEnd = gradient.end,
            gradientAngle = gradient.angle,
            strokeColor = shape.strokeColor,
            strokeWidth = shape.strokeWidth,
            hasShadow = shape.shadow != null,
            shadowColor = shadow.color,
            shadowBlur = shadow.blur,
            startArrow = shape.startArrow,
            endArrow = shape.endArrow,
            label = shape.label,
            labelSize = shape.labelSize,
        )
    }

    /** One unlocked shape in the selection is enough for the shape controls. */
    fun canFormatShapes(): Boolean =
        state.selectedElements.any { it is ShapeElement && !it.locked }

    /** A solid fill. It only paints once the gradient is off; see [clearSelectedShapeGradient]. */
    fun setSelectedShapeFill(argb: Long) {
        formatShapes { it.copy(fill = argb) }
    }

    /** Paints instead of the fill, from [start] to [end] along [angle] CSS degrees. */
    fun setSelectedShapeGradient(start: Long, end: Long, angle: Float) {
        formatShapes { it.copy(gradient = ShapeGradient(start, end, angle)) }
    }

    /** Back to the solid fill, which the shape was carrying all along. */
    fun clearSelectedShapeGradient() {
        formatShapes { it.copy(gradient = null) }
    }

    /** The outline, or the whole of a line. Zero width is no outline at all. */
    fun setSelectedShapeStroke(color: Long, width: Float) {
        formatShapes { it.copy(strokeColor = color, strokeWidth = width.coerceAtLeast(0f)) }
    }

    /**
     * [enabled] false drops the shadow rather than clearing its numbers, so the
     * document says "no shadow" the one way the model spells it. On, a shape that
     * already had one keeps its offset: the panel has no control for that, and
     * losing it to a toggle would be a change nobody asked for.
     */
    fun setSelectedShapeShadow(enabled: Boolean, color: Long, blur: Float) {
        formatShapes { shape ->
            val shadow: ShapeShadow? = if (!enabled) null else {
                (shape.shadow ?: ShapeShadow()).copy(color = color, blur = blur.coerceAtLeast(0f))
            }
            return@formatShapes shape.copy(shadow = shadow)
        }
    }

    /** Rectangles only; every other kind stores it and ignores it. */
    fun setSelectedShapeCornerRadius(radius: Float) {
        formatShapes { it.copy(cornerRadius = radius.coerceAtLeast(0f)) }
    }

    /** Lines only, for the same reason. */
    fun setSelectedShapeArrows(start: Boolean, end: Boolean) {
        formatShapes { it.copy(startArrow = start, endArrow = end) }
    }

    /** The text drawn in the middle of the shape. Blank is no label. */
    fun setSelectedShapeLabel(label: String) {
        formatShapes { it.copy(label = label) }
    }

    /** Floors at a point, like the text box's: type with no size can't be clicked back into. */
    fun setSelectedShapeLabelSize(size: Float) {
        formatShapes { it.copy(labelSize = size.coerceIn(1f, 400f)) }
    }

    // The shape setters' [formatSelection]: the unlocked shapes of the selection,
    // minus the ones the change left where they were, committed as one edit and
    // so as one history entry. Nothing moved is nothing to commit.
    private fun formatShapes(transform: (ShapeElement) -> ShapeElement) {
        val edits: List<Element> = state.selectedElements.mapNotNull { element ->
            if (element !is ShapeElement || element.locked) return@mapNotNull null
            val formatted: ShapeElement = transform(element)
            return@mapNotNull if (formatted == element) null else formatted
        }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * The code style the Format inspector shows, null when the primary element
     * is not a code block. Read off the primary, written to every unlocked code
     * block in the selection, the way the text and shape ones work.
     */
    fun selectedCode(): CodeProps? = (state.primaryElement as? CodeElement)?.let { code ->
        CodeProps(
            language = code.language,
            theme = code.theme.name,
            fontSize = code.fontSize,
            showLineNumbers = code.showLineNumbers,
            wrap = code.wrap,
        )
    }

    /**
     * The languages a picker offers, in menu order. The document's list, not the
     * shell's: what highlights and what it is called are the core's business.
     */
    fun codeLanguages(): List<String> = CodeLanguages

    /** The syntax palettes, named the way [CodeTheme] names them, in its order. */
    fun codeThemes(): List<String> = CodeTheme.entries.map { it.name }

    /**
     * Free-form on the model, so this takes whatever the picker hands over: an
     * unknown name highlights as plain text rather than failing.
     */
    fun setCodeLanguage(language: String) {
        formatCodeBlocks { it.copy(language = language) }
    }

    /**
     * A theme by name, from [codeThemes]. A name the enum doesn't have writes
     * nothing rather than falling back to a palette nobody picked: the string
     * crosses a language boundary on the way back, the way the snap kinds do.
     */
    fun setCodeTheme(theme: String) {
        val picked: CodeTheme = CodeTheme.entries.firstOrNull { it.name == theme } ?: return
        formatCodeBlocks { it.copy(theme = picked) }
    }

    /** Floors at a point, like the text box's: code with no size can't be read. */
    fun setCodeFontSize(size: Float) {
        formatCodeBlocks { it.copy(fontSize = size.coerceIn(1f, 400f)) }
    }

    fun setCodeLineNumbers(enabled: Boolean) {
        formatCodeBlocks { it.copy(showLineNumbers = enabled) }
    }

    fun setCodeWrap(enabled: Boolean) {
        formatCodeBlocks { it.copy(wrap = enabled) }
    }

    // The code setters' [formatSelection]: the core picks the unlocked code
    // blocks and drops the ones the change left alone, so a control set to what
    // it already said spends no history entry.
    private fun formatCodeBlocks(transform: (CodeElement) -> CodeElement) {
        val edits: List<Element> = state.selectedElements.formatCode(transform)
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /** Two unlocked elements are what a group is made of. */
    fun canGroup(): Boolean = canGroup(state.selectedElements)

    /**
     * Ungrouping is a single-group act: two groups selected is a batch nothing
     * else in the app does, so the menu item goes dead rather than guessing.
     */
    fun canUngroup(): Boolean = canUngroup(state.selectedElements)

    // Every can-fact takes the selection it judges, so the same rule serves both
    // the live selection and the one a right-click is about to settle on.
    private fun canGroup(elements: List<Element>): Boolean =
        elements.size >= 2 && elements.count { !it.locked } >= 2

    private fun canUngroup(elements: List<Element>): Boolean {
        val group: Element = elements.singleOrNull() ?: return false
        return group is GroupElement && !group.locked
    }

    // The selection an edit may touch. A locked element answers to nothing but
    // the unlock, which the presenter enforces too; this keeps the shell from
    // sending edits it knows will be dropped.
    private fun editable(): List<Element> = editable(state.selectedElements)

    private fun editable(elements: List<Element>): List<Element> = elements.filter { !it.locked }

    /**
     * What a menu opened by a right-click on [elementId] may offer, [elementId]
     * being null over empty slide space. See [ContextFacts] for why the shell
     * asks for these instead of reading the selection-based facts.
     *
     * The selection it judges is worked out here by the same rule
     * `EditorEvent.ContextClick` reduces by: an element already in the selection
     * keeps the whole selection, anything else becomes the selection on its own,
     * empty space clears it. A deliberate duplicate of the reducer, because the
     * event has not roundtripped yet when this is called. The two move together.
     */
    fun contextFacts(elementId: String?): ContextFacts {
        val elements: List<Element> = contextSelection(elementId)
        val unlocked: List<Element> = editable(elements)
        val primary: Element? = elements.firstOrNull()
        // The same rule the Arrange menu greys itself out by: one unlocked
        // primary is enough to reorder, flip, or line up against the slide.
        val arrangeable: Boolean = primary != null && !primary.locked
        return ContextFacts(
            canCut = unlocked.isNotEmpty(),
            canCopy = elements.isNotEmpty(),
            canPaste = state.canPaste,
            canDelete = unlocked.isNotEmpty(),
            canArrange = arrangeable,
            canGroup = canGroup(elements),
            canUngroup = canUngroup(elements),
            // Two elements have no gap between them to equalize.
            canDistribute = arrangeable && elements.size >= 3,
            canLock = primary != null,
            lockLabel = if (primary?.locked == true) "Unlock" else "Lock",
        )
    }

    private fun contextSelection(elementId: String?): List<Element> {
        if (elementId == null) return emptyList()
        val selection: List<Element> = state.selectedElements
        if (selection.any { it.id == elementId }) return selection
        return listOfNotNull(state.selectedSlide.elements.firstOrNull { it.id == elementId })
    }

    /**
     * Commits a typed frame onto every selected element, the way Keynote's
     * inspector does: typing 40 into X puts them all at x = 40 rather than moving
     * them as a block. Sizes floor at one document unit: an element with no
     * extent has nothing left to click, so there is no way to select it back out.
     */
    fun setSelectedElementFrame(x: Float, y: Float, width: Float, height: Float) {
        val frame = Frame(
            x = x,
            y = y,
            width = width.coerceAtLeast(1f),
            height = height.coerceAtLeast(1f),
        )
        val edits: List<Element> = editable().map { it.update(frame = frame) }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * [commit] false is a slider still under the thumb: it folds into the document
     * so the canvas redraws, but makes no history entry. True is the release, and
     * the whole drag lands as one undo step however many elements it moved.
     */
    fun setSelectedElementOpacity(opacity: Float, commit: Boolean) {
        val edits: List<Element> = editable().map { it.update(opacity = opacity.coerceIn(0f, 1f)) }
        if (edits.isEmpty()) return
        if (commit) viewModel.onUpdateElements(edits) else viewModel.onPreviewElements(edits)
    }

    /** Degrees clockwise. Typed, so it commits: the shell has no rotate gesture yet. */
    fun setSelectedElementRotation(degrees: Float) {
        val edits: List<Element> = editable().map { it.update(rotation = degrees) }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    fun flipSelectedElement(axis: FlipAxis) {
        val ids: List<String> = state.selectedElementIds
        if (ids.isEmpty()) return
        viewModel.onFlipElements(ids, axis)
    }

    fun reorderSelectedElement(move: ZOrderMove) {
        val ids: List<String> = state.selectedElementIds
        if (ids.isEmpty()) return
        viewModel.onReorderElements(ids, move)
    }

    /** The one edit a locked element still answers to. The primary decides which way. */
    fun toggleSelectedElementLock() {
        val primary: Element = state.primaryElement ?: return
        viewModel.onSetElementsLocked(state.selectedElementIds, !primary.locked)
    }

    /** Wraps the selection into one group. Does nothing unless [canGroup]. */
    fun groupSelection() {
        if (!canGroup()) return
        viewModel.onGroupElements(state.selectedElementIds)
    }

    /** Breaks the selected group apart. Does nothing unless [canUngroup]. */
    fun ungroupSelection() {
        if (!canUngroup()) return
        val group: Element = state.primaryElement ?: return
        viewModel.onUngroupElements(group.id)
    }

    /** Two or more line up on their own bounds, a lone one on the slide. */
    fun alignSelection(edge: AlignEdge) {
        viewModel.onAlignElements(edge)
    }

    /** Equalizes the gaps across the selection. Needs three unlocked members. */
    fun distributeSelection(axis: Axis) {
        viewModel.onDistributeElements(axis)
    }

    /**
     * Edit > Delete, focus resolved: the selected slide when the navigator holds
     * the focus, the unlocked part of the element selection when the canvas
     * does. The name is the canvas-only one it started as, kept because the verb
     * only got broader and the shell's menu item is the same item.
     *
     * No guard of its own: [EditorState.canDelete] is what the menu greys out
     * by and what the presenter acts on, and a second copy of the rule out here
     * could only ever disagree with it.
     */
    fun canDelete(): Boolean = state.canDelete

    fun deleteSelection() {
        viewModel.onDelete()
    }

    /** A slide of nothing but locked elements has nothing left to clear. */
    fun canClearAll(): Boolean = state.selectedSlide.elements.any { !it.locked }

    /** Empties the selected slide of everything unlocked. Locked elements stay. */
    fun clearAll() {
        if (!canClearAll()) return
        viewModel.onClearAll()
    }

    /**
     * Removes the selected slide, and the run it was hiding if it was collapsed.
     * Always available: the last slide out leaves a fresh blank one behind, so
     * there is no state where this has nothing to do.
     */
    fun deleteSelectedSlide() {
        deleteSlide(state.selectedSlide.id)
    }

    /**
     * The Edit menu's pasteboard verbs, focus resolved like [deleteSelection]:
     * the navigator's selected slide when it holds the focus, the canvas's
     * element selection when it does. The presenter picks the layer off the
     * same state these can-facts read, so a live item and the edit it fires
     * can never be about different things, and none of them guards.
     *
     * The clipboard behind them is the app's own, not NSPasteboard: elements
     * carry style, builds and grouping that no system flavour describes, so
     * [canPaste] answers off the shared state rather than off what some other
     * app last copied. Paste is the one verb focus doesn't split, it lands
     * whatever the clipboard holds.
     *
     * `copy` is a reserved ObjC method family, so the exporter renames every
     * `copyX` to `doCopyX`, which is what the shell calls. `@ObjCName` doesn't
     * buy the name back: the rename is applied after it.
     */
    fun canCut(): Boolean = state.canCut

    fun cutSelection() {
        viewModel.onCut()
    }

    fun canCopy(): Boolean = state.canCopy

    fun copySelection() {
        viewModel.onCopy()
    }

    fun canPaste(): Boolean = state.canPaste

    fun paste() {
        if (!canPaste()) return
        viewModel.onPaste()
    }

    fun canDuplicate(): Boolean = state.canDuplicate

    fun duplicateSelection() {
        viewModel.onDuplicate()
    }

    /** Style comes off the primary alone, the one the inspector speaks for. */
    fun canCopyStyle(): Boolean = state.primaryElement != null

    fun copyStyle() {
        val primary: Element = state.primaryElement ?: return
        viewModel.onCopyStyle(primary.id)
    }

    /** Needs a style on the clipboard and something unlocked to wear it. */
    fun canPasteStyle(): Boolean = state.canPasteStyle && editable().isNotEmpty()

    fun pasteStyle() {
        if (!canPasteStyle()) return
        viewModel.onPasteStyle(state.selectedElementIds)
    }

    /**
     * The slide verbs, by id: what a navigator row's context menu acts through,
     * a row being a slide the selection may not be on. Always available, all of
     * them: the core keeps the document non-empty, so cutting or deleting the
     * last slide leaves a blank one behind rather than nothing.
     *
     * `copy` is a reserved ObjC method family, so [copySlide] reaches the shell
     * as `doCopySlide`, the same rename the `doCopy` members above carry.
     */
    fun addSlideAfter(id: String) {
        viewModel.onAddSlide(id)
    }

    fun deleteSlide(id: String) {
        viewModel.onDeleteSlide(id)
    }

    fun duplicateSlide(id: String) {
        viewModel.onDuplicateSlide(id)
    }

    fun cutSlide(id: String) {
        viewModel.onCutSlide(id)
    }

    fun copySlide(id: String) {
        viewModel.onCopySlide(id)
    }

    /**
     * Pastes after [id] rather than after the selection. The select goes first
     * and the events flow serializes the two, so the paste deterministically
     * lands after that row however far the selection was from it.
     */
    fun pasteAfterSlide(id: String) {
        if (!canPaste()) return
        viewModel.onSelectSlide(id)
        viewModel.onPaste()
    }

    /**
     * A dropped navigator drag: the row [id] names lands in the gap under
     * [afterId], null being the gap above the first row, or under [afterId]
     * itself when [nest]. A drop onto the row's own gap costs no history entry,
     * so the shell may send every drop.
     */
    fun moveSlide(id: String, afterId: String?, nest: Boolean) {
        viewModel.onMoveSlide(id, afterId, nest)
    }

    /** Whether the slide is out of the presentation, for a menu item's title. */
    fun isSlideSkipped(id: String): Boolean =
        state.document.slides.firstOrNull { it.id == id }?.skipped == true

    fun setSlideSkipped(id: String, skipped: Boolean) {
        viewModel.onSetSlideSkipped(id, skipped)
    }

    /** The same verbs against the selected slide, for the Slide menu. */
    fun addSlideAfterSelection() {
        addSlideAfter(state.selectedSlide.id)
    }

    fun cutSelectedSlide() {
        cutSlide(state.selectedSlide.id)
    }

    fun copySelectedSlide() {
        copySlide(state.selectedSlide.id)
    }

    fun duplicateSelectedSlide() {
        duplicateSlide(state.selectedSlide.id)
    }

    fun isSelectedSlideSkipped(): Boolean = state.selectedSlide.skipped

    fun toggleSelectedSlideSkipped() {
        val slide: Slide = state.selectedSlide
        setSlideSkipped(slide.id, !slide.skipped)
    }

    /**
     * The selected slide's own properties, for the Document inspector. Primitives
     * rather than the slide itself, the way [ElementProps] is: a shell holding a
     * [Slide] could edit its way around anything the core decides.
     *
     * Every setter commits through `UpdateSlide`, so one click is one history
     * entry, and each resolves the selected slide at call time.
     */
    fun slideNumberVisible(): Boolean = state.selectedSlide.showsSlideNumber

    fun setSlideNumberVisible(visible: Boolean) {
        val slide: Slide = state.selectedSlide
        if (slide.showsSlideNumber == visible) return
        viewModel.onUpdateSlide(slide.copy(showsSlideNumber = visible))
    }

    /** What the slide paints behind its elements: 0 the deck's own, 1 color, 2 gradient. */
    fun backgroundKind(): Int = when (state.selectedSlide.background) {
        null -> 0
        is SlideBackground.Color -> 1
        is SlideBackground.Gradient -> 2
    }

    /**
     * The colors the background controls show, packed ARGB. A slide wearing
     * something else answers with the value the shell would commit if it switched
     * to this kind, so switching never has to invent one of its own.
     */
    fun backgroundColor(): Long =
        (state.selectedSlide.background as? SlideBackground.Color)?.color ?: DEFAULT_BACKGROUND_COLOR

    fun backgroundGradientStart(): Long =
        (state.selectedSlide.background as? SlideBackground.Gradient)?.start ?: DEFAULT_GRADIENT_START

    fun backgroundGradientEnd(): Long =
        (state.selectedSlide.background as? SlideBackground.Gradient)?.end ?: DEFAULT_GRADIENT_END

    /** Back to the deck's own background: the slide stops carrying one at all. */
    fun setBackgroundDefault() {
        setBackground(null)
    }

    fun setBackgroundColor(argb: Long) {
        setBackground(SlideBackground.Color(argb))
    }

    /** The angle stays the model's, which is the deck's own: only the stops are the shell's. */
    fun setBackgroundGradient(start: Long, end: Long) {
        setBackground(SlideBackground.Gradient(start, end))
    }

    private fun setBackground(background: SlideBackground?) {
        val slide: Slide = state.selectedSlide
        if (slide.background == background) return
        viewModel.onUpdateSlide(slide.copy(background = background))
    }

    /**
     * Registers [callback], fired whenever the editor state changes (including
     * edits made inside the Compose canvas), and returns the unsubscribe for the
     * host to call when it goes away. Swift can't observe a Kotlin StateFlow, so
     * this is how the sidebar learns to re-pull its outline and thumbnails.
     *
     * Collecting is also what starts the presenter: the state flow is lazily
     * shared, so the host subscribing at launch is what gets the editor running.
     */
    fun onChange(callback: () -> Unit): () -> Unit {
        val job = scope.launch { viewModel.states.collect { callback() } }
        return { job.cancel() }
    }

    /**
     * Registers [callback], fired on the main thread when the canvas is
     * right-clicked, carrying the element under it or null for empty slide space.
     * The loop has already been told by then; this is only the shell's cue to
     * open a menu, which it builds from [contextFacts] for that same id.
     *
     * One registration, like the menu it opens: last in wins.
     */
    fun setContextClickCallback(callback: (String?) -> Unit) {
        contextClick = callback
    }

    /**
     * Registers [callback], fired on the main thread when a right-click lands
     * inside an edit session, carrying where it landed in the canvas layer's own
     * space, in compose pixels. Nothing has been told anything by then: the click
     * moved neither the caret nor the selection, it only asked for a menu.
     *
     * One registration, like the one above: last in wins.
     */
    fun setFieldMenuCallback(callback: (Double, Double) -> Unit) {
        fieldMenuClick = callback
    }

    /**
     * What the field menu may offer right now. Empty when no caret is in
     * anything, which is also when the menu has no business popping.
     */
    fun fieldMenuFacts(): FieldMenuFacts {
        val selection: Boolean = fieldMenu.hasSelection
        return FieldMenuFacts(
            canCut = selection,
            canCopy = selection,
            canPaste = fieldMenu.canPaste,
            canSelectAll = fieldMenu.canSelectAll,
        )
    }

    fun fieldCut() = fieldEdit(fieldMenu::cut)

    fun fieldCopy() = fieldMenu.copy()

    fun fieldPaste() = fieldEdit(fieldMenu::paste)

    fun fieldSelectAll() = fieldEdit(fieldMenu::selectAll)

    /**
     * A field verb picked from a menu, i.e. run from AppKit's tracking loop
     * rather than from anything compose called. The edit lands in the field's own
     * snapshot state, and nothing here is inside a compose handler, so the apply
     * notification that would ordinarily ride along with one is sent by hand: the
     * scene invalidates off that, and a caret that moved without a repaint is the
     * whole bug class this app has been bitten by before.
     */
    private fun fieldEdit(verb: () -> Unit) {
        verb()
        Snapshot.sendApplyNotifications()
    }

    /**
     * Starts playing the document as it stands, from the selected slide.
     * [onExit] fires on the main thread when the player asks to stop (Escape).
     */
    fun startPlay(onExit: () -> Unit): PlaySession =
        PlaySession(state.document, state.selectedSlideIndex().coerceAtLeast(0), onExit)

    /**
     * Rasterizes a slide with the shared Compose renderer for native chrome to
     * display (navigator thumbs). Rendered at 2x for retina, sized in points.
     *
     * Cached by slide value: the host re-pulls every row on every state
     * emission, and a drag emits one per pointer sample, so rendering each row
     * every time starved the main thread. Only the slide that actually changed
     * misses the cache.
     *
     * Mid-gesture the cache answers even for the slide being dragged, stale on
     * purpose: one render costs 20-30ms, and paying that per pointer sample ate
     * three quarters of the main thread. The commit clears [EditorState.isPreviewing]
     * and the row catches up then, one render per gesture.
     */
    fun thumbnail(index: Int, width: Int): NSImage? {
        val slide = state.document.allSlides().getOrNull(index) ?: return null
        val number: Int? = state.slideNumber(slide.id)
        val cached = thumbnails[slide.id]
        // Settled, the cache has to match the slide; mid-gesture any render of it will do.
        val usable = cached != null && cached.width == width &&
            ((cached.slide == slide && cached.number == number) || state.isPreviewing)
        if (usable) return cached.image

        val height = (width * Document.SLIDE_HEIGHT / Document.SLIDE_WIDTH).toInt()
        val skiaImage = renderComposeScene(width * 2, height * 2) {
            SlideView(slide, number = number)
        }
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val nsData = png.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), png.size.toULong())
        }
        val image = NSImage(data = nsData)?.apply {
            setSize(NSMakeSize(width.toDouble(), height.toDouble()))
        } ?: return null

        thumbnails[slide.id] = Thumbnail(slide, number, width, image)
        return image
    }

    /**
     * Stops the editor (autosave included) and tears the scope down. Optional: a
     * document app keeps one editor for its whole life, so a host that never
     * closes the editor can leave this alone and let process exit do it.
     */
    fun close() {
        scope.cancel()
        viewModel.close()
    }
}
