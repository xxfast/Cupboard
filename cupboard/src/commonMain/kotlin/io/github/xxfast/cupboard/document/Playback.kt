package io.github.xxfast.cupboard.document

import kotlinx.serialization.Serializable

/**
 * What kind of show this deck is, Keynote's three.
 *
 * [Normal] is a presenter driving it: keys and clicks advance, and a slide's own
 * [SlideTransition] may still carry it along on its own.
 *
 * [SelfPlaying] runs the deck for a kiosk: every step waits
 * [PlaybackSettings.autoAdvanceMs] and goes. Keys still work, because someone
 * standing at the machine should be able to nudge it.
 *
 * [LinksOnly] hands the deck over to whoever is looking at it: nothing advances
 * but the links on the slides, which makes the deck a small hypertext rather than
 * a sequence. [PlaybackSettings.restartAfterIdleMs] is what puts it back to the
 * first slide once they walk away.
 */
@Serializable
enum class PlaybackType { Normal, SelfPlaying, LinksOnly }

/**
 * How the deck plays, as a property of the deck rather than of a run of it: the
 * same document plays the same way wherever it is opened.
 *
 * [autoAdvanceMs] is how long a [PlaybackType.SelfPlaying] deck holds each step,
 * and means nothing to the other two. [loop] wraps the end of the deck around to
 * its start, and the start back around to the end when the show is walked
 * backwards. [restartAfterIdleMs] is how long a [PlaybackType.LinksOnly] deck
 * waits without a key or a tap before it goes back to the beginning; 0 is off,
 * which is a deck that stays wherever it was left.
 */
@Serializable
data class PlaybackSettings(
    val type: PlaybackType = PlaybackType.Normal,
    val autoAdvanceMs: Int = 5000,
    val loop: Boolean = false,
    val restartAfterIdleMs: Int = 0,
)
