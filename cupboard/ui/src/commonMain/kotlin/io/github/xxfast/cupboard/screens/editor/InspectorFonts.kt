package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.xxfast.cupboard.canvas.isFontAvailable
import io.github.xxfast.cupboard.canvas.systemFontFamilies
import io.github.xxfast.cupboard.document.DeckFont
import io.github.xxfast.cupboard.document.TextRangeStyle
import io.github.xxfast.cupboard.document.TextFont
import io.github.xxfast.cupboard.document.fontDisplayName
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/** The generic families every platform has, which is the picker's Standard section. */
private val StandardFonts: List<TextFont> = listOf(TextFont.Sans, TextFont.Serif, TextFont.Monospace)

/** How wide the font list opens: the inspector's field, edge to edge. */
private val FontMenuWidth = 250.dp

/** One line of the font list: a section's title, or a face to pick. */
private sealed interface FontRow {
    data class Header(val title: String) : FontRow

    /** A face by name, from the deck or the system: a pick sets `fontName`. */
    data class Named(val family: String) : FontRow

    /** One of the three generic families: a pick clears `fontName`. */
    data class Generic(val font: TextFont) : FontRow
}

/**
 * The Text section's font field: what the text is set in ([style], the selected
 * words or the whole box, [MULTIPLE] when they disagree), and the list of
 * everything it could be set in under it.
 *
 * The list is the deck's families, the three generic ones, then every family the
 * system has, with a filter on top because the last section runs to hundreds.
 * Rows are plain text rather than drawn in their own face: one font load per
 * row is hundreds of loads to open a menu.
 *
 * A named face [TextFont] has an entry for (Helvetica Neue, Canela) has no
 * section of its own. Installed, it is under Installed by name like any other,
 * and a box still carrying the entry just reads as it.
 *
 * The open flag and the filter are view-local, like every other menu here: what
 * the user is typing into a list is no fact about the document. [label] is the
 * field's floating one, empty under a header that already says Font.
 */
@Composable
internal fun FontPicker(
    style: TextRangeStyle,
    deckFamilies: List<String>,
    enabled: Boolean,
    onPickName: (String) -> Unit,
    onPickGeneric: (TextFont) -> Unit,
    onAddFontFile: (() -> Unit)?,
    label: String = "Font",
) {
    var open: Boolean by remember { mutableStateOf(false) }

    Box {
        OutlinedField(
            label = label,
            value = style.fontDisplayName ?: MULTIPLE,
            trailing = "▾",
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { open = true },
        )

        if (open) Popup(
            popupPositionProvider = BelowOrAbove,
            onDismissRequest = { open = false },
            properties = PopupProperties(focusable = true),
        ) {
            FontMenu(
                // The name a pick of a named face ticks: the text's own, else the
                // system name of the entry it carries. The generic three have
                // none, and text in more than one face ticks nothing.
                tickedName = style.fontName ?: style.fontFamily?.familyName,
                tickedGeneric = style.fontFamily.takeIf { style.fontName == null },
                deckFamilies = deckFamilies,
                onPick = { row ->
                    open = false
                    when (row) {
                        is FontRow.Named -> onPickName(row.family)
                        is FontRow.Generic -> onPickGeneric(row.font)
                        is FontRow.Header -> Unit
                    }
                },
                onAddFontFile = onAddFontFile?.let { add ->
                    {
                        open = false
                        add()
                    }
                },
            )
        }
    }
}

/**
 * The open list: the filter, the three sections, and Add Font File pinned under
 * them so it is one click away however long Installed runs.
 *
 * The system's families are read once per opening rather than per keystroke or
 * per recomposition: listing them asks the font manager for every family it has.
 * Enter in the filter picks the first face left in the list.
 */
@Composable
private fun FontMenu(
    tickedName: String?,
    tickedGeneric: TextFont?,
    deckFamilies: List<String>,
    onPick: (FontRow) -> Unit,
    onAddFontFile: (() -> Unit)?,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val installed: List<String> = remember { systemFontFamilies() }
    var filter: String by remember { mutableStateOf("") }
    val rows: List<FontRow> = remember(deckFamilies, installed, filter) {
        fontRows(deckFamilies, installed, filter)
    }
    val focus: FocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(
        shape = MenuDefaults.shape,
        color = MenuDefaults.containerColor,
        tonalElevation = MenuDefaults.TonalElevation,
        shadowElevation = MenuDefaults.ShadowElevation,
        modifier = Modifier.width(FontMenuWidth).padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .height(32.dp)
                    .border(1.dp, tokens.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (filter.isEmpty()) Text("Filter", color = tokens.faint, fontSize = 13.sp)
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onPreviewKeyEvent { event ->
                            val entered: Boolean = event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            if (!entered) return@onPreviewKeyEvent false

                            rows.firstOrNull { it !is FontRow.Header }?.let(onPick)
                            return@onPreviewKeyEvent true
                        },
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 6.dp)) {
                if (rows.isEmpty()) item {
                    Text(
                        text = "No fonts match.",
                        color = tokens.subtle,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }

                items(rows) { row ->
                    when (row) {
                        is FontRow.Header -> Text(
                            text = row.title.uppercase(),
                            color = tokens.subtle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                        )

                        is FontRow.Named -> FontMenuItem(
                            title = row.family,
                            ticked = row.family.equals(tickedName, ignoreCase = true),
                            onClick = { onPick(row) },
                        )

                        is FontRow.Generic -> FontMenuItem(
                            title = row.font.displayName,
                            ticked = row.font == tickedGeneric,
                            onClick = { onPick(row) },
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = tokens.div,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            FontMenuItem(
                title = "Add Font File...",
                ticked = false,
                enabled = onAddFontFile != null,
                onClick = { onAddFontFile?.invoke() },
            )
        }
    }
}

/** A row of [FontMenu], dressed like [DropdownField]'s with the tick on the left. */
@Composable
private fun FontMenuItem(title: String, ticked: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    DropdownMenuItem(
        text = {
            Text(
                text = title,
                fontSize = 13.sp,
                color = if (enabled) tokens.text else tokens.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Box(Modifier.width(14.dp)) {
                if (ticked) Text("✓", color = tokens.accent, fontSize = 12.sp)
            }
        },
        enabled = enabled,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.height(34.dp),
    )
}

/**
 * The list as it stands under [filter]: each section's faces that contain it,
 * case aside, under their header, and a section with none left dropped whole.
 */
private fun fontRows(deck: List<String>, installed: List<String>, filter: String): List<FontRow> {
    val query: String = filter.trim()

    fun section(title: String, rows: List<FontRow>): List<FontRow> =
        if (rows.isEmpty()) emptyList() else listOf(FontRow.Header(title)) + rows

    return section("In This Deck", deck.filter { it.contains(query, ignoreCase = true) }.map(FontRow::Named)) +
        section(
            "Standard",
            StandardFonts.filter { it.displayName.contains(query, ignoreCase = true) }.map(FontRow::Generic),
        ) +
        section("Installed", installed.filter { it.contains(query, ignoreCase = true) }.map(FontRow::Named))
}

/**
 * Under the anchor where the list fits there, over it where it doesn't, and
 * kept inside the window either way: the Text section can sit anywhere in a
 * scrolled inspector, and a list hanging off the bottom can't be picked from.
 */
private object BelowOrAbove : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x: Int = anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val fitsBelow: Boolean = anchorBounds.bottom + popupContentSize.height <= windowSize.height
        val y: Int =
            if (fitsBelow) anchorBounds.bottom
            else maxOf(0, anchorBounds.top - popupContentSize.height)
        return IntOffset(x, y)
    }
}

/**
 * The line under the picker for a box set in a face that resolves nowhere here:
 * neither the deck nor the system has it, so it is drawing in its fallback.
 *
 * The fallback named is what actually draws: the box's [TextFont] entry where
 * the system has that face, its generic family otherwise. Nothing for a box set
 * by its entry alone, which always draws in something it names, and nothing
 * for text in more than one face.
 */
@Composable
internal fun MissingFontNotice(style: TextRangeStyle, deckFamilies: List<String>) {
    val name: String = style.fontName ?: return
    val available: Boolean = remember(name, deckFamilies) { isFontAvailable(name, deckFamilies) }
    if (available) return

    val entry: TextFont = style.fontFamily ?: return
    val fallback: String = remember(entry) {
        val entryInstalled: Boolean = entry.familyName?.let { isFontAvailable(it, emptyList()) } ?: false
        if (entryInstalled) entry.displayName else entry.generic.displayName
    }

    Text(
        text = "Not installed. Showing $fallback.",
        color = LocalChromeTokens.current.subtle,
        fontSize = 11.5.sp,
    )
}

/**
 * The Document tab's FONTS section: every cut the deck carries in its bundle,
 * each with the button that drops it, and Add Font File under them.
 *
 * Sorted by family, then weight, then upright before italic, so a family's cuts
 * sit together whatever order they were added in. Removing one leaves the text
 * set in its family alone; it falls back like any face that isn't there.
 */
@Composable
internal fun DeckFontsSection(
    fonts: List<DeckFont>,
    onRemove: (assetId: String) -> Unit,
    onAddFontFile: (() -> Unit)?,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val sorted: List<DeckFont> = remember(fonts) {
        fonts.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, DeckFont::family)
                .thenBy(DeckFont::weight)
                .thenBy(DeckFont::italic),
        )
    }

    SectionLabel("FONTS")

    if (sorted.isEmpty()) Text(
        text = "Fonts added here travel with the deck.",
        color = tokens.subtle,
        fontSize = 11.5.sp,
    ) else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (font in sorted) DeckFontRow(label = font.label, onRemove = { onRemove(font.assetId) })
    }

    TonalButton(
        label = "Add Font File...",
        enabled = onAddFontFile != null,
        onClick = { onAddFontFile?.invoke() },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One cut in [DeckFontsSection]: its name and the remove button, dressed like a link row. */
@Composable
private fun DeckFontRow(label: String, onRemove: () -> Unit) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(tokens.rowBg)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokens.text,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "✕", color = tokens.subtle, fontSize = 12.sp)
        }
    }
}

/** "Inter Bold", "Inter Light, Italic": the cut as a font menu names it. */
private val DeckFont.label: String
    get() = buildString {
        append(family)
        append(' ')
        append(weightName(weight))
        if (italic) append(", Italic")
    }

/** The OpenType name for a weight class, and the number for any other. */
private fun weightName(weight: Int): String = when (weight) {
    100 -> "Thin"
    200 -> "ExtraLight"
    300 -> "Light"
    400 -> "Regular"
    500 -> "Medium"
    600 -> "SemiBold"
    700 -> "Bold"
    800 -> "ExtraBold"
    900 -> "Black"
    else -> "$weight"
}
