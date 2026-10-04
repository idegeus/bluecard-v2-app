package nl.bluecard.app.stats

import nl.bluecard.app.session.GameKind
import nl.bluecard.multiplayer.session.MatchRecord

/** What you have done so far (all your games, also against bots), for unlocking skins and emoji. */
data class Progress(
    val wins: Int = 0,
    val played: Int = 0,
    val winsByGame: Map<String, Int> = emptyMap(),
    val playedByGame: Map<String, Int> = emptyMap(),
    val rightCalls: Int = 0,
    val unnoticed: Int = 0,
) {
    companion object {
        fun of(records: List<MatchRecord>, deviceId: String): Progress {
            var p = Progress()
            for (record in records) {
                val me = record.players.firstOrNull { it.deviceId == deviceId && it.human } ?: continue
                val won = Leaderboard.isWin(me)
                p = p.copy(
                    wins = p.wins + if (won) 1 else 0,
                    played = p.played + 1,
                    winsByGame = if (won) p.winsByGame + (record.gameId to (p.winsByGame[record.gameId] ?: 0) + 1) else p.winsByGame,
                    playedByGame = p.playedByGame + (record.gameId to (p.playedByGame[record.gameId] ?: 0) + 1),
                    rightCalls = p.rightCalls + me.rightCalls,
                    unnoticed = p.unnoticed + me.unnoticed,
                )
            }
            return p
        }
    }
}

/** What it takes to unlock a skin or emoji. */
sealed interface Requirement {
    /** How far you are, and where it is reached. */
    fun current(p: Progress): Int
    val target: Int
    fun met(p: Progress): Boolean = current(p) >= target

    data object Free : Requirement {
        override fun current(p: Progress) = 0
        override val target = 0
    }

    data class Wins(override val target: Int) : Requirement {
        override fun current(p: Progress) = p.wins
    }

    data class Played(override val target: Int) : Requirement {
        override fun current(p: Progress) = p.played
    }

    data class GameWins(val game: GameKind, override val target: Int) : Requirement {
        override fun current(p: Progress) = p.winsByGame[game.id] ?: 0
    }

    data class GamePlayed(val game: GameKind, override val target: Int) : Requirement {
        override fun current(p: Progress) = p.playedByGame[game.id] ?: 0
    }

    data class RightCalls(override val target: Int) : Requirement {
        override fun current(p: Progress) = p.rightCalls
    }

    data class Ninja(override val target: Int) : Requirement {
        override fun current(p: Progress) = p.unnoticed
    }
}
