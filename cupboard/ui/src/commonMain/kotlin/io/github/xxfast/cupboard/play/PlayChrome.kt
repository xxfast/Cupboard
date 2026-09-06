package io.github.xxfast.cupboard.play

import androidx.compose.ui.graphics.Color

// The chrome the play layer wears, fixed rather than themed: the presenter
// display is a second window on a second screen and the in-show overlays sit on
// top of a dark slide, so both stay dark whatever the editor is wearing.
internal val PresenterBackground: Color = Color(0xFF111216)
internal val PresenterPanel: Color = Color(0xFF17181C)
internal val PresenterControl: Color = Color(0xFF2B2D35)
internal val PresenterBorder: Color = Color(0xFF33363D)
internal val PresenterText: Color = Color(0xFFE8E8EA)
internal val PresenterDim: Color = Color(0xFFA0A0A8)
internal val PresenterFaint: Color = Color(0xFF6E6E76)
internal val PresenterAccent: Color = Color(0xFF7F52FF)
