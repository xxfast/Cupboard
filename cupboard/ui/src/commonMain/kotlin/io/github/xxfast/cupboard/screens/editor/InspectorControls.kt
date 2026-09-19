package io.github.xxfast.cupboard.screens.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.ShapeShadow
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import kotlin.math.roundToInt

/**
 * The inspector's shared controls: fields, buttons, segments, swatches, the
 * slider and the disclosure header.
 *
 * Here rather than next to their callers because all three panels draw from the
 * same set, and a field that looks one way in Format and another in Animate is
 * the bug this file exists to prevent. Everything is [ChromeTokens]-coloured and
 * toolkit-free, so the whole set compiles for every target `:cupboard:ui` has.
 */

/**
 * The text palette: the slide's own white, the deck's lilacs, its two ambers,
 * and black for a slide that has gone inverted. A fixed set like the background
 * grid, with the hex field underneath for anything outside it.
 */
internal val TEXT_SWATCHES: List<Long> = listOf(
    0xFFFFFFFF, 0xFFA9A0D8, 0xFFD9CFFF, 0xFFFFE28A, 0xFFA98FFF, 0xFFF5C518, 0xFF000000,
)

/**
 * The background palette, packed ARGB like the document model: the deck's own
 * darks first, then the accents it pairs with, then the two lights a slide needs
 * when it goes inverted. A fixed set, not a picker: the slide is a dark surface
 * and the point is the handful of colours that still read on it.
 */
internal val BACKGROUND_SWATCHES: List<Long> = listOf(
    0xFF000000, 0xFF17181C, 0xFF23262E, 0xFF101223, 0xFF2A2452, 0xFF4C2FA8,
    0xFF0F3B39, 0xFF10391F, 0xFF58151D, 0xFF6B4A0E, 0xFFD7D9DE, 0xFFFFFFFF,
)

/** What a slide falls into when it is switched to a background it has no colour for yet. */
internal const val DEFAULT_FILL: Long = 0xFF101223
internal const val DEFAULT_GRADIENT_START: Long = 0xFF2A2452

/** The padding and rhythm every panel body is laid out on. */
internal val PanelPadding: Dp = 16.dp
internal val PanelGap: Dp = 16.dp

/**
 * A panel's scrolling body: everything under whatever that panel pins above it.
 *
 * The scroll lives here rather than around the whole inspector because the
 * segmented controls, the object-style strip and the Build Order button are all
 * pinned, and a scroll wrapped around the lot would carry them off the top.
 */
@Composable
internal fun ColumnScope.PanelBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(PanelPadding),
        verticalArrangement = Arrangement.spacedBy(PanelGap),
        content = content,
    )
}

/** Anything pinned above or below [PanelBody]: the same gutter, no scroll. */
@Composable
internal fun PinnedBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(PanelPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** The centred title a panel with nothing selected wears instead of segments. */
@Composable
internal fun PanelTitle(text: String) {
    Text(
        text = text,
        color = LocalChromeTokens.current.dim,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun PanelDivider() {
    HorizontalDivider(thickness = 1.dp, color = LocalChromeTokens.current.div)
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        color = LocalChromeTokens.current.subtle,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

/** The label over one of the gradient's two grids, and over every slider. */
@Composable
internal fun SwatchLabel(text: String) {
    Text(text = text, color = LocalChromeTokens.current.subtle, fontSize = 11.5.sp)
}

/**
 * A collapsible section: chevron, title, and a summary of what is inside it at
 * the right, so a collapsed Fill still says what colour it is.
 *
 * Whether it is open comes off `EditorState.expandedSections` and the click goes
 * back through the loop, so the disclosure survives a selection change the way
 * Keynote's does.
 */
@Composable
internal fun DisclosureSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Column(verticalArrangement = Arrangement.spacedBy(PanelGap)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionChevron(expanded)
            Text(
                text = title,
                color = tokens.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            summary()
        }

        if (expanded) content()
    }
}

/**
 * A section header's disclosure control: the navigator's stroked chevron at the
 * size a 13sp header wants, pointing right when the section is shut and turning
 * down as it opens.
 *
 * The same drawing as [EditorNavigator]'s rather than a glyph, because a glyph
 * is whatever the platform font happens to have and the two disclosures in this
 * app should not be two different marks.
 */
@Composable
private fun SectionChevron(expanded: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val rotation: Float by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 140),
    )

    Canvas(Modifier.size(ChevronSize).rotate(rotation)) {
        val scale: Float = size.width / 16f
        val chevron: Path = Path().apply {
            moveTo(5.5f * scale, 3.5f * scale)
            lineTo(11f * scale, 8f * scale)
            lineTo(5.5f * scale, 12.5f * scale)
        }
        drawPath(
            path = chevron,
            color = tokens.icon,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private val ChevronSize: Dp = 16.dp

/** The well a disclosure summary is drawn in: 56x20, the panel's corner. */
private val SummaryWidth: Dp = 56.dp
private val SummaryHeight: Dp = 20.dp
private val SummaryCorner: Dp = 4.dp

/** The diagonal a "none" well is struck through with. Keynote's red, muted. */
private val NoneStrike: Color = Color(0xFFBF4A4A)

/**
 * A colour as a summary: the colour itself, or the struck-through grey well that
 * says there is none.
 */
@Composable
internal fun ColorSummary(color: Long?) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Canvas(Modifier.size(width = SummaryWidth, height = SummaryHeight)) {
        val corner = CornerRadius(SummaryCorner.toPx())
        drawRoundRect(
            color = if (color == null) tokens.track else Color(color),
            cornerRadius = corner,
        )
        drawRoundRect(color = tokens.outline, cornerRadius = corner, style = Stroke(1.dp.toPx()))
        if (color == null) drawLine(
            color = NoneStrike,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

/** A border as a summary: its line at its own weight, or the "none" well. */
@Composable
internal fun BorderSummary(color: Long, width: Float) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    if (width <= 0f) {
        ColorSummary(null)
        return
    }

    Canvas(Modifier.size(width = SummaryWidth, height = SummaryHeight)) {
        val corner = CornerRadius(SummaryCorner.toPx())
        drawRoundRect(color = tokens.track, cornerRadius = corner)
        drawLine(
            color = Color(color),
            start = Offset(6.dp.toPx(), size.height / 2f),
            end = Offset(size.width - 6.dp.toPx(), size.height / 2f),
            // Capped the way the style swatches cap theirs: past a few points it
            // is a summary made of border.
            strokeWidth = minOf(width.dp, 4.dp).toPx(),
        )
    }
}

/** A shadow as a summary: a lip of it under a plate, or the "none" well. */
@Composable
internal fun ShadowSummary(shadow: ShapeShadow?) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    if (shadow == null) {
        ColorSummary(null)
        return
    }

    Canvas(Modifier.size(width = SummaryWidth, height = SummaryHeight)) {
        val corner = CornerRadius(SummaryCorner.toPx())
        val lip: Float = 3.dp.toPx()
        val plate = Size(size.width, size.height - lip)
        drawRoundRect(
            color = Color(shadow.color),
            topLeft = Offset(lip, lip),
            size = Size(size.width - lip * 2, plate.height),
            cornerRadius = corner,
        )
        drawRoundRect(color = tokens.track, size = plate, cornerRadius = corner)
        drawRoundRect(
            color = tokens.outline,
            size = plate,
            cornerRadius = corner,
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** A value as a summary: what the Spacing and Lists headers read. */
@Composable
internal fun ValueSummary(text: String) {
    Text(text = text, color = LocalChromeTokens.current.dim, fontSize = 12.sp)
}

/** The palette, six to a row, the current colour ringed. */
@Composable
internal fun SwatchGrid(selected: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in BACKGROUND_SWATCHES.chunked(6)) {
            SwatchRow(colors = row, selected = selected, enabled = true, onPick = onPick)
        }
    }
}

/** One row of the palette, the swatches sharing the width evenly. */
@Composable
internal fun SwatchRow(
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

/**
 * One appearance checklist row: an 18dp check square plus its label.
 * [onToggle] null leaves it inert, which is what the layout placeholders want.
 */
@Composable
internal fun AppearanceRow(label: String, checked: Boolean, onToggle: ((Boolean) -> Unit)? = null) {
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

/** A static outlined field with the M3 floating label cut into the border. */
@Composable
internal fun OutlinedField(
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
        FieldLabel(label, enabled)
    }
}

/** The floating label, sitting on the border and masking it with the panel. */
@Composable
private fun FieldLabel(label: String, enabled: Boolean) {
    val tokens: ChromeTokens = LocalChromeTokens.current

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

/** Document units read as whole numbers; the fractions are the canvas's business. */
internal fun Float.asWholeNumber(): String = roundToInt().toString()

/** Two decimals is as fine as a multiplier gets before it stops meaning anything. */
internal fun Float.asMultiplier(): String = ((this * 100).roundToInt() / 100f).toString()

/** Packed ARGB the way it is typed: six digits when it is fully opaque, eight when it isn't. */
internal fun Long.asHex(): String {
    val digits: String = toString(16).padStart(8, '0').uppercase()
    return if (digits.startsWith("FF")) "#${digits.substring(2)}" else "#$digits"
}

/** Six digits take a full alpha, eight carry their own. Anything else is not a colour. */
internal fun String.toArgbOrNull(): Long? {
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
 *
 * [unit] rides inside the field after the value, and [trailing] is where the
 * numeric fields hang their stepper: both are part of the field rather than
 * controls beside it, so a row of them still lines up.
 */
@Composable
internal fun EntryField(
    label: String,
    display: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    monospace: Boolean = true,
    unit: String = "",
    trailing: (@Composable () -> Unit)? = null,
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
                .height(FieldHeight)
                .border(1.dp, if (enabled) tokens.outline else tokens.div, RoundedCornerShape(4.dp))
                .padding(start = 14.dp, end = if (trailing == null) 14.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The field sits in a box of its own rather than straight in the
            // row: a text field handed the row's height draws its glyphs into
            // whatever is left after the stepper, which clipped their tops.
            // Given its own line box it measures itself and is centred whole.
            Box(
                modifier = Modifier.weight(1f).height(FieldLineHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = if (enabled) tokens.text else tokens.faint,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                    ),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier
                        .fillMaxWidth()
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
            if (unit.isNotEmpty()) Text(
                text = unit,
                color = if (enabled) tokens.subtle else tokens.faint,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp, end = if (trailing == null) 0.dp else 6.dp),
            )
            trailing?.invoke()
        }
        FieldLabel(label, enabled)
    }
}

/**
 * Every field is one height, the floating label's own included: a row of them
 * lines up whether or not it carries a unit and a stepper.
 *
 * [FieldLineHeight] is the line box the value is centred in. It clears 13sp's
 * ascenders and descenders with room to spare, which is the whole point: a value
 * measured tighter than its own font loses the tops of its digits.
 */
private val FieldHeight: Dp = 44.dp
private val FieldLineHeight: Dp = 22.dp

/** The units a numeric field carries, spelled once so no two fields disagree. */
internal object Units {
    const val Points: String = "pt"
    const val Percent: String = "%"
    const val Degrees: String = "°"
    const val Seconds: String = "s"
    const val Times: String = "×"
    /** A plain count: a build's step index is a number and nothing more. */
    const val None: String = ""
}

/**
 * The up/down pair at a numeric field's trailing edge. One click is one step,
 * committed down the same path typing commits down, and the arrow at a bound is
 * dead rather than silently clamping.
 */
@Composable
private fun Stepper(upEnabled: Boolean, downEnabled: Boolean, onStep: (Int) -> Unit) {
    Column(Modifier.width(18.dp)) {
        StepperArrow(up = true, enabled = upEnabled) { onStep(1) }
        StepperArrow(up = false, enabled = downEnabled) { onStep(-1) }
    }
}

@Composable
private fun StepperArrow(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(15.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 9.dp, height = 6.dp)) {
            val color: Color = if (enabled) tokens.dim else tokens.faint
            val stroke: Float = 1.3.dp.toPx()
            val apexY: Float = if (up) 0f else size.height
            val baseY: Float = if (up) size.height else 0f
            drawLine(
                color = color,
                start = Offset(0f, baseY),
                end = Offset(size.width / 2f, apexY),
                strokeWidth = stroke,
            )
            drawLine(
                color = color,
                start = Offset(size.width, baseY),
                end = Offset(size.width / 2f, apexY),
                strokeWidth = stroke,
            )
        }
    }
}

/**
 * An [EntryField] over a number, with its unit inside it and a stepper on its
 * trailing edge. Anything that isn't a number reverts, and so does the no-change
 * case: "72.0" typed over 72 normalizes back to "72" rather than sitting there
 * as a phantom edit.
 *
 * [fractional] keeps the decimals, for the values that are multipliers rather
 * than document units. [step] is what one click of the stepper is worth, and it
 * lands on [onCommit] exactly as a typed value would: one step is one edit, one
 * history entry and one autosave write.
 */
@Composable
internal fun NumberField(
    label: String,
    value: Float,
    enabled: Boolean,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minimum: Float = Float.NEGATIVE_INFINITY,
    maximum: Float = Float.POSITIVE_INFINITY,
    fractional: Boolean = false,
    unit: String = Units.Points,
    step: Float = 1f,
) {
    val display: String = if (fractional) value.asMultiplier() else value.asWholeNumber()

    EntryField(
        label = label,
        display = display,
        enabled = enabled,
        modifier = modifier,
        unit = unit,
        trailing = {
            Stepper(
                upEnabled = enabled && value < maximum,
                downEnabled = enabled && value > minimum,
                onStep = { direction ->
                    val stepped: Float = (value + direction * step).coerceIn(minimum, maximum)
                    if (stepped != value) onCommit(stepped)
                },
            )
        },
    ) { text ->
        val entered: Float? = text.trim().toFloatOrNull()?.coerceIn(minimum, maximum)
        if (entered == null || entered == value) return@EntryField false

        onCommit(entered)
        return@EntryField true
    }
}

/** An [EntryField] over a packed ARGB colour, typed as hex. */
@Composable
internal fun HexField(
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

/**
 * [OutlinedField]'s look as a menu button: the current option in the field, the
 * whole list under it, the live one ticked.
 *
 * [display] overrides what the field reads, for the one picker whose value says
 * more than the option it sits on: a custom slide size is picked as "Custom..."
 * but shown as the size it actually is.
 */
@Composable
internal fun <T> DropdownField(
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

/** One entry of a [MenuButton]: what it reads and what picking it does. */
internal data class MenuChoice(
    val label: String,
    val enabled: Boolean = true,
    val onPick: () -> Unit,
)

/**
 * A [TonalButton] that drops a menu rather than acting: Align and Distribute,
 * and the Animate tab's effect lists.
 *
 * The open/closed flag is view-local by design. A menu that is showing is not a
 * fact about the document, and nothing on the canvas draws from it.
 */
@Composable
internal fun MenuButton(
    label: String,
    enabled: Boolean,
    choices: List<MenuChoice>,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    var open: Boolean by remember { mutableStateOf(false) }

    Box(modifier) {
        if (accent) AccentButton(
            label = label,
            enabled = enabled,
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        ) else TonalButton(
            label = "$label  ▾",
            enabled = enabled,
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (choice in choices) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = choice.label,
                            fontSize = 13.sp,
                            color = if (choice.enabled) {
                                LocalChromeTokens.current.text
                            } else {
                                LocalChromeTokens.current.faint
                            },
                        )
                    },
                    enabled = choice.enabled,
                    onClick = {
                        open = false
                        choice.onPick()
                    },
                )
            }
        }
    }
}

/** A full-width M3 tonal button, the inspector's action shape. */
@Composable
internal fun TonalButton(
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

/**
 * [TonalButton]'s louder twin, filled in the accent: the one button on an empty
 * Animate segment, which is the only thing there is to do on one.
 */
@Composable
internal fun AccentButton(
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
            .background(if (enabled) tokens.accent else tokens.track)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) tokens.accentText else tokens.faint,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** An outlined segmented button row, radius 20, hairlines between segments. */
@Composable
internal fun SegmentedRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, LocalChromeTokens.current.outline, RoundedCornerShape(20.dp)),
        content = content,
    )
}

/** [onClick] null leaves the segment inert. */
@Composable
internal fun RowScope.Segment(
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
internal fun SegmentLabel(text: String, selected: Boolean, enabled: Boolean) {
    Text(
        text = text,
        color = segmentTint(selected, enabled),
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** What a segment's content is painted, for its on/off and enabled/disabled corners. */
@Composable
internal fun segmentTint(selected: Boolean, enabled: Boolean): Color {
    val tokens: ChromeTokens = LocalChromeTokens.current
    return when {
        !enabled -> tokens.faint
        selected -> tokens.segOnText
        else -> tokens.segOff
    }
}

/**
 * The full-width segmented control a panel pins at its top: the Format segments
 * and the Animate tab's three builds, whose only difference is what they list.
 */
@Composable
internal fun <T> SegmentPicker(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedRow(modifier) {
        options.forEachIndexed { index, (option, title) ->
            val on: Boolean = option == selected
            Segment(selected = on, first = index == 0, onClick = { if (!on) onPick(option) }) {
                SegmentLabel(title, selected = on, enabled = true)
            }
        }
    }
}

/** One of the B / I / U / S toggles, drawn in the style it turns on. */
@Composable
internal fun RowScope.StyleSegment(
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

/** Text alignment glyph: four rules, the short ones placed per [variant]. */
@Composable
internal fun AlignGlyph(color: Color, variant: Int) {
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
 * Interactive once [onDrag] is given, inert otherwise. It draws only from
 * [fraction], never from where the pointer is: what a gesture shows has to come
 * back through the state, or the repaint can go missing mid-drag.
 */
@Composable
internal fun SliderRow(
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

/**
 * Default / Color / Gradient over the palette the picked mode asks for, one
 * settled [onChange] per tap. Null is Default: whatever sits behind this one,
 * which for a slide is its layout's or the deck's and for the deck is the app's
 * own dark gradient.
 *
 * Shared by the slide's Background section and the deck's: the two differ only
 * in where the background they show comes from and where the picked one goes.
 */
@Composable
internal fun BackgroundControls(background: SlideBackground?, onChange: (SlideBackground?) -> Unit) {
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
