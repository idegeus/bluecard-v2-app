package nl.bluecard.multiplayer.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.pesten.PsAction
import nl.bluecard.engine.pesten.PsBot
import nl.bluecard.engine.pesten.PsHouseRules
import nl.bluecard.engine.pesten.PsModule
import nl.bluecard.engine.pesten.PsPlayerView
import nl.bluecard.engine.zweedspesten.ZpModule
import nl.bluecard.multiplayer.transport.InMemoryAcceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The game-agnostic sessions also carry Pesten, a second game with its own state, actions and views. */
@OptIn(ExperimentalCoroutinesApi::class)
class PestenSessionTest {

    private val hostOptions = HostOptions(botDelayMs = 10, autoTakeoverMs = 30_000)

    private fun TestScope.newHost(): Pair<HostSession<*, *, *, *>, InMemoryAcceptor> {
        val host = HostSession(PsModule, "Ivo", PsHouseRules(), backgroundScope, hostOptions, seedSource = { 7L })
        val acceptor = InMemoryAcceptor()
        host.startAccepting(acceptor)
        return host to acceptor
    }

    private fun CoroutineScope.autoPlay(port: PlayerPort<PsPlayerView, PsAction>, seed: Int) = launch {
        val random = Random(seed)
        port.view.collect { view ->
            if (view != null && view.isMyTurn) port.submit(PsBot.chooseAction(view, BotDifficulty.NORMAL, random))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val HostSession<*, *, *, *>.pestenPort get() = hostPort as PlayerPort<PsPlayerView, PsAction>

    @Test
    fun `full Pesten game between host, remote client and a bot stays in sync`() = runTest {
        val (host, acceptor) = newHost()
        val client = ClientSession(PsModule, "Anna", "token-anna", { acceptor.connect("Anna") }, backgroundScope, ClientOptions())
        assertEquals(ConnectionStatus.Connected, client.connect())
        host.addBot(BotDifficulty.NORMAL)
        assertEquals(null, host.startGame())

        val hostPlayer = autoPlay(host.pestenPort, 1)
        val clientPlayer = autoPlay(client, 2)
        val hostFinal = withTimeout(600_000) { host.pestenPort.view.first { it?.result != null }!! }
        val clientFinal = withTimeout(600_000) { client.view.first { it?.result != null }!! }
        hostPlayer.cancel()
        clientPlayer.cancel()

        assertEquals(hostFinal.result, clientFinal.result)
        assertEquals(3, hostFinal.result!!.ranking.size)
        assertEquals(hostFinal.players, clientFinal.players)
    }

    @Test
    fun `probing tells the game, and a client of another game is refused`() = runTest {
        val (_, acceptor) = newHost()
        val probe = LobbyProbe.query(acceptor.connect("Probe"), "Probe", "token-probe")
        assertTrue(probe is ProbeResult.Found)
        assertEquals(PsModule.GAME_ID, (probe as ProbeResult.Found).info.gameId)

        val wrong = ClientSession(ZpModule, "Bas", "token-bas", { acceptor.connect("Bas") }, backgroundScope, ClientOptions())
        assertTrue(wrong.connect() !is ConnectionStatus.Connected)
    }

    @Test
    fun `host giving up against bots ends the game with the host last`() = runTest {
        val (host, _) = newHost()
        host.addBot(BotDifficulty.NORMAL)
        host.addBot(BotDifficulty.NORMAL)
        assertEquals(null, host.startGame())
        host.resignHost(endGame = true)
        val final = withTimeout(10_000) { host.pestenPort.view.first { it?.result != null }!! }
        assertEquals("host", final.result!!.ranking.last().playerId)
        assertEquals(SessionPhase.FINISHED, host.lobby.value.phase)
    }

    @Test
    fun `host giving up in a Bluetooth game lets a bot play on`() = runTest {
        val (host, acceptor) = newHost()
        val client = ClientSession(PsModule, "Anna", "token-anna", { acceptor.connect("Anna") }, backgroundScope, ClientOptions())
        client.connect()
        assertEquals(null, host.startGame())
        host.resignHost(endGame = false)
        assertTrue(host.lobby.value.seats.first { it.kind == SeatKind.HOST }.botControlled)
        val clientPlayer = autoPlay(client, 3)
        val final = withTimeout(600_000) { client.view.first { it?.result != null }!! }
        clientPlayer.cancel()
        assertEquals(2, final.result!!.ranking.size)
    }

    @Test
    fun `finished games are recorded on every phone and histories are merged on joining`() = runTest {
        val hostLog = InMemoryMatchLog()
        val clientLog = InMemoryMatchLog()
        val older = MatchRecord("old-1", PsModule.GAME_ID, 1L, listOf(MatchPlayer("dev-b", "Anna", 1, true), MatchPlayer("dev-c", "Bas", 2, true)))
        clientLog.merge(listOf(older))
        val host = HostSession(
            PsModule, "Ivo", PsHouseRules(), backgroundScope, hostOptions, seedSource = { 7L },
            matchLog = hostLog, hostDeviceId = "dev-a", clock = { 42L },
        )
        val acceptor = InMemoryAcceptor()
        host.startAccepting(acceptor)
        val client = ClientSession(
            PsModule, "Anna", "token-anna", { acceptor.connect("Anna") }, backgroundScope, ClientOptions(),
            deviceId = "dev-b", matchLog = clientLog,
        )
        client.connect()
        assertEquals("dev-b", host.lobby.first { l -> l.seats.any { it.deviceId == "dev-b" } }.seats.last().deviceId)
        withTimeout(10_000) { while (hostLog.all.none { it.id == "old-1" }) kotlinx.coroutines.delay(10) }

        host.addBot(BotDifficulty.NORMAL)
        assertEquals(null, host.startGame())
        val players = listOf(autoPlay(host.pestenPort, 1), autoPlay(client, 2))
        withTimeout(600_000) { client.view.first { it?.result != null } }
        withTimeout(10_000) { while (clientLog.all.size < 2 || hostLog.all.size < 2) kotlinx.coroutines.delay(10) }
        players.forEach { it.cancel() }

        val record = hostLog.all.first { it.id != "old-1" }
        assertEquals(record, clientLog.all.first { it.id == record.id })
        assertEquals(setOf("dev-a", "dev-b", null), record.players.map { it.deviceId }.toSet())
        assertEquals(2, record.players.count { it.human })
    }

    @Test
    fun `the host can change the seating order in the lobby, which is the order of play`() = runTest {
        val (host, _) = newHost()
        host.addBot(BotDifficulty.NORMAL)
        host.addBot(BotDifficulty.NORMAL)
        val order = host.lobby.value.seats.map { it.id }
        assertTrue(host.moveSeat(order[2], -2))
        assertEquals(listOf(order[2], order[0], order[1]), host.lobby.value.seats.map { it.id })
        assertTrue(!host.moveSeat(order[1], 1))
        assertEquals(null, host.startGame())
        assertEquals(listOf(order[2], order[0], order[1]), host.pestenPort.view.first { it != null }!!.players.map { it.id })
        assertTrue(!host.moveSeat(order[0], 1))
    }

    @Test
    fun `switching the game tells clients to reconnect for the new game`() = runTest {
        val (host, acceptor) = newHost()
        val client = ClientSession(PsModule, "Anna", "token-anna", { acceptor.connect("Anna") }, backgroundScope, ClientOptions())
        client.connect()
        val notice = backgroundScope.async { client.notices.first { it is SessionNotice.GameSwitched } }
        kotlinx.coroutines.yield()
        host.switchGame("zweeds-pesten")
        assertEquals(SessionNotice.GameSwitched("zweeds-pesten"), withTimeout(10_000) { notice.await() })
        assertEquals(ConnectionStatus.Lost(LostReason.GAME_SWITCHED), withTimeout(10_000) { client.connection.first { it is ConnectionStatus.Lost } })
    }
}
