package nl.bluecard.engine.pesten

import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PsEngineTest {

    private fun twoPlayers(aHand: String, bHand: String = "3D 4D 6D", discard: String = "5H", rules: PsHouseRules = PsHouseRules()) =
        PsFx.state(PsFx.player("a", aHand), PsFx.player("b", bHand), discard = discard, rules = rules)

    private fun threePlayers(aHand: String, discard: String = "5H", rules: PsHouseRules = PsHouseRules()) =
        PsFx.state(PsFx.player("a", aHand), PsFx.player("b", "3D 4D 6D"), PsFx.player("c", "3S 4S 6S"), discard = discard, rules = rules)

    // ---------------------------------------------------------------- setup

    @Test
    fun `new game deals seven cards, 54 cards in total and starts on an ordinary card`() {
        val players = listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B"), PlayerInfo("c", "C"))
        repeat(30) { seed ->
            val s = PsEngine.newGame(players, PsHouseRules(), seed.toLong())
            assertTrue(s.players.all { it.hand.size == 7 })
            assertEquals(54, s.allCards().size)
            assertEquals(54, s.allCards().toSet().size)
            val start = s.discardPile.top!!
            assertFalse("seed $seed started on $start", PsRules.isSpecial(start, s.rules))
        }
    }

    @Test
    fun `setup is validated`() {
        assertEquals(PsRejectReason.TOO_FEW_PLAYERS, PsRules.validateSetup(1, PsHouseRules()))
        assertEquals(PsRejectReason.TOO_MANY_PLAYERS, PsRules.validateSetup(7, PsHouseRules()))
        assertEquals(PsRejectReason.INVALID_HAND_SIZE, PsRules.validateSetup(2, PsHouseRules(handSize = 3)))
        assertEquals(PsRejectReason.NOT_ENOUGH_CARDS, PsRules.validateSetup(6, PsHouseRules(handSize = 8, jokers = 0)))
        assertNull(PsRules.validateSetup(6, PsHouseRules()))
    }

    // ---------------------------------------------------------------- matching

    @Test
    fun `same suit or same value fits, anything else does not`() {
        val s = twoPlayers("9H 5C 9C")
        assertEquals("b", s.act("a", ps("9H")).currentPlayerId)
        assertEquals("b", s.act("a", ps("5C")).currentPlayerId)
        assertEquals(PsRejectReason.DOES_NOT_FIT.name, s.rejection("a", ps("9C")))
    }

    @Test
    fun `jack fits on anything and sets the wished suit`() {
        val s = twoPlayers("JC 3D", bHand = "4D 4S").act("a", ps("JC", Suit.SPADES))
        assertEquals(Suit.SPADES, s.wishedSuit)
        assertEquals(PsRejectReason.WRONG_SUIT.name, s.rejection("b", ps("4D")))
        val after = s.act("b", ps("4S"))
        assertNull(after.wishedSuit)
    }

    @Test
    fun `a jack needs a suit choice`() {
        assertEquals(PsRejectReason.MUST_CHOOSE_SUIT.name, twoPlayers("JC 3D").rejection("a", ps("JC")))
    }

    @Test
    fun `after a joker anything may follow once the draw is settled`() {
        val s = twoPlayers("3D 4D", bHand = "9C 8S", discard = "5H XS")
        assertEquals("b", s.act("a", ps("3D")).currentPlayerId)
    }

    // ---------------------------------------------------------------- draw cards

    @Test
    fun `a two makes the next player draw two, a joker five`() {
        val two = twoPlayers("2H 3D", rules = PsHouseRules(playAfterPenalty = false)).act("a", ps("2H"))
        assertEquals(2, two.pendingDraw)
        val drawn = two.act("b", PsAction.Draw)
        assertEquals(5, drawn.hand("b").size)
        assertEquals(0, drawn.pendingDraw)
        assertEquals("a", drawn.currentPlayerId)

        val joker = twoPlayers("XH 3D").act("a", ps("XH"))
        assertEquals(5, joker.pendingDraw)
    }

    @Test
    fun `draw cards stack and the next player draws the total`() {
        val s = threePlayers("2H 3H")
            .let { it.copy(players = it.players.map { p -> if (p.id == "b") p.copy(hand = PsFx.cards("2D XS 7D")) else p }) }
            .act("a", ps("2H"))
        val stacked = s.act("b", ps("XS"))
        assertEquals(7, stacked.pendingDraw)
        assertEquals("c", stacked.currentPlayerId)
        assertEquals(PsRejectReason.MUST_DRAW_OR_STACK.name, stacked.rejection("c", ps("3S")))
        val drawn = stacked.act("c", PsAction.Draw)
        // Six cards in the draw pile: the discard pile (except the joker on top) is shuffled in to make seven.
        assertEquals(3 + 7, drawn.hand("c").size)
    }

    @Test
    fun `after taking the pak-cards you may start again yourself`() {
        val s = twoPlayers("2H 3D", bHand = "4C 9D").act("a", ps("2H")).act("b", PsAction.Draw)
        assertEquals("b", s.currentPlayerId)
        assertEquals(0, s.pendingDraw)
        assertNull(s.drawnCard)
        assertEquals(1, s.events<PsEvent.PlaysAfterPenalty>().size)
        // Any card that fits on the 2: here a heart or a 2 from the drawn cards, or draw/pass as usual.
        assertEquals("a", s.act("b", PsAction.Draw).act("b", PsAction.Pass).currentPlayerId)
    }

    @Test
    fun `without stacking a pending draw must be taken`() {
        val s = twoPlayers("2H 3D", bHand = "2D 4D", rules = PsHouseRules(stackDraws = false)).act("a", ps("2H"))
        assertEquals(PsRejectReason.MUST_DRAW_OR_STACK.name, s.rejection("b", ps("2D")))
    }

    // ---------------------------------------------------------------- other effects

    @Test
    fun `seven and king let the same player play again`() {
        val seven = threePlayers("7H 3H 4C").act("a", ps("7H"))
        assertEquals("a", seven.currentPlayerId)
        val king = threePlayers("KH 3H 4C").act("a", ps("KH"))
        assertEquals("a", king.currentPlayerId)
    }

    @Test
    fun `eight skips the next player`() {
        val s = threePlayers("8H 3C").act("a", ps("8H"))
        assertEquals("c", s.currentPlayerId)
        assertEquals(listOf("b"), s.events<PsEvent.PlayersSkipped>().single().skippedIds)
    }

    @Test
    fun `ace reverses the direction, with two players it means play again`() {
        val three = threePlayers("AH 3C").act("a", ps("AH"))
        assertEquals(-1, three.direction)
        assertEquals("c", three.currentPlayerId)
        val two = twoPlayers("AH 3C").act("a", ps("AH"))
        assertEquals("a", two.currentPlayerId)
    }

    // ---------------------------------------------------------------- drawing and passing

    @Test
    fun `after drawing only the drawn card may be played, or the player passes`() {
        val s = twoPlayers("3C 9S", discard = "5C").act("a", PsAction.Draw)
        assertEquals(Card.of("10C"), s.drawnCard)
        assertEquals("a", s.currentPlayerId)
        assertEquals(PsRejectReason.ONLY_DRAWN_CARD.name, s.rejection("a", ps("3C")))
        assertEquals(PsRejectReason.ALREADY_DRAWN.name, s.rejection("a", PsAction.Draw))
        assertEquals("b", s.act("a", ps("10C")).currentPlayerId)
        val passed = s.act("a", PsAction.Pass)
        assertEquals("b", passed.currentPlayerId)
        assertNull(passed.drawnCard)
    }

    @Test
    fun `passing needs a draw first`() {
        assertEquals(PsRejectReason.NOT_DRAWN_YET.name, twoPlayers("3C").rejection("a", PsAction.Pass))
    }

    @Test
    fun `an empty draw pile is refilled from the discard pile, keeping the top card`() {
        val s = PsFx.state(PsFx.player("a", "3D"), PsFx.player("b", "4D"), discard = "6C 7C 8C 5H", draw = "")
            .act("a", PsAction.Draw)
        assertEquals(Card.of("5H"), s.discardPile.top)
        assertEquals(1, s.discardPile.size)
        assertEquals(2, s.hand("a").size)
        assertEquals(2, s.drawPile.size)
        assertEquals(1, s.events<PsEvent.DrawPileReshuffled>().size)
    }

    // ---------------------------------------------------------------- going out

    @Test
    fun `going out on an ordinary card wins the game`() {
        val s = twoPlayers("9H", rules = PsHouseRules(lastCardCall = false)).act("a", ps("9H"))
        assertEquals(PsPhase.FINISHED, s.phase)
        assertEquals("a", PsEngine.result(s)!!.winner!!.playerId)
    }

    @Test
    fun `going out on a special card costs a card unless allowed`() {
        val rules = PsHouseRules(lastCardCall = false)
        val s = twoPlayers("8H", rules = rules).act("a", ps("8H"))
        assertEquals(PsPhase.PLAYING, s.phase)
        assertEquals(1, s.hand("a").size)
        assertEquals(1, s.events<PsEvent.SpecialFinishPenalty>().size)

        val allowed = twoPlayers("8H", rules = rules.copy(finishOnSpecial = true)).act("a", ps("8H"))
        assertEquals(PsPhase.FINISHED, allowed.phase)
    }

    @Test
    fun `playing until the last player ranks everybody`() {
        val rules = PsHouseRules(lastCardCall = false, playUntilLast = true)
        val s = threePlayers("9H", rules = rules).act("a", ps("9H"))
        assertEquals(PsPhase.PLAYING, s.phase)
        assertEquals(listOf("a"), s.finishOrder)
        assertEquals("b", s.currentPlayerId)
    }

    @Test
    fun `multiple cards of the same value only when allowed, and their draws add up`() {
        assertEquals(PsRejectReason.MULTIPLE_NOT_ALLOWED.name, twoPlayers("2H 2C 3D").rejection("a", ps("2H 2C")))
        val s = twoPlayers("2H 2C 3D", rules = PsHouseRules(allowMultiple = true)).act("a", ps("2H 2C"))
        assertEquals(4, s.pendingDraw)
    }
}
