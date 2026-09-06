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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import io.github.xxfast.cupboard.Cupboard
import io.github.xxfast.cupboard.OpenResult
import io.github.xxfast.cupboard.forgetRecent
import io.github.xxfast.cupboard.newDocument
import io.github.xxfast.cupboard.openDocument
import io.github.xxfast.cupboard.recentDocuments
import io.github.xxfast.cupboard.saveAs
import io.github.xxfast.cupboard.document.ActionKind
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Build
import io.github.xxfast.cupboard.document.BuildAction
import io.github.xxfast.cupboard.document.BuildDelivery
import io.github.xxfast.cupboard.document.BuildEffect
import io.github.xxfast.cupboard.document.BuildKind
import io.github.xxfast.cupboard.document.BuildTrigger
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeLanguages
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.DefaultCodeBoxHeight
import io.github.xxfast.cupboard.document.DefaultCodeBoxWidth
import io.github.xxfast.cupboard.document.DefaultDiagramHeight
import io.github.xxfast.cupboard.document.DefaultDiagramWidth
import io.github.xxfast.cupboard.document.DefaultEquationHeight
import io.github.xxfast.cupboard.document.DefaultEquationWidth
import io.github.xxfast.cupboard.document.DefaultImageHeight
import io.github.xxfast.cupboard.document.DefaultImageWidth
import io.github.xxfast.cupboard.document.DefaultTerminalHeight
import io.github.xxfast.cupboard.document.DefaultTerminalWidth
import io.github.xxfast.cupboard.document.DefaultTextBoxHeight
import io.github.xxfast.cupboard.document.DefaultTextBoxWidth
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GalleryElement
import io.github.xxfast.cupboard.document.GalleryImage
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ImageAdjust
import io.github.xxfast.cupboard.document.ImageElement
import io.github.xxfast.cupboard.document.ImageMask
import io.github.xxfast.cupboard.document.LinkTarget
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.PlaybackType
import io.github.xxfast.cupboard.document.ShapeCatalog
import io.github.xxfast.cupboard.document.ShapeCatalogEntry
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeGradient
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.SlideTransition
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.TransitionTrigger
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.action
import io.github.xxfast.cupboard.document.allSlides
import io.github.xxfast.cupboard.document.applyingObjectStyle
import io.github.xxfast.cupboard.document.codeBoxElement
import io.github.xxfast.cupboard.document.diagramElement
import io.github.xxfast.cupboard.document.element
import io.github.xxfast.cupboard.document.elementById
import io.github.xxfast.cupboard.document.equationElement
import io.github.xxfast.cupboard.document.fitted
import io.github.xxfast.cupboard.document.formatCode
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.galleryElement
import io.github.xxfast.cupboard.document.imageElement
import io.github.xxfast.cupboard.document.isBold
import io.github.xxfast.cupboard.document.layoutOf
import io.github.xxfast.cupboard.document.newId
import io.github.xxfast.cupboard.document.previewOf
import io.github.xxfast.cupboard.document.resolvedLink
import io.github.xxfast.cupboard.document.slideById
import io.github.xxfast.cupboard.document.slideSizePreset
import io.github.xxfast.cupboard.document.terminalElement
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
import io.github.xxfast.cupboard.export.ExportedFile
import io.github.xxfast.cupboard.export.toCupProject
import io.github.xxfast.cupboard.play.PlayerController
import io.github.xxfast.cupboard.play.PresentationPlayer
import io.github.xxfast.cupboard.play.PresenterView
import io.github.xxfast.cupboard.play.rememberPlayerController
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.Surface as SkiaSurface
import org.jetbrains.skia.Image as SkiaImage
import platform.AppKit.NSCursor
import platform.AppKit.NSCursorFrameResizeDirectionsAll
import platform.AppKit.NSCursorFrameResizePosition
import platform.AppKit.NSCursorFrameResizePositionBottomRight
import platform.AppKit.NSCursorFrameResizePositionTopRight
import platform.AppKit.NSImage
import platform.AppKit.NSView
import platform.AppKit.NSWorkspace
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSMakeRect
import platform.Foundation.NSMakeSize
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.posix.memcpy

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
/**
 * How much of the slide a freshly inserted image may take. Room to see it and
 * room to put something beside it: an image that lands filling the slide is one
 * the first thing you do is shrink.
 */
private const val IMAGE_INSERT_SHARE: Float = 0.6f

/** The smallest a mask window may be, in the image's own normalised units. */
private const val MIN_MASK_EXTENT: Float = 0.02f

/**
 * How many gallery strip thumbnails are kept decoded. A strip is a dozen at the
 * most and a deck holds a handful of galleries, so this outlives switching
 * between them without holding a whole deck's pictures at once.
 */
private const val GALLERY_THUMBNAILS: Int = 48

private const val DEFAULT_BACKGROUND_COLOR: Long = 0xFF101223
private const val DEFAULT_GRADIENT_START: Long = 0xFF2A2452
private const val DEFAULT_GRADIENT_END: Long = 0xFF101223

/**
 * What the transition kinds are called in the popup, in [TransitionKind]'s own
 * order, so a place in this list is an ordinal. Spelled here rather than on the
 * enum because two of them read as two words in a menu and as one in code.
 */
private val TransitionKindTitles: List<String> =
    listOf("None", "Dissolve", "Push", "Move In", "Wipe", "Magic Move")

/**
 * What the playback types are called in the Document panel's popup, in
 * [PlaybackType]'s own order, so a place in this list is an ordinal. Spelled
 * here rather than on the enum for the reason [TransitionKindTitles] is: two of
 * them read as two words in a menu and as one in code.
 */
private val PlaybackTypeTitles: List<String> = listOf("Normal", "Self-Playing", "Links Only")

/**
 * Where a link may point, in the order the popup offers them, "None" first for
 * an element that points nowhere. A place in this list is the whole protocol,
 * the way a transition's kind is a place in its own, except that [LinkTarget] is
 * a sealed interface rather than an enum: there is no ordinal to travel, so this
 * list is where the order lives, and [linkKindIndex] is the only reader of it.
 */
private val LinkKindTitles: List<String> = listOf(
    "None",
    "Next Slide",
    "Previous Slide",
    "First Slide",
    "Last Slide",
    "Exit Show",
    "Webpage",
    "Slide",
)

/**
 * The outlines an image may be cut to, in the order the mask popup offers them,
 * paired with what each is called. A place in these lists is the whole protocol,
 * the way a transition kind's is, and -1 is an image showing all of itself.
 *
 * Not every [ShapeKind]: a line has no inside to keep, so masking to one would
 * hide the picture entirely. The rest of the catalog reads as a window.
 */
private val MaskKinds: List<ShapeKind> = listOf(
    ShapeKind.Rectangle,
    ShapeKind.Ellipse,
    ShapeKind.Triangle,
    ShapeKind.Diamond,
    ShapeKind.Star,
    ShapeKind.Polygon,
    ShapeKind.Arrow,
    ShapeKind.QuoteBubble,
    ShapeKind.Callout,
)

/** What [MaskKinds] are called in the popup, in its order. */
private val MaskKindTitles: List<String> = listOf(
    "Rectangle",
    "Ellipse",
    "Triangle",
    "Diamond",
    "Star",
    "Polygon",
    "Arrow",
    "Quote Bubble",
    "Callout",
)

/** Where [target] sits in [LinkKindTitles]. Null points nowhere, which is 0. */
private fun linkKindIndex(target: LinkTarget?): Int = when (target) {
    null -> 0
    LinkTarget.Next -> 1
    LinkTarget.Previous -> 2
    LinkTarget.First -> 3
    LinkTarget.Last -> 4
    LinkTarget.ExitShow -> 5
    is LinkTarget.Url -> 6
    is LinkTarget.Slide -> 7
}

/**
 * The other way round: what the [kindIndex]th entry of [LinkKindTitles] points
 * at. [url] and [slideId] are only read by the two kinds that carry one, so the
 * shell may send both whatever it picked.
 */
private fun linkTargetOf(kindIndex: Int, url: String, slideId: String): LinkTarget? =
    when (kindIndex) {
        1 -> LinkTarget.Next
        2 -> LinkTarget.Previous
        3 -> LinkTarget.First
        4 -> LinkTarget.Last
        5 -> LinkTarget.ExitShow
        6 -> LinkTarget.Url(url)
        7 -> LinkTarget.Slide(slideId)
        else -> null
    }

/** What a transition may last: too short to see, and long enough to sit through. */
private const val MIN_TRANSITION_MS: Int = 100
private const val MAX_TRANSITION_MS: Int = 3000

/**
 * What the build effects are called in the build order's popup, in
 * [BuildEffect]'s own order, so a place in this list is an ordinal. Spelled here
 * for the same reason [TransitionKindTitles] is: two of them read as two words in
 * a menu and as one in code.
 */
private val BuildEffectTitles: List<String> =
    listOf("Appear", "Fade Up", "Pop", "Dissolve", "Move In", "Scale", "Wipe", "Typewriter")

/** The triggers, in [BuildTrigger]'s order. Same protocol as the effects. */
private val BuildTriggerTitles: List<String> = listOf("On Click", "With Previous", "After Previous")

/** What an action does to its element, in [ActionKind]'s order. Same protocol. */
private val ActionKindTitles: List<String> = listOf("Move", "Opacity", "Rotate", "Scale")

/**
 * The deliveries in [BuildDelivery]'s order. A row offers only the ones its own
 * element has pieces for, so this is a lookup rather than a menu; see
 * [deliveriesFor].
 */
private val BuildDeliveryTitles: List<String> =
    listOf("All at Once", "By Paragraph", "By Word", "By Character", "By Line")

/** What a build may last, the way [MIN_TRANSITION_MS] bounds a transition. */
private const val MIN_BUILD_MS: Int = 100
private const val MAX_BUILD_MS: Int = 3000

/** What a fresh action build does, so adding one shows something on the canvas. */
private const val DEFAULT_ACTION_DX: Float = 40f

/** How much of an element's content a build row's title carries. */
private const val HINT_LENGTH: Int = 26

/**
 * The deliveries [element] has pieces to hand over in, in the model's order.
 * Everything can be delivered whole, so the list is never empty and its first
 * entry is always [BuildDelivery.All].
 *
 * A row's `deliveryIndex` is a place in this list rather than an ordinal: what
 * the popup offers is what the element allows, and a position in a menu is what
 * comes back across the boundary.
 */
private fun deliveriesFor(element: Element?): List<BuildDelivery> = when (element) {
    is TextElement -> BuildDelivery.entries
    is CodeElement, is TerminalElement -> listOf(BuildDelivery.All, BuildDelivery.ByLine)
    else -> listOf(BuildDelivery.All)
}

/** Whether [element] has steps of its own for a build to walk it through. */
private fun hasSteps(element: Element?): Boolean = when (element) {
    is CodeElement -> element.steps.isNotEmpty()
    is DiagramElement -> element.steps.isNotEmpty()
    else -> false
}

/** What the inspector calls this kind of element, one word. */
private fun Element.kindName(): String = when (this) {
    is TextElement -> "Text"
    is ShapeElement -> "Shape"
    is ImageElement -> "Image"
    is GalleryElement -> "Gallery"
    is CodeElement -> "Code"
    is TerminalElement -> "Terminal"
    is DiagramElement -> "Diagram"
    is EquationElement -> "Equation"
    is GroupElement -> "Group"
}

/**
 * What a build row calls the element it plays: its kind, and as much of its
 * content as fits ("Text: Rendering Pipeline"). The first line that says
 * anything, since a row is one line high and a code block's second line is not
 * a name for it.
 */
private fun Element.rowTitle(): String {
    val content: String = when (this) {
        is TextElement -> text
        is ShapeElement -> label.ifBlank { kind.name }
        is ImageElement -> placeholder
        is GalleryElement -> "${images.size} images"
        is CodeElement -> language
        is TerminalElement -> title
        is DiagramElement -> source
        is EquationElement -> latex
        is GroupElement -> "${children.size} elements"
    }
    val hint: String = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
    if (hint.isEmpty()) return kindName()
    val short: String =
        if (hint.length <= HINT_LENGTH) hint
        else hint.take(HINT_LENGTH).trimEnd() + "…"
    return "${kindName()}: $short"
}

/** The line under a row's title: what plays, how long it takes, what starts it. */
private fun Build.rowMeta(): String {
    val effectTitle: String = BuildEffectTitles.getOrElse(effect.ordinal) { effect.name }
    val head: String = when (kind) {
        BuildKind.In -> effectTitle
        BuildKind.Out -> "Out · $effectTitle"
        BuildKind.Action -> "Action · ${action?.summary() ?: "None"}"
    }
    val triggerTitle: String = BuildTriggerTitles.getOrElse(trigger.ordinal) { trigger.name }
    return "$head · ${seconds(durationMs)} · $triggerTitle"
}

/** What one action reads as on a row: what it changes, and by how much. */
private fun BuildAction.summary(): String = when (kind) {
    ActionKind.Move -> "Move ${dx.short()}, ${dy.short()}"
    ActionKind.Opacity -> "Opacity ${opacity.short()}"
    ActionKind.Rotate -> "Rotate ${rotation.short()}°"
    ActionKind.Scale -> "Scale ${scale.short()}"
}

/** Milliseconds as the seconds a row says out loud: "0.4s", "1s". */
private fun seconds(ms: Int): String = "${(ms / 1000f).short()}s"

/**
 * A number as a row spells it: two decimals at the most, and no trailing zeros.
 * Hand-rolled because there is no `String.format` on Kotlin/Native.
 */
private fun Float.short(): String {
    val sign: String = if (this < 0f) "-" else ""
    val hundredths: Int = (abs(this) * 100).roundToInt()
    val whole: Int = hundredths / 100
    val fraction: Int = hundredths % 100
    return when {
        fraction == 0 -> "$sign$whole"
        fraction % 10 == 0 -> "$sign$whole.${fraction / 10}"
        else -> "$sign$whole.${fraction.toString().padStart(2, '0')}"
    }
}

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

/**
 * These bytes as Kotlin's. The shell hands images over as [NSData] (a panel, a
 * drop, the pasteboard all produce one), and everything past this line is a
 * [ByteArray]: the asset store has never heard of Foundation.
 */
private fun NSData.toByteArray(): ByteArray {
    val size: Int = length.toInt()
    if (size <= 0) return ByteArray(0)
    val copy = ByteArray(size)
    copy.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    return copy
}

/**
 * [extension] as an asset id's suffix: lower case, no dot, letters and digits
 * only, and "png" for anything left with nothing.
 *
 * The store never reads it, so this is only about what the bundle looks like in
 * Finder. Sanitised rather than trusted because an id has to stay one path
 * segment, and a dropped file's extension is whatever the file was called.
 */
private fun assetExtension(extension: String): String =
    extension.trimStart('.').lowercase().filter { it.isLetterOrDigit() }.take(8).ifEmpty { "png" }

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
 * One saved shape look from the deck's library, flattened for the style strip in
 * the Shape section. A picture and an id: the swatch paints itself from these
 * numbers, and [id] is the whole of what goes back when it is clicked.
 *
 * [hasGradient] carries stops either way, the way [ShapeProps] does, so a swatch
 * never has to invent a colour. [current] is the strip's ring: the primary shape
 * is already wearing exactly this look, fill, border, shadow and all.
 */
class ObjectStyleProps(
    val id: String,
    val name: String,
    /** Packed ARGB, the document model's color format. So is every color below. */
    val fill: Long,
    val gradientStart: Long,
    val gradientEnd: Long,
    val hasGradient: Boolean,
    val strokeColor: Long,
    val strokeWidth: Float,
    val hasShadow: Boolean,
    val cornerRadius: Float,
    val current: Boolean,
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
 * The primary selected element's terminal style, flattened for the native
 * inspector, and null unless that element is a [TerminalElement]. Same contract
 * as [CodeProps]: what the panel shows, never a handle onto the document.
 *
 * [title] is the name in the title bar, which is content rather than style, so
 * the setter for it writes the primary alone while the other three write the
 * whole selection.
 */
class TerminalProps(
    val title: String,
    val prompt: String,
    val fontSize: Float,
    val showTitleBar: Boolean,
)

/**
 * The primary selected element's diagram style, flattened for the native
 * inspector, and null unless that element is a [DiagramElement]. Same contract
 * as [CodeProps]: what the panel shows, never a handle onto the document.
 *
 * The source is not here. A diagram's text is content, edited on the canvas the
 * way a code block's is, so the panel only ever dresses what the layout draws:
 * the type size and the four colours.
 */
class DiagramProps(
    val fontSize: Float,
    val nodeFill: Long,
    val nodeStroke: Long,
    val nodeText: Long,
    val edgeColor: Long,
)

/**
 * The primary selected element's equation style, flattened for the native
 * inspector, and null unless that element is an [EquationElement]. Same
 * contract as [DiagramProps]: what the panel shows, never a handle onto the
 * document.
 *
 * The latex is not here. An equation's source is content, typed on the canvas,
 * so the panel only dresses what the layout draws: the size and the colour.
 */
class EquationProps(
    val fontSize: Float,
    val color: Long,
)

/**
 * The primary selected image, flattened for the Image section of the Format
 * panel: what shows of the picture, how it is corrected, and what is written
 * under it. [TextProps]'s family, a snapshot rather than a handle.
 *
 * The mask travels as a place in `maskKindTitles()`, -1 for an image showing all
 * of itself, with the window filled in whatever that place is: it is the whole
 * image for an unmasked one, so switching a mask on never has to invent a
 * window and switching it off never has to remember one. The window is in the
 * image's own normalised units, 0..1 on each axis, which is what the panel
 * shows as percentages.
 *
 * [hasAsset] is false for an image with no bytes behind it yet (a media
 * placeholder, or an insert whose file went missing): there is a picture to
 * frame and adjust only once one has landed.
 */
class ImageProps(
    val hasAsset: Boolean,
    val maskKindIndex: Int,
    val maskX: Float,
    val maskY: Float,
    val maskW: Float,
    val maskH: Float,
    val exposure: Float,
    val saturation: Float,
    val contrast: Float,
    val caption: String,
)

/**
 * The primary selected gallery, flattened for the Gallery section of the Format
 * panel: how many pictures it holds, which one is being authored, and what the
 * slide says about it. [ImageProps]'s neighbour, a snapshot rather than a handle.
 *
 * [caption] is the current picture's alone, because that is the one the field
 * edits: a caption belongs to a picture, and one field across the lot would give
 * every image in the box the same words. The correction below it is the whole
 * gallery's, which is what the model says: a carousel reads as one object.
 *
 * The pictures themselves are not here. Bytes never cross the boundary as a
 * value; the strip asks [EditorHost.galleryThumbnail] for one at a time.
 */
class GalleryProps(
    val count: Int,
    /** Always in range, and 0 for an empty gallery, which is the index nothing draws. */
    val current: Int,
    val caption: String,
    val showCaptions: Boolean,
    val exposure: Float,
    val saturation: Float,
    val contrast: Float,
)

/**
 * Where the primary selected element points when it is clicked in a show, and
 * null unless that element is one of the three kinds that hold a link at all.
 * [TextProps]'s family again: what the panel shows, never a handle onto the
 * document, and the setter behind it writes the whole selection.
 *
 * The kind travels as a place in `linkKindTitles()`, the way a transition's does.
 * [url] and [slideId] are filled in whatever the kind, the way a shape wearing no
 * gradient still carries its stops: switching a link from Next to Webpage never
 * has to invent an address, and switching it to Slide never has to invent a
 * destination.
 */
class LinkProps(
    /** A place in `linkKindTitles()`, 0 for an element that points nowhere. */
    val kindIndex: Int,
    val url: String,
    val slideId: String,
)

/**
 * How the whole deck plays, flattened for the Document panel: the kind of show,
 * and the three numbers behind it. A property of the deck rather than of a run
 * of it, so it sits with the theme and the slide size.
 *
 * [autoAdvanceMs] means something only to a self-playing deck and
 * [restartAfterIdleMs] only to a links-only one, but both are carried whatever
 * the type: a panel switching kinds shows what that kind would run with rather
 * than a zero it has to invent.
 */
class PlaybackProps(
    /** A place in `playbackTypeTitles()`, which is a [PlaybackType] ordinal. */
    val typeIndex: Int,
    val autoAdvanceMs: Int,
    val loop: Boolean,
    val restartAfterIdleMs: Int,
)

/**
 * The selected slide's transition, flattened for the native Animate panel: what
 * plays on the way out of the slide. [ElementProps]'s opposite number one level
 * up, and the same contract, a value rather than a handle.
 *
 * [kindIndex] -1 is the slide carrying no transition of its own, i.e. the deck's
 * default. The other four are then what switching off Default would commit,
 * filled in the way the slide background fills its colours in, so a shell
 * changing kinds never has to invent a duration or a direction.
 */
class TransitionProps(
    /** A place in `transitionKinds()`, or -1 for the deck's default. */
    val kindIndex: Int,
    /** A place in `transitionDirections()`. Means nothing to the kinds that don't travel. */
    val directionIndex: Int,
    val durationMs: Int,
    /** Whether the slide leaves on its own rather than waiting for a click. */
    val automatic: Boolean,
    /** How long an automatic slide sits before it goes. Nothing to a slide that waits. */
    val delayMs: Int,
)

/**
 * One row of the slide's build order, flattened for the native Animate panel:
 * what it says, and every number the editor under the list writes back.
 * [TransitionProps]'s neighbour, and the same contract, a value rather than a
 * handle onto the document.
 *
 * The enums travel as places in a list, the way a transition's kind does:
 * [kindIndex] in [BuildKind], [effectIndex] in `buildEffectTitles()`,
 * [triggerIndex] in `buildTriggerTitles()`, [actionKindIndex] in
 * `actionKindTitles()` and -1 for a build that carries no action, and
 * [deliveryIndex] in this row's own [deliveryTitles], which is what its element
 * allows rather than the whole model's list.
 *
 * The action numbers are filled in whatever the kind, the way a slide wearing no
 * gradient still carries its stops: switching an action from Move to Scale never
 * has to invent a factor. [elementStep] is -1 for a build that points at no step.
 */
class BuildRow(
    val index: Int,
    /** The element the build plays, kind and content hint: "Text: Rendering Pipeline". */
    val title: String,
    /** What plays and when: "Fade Up · 0.4s · On Click". */
    val meta: String,
    val elementId: String,
    /** The build's element is in the selection, which is what highlights the row. */
    val active: Boolean,
    val kindIndex: Int,
    val effectIndex: Int,
    val deliveryIndex: Int,
    val triggerIndex: Int,
    val durationMs: Int,
    val delayMs: Int,
    /** The element's own step this build moves to, -1 when it moves to none. */
    val elementStep: Int,
    /** -1 when the build carries no action, which is every build that is not one. */
    val actionKindIndex: Int,
    val dx: Float,
    val dy: Float,
    val opacity: Float,
    val rotation: Float,
    val scale: Float,
    /** The element has steps of its own, so the row has a step to point at. */
    val hasStepTarget: Boolean,
    /** The deliveries this build's element has pieces for, in menu order. */
    val deliveryTitles: List<String>,
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
 * One file of an exported CuP project: where it goes under the project root, and
 * what is in it. Forward slashes in [path], the way a Gradle layout is written.
 *
 * The core's `ExportedFile` flattened for ObjC, the way [OutlineRow] flattens an
 * outline entry: a data class doesn't cross the boundary, and the shell only
 * ever reads these on its way to disk.
 */
class ExportFile(val path: String, val contents: String)

/** The one line of an exported settings file that names the project. */
private val RootProjectName = Regex("""rootProject\.name = "(.*)"""")

/** What the presenter display's wall clock says: the time of day, to the minute. */
private val WallClock: NSDateFormatter = NSDateFormatter().apply { dateFormat = "HH:mm" }

/** How big the presenter window opens, in points. */
private const val PRESENTER_WIDTH: Double = 1280.0
private const val PRESENTER_HEIGHT: Double = 720.0

/**
 * A running presentation: a Compose view playing a snapshot of the document.
 * The host shows [view] full screen and calls [dispose] when it tears it down.
 * Playback keys are the player's own business; it only calls back on exit.
 *
 * A show also has a second face, [presenterView], for the window on the laptop
 * screen: the same playback, driven by the same controller, showing the current
 * slide, what is next, and the notes. A preview has none ([hasPresenter] false):
 * it is one slide with nothing after it, and nothing to speak over.
 *
 * The show plays [document], a snapshot, so the deck cannot change under the
 * audience. The presenter follows [live] instead, so notes typed into it during
 * the show are the notes it goes on showing.
 */
class PlaySession internal constructor(
    private val document: Document,
    startIndex: Int,
    private val onExit: () -> Unit,
    /** The document as it stands, for the presenter's notes. Null for a preview. */
    private val live: Flow<Document>? = null,
    /** Where a notes edit goes: slide id, new notes. Null for a preview. */
    private val notes: ((String, String) -> Unit)? = null,
    /**
     * A rehearsal: the same show, with nobody to show it to. [view] is still
     * what drives the playback both faces read, so the host keeps it composed
     * and hides it rather than leaving it out.
     */
    val isRehearsal: Boolean = false,
    /**
     * Where the slides resolve their image ids. The show and the presenter both
     * draw the deck, so both need it: a picture that is on the slide in the
     * editor is on the slide in front of the audience.
     */
    private val assets: AssetStore,
) {
    /**
     * The controller both faces play on, published by the show's own composition.
     * [PlayerController]'s constructor is internal to `:cupboard:ui`, so the only
     * way to one out here is remembering it inside a composable. Snapshot state
     * rather than a plain field, so the presenter picks it up on the frame it
     * lands rather than whenever it next happens to recompose.
     */
    private var controller: PlayerController? by mutableStateOf(null)

    private val composeView = ComposeNSView {
        val player: PlayerController = rememberPlayerController()
        DisposableEffect(player) {
            controller = player
            onDispose { if (controller === player) controller = null }
        }
        CompositionLocalProvider(LocalAssetStore provides assets) {
            PresentationPlayer(
                document = document,
                startIndex = startIndex,
                modifier = Modifier.fillMaxSize(),
                onExit = onExit,
                // The player has no idea what a browser is, and neither does
                // anything else in the shared module: a URL link comes out here.
                // A string that is not an address opens nothing rather than throwing.
                onOpenUrl = { url ->
                    NSURL.URLWithString(url)?.let { NSWorkspace.sharedWorkspace.openURL(it) }
                },
                controller = player,
            )
        }
    }

    private var presenterCompose: ComposeNSView? = null

    val view: NSView = composeView

    /** Whether this session has a presenter display to show at all. */
    val hasPresenter: Boolean = live != null && notes != null

    /**
     * The presenter display, built the first time it is asked for. Empty until
     * the show's composition has published its controller, which is a frame at
     * the very most: the window only opens once the show is up.
     */
    val presenterView: NSView
        get() = presenterCompose ?: buildPresenter().also { presenterCompose = it }

    private fun buildPresenter(): ComposeNSView {
        val documents: Flow<Document> = live ?: emptyFlow()
        val onNotesChange: (String, String) -> Unit = notes ?: { _, _ -> }
        return ComposeNSView(
            NSMakeRect(0.0, 0.0, PRESENTER_WIDTH, PRESENTER_HEIGHT),
        ) {
            val player: PlayerController? = controller
            val deck: Document by documents.collectAsState(initial = document)
            if (player != null) {
                CompositionLocalProvider(LocalAssetStore provides assets) {
                    PresenterView(
                        document = deck,
                        controller = player,
                        onNotesChange = onNotesChange,
                        clock = { WallClock.stringFromDate(NSDate()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    /** Playback from the presenter window, which has no player of its own. */
    fun next() {
        controller?.next()
    }

    fun previous() {
        controller?.previous()
    }

    /** Escape in the presenter window: ends the show, the way it does in it. */
    fun exit() {
        onExit()
    }

    /** Tears down the presenter display alone, the show carrying on without it. */
    fun disposePresenter() {
        presenterCompose?.dispose()
        presenterCompose = null
    }

    fun dispose() {
        disposePresenter()
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
class EditorHost(
    /**
     * The editor this host is a front end onto. Internal, not private: Swift
     * talks to the view model through the methods below and never holds one, but
     * [Documents] hands a host the editor it just opened, so one window can be
     * on a deck the user picked rather than on this machine's default one.
     *
     */
    internal val viewModel: EditorViewModel,
) {
    /**
     * A host on this machine's default deck, which is what the first window
     * opens on. Deliberately the bundle the Compose Desktop shell opens: two
     * front ends onto one document, not two apps.
     *
     * A secondary constructor rather than a default argument: ObjC export only
     * emits the full initializer, so a defaulted parameter would leave Swift
     * with no way to ask for the default deck.
     */
    constructor() : this(Cupboard.editor())


    /** The state the sidebar reads right now. Never stale: the canvas and this
     * are the same flow, so an edit made in Compose shows up here too. */
    private val state: EditorState get() = viewModel.states.value

    // The number is part of the cache key, not just the slide: skipping an
    // earlier slide renumbers every row after it without touching one of them.
    // So is the layout it resolved against: a slide draws what it inherits, so
    // editing a layout changes every thumbnail on it without touching a slide.
    // And so is the slide size, which changes the shape of an empty slide's
    // thumbnail without changing anything on it.
    private class Thumbnail(
        val slide: Slide,
        val layout: Slide?,
        val number: Int?,
        val background: SlideBackground?,
        val width: Int,
        val slideWidth: Float,
        val slideHeight: Float,
        val image: NSImage,
    )

    private val thumbnails = mutableMapOf<String, Thumbnail>()

    /**
     * Gallery strip thumbnails, by asset id and the width they were drawn at,
     * least recently added dropped first. The strip re-asks for every picture on
     * every pass, and decoding a photo per pass would starve the main thread the
     * way rendering a slide row per pass once did.
     */
    private val galleryThumbnails = mutableMapOf<String, NSImage>()

    /** The keys a decode is already on its way for, so a miss asks once. */
    private val galleryThumbnailsPending = mutableSetOf<String>()

    /**
     * Bumped whenever a gallery thumbnail lands. A decode finishes after the
     * state that asked for it, so its arrival is a change of its own: without
     * this the strip would stay empty until the next edit. See [onChange].
     */
    private val galleryTick = MutableStateFlow(0)

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
        CompositionLocalProvider(
            LocalResizeCursors provides AppKitResizeCursors,
            // Where the slide's images come from. The deck's own store, so the
            // canvas draws the bytes this window's bundle is carrying.
            LocalAssetStore provides viewModel.assets,
        ) {
            Box(Modifier.fillMaxSize().background(well), contentAlignment = Alignment.Center) {
                EditorCanvas(
                    slide = state.selectedSlide,
                    layout = state.selectedLayout,
                    background = state.document.background,
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
                    slideWidth = state.document.slideWidth,
                    slideHeight = state.document.slideHeight,
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

    /**
     * Which row is selected, and which row a click selects. Both count in the
     * list the navigator is showing, so in layout mode they count layouts: a row
     * index is a place in the rows, not a place in the deck.
     */
    fun selectedSlideIndex(): Int =
        if (state.isEditingLayouts) state.document.layouts.indexOfFirst { it.id == state.selectedSlideId }
        else state.selectedSlideIndex()

    fun selectSlide(index: Int) {
        if (!state.isEditingLayouts) {
            viewModel.onSelectSlideAt(index)
            return
        }
        val layout: Slide = state.document.layouts.getOrNull(index) ?: return
        viewModel.onSelectSlide(layout.id)
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
        viewModel.onInsertElement(
            entry.element(state.insertionFrame(entry.width, entry.height), state.defaults),
        )
    }

    /** A text box in the middle of the slide, carrying the placeholder to type over. */
    fun insertTextBox() {
        val frame: Frame = state.insertionFrame(DefaultTextBoxWidth, DefaultTextBoxHeight)
        viewModel.onInsertElement(textBoxElement(frame, state.defaults))
    }

    /** A code block in the middle of the slide, carrying a snippet to type over. */
    fun insertCodeBox() {
        val frame: Frame = state.insertionFrame(DefaultCodeBoxWidth, DefaultCodeBoxHeight)
        viewModel.onInsertElement(codeBoxElement(frame, state.defaults))
    }

    /** A terminal in the middle of the slide, carrying a command and its output. */
    fun insertTerminal() {
        val frame: Frame = state.insertionFrame(DefaultTerminalWidth, DefaultTerminalHeight)
        viewModel.onInsertElement(terminalElement(frame, state.defaults))
    }

    /** A diagram in the middle of the slide, carrying a flowchart to rewrite. */
    fun insertDiagram() {
        val frame: Frame = state.insertionFrame(DefaultDiagramWidth, DefaultDiagramHeight)
        viewModel.onInsertElement(diagramElement(frame, state.defaults))
    }

    /** An equation in the middle of the slide, carrying an identity to rewrite. */
    fun insertEquation() {
        val frame: Frame = state.insertionFrame(DefaultEquationWidth, DefaultEquationHeight)
        viewModel.onInsertElement(equationElement(frame, state.defaults))
    }

    /**
     * [bytes] on the slide: written to the deck's assets under a fresh id, then
     * inserted at its own aspect, no bigger than [IMAGE_INSERT_SHARE] of the
     * slide.
     *
     * The one insert that is not synchronous, and the one that takes bytes: an
     * image arrives from a panel, a drop or the pasteboard, and none of those
     * are the document's business. The decode and the write both happen off the
     * main thread and the element lands back on it, so dropping a 20 megapixel
     * photo doesn't stall the window.
     *
     * Bytes nothing can decode insert nothing rather than an empty frame:
     * a file that is not an image is a mistake, not a placeholder.
     */
    fun insertImage(bytes: NSData, extension: String) {
        val data: ByteArray = bytes.toByteArray()
        if (data.isEmpty()) return
        scope.launch {
            val size: NaturalSize = writeAsset(data, extension) ?: return@launch
            val fitted: ImageElement = imageElement(
                frame = state.insertionFrame(DefaultImageWidth, DefaultImageHeight),
                assetId = size.assetId,
                naturalWidth = size.width,
                naturalHeight = size.height,
                defaults = state.defaults,
            ).fitted(
                maxWidth = state.document.slideWidth * IMAGE_INSERT_SHARE,
                maxHeight = state.document.slideHeight * IMAGE_INSERT_SHARE,
            )
            viewModel.onInsertElement(fitted)
        }
    }

    /**
     * [files] on the slide as one gallery: several pictures in one box, shown one
     * at a time. [extensions] runs alongside [files], one per file, and a short
     * list reads as "png" for whatever it doesn't reach.
     *
     * [insertImage]'s plural, and asynchronous for the same reasons: the writes
     * and the decodes happen off the main thread and the element lands back on
     * it. The first picture's aspect is what the box is fitted to, which is
     * `galleryElement`'s rule, inside the same share of the slide a single image
     * inserts into.
     *
     * Files nothing can decode are dropped rather than kept as empty frames, and
     * a set where none of them decode inserts nothing at all.
     */
    fun insertGallery(files: List<NSData>, extensions: List<String>) {
        if (files.isEmpty()) return
        scope.launch {
            val images: List<GalleryImage> = writeGalleryImages(files, extensions)
            if (images.isEmpty()) return@launch
            viewModel.onInsertElement(
                galleryElement(
                    frame = state.insertionFrame(
                        width = state.document.slideWidth * IMAGE_INSERT_SHARE,
                        height = state.document.slideHeight * IMAGE_INSERT_SHARE,
                    ),
                    images = images,
                    defaults = state.defaults,
                ),
            )
        }
    }

    /**
     * New bytes behind the primary image, its frame left where it is: what
     * Replace Image does.
     *
     * The mask goes with the old picture. It is a window in the image's own
     * units, and the same window over a photo of another shape frames something
     * nobody chose; a fresh one is a drag away, and a kept one is a puzzle.
     */
    fun replaceImage(bytes: NSData, extension: String) {
        val target: ImageElement = state.primaryElement as? ImageElement ?: return
        if (target.locked) return
        val data: ByteArray = bytes.toByteArray()
        if (data.isEmpty()) return
        scope.launch {
            val size: NaturalSize = writeAsset(data, extension) ?: return@launch
            // Re-read: the write took a moment, and the selection may have moved
            // under it. Editing by id is what keeps this off the wrong element.
            val live: ImageElement = liveImage(target.id) ?: return@launch
            viewModel.onUpdateElements(
                listOf(
                    live.copy(
                        assetId = size.assetId,
                        naturalWidth = size.width,
                        naturalHeight = size.height,
                        mask = null,
                    ),
                ),
            )
        }
    }

    /** The size and id of [data] written to the deck's assets, null if it won't decode. */
    private suspend fun writeAsset(data: ByteArray, extension: String): NaturalSize? =
        withContext(Dispatchers.Default) {
            val decoded = runCatching { SkiaImage.makeFromEncoded(data) }.getOrNull()
                ?: return@withContext null
            val id = "${newId()}.${assetExtension(extension)}"
            viewModel.assets.write(id, data)
            NaturalSize(id, decoded.width, decoded.height)
        }

    /** What an asset write hands back: where the bytes went, and how big they are. */
    private class NaturalSize(val assetId: String, val width: Int, val height: Int)

    /** The image with [id] as the slide holds it now, if it is still there and unlocked. */
    private fun liveImage(id: String): ImageElement? =
        (state.selectedSlide.elementById(id) as? ImageElement)?.takeIf { !it.locked }

    /**
     * The picture the Format inspector shows, null when the primary element is
     * not an image. Read off the primary, like every other Format section.
     */
    fun selectedImage(): ImageProps? = (state.primaryElement as? ImageElement)?.let { image ->
        val window: Frame = image.mask?.frame ?: Frame(0f, 0f, 1f, 1f)
        ImageProps(
            hasAsset = image.assetId != null,
            maskKindIndex = image.mask?.let { MaskKinds.indexOf(it.kind) } ?: -1,
            maskX = window.x,
            maskY = window.y,
            maskW = window.width,
            maskH = window.height,
            exposure = image.adjust.exposure,
            saturation = image.adjust.saturation,
            contrast = image.adjust.contrast,
            caption = image.caption,
        )
    }

    /** The outlines an image may be cut to, in popup order. -1 is no mask at all. */
    fun maskKindTitles(): List<String> = MaskKindTitles

    /**
     * The window the selection's images show, and the outline it is cut to.
     * [kindIndex] outside `maskKindTitles()` drops the mask, which is what the
     * popup's None row picks.
     *
     * The window is normalised, so it is clamped rather than trusted: a mask
     * reaching past the image would sample nothing, and one with no extent would
     * leave the element blank with no handle to drag it back by.
     */
    fun setImageMask(kindIndex: Int, x: Float, y: Float, w: Float, h: Float) {
        val kind: ShapeKind? = MaskKinds.getOrNull(kindIndex)
        val mask: ImageMask? = kind?.let {
            val width: Float = w.coerceIn(MIN_MASK_EXTENT, 1f)
            val height: Float = h.coerceIn(MIN_MASK_EXTENT, 1f)
            ImageMask(
                kind = it,
                frame = Frame(
                    x = x.coerceIn(0f, 1f - width),
                    y = y.coerceIn(0f, 1f - height),
                    width = width,
                    height = height,
                ),
            )
        }
        formatImages { it.copy(mask = mask) }
    }

    /**
     * The three corrections, as one write: they compose into one colour matrix,
     * so there is nothing to be gained by setting them apart. [commit] false is
     * a slider still under the thumb, exactly as it is for opacity.
     */
    fun setImageAdjust(exposure: Float, saturation: Float, contrast: Float, commit: Boolean) {
        val adjust = ImageAdjust(
            exposure = exposure.coerceIn(-1f, 1f),
            saturation = saturation.coerceIn(0f, 2f),
            contrast = contrast.coerceIn(0f, 2f),
        )
        val edits: List<Element> = imageEdits { it.copy(adjust = adjust) }
        if (edits.isEmpty()) return
        if (commit) viewModel.onUpdateElements(edits) else viewModel.onPreviewElements(edits)
    }

    /** Back to the identity: the image as it was decoded. */
    fun resetImageAdjust() {
        formatImages { it.copy(adjust = ImageAdjust()) }
    }

    /**
     * What is written under the picture. The primary alone, the way a terminal's
     * title is: a caption is content, and one field across a selection would
     * give every image the same words.
     */
    fun setImageCaption(text: String) {
        val image: ImageElement = state.primaryElement as? ImageElement ?: return
        if (image.locked || image.caption == text) return
        viewModel.onUpdateElements(listOf(image.copy(caption = text)))
    }

    /**
     * The primary image with the region around its top-left corner rubbed out,
     * pointed at the asset that came back.
     *
     * The seed is (0, 0) because that is where a backdrop is: the corner of a
     * screenshot or a product shot is the thing being removed, and a click-to-
     * seed gesture on the canvas is a later job. The core writes a new asset
     * rather than rewriting the old one, so this is one document change undo
     * puts back whole.
     */
    fun removeImageBackground(tolerance: Float) {
        val target: ImageElement = state.primaryElement as? ImageElement ?: return
        val assetId: String = target.assetId ?: return
        if (target.locked) return
        scope.launch {
            val cleared: String =
                viewModel.assets.removeBackground(assetId, 0, 0, tolerance.coerceIn(0f, 1f))
            if (cleared == assetId) return@launch
            val live: ImageElement = liveImage(target.id) ?: return@launch
            viewModel.onUpdateElements(listOf(live.copy(assetId = cleared)))
        }
    }

    /** One unlocked image in the selection is enough for the image controls. */
    fun canFormatImages(): Boolean =
        state.selectedElements.any { it is ImageElement && !it.locked }

    // The image setters' [formatSelection]. The core has no formatImages of its
    // own yet, so the rule is spelled here: every unlocked image in the
    // selection, minus the ones the change left alone, so a control set to what
    // it already said spends no history entry.
    private fun imageEdits(transform: (ImageElement) -> ImageElement): List<Element> =
        state.selectedElements
            .filterIsInstance<ImageElement>()
            .filter { !it.locked }
            .mapNotNull { image -> transform(image).takeIf { it != image } }

    private fun formatImages(transform: (ImageElement) -> ImageElement) {
        val edits: List<Element> = imageEdits(transform)
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * The gallery the Format inspector shows, null when the primary element is
     * not one. Read off the primary, like every other Format section.
     */
    fun selectedGallery(): GalleryProps? =
        (state.primaryElement as? GalleryElement)?.let { gallery ->
            val current: Int = gallery.currentIndex()
            GalleryProps(
                count = gallery.images.size,
                current = current,
                caption = gallery.images.getOrNull(current)?.caption ?: "",
                showCaptions = gallery.showCaptions,
                exposure = gallery.adjust.exposure,
                saturation = gallery.adjust.saturation,
                contrast = gallery.adjust.contrast,
            )
        }

    /**
     * The [index]th picture of the primary gallery, drawn [width] points wide,
     * or null while it is being decoded and for an index the gallery hasn't got.
     *
     * Null now and an image later: the strip asks on every pass, a miss starts
     * one decode, and the one that lands wakes the shell through [onChange]. The
     * alternative is blocking a SwiftUI body on reading a photo off disk.
     */
    fun galleryThumbnail(index: Int, width: Int): NSImage? {
        if (width <= 0) return null
        val gallery: GalleryElement = state.primaryElement as? GalleryElement ?: return null
        val assetId: String = gallery.images.getOrNull(index)?.assetId ?: return null
        val key = "$assetId@$width"
        galleryThumbnails[key]?.let { return it }
        if (!galleryThumbnailsPending.add(key)) return null
        scope.launch {
            val drawn: DrawnThumbnail? = decodeGalleryThumbnail(assetId, width)
            galleryThumbnailsPending.remove(key)
            if (drawn == null) return@launch
            val png: ByteArray = drawn.png
            val data: NSData = png.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), png.size.toULong())
            }
            val image = NSImage(data = data)
            image.setSize(NSMakeSize(width.toDouble(), drawn.height.toDouble()))
            galleryThumbnails[key] = image
            while (galleryThumbnails.size > GALLERY_THUMBNAILS) {
                galleryThumbnails.remove(galleryThumbnails.keys.first())
            }
            galleryTick.value += 1
        }
        return null
    }

    /**
     * [assetId]'s bytes redrawn at [width] points, at 2x for retina, as PNG.
     * Downscaled rather than handed over whole: a strip of 48pt squares has no
     * use for twenty megapixels, and the cache above holds what comes out.
     *
     * Bytes rather than an [NSImage], so the decode and the resample both stay
     * off the main thread and only the wrapping happens back on it.
     */
    private suspend fun decodeGalleryThumbnail(assetId: String, width: Int): DrawnThumbnail? =
        withContext(Dispatchers.Default) {
            val bytes: ByteArray = viewModel.assets.read(assetId) ?: return@withContext null
            val decoded: SkiaImage =
                runCatching { SkiaImage.makeFromEncoded(bytes) }.getOrNull() ?: return@withContext null
            if (decoded.width <= 0 || decoded.height <= 0) return@withContext null
            val height: Int = (width.toFloat() * decoded.height / decoded.width)
                .roundToInt()
                .coerceAtLeast(1)
            val surface: SkiaSurface = SkiaSurface.makeRasterN32Premul(width * 2, height * 2)
            surface.canvas.drawImageRect(
                decoded,
                SkiaRect.makeWH(decoded.width.toFloat(), decoded.height.toFloat()),
                SkiaRect.makeWH(width * 2f, height * 2f),
            )
            val png: ByteArray = surface.makeImageSnapshot()
                .encodeToData(EncodedImageFormat.PNG)?.bytes ?: return@withContext null
            DrawnThumbnail(png, height)
        }

    /** A resampled picture on its way back to the main thread: the PNG, and how tall it is in points. */
    private class DrawnThumbnail(val png: ByteArray, val height: Int)

    /** Which picture is being authored, always in range. Empty is 0, which draws nothing. */
    private fun GalleryElement.currentIndex(): Int =
        if (images.isEmpty()) 0 else current.coerceIn(images.indices)

    /**
     * Which picture the editor canvas shows. The primary alone: a gallery's
     * current image is that gallery's, and there is nothing to spread across a
     * selection.
     */
    fun setGalleryCurrent(index: Int) {
        editGallery { gallery ->
            if (gallery.images.isEmpty()) gallery
            else gallery.copy(current = index.coerceIn(gallery.images.indices))
        }
    }

    /**
     * [files] appended to the primary gallery, in the order they were picked.
     * [insertGallery]'s other half, and asynchronous for the same reason.
     */
    fun addGalleryImages(files: List<NSData>, extensions: List<String>) {
        val target: GalleryElement = liveGallery() ?: return
        if (files.isEmpty()) return
        scope.launch {
            val added: List<GalleryImage> = writeGalleryImages(files, extensions)
            if (added.isEmpty()) return@launch
            // Re-read: the writes took a moment, and the selection may have moved
            // under them. Editing by id is what keeps this off the wrong element.
            val live: GalleryElement = liveGallery(target.id) ?: return@launch
            viewModel.onUpdateElements(listOf(live.copy(images = live.images + added)))
        }
    }

    /**
     * The [index]th picture out of the primary gallery, the ones after it moving
     * up. The bytes stay in the bundle: undo has to be able to put the picture
     * back, and an asset nothing points at costs a file rather than a slide.
     */
    fun removeGalleryImage(index: Int) {
        editGallery { gallery ->
            if (index !in gallery.images.indices) return@editGallery gallery
            val images: List<GalleryImage> = gallery.images.filterIndexed { at, _ -> at != index }
            gallery.copy(
                images = images,
                current = gallery.current.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
            )
        }
    }

    /**
     * The [from]th picture moved to [to], which is what reordering the strip
     * does. The picture being authored follows itself rather than its old place,
     * so shuffling the order never changes which one is on the canvas.
     */
    fun moveGalleryImage(from: Int, to: Int) {
        editGallery { gallery ->
            if (from !in gallery.images.indices) return@editGallery gallery
            if (to !in gallery.images.indices) return@editGallery gallery
            val shown: GalleryImage? = gallery.images.getOrNull(gallery.currentIndex())
            val images: MutableList<GalleryImage> = gallery.images.toMutableList()
            images.add(to, images.removeAt(from))
            gallery.copy(
                images = images,
                current = images.indexOf(shown).coerceAtLeast(0),
            )
        }
    }

    /**
     * What is written under the [index]th picture. One picture's, not the whole
     * gallery's: a caption names what is on screen, the way an image element's
     * does, and it goes to the primary alone for the same reason.
     */
    fun setGalleryCaption(index: Int, text: String) {
        editGallery { gallery ->
            val image: GalleryImage = gallery.images.getOrNull(index) ?: return@editGallery gallery
            gallery.copy(
                images = gallery.images.toMutableList().also { it[index] = image.copy(caption = text) },
            )
        }
    }

    fun setGalleryShowCaptions(on: Boolean) {
        editGallery { it.copy(showCaptions = on) }
    }

    /**
     * The three corrections, as one write, the way [setImageAdjust] takes them:
     * they compose into one colour matrix. [commit] false is a slider still
     * under the thumb. The whole gallery wears them, which is what the model
     * says: a carousel reads as one object.
     */
    fun setGalleryAdjust(exposure: Float, saturation: Float, contrast: Float, commit: Boolean) {
        val gallery: GalleryElement = liveGallery() ?: return
        val adjust = ImageAdjust(
            exposure = exposure.coerceIn(-1f, 1f),
            saturation = saturation.coerceIn(0f, 2f),
            contrast = contrast.coerceIn(0f, 2f),
        )
        if (gallery.adjust == adjust) return
        val edits: List<Element> = listOf(gallery.copy(adjust = adjust))
        if (commit) viewModel.onUpdateElements(edits) else viewModel.onPreviewElements(edits)
    }

    /**
     * The build order that walks the primary gallery through its pictures, one
     * click each after the first. Pressing it twice leaves one set, which is the
     * core's rule rather than this host's; see `Slide.gallerySteps`.
     */
    fun addGallerySteps() {
        val gallery: GalleryElement = liveGallery() ?: return
        viewModel.onAddGallerySteps(gallery.id)
    }

    /** The primary element when it is an unlocked gallery, which every setter needs. */
    private fun liveGallery(): GalleryElement? =
        (state.primaryElement as? GalleryElement)?.takeIf { !it.locked }

    /** The gallery with [id] as the slide holds it now, if it is still there and unlocked. */
    private fun liveGallery(id: String): GalleryElement? =
        (state.selectedSlide.elementById(id) as? GalleryElement)?.takeIf { !it.locked }

    /**
     * The image setters' [formatImages], for the one element that answers to
     * these controls: a gallery is a box, not a style, so there is no selection
     * to spread an edit across. A transform that changes nothing spends no
     * history entry, the same rule the rest of the panel follows.
     */
    private fun editGallery(transform: (GalleryElement) -> GalleryElement) {
        val gallery: GalleryElement = liveGallery() ?: return
        val edited: GalleryElement = transform(gallery)
        if (edited == gallery) return
        viewModel.onUpdateElements(listOf(edited))
    }

    /**
     * [files] written to the deck's assets, in order, as the pictures a gallery
     * is made of. [extensions] runs alongside, and only names what the file ends
     * up called in the bundle. Anything that won't decode is left out.
     */
    private suspend fun writeGalleryImages(
        files: List<NSData>,
        extensions: List<String>,
    ): List<GalleryImage> = files.mapIndexedNotNull { index, file ->
        val data: ByteArray = file.toByteArray()
        if (data.isEmpty()) return@mapIndexedNotNull null
        val size: NaturalSize =
            writeAsset(data, extensions.getOrElse(index) { "" }) ?: return@mapIndexedNotNull null
        GalleryImage(assetId = size.assetId, naturalWidth = size.width, naturalHeight = size.height)
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
            kind = element.kindName(),
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
     * The deck's saved shape looks, in library order: what the style strip
     * offers. Always the whole list, whatever is selected, so the strip is a
     * library rather than something that empties out under you.
     *
     * `current` is measured against the primary shape, and is false for every
     * style while the primary is not a shape: nothing is wearing a look when
     * nothing there has one.
     */
    fun objectStyles(): List<ObjectStyleProps> {
        val shape: ShapeElement? = state.primaryElement as? ShapeElement
        return state.objectStyles.map { style ->
            val gradient: ShapeGradient = style.gradient ?: ShapeGradient(style.fill, style.fill)
            return@map ObjectStyleProps(
                id = style.id,
                name = style.name,
                fill = style.fill,
                gradientStart = gradient.start,
                gradientEnd = gradient.end,
                hasGradient = style.gradient != null,
                strokeColor = style.strokeColor,
                strokeWidth = style.strokeWidth,
                hasShadow = style.shadow != null,
                cornerRadius = style.cornerRadius,
                // The look applied changing nothing is the look already worn.
                current = shape != null && shape.applyingObjectStyle(style) == shape,
            )
        }
    }

    /**
     * Dresses the whole selection in the saved style [styleId], the way the shape
     * setters write the whole selection. What is not an unlocked shape in there
     * is the core's to skip, and one pick is one history entry however many
     * shapes it moved.
     */
    fun applyObjectStyle(styleId: String) {
        viewModel.onApplyObjectStyle(state.selectedElements.map { it.id }, styleId)
    }

    /** The primary shape's look saved to the deck's library as [name]. */
    fun saveObjectStyle(name: String) {
        val shape: ShapeElement = state.primaryElement as? ShapeElement ?: return
        viewModel.onSaveObjectStyle(shape.id, name)
    }

    /** Drops [styleId] from the library. Nothing wearing it changes: a style is
     * applied by copy, so the shapes keep the look they were given. */
    fun deleteObjectStyle(styleId: String) {
        viewModel.onDeleteObjectStyle(styleId)
    }

    fun renameObjectStyle(styleId: String, name: String) {
        viewModel.onRenameObjectStyle(styleId, name)
    }

    /** Whether Use As Default has a text box to take the deck's text look off. */
    fun canUseAsDefaultTextStyle(): Boolean = state.canUseAsDefaultTextStyle

    /**
     * Writes the primary text box's look into the deck's defaults, so the next
     * fresh text box arrives set that way. Nothing already on a slide moves.
     */
    fun useAsDefaultTextStyle() {
        val id: String = state.primaryElement?.id ?: return
        viewModel.onUseAsDefaultTextStyle(id)
    }

    fun canUseAsDefaultShapeStyle(): Boolean = state.canUseAsDefaultShapeStyle

    /** [useAsDefaultTextStyle] for a shape: the deck's fill, stroke and label colour. */
    fun useAsDefaultShapeStyle() {
        val id: String = state.primaryElement?.id ?: return
        viewModel.onUseAsDefaultShapeStyle(id)
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

    /**
     * The terminal style the Format inspector shows, null when the primary
     * element is not a terminal. Read off the primary, written to every unlocked
     * terminal in the selection, the way the code one works.
     */
    fun selectedTerminal(): TerminalProps? =
        (state.primaryElement as? TerminalElement)?.let { terminal ->
            TerminalProps(
                title = terminal.title,
                prompt = terminal.prompt,
                fontSize = terminal.fontSize,
                showTitleBar = terminal.showTitleBar,
            )
        }

    /** One unlocked terminal in the selection is enough for the terminal controls. */
    fun canFormatTerminals(): Boolean =
        state.selectedElements.any { it is TerminalElement && !it.locked }

    /**
     * The name in the title bar. The primary alone, because a title is content:
     * writing one field across a selection would give every terminal the same
     * name, which is not what a batch of the other three does.
     */
    fun setTerminalTitle(title: String) {
        val terminal: TerminalElement = state.primaryElement as? TerminalElement ?: return
        if (terminal.locked || terminal.title == title) return
        viewModel.onUpdateElements(listOf(terminal.copy(title = title)))
    }

    /** What every line of input is prefixed with. Blank is no prompt at all. */
    fun setTerminalPrompt(prompt: String) {
        formatTerminals { it.copy(prompt = prompt) }
    }

    /** Floors at a point, like the code block's: type with no size can't be read. */
    fun setTerminalFontSize(size: Float) {
        formatTerminals { it.copy(fontSize = size.coerceIn(1f, 400f)) }
    }

    fun setTerminalTitleBar(enabled: Boolean) {
        formatTerminals { it.copy(showTitleBar = enabled) }
    }

    // The terminal setters' [formatSelection], written out here rather than in
    // the core the way [formatCode] is: the unlocked terminals of the selection,
    // minus the ones the change left alone, committed as one history entry.
    private fun formatTerminals(transform: (TerminalElement) -> TerminalElement) {
        val edits: List<Element> = state.selectedElements.mapNotNull { element ->
            if (element !is TerminalElement || element.locked) return@mapNotNull null
            val formatted: TerminalElement = transform(element)
            return@mapNotNull if (formatted == element) null else formatted
        }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * The diagram style the Format inspector shows, null when the primary
     * element is not a diagram. Read off the primary, written to every unlocked
     * diagram in the selection, the way the terminal one works.
     */
    fun selectedDiagram(): DiagramProps? =
        (state.primaryElement as? DiagramElement)?.let { diagram ->
            DiagramProps(
                fontSize = diagram.fontSize,
                nodeFill = diagram.nodeFill,
                nodeStroke = diagram.nodeStroke,
                nodeText = diagram.nodeText,
                edgeColor = diagram.edgeColor,
            )
        }

    /** One unlocked diagram in the selection is enough for the diagram controls. */
    fun canFormatDiagrams(): Boolean =
        state.selectedElements.any { it is DiagramElement && !it.locked }

    /** Floors at a point, like the terminal's: type with no size can't be read. */
    fun setDiagramFontSize(size: Float) {
        formatDiagrams { it.copy(fontSize = size.coerceIn(1f, 400f)) }
    }

    /** What a node's box is painted with. Packed ARGB, like every colour here. */
    fun setDiagramNodeFill(argb: Long) {
        formatDiagrams { it.copy(nodeFill = argb) }
    }

    /** The line around a node's box. */
    fun setDiagramNodeStroke(argb: Long) {
        formatDiagrams { it.copy(nodeStroke = argb) }
    }

    /** The label inside a node. */
    fun setDiagramNodeText(argb: Long) {
        formatDiagrams { it.copy(nodeText = argb) }
    }

    /** The arrows between nodes, heads included. */
    fun setDiagramEdgeColor(argb: Long) {
        formatDiagrams { it.copy(edgeColor = argb) }
    }

    // The diagram setters' [formatSelection], the same shape [formatTerminals]
    // takes: the unlocked diagrams of the selection, minus the ones the change
    // left alone, committed as one history entry.
    private fun formatDiagrams(transform: (DiagramElement) -> DiagramElement) {
        val edits: List<Element> = state.selectedElements.mapNotNull { element ->
            if (element !is DiagramElement || element.locked) return@mapNotNull null
            val formatted: DiagramElement = transform(element)
            return@mapNotNull if (formatted == element) null else formatted
        }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * The equation style the Format inspector shows, null when the primary
     * element is not an equation. Read off the primary, written to every
     * unlocked equation in the selection, the way the diagram one works.
     */
    fun selectedEquation(): EquationProps? =
        (state.primaryElement as? EquationElement)?.let { equation ->
            EquationProps(
                fontSize = equation.fontSize,
                color = equation.color,
            )
        }

    /** One unlocked equation in the selection is enough for the equation controls. */
    fun canFormatEquations(): Boolean =
        state.selectedElements.any { it is EquationElement && !it.locked }

    /** Floors at a point, like the diagram's: type with no size can't be read. */
    fun setEquationFontSize(size: Float) {
        formatEquations { it.copy(fontSize = size.coerceIn(1f, 400f)) }
    }

    /** What the whole expression is set in. Packed ARGB, like every colour here. */
    fun setEquationColor(argb: Long) {
        formatEquations { it.copy(color = argb) }
    }

    // The equation setters' [formatSelection], the same shape [formatDiagrams]
    // takes: the unlocked equations of the selection, minus the ones the change
    // left alone, committed as one history entry.
    private fun formatEquations(transform: (EquationElement) -> EquationElement) {
        val edits: List<Element> = state.selectedElements.mapNotNull { element ->
            if (element !is EquationElement || element.locked) return@mapNotNull null
            val formatted: EquationElement = transform(element)
            return@mapNotNull if (formatted == element) null else formatted
        }
        if (edits.isEmpty()) return
        viewModel.onUpdateElements(edits)
    }

    /**
     * Where a link may point, in menu order. Titles alone, the way
     * [transitionKinds] hands its list over: the position is the whole of what
     * comes back, and the targets behind the names are the document's business.
     */
    fun linkKindTitles(): List<String> = LinkKindTitles

    /**
     * The primary element's link, null when that element is not one of the kinds
     * that hold one. Read off the primary; [setSelectedLink] writes the whole
     * selection, the way every other Format control does.
     *
     * A text box answers off its target or its older URL string, whichever it
     * carries, since that is the one question `resolvedLink` answers.
     */
    fun selectedLink(): LinkProps? {
        val element: Element = state.primaryElement ?: return null
        if (element !is TextElement && element !is ShapeElement && element !is ImageElement) {
            return null
        }
        val target: LinkTarget? = element.resolvedLink()
        return LinkProps(
            kindIndex = linkKindIndex(target),
            url = (target as? LinkTarget.Url)?.url ?: "",
            // What picking Slide would commit: the deck's first slide, so the
            // popup marks something rather than opening on nothing.
            slideId = (target as? LinkTarget.Slide)?.slideId
                ?: state.document.slides.firstOrNull()?.id.orEmpty(),
        )
    }

    /**
     * The slides a link may point at, as the popup spells them: their place in
     * the presentation and their name. A skipped slide is offered by name alone,
     * having no place to spell, and jumping to one goes nowhere, which is the
     * document model's rule rather than a reason to hide the row.
     *
     * Parallel to [slideChoiceIds], and off one read of the document, the way the
     * layout popup's two lists are: a menu shows a name and hands back the id
     * beside it.
     */
    fun slideChoices(): List<String> = state.document.slides.map { slide ->
        val number: Int? = state.slideNumber(slide.id)
        return@map if (number == null) slide.title else "$number. ${slide.title}"
    }

    fun slideChoiceIds(): List<String> = state.document.slides.map { it.id }

    /**
     * Points every selected element at the [kindIndex]th kind, [url] and
     * [slideId] being what the two kinds that carry one take. Index 0 unlinks.
     *
     * No guard beyond the index: the core leaves out what is locked and what
     * holds no link, and drops a batch that changed nothing, so one pick is one
     * history entry however many elements it moved. An index the list doesn't
     * have writes nothing rather than unlinking, since the number crosses a
     * language boundary on the way back.
     */
    fun setSelectedLink(kindIndex: Int, url: String, slideId: String) {
        if (kindIndex !in LinkKindTitles.indices) return
        val ids: List<String> = state.selectedElementIds
        if (ids.isEmpty()) return
        viewModel.onSetElementLinks(ids, linkTargetOf(kindIndex, url, slideId))
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
     * Slide layouts, Keynote's masters. Layout mode is a selection sitting on a
     * layout rather than on a slide, so the shell asks whether it is in it rather
     * than keeping a flag of its own: one owner, the same rule the panel toggles
     * follow.
     *
     * The two lists are parallel and stay that way, ids and names off one read of
     * the document: a name is what a menu shows and an id is what goes back, and
     * neither travels as a [Slide] the shell could edit its way around.
     */
    fun isEditingLayouts(): Boolean = state.isEditingLayouts

    fun layoutNames(): List<String> = state.document.layouts.map { it.title }

    fun layoutIds(): List<String> = state.document.layouts.map { it.id }

    /** The layout the selected slide is built on, null when it is on none. */
    fun selectedSlideLayoutId(): String? = state.selectedLayout?.id

    /** Puts the selected slide on [layoutId], or on no layout when that is null. */
    fun applyLayout(layoutId: String?) {
        viewModel.onApplyLayout(state.selectedSlide.id, layoutId)
    }

    /** Every placeholder back where the layout says it goes. A slide on none has nothing to reapply. */
    fun canReapplyLayout(): Boolean = !state.isEditingLayouts && state.selectedLayout != null

    fun reapplyLayout() {
        if (!canReapplyLayout()) return
        viewModel.onReapplyLayout(state.selectedSlide.id)
    }

    fun editSlideLayouts() {
        viewModel.onEditSlideLayouts()
    }

    fun exitSlideLayouts() {
        viewModel.onExitSlideLayouts()
    }

    fun addLayout() {
        viewModel.onAddLayout()
    }

    /** The navigator's rename, which is also the only way a layout gets a name. */
    fun renameSelectedSlide(title: String) {
        viewModel.onRenameSlide(state.selectedSlide.id, title)
    }

    fun selectedSlideTitle(): String = state.selectedSlide.title

    /**
     * What each placeholder button is called, in the order the document model
     * lists the roles. The position is the whole protocol, the way `snapTitles`
     * and [setSnap] work: an index goes back to [addPlaceholder].
     */
    fun placeholderRoles(): List<String> = PlaceholderRole.entries.map { it.name }

    /** Layout mode only; the core drops it on a slide, where a role is an instance. */
    fun addPlaceholder(role: Int) {
        val placeholder: PlaceholderRole = PlaceholderRole.entries.getOrNull(role) ?: return
        viewModel.onAddPlaceholder(placeholder)
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
     * The transitions the Animate panel's popup offers, in the model's order.
     * Titles alone, the way [shapeCatalog] hands its list over: the position is
     * the whole of what comes back, and the kinds are the document's business.
     */
    fun transitionKinds(): List<String> = TransitionKindTitles

    /** The four ways a travelling transition may run, in the same protocol. */
    fun transitionDirections(): List<String> = TransitionDirection.entries.map { it.name }

    /**
     * The selected slide's transition, for the Animate panel. A slide wearing
     * none answers -1 with the values switching off Default would commit, the
     * way [backgroundColor] answers for a slide wearing another kind, so no
     * control ever has to invent a number.
     *
     * In layout mode this reads the layout, which never carries one: a layout is
     * a template for what a slide draws, and nothing on it is ever played.
     */
    fun selectedTransition(): TransitionProps {
        val transition: SlideTransition = state.selectedSlide.transition ?: SlideTransition()
        return TransitionProps(
            kindIndex = state.selectedSlide.transition?.kind?.ordinal ?: -1,
            directionIndex = transition.direction.ordinal,
            durationMs = transition.durationMs,
            automatic = transition.trigger == TransitionTrigger.Automatic,
            delayMs = transition.delayMs,
        )
    }

    /**
     * Dresses the selected slide in a whole transition: every control commits
     * all five values, the other four as they stand, the way a typed frame
     * commits all four of its numbers.
     *
     * [kindIndex] -1 puts the slide back on the deck's default, i.e. carrying no
     * transition at all. An index no kind answers to writes nothing rather than
     * clearing, since the number crosses a language boundary on the way back.
     *
     * No layout guard: the core writes slides alone and drops a transition onto
     * the one it is already wearing, so a copy of either rule out here could only
     * ever disagree with it.
     */
    fun setTransition(
        kindIndex: Int,
        directionIndex: Int,
        durationMs: Int,
        automatic: Boolean,
        delayMs: Int,
    ) {
        val transition: SlideTransition? = if (kindIndex < 0) null else {
            val kind: TransitionKind = TransitionKind.entries.getOrNull(kindIndex) ?: return
            SlideTransition(
                kind = kind,
                direction = TransitionDirection.entries.getOrNull(directionIndex)
                    ?: TransitionDirection.Left,
                durationMs = durationMs.coerceIn(MIN_TRANSITION_MS, MAX_TRANSITION_MS),
                trigger = if (automatic) TransitionTrigger.Automatic else TransitionTrigger.OnClick,
                delayMs = delayMs.coerceAtLeast(0),
            )
        }
        viewModel.onSetSlideTransition(state.selectedSlide.id, transition)
    }

    /**
     * The selected slide's build order, in the order it plays: what the Animate
     * panel's list draws and what its editor writes back.
     *
     * A build naming an element the slide no longer holds still gets a row: the
     * document says it is there, and a row is how it can be removed.
     */
    fun buildRows(): List<BuildRow> {
        val slide: Slide = state.selectedSlide
        val selected: List<String> = state.selectedElementIds
        return slide.builds.mapIndexed { index, build ->
            val element: Element? = slide.elementById(build.elementId)
            val deliveries: List<BuildDelivery> = deliveriesFor(element)
            // What an action control would commit if the build had one, so the
            // fields under an action popup never start from nothing.
            val action: BuildAction = build.action ?: BuildAction(ActionKind.Move)
            return@mapIndexed BuildRow(
                index = index,
                title = element?.rowTitle() ?: "Missing element",
                meta = build.rowMeta(),
                elementId = build.elementId,
                active = build.elementId in selected,
                kindIndex = build.kind.ordinal,
                effectIndex = build.effect.ordinal,
                deliveryIndex = deliveries.indexOf(build.delivery).coerceAtLeast(0),
                triggerIndex = build.trigger.ordinal,
                durationMs = build.durationMs,
                delayMs = build.delayMs,
                elementStep = build.elementStep ?: -1,
                actionKindIndex = build.action?.kind?.ordinal ?: -1,
                dx = action.dx,
                dy = action.dy,
                opacity = action.opacity,
                rotation = action.rotation,
                scale = action.scale,
                hasStepTarget = hasSteps(element),
                deliveryTitles = deliveries.map { BuildDeliveryTitles[it.ordinal] },
            )
        }
    }

    /** The effects a build may play, in the model's order. Positions come back. */
    fun buildEffectTitles(): List<String> = BuildEffectTitles

    /** What may start a build, in the same protocol. */
    fun buildTriggerTitles(): List<String> = BuildTriggerTitles

    /** What an action may do to its element, in the same protocol. */
    fun actionKindTitles(): List<String> = ActionKindTitles

    /**
     * Appends a build that brings the primary element on, which is where a new
     * one lands: the order is the order they play in. Nothing selected is no
     * build rather than a guess at whose it would be.
     */
    fun addBuildIn() {
        val element: Element = state.primaryElement ?: return
        viewModel.onAddBuild(Build(elementId = element.id, kind = BuildKind.In))
    }

    /** [addBuildIn]'s mirror: the build that takes the primary element away. */
    fun addBuildOut() {
        val element: Element = state.primaryElement ?: return
        viewModel.onAddBuild(
            Build(elementId = element.id, kind = BuildKind.Out, effect = BuildEffect.Dissolve),
        )
    }

    /**
     * Appends an action on the primary element: a move to start with, far enough
     * to see, since an action that changes nothing looks like one that failed.
     */
    fun addAction() {
        val element: Element = state.primaryElement ?: return
        viewModel.onAddBuild(
            Build.action(
                elementId = element.id,
                action = BuildAction(kind = ActionKind.Move, dx = DEFAULT_ACTION_DX),
            ),
        )
    }

    /**
     * Commits a whole build over the one at [index]: every control in the editor
     * sends the other values as they stand, the way a typed frame commits all
     * four of its numbers.
     *
     * The kind and the element are the build's own and never change here: what
     * a build plays is edited, what it plays on is not. [actionKindIndex] -1
     * clears the action, and a build that is not an action carries none whatever
     * the index says. An index the slide has no build at is a no-op.
     */
    fun updateBuild(
        index: Int,
        effectIndex: Int,
        deliveryIndex: Int,
        triggerIndex: Int,
        durationMs: Int,
        delayMs: Int,
        elementStep: Int,
        actionKindIndex: Int,
        dx: Float,
        dy: Float,
        opacity: Float,
        rotation: Float,
        scale: Float,
    ) {
        val slide: Slide = state.selectedSlide
        val build: Build = slide.builds.getOrNull(index) ?: return
        val deliveries: List<BuildDelivery> = deliveriesFor(slide.elementById(build.elementId))
        val action: BuildAction? =
            if (build.kind != BuildKind.Action) null
            else ActionKind.entries.getOrNull(actionKindIndex)?.let { kind ->
                BuildAction(
                    kind = kind,
                    dx = dx,
                    dy = dy,
                    opacity = opacity.coerceIn(0f, 1f),
                    rotation = rotation,
                    scale = scale,
                )
            }

        viewModel.onUpdateBuild(
            index,
            build.copy(
                effect = BuildEffect.entries.getOrNull(effectIndex) ?: build.effect,
                delivery = deliveries.getOrNull(deliveryIndex) ?: BuildDelivery.All,
                trigger = BuildTrigger.entries.getOrNull(triggerIndex) ?: build.trigger,
                durationMs = durationMs.coerceIn(MIN_BUILD_MS, MAX_BUILD_MS),
                delayMs = delayMs.coerceAtLeast(0),
                elementStep = elementStep.takeIf { it >= 0 },
                action = action,
            ),
        )
    }

    /** Drops the build at [index]. The element stays exactly where it is. */
    fun removeBuild(index: Int) {
        viewModel.onRemoveBuild(index)
    }

    /** The list's drag: steps are a function of the order, so this re-times the slide. */
    fun moveBuild(from: Int, to: Int) {
        viewModel.onMoveBuild(from, to)
    }

    /**
     * Puts the canvas selection on the element the build at [index] plays, so
     * clicking a row shows what it is about to animate.
     */
    fun selectBuildElement(index: Int) {
        val build: Build = state.selectedSlide.builds.getOrNull(index) ?: return
        viewModel.onSelectElement(build.elementId)
    }

    /**
     * The deck's own background, under every slide that asks for none of its
     * own. Read and written exactly the way the slide's is above, down to the
     * value a switched kind would commit, so one section of controls drives
     * either by being handed the other's four numbers.
     */
    fun deckBackgroundKind(): Int = when (state.document.background) {
        null -> 0
        is SlideBackground.Color -> 1
        is SlideBackground.Gradient -> 2
    }

    fun deckBackgroundColor(): Long =
        (state.document.background as? SlideBackground.Color)?.color ?: DEFAULT_BACKGROUND_COLOR

    fun deckBackgroundGradientStart(): Long =
        (state.document.background as? SlideBackground.Gradient)?.start ?: DEFAULT_GRADIENT_START

    fun deckBackgroundGradientEnd(): Long =
        (state.document.background as? SlideBackground.Gradient)?.end ?: DEFAULT_GRADIENT_END

    /** Back to the app's own dark gradient: the deck stops carrying one at all. */
    fun setDeckBackgroundDefault() {
        viewModel.onSetDocumentBackground(null)
    }

    fun setDeckBackgroundColor(argb: Long) {
        viewModel.onSetDocumentBackground(SlideBackground.Color(argb))
    }

    fun setDeckBackgroundGradient(start: Long, end: Long) {
        viewModel.onSetDocumentBackground(SlideBackground.Gradient(start, end))
    }

    /**
     * The looks the deck can be put on, in picker order: the five built-ins
     * first, then whatever the user has saved. Names alone, the way the layout
     * popup takes names: a Theme the shell could hold is a look it could edit
     * its way around, and the name is the whole of what goes back.
     */
    fun themeNames(): List<String> = state.themes.map { it.name }

    /** The user's own, the subset Delete Theme is offered for. */
    fun userThemeNames(): List<String> = state.userThemes.map { it.name }

    /** What the deck is wearing, which is a name the document carries. */
    fun currentThemeName(): String = state.document.themeName

    /**
     * Puts the deck on the theme called [name]: background, element defaults and
     * layouts at once. A name no theme answers to is a no-op in the core, so the
     * list having come from [themeNames] is not something to re-check here.
     */
    fun changeTheme(name: String) {
        viewModel.onChangeTheme(name)
    }

    /** The deck's own look saved to the library as [name], and the deck put on it. */
    fun saveAsTheme(name: String) {
        viewModel.onSaveAsTheme(name)
    }

    /** Drops [name] from the library. Nothing on any slide moves. */
    fun deleteUserTheme(name: String) {
        viewModel.onDeleteUserTheme(name)
    }

    /**
     * The slide shapes the size picker offers, in the catalog's order. Titles
     * alone, the way [shapeCatalog] hands its list over: the position is what
     * comes back, and the sizes behind the names are the document's business.
     */
    fun slideSizePresetTitles(): List<String> = SlideSizePreset.entries.map { it.title }

    /**
     * Which of [slideSizePresetTitles] the deck is on, -1 for a size that is
     * none of them. A number rather than a nullable: the popup shows a title or
     * it shows the measurements, and neither is a null for the shell to spell.
     */
    fun slideSizePresetIndex(): Int = state.document.slideSizePreset()?.ordinal ?: -1

    /** The slide the deck is on, in document units. What a custom field starts at. */
    fun slideWidth(): Float = state.document.slideWidth

    fun slideHeight(): Float = state.document.slideHeight

    /**
     * Puts the deck on a [width] by [height] slide. [scaleContent] carries the
     * content across with it; left false, only the slide changes and the new
     * edges fall where they fall.
     *
     * No guard: the core clamps both sides to what a slide may be and drops a
     * resize onto the size the deck is already on, so a copy of either rule out
     * here could only ever disagree with it.
     */
    fun setSlideSize(width: Float, height: Float, scaleContent: Boolean) {
        viewModel.onSetSlideSize(width, height, scaleContent)
    }

    /**
     * Puts the deck on the [index]th preset, [setSlideSize] by name: the shapes
     * travel as titles, so the measurements behind one are never the shell's to
     * hold. An index the catalog doesn't have is no resize rather than a crash,
     * the way [insertShape] treats its own, since the number crosses a language
     * boundary on the way back.
     */
    fun setSlideSizePreset(index: Int, scaleContent: Boolean) {
        val preset: SlideSizePreset = SlideSizePreset.entries.getOrNull(index) ?: return
        setSlideSize(preset.width, preset.height, scaleContent)
    }

    /**
     * The kinds of show the deck may be, in the popup's order. Titles alone,
     * like every other list that crosses here: a place in it is an ordinal.
     */
    fun playbackTypeTitles(): List<String> = PlaybackTypeTitles

    /** How the deck plays right now, what the Playback section shows. */
    fun playback(): PlaybackProps {
        val settings: PlaybackSettings = state.document.playback
        return PlaybackProps(
            typeIndex = settings.type.ordinal,
            autoAdvanceMs = settings.autoAdvanceMs,
            loop = settings.loop,
            restartAfterIdleMs = settings.restartAfterIdleMs,
        )
    }

    /**
     * Commits the lot, the way the background controls commit a whole kind: each
     * control sends its own value beside the three the panel is already holding,
     * so one click is one history entry and no setting is ever half-written.
     *
     * A type the enum doesn't have writes nothing, the way [setSnap] treats a
     * kind it doesn't know: the number crosses a language boundary on the way
     * back. Settings the deck already carries are dropped in the core, so a
     * control set to what it already said spends no history entry.
     */
    fun setPlayback(typeIndex: Int, autoAdvanceMs: Int, loop: Boolean, restartAfterIdleMs: Int) {
        val type: PlaybackType = PlaybackType.entries.getOrNull(typeIndex) ?: return
        viewModel.onSetPlayback(
            PlaybackSettings(
                type = type,
                autoAdvanceMs = autoAdvanceMs.coerceAtLeast(0),
                loop = loop,
                restartAfterIdleMs = restartAfterIdleMs.coerceAtLeast(0),
            ),
        )
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
        // A gallery thumbnail lands after the pass that asked for it, and no
        // state changed to say so. Dropping the first skips the value the flow
        // is already holding, so subscribing is not itself a change.
        val thumbs = scope.launch { galleryTick.drop(1).collect { callback() } }
        return {
            job.cancel()
            thumbs.cancel()
        }
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
    fun startPlay(onExit: () -> Unit): PlaySession = playSession(onExit, rehearsal = false)

    /**
     * Rehearses the deck from the selected slide: the show [startPlay] starts,
     * with the presenter display as its only face. The player is still composed,
     * since it is what the controller both faces read plays on, and the host
     * hides it: a rehearsal has no audience to put a slide in front of.
     */
    fun startRehearsal(onExit: () -> Unit): PlaySession = playSession(onExit, rehearsal = true)

    private fun playSession(onExit: () -> Unit, rehearsal: Boolean): PlaySession = PlaySession(
        document = state.document,
        startIndex = state.selectedSlideIndex().coerceAtLeast(0),
        onExit = onExit,
        // The show plays a snapshot; the presenter follows the document, so a
        // note typed mid-show is the note it goes on showing.
        live = viewModel.states.map { it.document }.distinctUntilChanged(),
        notes = { slideId, text ->
            val slide: Slide? = state.document.slideById(slideId)
            if (slide != null && slide.notes != text) viewModel.onUpdateSlide(slide.copy(notes = text))
        },
        isRehearsal = rehearsal,
        assets = viewModel.assets,
    )

    /**
     * Plays the selected slide alone, from its first step: the deck cut down to
     * that one slide, so the builds run the way they will in the show and
     * nothing follows it. [onExit] behaves as [startPlay]'s does.
     */
    fun startPreview(onExit: () -> Unit): PlaySession =
        PlaySession(
            state.document.previewOf(state.selectedSlide.id),
            0,
            onExit,
            assets = viewModel.assets,
        )

    /**
     * The document as a standalone CuP project, file by file. Generating is all
     * that happens here: writing them out is the shell's, so the panel, the
     * directories and the error dialog stay on the AppKit side and the generator
     * stays a pure function of the document.
     */
    fun cupProject(): List<ExportFile> =
        state.document.toCupProject().map { ExportFile(it.path, it.contents) }

    /**
     * What an export calls itself, and so what the folder written under the
     * chosen directory is named. Read back out of the settings file rather than
     * re-derived: the slug is the generator's rule, and a second copy of it out
     * here could only ever disagree with it.
     */
    fun cupProjectName(): String {
        val settings: ExportedFile? = state.document.toCupProject()
            .firstOrNull { it.path == "settings.gradle.kts" }
        val name: String? = settings?.let { RootProjectName.find(it.contents)?.groupValues?.get(1) }
        return name?.takeIf { it.isNotEmpty() } ?: "presentation"
    }

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
        // In layout mode the rows are the layouts, so that is what an index means
        // here too: one navigator, one row protocol, whichever list it is showing.
        if (state.isEditingLayouts) {
            val layout: Slide = state.document.layouts.getOrNull(index) ?: return null
            return render(layout, layout = null, number = null, width = width)
        }

        val slide: Slide = state.document.allSlides().getOrNull(index) ?: return null
        return render(
            slide,
            layout = state.document.layoutOf(slide),
            number = state.slideNumber(slide.id),
            width = width,
        )
    }

    /**
     * The same render for a layout by id, which is what the Document panel's
     * layout card shows. A layout is on no layout and carries no number, so it
     * draws as nothing but itself.
     */
    fun layoutThumbnail(layoutId: String, width: Int): NSImage? {
        val layout: Slide = state.document.layouts.firstOrNull { it.id == layoutId } ?: return null
        return render(layout, layout = null, number = null, width = width)
    }

    private fun render(slide: Slide, layout: Slide?, number: Int?, width: Int): NSImage? {
        val background: SlideBackground? = state.document.background
        val slideWidth: Float = state.document.slideWidth
        val slideHeight: Float = state.document.slideHeight
        val cached = thumbnails[slide.id]
        // Settled, the cache has to match the slide; mid-gesture any render of it will do.
        val usable = cached != null && cached.width == width &&
            cached.slideWidth == slideWidth && cached.slideHeight == slideHeight &&
            ((cached.slide == slide && cached.layout == layout && cached.number == number &&
                cached.background == background) || state.isPreviewing)
        if (usable) return cached.image

        val height = (width * slideHeight / slideWidth).toInt()
        val skiaImage = renderComposeScene(width * 2, height * 2) {
            // One frame, so an image only lands here if the canvas has already
            // decoded it: the row catches up on the next render, which is the
            // next edit. Cheaper than blocking a thumbnail on a decode.
            CompositionLocalProvider(LocalAssetStore provides viewModel.assets) {
                SlideView(
                    slide = slide,
                    layout = layout,
                    number = number,
                    background = background,
                    slideWidth = slideWidth,
                    slideHeight = slideHeight,
                )
            }
        }
        val png = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val nsData = png.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), png.size.toULong())
        }
        val image = NSImage(data = nsData)?.apply {
            setSize(NSMakeSize(width.toDouble(), height.toDouble()))
        } ?: return null

        thumbnails[slide.id] =
            Thumbnail(slide, layout, number, background, width, slideWidth, slideHeight, image)
        return image
    }

    /**
     * The bundle this window is on. What the shell compares an Open against, so
     * a deck that is already up gets its window raised instead of a second one.
     * Empty for a host with no bundle behind it.
     */
    fun location(): String = viewModel.location

    /** The deck's name, which is what the toolbar puts where a title bar would be. */
    fun title(): String = state.title

    /**
     * Whether the last few edits are still on their way to disk, which is what
     * the toolbar's "Edited" tag is behind. Not dirtiness: everything is saved,
     * this only says the debounced write has not landed yet.
     */
    fun isEdited(): Boolean = state.savePending

    /** File > Rename. The bundle keeps its own name; only the deck is renamed. */
    fun renameDocument(name: String) {
        viewModel.onRenameDocument(name)
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

/**
 * What File > New, Open, Save As and Open Recent come down to, as flat functions
 * a SwiftUI menu can call.
 *
 * A namespace rather than state: nothing is held here, every call goes straight
 * to `Cupboard`'s factories and comes back as an [EditorHost] the shell puts in
 * a window. Kept out of [EditorHost] because none of it is about one window: a
 * host that is being saved as is the argument, not the receiver.
 */
object Documents {
    /**
     * Lays out a fresh `Untitled.cupboard` in Cupboard's own folder and says
     * where it went. Open it with [open] like any other deck; nothing is opened
     * here, so New and a double-clicked file are one path.
     */
    fun newDocument(): String = Cupboard.newDocument()

    /** The deck at [path] in a host of its own, or why it could not be opened. */
    fun open(path: String): OpenOutcome = when (val result: OpenResult = Cupboard.openDocument(path)) {
        is OpenResult.Opened -> OpenOutcome(EditorHost(result.viewModel), null)
        is OpenResult.Failed -> OpenOutcome(null, result.reason)
    }

    /**
     * [host]'s deck copied into a new bundle at [path], and a host on that.
     *
     * The window swaps to what comes back and closes the one it passed in: a
     * view model's bundle is fixed for its life, so Save As is a new editor
     * rather than a moved one.
     */
    fun saveAs(host: EditorHost, path: String): EditorHost =
        EditorHost(Cupboard.saveAs(host.viewModel, path))

    /** The decks this machine opened last, newest first, for Open Recent. */
    fun recents(): List<String> = Cupboard.recentDocuments()

    /** Takes [path] out of that list, for the menu's Clear. */
    fun forgetRecent(path: String) {
        Cupboard.forgetRecent(path)
    }
}

/**
 * Either the host a deck opened into or the sentence saying why it did not.
 *
 * A pair rather than a nullable host because a failure has something to say, and
 * a plain class rather than a sealed hierarchy because Swift reads two optional
 * properties more easily than it type-checks an exported ObjC class.
 */
class OpenOutcome(val host: EditorHost?, val error: String?)
