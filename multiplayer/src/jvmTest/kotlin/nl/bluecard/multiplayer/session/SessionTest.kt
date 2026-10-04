package nl.bluecard.multiplayer.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.zweedspesten.ZpAction
import nl.bluecard.engine.zweedspesten.ZpBot
import nl.bluecard.engine.zweedspesten.ZpGameState
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.engine.zweedspesten.ZpModule
import nl.bluecard.engine.zweedspesten.ZpPhase
import nl.bluecard.engine.zweedspesten.ZpPlayerView
import nl.bluecard.multiplayer.protocol.DecodeResult
import nl.bluecard.multiplayer.protocol.DisconnectReason
import nl.bluecard.multiplayer.protocol.ErrorCode
import nl.bluecard.multiplayer.protocol.HelloIntent
import nl.bluecard.multiplayer.protocol.JoinRejectReason
import nl.bluecard.multiplayer.protocol.NetMessage
import nl.bluecard.multiplayer.protocol.ProtocolCodec
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.transport.InMemoryAcceptor
import nl.bluecard.multiplayer.transport.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTest {

    private val hostOptions = HostOptions(botDelayMs = 10, autoTakeoverMs = 30_000)
    private val clientOptions = ClientOptions(reconnectDelayMs = 500)

    private fun TestScope.newHost(
        rules: ZpHouseRules = ZpHouseRules(),
        options: HostOptions = hostOptions,
    ): Pair<HostSession<ZpHouseRules, ZpGameState, ZpAction, ZpPlayerView>, InMemoryAcceptor> {
        val host = HostSession(ZpModule, "Ivo", rules, backgroundScope, options, seedSource = { 42L })
        val acceptor = InMemoryAcceptor()
        host.startAccepting(acceptor)
        return host to acceptor
    }

    private fun TestScope.newClient(
        acceptor: InMemoryAcceptor,
        name: String,
        token: String = "token-$name",
        options: ClientOptions = clientOptions,
    ) = ClientSession(ZpModule, name, token, { acceptor.connect(name) }, backgroundScope, options)

    /** Plays automatically for a port, choosing moves like a bot but going through the normal submit path. */
    private fun CoroutineScope.autoPlay(port: PlayerPort<ZpPlayerView, ZpAction>, seed: Int) = launch {
        val random = Random(seed)
        port.view.collect { view ->
            if (view != null && view.legal.hasAnyMove && view.phase != ZpPhase.FINISHED) {
                port.submit(ZpBot.chooseAction(view, BotDifficulty.NORMAL, random))
            }
        }
    }

    /** A raw protocol client for sending hand-crafted (also corrupt) messages. */
    private class RawClient(val link: Link) {
        private val codec = ProtocolCodec()
        private var seq = 0L
        suspend fun send(message: NetMessage) = link.writeLine(codec.encode(++seq, message))
        suspend fun sendRaw(line: String) = link.writeLine(line)
        suspend fun next(): NetMessage {
            while (true) {
                val line = link.readLine() ?: error("closed")
                val decoded = codec.decode(line)
                if (decoded is DecodeResult.Ok) {
                    val msg = decoded.envelope.msg
                    if (msg !is NetMessage.Ping) return msg
                }
            }
        }
        suspend inline fun <reified T : NetMessage> expect(): T {
            while (true) {
                val msg = next()
                if (msg is T) return msg
            }
        }
    }

    // ------------------------------------------------------------------ lobby

    @Test
    fun `client joins the lobby and both sides see the seat`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        assertEquals(ConnectionStatus.Connected, client.connect())
        val hostLobby = host.lobby.first { it.seats.size == 2 }
        assertEquals(listOf("Ivo", "Anna"), hostLobby.seats.map { it.name })
        val clientLobby = client.lobby.first { it != null && it.seats.size == 2 }!!
        assertEquals(SessionPhase.LOBBY, clientLobby.phase)
        assertEquals("p1", client.playerId.value)
    }

    @Test
    fun `duplicate names get a suffix`() = runTest {
        val (host, acceptor) = newHost()
        newClient(acceptor, "Ivo").connect()
        assertEquals(listOf("Ivo", "Ivo (2)"), host.lobby.first { it.seats.size == 2 }.seats.map { it.name })
    }

    @Test
    fun `lobby full and game in progress are rejected when there is no room to watch`() = runTest {
        val (host, acceptor) = newHost(options = hostOptions.copy(maxSpectators = 0))
        repeat(3) { host.addBot(BotDifficulty.EASY) }
        assertEquals(ConnectionStatus.Connected, newClient(acceptor, "Anna").connect())
        val full = newClient(acceptor, "Bram").connect()
        assertEquals(ConnectionStatus.Rejected(JoinRejectReason.LOBBY_FULL.name), full)

        host.removeSeat(host.lobby.value.seats.first { it.kind == SeatKind.BOT }.id)
        assertEquals(null, host.startGame())
        val late = newClient(acceptor, "Cor").connect()
        assertEquals(ConnectionStatus.Rejected(JoinRejectReason.GAME_IN_PROGRESS.name), late)
    }

    @Test
    fun `starting needs enough players`() = runTest {
        val (host, _) = newHost()
        assertEquals("TOO_FEW_PLAYERS", host.startGame())
        host.addBot(BotDifficulty.NORMAL)
        assertEquals(null, host.startGame())
        assertEquals("ALREADY_RUNNING", host.startGame())
    }

    @Test
    fun `client leaving the lobby frees the seat`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.lobby.first { it.seats.size == 2 }
        client.leave()
        host.lobby.first { it.seats.size == 1 }
    }

    @Test
    fun `host can kick a player`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.lobby.first { it.seats.size == 2 }
        assertTrue(host.removeSeat("p1"))
        assertEquals(ConnectionStatus.Lost(LostReason.KICKED), client.connection.first { it is ConnectionStatus.Lost })
    }

    @Test
    fun `rule changes reach the clients`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.updateConfig(ZpHouseRules(handSize = 4))
        val lobby = client.lobby.first { lobby ->
            lobby != null && ProtocolJson.json.decodeFromJsonElement(ZpHouseRules.serializer(), lobby.config).handSize == 4
        }
        assertNotNull(lobby)
    }

    @Test
    fun `lobby probe returns game information without joining`() = runTest {
        val (host, acceptor) = newHost()
        host.addBot(BotDifficulty.EASY)
        val result = LobbyProbe.query(acceptor.connect("probe"), "Anna", "t")
        val info = (result as ProbeResult.Found).info
        assertEquals("Ivo", info.hostName)
        assertEquals(ZpModule.GAME_ID, info.gameId)
        assertEquals(2, info.playerCount)
        assertEquals(5, info.maxPlayers)
        runCurrent()
        assertEquals(2, host.lobby.value.seats.size)
    }

    // ------------------------------------------------------------------ playing over the network

    @Test
    fun `full game between host, remote client and a bot stays in sync`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.addBot(BotDifficulty.NORMAL)
        assertEquals(null, host.startGame())

        val hostPlayer = autoPlay(host.hostPort, 1)
        val clientPlayer = autoPlay(client, 2)

        val hostFinal = withTimeout(600_000) { host.hostPort.view.first { it?.result != null }!! }
        val clientFinal = withTimeout(600_000) { client.view.first { it?.result != null }!! }
        hostPlayer.cancel()
        clientPlayer.cancel()

        assertEquals(hostFinal.result, clientFinal.result)
        assertEquals(3, hostFinal.result!!.ranking.size)
        assertEquals(hostFinal.stateVersion, clientFinal.stateVersion)
        assertEquals(hostFinal.players, clientFinal.players)
        assertEquals("p1", clientFinal.viewerId)
        assertEquals(SessionPhase.FINISHED, client.lobby.first { it?.phase == SessionPhase.FINISHED }!!.phase)
    }

    @Test
    fun `full game with host, two remote clients and a bot stays in sync`() = runTest {
        val (host, acceptor) = newHost()
        val anna = newClient(acceptor, "Anna")
        val bram = newClient(acceptor, "Bram")
        assertEquals(ConnectionStatus.Connected, anna.connect())
        assertEquals(ConnectionStatus.Connected, bram.connect())
        host.addBot(BotDifficulty.NORMAL)
        assertEquals(null, host.startGame())

        val players = listOf(autoPlay(host.hostPort, 1), autoPlay(anna, 2), autoPlay(bram, 3))
        val finals = listOf(host.hostPort, anna, bram).map { port ->
            withTimeout(900_000) { port.view.first { it?.result != null }!! }
        }
        players.forEach { it.cancel() }

        assertEquals(4, finals.first().result!!.ranking.size)
        finals.forEach { view ->
            assertEquals(finals.first().result, view.result)
            assertEquals(finals.first().players, view.players)
        }
        assertEquals(listOf(HostSession.HOST_SEAT_ID, "p1", "p2"), finals.map { it.viewerId })
    }

    @Test
    fun `views are synchronised and never contain the other player's hand`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.startGame()
        val hostView = host.hostPort.view.first { it != null }!!
        val clientView = client.view.first { it != null }!!
        assertEquals(hostView.currentPlayerId, clientView.currentPlayerId)
        assertEquals(hostView.discardTop, clientView.discardTop)
        assertEquals(hostView.drawPileCount, clientView.drawPileCount)
        assertTrue(hostView.myHand.intersect(clientView.myHand.toSet()).isEmpty())
        assertEquals(clientView.myHand.size, hostView.player("p1")!!.handCount)
    }

    @Test
    fun `action of the wrong player is rejected by the host`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.startGame()
        val view = client.view.first { it != null }!!
        val notMine = if (view.currentPlayerId == "p1") host.hostPort else client
        val notMineView = notMine.view.first { it != null }!!
        val result = notMine.submit(ZpAction.Play(listOf(notMineView.myHand.first())))
        assertEquals(SubmitResult.Rejected("NOT_YOUR_TURN"), result)
    }

    @Test
    fun `invalid card from a client is rejected`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.startGame()
        // Whoever starts: let the host play until it is the client's turn.
        while (true) {
            val hostView = host.hostPort.view.first { it != null }!!
            if (hostView.currentPlayerId != HostSession.HOST_SEAT_ID) break
            host.hostPort.submit(ZpBot.chooseAction(hostView, BotDifficulty.NORMAL, Random(1)))
        }
        val view = client.view.first { it?.currentPlayerId == "p1" }!!
        val hostsCard = host.hostPort.view.value!!.myHand.first()
        assertEquals(SubmitResult.Rejected("CARD_NOT_AVAILABLE"), client.submit(ZpAction.Play(listOf(hostsCard))))
        assertEquals(view.stateVersion, client.view.value!!.stateVersion)
    }

    @Test
    fun `duplicate actions are ignored`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = true))
        val raw = RawClient(acceptor.connect("raw"))
        raw.send(NetMessage.Hello("Raw", "raw-token"))
        raw.expect<NetMessage.JoinAccepted>()
        host.startGame()
        val ready = ProtocolJson.json.encodeToJsonElement(ZpAction.serializer(), ZpAction.Ready)
        raw.send(NetMessage.PlayerAction(1, 0, ready))
        assertTrue(raw.expect<NetMessage.ActionResult>().accepted)
        raw.send(NetMessage.PlayerAction(1, 0, ready))
        val duplicate = raw.expect<NetMessage.ActionResult>()
        assertFalse(duplicate.accepted)
        assertEquals(HostSession.REASON_DUPLICATE, duplicate.reason)
        // A new id with the same intent is evaluated by the engine (and rejected because already ready).
        raw.send(NetMessage.PlayerAction(2, 0, ready))
        assertEquals("ALREADY_READY", raw.expect<NetMessage.ActionResult>().reason)
    }

    @Test
    fun `corrupt and unknown messages get an error but keep the connection open`() = runTest {
        val (host, acceptor) = newHost()
        val raw = RawClient(acceptor.connect("raw"))
        raw.sendRaw("this is not json")
        assertEquals(ErrorCode.MALFORMED, raw.expect<NetMessage.Error>().code)
        raw.sendRaw("{\"v\":2,\"seq\":2,\"msg\":{\"type\":\"TELEPORT\"}}")
        assertEquals(ErrorCode.UNKNOWN_TYPE, raw.expect<NetMessage.Error>().code)
        raw.send(NetMessage.PlayerAction(1, 0, ProtocolJson.json.parseToJsonElement("{}")))
        assertEquals(ErrorCode.NOT_JOINED, raw.expect<NetMessage.Error>().code)
        raw.send(NetMessage.Hello("Raw", "raw-token"))
        raw.expect<NetMessage.JoinAccepted>()
        host.lobby.first { it.seats.size == 2 }
    }

    @Test
    fun `malformed action payload is rejected`() = runTest {
        val (host, acceptor) = newHost()
        val raw = RawClient(acceptor.connect("raw"))
        raw.send(NetMessage.Hello("Raw", "raw-token"))
        raw.expect<NetMessage.JoinAccepted>()
        host.startGame()
        raw.send(NetMessage.PlayerAction(1, 0, ProtocolJson.json.parseToJsonElement("{\"type\":\"FLY\"}")))
        assertEquals(HostSession.REASON_MALFORMED, raw.expect<NetMessage.ActionResult>().reason)
    }

    @Test
    fun `other protocol version is rejected`() = runTest {
        val (_, acceptor) = newHost()
        val raw = RawClient(acceptor.connect("raw"))
        raw.sendRaw("{\"v\":3,\"seq\":1,\"msg\":{\"type\":\"HELLO\",\"playerName\":\"X\",\"playerToken\":\"t\"}}")
        assertEquals(JoinRejectReason.VERSION_MISMATCH, raw.expect<NetMessage.JoinRejected>().reason)
    }

    @Test
    fun `client detects a host with another protocol version`() = runTest {
        val acceptor = InMemoryAcceptor()
        val fakeHost = backgroundScope.launch {
            val link = acceptor.accept()
            link.readLine()
            link.writeLine("{\"v\":9,\"seq\":1,\"msg\":{\"type\":\"WHATEVER\"}}")
        }
        val client = newClient(acceptor, "Anna")
        assertEquals(ConnectionStatus.Rejected(ClientSession.REASON_VERSION), client.connect())
        fakeHost.cancel()
    }

    // ------------------------------------------------------------------ disconnects

    @Test
    fun `host closing is reported to clients`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.close()
        assertEquals(ConnectionStatus.Lost(LostReason.HOST_CLOSED), client.connection.first { it is ConnectionStatus.Lost })
    }

    @Test
    fun `client reconnects with its token and gets its seat and cards back`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        val first = RawClient(acceptor.connect("Anna"))
        first.send(NetMessage.Hello("Anna", "anna-token"))
        val accepted = first.expect<NetMessage.JoinAccepted>()
        host.startGame()
        val stateBefore = first.expect<NetMessage.GameState>()
        first.link.close()

        val seatGone = host.lobby.first { lobby -> lobby.seats.any { it.id == accepted.playerId && !it.connected } }
        assertNotNull(seatGone)

        val second = RawClient(acceptor.connect("Anna"))
        second.send(NetMessage.Hello("Anna", "anna-token"))
        val again = second.expect<NetMessage.JoinAccepted>()
        assertTrue(again.reconnected)
        assertEquals(accepted.playerId, again.playerId)
        second.expect<NetMessage.GameStart>()
        val stateAfter = second.expect<NetMessage.GameState>()
        val before = ProtocolJson.json.decodeFromJsonElement(ZpPlayerView.serializer(), stateBefore.view)
        val after = ProtocolJson.json.decodeFromJsonElement(ZpPlayerView.serializer(), stateAfter.view)
        assertEquals(before.myHand, after.myHand)
        host.lobby.first { lobby -> lobby.seats.first { it.id == accepted.playerId }.connected }
    }

    @Test
    fun `client reconnects automatically when the link drops`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        var links = 0
        val links0 = mutableListOf<Link>()
        val client = ClientSession(
            ZpModule, "Anna", "anna-token",
            { links++; acceptor.connect("Anna").also { links0 += it } },
            backgroundScope, clientOptions,
        )
        client.connect()
        host.startGame()
        client.view.first { it != null }
        links0.last().close() // simulate a dropped Bluetooth connection
        client.connection.first { it is ConnectionStatus.Reconnecting }
        client.connection.first { it == ConnectionStatus.Connected }
        assertEquals(2, links)
        val view = client.view.first { it != null }!!
        assertEquals("p1", view.viewerId)
        host.lobby.first { lobby -> lobby.seats.first { it.id == "p1" }.connected }
    }

    @Test
    fun `client gives up after the reconnect attempts and reports the loss`() = runTest {
        val (host, acceptor) = newHost()
        var attempt = 0
        val client = ClientSession(
            ZpModule, "Anna", "anna-token",
            {
                attempt++
                if (attempt == 1) acceptor.connect("Anna") else throw java.io.IOException("out of range")
            },
            backgroundScope, clientOptions,
        )
        client.connect()
        host.close()
        // Host closed explicitly -> no reconnect attempts.
        assertEquals(ConnectionStatus.Lost(LostReason.HOST_CLOSED), client.connection.first { it is ConnectionStatus.Lost })
        assertEquals(1, attempt)
    }

    @Test
    fun `reconnect fails cleanly when the host is gone`() = runTest {
        val (_, acceptor) = newHost()
        var attempt = 0
        val firstLink = mutableListOf<Link>()
        val client = ClientSession(
            ZpModule, "Anna", "anna-token",
            {
                attempt++
                if (attempt == 1) acceptor.connect("Anna").also { firstLink += it } else throw java.io.IOException("out of range")
            },
            backgroundScope, clientOptions,
        )
        client.connect()
        firstLink.single().close()
        assertEquals(ConnectionStatus.Lost(LostReason.CONNECTION_LOST), client.connection.first { it is ConnectionStatus.Lost })
        assertEquals(1 + clientOptions.reconnectAttempts, attempt)
        assertEquals(SubmitResult.Failed(ClientSession.NOT_CONNECTED), client.submit(ZpAction.PickUp))
    }

    @Test
    fun `silent connections are dropped by the heartbeat`() = runTest {
        val (host, acceptor) = newHost()
        val raw = RawClient(acceptor.connect("raw"))
        raw.send(NetMessage.Hello("Raw", "raw-token"))
        raw.expect<NetMessage.JoinAccepted>()
        host.lobby.first { it.seats.size == 2 }
        // The raw client never answers pings.
        advanceTimeBy(hostOptions.heartbeatIntervalMs * (hostOptions.heartbeatMissedLimit + 2))
        host.lobby.first { it.seats.size == 1 }
    }

    @Test
    fun `disconnected player is taken over by a bot and the game continues`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        val raw = RawClient(acceptor.connect("raw"))
        raw.send(NetMessage.Hello("Raw", "raw-token"))
        raw.expect<NetMessage.JoinAccepted>()
        host.startGame()
        raw.expect<NetMessage.GameState>()
        raw.link.close()
        host.lobby.first { lobby -> lobby.seats.any { it.id == "p1" && !it.connected } }
        val takenOver = host.lobby.first { lobby -> lobby.seats.any { it.id == "p1" && it.botControlled } }
        assertNotNull(takenOver)
        // The host plays automatically too; the game must reach the end with the bot playing for p1.
        val hostPlayer = autoPlay(host.hostPort, 5)
        val final = host.hostPort.view.first { it?.result != null }!!
        hostPlayer.cancel()
        assertEquals(2, final.result!!.ranking.size)
    }

    @Test
    fun `player leaving during the game is replaced by a bot immediately`() = runTest {
        val (host, acceptor) = newHost(ZpHouseRules(swapPhase = false))
        val raw = RawClient(acceptor.connect("raw"))
        raw.send(NetMessage.Hello("Raw", "raw-token"))
        raw.expect<NetMessage.JoinAccepted>()
        host.startGame()
        raw.send(NetMessage.Disconnect(DisconnectReason.LEFT))
        host.lobby.first { lobby -> lobby.seats.any { it.id == "p1" && it.botControlled && !it.connected } }
    }

    @Test
    fun `restart and return to lobby`() = runTest {
        val (host, acceptor) = newHost()
        val client = newClient(acceptor, "Anna")
        client.connect()
        host.addBot(BotDifficulty.NORMAL)
        host.startGame()
        val hostPlayer = autoPlay(host.hostPort, 1)
        val clientPlayer = autoPlay(client, 2)
        client.view.first { it?.result != null }
        assertEquals(null, host.startGame()) // "opnieuw spelen"
        client.view.first { it != null && it.result == null }
        hostPlayer.cancel()
        clientPlayer.cancel()
        host.returnToLobby()
        assertEquals(SessionPhase.LOBBY, client.lobby.first { it?.phase == SessionPhase.LOBBY }!!.phase)
    }

    @Test
    fun `local game against bots runs without any connection`() = runTest {
        val host = HostSession(ZpModule, "Ivo", ZpHouseRules(), backgroundScope, hostOptions, seedSource = { 7L })
        host.addBot(BotDifficulty.NORMAL)
        host.addBot(BotDifficulty.EASY)
        assertEquals(null, host.startGame())
        val player = autoPlay(host.hostPort, 3)
        val final = host.hostPort.view.first { it?.result != null }!!
        player.cancel()
        assertEquals(3, final.result!!.ranking.size)
        assertEquals(ConnectionStatus.Local, host.hostPort.connection.value)
    }

    @Test
    fun `saved local game can be restored`() = runTest {
        val host = HostSession(ZpModule, "Ivo", ZpHouseRules(), backgroundScope, hostOptions, seedSource = { 7L })
        host.addBot(BotDifficulty.NORMAL)
        host.startGame()
        host.hostPort.submit(ZpAction.Ready)
        val (state, seats) = host.snapshot()!!
        val text = ProtocolJson.json.encodeToString(ZpGameState.serializer(), state)

        val restored = HostSession(ZpModule, "Ivo", ZpHouseRules(), backgroundScope, hostOptions)
        restored.restore(ProtocolJson.json.decodeFromString(ZpGameState.serializer(), text), seats)
        val view = restored.hostPort.view.first { it != null }!!
        assertEquals(host.hostPort.view.value!!.myHand, view.myHand)
        assertEquals(SessionPhase.IN_GAME, restored.lobby.value.phase)
    }

    @Test
    fun `query hello does not take a seat`() = runTest {
        val (host, acceptor) = newHost()
        val raw = RawClient(acceptor.connect("raw"))
        raw.send(NetMessage.Hello("Raw", "t", HelloIntent.QUERY))
        val info = raw.expect<NetMessage.LobbyInfo>()
        assertEquals(1, info.playerCount)
        runCurrent()
        assertEquals(1, host.lobby.value.seats.size)
    }
}
