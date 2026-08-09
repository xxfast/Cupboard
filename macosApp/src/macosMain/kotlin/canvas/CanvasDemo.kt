package io.github.xxfast.cupboard.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Stand-in for the slide canvas: exercises rendering (gradient, shapes, text),
 * pointer input (click, drag), state/recomposition, and text input.
 */
@Composable
fun CanvasDemo() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        var clicks by remember { mutableIntStateOf(0) }
        var text by remember { mutableStateOf("") }
        var boxOffset by remember { mutableStateOf(IntOffset(40, 120)) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2A2452), Color(0xFF171930), Color(0xFF101223))
                    )
                )
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Compose canvas — no JVM", color = Color.White, fontSize = 28.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { clicks++ }) { Text("Clicks: $clicks") }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Type here (spike 2)") },
                        singleLine = true,
                    )
                }
            }

            // Draggable "slide element" with the document accent color
            Box(
                modifier = Modifier
                    .offset { boxOffset }
                    .size(150.dp, 76.dp)
                    .background(Color(0xFF7F52FF).copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                    .border(1.5.dp, Color(0xFFA98FFF).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            boxOffset = IntOffset(
                                (boxOffset.x + dragAmount.x).roundToInt(),
                                (boxOffset.y + dragAmount.y).roundToInt(),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("drag me", color = Color(0xFFD9CFFF), fontSize = 15.sp)
            }
        }
    }
}
