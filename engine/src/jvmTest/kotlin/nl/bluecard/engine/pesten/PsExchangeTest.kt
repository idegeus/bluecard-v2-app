package nl.bluecard.engine.pesten

import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.core.WinnerExchange
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** House rule "winnaar ruilt met de pestkop". */
class PsExchangeTest {

    private val players = listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B"), PlayerInfo("c", "C"))
    private val previous = GameResult(listOf(RankingEntry("b", "B", 1, 0), RankingEntry("a", "A", 2, 3), RankingEntry("c", "C", 3, 5)))

    @Test
    fun `without the rule or without a previous round nothing is exchanged`() {
        assertNull(PsEngine.newGame(players, PsHouseRules(), 1, previous).exchange)
        assertNull(PsEngine.newGame(players, PsHouseRules(winnerSwap = true), 1, null).exchange)
    }

    @Test
    fun `the winner gives a card of choice and gets the loser's best card`() {
        val s = PsFx.state(PsFx.player("a", "3C 4D"), PsFx.player("b", "9H 5S"), PsFx.player("c", "XH 6C"))
            .copy(exchange = WinnerExchange("b", "c"))
        assertEquals(setOf("b"), PsViews.pendingActors(s))
        assertEquals(PsRejectReason.EXCHANGE_PENDING.name, s.rejection("a", ps("3C")))
        assertEquals(PsRejectReason.NOT_THE_WINNER.name, s.rejection("c", PsAction.GiveCard(Card.of("6C"))))
        assertEquals(PsFx.cards("5S 9H"), PsViews.create(s, "b").legal.giveCards)

        val after = s.act("b", PsAction.GiveCard(Card.of("5S")))
        assertNull(after.exchange)
        assertEquals(PsFx.cards("9H XH").toSet(), after.hand("b").toSet())
        assertEquals(PsFx.cards("5S 6C").toSet(), after.hand("c").toSet())
        assertEquals("a", after.currentPlayerId)
        assertEquals(1, after.events<PsEvent.CardsExchanged>().size)
    }

    @Test
    fun `a bot winner gives its least useful card`() {
        val s = PsFx.state(PsFx.player("a", "3C 4D"), PsFx.player("b", "XS 2S 5D"), PsFx.player("c", "9C 6C"))
            .copy(exchange = WinnerExchange("b", "c"))
        val action = PsBot.chooseAction(PsViews.create(s, "b"), nl.bluecard.engine.core.BotDifficulty.NORMAL, kotlin.random.Random(1))
        assertEquals(PsAction.GiveCard(Card.of("5D")), action)
        assertTrue(s.act("b", action).exchange == null)
    }
}
