package nl.bluecard.engine.pesten

import nl.bluecard.engine.pesten.PsFx.player
import nl.bluecard.engine.pesten.PsFx.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PsRound5Test {

    @Test
    fun `drawing cancels the last-card call so it must be called again`() {
        val s = state(player("a", hand = "9D", announced = true), player("b", hand = "3C 4C 6C"))
        val after = s.act("a", PsAction.Draw)
        assertFalse(after.player("a")!!.announced)
    }

    @Test
    fun `players who are out cannot call cheat or forgot`() {
        val s = state(
            player("a", hand = "9D 3S"),
            player("b", hand = "3C 4C"),
            player("c", finished = 1),
            enforce = false,
        ).act("a", ps("3S"))
        assertEquals("NOT_PLAYING", s.rejection("c", PsAction.Challenge()))
        assertFalse(PsViews.create(s, "c").legal.canChallenge)
        assertTrue(PsViews.create(s, "b").legal.canChallenge)
    }

    @Test
    fun `escalating penalty costs one more card each time`() {
        val rules = PsHouseRules(escalatingPenalty = true)
        val base = state(player("a", hand = "9D 3S 4S"), player("b", hand = "3C 4C"), rules = rules, enforce = false)
        val first = base.act("a", ps("3S")).act("b", PsAction.Challenge())
        val again = base.copy(players = base.players.map { if (it.id == "a") it.copy(cheatsCaught = 1) else it })
            .act("a", ps("3S")).act("b", PsAction.Challenge())
        assertEquals(first.player("a")!!.hand.size + 1, again.player("a")!!.hand.size)
        assertEquals(2, again.player("a")!!.cheatsCaught)
    }

    @Test
    fun `the summary counts last-card calls and drawn cards`() {
        val s = state(player("a", hand = "9D 3S"), player("b", hand = "3C 4C 6C"))
        val called = s.act("a", PsAction.CallLastCard)
        assertEquals(1, called.stats["a"]?.lastCardCalls)
        val drawn = s.act("a", PsAction.Draw)
        assertEquals(1, drawn.stats["a"]?.cardsDrawn)
    }
}
