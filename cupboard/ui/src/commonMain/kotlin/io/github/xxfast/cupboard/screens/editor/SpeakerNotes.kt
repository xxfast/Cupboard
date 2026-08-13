package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.theme.ChromeTokens
import io.github.xxfast.cupboard.theme.LocalChromeTokens

/** The presenter notes strip docked below the canvas in the stacked layout. */
@Composable
fun SpeakerNotes(notes: String, modifier: Modifier = Modifier) {
    val tokens: ChromeTokens = LocalChromeTokens.current

    Column(modifier.fillMaxWidth().background(tokens.panel)) {
        HorizontalDivider(thickness = 1.dp, color = tokens.border)
        Column(
            modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "SPEAKER NOTES",
                color = tokens.faint,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            if (notes.isNotEmpty()) Text(
                text = notes,
                color = tokens.dim,
                fontSize = 13.5.sp,
                lineHeight = 20.25.sp,
            )
        }
    }
}
