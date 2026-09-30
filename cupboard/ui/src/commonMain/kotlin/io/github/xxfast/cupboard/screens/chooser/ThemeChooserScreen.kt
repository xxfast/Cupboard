package io.github.xxfast.cupboard.screens.chooser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import io.github.xxfast.cupboard.canvas.SlideThumbnail
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import io.github.xxfast.cupboard.document.ThemeCategory
import io.github.xxfast.cupboard.document.preview
import io.github.xxfast.cupboard.theme.ChromeTheme
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LinuxChrome
import io.github.xxfast.cupboard.theme.LocalChromeTheme
import io.github.xxfast.cupboard.theme.LocalChromeTokens
import io.github.xxfast.cupboard.theme.toColorScheme

/**
 * Keynote's File > New wizard, in Material 3 chrome: categories down the side,
 * the themes of the one picked as a grid of thumbnails, Cancel and Create along
 * the bottom.
 *
 * [onCreate] gets the theme and size the last state had picked. What it does
 * next (a bundle, a window) is the shell's, same as [onCancel].
 */
@Composable
fun ThemeChooserScreen(
    viewModel: ThemeChooserViewModel,
    onCancel: () -> Unit,
    onCreate: (Theme, SlideSizePreset) -> Unit,
    theme: ChromeTheme = LinuxChrome,
    modifier: Modifier = Modifier,
) {
    val state: ThemeChooserState by viewModel.states.collectAsState()

    ThemeChooserView(
        state = state,
        onSelectCategory = viewModel::onSelectCategory,
        onSelectTheme = viewModel::onSelectTheme,
        onSelectSize = viewModel::onSelectSize,
        onCancel = onCancel,
        onCreate = { state.selectedTheme?.let { onCreate(it, state.size) } },
        theme = theme,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeChooserView(
    state: ThemeChooserState,
    onSelectCategory: (String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onSelectSize: (SlideSizePreset) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    theme: ChromeTheme = LinuxChrome,
    modifier: Modifier = Modifier,
) {
    val dark: Boolean = isSystemInDarkTheme()
    val tokens: ChromeTokens = if (dark) theme.dark else theme.light
    val focus: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    CompositionLocalProvider(
        LocalChromeTokens provides tokens,
        LocalChromeTheme provides theme,
    ) {
        MaterialTheme(colorScheme = tokens.toColorScheme(dark)) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(tokens.panel)
                    .focusRequester(focus)
                    .focusTarget()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Escape -> onCancel()
                            Key.Enter, Key.NumPadEnter -> onCreate()
                            else -> return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    },
            ) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Sidebar(state, onSelectCategory, tokens)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Header(state, onSelectSize, tokens)
                        state.selectedCategory?.let { category ->
                            ThemeGrid(category, state, onSelectTheme, onCreate, tokens)
                        }
                    }
                }

                HorizontalDivider(color = tokens.div)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    ChooserButton("Cancel", onCancel, tokens.ctrl, tokens.ctrlText)
                    ChooserButton("Create", onCreate, tokens.accent, tokens.accentText)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    state: ThemeChooserState,
    onSelectCategory: (String) -> Unit,
    tokens: ChromeTokens,
) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .fillMaxHeight()
            .background(tokens.chrome)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (category in state.categories) {
            val selected: Boolean = category.name == state.category
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) tokens.hov2 else tokens.chrome)
                    .clickable { onSelectCategory(category.name) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val color: Color = if (selected) tokens.accent else tokens.icon
                CategoryIcon(category.name, color)
                Text(
                    text = category.name,
                    color = tokens.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** A small line glyph per category: a slide for Basic, a cup for anything else. */
@Composable
private fun CategoryIcon(name: String, color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.4.dp.toPx())
        if (name == "Basic") {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, size.height * 0.2f),
                size = Size(size.width, size.height * 0.6f),
                cornerRadius = CornerRadius(2.dp.toPx()),
                style = stroke,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.25f, size.height * 0.42f),
                end = Offset(size.width * 0.6f, size.height * 0.42f),
                strokeWidth = stroke.width,
            )
        } else {
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.1f, size.height * 0.2f),
                size = Size(size.width * 0.6f, size.height * 0.65f),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.5f, size.height * 0.32f),
                size = Size(size.width * 0.4f, size.height * 0.35f),
                style = stroke,
            )
        }
    }
}

@Composable
private fun Header(
    state: ThemeChooserState,
    onSelectSize: (SlideSizePreset) -> Unit,
    tokens: ChromeTokens,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.category,
            color = tokens.text,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )

        var open: Boolean by remember { mutableStateOf(false) }
        Box {
            Text(
                text = "${state.size.label()} ↕",
                color = tokens.accent,
                fontSize = 15.sp,
                modifier = Modifier.clickable { open = true }.padding(4.dp),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (preset in SlideSizePreset.entries) {
                    DropdownMenuItem(
                        text = { Text((if (preset == state.size) "✓ " else "    ") + preset.label()) },
                        onClick = {
                            onSelectSize(preset)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/** "Wide (16:9)" and "Standard (4:3)", the way Keynote words them. */
private fun SlideSizePreset.label(): String = when (this) {
    SlideSizePreset.Widescreen -> "Wide (16:9)"
    SlideSizePreset.Standard -> "Standard (4:3)"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThemeGrid(
    category: ThemeCategory,
    state: ThemeChooserState,
    onSelectTheme: (String) -> Unit,
    onCreate: () -> Unit,
    tokens: ChromeTokens,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(category.themes, key = { it.name }) { item ->
            val selected: Boolean = item.name == state.theme
            // The deck the thumbnail draws: the theme's title slide at the
            // size Create will make, rebuilt only when either changes.
            val deck: Document = remember(item, state.size) { item.preview(state.size) }

            Column(
                modifier = Modifier.combinedClickable(
                    onClick = { onSelectTheme(item.name) },
                    onDoubleClick = {
                        onSelectTheme(item.name)
                        onCreate()
                    },
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val ring: Dp = 3.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (selected) Modifier.border(ring, tokens.accent, RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .padding(ring),
                    ) {
                        deck.slides.firstOrNull()?.let { slide ->
                            SlideThumbnail(
                                slide = slide,
                                layout = deck.layouts.firstOrNull { it.id == slide.layoutId },
                                background = deck.background,
                                slideWidth = deck.slideWidth,
                                slideHeight = deck.slideHeight,
                                width = this@BoxWithConstraints.maxWidth - ring * 2,
                                cornerRadius = 7.dp,
                            )
                        }
                    }
                }

                Text(
                    text = item.name,
                    color = if (selected) tokens.accentText else tokens.text,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) tokens.accent else tokens.panel)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ChooserButton(
    text: String,
    onClick: () -> Unit,
    fill: Color,
    content: Color,
) {
    Text(
        text = text,
        color = content,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
    )
}
