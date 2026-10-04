package nl.bluecard.engine.core

import nl.bluecard.engine.hartenjagen.HjModule
import nl.bluecard.engine.model.PlayerInfo
import nl.bluecard.engine.pesten.PsModule
import nl.bluecard.engine.presidenten.PrModule
import nl.bluecard.engine.zweedspesten.ZpModule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spectators (not at the table) get a view without anybody's cards and without moves. */
class SpectatorViewTest {
    private val players = listOf(PlayerInfo("a", "A"), PlayerInfo("b", "B"), PlayerInfo("c", "C"), PlayerInfo("d", "D"))

    private fun <C : Any, S : Any, A : Any, V : Any> check(module: GameModule<C, S, A, V>, hand: (V) -> List<*>) {
        val state = module.newGame(players, module.defaultConfig(), 7L)
        val view = module.view(state, "watcher")
        assertTrue(module.info.id, hand(view).isEmpty())
        // A bot asked to act for the spectator must not find anything to do (the host never asks, but views stay safe).
        assertFalse(module.info.id, "watcher" in module.pendingActors(state))
    }

    @Test
    fun `every game has a safe spectator view`() {
        check(ZpModule) { it.myHand }
        check(PsModule) { it.myHand }
        check(PrModule) { it.myHand }
        check(HjModule) { it.myHand }
    }
}
