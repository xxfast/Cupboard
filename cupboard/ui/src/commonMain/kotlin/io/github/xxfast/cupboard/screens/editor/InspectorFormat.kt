package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.LocalAssetStore
import io.github.xxfast.cupboard.canvas.gradientStop
import io.github.xxfast.cupboard.canvas.rememberAssetImage
import io.github.xxfast.cupboard.canvas.removeBackground
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeLanguages
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.DiagramElement
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
import io.github.xxfast.cupboard.document.ObjectStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
import io.github.xxfast.cupboard.document.ShapeElement
import io.github.xxfast.cupboard.document.ShapeGradient
import io.github.xxfast.cupboard.document.ShapeKind
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.Slide
import io.github.xxfast.cupboard.document.TerminalElement
import io.github.xxfast.cupboard.document.TextAlign
import io.github.xxfast.cupboard.document.TextElement
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.applyingObjectStyle
import io.github.xxfast.cupboard.document.formatCode
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.isBold
import io.github.xxfast.cupboard.document.resolvedLink
import io.github.xxfast.cupboard.document.sources
import io.github.xxfast.cupboard.document.toggleBold
import io.github.xxfast.cupboard.document.toggleItalic
import io.github.xxfast.cupboard.document.toggleStrikethrough
import io.github.xxfast.cupboard.document.toggleUnderline
import io.github.xxfast.cupboard.editor.AlignEdge
import io.github.xxfast.cupboard.editor.Axis
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Format tab: Keynote's shape, a full-width segmented control over one
 * segment's body.
 *
 * Which segments the selection offers and which of them is showing are both the
 * core's ([EditorState.formatSegments], [EditorState.activeFormatSegment]), so
 * the preference survives a selection that can't honour it and comes back when
 * one that can arrives. The picker and the object-style strip are pinned; only
 * the sections under them scroll.
 *
 * With nothing selected the tab falls back to the slide itself, which is where
 * the layout card, the appearance checklist and the slide background live.
 */
@Composable
internal fun ColumnScope.FormatPanel(
    segments: List<FormatSegment>,
    activeSegment: FormatSegment?,
    onSelectSegment: (FormatSegment) -> Unit,
    expandedSections: Set<InspectorSection>,
    onToggleSection: (InspectorSection) -> Unit,
    selectedElements: List<Element>,
    onUpdateElements: (List<Element>) -> Unit,
    onPreviewElements: (List<Element>) -> Unit,
    onReorderElements: (List<String>, ZOrderMove) -> Unit,
    onSetElementsLocked: (List<String>, Boolean) -> Unit,
    onFlipElements: (List<String>, FlipAxis) -> Unit,
    onGroupElements: (List<String>) -> Unit,
    onUngroupElements: (String) -> Unit,
    onAlignElements: (AlignEdge) -> Unit,
    onDistributeElements: (Axis) -> Unit,
    canBringForward: Boolean,
    canSendBackward: Boolean,
    slides: List<Slide>,
    onSetElementLinks: (List<String>, LinkTarget?) -> Unit,
    objectStyles: List<ObjectStyle>,
    onApplyObjectStyle: (ids: List<String>, styleId: String) -> Unit,
    onSaveObjectStyle: (shapeId: String, name: String) -> Unit,
    onRenameObjectStyle: (styleId: String, name: String) -> Unit,
    onDeleteObjectStyle: (styleId: String) -> Unit,
    onReplaceImage: ((ImageElement) -> Unit)?,
    onAddGalleryImages: ((GalleryElement) -> Unit)?,
    onAddGallerySteps: (String) -> Unit,
    /**
     * The code block's versions: which one the canvas is showing
     * ([EditorState.codeVersion]) and the four verbs of the Versions list.
     */
    codeVersion: Int,
    onSelectCodeVersion: (Int) -> Unit,
    onAddCodeVersion: (String) -> Unit,
    onRemoveCodeVersion: (elementId: String, index: Int) -> Unit,
    onMoveCodeVersion: (elementId: String, from: Int, to: Int) -> Unit,
    /** The slide fallback's own arguments: what Format shows with nothing selected. */
    slide: Slide,
    layouts: List<Slide>,
    isEditingLayouts: Boolean,
    onUpdateSlide: (Slide) -> Unit,
    onApplyLayout: (slideId: String, layoutId: String?) -> Unit,
    onReapplyLayout: (slideId: String) -> Unit,
    onEditSlideLayouts: () -> Unit,
    onExitSlideLayouts: () -> Unit,
    onAddPlaceholder: (PlaceholderRole) -> Unit,
    onRenameSlide: (id: String, title: String) -> Unit,
) {
    val primary: Element? = selectedElements.firstOrNull()
    if (activeSegment == null || primary == null) {
        SlideFormatPanel(
            slide = slide,
            layouts = layouts,
            isEditingLayouts = isEditingLayouts,
            onUpdate = onUpdateSlide,
            onApplyLayout = onApplyLayout,
            onReapplyLayout = onReapplyLayout,
            onEditSlideLayouts = onEditSlideLayouts,
            onExitSlideLayouts = onExitSlideLayouts,
            onAddPlaceholder = onAddPlaceholder,
            onRenameSlide = onRenameSlide,
        )
        return
    }

    PinnedBlock {
        SegmentPicker(
            options = segments.map { segment -> segment to segment.name },
            selected = activeSegment,
            onPick = onSelectSegment,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Keynote pins the style swatches above the scroll and lets only the
    // sections below them move, which is the one thing about this panel that is
    // layout rather than content.
    if (activeSegment == FormatSegment.Style && primary is ShapeElement) PinnedBlock {
        ObjectStyleStrip(
            styles = objectStyles,
            primary = primary,
            ids = selectedElements.map { it.id },
            enabled = !primary.locked,
            onApply = onApplyObjectStyle,
            onSave = onSaveObjectStyle,
            onRename = onRenameObjectStyle,
            onDelete = onDeleteObjectStyle,
        )
        PanelDivider()
    }

    PanelBody {
        when (activeSegment) {
            FormatSegment.Style -> StyleBody(
                primary = primary,
                elements = selectedElements,
                onUpdate = onUpdateElements,
                onPreview = onPreviewElements,
                expandedSections = expandedSections,
                onToggleSection = onToggleSection,
            )

            FormatSegment.Arrange -> ArrangeBody(
                elements = selectedElements,
                onUpdate = onUpdateElements,
                onReorder = onReorderElements,
                onSetLocked = onSetElementsLocked,
                onFlip = onFlipElements,
                onGroup = onGroupElements,
                onUngroup = onUngroupElements,
                onAlign = onAlignElements,
                onDistribute = onDistributeElements,
                canBringForward = canBringForward,
                canSendBackward = canSendBackward,
            )

            else -> KindBody(
                segment = activeSegment,
                primary = primary,
                elements = selectedElements,
                onUpdate = onUpdateElements,
                onPreview = onPreviewElements,
                expandedSections = expandedSections,
                onToggleSection = onToggleSection,
                slides = slides,
                onSetLinks = onSetElementLinks,
                onReplaceImage = onReplaceImage,
                onAddGalleryImages = onAddGalleryImages,
                onAddGallerySteps = onAddGallerySteps,
                codeVersion = codeVersion,
                onSelectCodeVersion = onSelectCodeVersion,
                onAddCodeVersion = onAddCodeVersion,
                onRemoveCodeVersion = onRemoveCodeVersion,
                onMoveCodeVersion = onMoveCodeVersion,
            )
        }
    }
}

/**
 * The Style segment: how the primary element is dressed, whatever kind it is,
 * with Opacity last because every kind has one.
 *
 * What sits above the opacity follows the kind, the way Keynote's does: a shape
 * has a fill, a line has a stroke and two ends, a code block has a theme and an
 * image has neither. A kind with nothing of its own is the opacity alone rather
 * than an invented control.
 */
@Composable
private fun StyleBody(
    primary: Element,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
    expandedSections: Set<InspectorSection>,
    onToggleSection: (InspectorSection) -> Unit,
) {
    when (primary) {
        is ShapeElement -> ShapeStyleBody(
            primary = primary,
            elements = elements,
            onUpdate = onUpdate,
            expandedSections = expandedSections,
            onToggleSection = onToggleSection,
        )

        is CodeElement -> DropdownField(
            label = "Theme",
            value = primary.theme,
            options = CodeTheme.entries.map { theme -> theme to theme.name },
            enabled = !primary.locked,
            onPick = { theme ->
                elements.formatCode { it.copy(theme = theme) }
                    .takeIf { it.isNotEmpty() }
                    ?.let(onUpdate)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        is TerminalElement -> AppearanceRow(
            label = "Show Title Bar",
            checked = primary.showTitleBar,
            onToggle = if (primary.locked) null else ({ on ->
                elements.formatTerminals { it.copy(showTitleBar = on) }
                    .takeIf { it.isNotEmpty() }
                    ?.let(onUpdate)
            }),
        )

        is DiagramElement -> DiagramColors(primary = primary, elements = elements, onUpdate = onUpdate)

        is EquationElement -> EquationColor(primary = primary, elements = elements, onUpdate = onUpdate)

        else -> Unit
    }

    PanelDivider()

    OpacitySection(elements = elements, onUpdate = onUpdate, onPreview = onPreview)
}

/**
 * A shape's looks: Fill, Border and Shadow as disclosures, then the corner
 * radius of the one kind that has one.
 *
 * A line takes the same three with different words on them. It has no inside to
 * fill, so the Fill disclosure is absent rather than dead, and its Border is the
 * stroke plus the two ends, which is where Keynote puts them too.
 */
@Composable
private fun ShapeStyleBody(
    primary: ShapeElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    expandedSections: Set<InspectorSection>,
    onToggleSection: (InspectorSection) -> Unit,
) {
    val enabled: Boolean = !primary.locked
    val gradient: ShapeGradient? = primary.gradient
    val shadow: ShapeShadow? = primary.shadow
    val isLine: Boolean = primary.kind == ShapeKind.Line

    // False when the transform changed nothing anywhere, so a field that typed
    // its way to the value the document already holds puts itself back.
    fun format(transform: (ShapeElement) -> ShapeElement): Boolean {
        val formatted: List<Element> = elements.formatShapes(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    if (!isLine) DisclosureSection(
        title = "Fill",
        expanded = InspectorSection.Fill in expandedSections,
        onToggle = { onToggleSection(InspectorSection.Fill) },
        summary = { ColorSummary(gradient?.start ?: primary.fill) },
    ) {
        SegmentedRow {
            Segment(
                selected = gradient == null,
                first = true,
                // Dropping the gradient is all it takes to go back to the solid:
                // the fill was never overwritten, so it is still the one to
                // return to.
                onClick = if (enabled) ({ format { it.copy(gradient = null) } }) else null,
            ) {
                SegmentLabel("Color", selected = gradient == null, enabled = enabled)
            }
            Segment(
                selected = gradient != null,
                first = false,
                // A shape switched into a gradient has none yet, so it starts
                // from its own fill and runs to the palette's deep indigo.
                onClick = if (enabled) ({
                    format {
                        if (it.gradient != null) it
                        else it.copy(gradient = ShapeGradient(it.fill, SHAPE_GRADIENT_END))
                    }
                }) else null,
            ) {
                SegmentLabel("Gradient", selected = gradient != null, enabled = enabled)
            }
        }

        if (gradient == null) {
            SwatchRow(
                colors = TEXT_SWATCHES,
                selected = primary.fill,
                enabled = enabled,
                onPick = { color -> format { it.copy(fill = color) } },
            )
            HexField(
                label = "Hex",
                color = primary.fill,
                enabled = enabled,
                onCommit = { color -> format { it.copy(fill = color) } },
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HexField(
                    label = "Start",
                    color = gradient.start,
                    enabled = enabled,
                    onCommit = { color ->
                        format { it.copy(gradient = it.gradient?.copy(start = color)) }
                    },
                    modifier = Modifier.weight(1f),
                )
                HexField(
                    label = "End",
                    color = gradient.end,
                    enabled = enabled,
                    onCommit = { color ->
                        format { it.copy(gradient = it.gradient?.copy(end = color)) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            NumberField(
                label = "Angle",
                value = gradient.angle,
                enabled = enabled,
                onCommit = { angle ->
                    format { it.copy(gradient = it.gradient?.copy(angle = angle)) }
                },
                modifier = Modifier.width(120.dp),
                unit = Units.Degrees,
            )
        }
    }

    DisclosureSection(
        title = if (isLine) "Stroke" else "Border",
        expanded = InspectorSection.Border in expandedSections,
        onToggle = { onToggleSection(InspectorSection.Border) },
        summary = { BorderSummary(primary.strokeColor, primary.strokeWidth) },
    ) {
        SwatchRow(
            colors = TEXT_SWATCHES,
            selected = primary.strokeColor,
            enabled = enabled,
            onPick = { color -> format { it.copy(strokeColor = color) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HexField(
                label = "Hex",
                color = primary.strokeColor,
                enabled = enabled,
                onCommit = { color -> format { it.copy(strokeColor = color) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Width",
                value = primary.strokeWidth,
                enabled = enabled,
                onCommit = { width -> format { it.copy(strokeWidth = width) } },
                modifier = Modifier.width(104.dp),
                minimum = 0f,
                fractional = true,
            )
        }

        if (isLine) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppearanceRow(
                label = "Start Arrow",
                checked = primary.startArrow,
                onToggle = if (!enabled) null else ({ on -> format { it.copy(startArrow = on) } }),
            )
            AppearanceRow(
                label = "End Arrow",
                checked = primary.endArrow,
                onToggle = if (!enabled) null else ({ on -> format { it.copy(endArrow = on) } }),
            )
        }
    }

    DisclosureSection(
        title = "Shadow",
        expanded = InspectorSection.Shadow in expandedSections,
        onToggle = { onToggleSection(InspectorSection.Shadow) },
        summary = { ShadowSummary(shadow) },
    ) {
        // Off is no shadow at all rather than a transparent one, so switching it
        // back on lands on the document's own default every time.
        AppearanceRow(
            label = "Shadow",
            checked = shadow != null,
            onToggle = if (!enabled) null else ({ on ->
                format { it.copy(shadow = if (on) ShapeShadow() else null) }
            }),
        )
        if (shadow != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HexField(
                label = "Color",
                color = shadow.color,
                enabled = enabled,
                onCommit = { color -> format { it.copy(shadow = it.shadow?.copy(color = color)) } },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Blur",
                value = shadow.blur,
                enabled = enabled,
                onCommit = { blur -> format { it.copy(shadow = it.shadow?.copy(blur = blur)) } },
                modifier = Modifier.width(104.dp),
                minimum = 0f,
            )
        }
    }

    if (primary.kind == ShapeKind.Rectangle) NumberField(
        label = "Corner Radius",
        value = primary.cornerRadius,
        enabled = enabled,
        onCommit = { radius -> format { it.copy(cornerRadius = radius) } },
        modifier = Modifier.width(150.dp),
        minimum = 0f,
    )
}

/** The four colours a chart is made of, typed as hex: node, outline, label, arrow. */
@Composable
private fun DiagramColors(
    primary: DiagramElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    fun format(transform: (DiagramElement) -> DiagramElement): Boolean {
        val formatted: List<Element> = elements.formatDiagrams(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    HexField(
        label = "Node Fill",
        color = primary.nodeFill,
        enabled = enabled,
        onCommit = { color -> format { it.copy(nodeFill = color) } },
    )
    HexField(
        label = "Node Stroke",
        color = primary.nodeStroke,
        enabled = enabled,
        onCommit = { color -> format { it.copy(nodeStroke = color) } },
    )
    HexField(
        label = "Node Text",
        color = primary.nodeText,
        enabled = enabled,
        onCommit = { color -> format { it.copy(nodeText = color) } },
    )
    HexField(
        label = "Edge",
        color = primary.edgeColor,
        enabled = enabled,
        onCommit = { color -> format { it.copy(edgeColor = color) } },
    )
}

/**
 * An equation's one colour, over the text palette: an equation is ink on the
 * slide, set in the same serif at the same sizes as a heading, so the palette
 * that dresses text is the palette that dresses it.
 */
@Composable
private fun EquationColor(
    primary: EquationElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    fun format(transform: (EquationElement) -> EquationElement): Boolean {
        val formatted: List<Element> = elements.formatEquations(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    SwatchLabel("Color")
    SwatchRow(
        colors = TEXT_SWATCHES,
        selected = primary.color,
        enabled = enabled,
        onPick = { color -> format { it.copy(color = color) } },
    )
    HexField(
        label = "Hex",
        color = primary.color,
        enabled = enabled,
        onCommit = { color -> format { it.copy(color = color) } },
    )
}

/** Every kind's last row: the whole drag is one edit, samples preview and the release commits. */
@Composable
private fun OpacitySection(
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
) {
    val primary: Element = elements.first()

    SwatchLabel("Opacity")
    SliderRow(
        fraction = primary.opacity,
        valueLabel = "${(primary.opacity * 100).roundToInt()}%",
        enabled = !primary.locked,
        onDrag = { value -> onPreview(elements.map { it.update(opacity = value) }) },
        onRelease = { value -> onUpdate(elements.map { it.update(opacity = value) }) },
    )
}

/**
 * The kind segment: what the selected element holds, as opposed to how it is
 * dressed. Text for a text box, the picture's crop and corrections for an image,
 * the language for a code block, and so on down the kinds.
 *
 * A shape's Text segment is its label, which is the only text a shape has.
 * Link closes the segment for the three kinds that can hold one.
 */
@Composable
private fun KindBody(
    segment: FormatSegment,
    primary: Element,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
    expandedSections: Set<InspectorSection>,
    onToggleSection: (InspectorSection) -> Unit,
    slides: List<Slide>,
    onSetLinks: (List<String>, LinkTarget?) -> Unit,
    onReplaceImage: ((ImageElement) -> Unit)?,
    onAddGalleryImages: ((GalleryElement) -> Unit)?,
    onAddGallerySteps: (String) -> Unit,
    codeVersion: Int,
    onSelectCodeVersion: (Int) -> Unit,
    onAddCodeVersion: (String) -> Unit,
    onRemoveCodeVersion: (elementId: String, index: Int) -> Unit,
    onMoveCodeVersion: (elementId: String, from: Int, to: Int) -> Unit,
) {
    when {
        segment == FormatSegment.Text && primary is TextElement -> TextBody(
            primary = primary,
            elements = elements,
            onUpdate = onUpdate,
            expandedSections = expandedSections,
            onToggleSection = onToggleSection,
        )

        segment == FormatSegment.Text && primary is ShapeElement ->
            ShapeLabelBody(primary = primary, elements = elements, onUpdate = onUpdate)

        segment == FormatSegment.Image && primary is ImageElement -> ImageBody(
            primary = primary,
            elements = elements,
            onUpdate = onUpdate,
            onPreview = onPreview,
            onReplaceImage = onReplaceImage,
        )

        segment == FormatSegment.Code && primary is CodeElement -> CodeBody(
            primary = primary,
            elements = elements,
            onUpdate = onUpdate,
            version = codeVersion,
            onSelectVersion = onSelectCodeVersion,
            onAddVersion = onAddCodeVersion,
            onRemoveVersion = onRemoveCodeVersion,
            onMoveVersion = onMoveCodeVersion,
        )

        segment == FormatSegment.Terminal && primary is TerminalElement ->
            TerminalBody(primary = primary, elements = elements, onUpdate = onUpdate)

        segment == FormatSegment.Diagram && primary is DiagramElement ->
            SizeOnlyBody(
                value = primary.fontSize,
                enabled = !primary.locked,
                onCommit = { size ->
                    elements.formatDiagrams { it.copy(fontSize = size) }
                        .takeIf { it.isNotEmpty() }
                        ?.let(onUpdate)
                },
            )

        segment == FormatSegment.Equation && primary is EquationElement ->
            SizeOnlyBody(
                value = primary.fontSize,
                enabled = !primary.locked,
                onCommit = { size ->
                    elements.formatEquations { it.copy(fontSize = size) }
                        .takeIf { it.isNotEmpty() }
                        ?.let(onUpdate)
                },
            )

        segment == FormatSegment.Gallery && primary is GalleryElement -> GalleryBody(
            primary = primary,
            elements = elements,
            onUpdate = onUpdate,
            onPreview = onPreview,
            onAddImages = onAddGalleryImages,
            onAddSteps = onAddGallerySteps,
        )

        else -> Unit
    }

    // Only the three kinds that hold one. A group is one object to drag and
    // several to click, so it links nowhere, and neither does a chart or a code
    // block: the section is absent rather than dead.
    if (primary is TextElement || primary is ShapeElement || primary is ImageElement) {
        PanelDivider()

        LinkSection(
            primary = primary,
            ids = elements.map { it.id },
            enabled = !primary.locked,
            slides = slides,
            onSetLinks = onSetLinks,
        )
    }
}

/** The one field a diagram and an equation have between them. */
@Composable
private fun SizeOnlyBody(value: Float, enabled: Boolean, onCommit: (Float) -> Unit) {
    NumberField(
        label = "Size",
        value = value,
        enabled = enabled,
        onCommit = onCommit,
        modifier = Modifier.width(120.dp),
        minimum = 1f,
    )
}

/** The weights the inspector offers, which is the range the canvas can actually draw. */
private val TEXT_WEIGHTS: List<Pair<Int, String>> = listOf(
    300 to "Light",
    400 to "Regular",
    500 to "Medium",
    600 to "Semibold",
    700 to "Bold",
)

/** Keynote's three titles for [ListStyle], which read better in a menu than the enum does. */
private val TextListStyles: List<Pair<ListStyle, String>> = listOf(
    ListStyle.None to "None",
    ListStyle.Bullet to "Bullet",
    ListStyle.Numbered to "Numbered",
)

/** [value] appended under its own name where the list doesn't already carry it. */
private fun List<Pair<Int, String>>.withEntry(value: Int): List<Pair<Int, String>> =
    if (any { (weight, _) -> weight == value }) this else this + (value to "$value")

/**
 * A text box's text: the font, the four toggles, the colour, the alignment, then
 * Spacing and Bullets as the two disclosures Keynote gives them.
 *
 * Every control reads [primary] and writes the whole selection through
 * [formatText], so a mixed selection styles its text boxes and leaves the shapes
 * and images in it alone. Formatting is whole-box, not per-range: the document
 * holds one style per element, so a caret sitting in the text makes no
 * difference to what any of this does.
 *
 * Nothing here previews. Each of these is one settled edit, so each is one
 * history entry and one autosave write, and there is no gesture to stream.
 */
@Composable
private fun TextBody(
    primary: TextElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    expandedSections: Set<InspectorSection>,
    onToggleSection: (InspectorSection) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    // False when the transform changed nothing anywhere: no event, and no field
    // left holding a value the document never took.
    fun format(transform: (TextElement) -> TextElement): Boolean {
        val formatted: List<Element> = elements.formatText(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    DropdownField(
        label = "Font",
        value = primary.fontFamily,
        options = TextFont.entries.map { font -> font to font.name },
        enabled = enabled,
        onPick = { font -> format { it.copy(fontFamily = font) } },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownField(
            label = "Weight",
            // A weight the list has no name for still shows: an imported 450
            // reads as 450 rather than as an empty field.
            value = primary.fontWeight,
            options = TEXT_WEIGHTS.withEntry(primary.fontWeight),
            enabled = enabled,
            onPick = { weight -> format { it.copy(fontWeight = weight) } },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Size",
            value = primary.fontSize,
            enabled = enabled,
            onCommit = { size -> format { it.copy(fontSize = size) } },
            modifier = Modifier.width(104.dp),
            minimum = 1f,
        )
    }

    SegmentedRow {
        StyleSegment("B", primary.isBold, enabled, first = true) { format { it.toggleBold() } }
        StyleSegment("I", primary.italic, enabled, style = FontStyle.Italic) {
            format { it.toggleItalic() }
        }
        StyleSegment("U", primary.underline, enabled, decoration = TextDecoration.Underline) {
            format { it.toggleUnderline() }
        }
        StyleSegment("S", primary.strikethrough, enabled, decoration = TextDecoration.LineThrough) {
            format { it.toggleStrikethrough() }
        }
    }

    SwatchLabel("Color")
    SwatchRow(
        colors = TEXT_SWATCHES,
        selected = primary.color,
        enabled = enabled,
        onPick = { color -> format { it.copy(color = color) } },
    )
    HexField(
        label = "Hex",
        color = primary.color,
        enabled = enabled,
        onCommit = { color -> format { it.copy(color = color) } },
    )

    SegmentedRow {
        for ((index, align) in TextAlign.entries.withIndex()) {
            Segment(
                selected = primary.align == align,
                first = index == 0,
                onClick = if (enabled) ({ format { it.copy(align = align) } }) else null,
            ) {
                AlignGlyph(color = segmentTint(primary.align == align, enabled), variant = index)
            }
        }
    }

    DisclosureSection(
        title = "Spacing",
        expanded = InspectorSection.Spacing in expandedSections,
        onToggle = { onToggleSection(InspectorSection.Spacing) },
        summary = { ValueSummary(primary.lineHeight.asMultiplier()) },
    ) {
        NumberField(
            label = "Line Spacing",
            value = primary.lineHeight,
            enabled = enabled,
            onCommit = { spacing -> format { it.copy(lineHeight = spacing) } },
            modifier = Modifier.width(150.dp),
            minimum = 0.5f,
            fractional = true,
            unit = Units.Times,
            step = 0.1f,
        )
    }

    DisclosureSection(
        title = "Bullets & Lists",
        expanded = InspectorSection.Lists in expandedSections,
        onToggle = { onToggleSection(InspectorSection.Lists) },
        summary = {
            ValueSummary(TextListStyles.first { (style, _) -> style == primary.listStyle }.second)
        },
    ) {
        DropdownField(
            label = "List",
            value = primary.listStyle,
            options = TextListStyles,
            enabled = enabled,
            onPick = { style -> format { it.copy(listStyle = style) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A shape's Text segment: the label it draws over itself, and how big. */
@Composable
private fun ShapeLabelBody(
    primary: ShapeElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    fun format(transform: (ShapeElement) -> ShapeElement): Boolean {
        val formatted: List<Element> = elements.formatShapes(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    // Empty is no label: a shape nobody typed into draws nothing over itself.
    EntryField(
        label = "Label",
        display = primary.label,
        enabled = enabled,
        monospace = false,
    ) { entered ->
        if (entered == primary.label) false else format { it.copy(label = entered) }
    }
    NumberField(
        label = "Label Size",
        value = primary.labelSize,
        enabled = enabled,
        onCommit = { size -> format { it.copy(labelSize = size) } },
        modifier = Modifier.width(150.dp),
        minimum = 1f,
    )
}

/**
 * A code block's content settings: what language it is highlighted as, how big
 * it is set, the two switches about how it wraps, and the versions it morphs
 * between.
 *
 * The code itself isn't edited here. It is typed on the canvas, the way a text
 * box's text is; this is only how the block is read, and which of its versions
 * the canvas is typing into.
 */
@Composable
private fun CodeBody(
    primary: CodeElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    version: Int,
    onSelectVersion: (Int) -> Unit,
    onAddVersion: (String) -> Unit,
    onRemoveVersion: (elementId: String, index: Int) -> Unit,
    onMoveVersion: (elementId: String, from: Int, to: Int) -> Unit,
) {
    val enabled: Boolean = !primary.locked
    // The document's language is free-form and resolved case-insensitively
    // ("kotlin" on disk, "Kotlin" in the menu), so the field shows the catalog's
    // spelling of whatever it holds. One the catalog has no spelling for still
    // shows, appended as itself, rather than leaving the field blank.
    val language: String = CodeLanguages
        .firstOrNull { it.equals(primary.language, ignoreCase = true) }
        ?: primary.language

    fun format(transform: (CodeElement) -> CodeElement): Boolean {
        val formatted: List<Element> = elements.formatCode(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    DropdownField(
        label = "Language",
        value = language,
        options = CodeLanguages.withLanguage(language),
        enabled = enabled,
        onPick = { picked -> format { it.copy(language = picked) } },
    )
    NumberField(
        label = "Size",
        value = primary.fontSize,
        enabled = enabled,
        onCommit = { size -> format { it.copy(fontSize = size) } },
        modifier = Modifier.width(120.dp),
        minimum = 1f,
    )
    AppearanceRow(
        label = "Line Numbers",
        checked = primary.showLineNumbers,
        onToggle = if (!enabled) null else ({ on -> format { it.copy(showLineNumbers = on) } }),
    )
    AppearanceRow(
        label = "Wrap",
        checked = primary.wrap,
        onToggle = if (!enabled) null else ({ on -> format { it.copy(wrap = on) } }),
    )

    // One block only: which version is showing is a fact about this block and
    // about the canvas drawing it, and a picker speaking for several selected
    // blocks at once would have nothing to show for itself.
    if (elements.size != 1) return

    val tokens: ChromeTokens = LocalChromeTokens.current
    val sources: List<String> = primary.sources
    // The state's version is free to be out of range for a moment (an undo that
    // took the last one back), and the list has to point at a row that is there.
    val shown: Int = version.coerceIn(sources.indices)

    PanelDivider()
    SectionLabel("VERSIONS")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in sources.indices) VersionRow(
            label = "Version ${index + 1}",
            selected = index == shown,
            enabled = enabled,
            onClick = { onSelectVersion(index) },
        )
    }

    // Up and down rather than a drag: a version list is short, the order is the
    // order it plays in, and two buttons walk a version along it a click at a
    // time without a gesture to arbitrate against the canvas.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "+",
            enabled = enabled,
            onClick = { onAddVersion(primary.id) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "−",
            enabled = enabled && sources.size > 1,
            onClick = { onRemoveVersion(primary.id, shown) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "▲",
            enabled = enabled && shown > 0,
            onClick = { onMoveVersion(primary.id, shown, shown - 1) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "▼",
            enabled = enabled && shown < sources.size - 1,
            onClick = { onMoveVersion(primary.id, shown, shown + 1) },
            modifier = Modifier.weight(1f),
        )
    }

    Text(
        text = "The canvas edits the shown version. A step morphs into the next.",
        color = tokens.subtle,
        fontSize = 11.5.sp,
    )
}

/**
 * One version in the code block's list: which one it is, and whether it is the
 * one the canvas is showing.
 *
 * Dressed like the build-order rows next door, minus the grab glyph: nothing is
 * dragged here, so the row is a plain target rather than a handle.
 */
@Composable
private fun VersionRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val corner: RoundedCornerShape = RoundedCornerShape(6.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(corner)
            .background(if (selected) tokens.tonal else tokens.rowBg)
            .let { if (selected) it.border(1.dp, tokens.accent, corner) else it }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) tokens.tonalText else tokens.text,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/**
 * A terminal's content settings. The transcript itself is typed on the canvas,
 * the way a code block's code is; the title is the exception among the fields,
 * since it names the one session rather than the look of every selected one.
 */
@Composable
private fun TerminalBody(
    primary: TerminalElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    fun format(transform: (TerminalElement) -> TerminalElement): Boolean {
        val formatted: List<Element> = elements.formatTerminals(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    EntryField(
        label = "Title",
        display = primary.title,
        enabled = enabled,
        monospace = false,
    ) { entered ->
        if (entered == primary.title) false
        else {
            onUpdate(listOf(primary.copy(title = entered)))
            true
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EntryField(
            label = "Prompt",
            display = primary.prompt,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) { entered ->
            if (entered == primary.prompt) false else format { it.copy(prompt = entered) }
        }
        NumberField(
            label = "Size",
            value = primary.fontSize,
            enabled = enabled,
            onCommit = { size -> format { it.copy(fontSize = size) } },
            modifier = Modifier.width(104.dp),
            minimum = 1f,
        )
    }
}

/** The whole picture, in the image-normalised units [ImageMask.frame] is in. */
private val WholeImage: Frame = Frame(0f, 0f, 1f, 1f)

/**
 * What an image can be cut to: the catalog's outlines, plus no mask at all.
 *
 * [ShapeKind.Line] is not offered. It is a stroke between two corners with no
 * inside to show a picture through, which is why the renderer never asks it for
 * an outline either.
 */
private val MASK_CHOICES: List<Pair<ShapeKind?, String>> = listOf(
    null to "None",
    ShapeKind.Rectangle to "Rectangle",
    ShapeKind.Ellipse to "Oval",
    ShapeKind.Triangle to "Triangle",
    ShapeKind.Arrow to "Arrow",
    ShapeKind.Diamond to "Diamond",
    ShapeKind.Star to "Star",
    ShapeKind.Polygon to "Hexagon",
    ShapeKind.QuoteBubble to "Quote Bubble",
    ShapeKind.Callout to "Callout",
)

/** How close a colour has to be to the seed to be rubbed out, to start with. */
private const val DefaultRemovalTolerance: Float = 0.25f

/**
 * An image's own segment: the mask, the three corrections, the caption, and the
 * two verbs that touch the bytes.
 *
 * Reads [primary] and writes the whole selection through [formatImages], like
 * every other kind body. The three adjustment sliders are gestures, so they
 * preview per sample and commit on release, the way opacity does: one drag is
 * one undo entry and one autosave write.
 *
 * The mask window is four percentages rather than a gesture on the canvas: the
 * numbers are exact, they are what the document holds, and a drag over the
 * picture is a different feature to this one. Typing into one while the image
 * has no mask gives it a rectangular one, which is the crop those numbers mean.
 *
 * Background removal is the one control here that rewrites bytes. It runs off
 * the composition, writes a new asset, and points this element at it: the old
 * bytes stay, so undo puts the picture back for free and a second image sharing
 * it is untouched. The seed is the image's top-left pixel, which is the corner a
 * flat background is most likely to own.
 */
@Composable
private fun ImageBody(
    primary: ImageElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
    onReplaceImage: ((ImageElement) -> Unit)?,
) {
    val enabled: Boolean = !primary.locked
    val assets: AssetStore = LocalAssetStore.current
    val scope: CoroutineScope = rememberCoroutineScope()
    val window: Frame = primary.mask?.frame ?: WholeImage
    val adjust: ImageAdjust = primary.adjust
    // View-local: how hard to rub is a setting for the next click rather than
    // anything the document holds. Reset with the element it is aimed at.
    var tolerance: Float by remember(primary.id) { mutableStateOf(DefaultRemovalTolerance) }

    fun format(transform: (ImageElement) -> ImageElement): Boolean {
        val formatted: List<Element> = elements.formatImages(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    fun preview(transform: (ImageElement) -> ImageElement) {
        val formatted: List<Element> = elements.formatImages(transform)
        if (formatted.isNotEmpty()) onPreview(formatted)
    }

    fun withWindow(edit: (Frame) -> Frame): Boolean = format { element ->
        val mask: ImageMask = element.mask ?: ImageMask(ShapeKind.Rectangle, WholeImage)
        element.copy(mask = mask.copy(frame = edit(mask.frame)))
    }

    DropdownField(
        label = "Mask",
        value = primary.mask?.kind,
        options = MASK_CHOICES,
        enabled = enabled,
        onPick = { kind ->
            format { element ->
                when (kind) {
                    null -> element.copy(mask = null)
                    else -> element.copy(
                        mask = element.mask?.copy(kind = kind) ?: ImageMask(kind, WholeImage),
                    )
                }
            }
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "X",
            value = window.x * 100f,
            enabled = enabled,
            onCommit = { x -> withWindow { it.copy(x = x / 100f) } },
            modifier = Modifier.weight(1f),
            unit = Units.Percent,
        )
        NumberField(
            label = "Y",
            value = window.y * 100f,
            enabled = enabled,
            onCommit = { y -> withWindow { it.copy(y = y / 100f) } },
            modifier = Modifier.weight(1f),
            unit = Units.Percent,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "W",
            value = window.width * 100f,
            enabled = enabled,
            onCommit = { width -> withWindow { it.copy(width = width / 100f) } },
            modifier = Modifier.weight(1f),
            minimum = 1f,
            unit = Units.Percent,
        )
        NumberField(
            label = "H",
            value = window.height * 100f,
            enabled = enabled,
            onCommit = { height -> withWindow { it.copy(height = height / 100f) } },
            modifier = Modifier.weight(1f),
            minimum = 1f,
            unit = Units.Percent,
        )
    }

    AdjustSliders(
        adjust = adjust,
        enabled = enabled,
        onDrag = { edit -> preview { it.copy(adjust = edit(it.adjust)) } },
        onRelease = { edit -> format { it.copy(adjust = edit(it.adjust)) } },
        onReset = { format { it.copy(adjust = ImageAdjust()) } },
    )

    EntryField(
        label = "Caption",
        display = primary.caption,
        enabled = enabled,
        monospace = false,
    ) { text ->
        if (text == primary.caption) return@EntryField false
        format { it.copy(caption = text) }
    }

    SwatchLabel("Tolerance")
    SliderRow(
        fraction = tolerance,
        valueLabel = tolerance.asMultiplier(),
        enabled = enabled,
        onDrag = { value -> tolerance = value },
        onRelease = { value -> tolerance = value },
    )
    TonalButton(
        label = "Remove Background",
        enabled = enabled && primary.assetId != null,
        onClick = {
            val assetId: String = primary.assetId ?: return@TonalButton
            scope.launch {
                val cleared: String = assets.removeBackground(assetId, 0, 0, tolerance)
                if (cleared != assetId) onUpdate(listOf(primary.copy(assetId = cleared)))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    TonalButton(
        label = "Replace Image...",
        enabled = enabled && onReplaceImage != null,
        onClick = { onReplaceImage?.invoke(primary) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Exposure, saturation and contrast, plus the button that puts all three back.
 *
 * One block rather than two because an image and a gallery correct the same
 * three numbers the same way; only where they land differs, which is what the
 * two callbacks carry.
 */
@Composable
private fun AdjustSliders(
    adjust: ImageAdjust,
    enabled: Boolean,
    onDrag: ((ImageAdjust) -> ImageAdjust) -> Unit,
    onRelease: ((ImageAdjust) -> ImageAdjust) -> Unit,
    onReset: () -> Unit,
) {
    SwatchLabel("Exposure")
    SliderRow(
        // The slider runs 0..1 over a correction that runs -1..1.
        fraction = (adjust.exposure + 1f) / 2f,
        valueLabel = adjust.exposure.asMultiplier(),
        enabled = enabled,
        onDrag = { value -> onDrag { it.copy(exposure = value * 2f - 1f) } },
        onRelease = { value -> onRelease { it.copy(exposure = value * 2f - 1f) } },
    )
    SwatchLabel("Saturation")
    SliderRow(
        fraction = adjust.saturation / 2f,
        valueLabel = adjust.saturation.asMultiplier(),
        enabled = enabled,
        onDrag = { value -> onDrag { it.copy(saturation = value * 2f) } },
        onRelease = { value -> onRelease { it.copy(saturation = value * 2f) } },
    )
    SwatchLabel("Contrast")
    SliderRow(
        fraction = adjust.contrast / 2f,
        valueLabel = adjust.contrast.asMultiplier(),
        enabled = enabled,
        onDrag = { value -> onDrag { it.copy(contrast = value * 2f) } },
        onRelease = { value -> onRelease { it.copy(contrast = value * 2f) } },
    )
    TonalButton(
        label = "Reset Adjustments",
        enabled = enabled && adjust != ImageAdjust(),
        onClick = onReset,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The side of one thumbnail in the gallery strip. */
private val GalleryThumbSize: Dp = 48.dp

/**
 * A gallery's own segment: the pictures it holds, the one being authored, and
 * what walks it in play.
 *
 * The strip is the segment. A gallery is a list of pictures shown one at a time,
 * so the list is what has to be on screen: clicking a thumbnail says which one is
 * being authored, and the caption field, Remove and the two reorder arrows all
 * act on that one. Everything below the strip is the whole gallery's, so it goes
 * through [formatGalleries] and dresses every gallery in the selection.
 *
 * Which picture is current lives on the element rather than here, so picking one
 * to caption survives a save, a reopen and an undo. That makes it an edit like
 * any other, which is why a click on a thumbnail commits through [onUpdate].
 */
@Composable
private fun GalleryBody(
    primary: GalleryElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
    onAddImages: ((GalleryElement) -> Unit)?,
    onAddSteps: (String) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val enabled: Boolean = !primary.locked
    val images: List<GalleryImage> = primary.images
    val adjust: ImageAdjust = primary.adjust
    // The document's index is free to be out of range (an undo that took the
    // last picture back), and the strip has to point at a real thumbnail.
    val current: Int = primary.current.coerceIn(0, maxOf(0, images.size - 1))

    fun format(transform: (GalleryElement) -> GalleryElement): Boolean {
        val formatted: List<Element> = elements.formatGalleries(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    fun preview(transform: (GalleryElement) -> GalleryElement) {
        val formatted: List<Element> = elements.formatGalleries(transform)
        if (formatted.isNotEmpty()) onPreview(formatted)
    }

    // The picture-by-picture verbs write the primary alone: which image is
    // current and what it says are facts about this gallery, not a look to
    // spread over every gallery that happens to be selected.
    fun edit(transform: (GalleryElement) -> GalleryElement): Boolean {
        if (!enabled) return false
        val edited: GalleryElement = transform(primary)
        if (edited == primary) return false

        onUpdate(listOf(edited))
        return true
    }

    // Swapping with the neighbour rather than lifting and reinserting: the
    // current picture travels with the click, so holding an arrow down walks it
    // along the strip.
    fun move(step: Int): Boolean = edit { gallery ->
        val target: Int = current + step
        if (target !in gallery.images.indices) return@edit gallery

        val moved: MutableList<GalleryImage> = gallery.images.toMutableList()
        moved[current] = moved[target].also { moved[target] = moved[current] }
        gallery.copy(images = moved, current = target)
    }

    if (images.isEmpty()) {
        Text(text = "No images yet.", color = tokens.dim, fontSize = 12.sp)
    } else {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((index, image) in images.withIndex()) GalleryThumbnail(
                image = image,
                selected = index == current,
                enabled = enabled,
                onClick = { edit { it.copy(current = index) } },
            )
        }
        Text(
            text = "Image ${current + 1} of ${images.size}",
            color = tokens.subtle,
            fontSize = 11.5.sp,
        )
    }

    TonalButton(
        label = "Add Images...",
        enabled = enabled && onAddImages != null,
        onClick = { onAddImages?.invoke(primary) },
        modifier = Modifier.fillMaxWidth(),
    )
    // Removing the current one leaves the strip pointing at what slid into its
    // place, and at the new last picture where it was the last one.
    TonalButton(
        label = "Remove",
        enabled = enabled && images.isNotEmpty(),
        onClick = {
            edit { gallery ->
                if (current !in gallery.images.indices) return@edit gallery
                val kept: List<GalleryImage> =
                    gallery.images.filterIndexed { index, _ -> index != current }
                gallery.copy(images = kept, current = current.coerceAtMost(kept.size - 1))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "◀",
            enabled = enabled && current > 0,
            onClick = { move(-1) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "▶",
            enabled = enabled && current < images.size - 1,
            onClick = { move(1) },
            modifier = Modifier.weight(1f),
        )
    }

    // Empty is no caption: a picture nobody typed under draws nothing.
    EntryField(
        label = "Caption",
        display = images.getOrNull(current)?.caption.orEmpty(),
        enabled = enabled && images.isNotEmpty(),
        monospace = false,
    ) { text ->
        edit { gallery ->
            val image: GalleryImage = gallery.images.getOrNull(current) ?: return@edit gallery
            if (text == image.caption) return@edit gallery

            gallery.copy(
                images = gallery.images.toMutableList().also {
                    it[current] = image.copy(caption = text)
                },
            )
        }
    }

    AppearanceRow(
        label = "Show Captions",
        checked = primary.showCaptions,
        onToggle = if (!enabled) null else ({ on -> format { it.copy(showCaptions = on) } }),
    )

    // The corrections are the gallery's, not the picture's: the image body's
    // three sliders over the whole carousel at once.
    AdjustSliders(
        adjust = adjust,
        enabled = enabled,
        onDrag = { edit -> preview { it.copy(adjust = edit(it.adjust)) } },
        onRelease = { edit -> format { it.copy(adjust = edit(it.adjust)) } },
        onReset = { format { it.copy(adjust = ImageAdjust()) } },
    )

    // Live on a locked gallery: the builds belong to the slide's build order
    // rather than to the element, so writing them is not editing it.
    TonalButton(
        label = "Add Slide Steps",
        enabled = images.size > 1,
        onClick = { onAddSteps(primary.id) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(text = "One click per image in play.", color = tokens.subtle, fontSize = 11.5.sp)
}

/**
 * One picture in the strip, cropped square, ringed where it is the current one.
 *
 * The hairline is what keeps a picture with a pale edge off the panel, and the
 * accent ring replaces it on the current one, the way every other swatch in this
 * inspector reads. Undecoded bytes draw as the empty well rather than as a gap,
 * so the strip never changes width under the pointer.
 */
@Composable
private fun GalleryThumbnail(
    image: GalleryImage,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val bitmap: ImageBitmap? = rememberAssetImage(image.assetId)

    Box(
        modifier = Modifier
            .size(GalleryThumbSize)
            .clip(RoundedCornerShape(6.dp))
            .background(tokens.track)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.accent else tokens.outline,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (bitmap != null) Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(GalleryThumbSize).clip(RoundedCornerShape(6.dp)),
        )
    }
}

/**
 * The Arrange segment, top to bottom: z-order as two pairs, the two alignment
 * menus, the geometry, the rotation, then the lock and group pairs.
 *
 * Both z-order flags are the core's, so a selection at the front of the slide
 * greys Front and Forward together and nothing here works out an answer the
 * reducer could disagree with. Align takes one element (the core aligns a lone
 * one to the slide); Distribute wants three, since two have no gap between them
 * to equalize.
 *
 * Lock/Unlock and Group/Ungroup are always both there, the inapplicable half
 * greyed, which is Keynote's shape and what keeps the panel from reflowing under
 * the pointer as a selection changes.
 */
@Composable
private fun ArrangeBody(
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onReorder: (List<String>, ZOrderMove) -> Unit,
    onSetLocked: (List<String>, Boolean) -> Unit,
    onFlip: (List<String>, FlipAxis) -> Unit,
    onGroup: (List<String>) -> Unit,
    onUngroup: (String) -> Unit,
    onAlign: (AlignEdge) -> Unit,
    onDistribute: (Axis) -> Unit,
    canBringForward: Boolean,
    canSendBackward: Boolean,
) {
    val primary: Element = elements.first()
    val ids: List<String> = elements.map { it.id }
    val frame: Frame = primary.frame
    val enabled: Boolean = !primary.locked
    val unlocked: Int = elements.count { !it.locked }

    fun frames(edit: (Frame) -> Frame): List<Element> =
        elements.map { it.update(frame = edit(it.frame)) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "Back",
            enabled = canSendBackward,
            onClick = { onReorder(ids, ZOrderMove.ToBack) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Front",
            enabled = canBringForward,
            onClick = { onReorder(ids, ZOrderMove.ToFront) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "Backward",
            enabled = canSendBackward,
            onClick = { onReorder(ids, ZOrderMove.Backward) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Forward",
            enabled = canBringForward,
            onClick = { onReorder(ids, ZOrderMove.Forward) },
            modifier = Modifier.weight(1f),
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MenuButton(
            label = "Align",
            enabled = unlocked > 0,
            choices = ALIGN_EDGES.map { (edge, title) ->
                MenuChoice(title) { onAlign(edge) }
            },
            modifier = Modifier.weight(1f),
        )
        MenuButton(
            label = "Distribute",
            // Two elements have no gap between them to equalize.
            enabled = unlocked >= 3,
            choices = listOf(
                MenuChoice("Horizontally") { onDistribute(Axis.Horizontal) },
                MenuChoice("Vertically") { onDistribute(Axis.Vertical) },
            ),
            modifier = Modifier.weight(1f),
        )
    }

    PanelDivider()

    SectionLabel("SIZE")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "W",
            value = frame.width,
            enabled = enabled,
            onCommit = { width -> onUpdate(frames { it.copy(width = width) }) },
            modifier = Modifier.weight(1f),
            minimum = 1f,
        )
        NumberField(
            label = "H",
            value = frame.height,
            enabled = enabled,
            onCommit = { height -> onUpdate(frames { it.copy(height = height) }) },
            modifier = Modifier.weight(1f),
            minimum = 1f,
        )
    }

    SectionLabel("POSITION")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "X",
            value = frame.x,
            enabled = enabled,
            onCommit = { x -> onUpdate(frames { it.copy(x = x) }) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Y",
            value = frame.y,
            enabled = enabled,
            onCommit = { y -> onUpdate(frames { it.copy(y = y) }) },
            modifier = Modifier.weight(1f),
        )
    }

    PanelDivider()

    SectionLabel("ROTATE")
    NumberField(
        label = "Angle",
        value = primary.rotation,
        enabled = enabled,
        onCommit = { angle -> onUpdate(elements.map { it.update(rotation = angle) }) },
        modifier = Modifier.width(150.dp),
        unit = Units.Degrees,
    )
    SegmentedRow {
        Segment(
            selected = primary.flippedHorizontally,
            first = true,
            onClick = if (enabled) ({ onFlip(ids, FlipAxis.Horizontal) }) else null,
        ) {
            SegmentLabel("Flip H", selected = primary.flippedHorizontally, enabled = enabled)
        }
        Segment(
            selected = primary.flippedVertically,
            first = false,
            onClick = if (enabled) ({ onFlip(ids, FlipAxis.Vertical) }) else null,
        ) {
            SegmentLabel("Flip V", selected = primary.flippedVertically, enabled = enabled)
        }
    }

    PanelDivider()

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "Lock",
            enabled = unlocked > 0,
            onClick = { onSetLocked(ids, true) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Unlock",
            enabled = elements.any { it.locked },
            onClick = { onSetLocked(ids, false) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "Group",
            enabled = unlocked >= 2,
            onClick = { onGroup(ids) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Ungroup",
            // Ungrouping is a single-group act: two groups selected is a batch
            // nothing else in the app does.
            enabled = primary is GroupElement && elements.size == 1 && enabled,
            onClick = { onUngroup(primary.id) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** The six edges the Align menu offers, in the order Keynote lists them. */
private val ALIGN_EDGES: List<Pair<AlignEdge, String>> = listOf(
    AlignEdge.Left to "Left",
    AlignEdge.CenterX to "Center",
    AlignEdge.Right to "Right",
    AlignEdge.Top to "Top",
    AlignEdge.CenterY to "Middle",
    AlignEdge.Bottom to "Bottom",
)

/**
 * Format with nothing selected: the slide itself, which is what Keynote falls
 * back to and where its layout card lives.
 *
 * In layout mode the same panel is the layout's: its name, the placeholders it
 * offers and the background it hands down. Both moods end on the button that
 * makes the other half of the trip, pinned at the foot.
 */
@Composable
private fun ColumnScope.SlideFormatPanel(
    slide: Slide,
    layouts: List<Slide>,
    isEditingLayouts: Boolean,
    onUpdate: (Slide) -> Unit,
    onApplyLayout: (String, String?) -> Unit,
    onReapplyLayout: (String) -> Unit,
    onEditSlideLayouts: () -> Unit,
    onExitSlideLayouts: () -> Unit,
    onAddPlaceholder: (PlaceholderRole) -> Unit,
    onRenameSlide: (String, String) -> Unit,
) {
    PanelTitle(if (isEditingLayouts) "Layout" else "Slide")
    PanelDivider()

    PanelBody {
        if (isEditingLayouts) {
            // A layout is picked out of a list by its name, so the name is the
            // one thing about it that has to be editable somewhere. This is that
            // somewhere; the navigator's Rename is the same event.
            EntryField(
                label = "Name",
                display = slide.title,
                enabled = true,
                monospace = false,
            ) { entered ->
                val name: String = entered.trim()
                if (name.isEmpty() || name == slide.title) return@EntryField false

                onRenameSlide(slide.id, name)
                return@EntryField true
            }

            PanelDivider()

            SectionLabel("PLACEHOLDERS")
            // Two by two rather than a row of four: "Media" doesn't fit a
            // quarter of 282dp, and a 2x2 block reads as one set of four either
            // way.
            for (pair in PlaceholderRole.entries.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (role in pair) {
                        TonalButton(
                            label = role.name,
                            enabled = true,
                            onClick = { onAddPlaceholder(role) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            SectionLabel("SLIDE LAYOUT")
            val options: List<Pair<String?, String>> =
                listOf<Pair<String?, String>>(null to "None") + layouts.map { it.id to it.title }

            DropdownField(
                label = "Slide Layout",
                value = slide.layoutId,
                options = options,
                enabled = true,
                onPick = { layoutId -> onApplyLayout(slide.id, layoutId) },
                modifier = Modifier.fillMaxWidth(),
            )
            // A slide whose layout is gone reads as being on none, so it has
            // nothing to put back either.
            TonalButton(
                label = "Reapply Layout",
                enabled = layouts.any { it.id == slide.layoutId },
                onClick = { onReapplyLayout(slide.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PanelDivider()

        SectionLabel("APPEARANCE")
        // Still inert. A slide owns the placeholders it was given, so hiding one
        // is deleting it, and nothing in the model says "this slide, without its
        // layout's title" yet.
        AppearanceRow(label = "Title", checked = true)
        AppearanceRow(label = "Body", checked = true)
        AppearanceRow(
            label = "Slide Number",
            checked = slide.showsSlideNumber,
            onToggle = { shows -> onUpdate(slide.copy(showsSlideNumber = shows)) },
        )

        PanelDivider()

        SectionLabel("BACKGROUND")
        BackgroundControls(
            background = slide.background,
            onChange = { background -> onUpdate(slide.copy(background = background)) },
        )
    }

    PanelDivider()

    PinnedBlock {
        TonalButton(
            label = if (isEditingLayouts) "Done" else "Edit Slide Layout",
            enabled = true,
            onClick = if (isEditingLayouts) onExitSlideLayouts else onEditSlideLayouts,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Where the selection points, as Keynote's one menu.
 *
 * The eight rows are the seven [LinkTarget]s plus None, and the destination the
 * two open-ended ones need comes from a second control under the menu rather
 * than from a dialog: a URL is typed, a slide is picked.
 *
 * Shown against the primary and committed over the whole selection, like every
 * other control in the panel. The value is [Element.resolvedLink], so a deck
 * written before targets existed shows its old whole-box URL as the URL it is.
 */
@Composable
private fun LinkSection(
    primary: Element,
    ids: List<String>,
    enabled: Boolean,
    slides: List<Slide>,
    onSetLinks: (List<String>, LinkTarget?) -> Unit,
) {
    val target: LinkTarget? = primary.resolvedLink()

    // The six settled rows commit on the pick, so the two open-ended ones are the
    // only reason this is state at all: "URL..." has to open its field before
    // there is a URL for the document to hold. Keyed on the live target, so an
    // undo or a new selection puts the menu back where the document is.
    var choice: LinkChoice by remember(primary.id, target) { mutableStateOf(target.choice()) }

    SectionLabel("LINK")
    DropdownField(
        label = "Action",
        value = choice,
        options = LINK_CHOICES,
        enabled = enabled,
        onPick = { picked ->
            choice = picked
            when (picked) {
                LinkChoice.None -> onSetLinks(ids, null)
                LinkChoice.Next -> onSetLinks(ids, LinkTarget.Next)
                LinkChoice.Previous -> onSetLinks(ids, LinkTarget.Previous)
                LinkChoice.First -> onSetLinks(ids, LinkTarget.First)
                LinkChoice.Last -> onSetLinks(ids, LinkTarget.Last)
                LinkChoice.ExitShow -> onSetLinks(ids, LinkTarget.ExitShow)
                // These two name no destination yet. Their own field commits.
                LinkChoice.Url, LinkChoice.Slide -> Unit
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    val url: String? = (target as? LinkTarget.Url)?.url

    // Empty is not a link: the field reverts rather than sending a URL that
    // opens nothing.
    if (choice == LinkChoice.Url) EntryField(
        label = "Address",
        display = url.orEmpty(),
        enabled = enabled,
        monospace = false,
    ) { entered ->
        val typed: String = entered.trim()
        if (typed.isEmpty() || typed == url) return@EntryField false

        onSetLinks(ids, LinkTarget.Url(typed))
        return@EntryField true
    }

    // Layouts are not offered: a layout is never played, so a link into one goes
    // nowhere.
    if (choice == LinkChoice.Slide) DropdownField(
        label = "Slide",
        value = (target as? LinkTarget.Slide)?.slideId,
        options = slides.mapIndexed { index, slide -> slide.id to slide.linkTitle(index + 1) },
        enabled = enabled,
        onPick = { id -> if (id != null) onSetLinks(ids, LinkTarget.Slide(id)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Which row of the link menu is live.
 *
 * [LinkTarget] itself won't do as the menu's value: two of its seven carry what
 * they point at, and a row has to mean "a URL" before there is a URL.
 */
private enum class LinkChoice { None, Next, Previous, First, Last, ExitShow, Url, Slide }

private val LINK_CHOICES: List<Pair<LinkChoice, String>> = listOf(
    LinkChoice.None to "None",
    LinkChoice.Next to "Next Slide",
    LinkChoice.Previous to "Previous Slide",
    LinkChoice.First to "First Slide",
    LinkChoice.Last to "Last Slide",
    LinkChoice.ExitShow to "Exit Show",
    LinkChoice.Url to "URL...",
    LinkChoice.Slide to "Slide...",
)

private fun LinkTarget?.choice(): LinkChoice = when (this) {
    null -> LinkChoice.None
    LinkTarget.Next -> LinkChoice.Next
    LinkTarget.Previous -> LinkChoice.Previous
    LinkTarget.First -> LinkChoice.First
    LinkTarget.Last -> LinkChoice.Last
    LinkTarget.ExitShow -> LinkChoice.ExitShow
    is LinkTarget.Url -> LinkChoice.Url
    is LinkTarget.Slide -> LinkChoice.Slide
}

/** A slide in the link picker: its number always, its title when it has one. */
private fun Slide.linkTitle(number: Int): String =
    if (title.isBlank()) "$number" else "$number. $title"

/** Where a shape's gradient runs to when it is switched on and has none yet. */
private const val SHAPE_GRADIENT_END: Long = 0xFF2A2452

/** How many style swatches fit across the 282dp panel, gaps and padding in. */
private const val StylesPerRow: Int = 6

/** The swatch, its corner (the palette's), and the lip of shadow one with a
 * shadow shows under it. */
private val StyleSwatchSize: Dp = 28.dp
private val StyleCorner: Dp = 6.dp
private val StyleShadowHint: Dp = 3.dp

/** As heavy as a swatch draws a border, however heavy the style's own is: past
 * this it is a swatch made of border. */
private val StyleStrokeCap: Dp = 3.dp

/**
 * The deck's saved shape looks, one swatch each, and the button that adds the
 * selected shape to them. Pinned above the Style segment's scroll, the way
 * Keynote pins its style grid.
 *
 * A swatch is the style painted rather than named, since what a style is worth
 * saying is what it looks like. Clicking one dresses the whole selection in it,
 * and the presenter drops whatever in that selection isn't an unlocked shape.
 *
 * Rename and Delete hang off a long press on the swatch: two verbs that belong
 * to one style and nothing else, so they live on the style rather than in a row
 * of buttons that would have to ask which one first.
 *
 * Save Style is live even on a locked shape. Reading a look is not editing the
 * element, which is the rule the event follows too.
 */
@Composable
private fun ObjectStyleStrip(
    styles: List<ObjectStyle>,
    primary: ShapeElement,
    ids: List<String>,
    enabled: Boolean,
    onApply: (ids: List<String>, styleId: String) -> Unit,
    onSave: (shapeId: String, name: String) -> Unit,
    onRename: (styleId: String, name: String) -> Unit,
    onDelete: (styleId: String) -> Unit,
) {
    // View-local, like the theme card's: a name reaches the loop on OK and
    // never before.
    var saving: Boolean by remember { mutableStateOf(false) }
    var renaming: ObjectStyle? by remember { mutableStateOf(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in styles.chunked(StylesPerRow)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (style in row) ObjectStyleSwatch(
                    style = style,
                    // The look the shape is already wearing, which is the one
                    // click that would change nothing: exactly what a ring says.
                    selected = primary.applyingObjectStyle(style) == primary,
                    enabled = enabled,
                    onApply = { onApply(ids, style.id) },
                    onRename = { renaming = style },
                    onDelete = { onDelete(style.id) },
                )
            }
        }
    }

    TonalButton(
        label = "Save Style...",
        enabled = true,
        onClick = { saving = true },
        modifier = Modifier.fillMaxWidth(),
    )

    // Unprefilled: a saved style is always a new entry, so there is no name to
    // start from the way Save Theme has the one the deck is on.
    if (saving) NameDialog(
        title = "Save Style",
        confirmLabel = "Save",
        name = "",
        onDismiss = { saving = false },
        onCommit = { name ->
            saving = false
            onSave(primary.id, name)
        },
    )

    renaming?.let { style ->
        NameDialog(
            title = "Rename Style",
            confirmLabel = "Rename",
            name = style.name,
            onDismiss = { renaming = null },
            onCommit = { name ->
                renaming = null
                onRename(style.id, name)
            },
        )
    }
}

/**
 * One style, painted: its fill or its gradient in a rounded rect, its border
 * traced inside that, and a lip of its shadow under it where it has one.
 *
 * The hairline is what keeps a transparent style off a dark panel, and the
 * accent ring replaces it on the one the shape is wearing, which is how every
 * other swatch in this panel reads.
 */
@Composable
private fun ObjectStyleSwatch(
    style: ObjectStyle,
    selected: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    var menuOpen: Boolean by remember { mutableStateOf(false) }

    Box {
        Canvas(
            modifier = Modifier
                .size(StyleSwatchSize)
                .pointerInput(style.id, enabled) {
                    detectTapGestures(
                        onLongPress = { menuOpen = true },
                        onTap = { if (enabled) onApply() },
                    )
                },
        ) {
            val hint: Float = if (style.shadow == null) 0f else StyleShadowHint.toPx()
            val body: Size = Size(size.width, size.height - hint)
            val corner: CornerRadius = CornerRadius(StyleCorner.toPx())

            style.shadow?.let { shadow ->
                drawRoundRect(
                    color = Color(shadow.color),
                    topLeft = Offset(hint, hint),
                    size = Size(body.width - hint * 2, body.height),
                    cornerRadius = corner,
                )
            }

            drawRoundRect(brush = style.brush(body), size = body, cornerRadius = corner)

            val stroke: Float = minOf(style.strokeWidth.dp, StyleStrokeCap).toPx()
            if (stroke > 0f) drawRoundRect(
                color = Color(style.strokeColor),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(body.width - stroke, body.height - stroke),
                cornerRadius = corner,
                style = Stroke(stroke),
            )

            val ring: Float = if (selected) 2.dp.toPx() else 1.dp.toPx()
            drawRoundRect(
                color = if (selected) tokens.accent else tokens.outline,
                topLeft = Offset(ring / 2f, ring / 2f),
                size = Size(body.width - ring, body.height - ring),
                cornerRadius = corner,
                style = Stroke(ring),
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Rename...", fontSize = 13.sp) },
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", fontSize = 13.sp) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** What a style paints its swatch with: its gradient over [size] when it has
 * one, its solid fill otherwise. The angle is the canvas's, so a swatch leans
 * the way the shape will. */
private fun ObjectStyle.brush(size: Size): Brush {
    val gradient: ShapeGradient = gradient ?: return SolidColor(Color(fill))

    return Brush.linearGradient(
        colors = listOf(Color(gradient.start), Color(gradient.end)),
        start = gradientStop(size, gradient.angle, -1f),
        end = gradientStop(size, gradient.angle, 1f),
    )
}

/**
 * [transform] over the selection's shapes, the way `formatText` does its text:
 * locked elements and everything that isn't a [ShapeElement] drop out, and so
 * does any shape the transform left alone. An empty result is an edit that
 * changed nothing, which is no event and no history entry.
 *
 * Here rather than in the document module because the inspector is the only
 * caller: no menu verb formats a shape yet. The five below it are the same
 * filter over the four other kinds that only this panel dresses.
 */
private fun List<Element>.formatShapes(
    transform: (ShapeElement) -> ShapeElement,
): List<Element> = mapNotNull { element ->
    if (element !is ShapeElement || element.locked) return@mapNotNull null
    val formatted: ShapeElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

private fun List<Element>.formatTerminals(
    transform: (TerminalElement) -> TerminalElement,
): List<Element> = mapNotNull { element ->
    if (element !is TerminalElement || element.locked) return@mapNotNull null
    val formatted: TerminalElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

private fun List<Element>.formatDiagrams(
    transform: (DiagramElement) -> DiagramElement,
): List<Element> = mapNotNull { element ->
    if (element !is DiagramElement || element.locked) return@mapNotNull null
    val formatted: DiagramElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

private fun List<Element>.formatEquations(
    transform: (EquationElement) -> EquationElement,
): List<Element> = mapNotNull { element ->
    if (element !is EquationElement || element.locked) return@mapNotNull null
    val formatted: EquationElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

private fun List<Element>.formatImages(
    transform: (ImageElement) -> ImageElement,
): List<Element> = mapNotNull { element ->
    if (element !is ImageElement || element.locked) return@mapNotNull null
    val formatted: ImageElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

private fun List<Element>.formatGalleries(
    transform: (GalleryElement) -> GalleryElement,
): List<Element> = mapNotNull { element ->
    if (element !is GalleryElement || element.locked) return@mapNotNull null
    val formatted: GalleryElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

/** [language] appended under its own name where the catalog doesn't carry it. */
private fun List<String>.withLanguage(language: String): List<Pair<String, String>> =
    (if (contains(language)) this else this + language).map { it to it }
