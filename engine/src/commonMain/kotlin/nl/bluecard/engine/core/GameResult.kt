package nl.bluecard.engine.core

import kotlinx.serialization.Serializable

/** One line of the final standings. [position] starts at 1 (the winner). */
@Serializable
data class RankingEntry(
    val playerId: String,
    val name: String,
    val position: Int,
    val cardsLeft: Int,
    /** Penalty points in games that keep a score (Hartenjagen; lowest wins). Null for the other games. */
    val score: Int? = null,
)

/** Final standings of a finished game, ordered by position, plus everybody's [stats] for the summary. */
@Serializable
data class GameResult(val ranking: List<RankingEntry>, val stats: Map<String, PlayerStats> = emptyMap()) {
    val winner: RankingEntry? get() = ranking.firstOrNull()
    val loser: RankingEntry? get() = if (ranking.size > 1) ranking.last() else null
}
