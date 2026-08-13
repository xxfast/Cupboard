package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import io.github.xxfast.cupboard.document.Element
import io.github.xxfast.cupboard.document.Frame
import io.github.xxfast.cupboard.document.GroupElement
import io.github.xxfast.cupboard.document.ZOrderMove
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlin.math.roundToInt

/**
 * The 282dp M3 inspector: Format / Animate / Slide tabs over their bodies.
 * Clicking the active tab does nothing (close-on-reclick is macOS-only).
 *
 * Format is live whenever [selectedElements] isn't empty: the geometry, rotation,
 * opacity, z-order and lock of the selection. With nothing selected it falls back
 * to the design's text mock, which is still a placeholder (text formatting is its
 * own roadmap item). Animate and Slide remain mocks throughout.
 *
 * The "Slide" tab is [InspectorTab.Document]: same pane, per-platform label.
 */
@Composable
fun EditorInspector(
    tab: InspectorTab,
    onSelectTab: (InspectorTab) -> Unit,
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
                else ElementFormatPanel(
                    elements = selectedElements,
                    onUpdate = onUpdateElements,
                    onPreview = onPreviewElements,
                    onReorder = onReorderElements,
                    onSetLocked = onSetElementsLocked,
                    onFlip = onFlipElements,
                    onGroup = onGroupElements,
                    onUngroup = onUngroupElements,
                )

                InspectorTab.Animate -> AnimatePanel()
                InspectorTab.Document -> SlidePanel()
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

@Composable
private fun AnimatePanel() {
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

@Composable
private fun SlidePanel() {
    val tokens: ChromeTokens = LocalChromeTokens.current

    // Slide layout card.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.ctrl)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .size(width = 62.dp, height = 35.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.68f).height(4.dp).background(Color(0xFF2A2630)))
            Box(Modifier.fillMaxWidth(0.46f).height(3.dp).background(Color(0xFF9A958D)))
        }
        Column(Modifier.weight(1f)) {
            Text("Slide Layout", color = tokens.subtle, fontSize = 11.sp)
            Text("Title", color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("⌄", color = tokens.subtle, fontSize = 10.sp)
    }

    SectionLabel("APPEARANCE")
    AppearanceRow(label = "Title", checked = true)
    AppearanceRow(label = "Body", checked = true)
    AppearanceRow(label = "Slide Number", checked = false)

    PanelDivider()

    SectionLabel("BACKGROUND")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.segBg)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.accent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Text("✓", color = tokens.accentText, fontSize = 11.sp)
            Text("Standard", color = tokens.accentText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Dynamic", color = tokens.subtle, fontSize = 12.sp)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, tokens.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Colour Fill", color = tokens.ctrlText, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
        Text("⌄", color = tokens.subtle, fontSize = 9.sp)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, tokens.outline, RoundedCornerShape(8.dp)),
        )
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFFF0357B),
                            Color(0xFFFFC24B),
                            Color(0xFF43C57E),
                            Color(0xFF3FA9F5),
                            Color(0xFF7F52FF),
                            Color(0xFFF0357B),
                        ),
                    ),
                ),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.tonal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text("Edit Slide Layout", color = tokens.tonalText, fontSize = 12.5.sp)
    }
}

/** One appearance checklist row: an 18dp check square plus its label. */
@Composable
private fun AppearanceRow(label: String, checked: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
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
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .border(1.dp, tokens.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value,
                color = tokens.text,
                fontSize = if (monospace) 13.sp else 14.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) Text(trailing, color = tokens.dim, fontSize = 10.sp)
        }
        // The label sits on the border, masking it with the panel background.
        Text(
            text = label,
            color = tokens.subtle,
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

/**
 * [OutlinedField]'s look with an editable value, committed on Enter and on focus
 * loss. Anything that isn't a number reverts to what the document holds, so a
 * half-typed field can never write a garbage frame.
 *
 * The text is keyed on the displayed value, so an edit that lands elsewhere (an
 * undo, a canvas drag) redraws the field instead of leaving stale digits behind.
 */
@Composable
private fun NumberField(
    label: String,
    value: Float,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Float = Float.NEGATIVE_INFINITY,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val focusManager: FocusManager = LocalFocusManager.current
    val display: String = value.asWholeNumber()
    var text: String by remember(display) { mutableStateOf(display) }
    var focused: Boolean by remember { mutableStateOf(false) }

    // Reverting covers the no-change case too: "72.0" typed over 72 normalizes
    // back to "72" rather than sitting there as a phantom edit.
    fun commit() {
        val entered: Float? = text.trim().toFloatOrNull()?.coerceAtLeast(minimum)
        if (entered == null || entered == value) text = display
        else onCommit(entered)
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
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(tokens.accent),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused) commit()
                        focused = state.isFocused
                    }
                    .onPreviewKeyEvent { event ->
                        val entered: Boolean = event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        if (entered) {
                            commit()
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
