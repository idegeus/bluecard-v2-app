package nl.bluecard.engine.pesten

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PsConcedeTest {

    @Test
    fun `giving up ends the game with that player last`() {
        val s = PsFx.state(PsFx.player("a", "3C"), PsFx.player("b", "4D 5D 6D"), PsFx.player("c", "7S 8S"))
        val over = PsEngine.concede(s, "a")
        assertEquals(PsPhase.FINISHED, over.phase)
        val ranking = PsEngine.result(over)!!.ranking
        assertEquals(listOf("c", "b", "a"), ranking.map { it.playerId })
        assertEquals(1, over.events<PsEvent.Resigned>().size)
        assertEquals(s.version + 1, over.version)
        assertSame(over, PsEngine.concede(over, "b"))
    }
}
