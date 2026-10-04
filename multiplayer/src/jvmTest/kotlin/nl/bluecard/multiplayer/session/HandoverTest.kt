package nl.bluecard.multiplayer.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.zweedspesten.ZpAction
import nl.bluecard.engine.zweedspesten.ZpGameState
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpModule
import nl.bluecard.engine.zweedspesten.ZpPlayerView
import nl.bluecard.multiplayer.transport.InMemoryAcceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host leaves and another player takes the table over, game and all. */
@OptIn(ExperimentalCoroutinesApi::class)
class HandoverTest {

    private val options = HostOptions(botDelayMs = 10, autoTakeoverMs = 30_000)

    private fun TestScope.host(name: String, token: String) =
        HostSession(ZpModule, name, ZpHouseRules(), backgroundScope, options, seedSource = { 42L }, hostToken = token)

    private fun TestScope.client(acceptor: InMemoryAcceptor, name: String) =
        ClientSession<ZpHouseRules, ZpGameState, ZpAction, ZpPlayerView>(
            ZpModule, name, "token-$name", { acceptor.connect(name) }, backgroundScope, ClientOptions(reconnectDelayMs = 500),
        )

    @Test
    fun `the first player takes over when the host leaves`() = runTest {
        val ivo = host("Ivo", "token-Ivo")
        val first = InMemoryAcceptor()
        ivo.startAccepting(first)
        val anna = client(first, "Anna")
        val bram = client(first, "Bram")
        anna.connect()
        bram.connect()
        ivo.lobby.first { it.seats.size == 3 }
        assertEquals(null, ivo.startGame())
        // Everybody knows Anna takes over; Anna has a copy of the table.
        assertEquals("p1", bram.successor.first { it != null }!!.seatId)
        anna.view.first { it != null }
        runCurrent()
        val snapshot = assertNotNull(anna.handover).let { anna.handover!! }
        assertTrue(snapshot.state != null)

        assertTrue(ivo.leaveWithHandover())
        assertEquals(ConnectionStatus.Lost(LostReason.HOST_MOVED), anna.connection.first { it is ConnectionStatus.Lost })
        assertEquals(ConnectionStatus.Lost(LostReason.HOST_MOVED), bram.connection.first { it is ConnectionStatus.Lost })

        // Anna's phone becomes the host, with the same game.
        val annaHost = host("Anna", "token-Anna")
        annaHost.adopt(anna.handover!!, "p1")
        val second = InMemoryAcceptor()
        annaHost.startAccepting(second)
        val lobby = annaHost.lobby.value
        assertEquals(SessionPhase.IN_GAME, lobby.phase)
        assertEquals(SeatKind.HOST, lobby.seats.first { it.id == "p1" }.kind)
        // A bot plays for Ivo until he comes back.
        assertTrue(lobby.seats.first { it.id == "host" }.botControlled)
        assertEquals("p1", annaHost.hostPort.playerId.value)
        assertTrue(annaHost.hostPort.view.first { it != null }!!.myHand.isNotEmpty())

        // Bram reconnects to Anna and gets his own seat back.
        val bramAgain = client(second, "Bram")
        assertEquals(ConnectionStatus.Connected, bramAgain.connect())
        assertEquals("p2", bramAgain.playerId.value)
        assertFalse(annaHost.lobby.first { l -> l.seats.first { it.id == "p2" }.connected }.seats.first { it.id == "p2" }.botControlled)

        // Ivo can come back as a player and plays himself again.
        val ivoAgain = ClientSession<ZpHouseRules, ZpGameState, ZpAction, ZpPlayerView>(
            ZpModule, "Ivo", "token-Ivo", { second.connect("Ivo") }, backgroundScope,
        )
        assertEquals(ConnectionStatus.Connected, ivoAgain.connect())
        assertEquals("host", ivoAgain.playerId.value)
        val ivoSeat = annaHost.lobby.first { l -> l.seats.first { it.id == "host" }.connected }.seats.first { it.id == "host" }
        assertFalse(ivoSeat.botControlled)
    }

    @Test
    fun `nobody can take over at a table with only bots`() = runTest {
        val ivo = host("Ivo", "t")
        ivo.addBot(BotDifficulty.EASY)
        assertFalse(ivo.leaveWithHandover())
    }
}
