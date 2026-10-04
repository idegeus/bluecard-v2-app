package nl.bluecard.engine.zweedspesten

import org.junit.Assert.assertEquals
import org.junit.Test

class ZpConcedeTest {

    @Test
    fun `giving up ends the game with that player last`() {
        val s = Fx.state(Fx.player("a", hand = "3C"), Fx.player("b", hand = "4D 5D 6D"), Fx.player("c", hand = "7S 8S"), discard = "2H")
        val over = ZpEngine.concede(s, "a")
        assertEquals(ZpPhase.FINISHED, over.phase)
        assertEquals(listOf("c", "b", "a"), ZpEngine.result(over)!!.ranking.map { it.playerId })
        assertEquals(1, over.events<ZpEvent.Resigned>().size)
    }
}
