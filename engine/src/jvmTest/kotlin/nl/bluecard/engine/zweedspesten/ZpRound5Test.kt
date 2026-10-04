package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.zweedspesten.Fx.player
import nl.bluecard.engine.zweedspesten.Fx.state
import nl.bluecard.engine.core.CheatWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Escalating cheat penalty, no "Vals!" from players who are out, and the limit on laying a picked-up card back. */
class ZpRound5Test {

    @Test
    fun `escalating penalty adds one card from the draw pile for every earlier catch`() {
        val rules = ZpHouseRules(escalatingPenalty = true)
        val base = state(
            player("a", hand = "3C 4C 5C"),
            player("b", hand = "9D 9H"),
            discard = "KH",
            draw = "2S 6S 8S",
            rules = rules,
            enforce = false,
        )
        // First time caught: just the pile.
        val first = base.act("a", play("3C")).act("b", ZpAction.Challenge())
        assertEquals(1, first.p("a").cheatsCaught)
        val sizeFirst = first.p("a").hand.size
        // Caught again (second time): pile plus one card from the draw pile.
        val again = base.copy(players = base.players.map { if (it.id == "a") it.copy(cheatsCaught = 1) else it })
            .act("a", play("3C")).act("b", ZpAction.Challenge())
        assertEquals(2, again.p("a").cheatsCaught)
        assertEquals(sizeFirst + 1, again.p("a").hand.size)
        assertEquals(52, again.allCards().size + 52 - base.allCards().size)
    }

    @Test
    fun `players who are out cannot call cheat`() {
        val s = state(
            player("a", hand = "3C 4C"),
            player("b", hand = "9D"),
            player("c", finished = 1),
            discard = "KH",
            enforce = false,
        ).act("a", play("3C"))
        assertEquals("NOT_PLAYING", s.rejection("c", ZpAction.Challenge()))
        assertFalse(ZpViews.create(s, "c").legal.canChallenge)
        assertTrue(ZpViews.create(s, "b").legal.canChallenge)
    }

    @Test
    fun `a picked up card may only be laid straight back a few times`() {
        val rules = ZpHouseRules(playAfterPickUp = true)
        var s = state(player("a", hand = "AD"), player("b", hand = "AS"), discard = "7H", rules = rules)
        // a picks up the 7 and lays it back, MAX_BOUNCES times.
        repeat(ZpRules.MAX_BOUNCES) {
            s = s.act("a", ZpAction.PickUp)
            assertEquals(nl.bluecard.engine.model.Card.of("7H"), s.p("a").bounceCard)
            s = s.act("a", play("7H"))
            s = s.copy(turn = s.turn.copy(currentPlayerId = "a"))
        }
        s = s.act("a", ZpAction.PickUp)
        assertEquals("REPLAY_LIMIT", s.rejection("a", play("7H")))
        assertFalse(nl.bluecard.engine.model.Card.of("7H") in ZpViews.create(s, "a").legal.playableCards)
        // Another card is still fine.
        s.act("a", play("AD"))
    }

    @Test
    fun `the summary counts cheats, calls and piles`() {
        val base = state(player("a", hand = "3C 4C 5C"), player("b", hand = "9D 9H"), discard = "KH", draw = "2S 6S 8S", enforce = false)
        val caught = base.act("a", play("3C")).act("b", ZpAction.Challenge())
        assertEquals(1, caught.stats["a"]?.cheatsCaught)
        assertEquals(1, caught.stats["b"]?.cheatsSpotted)
        // The undone cheat does not count as a played card.
        assertEquals(0, caught.stats["a"]?.cardsPlayed ?: 0)
        val honest = state(player("a", hand = "AC 4C"), player("b", hand = "9D 9H"), discard = "KH", enforce = false)
            .act("a", play("AC")).act("b", ZpAction.Challenge())
        assertEquals(1, honest.stats["b"]?.falseCalls)
    }

    @Test
    fun `a cheat nobody calls out counts for the ninja`() {
        val base = state(player("a", hand = "3C 4C 5C"), player("b", hand = "9D 9H AH"), discard = "KH", draw = "2S 6S 8S", enforce = false)
        // a cheats (3 on a king); b just plays on and nobody calls it within the window: a got away with it.
        val playedOn = ZpEngine.tick(base.act("a", play("3C")).act("b", play("AH")), 0)
        assertEquals(0, playedOn.stats["a"]?.cheatsUnnoticed ?: 0)
        val escaped = ZpEngine.tick(playedOn, CheatWindow.MS)
        assertEquals(1, escaped.stats["a"]?.cheatsUnnoticed)
        // Caught: no ninja point.
        val caught = base.act("a", play("3C")).act("b", ZpAction.Challenge())
        assertEquals(0, caught.stats["a"]?.cheatsUnnoticed ?: 0)
    }
}
