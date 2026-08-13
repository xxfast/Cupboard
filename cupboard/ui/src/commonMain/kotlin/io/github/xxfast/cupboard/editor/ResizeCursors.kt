package io.github.xxfast.cupboard.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * How a shell shows a resize cursor while the pointer sits over a selection
 * handle. [direction] null means it sits over none, which is the cue to put the
 * cursor back; so is the returned modifier leaving the tree.
 *
 * A modifier rather than a `PointerIcon`, because the two shells reach the
 * cursor by different routes and only one of them is declarative. Compose for
 * Desktop has AWT cursors and `Modifier.pointerHoverIcon`; Compose's macOS
 * backend only understands its own internal cursor type, so a `PointerIcon`
 * built in the shell would resolve to a plain arrow and the macOS shell drives
 * `NSCursor` itself, returning an empty modifier. Both fit behind this.
 */
fun interface ResizeCursors {
    @Composable
    fun cursor(direction: ResizeDirection?): Modifier
}

/**
 * The shell's resize cursors, null where there are none. Only the two desktop
 * shells fill this in: on iOS, Android and the web there is no cursor to change,
 * and nothing about the canvas changes for leaving it null.
 */
val LocalResizeCursors: ProvidableCompositionLocal<ResizeCursors?> =
    staticCompositionLocalOf { null }
