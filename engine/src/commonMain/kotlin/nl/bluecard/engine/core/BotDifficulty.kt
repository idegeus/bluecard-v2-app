package nl.bluecard.engine.core

import kotlinx.serialization.Serializable

@Serializable
enum class BotDifficulty {
    /** Plays a random legal move. */
    EASY,

    /** Uses simple heuristics: saves special cards, sheds low cards first, burns big piles. */
    NORMAL,
}
