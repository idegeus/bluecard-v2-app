package nl.bluecard.engine.pesten

import nl.bluecard.engine.core.CheatWindow
import nl.bluecard.engine.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Laatste kaart!", "Vergeten!" and "Vals!". */
class PsCallsTest {

    private fun table(aHand: String, enforce: Boolean = true) = PsFx.state(
        PsFx.player("a", aHand),
        PsFx.player("b", "3D 4D 6D"),
        PsFx.player("c", "3S 4S 6S"),
        enforce = enforce,
    )

    // ---------------------------------------------------------------- last card

    @Test
    fun `going down to one card without calling it can be caught by anyone`() {
        val s = table("9H 3C").act("a", ps("9H"))
        assertEquals("a", s.forgottenId)
        assertTrue(PsViews.create(s, "c").legal.canCatch)
        assertFalse(PsViews.create(s, "a").legal.canCatch)

        val caught = s.act("c", PsAction.CatchLastCard)
        assertEquals(3, caught.hand("a").size)
        assertNull(caught.forgottenId)
        assertEquals("b", caught.currentPlayerId) // the turn is not affected
        assertEquals(2, caught.events<PsEvent.LastCardForgotten>().single().penaltyCount)
    }

    @Test
    fun `calling last card before or right after the play is safe`() {
        val before = table("9H 3C").act("a", PsAction.CallLastCard).act("a", ps("9H"))
        assertNull(before.forgottenId)

        val after = table("9H 3C").act("a", ps("9H")).act("a", PsAction.CallLastCard)
        assertNull(after.forgottenId)
        assertEquals(PsRejectReason.NOTHING_TO_CATCH.name, after.rejection("b", PsAction.CatchLastCard))
    }

    @Test
    fun `the window closes when the next player draws or passes`() {
        val s = table("9H 3C").act("a", ps("9H")).act("b", PsAction.Draw)
        assertNull(s.forgottenId)
    }

    @Test
    fun `last card can only be called with one or two cards`() {
        assertEquals(PsRejectReason.LAST_CARD_NOT_ALLOWED.name, table("9H 3C 4C").rejection("a", PsAction.CallLastCard))
    }

    @Test
    fun `the announcement is forgotten once the hand grows again`() {
        val s = table("9H 3C").act("a", PsAction.CallLastCard)
        assertTrue(s.p("a").announced)
        val drawn = s.act("a", PsAction.Draw)
        assertFalse(drawn.p("a").announced)
    }

    @Test
    fun `without the rule nothing has to be called`() {
        val s = table("9H 3C").copy(rules = PsHouseRules(lastCardCall = false, enforceRules = true)).act("a", ps("9H"))
        assertNull(s.forgottenId)
    }

    // ---------------------------------------------------------------- Vals!

    @Test
    fun `with enforcement an illegal card is refused`() {
        assertEquals(PsRejectReason.DOES_NOT_FIT.name, table("3C 4C 5C").rejection("a", ps("3C")))
    }

    @Test
    fun `a caught cheat is undone and costs the cheater the penalty and the turn`() {
        val s = table("3C 4C 5C", enforce = false).act("a", ps("3C"))
        assertEquals(Card.of("3C"), s.discardPile.top)
        assertTrue(PsViews.create(s, "c").legal.canChallenge)

        val caught = s.act("c", PsAction.Challenge())
        assertEquals(Card.of("5H"), caught.discardPile.top)
        assertEquals(5, caught.hand("a").size) // 3 own cards back + 2 penalty
        assertEquals("b", caught.currentPlayerId)
        assertNull(caught.lastPlay)
        assertEquals(1, caught.events<PsEvent.CheatCaught>().size)
    }

    @Test
    fun `a cheat that dodged a draw card also pays what was pending`() {
        // (playAfterPenalty does not apply to penalties for cheating: the cheater always loses the turn.)
        val start = table("3C 4C 5C", enforce = false).copy(pendingDraw = 2)
        val caught = start.act("a", ps("3C")).act("b", PsAction.Challenge())
        assertEquals(3 + 2 + 2, caught.hand("a").size)
        assertEquals(0, caught.pendingDraw)
    }

    @Test
    fun `a false accusation costs the accuser the penalty`() {
        val s = table("9H 3C 4C", enforce = false).act("a", ps("9H")).act("c", PsAction.Challenge())
        assertEquals(5, s.hand("c").size)
        assertEquals(Card.of("9H"), s.discardPile.top)
        assertEquals("b", s.currentPlayerId)
        assertEquals(1, s.events<PsEvent.FalseAccusation>().size)
    }

    @Test
    fun `you cannot go out on a cheated card or accuse yourself`() {
        val s = table("3C", enforce = false)
        assertEquals(PsRejectReason.LAST_CARD_MUST_FIT.name, s.rejection("a", ps("3C")))
        val played = table("3C 4C", enforce = false).act("a", ps("3C"))
        assertEquals(PsRejectReason.CANNOT_CHALLENGE_SELF.name, played.rejection("a", PsAction.Challenge()))
    }

    @Test
    fun `a cheat can be called after the next player drew, until the window closes`() {
        val cheated = table("3C 4C 5C", enforce = false).act("a", ps("3C"))
        val drawn = cheated.act("b", PsAction.Draw)
        val handBefore = drawn.p("a").hand.size
        // Called late: no rewind, a draws the penalty.
        val caught = drawn.act("c", PsAction.Challenge())
        val event = caught.events<PsEvent.CheatCaught>().single()
        assertTrue(event.penaltyCount > 0)
        assertEquals(handBefore + event.penaltyCount, caught.p("a").hand.size)
        assertNull(caught.lastPlay)

        val stamped = PsEngine.tick(drawn, 5_000)
        assertEquals(5_000L + CheatWindow.MS, PsEngine.nextTickAt(stamped))
        val closed = PsEngine.tick(stamped, 5_000 + CheatWindow.MS)
        assertNull(closed.lastPlay)
        assertEquals(PsRejectReason.NOTHING_TO_CHALLENGE.name, closed.rejection("c", PsAction.Challenge()))
        assertEquals(1, closed.stats.getValue("a").cheatsUnnoticed)
    }

    @Test
    fun `views never reveal whether a play was legal or what someone drew`() {
        val s = table("3C 4C 5C", enforce = false).act("a", ps("3C"))
        val view = PsViews.create(s, "b")
        assertEquals(Card.of("5H"), view.challenge?.topBefore)
        val drawn = s.act("b", PsAction.Draw)
        assertNull(PsViews.create(drawn, "a").drawnCard)
        assertEquals(Card.of("10C"), PsViews.create(drawn, "b").drawnCard)
        assertTrue(PsViews.create(drawn, "a").myHand.none { it in drawn.hand("b") })
    }
}
