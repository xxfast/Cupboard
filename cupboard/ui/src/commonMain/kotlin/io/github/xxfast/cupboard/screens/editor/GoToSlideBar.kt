package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTheme
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/**
 * Go to Slide's quick bar: one field for a slide number, floated from the top
 * centre of whatever it is anchored to. Enter on a number in `1..count` hands
 * it to [onGo]; anything else keeps the bar up for another try. Escape and a
 * click outside are [onDismiss]. A focusable popup, so closing it hands the
 * keys back to the window under it.
 *
 * The number is the navigator row's, so [count] is the deck's
 * `EditorState.presentedSlideCount`: skipped slides have none.
 */
@Composable
internal fun GoToSlideBar(
    count: Int,
    onGo: (number: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val shape: Shape = RoundedCornerShape(LocalChromeTheme.current.rowR)
    var text: String by remember { mutableStateOf("") }
    val focus: FocusRequester = remember { FocusRequester() }

    // Typed into straight away: the shortcut that opened it is the whole ask.
    LaunchedEffect(Unit) { focus.requestFocus() }

    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .shadow(8.dp, shape)
                .background(tokens.panel, shape)
                .border(1.dp, tokens.border, shape)
                .padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .border(1.dp, tokens.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text("Slide number (1–$count)", color = tokens.faint, fontSize = 13.sp)
                }

                BasicTextField(
                    value = text,
                    onValueChange = { typed -> text = typed.filter(Char::isDigit) },
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(tokens.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    val number: Int? = text.toIntOrNull()
                                    if (number != null && number in 1..count) onGo(number)
                                    return@onPreviewKeyEvent true
                                }

                                Key.Escape -> {
                                    onDismiss()
                                    return@onPreviewKeyEvent true
                                }

                                else -> return@onPreviewKeyEvent false
                            }
                        },
                )
            }
        }
    }
}
