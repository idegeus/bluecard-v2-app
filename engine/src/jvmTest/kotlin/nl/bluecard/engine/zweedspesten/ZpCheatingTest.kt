package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.zweedspesten.Fx.cards
import nl.bluecard.engine.zweedspesten.Fx.player
import nl.bluecard.engine.zweedspesten.Fx.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rules not enforced (the default): like a real table, any card may be laid down. */
class ZpCheatingTest {

    @Test
    fun `rules are not enforced by default`() {
        assertFalse(ZpHouseRules().enforceRules)
        assertEquals(ZpPreset.CLASSIC, ZpPreset.matching(ZpHouseRules(enforceRules = true)))
    }

    @Test
    fun `a lower card and a ten on a seven are accepted when rules are not enforced`() {
        val onKing = state(player("a", hand = "3C KD"), player("b", hand = "5C"), discard = "KH", enforce = false)
        assertEquals(cards("3C"), onKing.act("a", play("3C")).discardPile.cards.takeLast(1))

        val onSeven = state(player("a", hand = "10C AD"), player("b", hand = "5C"), discard = "7H", enforce = false)
        val burned = onSeven.act("a", play("10C"))
        assertTrue(burned.discardPile.isEmpty) // effects still work
        onSeven.act("a", play("AD"))
    }

    @Test
    fun `turn order, ownership, same rank and sources are still enforced`() {
        val s = state(player("a", hand = "3C 4D", up = "AS"), player("b", hand = "5C"), discard = "KH", enforce = false)
        assertEquals("NOT_YOUR_TURN", s.rejection("b", play("5C")))
        assertEquals("CARD_NOT_AVAILABLE", s.rejection("a", play("5C")))
        assertEquals("MIXED_RANKS", s.rejection("a", play("3C 4D")))
        assertEquals("WRONG_SOURCE", s.rejection("a", play("AS")))
    }

    @Test
    fun `view offers every card but marks the fitting ones`() {
        val s = state(player("a", hand = "3C 9D KS"), player("b", hand = "5C"), discard = "8H", enforce = false)
        val legal = ZpViews.create(s, "a").legal
        assertEquals(cards("3C 9D KS"), legal.playableCards)
        assertEquals(cards("9D KS"), legal.fittingCards)
        assertTrue(legal.canPickUp)
    }

    @Test
    fun `blind cards are still judged by the rules`() {
        val s = state(player("a", down = "3C"), player("b", hand = "5C"), discard = "KH", enforce = false)
        val after = s.act("a", ZpAction.PlayBlind(0))
        assertEquals(cards("3C KH"), after.p("a").hand)
    }

    @Test
    fun `bots never cheat`() {
        val s = state(player("a", hand = "3C 4C 5C"), player("b", hand = "5D"), discard = "KH", enforce = false)
        repeat(20) { seed ->
            for (difficulty in BotDifficulty.entries) {
                val action = ZpBot.chooseAction(ZpViews.create(s, "a"), difficulty, Random(seed))
                assertEquals(ZpAction.PickUp, action)
            }
        }
    }
}
