package nl.bluecard.engine.zweedspesten

import kotlinx.serialization.json.Json
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Rank
import nl.bluecard.engine.zweedspesten.Fx.cards
import nl.bluecard.engine.zweedspesten.Fx.player
import nl.bluecard.engine.zweedspesten.Fx.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Random source that never triggers the bot's occasional random choice. */
private object NoRandom : Random() {
    override fun nextBits(bitCount: Int): Int = Int.MAX_VALUE ushr (32 - bitCount)
}

class ZpViewAndBotTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `view never contains other players hidden cards`() {
        val s = ZpEngine.newGame(
            listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B"), PlayerInfo("c", "C")),
            ZpHouseRules(),
            seed = 11,
        )
        val view = ZpViews.create(s, "a")
        val encoded = json.encodeToString(ZpPlayerView.serializer(), view)
        val hidden = s.players.filter { it.id != "a" }.flatMap { it.hand + it.faceDown } +
            s.p("a").faceDown + s.drawPile.cards
        for (card in hidden) {
            assertFalse("leaked $card", encoded.contains("\"${card.code}\""))
        }
        assertEquals(s.p("a").hand, view.myHand)
        assertEquals(3, view.player("b")!!.handCount)
        assertEquals(3, view.player("b")!!.faceDownCount)
        assertEquals(s.p("b").faceUp, view.player("b")!!.faceUp)
    }

    @Test
    fun `legal moves list exactly the playable cards`() {
        val s = state(player("a", hand = "3C 7D 9S 10H KC"), player("b", hand = "5C"), discard = "8H")
        val legal = ZpViews.create(s, "a").legal
        assertEquals(CardSource.HAND, legal.source)
        assertEquals(cards("9S 10H KC"), legal.playableCards)
        assertTrue(legal.canPickUp) // taking the pile is always allowed
        // Every listed card is accepted by the engine, every other card is rejected.
        for (card in s.p("a").hand) {
            val accepted = ZpEngine.apply(s, "a", ZpAction.Play(listOf(card))) is nl.bluecard.engine.core.ActionResult.Accepted
            assertEquals(card in legal.playableCards, accepted)
        }
    }

    @Test
    fun `pick up is offered when nothing fits`() {
        val s = state(player("a", hand = "3C 4D"), player("b", hand = "5C"), discard = "KH")
        val legal = ZpViews.create(s, "a").legal
        assertTrue(legal.playableCards.isEmpty())
        assertTrue(legal.canPickUp)
        assertTrue(legal.hasAnyMove)
    }

    @Test
    fun `other players get no legal moves while it is not their turn`() {
        val s = state(player("a", hand = "3C"), player("b", hand = "5C"), discard = "2H")
        assertEquals(ZpLegalMoves.NONE, ZpViews.create(s, "b").legal)
        assertTrue(ZpViews.create(s, "a").isMyTurn)
    }

    @Test
    fun `blind phase offers all face-down positions`() {
        val s = state(player("a", down = "3C 4C 5C"), player("b", hand = "5D"), discard = "2H")
        val legal = ZpViews.create(s, "a").legal
        assertEquals(CardSource.FACE_DOWN, legal.source)
        assertEquals(listOf(0, 1, 2), legal.blindIndices)
        assertTrue(legal.playableCards.isEmpty())
    }

    @Test
    fun `view shows the seven requirement and offers no ten on it`() {
        val s = state(player("a", hand = "3C 10D AC"), player("b", hand = "5D"), discard = "4H 7H")
        val view = ZpViews.create(s, "a")
        assertEquals(ZpRequirement(maxValue = 7), view.requirement)
        assertEquals(Fx.cards("3C"), view.legal.playableCards)
        assertTrue(view.legal.canPickUp)
    }

    @Test
    fun `pending actors are all unready players during swap and the current player during play`() {
        val swap = state(
            player("a", hand = "3C", up = "4C", ready = true),
            player("b", hand = "5C", up = "6C", ready = false),
            phase = ZpPhase.SWAPPING,
        )
        assertEquals(setOf("b"), ZpViews.pendingActors(swap))
        val playing = state(player("a", hand = "3C"), player("b", hand = "5C"), current = "b")
        assertEquals(setOf("b"), ZpViews.pendingActors(playing))
    }

    @Test
    fun `normal bot keeps strong cards face up during the swap phase`() {
        var s = state(
            player("a", hand = "10C 2D AH", up = "3C 4D 5H", down = "6C 6D 6H", ready = false),
            player("b", hand = "7C 7D 7H", up = "8C 8D 8H", down = "9C 9D 9H", ready = false),
            phase = ZpPhase.SWAPPING,
        )
        var guard = 0
        while (!s.p("a").ready && guard++ < 10) {
            val action = ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.NORMAL, NoRandom)
            s = s.act("a", action)
        }
        assertEquals(cards("10C 2D AH").toSet(), s.p("a").faceUp.toSet())
        assertEquals(cards("3C 4D 5H"), s.p("a").hand)
    }

    @Test
    fun `normal bot sheds its lowest ordinary card and saves specials`() {
        val s = state(player("a", hand = "2C 5D 9S 10H KC"), player("b", hand = "5C 6C 7C"), discard = "4H")
        val action = ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.NORMAL, NoRandom)
        assertEquals(play("5D"), action)
    }

    @Test
    fun `normal bot burns a big pile`() {
        val s = state(player("a", hand = "10H KC"), player("b", hand = "5C"), discard = "3C 4C 5H 6H 8H QH")
        val action = ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.NORMAL, NoRandom)
        assertEquals(play("10H"), action)
    }

    @Test
    fun `bot plays all copies of a rank and completes four of a kind`() {
        val s = state(player("a", hand = "6C 6D AC"), player("b", hand = "5C"), discard = "6H 6S")
        val action = ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.NORMAL, NoRandom)
        assertEquals(play("6C 6D"), action)
    }

    @Test
    fun `bot picks up or gambles when nothing fits`() {
        val s = state(player("a", hand = "3C 4D"), player("b", hand = "5C"), discard = "KH", draw = "QS")
        assertEquals(ZpAction.PickUp, ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.NORMAL, NoRandom))
        val gamble = s.copy(rules = ZpHouseRules(drawGamble = true))
        assertEquals(ZpAction.Gamble, ZpBot.chooseAction(ZpViews.create(gamble, "a"), BotDifficulty.NORMAL, NoRandom))
    }

    @Test
    fun `bot plays a blind card when only face-down cards are left`() {
        val s = state(player("a", down = "3C 4D"), player("b", hand = "5C"), discard = "KH")
        val action = ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.EASY, Random(1))
        assertTrue(action is ZpAction.PlayBlind)
    }

    @Test
    fun `bot uses skip and lower cards to block a player about to go out`() {
        val rules = ZpHouseRules().withEffect(Rank.EIGHT, ZpEffect.SKIP)
        val s = state(
            player("a", hand = "4C 7D QS"),
            player("b", up = "AC"),
            discard = "3H",
            rules = rules,
        )
        val action = ZpBot.chooseAction(ZpViews.create(s, "a"), BotDifficulty.NORMAL, NoRandom)
        assertEquals(play("7D"), action)
    }
}
