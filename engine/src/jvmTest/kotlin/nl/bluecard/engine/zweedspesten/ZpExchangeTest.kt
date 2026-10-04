package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.core.WinnerExchange
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** House rule "winnaar ruilt met de pestkop". */
class ZpExchangeTest {

    @Test
    fun `next round starts with the exchange, then the swap phase goes on`() {
        val players = listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B"))
        val previous = GameResult(listOf(RankingEntry("a", "A", 1, 0), RankingEntry("b", "B", 2, 4)))
        val s = ZpEngine.newGame(players, ZpHouseRules(winnerSwap = true), 3, previous)
        assertEquals(WinnerExchange("a", "b"), s.exchange)
        assertEquals(setOf("a"), ZpViews.pendingActors(s))
        assertEquals(ZpRejectReason.EXCHANGE_PENDING.name, s.rejection("b", ZpAction.Ready))

        val give = s.p("a").hand.first()
        val after = s.act("a", ZpAction.GiveCard(give))
        assertNull(after.exchange)
        assertEquals(ZpPhase.SWAPPING, after.phase)
        assertEquals(3, after.p("a").hand.size)
        assertEquals(true, give in after.p("b").hand)
        assertEquals(52, after.allCards().toSet().size)
    }

    @Test
    fun `the loser hands over the best card - a burn card beats an ace`() {
        val s = Fx.state(Fx.player("a", hand = "3C 4D"), Fx.player("b", hand = "AH 10S 5C"), discard = "", phase = ZpPhase.SWAPPING)
            .copy(exchange = WinnerExchange("a", "b"))
        val after = s.act("a", ZpAction.GiveCard(Card.of("3C")))
        assertEquals(Fx.cards("4D 10S").toSet(), after.p("a").hand.toSet())
    }
}
