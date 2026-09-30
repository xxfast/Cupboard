package io.github.xxfast.cupboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.prefs.Preferences

/**
 * How this machine plays a show, remembered across shows and launches.
 *
 * The shell's own preferences rather than the editor's: both are about the
 * windows and displays on this machine, and a deck opened somewhere else has no
 * business carrying them there. App-wide rather than per window, like Keynote's,
 * so every open deck plays the same way. Snapshot state over the store, so the
 * Play menu's ticks follow a change made from any window.
 */
internal object PlayPreferences {
    private val store: Preferences = Preferences.userNodeForPackage(PlayPreferences::class.java)

    private var _inWindow: Boolean by mutableStateOf(store.getBoolean("playInWindow", false))
    private var _showOnPrimaryDisplay: Boolean by mutableStateOf(store.getBoolean("showOnPrimaryDisplay", false))

    /** Play > In Window rather than In Full Screen: the editor window becomes the show. */
    var inWindow: Boolean
        get() = _inWindow
        set(value) {
            _inWindow = value
            store.putBoolean("playInWindow", value)
        }

    /**
     * Whether a full-screen show fills the primary display, leaving the other
     * one to the presenter display. Off by default, the talk going to the
     * projector; Swap Displays is what flips it, and the next show starts the
     * way the last one was left.
     */
    var showOnPrimaryDisplay: Boolean
        get() = _showOnPrimaryDisplay
        set(value) {
            _showOnPrimaryDisplay = value
            store.putBoolean("showOnPrimaryDisplay", value)
        }
}
