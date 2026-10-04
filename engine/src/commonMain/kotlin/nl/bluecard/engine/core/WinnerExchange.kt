package nl.bluecard.engine.core

import kotlinx.serialization.Serializable

/**
 * House rule "winnaar ruilt met de pestkop": at the start of the next round the previous winner gives the previous
 * loser one card of their choice and gets the loser's best card in return. Pending until the winner has chosen.
 */
@Serializable
data class WinnerExchange(val winnerId: String, val loserId: String) {
    companion object {
        /** The exchange for a new round, when the rule is on and winner and loser both play again. */
        fun after(previous: GameResult?, enabled: Boolean, playerIds: Collection<String>): WinnerExchange? {
            if (!enabled || previous == null) return null
            val winner = previous.winner?.playerId ?: return null
            val loser = previous.loser?.playerId ?: return null
            if (winner == loser || winner !in playerIds || loser !in playerIds) return null
            return WinnerExchange(winner, loser)
        }
    }
}
