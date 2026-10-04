package nl.bluecard.engine.hartenjagen

import kotlinx.serialization.json.Json
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.model.PlayerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class HjEngineTest {

    private val four = (1..4).map { PlayerInfo("p$it", "Speler $it") }

    private fun cards(vararg codes: String) = codes.map { Card.of(it) }

    private fun table(rules: HjHouseRules, vararg hands: List<Card>, trickNumber: Int = 1, heartsBroken: Boolean = false) = HjGameState(
        rules = rules,
        players = hands.mapIndexed { i, h -> HjPlayerState("p${i + 1}", "Speler ${i + 1}", h) },
        currentPlayerId = "p1",
        trickNumber = trickNumber,
        heartsBroken = heartsBroken,
    )

    private fun HjGameState.act(id: String, action: HjAction): HjGameState = when (val r = HjEngine.apply(this, id, action)) {
        is ActionResult.Accepted -> r.state
        is ActionResult.Rejected -> fail("$action by $id rejected: ${r.reason}") as Nothing
    }

    private fun HjGameState.rejected(id: String, action: HjAction): String =
        (HjEngine.apply(this, id, action) as? ActionResult.Rejected)?.reason ?: fail("$action accepted") as Nothing

    @Test
    fun `cards are dealt evenly, with cards left out for three players`() {
        val s = HjEngine.newGame(four.take(3), HjHouseRules(), 1)
        assertEquals(17, s.players.first().hand.size)
        assertEquals(listOf(Card.of("2D")), s.removed)
        assertEquals(52, s.allCards().toSet().size)
    }

    @Test
    fun `follow suit and the highest card of the led suit wins`() {
        val s = table(HjHouseRules(), cards("5C", "9H"), cards("KC", "2H"), cards("3D", "4H"), cards("AC", "5H"))
        val a = s.act("p1", HjAction.Play(Card.of("5C")))
        assertEquals(HjRejectReason.MUST_FOLLOW_SUIT.name, a.rejected("p2", HjAction.Play(Card.of("2H"))))
        val b = a.act("p2", HjAction.Play(Card.of("KC"))).act("p3", HjAction.Play(Card.of("4H"))).act("p4", HjAction.Play(Card.of("AC")))
        assertEquals("p4", b.currentPlayerId)
        assertEquals(listOf(Card.of("4H")), b.player("p4")!!.taken)
        assertTrue(b.heartsBroken)
    }

    @Test
    fun `hearts cannot be led before they are broken`() {
        val s = table(HjHouseRules(), cards("5C", "9H"), cards("KC", "2H"), cards("3D", "4H"), cards("AC", "5H"))
        assertEquals(HjRejectReason.HEARTS_NOT_BROKEN.name, s.rejected("p1", HjAction.Play(Card.of("9H"))))
    }

    @Test
    fun `international rules pass three cards and the two of clubs leads`() {
        val rules = HjPreset.INTERNATIONAL.rules
        val s = HjEngine.newGame(four, rules, 9)
        assertEquals(HjPhase.PASSING, s.phase)
        var t = s
        for (p in s.players) t = t.act(p.id, HjAction.PassCards(t.player(p.id)!!.hand.take(3)))
        assertEquals(HjPhase.PLAYING, t.phase)
        assertTrue(t.players.all { it.hand.size == 13 })
        val leader = t.player(t.currentPlayerId!!)!!
        assertTrue(Card.of("2C") in leader.hand)
        val other = leader.hand.first { it != Card.of("2C") }
        assertEquals(HjRejectReason.MUST_LEAD_CLUB.name, t.rejected(leader.id, HjAction.Play(other)))
    }

    @Test
    fun `scores add up per deal and shooting the moon gives the others the points`() {
        val rules = HjHouseRules(targetScore = 0, shootTheMoon = true, queenPoints = 13, jackPoints = 0)
        var state = HjEngine.newGame(four, rules, 4)
        // Give p1 every penalty card by playing with a rigged state: p1 holds all hearts and the queen and wins all.
        state = state.copy(
            players = listOf(
                HjPlayerState("p1", "a", taken = cards("QS") + (2..14).map { Card(nl.bluecard.engine.model.Rank.fromValue(it)!!, nl.bluecard.engine.model.Suit.HEARTS) }, hand = cards("AC")),
                HjPlayerState("p2", "b", hand = cards("2C")),
                HjPlayerState("p3", "c", hand = cards("3C")),
                HjPlayerState("p4", "d", hand = cards("4C")),
            ),
            currentPlayerId = "p1",
            trickNumber = 12,
            heartsBroken = true,
        )
        val end = state.act("p1", HjAction.Play(Card.of("AC"))).act("p2", HjAction.Play(Card.of("2C")))
            .act("p3", HjAction.Play(Card.of("3C"))).act("p4", HjAction.Play(Card.of("4C")))
        assertEquals(HjPhase.FINISHED, end.phase)
        assertEquals(0, end.player("p1")!!.score)
        assertEquals(26, end.player("p2")!!.score)
        val result = HjEngine.result(end)!!
        assertEquals("p1", result.winner!!.playerId)
        assertEquals(0, result.winner!!.score)
    }

    @Test
    fun `many random games end with every card kept`() {
        val json = Json { ignoreUnknownKeys = true }
        repeat(250) { seed ->
            val random = Random(seed)
            val rules = (if (random.nextBoolean()) HjPreset.CLASSIC.rules else HjPreset.INTERNATIONAL.rules).copy(
                targetScore = HjHouseRules.TARGETS.random(random),
                heartsBroken = random.nextBoolean(),
                noPointsFirstTrick = random.nextBoolean(),
                passCount = if (random.nextBoolean()) 3 else 0,
            )
            val players = (1..random.nextInt(3, 7)).map { PlayerInfo("p$it", "P$it") }
            var state = HjEngine.newGame(players, rules, seed.toLong())
            var steps = 0
            while (state.phase != HjPhase.FINISHED) {
                val actor = HjViews.pendingActors(state).first()
                val action = HjBot.chooseAction(HjViews.create(state, actor), if (random.nextBoolean()) BotDifficulty.EASY else BotDifficulty.NORMAL, random)
                state = when (val r = HjEngine.apply(state, actor, action)) {
                    is ActionResult.Accepted -> r.state
                    is ActionResult.Rejected -> fail("seed=$seed $action by $actor rejected: ${r.reason}") as Nothing
                }
                assertEquals("seed=$seed", 52, state.allCards().size)
                assertEquals("seed=$seed", 52, state.allCards().toSet().size)
                assertTrue("seed=$seed too long", ++steps < 20_000)
            }
            val result = HjEngine.result(state)!!
            assertEquals((1..players.size).toList(), result.ranking.map { it.position })
            val scores = result.ranking.map { it.score!! }
            assertEquals(scores.sorted(), scores)
            assertEquals(state, json.decodeFromString(HjGameState.serializer(), json.encodeToString(HjGameState.serializer(), state)))
        }
    }

    @Test
    fun `giving up puts that player last`() {
        val s = HjEngine.concede(HjEngine.newGame(four, HjHouseRules(), 2), "p1")
        assertEquals("p1", HjEngine.result(s)!!.loser!!.playerId)
    }
}
