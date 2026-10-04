package nl.bluecard.engine.pesten

import kotlinx.serialization.json.Json
import nl.bluecard.engine.core.ActionResult
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.model.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * Plays many complete games under random house rules and checks global invariants after every single action.
 * Besides the (honest) bots a "cheater" lays random cards and players randomly call "Vals!", "Laatste kaart!"
 * and "Vergeten!", so undoing cheats and the out-of-turn calls are exercised as well.
 */
class PsSimulationTest {

    private fun randomRules(random: Random): PsHouseRules {
        var rules = PsHouseRules(
            effects = emptyMap(),
            jokers = random.nextInt(0, 3),
            jokerDraw = random.nextInt(3, 6),
            handSize = random.nextInt(5, 9),
            stackDraws = random.nextBoolean(),
            allowMultiple = random.nextBoolean(),
            lastCardCall = random.nextBoolean(),
            finishOnSpecial = random.nextBoolean(),
            playUntilLast = random.nextBoolean(),
            enforceRules = random.nextBoolean(),
        )
        for (rank in Rank.STANDARD) {
            if (random.nextInt(3) == 0) rules = rules.withEffect(rank, PsEffect.entries.random(random))
        }
        return rules
    }

    private fun runGame(seed: Long): Int {
        val random = Random(seed)
        val rules = randomRules(random)
        val playerCount = (2..PsRules.maxPlayersFor(rules)).random(random)
        val players = (1..playerCount).map { PlayerInfo("p$it", "Speler $it") }
        val total = 52 + rules.jokers
        var state = PsEngine.newGame(players, rules, seed)
        var actions = 0
        while (state.phase != PsPhase.FINISHED) {
            // Out-of-turn calls by a random other player.
            val other = state.players.random(random)
            val otherView = PsViews.create(state, other.id)
            val call = when {
                otherView.legal.canChallenge && random.nextInt(4) == 0 -> PsAction.Challenge()
                otherView.legal.canCatch && random.nextInt(3) == 0 -> PsAction.CatchLastCard
                otherView.legal.canCallLastCard && random.nextInt(3) == 0 -> PsAction.CallLastCard
                else -> null
            }
            val (actor, action) = if (call != null) {
                other.id to call
            } else {
                val current = requireNotNull(state.currentPlayerId) { "no current player (seed=$seed)" }
                val view = PsViews.create(state, current)
                val cheat = !rules.enforceRules && current == "p1" && random.nextInt(4) == 0 && view.myHand.size > 1
                current to if (cheat) {
                    PsAction.Play(listOf(view.myHand.random(random)), nl.bluecard.engine.model.Suit.entries.random(random))
                } else {
                    PsBot.chooseAction(view, if (random.nextBoolean()) BotDifficulty.NORMAL else BotDifficulty.EASY, random)
                }
            }
            when (val r = PsEngine.apply(state, actor, action)) {
                is ActionResult.Accepted -> state = r.state
                is ActionResult.Rejected -> {
                    // Random cheats may legitimately be refused (e.g. going out on a cheat); bots never are.
                    if (call == null && !(actor == "p1" && !rules.enforceRules)) {
                        fail("seed=$seed: $action by $actor rejected: ${r.reason} rules=$rules")
                    }
                }
            }
            assertEquals("cards not conserved (seed=$seed)", total, state.allCards().size)
            assertEquals("duplicate cards (seed=$seed)", total, state.allCards().toSet().size)
            assertTrue("negative pending draw", state.pendingDraw >= 0)
            actions++
            if (actions > MAX_ACTIONS) return -1
        }
        val result = PsEngine.result(state)!!
        assertEquals(playerCount, result.ranking.size)
        assertEquals((1..playerCount).toList(), result.ranking.map { it.position })
        return actions
    }

    @Test
    fun `hundreds of games with random rules, cheats and calls keep every card and finish`() {
        var finished = 0
        repeat(GAMES) { i -> if (runGame(1_000L + i) >= 0) finished++ }
        assertTrue("only $finished of $GAMES games finished", finished >= GAMES * 9 / 10)
    }

    @Test
    fun `honest bot games with the classic rules always finish`() {
        repeat(200) { i ->
            val players = (1..4).map { PlayerInfo("p$it", "P$it") }
            var state = PsEngine.newGame(players, PsHouseRules(), i.toLong())
            val random = Random(i)
            var actions = 0
            while (state.phase != PsPhase.FINISHED) {
                val actor = state.currentPlayerId!!
                val action = PsBot.chooseAction(PsViews.create(state, actor), BotDifficulty.NORMAL, random)
                state = (PsEngine.apply(state, actor, action) as? ActionResult.Accepted)?.state
                    ?: fail("seed=$i: $action rejected").let { error("unreachable") }
                assertTrue("seed=$i takes too long", ++actions < MAX_ACTIONS)
            }
        }
    }

    @Test
    fun `state and views survive a JSON round trip`() {
        val json = Json { encodeDefaults = false }
        val players = listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B"))
        var state = PsEngine.newGame(players, PsHouseRules(), 7)
        val random = Random(7)
        repeat(20) {
            val actor = state.currentPlayerId ?: return@repeat
            val action = PsBot.chooseAction(PsViews.create(state, actor), BotDifficulty.NORMAL, random)
            val encodedAction = json.encodeToString(PsAction.serializer(), action)
            assertEquals(action, json.decodeFromString(PsAction.serializer(), encodedAction))
            state = (PsEngine.apply(state, actor, action) as ActionResult.Accepted).state
        }
        assertEquals(state, json.decodeFromString(PsGameState.serializer(), json.encodeToString(PsGameState.serializer(), state)))
        val view = PsViews.create(state, "a")
        assertEquals(view, json.decodeFromString(PsPlayerView.serializer(), json.encodeToString(PsPlayerView.serializer(), view)))
    }

    private companion object {
        const val GAMES = 600
        const val MAX_ACTIONS = 3_000
    }
}
