package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.editor.LocalResizeCursors
import io.github.xxfast.cupboard.editor.ResizeDirection
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/** How far into the strip its top edge still takes a resize drag. */
private val ResizeGrip = 6.dp

/**
 * The presenter notes strip docked below the canvas in the stacked layout, and
 * Keynote's in what it does: click anywhere in it for a caret, Return for a new
 * line, Escape to leave, and long notes scroll. Its top edge drags to resize it,
 * between [NotesMinHeight] and [maxHeight], and dragged well under the minimum
 * it hides instead, keeping the height it had.
 *
 * The field keeps its own text and streams it out, the presenter display's
 * reason: the loop answers a keystroke a frame late, and rendering that back
 * would fight the caret. The document is the authority again whenever the caret
 * is elsewhere ([isEditing] false), which is how an undo shows up here.
 */
@Composable
fun SpeakerNotes(
    slideId: String,
    notes: String,
    isEditing: Boolean,
    height: Float,
    maxHeight: Float,
    onBeginEdit: () -> Unit,
    onEdit: (String) -> Unit,
    onEndEdit: () -> Unit,
    onResize: (Float) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current
    val ceiling: Float = maxHeight.coerceAtLeast(NotesMinHeight)
    val shown: Float = height.coerceAtMost(ceiling)

    // The drag reads these as they stand now, not as they were when the
    // gesture's coroutine started.
    val current: State<Float> = rememberUpdatedState(shown)
    val limit: State<Float> = rememberUpdatedState(ceiling)

    Box(modifier.fillMaxWidth().height(shown.dp).background(tokens.panel)) {
        Column(Modifier.fillMaxSize()) {
            HorizontalDivider(thickness = 1.dp, color = tokens.border)

            Column(
                modifier = Modifier
                    .padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "SPEAKER NOTES",
                    color = tokens.faint,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )

                // A new slide is a new field: its own text, and no caret.
                key(slideId) {
                    val focus: FocusManager = LocalFocusManager.current
                    var text: String by remember { mutableStateOf(notes) }
                    var focused: Boolean by remember { mutableStateOf(false) }

                    LaunchedEffect(notes) { if (!isEditing) text = notes }

                    // The editor ended the session itself, on a click elsewhere
                    // or an undo: the caret goes with it.
                    LaunchedEffect(isEditing) { if (!isEditing && focused) focus.clearFocus() }

                    BasicTextField(
                        value = text,
                        onValueChange = { edited ->
                            text = edited
                            onEdit(edited)
                        },
                        textStyle = TextStyle(
                            color = tokens.dim,
                            fontSize = 13.5.sp,
                            lineHeight = 20.25.sp,
                        ),
                        cursorBrush = SolidColor(tokens.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { state ->
                                if (state.isFocused == focused) return@onFocusChanged
                                focused = state.isFocused
                                if (focused) onBeginEdit() else onEndEdit()
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.key != Key.Escape) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyDown) focus.clearFocus()
                                return@onPreviewKeyEvent true
                            },
                    )
                }
            }
        }

        // What the drag shows comes back as [height]: the gesture only does the
        // sums, from where the strip stood when it began.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(ResizeGrip)
                .then(LocalResizeCursors.current?.cursor(ResizeDirection.Vertical) ?: Modifier)
                .pointerInput(Unit) {
                    var start = 0f
                    var asked = 0f

                    detectVerticalDragGestures(
                        onDragStart = {
                            start = current.value
                            asked = start
                        },
                        onDragEnd = {
                            if (asked < NotesMinHeight / 2) {
                                onResize(start)
                                onHide()
                            }
                        },
                        onDragCancel = { onResize(start) },
                    ) { _, dragged ->
                        asked -= dragged / density
                        onResize(asked.coerceIn(NotesMinHeight, limit.value))
                    }
                },
        )
    }
}
