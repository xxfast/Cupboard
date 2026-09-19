package io.github.xxfast.cupboard.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xxfast.cupboard.document.PlaybackSettings
import io.github.xxfast.cupboard.document.PlaybackType
import io.github.xxfast.cupboard.document.SlideBackground
import io.github.xxfast.cupboard.document.SlideSizePreset
import io.github.xxfast.cupboard.document.Theme
import kotlin.math.roundToInt

/**
 * The Document tab: what belongs to the deck rather than to any one slide.
 *
 * The theme, the slide size, the playback settings and the background every
 * slide falls back to. The slide's own layout, appearance and background moved
 * to Format-with-nothing-selected, which is where Keynote keeps them, so this
 * tab holds exactly the four things that are true of the whole file.
 *
 * Every control is stateless against its argument and commits one settled edit
 * per tap: there is no continuous picker here, so one tap is one history entry.
 */
@Composable
internal fun ColumnScope.DocumentPanel(
    themes: List<Theme>,
    userThemes: List<Theme>,
    themeName: String,
    documentBackground: SlideBackground?,
    slideWidth: Float,
    slideHeight: Float,
    slideSizePreset: SlideSizePreset?,
    playback: PlaybackSettings,
    onChangeTheme: (String) -> Unit,
    onSaveAsTheme: (String) -> Unit,
    onDeleteUserTheme: (String) -> Unit,
    onSetDocumentBackground: (SlideBackground?) -> Unit,
    onSetSlideSize: (Float, Float, Boolean) -> Unit,
    onSetPlayback: (PlaybackSettings) -> Unit,
) {
    PanelBody {
        SectionLabel("THEME")
        // A deck can be on a theme that is no longer in the library: it was
        // saved, used, then deleted. The name still says what the deck is on, so
        // it is offered as an option of its own rather than shown as blank.
        val named: List<Pair<String, String>> = themes.map { it.name to it.name }
        val options: List<Pair<String, String>> =
            if (named.any { (name, _) -> name == themeName }) named
            else named + (themeName to themeName)

        DropdownField(
            label = "Theme",
            value = themeName,
            options = options,
            enabled = true,
            onPick = onChangeTheme,
            modifier = Modifier.fillMaxWidth(),
        )

        // View-local, like the navigator's rename: the name reaches the loop on
        // OK and never before.
        var saving: Boolean by remember { mutableStateOf(false) }
        val isUserTheme: Boolean = userThemes.any { it.name == themeName }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TonalButton(
                label = "Save Theme...",
                enabled = true,
                onClick = { saving = true },
                modifier = Modifier.weight(1f),
            )
            // Only the user's own: a built-in is always there to go back to.
            if (isUserTheme) TonalButton(
                label = "Delete Theme",
                enabled = true,
                onClick = { onDeleteUserTheme(themeName) },
                modifier = Modifier.weight(1f),
            )
        }

        // Prefilled with the current name, so saving over the theme you are on
        // is the default and a new one is a retype.
        if (saving) NameDialog(
            title = "Save Theme",
            confirmLabel = "Save",
            name = themeName,
            onDismiss = { saving = false },
            onCommit = { name ->
                saving = false
                onSaveAsTheme(name)
            },
        )

        PanelDivider()

        SlideSizeSection(
            width = slideWidth,
            height = slideHeight,
            preset = slideSizePreset,
            onSetSlideSize = onSetSlideSize,
        )

        PanelDivider()

        PlaybackSection(playback = playback, onSetPlayback = onSetPlayback)

        PanelDivider()

        SectionLabel("DECK BACKGROUND")
        BackgroundControls(background = documentBackground, onChange = onSetDocumentBackground)
    }
}

/**
 * The PLAYBACK section: what kind of show the deck is.
 *
 * Each kind brings only its own settings: an auto-advance on a normal deck would
 * be a number that means nothing, so the field isn't there to read.
 *
 * Both times are typed in seconds and held in milliseconds, rounded to the whole
 * second the field shows: a kiosk is not set to 4.7 seconds a slide.
 */
@Composable
private fun PlaybackSection(playback: PlaybackSettings, onSetPlayback: (PlaybackSettings) -> Unit) {
    SectionLabel("PLAYBACK")
    DropdownField(
        label = "Type",
        value = playback.type,
        options = PLAYBACK_TYPES,
        enabled = true,
        onPick = { type -> onSetPlayback(playback.copy(type = type)) },
        modifier = Modifier.fillMaxWidth(),
    )

    if (playback.type == PlaybackType.SelfPlaying) {
        NumberField(
            label = "Advance every",
            value = playback.autoAdvanceMs.asSecondsValue(),
            enabled = true,
            onCommit = { seconds -> onSetPlayback(playback.copy(autoAdvanceMs = seconds.asMs())) },
            modifier = Modifier.fillMaxWidth(),
            minimum = 1f,
            unit = Units.Seconds,
        )
        AppearanceRow(
            label = "Loop",
            checked = playback.loop,
            onToggle = { loop -> onSetPlayback(playback.copy(loop = loop)) },
        )
    }

    // 0 is the deck that stays wherever the last person left it, which is a real
    // answer rather than a missing one, so the field says so instead of blanking.
    if (playback.type == PlaybackType.LinksOnly) NumberField(
        label = "Restart after idle (0 = never)",
        value = playback.restartAfterIdleMs.asSecondsValue(),
        enabled = true,
        onCommit = { seconds -> onSetPlayback(playback.copy(restartAfterIdleMs = seconds.asMs())) },
        modifier = Modifier.fillMaxWidth(),
        minimum = 0f,
        unit = Units.Seconds,
    )
}

/** Keynote's three kinds of show, titled the way its menu titles them. */
private val PLAYBACK_TYPES: List<Pair<PlaybackType, String>> = listOf(
    PlaybackType.Normal to "Normal",
    PlaybackType.SelfPlaying to "Self-Playing",
    PlaybackType.LinksOnly to "Links Only",
)

private fun Int.asSecondsValue(): Float = this / 1000f

private fun Float.asMs(): Int = roundToInt() * 1000

/**
 * The SLIDE SIZE section: the named shapes, then Custom for anything else.
 *
 * Nothing here resizes on the pick. A slide size is the deck's shape, so both
 * routes stop and ask the one question that has no default answer: does the
 * content come along. The preset asks it as three buttons, the custom size as a
 * checkbox next to the numbers it is already asking for.
 *
 * The field reads the size rather than the word "Custom" when the deck is on
 * one, since 1600 x 900 says what the deck is and "Custom" doesn't.
 */
@Composable
private fun SlideSizeSection(
    width: Float,
    height: Float,
    preset: SlideSizePreset?,
    onSetSlideSize: (Float, Float, Boolean) -> Unit,
) {
    // View-local, like the theme's Save dialog: the size reaches the loop on the
    // answer and never on the pick.
    var pending: SlideSizePreset? by remember { mutableStateOf(null) }
    var customizing: Boolean by remember { mutableStateOf(false) }

    val options: List<Pair<SlideSizePreset?, String>> =
        SlideSizePreset.entries.map { it to it.title } + (null to "Custom...")

    SectionLabel("SLIDE SIZE")
    DropdownField(
        label = "Size",
        value = preset,
        options = options,
        enabled = true,
        onPick = { picked -> if (picked == null) customizing = true else pending = picked },
        modifier = Modifier.fillMaxWidth(),
        display = if (preset == null) "${width.asWholeNumber()} × ${height.asWholeNumber()}" else null,
    )

    pending?.let { picked ->
        ScaleContentDialog(
            title = picked.title,
            onDismiss = { pending = null },
            onAnswer = { scale ->
                pending = null
                onSetSlideSize(picked.width, picked.height, scale)
            },
        )
    }

    if (customizing) CustomSlideSizeDialog(
        width = width,
        height = height,
        onDismiss = { customizing = false },
        onCommit = { newWidth, newHeight, scale ->
            customizing = false
            onSetSlideSize(newWidth, newHeight, scale)
        },
    )
}

/** The preset's one question, as its two answers plus the way out. */
@Composable
private fun ScaleContentDialog(title: String, onDismiss: () -> Unit, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scale content to fit?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = { Text("Moving this deck to $title.", fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text("Scale") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onAnswer(false) }) { Text("Don't Scale") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * A size typed by hand: the two sides prefilled with the deck's own, and the
 * preset dialog's question as the checkbox under them.
 *
 * The fields are [NumberField]s, so each settles on Enter or on leaving it, and
 * OK sends whatever they have settled on. The bounds are the document's, not
 * this dialog's: [io.github.xxfast.cupboard.document.resized] clamps.
 */
@Composable
private fun CustomSlideSizeDialog(
    width: Float,
    height: Float,
    onDismiss: () -> Unit,
    onCommit: (width: Float, height: Float, scaleContent: Boolean) -> Unit,
) {
    var newWidth: Float by remember { mutableStateOf(width) }
    var newHeight: Float by remember { mutableStateOf(height) }
    var scale: Boolean by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Slide Size", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NumberField(
                        label = "Width",
                        value = newWidth,
                        enabled = true,
                        onCommit = { entered -> newWidth = entered },
                        modifier = Modifier.weight(1f),
                        minimum = 1f,
                    )
                    NumberField(
                        label = "Height",
                        value = newHeight,
                        enabled = true,
                        onCommit = { entered -> newHeight = entered },
                        modifier = Modifier.weight(1f),
                        minimum = 1f,
                    )
                }
                AppearanceRow(
                    label = "Scale content",
                    checked = scale,
                    onToggle = { on -> scale = on },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCommit(newWidth, newHeight, scale) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
