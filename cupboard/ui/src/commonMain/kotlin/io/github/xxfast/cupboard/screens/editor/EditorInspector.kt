package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/**
 * The 282dp M3 inspector: Format / Animate / Slide tabs over static placeholder
 * bodies mirroring the design's Linux mock. Real editing panels land in later
 * phases; clicking the active tab does nothing (close-on-reclick is macOS-only).
 *
 * The "Slide" tab is [InspectorTab.Document]: same pane, per-platform label.
 */
@Composable
fun EditorInspector(
    tab: InspectorTab,
    onSelectTab: (InspectorTab) -> Unit,
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
                InspectorTab.Format -> FormatPanel()
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

@Composable
private fun FormatPanel() {
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

@Composable
private fun RowScope.Segment(selected: Boolean, first: Boolean, content: @Composable () -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(Modifier.weight(1f).height(38.dp)) {
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(tokens.outline))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (selected) tokens.segOn else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
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

/** A static slider mock: filled track up to [fraction], knob riding the seam. */
@Composable
private fun SliderRow(fraction: Float, valueLabel: String, modifier: Modifier = Modifier) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(20.dp)) {
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
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.accent),
            )
            // Alignment bias maps 0..1 along the track to -1..1.
            Box(
                Modifier
                    .align(BiasAlignment(fraction * 2f - 1f, 0f))
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tokens.accent),
            )
        }
        Text(
            text = valueLabel,
            color = tokens.text,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

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
