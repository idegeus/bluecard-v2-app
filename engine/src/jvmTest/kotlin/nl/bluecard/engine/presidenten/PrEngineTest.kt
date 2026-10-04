package nl.bluecard.engine.presidenten

import kotlinx.serialization.json.Json
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class PrEngineTest {

    private val four = (1..4).map { PlayerInfo("p$it", "Speler $it") }

    private fun cards(vararg codes: String) = codes.map { Card.of(it) }

    /** A game in progress with chosen hands; p1 to play. */
    private fun table(rules: PrHouseRules = PrHouseRules(), vararg hands: List<Card>) = PrGameState(
        rules = rules,
        players = hands.mapIndexed { i, h -> PrPlayerState("p${i + 1}", "Speler ${i + 1}", h.sortedWith(PrRules.handOrder(rules))) },
        currentPlayerId = "p1",
    )

    private fun PrGameState.act(id: String, action: PrAction): PrGameState = when (val r = PrEngine.apply(this, id, action)) {
        is ActionResult.Accepted -> r.state
        is ActionResult.Rejected -> fail("$action by $id rejected: ${r.reason}") as Nothing
    }

    private fun PrGameState.rejected(id: String, action: PrAction): String =
        (PrEngine.apply(this, id, action) as? ActionResult.Rejected)?.reason ?: fail("$action by $id accepted") as Nothing

    @Test
    fun `all cards are dealt and the holder of the three of clubs starts`() {
        val s = PrEngine.newGame(four, PrHouseRules(), 42)
        assertEquals(52, s.allCards().toSet().size)
        assertEquals(13, s.players.first().hand.size)
        val starter = s.player(s.currentPlayerId!!)!!
        assertTrue(Card.of("3C") in starter.hand)
    }

    @Test
    fun `sets must match the count and go higher`() {
        val s = table(
            PrHouseRules(),
            cards("5C", "5D", "9H"), cards("4C", "4D", "KH"), cards("6C", "6D", "QS"), cards("7C", "8D", "JS"),
        )
        val a = s.act("p1", PrAction.Play(cards("5C", "5D")))
        assertEquals(PrRejectReason.WRONG_COUNT.name, a.rejected("p2", PrAction.Play(cards("KH"))))
        assertEquals(PrRejectReason.TOO_LOW.name, a.rejected("p2", PrAction.Play(cards("4C", "4D"))))
        assertEquals(PrRejectReason.MIXED_RANKS.name, a.rejected("p2", PrAction.Play(cards("4C", "KH"))))
        val b = a.act("p2", PrAction.Pass).act("p3", PrAction.Play(cards("6C", "6D")))
        assertEquals("p4", b.currentPlayerId)
    }

    @Test
    fun `when everybody passes the last player wins the trick and leads`() {
        val s = table(PrHouseRules(), cards("9C", "3D"), cards("4C", "5H"), cards("6C", "7S"), cards("7C", "8D"))
        val t = s.act("p1", PrAction.Play(cards("9C"))).act("p2", PrAction.Pass).act("p3", PrAction.Pass).act("p4", PrAction.Pass)
        assertTrue(t.plays.isEmpty())
        assertEquals("p1", t.currentPlayerId)
        assertEquals("p1", t.lastTrickWinnerId)
        assertEquals(PrRejectReason.CANNOT_PASS_LEAD.name, t.rejected("p1", PrAction.Pass))
    }

    @Test
    fun `a two cannot be beaten and ends the trick at once`() {
        val s = table(PrHouseRules(), cards("2C", "3D"), cards("4C", "5H"), cards("6C", "7S"), cards("7C", "8D"))
        val t = s.act("p1", PrAction.Play(cards("2C")))
        assertTrue(t.plays.isEmpty())
        assertEquals("p1", t.currentPlayerId)
    }

    @Test
    fun `passing is final for the trick by default but not with the other rule`() {
        val hands = arrayOf(cards("5C", "3D", "AC"), cards("6C", "8H", "4S"), cards("7C", "4D", "5S"), cards("9C", "4H", "6S"))
        val s = table(PrHouseRules(), *hands)
        val t = s.act("p1", PrAction.Play(cards("5C"))).act("p2", PrAction.Pass).act("p3", PrAction.Play(cards("7C")))
            .act("p4", PrAction.Play(cards("9C")))
        // p2 passed earlier and sits out: p1 is next.
        assertEquals("p1", t.currentPlayerId)
        val open = table(PrHouseRules(passIsFinal = false), *hands)
        val u = open.act("p1", PrAction.Play(cards("5C"))).act("p2", PrAction.Pass).act("p3", PrAction.Play(cards("7C")))
            .act("p4", PrAction.Play(cards("9C"))).act("p1", PrAction.Pass)
        assertEquals("p2", u.currentPlayerId)
    }

    @Test
    fun `jokers fill up a set`() {
        val rules = PrHouseRules(jokers = 2)
        val s = table(rules, cards("8C", "XH", "3D"), cards("7C", "7D", "4S"), cards("6C", "4D", "5S"))
        val sets = PrViews.legalMoves(s, s.player("p1")!!).sets
        assertTrue(sets.any { it.toSet() == cards("8C", "XH").toSet() })
        val t = s.act("p1", PrAction.Play(cards("8C", "XH")))
        assertEquals(PrRejectReason.TOO_LOW.name, t.rejected("p2", PrAction.Play(cards("7C", "7D"))))
    }

    @Test
    fun `equal value skips the next player with that rule`() {
        val s = table(PrHouseRules(equalSkips = true), cards("8C", "3D"), cards("8D", "5H"), cards("9C", "7S"), cards("JC", "8H"))
        val t = s.act("p1", PrAction.Play(cards("8C"))).act("p2", PrAction.Play(cards("8D")))
        assertEquals("p4", t.currentPlayerId)
    }

    @Test
    fun `the next round starts with the exchange and the sloeber leads`() {
        val previous = GameResult(
            four.mapIndexed { i, p -> nl.bluecard.engine.core.RankingEntry(p.id, p.name, i + 1, 0) },
        )
        val s = PrEngine.newGame(four, PrHouseRules(), 7, previous)
        assertEquals(PrPhase.EXCHANGING, s.phase)
        assertEquals(PrTitle.PRESIDENT, s.player("p1")!!.title)
        assertEquals(PrTitle.SCUM, s.player("p4")!!.title)
        assertEquals(15, s.player("p1")!!.hand.size)
        assertEquals(11, s.player("p4")!!.hand.size)
        assertEquals(setOf("p1", "p2"), PrViews.pendingActors(s))
        val giveP1 = PrViews.create(s, "p1").legal
        assertEquals(2, giveP1.giveCount)
        val t = s.act("p1", PrAction.GiveCards(s.player("p1")!!.hand.take(2)))
            .act("p2", PrAction.GiveCards(s.player("p2")!!.hand.take(1)))
        assertEquals(PrPhase.PLAYING, t.phase)
        assertEquals("p4", t.currentPlayerId)
        assertEquals(13, t.player("p4")!!.hand.size)
        assertEquals(52, t.allCards().toSet().size)
    }

    @Test
    fun `views never show other hands`() {
        val s = PrEngine.newGame(four, PrHouseRules(), 3)
        val v = PrViews.create(s, "p2")
        assertEquals(s.player("p2")!!.hand.toSet(), v.myHand.toSet())
        assertNull(v.result)
    }

    @Test
    fun `many random games end properly and keep every card`() {
        val json = Json { ignoreUnknownKeys = true }
        repeat(400) { seed ->
            val random = Random(seed)
            val rules = PrHouseRules(
                twoHigh = random.nextBoolean(),
                jokers = random.nextInt(0, 3),
                exchange = random.nextBoolean(),
                passIsFinal = random.nextBoolean(),
                equalSkips = random.nextBoolean(),
            )
            val players = (1..random.nextInt(3, 8)).map { PlayerInfo("p$it", "P$it") }
            val total = 52 + rules.jokers
            var previous: GameResult? = null
            repeat(2) { round ->
                var state = PrEngine.newGame(players, rules, seed * 10L + round, previous)
                var steps = 0
                while (state.phase != PrPhase.FINISHED) {
                    val actor = PrViews.pendingActors(state).first()
                    val view = PrViews.create(state, actor)
                    val action = PrBot.chooseAction(view, if (random.nextBoolean()) BotDifficulty.EASY else BotDifficulty.NORMAL, random)
                    state = when (val r = PrEngine.apply(state, actor, action)) {
                        is ActionResult.Accepted -> r.state
                        is ActionResult.Rejected -> fail("seed=$seed: $action by $actor rejected: ${r.reason}") as Nothing
                    }
                    assertEquals("cards (seed=$seed)", total, state.allCards().toSet().size)
                    assertEquals("cards (seed=$seed)", total, state.allCards().size)
                    assertTrue("too long (seed=$seed)", ++steps < 2_000)
                }
                val result = assertNotNull(PrEngine.result(state)).let { PrEngine.result(state)!! }
                assertEquals(players.size, result.ranking.size)
                assertEquals((1..players.size).toList(), result.ranking.map { it.position })
                // Round trip through JSON (saved games and Bluetooth).
                assertEquals(state, json.decodeFromString(PrGameState.serializer(), json.encodeToString(PrGameState.serializer(), state)))
                previous = result
            }
        }
    }

    @Test
    fun `giving up ends the game with that player last`() {
        val s = PrEngine.concede(PrEngine.newGame(four, PrHouseRules(), 5), "p2")
        val result = PrEngine.result(s)!!
        assertEquals("p2", result.loser?.playerId)
    }
}
