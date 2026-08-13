package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

private val SYNCED_GREEN: Color = Color(0xFF4CAF7D)

/** The 30dp mono status strip under the canvas well (Windows/Linux layouts only). */
@Composable
fun EditorStatusBar(
    slideNumber: Int,
    slideCount: Int,
    uiLabel: String,
    modifier: Modifier = Modifier,
) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Column(modifier.fillMaxWidth().background(tokens.panel)) {
        HorizontalDivider(thickness = 1.dp, color = tokens.border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(29.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusText("slide $slideNumber / $slideCount", tokens.faint)
            StatusText("944 × 531 @ 1x", tokens.faint)
            // The one flexible cell: ellipsizes in a narrow window instead of
            // pushing the synced indicator off the end of the row.
            StatusText(uiLabel, tokens.faint, modifier = Modifier.weight(1f))
            StatusText("● synced", SYNCED_GREEN)
        }
    }
}

@Composable
private fun StatusText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
