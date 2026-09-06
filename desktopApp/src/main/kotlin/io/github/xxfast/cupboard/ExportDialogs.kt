package io.github.xxfast.cupboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import io.github.xxfast.cupboard.document.AssetStore
import io.github.xxfast.cupboard.document.Document
import io.github.xxfast.cupboard.export.GifOptions
import io.github.xxfast.cupboard.export.HandoutLayout
import io.github.xxfast.cupboard.export.PdfOptions
import kotlinx.coroutines.CoroutineScope

/**
 * File > Export's dialogs: the options for whatever was picked, then the modal
 * that sits there while the deck is drawn.
 *
 * Composed once per window, next to the editor rather than inside a menu
 * callback, because a menu item can only set state and Compose's dialogs are
 * windows in the tree like any other. Material 2 rather than the shared shell's
 * m3: material3 is an `implementation` dependency of `:cupboard:ui`, so it isn't
 * on this shell's classpath, and three dialogs are not worth adding it for.
 */
@Composable
internal fun ExportDialogs(
    exports: Exports,
    document: Document,
    name: String,
    assets: AssetStore,
    window: ComposeWindow,
) {
    val scope: CoroutineScope = rememberCoroutineScope()

    val start: (ExportRequest) -> Unit = { request ->
        exports.dismiss()
        startExport(request, document, name, assets, window, scope) { exports.progress = it }
    }

    when (exports.asking) {
        null -> Unit

        ExportFormat.Pdf -> PdfOptionsDialog(exports::dismiss) { start(ExportRequest.Pdf(it)) }

        ExportFormat.Png -> PngOptionsDialog(exports::dismiss) { start(ExportRequest.Png(it)) }

        ExportFormat.Gif -> GifOptionsDialog(exports::dismiss) { start(ExportRequest.Gif(it)) }

        // Nothing to ask: the panel is the whole dialog, and it opens on the
        // frame after the pick rather than out of the menu callback, so every
        // format leaves the menu by the same door.
        ExportFormat.Html -> LaunchedEffect(Unit) { start(ExportRequest.Html) }

        ExportFormat.Pptx -> LaunchedEffect(Unit) { start(ExportRequest.Pptx) }

        ExportFormat.Print -> LaunchedEffect(Unit) { start(ExportRequest.Print) }
    }

    exports.progress?.let { ProgressDialog(it) }
}

/** Layout, builds and skipped slides: what a handout is, in three questions. */
@Composable
private fun PdfOptionsDialog(onCancel: () -> Unit, onExport: (PdfOptions) -> Unit) {
    var options: PdfOptions by remember { mutableStateOf(PdfOptions()) }

    OptionsDialog(
        title = "Export as PDF",
        height = 260.dp,
        onCancel = onCancel,
        onExport = { onExport(options) },
    ) {
        Chooser(
            label = "Layout",
            value = options.layout,
            values = HandoutLayout.entries,
            name = HandoutLayout::label,
            onPick = { options = options.copy(layout = it) },
        )
        Switch(
            label = "Every build",
            checked = options.everyBuild,
            onCheck = { options = options.copy(everyBuild = it) },
        )
        Switch(
            label = "Include skipped slides",
            checked = options.skippedSlides,
            onCheck = { options = options.copy(skippedSlides = it) },
        )
    }
}

/** A folder of stills, one per slide or one per step. */
@Composable
private fun PngOptionsDialog(onCancel: () -> Unit, onExport: (Boolean) -> Unit) {
    var everyBuild: Boolean by remember { mutableStateOf(false) }

    OptionsDialog(
        title = "Export as Images",
        height = 190.dp,
        onCancel = onCancel,
        onExport = { onExport(everyBuild) },
    ) {
        Text("One PNG per slide, in a folder of their own.", style = MaterialTheme.typography.body2)
        Switch(label = "Every build", checked = everyBuild, onCheck = { everyBuild = it })
    }
}

/** The widths a GIF is worth writing at: a chat, a README, a slide of its own. */
private val GifWidths: List<Int> = listOf(480, 960, 1280)

/** How long a frame is held, in seconds. Anything shorter is a flicker. */
private val FrameSeconds: List<Float> = listOf(0.5f, 1f, 1.5f, 2f, 3f, 5f)

@Composable
private fun GifOptionsDialog(onCancel: () -> Unit, onExport: (GifOptions) -> Unit) {
    var options: GifOptions by remember { mutableStateOf(GifOptions()) }

    OptionsDialog(
        title = "Export as GIF",
        height = 270.dp,
        onCancel = onCancel,
        onExport = { onExport(options) },
    ) {
        Chooser(
            label = "Width",
            value = options.width,
            values = GifWidths,
            name = { "$it px" },
            onPick = { options = options.copy(width = it) },
        )
        Chooser(
            label = "Seconds per frame",
            value = options.frameDelayMs / 1000f,
            values = FrameSeconds,
            name = { if (it == it.toInt().toFloat()) "${it.toInt()}s" else "${it}s" },
            onPick = { options = options.copy(frameDelayMs = (it * 1000).toInt()) },
        )
        Switch(
            label = "Loop",
            checked = options.loop,
            onCheck = { options = options.copy(loop = it) },
        )
    }
}

/**
 * The deck being drawn, a frame at a time.
 *
 * No cancel: every writer here builds its file whole and hands it back at the
 * end, so there is nothing half-written to stop. The count is pulled on the
 * frame rather than pushed from the worker, which is what [ExportProgress] is
 * shaped for.
 */
@Composable
private fun ProgressDialog(progress: ExportProgress) {
    val done: Int by produceState(0, progress) {
        while (true) {
            value = progress.done
            withFrameNanos { }
        }
    }

    DialogWindow(
        onCloseRequest = { },
        state = rememberDialogState(size = DpSize(340.dp, 150.dp)),
        title = progress.title,
        resizable = false,
    ) {
        Dressed {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Text("Exporting...", style = MaterialTheme.typography.subtitle1)
                LinearProgressIndicator(
                    // A deck of no slides would divide by zero, and an
                    // indeterminate bar is the honest picture of it anyway.
                    progress = if (progress.total > 0) done.toFloat() / progress.total else 0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "$done of ${progress.total} slides",
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}

/** The frame every options dialog shares: its questions, then Cancel and Export. */
@Composable
private fun OptionsDialog(
    title: String,
    height: Dp,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DialogWindow(
        onCloseRequest = onCancel,
        state = rememberDialogState(size = DpSize(360.dp, height)),
        title = title,
        resizable = false,
    ) {
        Dressed {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = onExport) { Text("Export") }
                }
            }
        }
    }
}

/** The dialog's ground, in whichever of the two themes the OS is wearing. */
@Composable
private fun Dressed(content: @Composable () -> Unit) {
    MaterialTheme(colors = if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/** One labelled choice, as the button that opens its list of them. */
@Composable
private fun <T> Chooser(
    label: String,
    value: T,
    values: List<T>,
    name: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open: Boolean by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.caption)
        Box {
            OutlinedButton(
                onClick = { open = true },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.width(200.dp),
            ) {
                Text(name(value), modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for (option in values) DropdownMenuItem(
                    onClick = {
                        open = false
                        onPick(option)
                    },
                ) {
                    Text(name(option))
                }
            }
        }
    }
}

/** A checkbox with its label clickable too, which is the whole row. */
@Composable
private fun Switch(label: String, checked: Boolean, onCheck: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.clickable { onCheck(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheck)
        Text(label, style = MaterialTheme.typography.body2)
    }
}

/** What a layout is called in the dropdown. */
private val HandoutLayout.label: String
    get() = when (this) {
        HandoutLayout.OnePerPage -> "One per page"
        HandoutLayout.TwoPerPage -> "Two per page"
        HandoutLayout.FourPerPage -> "Four per page"
        HandoutLayout.SixPerPage -> "Six per page"
        HandoutLayout.OneWithNotes -> "One with notes"
    }
