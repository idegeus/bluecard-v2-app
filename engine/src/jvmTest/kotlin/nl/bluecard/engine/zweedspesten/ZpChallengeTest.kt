package nl.bluecard.engine.zweedspesten

import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.CheatWindow
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.zweedspesten.Fx.cards
import nl.bluecard.engine.zweedspesten.Fx.player
import nl.bluecard.engine.zweedspesten.Fx.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** "Vals!" — calling out cheating when rules are not enforced. */
class ZpChallengeTest {

    private fun cheatTable(draw: String = "") = state(
        player("a", hand = "3C 4D KS"),
        player("b", hand = "5C 6C"),
        player("c", hand = "5D 6D"),
        discard = "8H KH",
        draw = draw,
        enforce = false,
    )

    @Test
    fun `caught cheater takes the cards back plus the pile and loses the turn`() {
        val s = cheatTable(draw = "2S 9S")
        val cheated = s.act("a", play("3C"))
        assertEquals("b", cheated.turn.currentPlayerId)
        assertEquals(cards("4D 9S KS"), cheated.p("a").hand) // refilled from the draw pile
        val caught = cheated.act("c", ZpAction.Challenge())
        assertEquals(cards("3C 4D 8H KH KS"), caught.p("a").hand)
        assertTrue(caught.discardPile.isEmpty)
        assertEquals(cards("2S 9S"), caught.drawPile.cards) // the refill was undone
        assertEquals("b", caught.turn.currentPlayerId)
        assertNull(caught.lastPlay)
        val event = caught.events<ZpEvent.CheatCaught>().single()
        assertEquals("c", event.accuserId)
        assertEquals("a", event.cheaterId)
        assertEquals(3, event.penaltyCount)
        assertEquals(s.allCards().sorted(), caught.allCards().sorted())
    }

    @Test
    fun `a cheated burn is undone too`() {
        val s = state(player("a", hand = "10C 4D"), player("b", hand = "5C"), discard = "4H 7H", enforce = false)
        val burned = s.act("a", play("10C"))
        assertTrue(burned.discardPile.isEmpty)
        assertEquals("a", burned.turn.currentPlayerId) // extra turn from the burn
        val caught = burned.act("b", ZpAction.Challenge())
        assertEquals(cards("4D 4H 7H 10C"), caught.p("a").hand)
        assertTrue(caught.burned.isEmpty())
        assertEquals("b", caught.turn.currentPlayerId)
    }

    @Test
    fun `false accusation by the next player costs the pile and the turn`() {
        val s = cheatTable()
        val legal = s.act("a", play("KS"))
        val wrong = legal.act("b", ZpAction.Challenge())
        assertEquals(cards("5C 6C 8H KH KS"), wrong.p("b").hand)
        assertTrue(wrong.discardPile.isEmpty)
        assertEquals("c", wrong.turn.currentPlayerId)
        assertEquals(3, wrong.events<ZpEvent.FalseAccusation>().single().penaltyCount)
    }

    @Test
    fun `false accusation out of turn costs the pile but keeps the turn order`() {
        val legal = cheatTable().act("a", play("KS"))
        val wrong = legal.act("c", ZpAction.Challenge())
        assertEquals(cards("5D 6D 8H KH KS"), wrong.p("c").hand)
        assertEquals("b", wrong.turn.currentPlayerId)
    }

    @Test
    fun `you cannot accuse yourself`() {
        val cheated = cheatTable().act("a", play("3C"))
        assertEquals("CANNOT_CHALLENGE_SELF", cheated.rejection("a", ZpAction.Challenge()))
    }

    @Test
    fun `a cheat caught after the next player moved costs the pile as it is now`() {
        val s = state(
            player("a", hand = "3C 4D KS"),
            player("b", hand = "AC 6C"),
            player("c", hand = "5D 6D"),
            discard = "8H KH",
            draw = "2S 9S 7S",
            enforce = false,
        )
        val cheated = s.act("a", play("3C"))
        val moved = cheated.act("b", play("AC")) // b plays on, honestly
        assertEquals("c", moved.turn.currentPlayerId)
        assertEquals(2, moved.plays.size)
        val caught = moved.act("c", ZpAction.Challenge(cheated.lastPlay!!.id))
        // a takes the pile as it lies now (8H KH 3C AC); the game is not rewound.
        assertEquals((moved.p("a").hand + cards("8H KH 3C AC")).sorted(), caught.p("a").hand)
        assertTrue(caught.discardPile.isEmpty)
        assertEquals("c", caught.turn.currentPlayerId)
        assertEquals(listOf("b"), caught.plays.map { it.playerId }) // b's play can still be called
        assertEquals(4, caught.events<ZpEvent.CheatCaught>().single().penaltyCount)
        assertEquals(s.allCards().sorted(), caught.allCards().sorted())
    }

    @Test
    fun `without a play id the newest play by someone else is called`() {
        val s = state(
            player("a", hand = "3C 4D"),
            player("b", hand = "AC 6C"),
            player("c", hand = "5D 6D"),
            discard = "KH",
            draw = "2S 9S 7S 6S 5S",
            enforce = false,
        )
        val moved = s.act("a", play("3C")).act("b", play("AC"))
        // c calls on b's honest ace: a false accusation; a's cheat stays open.
        val wrong = moved.act("c", ZpAction.Challenge())
        assertEquals(1, wrong.events<ZpEvent.FalseAccusation>().size)
        assertEquals(listOf("a"), wrong.plays.map { it.playerId })
        // b names a's play and catches it, although the pile is empty now: a draws as many cards as were cheated.
        val caught = wrong.act("b", ZpAction.Challenge(wrong.plays.single().id))
        val event = caught.events<ZpEvent.CheatCaught>().single()
        assertEquals("a", event.cheaterId)
        assertEquals(1, event.penaltyCount)
        assertEquals(s.allCards().sorted(), caught.allCards().sorted())
    }

    @Test
    fun `the window closes after ten seconds and an uncaught cheat counts for the ninja`() {
        val cheated = cheatTable(draw = "2S").act("a", play("3C"))
        val next = cheated.act("b", ZpAction.PickUp)
        val stamped = ZpEngine.tick(next, 1_000)
        assertEquals(1_000L, stamped.lastPlay!!.atMs)
        assertEquals(1_000L + CheatWindow.MS, ZpEngine.nextTickAt(stamped))
        assertTrue(ZpEngine.tick(stamped, 1_000 + CheatWindow.MS - 1) === stamped)
        val expired = ZpEngine.tick(stamped, 1_000 + CheatWindow.MS)
        assertNull(expired.lastPlay)
        assertEquals("NOTHING_TO_CHALLENGE", expired.rejection("c", ZpAction.Challenge()))
        assertEquals(1, expired.stats.getValue("a").cheatsUnnoticed)
        assertNull(ZpEngine.nextTickAt(expired))
    }

    @Test
    fun `nothing can be challenged when rules are enforced`() {
        val s = state(player("a", hand = "KS 4D"), player("b", hand = "5C"), discard = "8H")
        val played = s.act("a", play("KS"))
        assertNull(played.lastPlay)
        assertFalse(ZpViews.create(played, "b").legal.canChallenge)
        assertEquals("NOTHING_TO_CHALLENGE", played.rejection("b", ZpAction.Challenge()))
    }

    @Test
    fun `others see the play but not whether it was legal`() {
        val cheated = cheatTable().act("a", play("3C"))
        val view = ZpViews.create(cheated, "c")
        assertTrue(view.legal.canChallenge)
        assertEquals(cards("3C"), view.challenge!!.cards)
        assertEquals(ZpRequirement(minValue = 13), view.challenge!!.requirementBefore)
        assertFalse(ZpViews.create(cheated, "a").legal.canChallenge)
    }

    @Test
    fun `you cannot go out with a cheated last card`() {
        val s = state(player("a", up = "3C"), player("b", hand = "5C"), discard = "KH", enforce = false)
        assertEquals("LAST_CARD_MUST_FIT", s.rejection("a", play("3C")))
        val fits = state(player("a", up = "AC"), player("b", hand = "5C"), discard = "KH", enforce = false)
        assertEquals(1, fits.act("a", play("AC")).p("a").finishedPosition)
    }

    @Test
    fun `bots call out cheating but not honest plays`() {
        val alwaysCall = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val cheated = cheatTable().act("a", play("3C"))
        val botView = ZpViews.create(cheated, "b")
        assertTrue(ZpBot.chooseAction(botView, BotDifficulty.NORMAL, alwaysCall) is ZpAction.Challenge)

        val honest = cheatTable().act("a", play("KS"))
        val honestView = ZpViews.create(honest, "b")
        assertTrue(ZpBot.chooseAction(honestView, BotDifficulty.NORMAL, alwaysCall) !is ZpAction.Challenge)
    }

    @Test
    fun `state with an open challenge survives serialization`() {
        val cheated = cheatTable(draw = "2S").act("a", play("3C"))
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val text = json.encodeToString(ZpGameState.serializer(), cheated)
        assertEquals(cheated, json.decodeFromString(ZpGameState.serializer(), text))
        assertEquals(Card.of("3C"), cheated.lastPlay!!.cards.single())
    }
}
