package nl.bluecard.app.stats

import nl.bluecard.multiplayer.session.MatchPlayer
import nl.bluecard.multiplayer.session.MatchRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class LeaderboardTest {

    private fun game(id: String, vararg players: MatchPlayer, gameId: String = "pesten", at: Long = id.hashCode().toLong()) =
        MatchRecord(id, gameId, at, players.toList())

    private fun human(device: String, name: String, position: Int, gaveUp: Boolean = false) =
        MatchPlayer(device, name, position, human = true, gaveUp = gaveUp)

    private fun bot(position: Int) = MatchPlayer(null, "Bot", position, human = false)

    @Test
    fun `ranking counts shared games only, best first`() {
        val records = listOf(
            game("1", human("a", "Ivo", 1), human("b", "Anna", 2), at = 1),
            game("2", human("b", "Anna", 1), human("a", "Ivo", 2), at = 2),
            game("3", human("b", "Anna", 1), human("a", "Ivo", 2), bot(3), at = 3),
            // Against bots only: private, not on the shared ranking.
            game("4", human("a", "Ivo", 1), bot(2), at = 4),
        )
        val ranking = Leaderboard.ranking(records)
        assertEquals(listOf("b", "a"), ranking.map { it.deviceId })
        assertEquals(PlayerStats("b", "Anna", played = 3, wins = 2, losses = 1), ranking[0])
        assertEquals(PlayerStats("a", "Ivo", played = 3, wins = 1, losses = 1), ranking[1])
    }

    @Test
    fun `a bot finishing first after you gave up is not your win`() {
        val records = listOf(game("1", human("a", "Ivo", 1, gaveUp = true), human("b", "Anna", 2)))
        val ivo = Leaderboard.ranking(records).first { it.deviceId == "a" }
        assertEquals(0, ivo.wins)
        assertEquals(1, ivo.losses)
    }

    @Test
    fun `your own totals include games against bots, per game`() {
        val records = listOf(
            game("1", human("a", "Ivo", 1), bot(2), at = 1),
            game("2", human("a", "Ivo", 2), bot(1), gameId = "zweeds-pesten", at = 2),
            game("3", human("b", "Anna", 1), human("a", "Ivo 2", 2), at = 3),
        )
        assertEquals(PlayerStats("a", "Ivo 2", played = 3, wins = 1, losses = 2), Leaderboard.mine(records, "a"))
        assertEquals(1, Leaderboard.mine(records, "a", gameId = "zweeds-pesten").played)
    }

    @Test
    fun `newest name is shown`() {
        val records = listOf(
            game("1", human("a", "Ivo", 1), human("b", "Anna", 2), at = 1),
            game("2", human("a", "Ivo de G", 1), human("b", "Anna", 2), at = 2),
        )
        assertEquals("Ivo de G", Leaderboard.ranking(records).first().name)
    }

    @Test
    fun `right and wrong cheat calls add up`() {
        val records = listOf(
            game("1", human("a", "Ivo", 1).copy(rightCalls = 2, wrongCalls = 1), human("b", "Anna", 2).copy(caught = 2)),
            game("2", human("a", "Ivo", 2).copy(rightCalls = 1), human("b", "Anna", 1).copy(wrongCalls = 3), at = 2),
        )
        val ivo = Leaderboard.ranking(records).first { it.deviceId == "a" }
        assertEquals(3, ivo.rightCalls)
        assertEquals(1, ivo.wrongCalls)
        val anna = Leaderboard.mine(records, "b")
        assertEquals(3, anna.wrongCalls)
        assertEquals(2, anna.caught)
    }

    @Test
    fun `head to head counts who finished ahead in games together`() {
        val records = listOf(
            game("1", human("a", "Ivo", 1), human("b", "Anna", 2)),
            game("2", human("a", "Ivo", 3), bot(1), human("b", "Anna", 2), at = 2),
            game("3", human("a", "Ivo", 1), human("c", "Bram", 2), at = 3),
        )
        assertEquals(1 to 1, nl.bluecard.app.ui.components.headToHead(records, "a", "b"))
        assertEquals(0 to 0, nl.bluecard.app.ui.components.headToHead(records, "b", "c"))
    }
}
