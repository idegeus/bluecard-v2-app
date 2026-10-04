package nl.bluecard.engine.zweedspesten

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
 * Plays many complete games with bots under random house rules and checks global invariants after
 * every single action. This catches rule interactions that are hard to enumerate by hand.
 */
class ZpSimulationTest {

    private fun randomRules(random: Random): ZpHouseRules {
        var rules = ZpHouseRules(
            effects = emptyMap(),
            handSize = random.nextInt(3, 6),
            allowMultiple = random.nextBoolean(),
            fourOfAKindBurns = random.nextBoolean(),
            burnGivesExtraTurn = random.nextBoolean(),
            lowerIncludesEqual = random.nextBoolean(),
            swapPhase = random.nextBoolean(),
            drawGamble = random.nextBoolean(),
            reshuffleBurned = random.nextInt(4) == 0,
            playUntilLast = random.nextInt(4) != 0,
            startRule = if (random.nextBoolean()) StartRule.LOWEST_CARD else StartRule.RANDOM,
            enforceRules = random.nextBoolean(),
            playAfterPickUp = random.nextBoolean(),
        )
        for (rank in Rank.STANDARD) {
            if (random.nextInt(3) == 0) rules = rules.withEffect(rank, ZpEffect.entries.random(random))
        }
        return rules
    }

    private fun runGame(seed: Long, rules: ZpHouseRules, playerCount: Int, difficulties: List<BotDifficulty>): Int {
        val random = Random(seed)
        val players = (1..playerCount).map { PlayerInfo("p$it", "Speler $it") }
        var state = ZpEngine.newGame(players, rules, seed)
        var actions = 0
        while (state.phase != ZpPhase.FINISHED) {
            val actors = ZpViews.pendingActors(state)
            assertTrue("no pending actor in ${state.phase}", actors.isNotEmpty())
            val actor = actors.first()
            val view = ZpViews.create(state, actor)
            assertTrue("actor without legal move", view.legal.hasAnyMove)
            val difficulty = difficulties[players.indexOfFirst { it.id == actor }]
            val action = ZpBot.chooseAction(view, difficulty, random)
            // Independent check of the "7 or lower" rule: after a LOWER card no higher card may ever be played.
            val top = state.discardPile.top
            if (action is ZpAction.Play && top != null && rules.effectOf(top.rank) == ZpEffect.LOWER) {
                val limit = if (rules.lowerIncludesEqual) top.rank.value else top.rank.value - 1
                assertTrue("seed=$seed: ${action.cards} played on $top", action.cards.all { it.rank.value <= limit })
            }
            when (val r = ZpEngine.apply(state, actor, action)) {
                is ActionResult.Accepted -> state = r.state
                is ActionResult.Rejected -> fail("seed=$seed bot action $action rejected: ${r.reason} rules=$rules")
            }
            assertEquals("cards not conserved (seed=$seed)", 52, state.allCards().size)
            assertEquals("duplicate cards (seed=$seed)", 52, state.allCards().toSet().size)
            actions++
            if (actions > MAX_ACTIONS) return -1
        }
        val result = ZpEngine.result(state)!!
        assertEquals(playerCount, result.ranking.size)
        assertEquals((1..playerCount).toList(), result.ranking.map { it.position })
        return actions
    }

    @Test
    fun `hundreds of bot games under random house rules never produce invalid moves or lose cards`() {
        // Exotic random rule sets (e.g. no burn or reset cards at all, or reshuffling burned cards) can make a
        // game last very long; that is a property of those rules, not a bug. This test therefore checks the
        // invariants on every action (no rejected bot move, 52 unique cards, a legal move always exists)
        // and that the ranking is complete for every game that does finish within the cap.
        val random = Random(2024)
        var finished = 0
        repeat(600) { i ->
            val rules = randomRules(random)
            val count = random.nextInt(2, ZpRules.maxPlayersFor(rules) + 1)
            val difficulties = List(count) { BotDifficulty.entries.random(random) }
            if (runGame(seed = i.toLong(), rules = rules, playerCount = count, difficulties = difficulties) > 0) finished++
        }
        assertTrue("only $finished games finished", finished > 300)
    }

    @Test
    fun `classic rules with normal bots always finish`() {
        repeat(300) { i ->
            val count = 2 + i % 4
            val actions = runGame(i.toLong(), ZpHouseRules(), count, List(count) { BotDifficulty.NORMAL })
            assertTrue("classic game $i did not finish", actions > 0)
        }
    }

    @Test
    fun `all presets with mixed bots finish`() {
        for (preset in ZpPreset.entries) {
            repeat(100) { i ->
                val count = 2 + i % 4
                val actions = runGame(
                    1000L + i,
                    preset.rules,
                    count,
                    List(count) { if (it % 2 == 0) BotDifficulty.NORMAL else BotDifficulty.EASY },
                )
                assertTrue("${preset.name} game $i did not finish", actions > 0)
            }
        }
    }

    private companion object {
        const val MAX_ACTIONS = 3000
    }
}
