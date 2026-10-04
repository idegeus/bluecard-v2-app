package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.model.Card
import nl.bluecard.engine.zweedspesten.Fx.cards
import nl.bluecard.engine.zweedspesten.Fx.player
import nl.bluecard.engine.zweedspesten.Fx.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** House rule "Na pakken zelf beginnen". */
class ZpPlayAfterPickUpTest {

    private val rule = ZpHouseRules(playAfterPickUp = true)

    @Test
    fun `off by default, on in the TIS preset`() {
        assertFalse(ZpHouseRules().playAfterPickUp)
        assertEquals(ZpPreset.TIS, ZpPreset.matching(rule))
        assertEquals(ZpPreset.TIS, ZpPreset.matching(rule.copy(enforceRules = true)))
        // TIS is classic plus this one rule.
        assertEquals(ZpPreset.CLASSIC.rules, ZpPreset.TIS.rules.copy(playAfterPickUp = false))
    }

    @Test
    fun `whoever takes the pile starts the new one`() {
        val s = state(player("a", hand = "3C 4C"), player("b", hand = "5C"), discard = "5H KH", rules = rule)
        val after = s.act("a", ZpAction.PickUp)
        assertEquals("a", after.turn.currentPlayerId)
        assertTrue(after.discardPile.isEmpty)
        assertEquals(1, after.events<ZpEvent.StartsAfterPickUp>().size)
        // Any card goes on the empty pile, then the turn passes as usual.
        val played = after.act("a", play("3C"))
        assertEquals("b", played.turn.currentPlayerId)
    }

    @Test
    fun `picking up again right away is not possible`() {
        val s = state(player("a", hand = "3C"), player("b", hand = "5C"), discard = "KH", rules = rule)
        val after = s.act("a", ZpAction.PickUp)
        assertEquals("PILE_EMPTY", after.rejection("a", ZpAction.PickUp))
    }

    @Test
    fun `also after a blind card that did not fit`() {
        val s = state(player("a", down = "3C KD"), player("b", hand = "5C"), discard = "6H", rules = rule)
        val after = s.act("a", ZpAction.PlayBlind(0))
        assertEquals(cards("3C 6H"), after.p("a").hand)
        assertEquals("a", after.turn.currentPlayerId)
    }

    @Test
    fun `also after a gamble that did not fit`() {
        val s = state(
            player("a", hand = "3C 4C 5C"),
            player("b", hand = "5D"),
            discard = "8H",
            draw = "KD 4S",
            rules = rule.copy(drawGamble = true),
        )
        val after = s.act("a", ZpAction.Gamble)
        assertEquals("a", after.turn.currentPlayerId)
    }

    @Test
    fun `without the rule the turn passes`() {
        val s = state(player("a", hand = "3C"), player("b", hand = "5C"), discard = "KH")
        assertEquals("b", s.act("a", ZpAction.PickUp).turn.currentPlayerId)
    }

    @Test
    fun `cheating penalties still cost the turn`() {
        val cheat = state(
            player("a", hand = "3C 4D KS"),
            player("b", hand = "5C 6C"),
            player("c", hand = "5D 6D"),
            discard = "8H KH",
            rules = rule,
            enforce = false,
        ).act("a", play("3C"))
        val caught = cheat.act("c", ZpAction.Challenge())
        assertEquals("b", caught.turn.currentPlayerId)

        val honest = state(
            player("a", hand = "KS 4D"),
            player("b", hand = "5C 6C"),
            player("c", hand = "5D 6D"),
            discard = "8H",
            rules = rule,
            enforce = false,
        ).act("a", play("KS"))
        val wrong = honest.act("b", ZpAction.Challenge())
        assertEquals("c", wrong.turn.currentPlayerId)
        assertEquals(Card.of("KS"), wrong.p("b").hand.last())
    }
}
