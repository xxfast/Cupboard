package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.canvas.gradientStop
import io.github.xxfast.cupboard.document.CodeElement
import io.github.xxfast.cupboard.document.CodeLanguages
import io.github.xxfast.cupboard.document.CodeTheme
import io.github.xxfast.cupboard.document.DiagramElement
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.EquationElement
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ListStyle
import io.github.xxfast.cupboard.document.ObjectStyle
import io.github.xxfast.cupboard.document.PlaceholderRole
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
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.TransitionDirection
import io.github.xxfast.cupboard.document.TransitionKind
import io.github.xxfast.cupboard.document.TransitionTrigger
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.document.applyingObjectStyle
import io.github.xxfast.cupboard.document.formatCode
import io.github.xxfast.cupboard.document.formatText
import io.github.xxfast.cupboard.document.isBold
import io.github.xxfast.cupboard.document.toggleBold
import io.github.xxfast.cupboard.document.toggleItalic
import io.github.xxfast.cupboard.document.toggleStrikethrough
import io.github.xxfast.cupboard.document.toggleUnderline
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlin.math.roundToInt

/**
 * The 282dp M3 inspector: Format / Animate / Slide tabs over their bodies.
 * Clicking the active tab does nothing (close-on-reclick is macOS-only).
 *
 * Format is live whenever [selectedElements] isn't empty: the text styling where
 * the primary element is a text box, the shape styling where it is a shape, the
 * code styling where it is a code block, then the geometry, rotation, opacity,
 * z-order and lock of the selection. With nothing selected it falls back to the
 * design's text mock. Animate edits the slide's transition and mocks its builds,
 * and the Slide tab's layout card is a mock; the rest of that tab edits the slide.
 *
 * The "Slide" tab is [InspectorTab.Document]: same pane, per-platform label.
 */
@Composable
fun EditorInspector(
    tab: InspectorTab,
    onSelectTab: (InspectorTab) -> Unit,
    /** The selected slide, which the Slide tab edits. */
    slide: Slide,
    onUpdateSlide: (Slide) -> Unit,
    /** An in-flight sample of [slide], for the Animate tab's duration drag. */
    onPreviewSlide: (Slide) -> Unit,
    /** What [slide] plays on its way out, null putting it back on the deck's own. */
    onSetSlideTransition: (slideId: String, transition: SlideTransition?) -> Unit,
    /** The deck's layouts: what the Slide tab's picker offers. */
    layouts: List<Slide>,
    /**
     * Whether [slide] is one of [layouts], being edited. The Slide tab is then a
     * layout editor: it names the layout and fills it with placeholders instead
     * of putting a slide on one.
     */
    isEditingLayouts: Boolean,
    onApplyLayout: (slideId: String, layoutId: String?) -> Unit,
    onReapplyLayout: (slideId: String) -> Unit,
    onEditSlideLayouts: () -> Unit,
    onExitSlideLayouts: () -> Unit,
    onAddPlaceholder: (PlaceholderRole) -> Unit,
    onRenameSlide: (id: String, title: String) -> Unit,
    /** Every theme the deck can be put on: the built-ins, then the user's. */
    themes: List<Theme>,
    /** The ones of [themes] the user saved, and so the only ones deletable. */
    userThemes: List<Theme>,
    /** The theme the deck is on, by name. */
    themeName: String,
    /** Behind every slide that asks for none of its own; null is the app's dark gradient. */
    documentBackground: SlideBackground?,
    /** The deck's slide shape, in document units, and the preset it is exactly,
     * null for a custom one: between them, what the size picker shows. */
    slideWidth: Float,
    slideHeight: Float,
    slideSizePreset: SlideSizePreset?,
    onChangeTheme: (name: String) -> Unit,
    onSaveAsTheme: (name: String) -> Unit,
    onDeleteUserTheme: (name: String) -> Unit,
    onSetDocumentBackground: (SlideBackground?) -> Unit,
    onSetSlideSize: (width: Float, height: Float, scaleContent: Boolean) -> Unit,
    /** The deck's saved shape looks: what the shape section's style strip offers. */
    objectStyles: List<ObjectStyle>,
    onApplyObjectStyle: (ids: List<String>, styleId: String) -> Unit,
    onSaveObjectStyle: (shapeId: String, name: String) -> Unit,
    onRenameObjectStyle: (styleId: String, name: String) -> Unit,
    onDeleteObjectStyle: (styleId: String) -> Unit,
    selectedElements: List<Element>,
    onUpdateElements: (List<Element>) -> Unit,
    onPreviewElements: (List<Element>) -> Unit,
    onReorderElements: (List<String>, ZOrderMove) -> Unit,
    onSetElementsLocked: (List<String>, Boolean) -> Unit,
    onFlipElements: (List<String>, FlipAxis) -> Unit,
    onGroupElements: (List<String>) -> Unit,
    onUngroupElements: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val tabs: List<Pair<String, InspectorTab>> = listOf(
        "Format" to InspectorTab.Format,
        "Animate" to InspectorTab.Animate,
        "Slide" to InspectorTab.Document,
    )
    val selectedIndex: Int = tabs.indexOfFirst { (_, target) -> target == tab }

    Column(
        modifier = modifier
            .width(282.dp)
            .fillMaxHeight()
            .background(tokens.insBg),
    ) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = tokens.insBg,
            contentColor = tokens.text,
            indicator = { tabPositions -> TabIndicator(tabPositions[selectedIndex]) },
            divider = { HorizontalDivider(thickness = 1.dp, color = tokens.div) },
        ) {
            tabs.forEachIndexed { index, (title, target) ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { if (index != selectedIndex) onSelectTab(target) },
                    text = { Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = tokens.text,
                    unselectedContentColor = tokens.dim,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                InspectorTab.Format -> if (selectedElements.isEmpty()) TextFormatPanel()
                else {
                    // The text section sits above the geometry, the way the
                    // design has it, and only where there is text to style.
                    val primary: Element = selectedElements.first()
                    if (primary is TextElement) TextSection(
                        primary = primary,
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                    )

                    if (primary is ShapeElement) ShapeSection(
                        primary = primary,
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                        styles = objectStyles,
                        onApplyStyle = onApplyObjectStyle,
                        onSaveStyle = onSaveObjectStyle,
                        onRenameStyle = onRenameObjectStyle,
                        onDeleteStyle = onDeleteObjectStyle,
                    )

                    if (primary is CodeElement) CodeSection(
                        primary = primary,
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                    )

                    if (primary is TerminalElement) TerminalSection(
                        primary = primary,
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                    )

                    if (primary is DiagramElement) DiagramSection(
                        primary = primary,
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                    )

                    if (primary is EquationElement) EquationSection(
                        primary = primary,
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                    )

                    ElementFormatPanel(
                        elements = selectedElements,
                        onUpdate = onUpdateElements,
                        onPreview = onPreviewElements,
                        onReorder = onReorderElements,
                        onSetLocked = onSetElementsLocked,
                        onFlip = onFlipElements,
                        onGroup = onGroupElements,
                        onUngroup = onUngroupElements,
                    )
                }

                InspectorTab.Animate -> AnimatePanel(
                    slide = slide,
                    isEditingLayouts = isEditingLayouts,
                    hasSelection = selectedElements.isNotEmpty(),
                    onSetTransition = onSetSlideTransition,
                    onPreview = onPreviewSlide,
                    onUpdate = onUpdateSlide,
                )
                InspectorTab.Document -> SlidePanel(
                    slide = slide,
                    layouts = layouts,
                    isEditingLayouts = isEditingLayouts,
                    themes = themes,
                    userThemes = userThemes,
                    themeName = themeName,
                    documentBackground = documentBackground,
                    slideWidth = slideWidth,
                    slideHeight = slideHeight,
                    slideSizePreset = slideSizePreset,
                    onChangeTheme = onChangeTheme,
                    onSaveAsTheme = onSaveAsTheme,
                    onDeleteUserTheme = onDeleteUserTheme,
                    onSetDocumentBackground = onSetDocumentBackground,
                    onSetSlideSize = onSetSlideSize,
                    onUpdate = onUpdateSlide,
                    onApplyLayout = onApplyLayout,
                    onReapplyLayout = onReapplyLayout,
                    onEditSlideLayouts = onEditSlideLayouts,
                    onExitSlideLayouts = onExitSlideLayouts,
                    onAddPlaceholder = onAddPlaceholder,
                    onRenameSlide = onRenameSlide,
                )
            }
        }
    }
}

/** The design's 52x3 rounded indicator, centered under the active tab. */
@Composable
private fun TabIndicator(position: TabPosition) {
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = position.left + (position.width - 52.dp) / 2)
            .size(width = 52.dp, height = 3.dp)
            .background(
                color = LocalChromeTokens.current.accent,
                shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
            ),
    )
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(thickness = 1.dp, color = LocalChromeTokens.current.div)
}

/** The design's text mock, shown while nothing is selected. Still a placeholder. */
@Composable
private fun TextFormatPanel() {
    val tokens: ChromeTokens = LocalChromeTokens.current

    SectionLabel("TEXT")
    OutlinedField(label = "Font", value = "Ubuntu", trailing = "▾")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedField(label = "Weight", value = "Bold", trailing = "▾", modifier = Modifier.weight(1f))
        OutlinedField(label = "Size", value = "46", modifier = Modifier.width(74.dp))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedRow(modifier = Modifier.weight(1f)) {
            Segment(selected = true, first = true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("✓", color = tokens.segOnText, fontSize = 11.sp)
                    Text("B", color = tokens.segOnText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Segment(selected = false, first = false) {
                Text("I", color = tokens.segOff, fontSize = 13.sp, fontStyle = FontStyle.Italic)
            }
            Segment(selected = false, first = false) {
                Text("U", color = tokens.segOff, fontSize = 13.sp, textDecoration = TextDecoration.Underline)
            }
        }
        // Text color well.
        Box(
            modifier = Modifier
                .size(38.dp)
                .border(1.dp, tokens.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
        }
    }
    SegmentedRow {
        Segment(selected = true, first = true) { AlignGlyph(color = tokens.segOnText, variant = 0) }
        Segment(selected = false, first = false) { AlignGlyph(color = tokens.segOff, variant = 1) }
        Segment(selected = false, first = false) { AlignGlyph(color = tokens.segOff, variant = 2) }
        Segment(selected = false, first = false) { AlignGlyph(color = tokens.segOff, variant = 3) }
    }

    PanelDivider()

    SectionLabel("POSITION & SIZE")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedField(label = "X", value = "72", monospace = true, height = 40.dp, modifier = Modifier.weight(1f))
        OutlinedField(label = "Y", value = "64", monospace = true, height = 40.dp, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedField(label = "W", value = "800", monospace = true, height = 40.dp, modifier = Modifier.weight(1f))
        OutlinedField(label = "H", value = "118", monospace = true, height = 40.dp, modifier = Modifier.weight(1f))
    }

    PanelDivider()

    SectionLabel("OPACITY")
    SliderRow(fraction = 1f, valueLabel = "100%")
}

/** The weights the inspector offers, which is the range the canvas can actually draw. */
private val TEXT_WEIGHTS: List<Pair<Int, String>> = listOf(
    300 to "Light",
    400 to "Regular",
    500 to "Medium",
    600 to "Semibold",
    700 to "Bold",
)

/**
 * The text palette: the slide's own white, the deck's lilacs, its two ambers,
 * and black for a slide that has gone inverted. A fixed set like the background
 * grid, with the hex field underneath for anything outside it.
 */
private val TEXT_SWATCHES: List<Long> = listOf(
    0xFFFFFFFF, 0xFFA9A0D8, 0xFFD9CFFF, 0xFFFFE28A, 0xFFA98FFF, 0xFFF5C518, 0xFF000000,
)

/**
 * The live TEXT section, shown when the primary element is a text box.
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
private fun TextSection(
    primary: TextElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
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

    SectionLabel("TEXT")
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
            modifier = Modifier.width(74.dp),
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
                AlignGlyph(
                    color = segmentTint(primary.align == align, enabled),
                    variant = index,
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(
            label = "Line Spacing",
            value = primary.lineHeight,
            enabled = enabled,
            onCommit = { spacing -> format { it.copy(lineHeight = spacing) } },
            modifier = Modifier.width(110.dp),
            minimum = 0.5f,
            fractional = true,
        )
        DropdownField(
            label = "List",
            value = primary.listStyle,
            options = TextListStyles,
            enabled = enabled,
            onPick = { style -> format { it.copy(listStyle = style) } },
            modifier = Modifier.weight(1f),
        )
    }

    // Empty clears it: a link nobody typed is no link, not an empty one.
    EntryField(
        label = "Link",
        display = primary.link.orEmpty(),
        enabled = enabled,
        monospace = false,
    ) { entered ->
        val link: String? = entered.trim().takeIf { it.isNotEmpty() }
        if (link == primary.link) false else format { it.copy(link = link) }
    }

    PanelDivider()
}

/** Keynote's three titles for [ListStyle], which read better in a menu than the enum does. */
private val TextListStyles: List<Pair<ListStyle, String>> = listOf(
    ListStyle.None to "None",
    ListStyle.Bullet to "Bullet",
    ListStyle.Numbered to "Numbered",
)

/** [value] appended under its own name where the list doesn't already carry it. */
private fun List<Pair<Int, String>>.withEntry(value: Int): List<Pair<Int, String>> =
    if (any { (weight, _) -> weight == value }) this else this + (value to "$value")

/** One of the B / I / U / S toggles, drawn in the style it turns on. */
@Composable
private fun RowScope.StyleSegment(
    glyph: String,
    selected: Boolean,
    enabled: Boolean,
    first: Boolean = false,
    style: FontStyle = FontStyle.Normal,
    decoration: TextDecoration = TextDecoration.None,
    onClick: () -> Unit,
) {
    Segment(selected = selected, first = first, onClick = if (enabled) onClick else null) {
        Text(
            text = glyph,
            color = segmentTint(selected, enabled),
            fontSize = 13.sp,
            fontWeight = if (glyph == "B") FontWeight.Bold else FontWeight.Medium,
            fontStyle = style,
            textDecoration = decoration,
        )
    }
}

/** What a segment's content is painted, for its on/off and enabled/disabled corners. */
@Composable
private fun segmentTint(selected: Boolean, enabled: Boolean): Color {
    val tokens: ChromeTokens = LocalChromeTokens.current
    return when {
        !enabled -> tokens.faint
        selected -> tokens.segOnText
        else -> tokens.segOff
    }
}

/**
 * [OutlinedField]'s look as a menu button: the current option in the field, the
 * whole list under it, the live one ticked.
 *
 * [display] overrides what the field reads, for the one picker whose value says
 * more than the option it sits on: a custom slide size is picked as "Custom..."
 * but shown as the size it actually is.
 */
@Composable
private fun <T> DropdownField(
    label: String,
    value: T,
    options: List<Pair<T, String>>,
    enabled: Boolean,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    display: String? = null,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    var open: Boolean by remember { mutableStateOf(false) }

    Box(modifier) {
        OutlinedField(
            label = label,
            value = display ?: options.firstOrNull { (option, _) -> option == value }?.second.orEmpty(),
            trailing = "▾",
            enabled = enabled,
            modifier = Modifier.clickable(enabled = enabled) { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((option, title) in options) {
                DropdownMenuItem(
                    text = { Text(title, fontSize = 13.sp) },
                    leadingIcon = {
                        Box(Modifier.width(14.dp)) {
                            if (option == value) {
                                Text("✓", color = tokens.accent, fontSize = 12.sp)
                            }
                        }
                    },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                )
            }
        }
    }
}

/**
 * [transform] over the selection's shapes, the way `formatText` does its text:
 * locked elements and everything that isn't a [ShapeElement] drop out, and so
 * does any shape the transform left alone. An empty result is an edit that
 * changed nothing, which is no event and no history entry.
 *
 * Here rather than in the document module because the inspector is the only
 * caller: no menu verb formats a shape yet.
 */
private fun List<Element>.formatShapes(
    transform: (ShapeElement) -> ShapeElement,
): List<Element> = mapNotNull { element ->
    if (element !is ShapeElement || element.locked) return@mapNotNull null
    val formatted: ShapeElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

/**
 * The live SHAPE section, shown when the primary element is a shape.
 *
 * Reads [primary] and writes the whole selection through [formatShapes], like
 * the text section: a mixed selection styles its shapes and leaves the rest
 * alone. Every control is discrete, so none of them preview: there is no
 * gesture here, only committed values.
 *
 * What shows follows the kind. A corner radius means nothing to an oval, an
 * arrowhead nothing to a rectangle, and a line has no inside to write a label
 * in, so each of those appears only where it does something.
 *
 * The deck's saved looks sit at the top, above the controls they are made of:
 * a style is the whole of this section in one click, so it comes before the
 * long way round rather than after it.
 */
@Composable
private fun ShapeSection(
    primary: ShapeElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    styles: List<ObjectStyle>,
    onApplyStyle: (ids: List<String>, styleId: String) -> Unit,
    onSaveStyle: (shapeId: String, name: String) -> Unit,
    onRenameStyle: (styleId: String, name: String) -> Unit,
    onDeleteStyle: (styleId: String) -> Unit,
) {
    val enabled: Boolean = !primary.locked
    val gradient: ShapeGradient? = primary.gradient
    val shadow: ShapeShadow? = primary.shadow

    ObjectStyleSection(
        styles = styles,
        primary = primary,
        ids = elements.map { it.id },
        enabled = enabled,
        onApply = onApplyStyle,
        onSave = onSaveStyle,
        onRename = onRenameStyle,
        onDelete = onDeleteStyle,
    )

    // False when the transform changed nothing anywhere, so a field that typed
    // its way to the value the document already holds puts itself back.
    fun format(transform: (ShapeElement) -> ShapeElement): Boolean {
        val formatted: List<Element> = elements.formatShapes(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    SectionLabel("SHAPE")
    SegmentedRow {
        Segment(
            selected = gradient == null,
            first = true,
            // Dropping the gradient is all it takes to go back to the solid:
            // the fill was never overwritten, so it is still the one to return to.
            onClick = if (enabled) ({ format { it.copy(gradient = null) } }) else null,
        ) {
            SegmentLabel("Color", selected = gradient == null, enabled = enabled)
        }
        Segment(
            selected = gradient != null,
            first = false,
            // A shape switched into a gradient has none yet, so it starts from
            // its own fill and runs to the palette's deep indigo.
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
        SwatchLabel("Fill")
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
            modifier = Modifier.width(96.dp),
        )
    }

    SwatchLabel("Border")
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
            modifier = Modifier.width(74.dp),
            minimum = 0f,
            fractional = true,
        )
    }

    // Off is no shadow at all rather than a transparent one, so switching it
    // back on lands on the document's own default every time.
    AppearanceRow(
        label = "Shadow",
        checked = shadow != null,
        onToggle = if (!enabled) null else ({ on ->
            format { it.copy(shadow = if (on) ShapeShadow() else null) }
        }),
    )
    if (shadow != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HexField(
                label = "Shadow",
                color = shadow.color,
                enabled = enabled,
                onCommit = { color ->
                    format { it.copy(shadow = it.shadow?.copy(color = color)) }
                },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Blur",
                value = shadow.blur,
                enabled = enabled,
                onCommit = { blur -> format { it.copy(shadow = it.shadow?.copy(blur = blur)) } },
                modifier = Modifier.width(74.dp),
                minimum = 0f,
            )
        }
    }

    if (primary.kind == ShapeKind.Rectangle) NumberField(
        label = "Corner Radius",
        value = primary.cornerRadius,
        enabled = enabled,
        onCommit = { radius -> format { it.copy(cornerRadius = radius) } },
        modifier = Modifier.width(130.dp),
        minimum = 0f,
    )

    if (primary.kind == ShapeKind.Line) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppearanceRow(
                label = "Start Arrow",
                checked = primary.startArrow,
                onToggle = if (!enabled) null else ({ on ->
                    format { it.copy(startArrow = on) }
                }),
            )
            AppearanceRow(
                label = "End Arrow",
                checked = primary.endArrow,
                onToggle = if (!enabled) null else ({ on -> format { it.copy(endArrow = on) } }),
            )
        }
    } else {
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
            modifier = Modifier.width(110.dp),
            minimum = 1f,
        )
    }

    PanelDivider()
}

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
 * The STYLES block: the deck's saved shape looks, one swatch each, and the
 * button that adds the selected shape to them.
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
private fun ObjectStyleSection(
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

    SectionLabel("STYLES")

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

    PanelDivider()
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
 * The live CODE section, shown when the primary element is a code block.
 *
 * Reads [primary] and writes the whole selection through `formatCode`, like the
 * text section: a mixed selection styles its code blocks and leaves the rest
 * alone. Nothing here previews, since every control is one settled edit.
 *
 * The code itself isn't edited here. It is typed on the canvas, the way a text
 * box's text is; this section is only how the block is dressed.
 */
@Composable
private fun CodeSection(
    primary: CodeElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked
    // The document's language is free-form and resolved case-insensitively
    // ("kotlin" on disk, "Kotlin" in the menu), so the field shows the catalog's
    // spelling of whatever it holds. One the catalog has no spelling for still
    // shows, appended as itself, rather than leaving the field blank.
    val language: String = CodeLanguages
        .firstOrNull { it.equals(primary.language, ignoreCase = true) }
        ?: primary.language

    // False when the transform changed nothing anywhere: no event, and no field
    // left holding a value the document never took.
    fun format(transform: (CodeElement) -> CodeElement): Boolean {
        val formatted: List<Element> = elements.formatCode(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    SectionLabel("CODE")
    DropdownField(
        label = "Language",
        value = language,
        options = CodeLanguages.withLanguage(language),
        enabled = enabled,
        onPick = { picked -> format { it.copy(language = picked) } },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DropdownField(
            label = "Theme",
            value = primary.theme,
            options = CodeTheme.entries.map { theme -> theme to theme.name },
            enabled = enabled,
            onPick = { theme -> format { it.copy(theme = theme) } },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Size",
            value = primary.fontSize,
            enabled = enabled,
            onCommit = { size -> format { it.copy(fontSize = size) } },
            modifier = Modifier.width(74.dp),
            minimum = 1f,
        )
    }

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

    PanelDivider()
}

/**
 * [transform] applied to every unlocked terminal in the selection, as the ones
 * that actually changed.
 *
 * Here rather than in the document module for [List.formatShapes]' reason: the
 * inspector is the only caller, since no menu verb formats a terminal.
 */
private fun List<Element>.formatTerminals(
    transform: (TerminalElement) -> TerminalElement,
): List<Element> = mapNotNull { element ->
    if (element !is TerminalElement || element.locked) return@mapNotNull null
    val formatted: TerminalElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

/**
 * The live TERMINAL section, shown when the primary element is a terminal.
 *
 * Reads [primary] and writes the whole selection through [formatTerminals], like
 * the code section: a mixed selection dresses its terminals and leaves the rest
 * alone. Nothing here previews, since every control is one settled edit.
 *
 * The transcript itself isn't edited here. It is typed on the canvas, the way a
 * code block's code is. The title is the exception among the fields: it names
 * the one session rather than the look of every selected one, so it is written
 * to [primary] alone.
 */
@Composable
private fun TerminalSection(
    primary: TerminalElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    // False when the transform changed nothing anywhere: no event, and no field
    // left holding a value the document never took.
    fun format(transform: (TerminalElement) -> TerminalElement): Boolean {
        val formatted: List<Element> = elements.formatTerminals(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    SectionLabel("TERMINAL")
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
            modifier = Modifier.width(74.dp),
            minimum = 1f,
        )
    }

    AppearanceRow(
        label = "Show Title Bar",
        checked = primary.showTitleBar,
        onToggle = if (!enabled) null else ({ on -> format { it.copy(showTitleBar = on) } }),
    )

    PanelDivider()
}

/**
 * [transform] applied to every unlocked diagram in the selection, as the ones
 * that actually changed.
 *
 * Here rather than in the document module for [formatTerminals]' reason: the
 * inspector is the only caller, since no menu verb formats a diagram.
 */
private fun List<Element>.formatDiagrams(
    transform: (DiagramElement) -> DiagramElement,
): List<Element> = mapNotNull { element ->
    if (element !is DiagramElement || element.locked) return@mapNotNull null
    val formatted: DiagramElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

/**
 * The live DIAGRAM section, shown when the primary element is a diagram.
 *
 * Reads [primary] and writes the whole selection through [formatDiagrams], like
 * the terminal section: a mixed selection dresses its diagrams and leaves the
 * rest alone. Nothing here previews, since every control is one settled edit.
 *
 * The source itself isn't edited here. It is typed on the canvas over the chart
 * it draws, the way a code block's code is. What is left is the palette, which
 * is four colours because that is every part a chart is made of: the inside of a
 * node, its outline, its label, and the arrows between them. They are typed as
 * hex rather than picked off the text swatches, which are a text palette and
 * would say nothing useful about a node fill that is deliberately translucent.
 */
@Composable
private fun DiagramSection(
    primary: DiagramElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    // False when the transform changed nothing anywhere: no event, and no field
    // left holding a value the document never took.
    fun format(transform: (DiagramElement) -> DiagramElement): Boolean {
        val formatted: List<Element> = elements.formatDiagrams(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    SectionLabel("DIAGRAM")
    NumberField(
        label = "Size",
        value = primary.fontSize,
        enabled = enabled,
        onCommit = { size -> format { it.copy(fontSize = size) } },
        modifier = Modifier.width(96.dp),
        minimum = 1f,
    )
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

    PanelDivider()
}

/**
 * [transform] applied to every unlocked equation in the selection, as the ones
 * that actually changed.
 *
 * Here rather than in the document module for [formatDiagrams]' reason: the
 * inspector is the only caller, since no menu verb formats an equation.
 */
private fun List<Element>.formatEquations(
    transform: (EquationElement) -> EquationElement,
): List<Element> = mapNotNull { element ->
    if (element !is EquationElement || element.locked) return@mapNotNull null
    val formatted: EquationElement = transform(element)
    return@mapNotNull if (formatted == element) null else formatted
}

/**
 * The live EQUATION section, shown when the primary element is an equation.
 *
 * Reads [primary] and writes the whole selection through [formatEquations], like
 * the diagram section: a mixed selection dresses its equations and leaves the
 * rest alone. Nothing here previews, since every control is one settled edit.
 *
 * The LaTeX itself isn't edited here. It is typed on the canvas over the math it
 * sets, the way a diagram's source is. What is left is the size and the one
 * colour, and that colour gets the text swatches above its hex field: an
 * equation is ink on the slide, set in the same serif at the same sizes as a
 * heading, so the palette that dresses text is the palette that dresses it.
 */
@Composable
private fun EquationSection(
    primary: EquationElement,
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
) {
    val enabled: Boolean = !primary.locked

    // False when the transform changed nothing anywhere: no event, and no field
    // left holding a value the document never took.
    fun format(transform: (EquationElement) -> EquationElement): Boolean {
        val formatted: List<Element> = elements.formatEquations(transform)
        if (formatted.isEmpty()) return false

        onUpdate(formatted)
        return true
    }

    SectionLabel("EQUATION")
    NumberField(
        label = "Size",
        value = primary.fontSize,
        enabled = enabled,
        onCommit = { size -> format { it.copy(fontSize = size) } },
        modifier = Modifier.width(96.dp),
        minimum = 1f,
    )

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

    PanelDivider()
}

/** [language] appended under its own name where the catalog doesn't carry it. */
private fun List<String>.withLanguage(language: String): List<Pair<String, String>> =
    (if (contains(language)) this else this + language).map { it to it }

/**
 * The live property editor for the selection.
 *
 * Every control shows the primary element (the first selected) and edits the
 * whole selection: typing 40 into X puts every selected element at x = 40, the
 * way Keynote's inspector does, rather than moving them as a block. Controls are
 * stateless against [elements]: what they show is what came back through the
 * state, and what they send is [Element.update] applied to those same values.
 *
 * A locked primary dims everything but its unlock button; the presenter enforces
 * the same rule per element, this only stops the user reaching for it.
 */
@Composable
private fun ElementFormatPanel(
    elements: List<Element>,
    onUpdate: (List<Element>) -> Unit,
    onPreview: (List<Element>) -> Unit,
    onReorder: (List<String>, ZOrderMove) -> Unit,
    onSetLocked: (List<String>, Boolean) -> Unit,
    onFlip: (List<String>, FlipAxis) -> Unit,
    onGroup: (List<String>) -> Unit,
    onUngroup: (String) -> Unit,
) {
    val primary: Element = elements.first()
    val ids: List<String> = elements.map { it.id }
    val frame: Frame = primary.frame
    val enabled: Boolean = !primary.locked

    fun frames(edit: (Frame) -> Frame): List<Element> =
        elements.map { it.update(frame = edit(it.frame)) }

    SectionLabel("POSITION & SIZE")
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

    PanelDivider()

    SectionLabel("ROTATE")
    NumberField(
        label = "Angle",
        value = primary.rotation,
        enabled = enabled,
        onCommit = { angle -> onUpdate(elements.map { it.update(rotation = angle) }) },
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

    SectionLabel("OPACITY")
    SliderRow(
        fraction = primary.opacity,
        valueLabel = "${(primary.opacity * 100).roundToInt()}%",
        enabled = enabled,
        // The whole drag is one edit: samples preview, the release commits.
        onDrag = { value -> onPreview(elements.map { it.update(opacity = value) }) },
        onRelease = { value -> onUpdate(elements.map { it.update(opacity = value) }) },
    )

    PanelDivider()

    SectionLabel("ARRANGE")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "Bring Forward",
            enabled = enabled,
            onClick = { onReorder(ids, ZOrderMove.Forward) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Send Backward",
            enabled = enabled,
            onClick = { onReorder(ids, ZOrderMove.Backward) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TonalButton(
            label = "Bring to Front",
            enabled = enabled,
            onClick = { onReorder(ids, ZOrderMove.ToFront) },
            modifier = Modifier.weight(1f),
        )
        TonalButton(
            label = "Send to Back",
            enabled = enabled,
            onClick = { onReorder(ids, ZOrderMove.ToBack) },
            modifier = Modifier.weight(1f),
        )
    }

    // Two unlocked elements make a group; a lone group comes apart again. Neither
    // button is worth a section on its own, so the section is only here when one
    // of them has something to do.
    val groupable: Boolean = elements.count { !it.locked } >= 2
    val ungroupable: Boolean = primary is GroupElement && elements.size == 1

    if (groupable || ungroupable) {
        PanelDivider()

        SectionLabel("GROUP")
        if (groupable) TonalButton(
            label = "Group",
            enabled = enabled,
            onClick = { onGroup(ids) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (ungroupable) TonalButton(
            label = "Ungroup",
            enabled = enabled,
            onClick = { onUngroup(primary.id) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    PanelDivider()

    TonalButton(
        label = if (primary.locked) "Unlock" else "Lock",
        enabled = true,
        onClick = { onSetLocked(ids, !primary.locked) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The Animate tab: what the selected element builds in with, and what the slide
 * itself leaves on.
 *
 * Builds are still the design's mock, so they show only where they would apply,
 * against a selected element. The transition below them is real and always
 * there, because it belongs to the slide rather than to the selection.
 *
 * Every control commits one settled edit through [onSetTransition], the duration
 * drag excepted: its samples preview the slide and its release commits one, the
 * way the opacity slider does, so a drag is one history entry rather than one
 * per sample.
 *
 * Layouts have no transitions. A layout is never presented; the slide wearing it
 * is, and it carries its own.
 */
@Composable
private fun AnimatePanel(
    slide: Slide,
    isEditingLayouts: Boolean,
    hasSelection: Boolean,
    onSetTransition: (slideId: String, transition: SlideTransition?) -> Unit,
    onPreview: (Slide) -> Unit,
    onUpdate: (Slide) -> Unit,
) {
    if (isEditingLayouts) {
        Text(
            text = "Layouts have no transitions.",
            color = LocalChromeTokens.current.subtle,
            fontSize = 12.sp,
        )
        return
    }

    if (hasSelection) {
        BuildMock()
        PanelDivider()
    }

    // The label says which of the two things on this tab it belongs to, but only
    // where both are on it.
    SectionLabel(if (hasSelection) "SLIDE TRANSITION" else "TRANSITION")
    TransitionSection(
        slide = slide,
        onSetTransition = onSetTransition,
        onPreview = onPreview,
        onUpdate = onUpdate,
    )
}

/** The slide's own transition, the whole of it stateless against [slide]. */
@Composable
private fun TransitionSection(
    slide: Slide,
    onSetTransition: (String, SlideTransition?) -> Unit,
    onPreview: (Slide) -> Unit,
    onUpdate: (Slide) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val transition: SlideTransition? = slide.transition

    // Null is the deck's own transition rather than no transition at all, which
    // is what "Default" says and TransitionKind.None doesn't.
    DropdownField(
        label = "Effect",
        value = transition?.kind,
        options = TRANSITION_KINDS,
        enabled = true,
        onPick = { kind ->
            val picked: SlideTransition? =
                kind?.let { (transition ?: SlideTransition()).copy(kind = it) }
            onSetTransition(slide.id, picked)
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (transition == null) return

    if (transition.kind in DIRECTIONAL_KINDS) SegmentedRow(Modifier.fillMaxWidth()) {
        TransitionDirection.entries.forEachIndexed { index, direction ->
            val selected: Boolean = transition.direction == direction
            Segment(
                selected = selected,
                first = index == 0,
                onClick = { onSetTransition(slide.id, transition.copy(direction = direction)) },
            ) {
                SegmentLabel(direction.name, selected = selected, enabled = true)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Duration", color = tokens.subtle, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        SliderRow(
            fraction = transition.durationMs.asDurationFraction(),
            valueLabel = transition.durationMs.asSeconds(),
            modifier = Modifier.weight(1f),
            onDrag = { value -> onPreview(slide.withDuration(value)) },
            onRelease = { value -> onUpdate(slide.withDuration(value)) },
        )
    }

    SegmentedRow(Modifier.fillMaxWidth()) {
        TRANSITION_TRIGGERS.forEachIndexed { index, (trigger, title) ->
            val selected: Boolean = transition.trigger == trigger
            Segment(
                selected = selected,
                first = index == 0,
                onClick = { onSetTransition(slide.id, transition.copy(trigger = trigger)) },
            ) {
                SegmentLabel(title, selected = selected, enabled = true)
            }
        }
    }

    // Only the automatic slide has one: a slide waiting for a click waits as
    // long as it is left waiting.
    if (transition.trigger == TransitionTrigger.Automatic) NumberField(
        label = "Delay",
        value = transition.delayMs / 1000f,
        enabled = true,
        onCommit = { seconds ->
            val delayMs: Int = (seconds * 1000).roundToInt()
            onSetTransition(slide.id, transition.copy(delayMs = delayMs))
        },
        modifier = Modifier.fillMaxWidth(),
        minimum = 0f,
        fractional = true,
    )
}

/** "Default" is the deck's transition, and the only option that isn't a kind. */
private val TRANSITION_KINDS: List<Pair<TransitionKind?, String>> = listOf(
    null to "Default",
    TransitionKind.None to "None",
    TransitionKind.Dissolve to "Dissolve",
    TransitionKind.Push to "Push",
    TransitionKind.MoveIn to "Move In",
    TransitionKind.Wipe to "Wipe",
    TransitionKind.MagicMove to "Magic Move",
)

/** The kinds that travel, and so the only ones a direction means anything to. */
private val DIRECTIONAL_KINDS: Set<TransitionKind> =
    setOf(TransitionKind.Push, TransitionKind.MoveIn, TransitionKind.Wipe)

private val TRANSITION_TRIGGERS: List<Pair<TransitionTrigger, String>> = listOf(
    TransitionTrigger.OnClick to "On Click",
    TransitionTrigger.Automatic to "Automatically",
)

/** The duration slider's ends, and the tenth of a second its samples land on. */
private const val MIN_DURATION_MS: Int = 100
private const val MAX_DURATION_MS: Int = 3000
private const val DURATION_STEP_MS: Int = 100

/** Where a duration sits along the slider, as 0..1. */
private fun Int.asDurationFraction(): Float =
    (this - MIN_DURATION_MS).toFloat() / (MAX_DURATION_MS - MIN_DURATION_MS)

/** A slider sample as a duration, snapped so the label reads in whole tenths. */
private fun Float.asDurationMs(): Int {
    val raw: Float = MIN_DURATION_MS + this * (MAX_DURATION_MS - MIN_DURATION_MS)
    return (raw / DURATION_STEP_MS).roundToInt() * DURATION_STEP_MS
}

/** Tenths of a second, as fine as the slider goes: 600 reads "0.6s". */
private fun Int.asSeconds(): String = "${this / 1000}.${this % 1000 / 100}s"

/**
 * The slide with a duration off a slider sample. Only ever called on a slide
 * that has a transition, since only that slide draws the slider.
 */
private fun Slide.withDuration(sample: Float): Slide {
    val transition: SlideTransition = this.transition ?: SlideTransition()
    return copy(transition = transition.copy(durationMs = sample.asDurationMs()))
}

/** The design's build mock, still a placeholder: no build is editable yet. */
@Composable
private fun BuildMock() {
    val tokens: ChromeTokens = LocalChromeTokens.current

    SectionLabel("BUILD IN")
    OutlinedField(label = "Effect", value = "Fade Up", trailing = "▾")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Duration", color = tokens.subtle, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        SliderRow(fraction = 0.3f, valueLabel = "0.4s", modifier = Modifier.weight(1f))
    }

    PanelDivider()

    SectionLabel("BUILD ORDER")
    BuildOrderCard(order = 1, label = "Title", meta = "Fade Up · 0.4s", active = true)
    BuildOrderCard(order = 2, label = "Shape", meta = "Pop · 0.3s · after 1", active = false)
    BuildOrderCard(order = 3, label = "Image", meta = "Dissolve · 0.5s · with 2", active = false)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.tonal)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "+ Add build",
            color = tokens.tonalText,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * The Slide tab, in either of its two moods. On a slide it is the layout the
 * slide is on plus its own appearance and background; on a layout it is that
 * layout's name and its placeholders, with the appearance and background
 * sections working exactly as they do on a slide, because a layout is one.
 *
 * Every control is stateless against [slide] and commits whole slides through
 * [onUpdate], one settled edit per tap: there is no continuous colour picker
 * here, so one tap is one history entry. The layout verbs are events of their
 * own rather than slide edits, since what they change is more than this slide.
 *
 * The theme and the deck background sit at the top on a slide and nowhere at
 * all on a layout: they are the deck's, and the panel in layout mode is about
 * the one layout it has open.
 */
@Composable
private fun SlidePanel(
    slide: Slide,
    layouts: List<Slide>,
    isEditingLayouts: Boolean,
    themes: List<Theme>,
    userThemes: List<Theme>,
    themeName: String,
    documentBackground: SlideBackground?,
    slideWidth: Float,
    slideHeight: Float,
    slideSizePreset: SlideSizePreset?,
    onChangeTheme: (String) -> Unit,
    onSaveAsTheme: (String) -> Unit,
    onDeleteUserTheme: (String) -> Unit,
    onSetDocumentBackground: (SlideBackground?) -> Unit,
    onSetSlideSize: (Float, Float, Boolean) -> Unit,
    onUpdate: (Slide) -> Unit,
    onApplyLayout: (String, String?) -> Unit,
    onReapplyLayout: (String) -> Unit,
    onEditSlideLayouts: () -> Unit,
    onExitSlideLayouts: () -> Unit,
    onAddPlaceholder: (PlaceholderRole) -> Unit,
    onRenameSlide: (String, String) -> Unit,
) {
    if (!isEditingLayouts) {
        SectionLabel("THEME")
        // A deck can be on a theme that is no longer in the library: it was
        // saved, used, then deleted. The name still says what the deck is on, so
        // it is offered as an option of its own rather than shown as blank.
        val named: List<Pair<String, String>> = themes.map { it.name to it.name }
        val options: List<Pair<String, String>> =
            if (named.any { (name, _) -> name == themeName }) named
            else named + (themeName to themeName)

        DropdownField(
            label = "Theme",
            value = themeName,
            options = options,
            enabled = true,
            onPick = onChangeTheme,
            modifier = Modifier.fillMaxWidth(),
        )

        // View-local, like the navigator's rename: the name reaches the loop on
        // OK and never before.
        var saving: Boolean by remember { mutableStateOf(false) }
        val isUserTheme: Boolean = userThemes.any { it.name == themeName }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TonalButton(
                label = "Save Theme...",
                enabled = true,
                onClick = { saving = true },
                modifier = Modifier.weight(1f),
            )
            // Only the user's own: a built-in is always there to go back to.
            if (isUserTheme) TonalButton(
                label = "Delete Theme",
                enabled = true,
                onClick = { onDeleteUserTheme(themeName) },
                modifier = Modifier.weight(1f),
            )
        }

        // Prefilled with the current name, so saving over the theme you are on
        // is the default and a new one is a retype.
        if (saving) NameDialog(
            title = "Save Theme",
            confirmLabel = "Save",
            name = themeName,
            onDismiss = { saving = false },
            onCommit = { name ->
                saving = false
                onSaveAsTheme(name)
            },
        )

        PanelDivider()

        SlideSizeSection(
            width = slideWidth,
            height = slideHeight,
            preset = slideSizePreset,
            onSetSlideSize = onSetSlideSize,
        )

        PanelDivider()

        SectionLabel("DECK BACKGROUND")
        BackgroundControls(background = documentBackground, onChange = onSetDocumentBackground)

        PanelDivider()
    }

    SectionLabel("LAYOUT")

    if (isEditingLayouts) {
        // A layout is picked out of a list by its name, so the name is the one
        // thing about it that has to be editable somewhere. This is that
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
        // Two by two rather than a row of four: "Media" doesn't fit a quarter of
        // 282dp, and a 2x2 block reads as one set of four either way.
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
        // A slide whose layout is gone reads as being on none, so it has nothing
        // to put back either.
        TonalButton(
            label = "Reapply Layout",
            enabled = layouts.any { it.id == slide.layoutId },
            onClick = { onReapplyLayout(slide.id) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    PanelDivider()

    SectionLabel("APPEARANCE")
    // Still inert. A slide owns the placeholders it was given, so hiding one is
    // deleting it, and nothing in the model says "this slide, without its
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

    // The mode switch, pinned to the bottom of the panel the way the design has
    // it, saying whichever half of the trip is left to make.
    TonalButton(
        label = if (isEditingLayouts) "Done" else "Edit Slide Layouts",
        enabled = true,
        onClick = if (isEditingLayouts) onExitSlideLayouts else onEditSlideLayouts,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The SLIDE SIZE section: the named shapes, then Custom for anything else.
 *
 * Nothing here resizes on the pick. A slide size is the deck's shape, so both
 * routes stop and ask the one question that has no default answer: does the
 * content come along. The preset asks it as three buttons, the custom size as a
 * checkbox next to the numbers it is already asking for.
 *
 * The field reads the size rather than the word "Custom" when the deck is on
 * one, since 1600 x 900 says what the deck is and "Custom" doesn't.
 */
@Composable
private fun SlideSizeSection(
    width: Float,
    height: Float,
    preset: SlideSizePreset?,
    onSetSlideSize: (Float, Float, Boolean) -> Unit,
) {
    // View-local, like the theme's Save dialog: the size reaches the loop on the
    // answer and never on the pick.
    var pending: SlideSizePreset? by remember { mutableStateOf(null) }
    var customizing: Boolean by remember { mutableStateOf(false) }

    val options: List<Pair<SlideSizePreset?, String>> =
        SlideSizePreset.entries.map { it to it.title } + (null to "Custom...")

    SectionLabel("SLIDE SIZE")
    DropdownField(
        label = "Size",
        value = preset,
        options = options,
        enabled = true,
        onPick = { picked -> if (picked == null) customizing = true else pending = picked },
        modifier = Modifier.fillMaxWidth(),
        display = if (preset == null) "${width.asWholeNumber()} × ${height.asWholeNumber()}" else null,
    )

    pending?.let { picked ->
        ScaleContentDialog(
            title = picked.title,
            onDismiss = { pending = null },
            onAnswer = { scale ->
                pending = null
                onSetSlideSize(picked.width, picked.height, scale)
            },
        )
    }

    if (customizing) CustomSlideSizeDialog(
        width = width,
        height = height,
        onDismiss = { customizing = false },
        onCommit = { newWidth, newHeight, scale ->
            customizing = false
            onSetSlideSize(newWidth, newHeight, scale)
        },
    )
}

/** The preset's one question, as its two answers plus the way out. */
@Composable
private fun ScaleContentDialog(title: String, onDismiss: () -> Unit, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scale content to fit?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text("Moving this deck to $title.", fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Scale") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onAnswer(false) }) { Text("Don't Scale") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * A size typed by hand: the two sides prefilled with the deck's own, and the
 * preset dialog's question as the checkbox under them.
 *
 * The fields are [NumberField]s, so each settles on Enter or on leaving it, and
 * OK sends whatever they have settled on. The bounds are the document's, not
 * this dialog's: [io.github.xxfast.cupboard.document.resized] clamps.
 */
@Composable
private fun CustomSlideSizeDialog(
    width: Float,
    height: Float,
    onDismiss: () -> Unit,
    onCommit: (width: Float, height: Float, scaleContent: Boolean) -> Unit,
) {
    var newWidth: Float by remember { mutableStateOf(width) }
    var newHeight: Float by remember { mutableStateOf(height) }
    var scale: Boolean by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Slide Size", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NumberField(
                        label = "Width",
                        value = newWidth,
                        enabled = true,
                        onCommit = { entered -> newWidth = entered },
                        modifier = Modifier.weight(1f),
                        minimum = 1f,
                    )
                    NumberField(
                        label = "Height",
                        value = newHeight,
                        enabled = true,
                        onCommit = { entered -> newHeight = entered },
                        modifier = Modifier.weight(1f),
                        minimum = 1f,
                    )
                }
                AppearanceRow(
                    label = "Scale content",
                    checked = scale,
                    onToggle = { on -> scale = on },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCommit(newWidth, newHeight, scale) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Default / Color / Gradient over the palette the picked mode asks for, one
 * settled [onChange] per tap. Null is Default: whatever sits behind this one,
 * which for a slide is its layout's or the deck's and for the deck is the app's
 * own dark gradient.
 *
 * Shared by the slide's BACKGROUND section and the deck's: the two differ only
 * in where the background they show comes from and where the picked one goes.
 */
@Composable
private fun BackgroundControls(background: SlideBackground?, onChange: (SlideBackground?) -> Unit) {
    SegmentedRow {
        Segment(
            selected = background == null,
            first = true,
            onClick = { onChange(null) },
        ) {
            SegmentLabel("Default", selected = background == null, enabled = true)
        }
        Segment(
            selected = background is SlideBackground.Color,
            first = false,
            // Switching into a mode there is no colour for yet has to land on
            // some colour, so it lands on the palette's deep navy.
            onClick = {
                if (background !is SlideBackground.Color) {
                    onChange(SlideBackground.Color(DEFAULT_FILL))
                }
            },
        ) {
            SegmentLabel("Color", selected = background is SlideBackground.Color, enabled = true)
        }
        Segment(
            selected = background is SlideBackground.Gradient,
            first = false,
            onClick = {
                if (background !is SlideBackground.Gradient) {
                    onChange(SlideBackground.Gradient(DEFAULT_GRADIENT_START, DEFAULT_FILL))
                }
            },
        ) {
            SegmentLabel("Gradient", selected = background is SlideBackground.Gradient, enabled = true)
        }
    }

    when (background) {
        null -> Unit

        is SlideBackground.Color -> SwatchGrid(
            selected = background.color,
            onPick = { color -> onChange(SlideBackground.Color(color)) },
        )

        is SlideBackground.Gradient -> {
            // The angle stays at its default: an angle control is a slider, and a
            // slider is a gesture, which this section has no need of yet.
            SwatchLabel("Start")
            SwatchGrid(
                selected = background.start,
                onPick = { color -> onChange(background.copy(start = color)) },
            )
            SwatchLabel("End")
            SwatchGrid(
                selected = background.end,
                onPick = { color -> onChange(background.copy(end = color)) },
            )
        }
    }
}

/**
 * One appearance checklist row: an 18dp check square plus its label.
 * [onToggle] null leaves it inert, which is what the layout placeholders want.
 */
@Composable
private fun AppearanceRow(label: String, checked: Boolean, onToggle: ((Boolean) -> Unit)? = null) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
        modifier = Modifier.clickable(enabled = onToggle != null) { onToggle?.invoke(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) tokens.accent else tokens.track),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Text("✓", color = tokens.accentText, fontSize = 10.sp)
        }
        Text(label, color = tokens.text, fontSize = 13.sp)
    }
}

/**
 * The background palette, packed ARGB like the document model: the deck's own
 * darks first, then the accents it pairs with, then the two lights a slide needs
 * when it goes inverted. A fixed set, not a picker: the slide is a dark surface
 * and the point is the handful of colours that still read on it.
 */
private val BACKGROUND_SWATCHES: List<Long> = listOf(
    0xFF000000, 0xFF17181C, 0xFF23262E, 0xFF101223, 0xFF2A2452, 0xFF4C2FA8,
    0xFF0F3B39, 0xFF10391F, 0xFF58151D, 0xFF6B4A0E, 0xFFD7D9DE, 0xFFFFFFFF,
)

/** What a slide falls into when it is switched to a background it has no colour for yet. */
private const val DEFAULT_FILL: Long = 0xFF101223
private const val DEFAULT_GRADIENT_START: Long = 0xFF2A2452

/** The palette, six to a row, the current colour ringed. */
@Composable
private fun SwatchGrid(selected: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in BACKGROUND_SWATCHES.chunked(6)) {
            SwatchRow(colors = row, selected = selected, enabled = true, onPick = onPick)
        }
    }
}

/** One row of the palette, the swatches sharing the width evenly. */
@Composable
private fun SwatchRow(
    colors: List<Long>,
    selected: Long,
    enabled: Boolean,
    onPick: (Long) -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (color in colors) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(color))
                    // The hairline is what keeps a near-black swatch off a
                    // near-black panel; the ring replaces it when picked.
                    .border(
                        width = if (color == selected) 2.dp else 1.dp,
                        color = if (color == selected) tokens.accent else tokens.outline,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable(enabled = enabled) { onPick(color) },
            )
        }
    }
}

/** The label over one of the gradient's two grids. */
@Composable
private fun SwatchLabel(text: String) {
    Text(text = text, color = LocalChromeTokens.current.subtle, fontSize = 11.5.sp)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LocalChromeTokens.current.subtle,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

/** A static outlined field with the M3 floating label cut into the border. */
@Composable
private fun OutlinedField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    monospace: Boolean = false,
    height: Dp = 44.dp,
    enabled: Boolean = true,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .border(1.dp, if (enabled) tokens.outline else tokens.div, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                color = if (enabled) tokens.text else tokens.faint,
                fontSize = if (monospace) 13.sp else 14.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, color = if (enabled) tokens.dim else tokens.faint, fontSize = 10.sp)
            }
        }
        // The label sits on the border, masking it with the panel background.
        Text(
            text = label,
            color = if (enabled) tokens.subtle else tokens.faint,
            fontSize = 10.5.sp,
            modifier = Modifier
                .offset(x = 10.dp, y = (-7).dp)
                .background(tokens.insBg)
                .padding(horizontal = 5.dp),
        )
    }
}

/** Document units read as whole numbers; the fractions are the canvas's business. */
private fun Float.asWholeNumber(): String = roundToInt().toString()

/** Two decimals is as fine as a multiplier gets before it stops meaning anything. */
private fun Float.asMultiplier(): String = ((this * 100).roundToInt() / 100f).toString()

/** Packed ARGB the way it is typed: six digits when it is fully opaque, eight when it isn't. */
private fun Long.asHex(): String {
    val digits: String = toString(16).padStart(8, '0').uppercase()
    return if (digits.startsWith("FF")) "#${digits.substring(2)}" else "#$digits"
}

/** Six digits take a full alpha, eight carry their own. Anything else is not a colour. */
private fun String.toArgbOrNull(): Long? {
    val digits: String = trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null

    val value: Long = digits.toLongOrNull(16) ?: return null
    return if (digits.length == 6) value or 0xFF000000 else value
}

/**
 * [OutlinedField]'s look with an editable value, committed on Enter and on focus
 * loss. [onCommit] false puts [display] back, so a half-typed field can never
 * sit there pretending to hold what the document holds.
 *
 * The text is keyed on the displayed value, so an edit that lands elsewhere (an
 * undo, a canvas drag) redraws the field instead of leaving stale digits behind.
 */
@Composable
private fun EntryField(
    label: String,
    display: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    monospace: Boolean = true,
    onCommit: (String) -> Boolean,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val focusManager: FocusManager = LocalFocusManager.current
    var text: String by remember(display) { mutableStateOf(display) }
    var focused: Boolean by remember { mutableStateOf(false) }
    // The callback closes over the current element, so a commit that lands after
    // a state change has to run the latest one rather than the one it started with.
    val commit: (String) -> Boolean by rememberUpdatedState(onCommit)

    fun settle() {
        if (!commit(text)) text = display
    }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, if (enabled) tokens.outline else tokens.div, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(
                    color = if (enabled) tokens.text else tokens.faint,
                    fontSize = 13.sp,
                    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                ),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused) settle()
                        focused = state.isFocused
                    }
                    .onPreviewKeyEvent { event ->
                        val entered: Boolean = event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        if (entered) {
                            settle()
                            focusManager.clearFocus()
                        }
                        entered
                    },
            )
        }
        Text(
            text = label,
            color = if (enabled) tokens.subtle else tokens.faint,
            fontSize = 10.5.sp,
            modifier = Modifier
                .offset(x = 10.dp, y = (-7).dp)
                .background(tokens.insBg)
                .padding(horizontal = 5.dp),
        )
    }
}

/**
 * An [EntryField] over a number. Anything that isn't one reverts, and so does
 * the no-change case: "72.0" typed over 72 normalizes back to "72" rather than
 * sitting there as a phantom edit.
 *
 * [fractional] keeps the decimals, for the values that are multipliers rather
 * than document units.
 */
@Composable
private fun NumberField(
    label: String,
    value: Float,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Float = Float.NEGATIVE_INFINITY,
    fractional: Boolean = false,
) {
    val display: String = if (fractional) value.asMultiplier() else value.asWholeNumber()

    EntryField(label = label, display = display, enabled = enabled, modifier = modifier) { text ->
        val entered: Float? = text.trim().toFloatOrNull()?.coerceAtLeast(minimum)
        if (entered == null || entered == value) return@EntryField false

        onCommit(entered)
        return@EntryField true
    }
}

/** An [EntryField] over a packed ARGB colour, typed as hex. */
@Composable
private fun HexField(
    label: String,
    color: Long,
    enabled: Boolean,
    onCommit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntryField(
        label = label,
        display = color.asHex(),
        enabled = enabled,
        modifier = modifier,
    ) { text ->
        val entered: Long? = text.toArgbOrNull()
        if (entered == null || entered == color) return@EntryField false

        onCommit(entered)
        return@EntryField true
    }
}

/** A full-width M3 tonal button, the inspector's action shape. */
@Composable
private fun TonalButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) tokens.tonal else tokens.track)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) tokens.tonalText else tokens.faint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** An outlined segmented button row, radius 20, hairlines between segments. */
@Composable
private fun SegmentedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LocalChromeTokens.current.outline, RoundedCornerShape(20.dp)),
        content = content,
    )
}

/** [onClick] null leaves the segment inert, which is what the mocks want. */
@Composable
private fun RowScope.Segment(
    selected: Boolean,
    first: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(Modifier.weight(1f).height(38.dp)) {
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(tokens.outline))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (selected) tokens.segOn else Color.Transparent)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** A segment's text, coloured for its on/off and enabled/disabled corners. */
@Composable
private fun SegmentLabel(text: String, selected: Boolean, enabled: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Text(
        text = text,
        color = when {
            !enabled -> tokens.faint
            selected -> tokens.segOnText
            else -> tokens.segOff
        },
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Text alignment glyph: four rules, the short ones placed per [variant]. */
@Composable
private fun AlignGlyph(color: Color, variant: Int) {
    Canvas(Modifier.size(width = 12.dp, height = 10.dp)) {
        val s: Float = size.width / 12f
        val stroke: Float = 1.3.dp.toPx()
        val short: ClosedFloatingPointRange<Float> = when (variant) {
            0 -> 0f..8f
            1 -> 2f..10f
            2 -> 4f..12f
            else -> 0f..12f
        }
        for ((index, y) in listOf(0.7f, 3.8f, 6.9f, 10f).withIndex()) {
            val range: ClosedFloatingPointRange<Float> = if (index % 2 == 0) 0f..12f else short
            drawLine(
                color = color,
                start = Offset(range.start * s, y * s),
                end = Offset(range.endInclusive * s, y * s),
                strokeWidth = stroke,
            )
        }
    }
}

/**
 * A slider: filled track up to [fraction], knob riding the seam.
 *
 * Interactive once [onDrag] is given, inert otherwise (the mock panels). It draws
 * only from [fraction], never from where the pointer is: what a gesture shows has
 * to come back through the state, or the repaint can go missing mid-drag.
 */
@Composable
private fun SliderRow(
    fraction: Float,
    valueLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDrag: ((Float) -> Unit)? = null,
    onRelease: ((Float) -> Unit)? = null,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val interactive: Boolean = enabled && onDrag != null
    val filled: Float = fraction.coerceIn(0f, 1f)
    val fill: Color = if (enabled) tokens.accent else tokens.faint
    // The callbacks close over the current element, so the gesture loop has to
    // read the latest ones rather than the pair it started with.
    val stream: ((Float) -> Unit)? by rememberUpdatedState(onDrag)
    val commit: ((Float) -> Unit)? by rememberUpdatedState(onRelease)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(20.dp)
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val down: PointerInputChange = awaitFirstDown()
                            var value: Float = valueAt(down.position.x, size.width)
                            down.consume()
                            stream?.invoke(value)
                            drag(down.id) { change ->
                                value = valueAt(change.position.x, size.width)
                                change.consume()
                                stream?.invoke(value)
                            }
                            commit?.invoke(value)
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.track),
            )
            Box(
                Modifier
                    .fillMaxWidth(filled)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(fill),
            )
            // Alignment bias maps 0..1 along the track to -1..1.
            Box(
                Modifier
                    .align(BiasAlignment(filled * 2f - 1f, 0f))
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(fill),
            )
        }
        Text(
            text = valueLabel,
            color = if (enabled) tokens.text else tokens.faint,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** Where [x] sits along a [width]-wide track, as 0..1. A zero width reads as 0. */
private fun valueAt(x: Float, width: Int): Float =
    if (width == 0) 0f else (x / width).coerceIn(0f, 1f)

/** One build-order row as a 12dp-radius tonal card, per the Linux mock. */
@Composable
private fun BuildOrderCard(order: Int, label: String, meta: String, active: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.tonal)
            .let {
                if (active) it.border(1.dp, tokens.accent, RoundedCornerShape(12.dp))
                else it
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (active) tokens.accent else tokens.badgeOff),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$order",
                color = if (active) tokens.accentText else tokens.badgeOffText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = tokens.tonalText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(meta, color = tokens.subtle, fontSize = 11.5.sp)
        }
        Text("⠿", color = tokens.subtle, fontSize = 13.sp)
    }
}
