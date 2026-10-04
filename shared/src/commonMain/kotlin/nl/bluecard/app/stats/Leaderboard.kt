package nl.bluecard.app.stats

import nl.bluecard.multiplayer.session.MatchPlayer
import nl.bluecard.multiplayer.session.MatchRecord

/** Totals of one person, identified by the public id of their phone. */
data class PlayerStats(
    val deviceId: String,
    val name: String,
    val played: Int = 0,
    val wins: Int = 0,
    /** Ended last ("pestkop") or gave up. */
    val losses: Int = 0,
    /** "Vals!" called rightly and wrongly, and how often caught cheating, over all these games. */
    val rightCalls: Int = 0,
    val wrongCalls: Int = 0,
    val caught: Int = 0,
) {
    val hasCheatStats: Boolean get() = rightCalls + wrongCalls + caught > 0

    val winRate: Int get() = if (played == 0) 0 else wins * 100 / played
}

/** Turns the finished games into rankings and personal totals. Pure functions, unit tested. */
object Leaderboard {

    /** A win counts only when the player finished first themselves (not a bot after giving up). */
    fun isWin(player: MatchPlayer): Boolean = player.position == 1 && !player.gaveUp

    fun isLoss(player: MatchPlayer, record: MatchRecord): Boolean = player.gaveUp || player.position == record.players.size

    /**
     * Everybody you played against (games with at least two people), best first: most wins, then win rate, then
     * fewest losses. [gameId] null = all games.
     */
    fun ranking(records: List<MatchRecord>, gameId: String? = null): List<PlayerStats> {
        val stats = linkedMapOf<String, PlayerStats>()
        for (record in records.filter { it.shareable && (gameId == null || it.gameId == gameId) }.sortedBy { it.playedAt }) {
            for (player in record.players) {
                val id = player.deviceId ?: continue
                if (!player.human) continue
                val old = stats[id] ?: PlayerStats(id, player.name)
                stats[id] = old.copy(
                    name = player.name, // newest name wins
                    played = old.played + 1,
                    wins = old.wins + if (isWin(player)) 1 else 0,
                    losses = old.losses + if (isLoss(player, record)) 1 else 0,
                    rightCalls = old.rightCalls + player.rightCalls,
                    wrongCalls = old.wrongCalls + player.wrongCalls,
                    caught = old.caught + player.caught,
                )
            }
        }
        return stats.values.sortedWith(
            compareByDescending<PlayerStats> { it.wins }.thenByDescending { it.winRate }.thenBy { it.losses }.thenBy { it.name },
        )
    }

    /** Your own totals over all games, also against bots. */
    fun mine(records: List<MatchRecord>, deviceId: String, gameId: String? = null): PlayerStats {
        var stats = PlayerStats(deviceId, "")
        for (record in records.filter { gameId == null || it.gameId == gameId }) {
            val me = record.players.firstOrNull { it.deviceId == deviceId && it.human } ?: continue
            stats = stats.copy(
                name = me.name,
                played = stats.played + 1,
                wins = stats.wins + if (isWin(me)) 1 else 0,
                losses = stats.losses + if (isLoss(me, record)) 1 else 0,
                rightCalls = stats.rightCalls + me.rightCalls,
                wrongCalls = stats.wrongCalls + me.wrongCalls,
                caught = stats.caught + me.caught,
            )
        }
        return stats
    }
}
