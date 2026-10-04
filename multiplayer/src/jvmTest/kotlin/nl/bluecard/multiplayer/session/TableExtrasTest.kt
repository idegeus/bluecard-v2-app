package nl.bluecard.multiplayer.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spectators, buzzes/reactions, the shuffle ritual and the shared table style. */
@OptIn(ExperimentalCoroutinesApi::class)
class TableExtrasTest {

    private val hostOptions = HostOptions(botDelayMs = 10, autoTakeoverMs = 30_000, botShuffleMs = 1_000, shuffleTimeoutMs = 20_000)

    private fun TestScope.newHost(): Pair<HostSession<ZpHouseRules, ZpGameState, ZpAction, ZpPlayerView>, InMemoryAcceptor> {
        var now = 0L
        val host = HostSession(ZpModule, "Ivo", ZpHouseRules(), backgroundScope, hostOptions, seedSource = { 42L }, clock = { testScheduler.currentTime + now++ })
        val acceptor = InMemoryAcceptor()
        host.startAccepting(acceptor)
        return host to acceptor
    }

    private fun TestScope.newClient(acceptor: InMemoryAcceptor, name: String) =
        ClientSession(ZpModule, name, "token-$name", { acceptor.connect(name) }, backgroundScope, clock = { testScheduler.currentTime })

    @Test
    fun `joining during a game makes you a spectator who plays the next round`() = runTest {
        val (host, acceptor) = newHost()
        host.addBot(BotDifficulty.EASY)
        assertNull(host.startGame())

        val late = newClient(acceptor, "Anna")
        assertEquals(ConnectionStatus.Connected, late.connect())
        val lobby = host.lobby.first { it.spectators.isNotEmpty() }
        assertEquals(listOf("Anna"), lobby.spectators.map { it.name })
        assertEquals(2, lobby.seats.size)

        // The spectator sees the table, but no cards of anyone and no moves.
        val view = late.view.first { it != null }!!
        assertTrue(view.myHand.isEmpty())
        assertFalse(view.legal.hasAnyMove)
        assertTrue(view.players.none { it.id == late.playerId.value })
        // Acting is refused.
        assertEquals(SubmitResult.Rejected("NOT_PLAYING"), late.submit(ZpAction.PickUp))

        // Next round: a seat at the table.
        host.returnToLobby()
        val next = host.lobby.first { it.phase == SessionPhase.LOBBY }
        assertTrue(next.spectators.isEmpty())
        assertEquals(listOf("Ivo", "Bot Bas", "Anna"), next.seats.map { it.name })
    }

    @Test
    fun `a full table lets newcomers watch`() = runTest {
        val (host, acceptor) = newHost()
        repeat(4) { host.addBot(BotDifficulty.EASY) }
        val extra = newClient(acceptor, "Bram")
        assertEquals(ConnectionStatus.Connected, extra.connect())
        assertEquals(listOf("Bram"), host.lobby.first { it.spectators.isNotEmpty() }.spectators.map { it.name })
    }

    @Test
    fun `reactions reach everybody and buzzes are rate limited`() = runTest {
        val (host, acceptor) = newHost()
        val anna = newClient(acceptor, "Anna")
        anna.connect()
        host.lobby.first { it.seats.size == 2 }
        val annaEvents = mutableListOf<SocialEvent>()
        val hostEvents = mutableListOf<SocialEvent>()
        backgroundScope.launch { anna.social.collect { annaEvents += it } }
        backgroundScope.launch { host.hostPort.social.collect { hostEvents += it } }
        runCurrent()

        assertTrue(host.hostPort.sendSocial(SocialKind.HEART))
        assertTrue(anna.sendSocial(SocialKind.LAUGH))
        runCurrent()
        assertEquals(listOf(SocialKind.HEART, SocialKind.LAUGH), annaEvents.map { it.kind })
        assertEquals(listOf("Ivo", "Anna"), hostEvents.map { it.fromName })

        // No buzzing in the lobby, nor right after something happened.
        assertFalse(host.hostPort.sendSocial(SocialKind.BUZZ, "p1"))
        assertNull(host.startGame())
        anna.view.first { it != null }
        assertFalse(host.hostPort.sendSocial(SocialKind.BUZZ, "p1"))
        // Anna has to get ready (swap phase) and does nothing for 5 s: now she can be buzzed, three times a minute.
        advanceTimeBy(SocialLimits.BUZZ_IDLE_MS + 100)
        repeat(SocialLimits.BUZZES_PER_MINUTE) { assertTrue(host.hostPort.sendSocial(SocialKind.BUZZ, "p1")) }
        assertFalse(host.hostPort.sendSocial(SocialKind.BUZZ, "p1"))
        runCurrent()
        assertEquals(SocialLimits.BUZZES_PER_MINUTE, annaEvents.count { it.kind == SocialKind.BUZZ && it.toId == "p1" })
        advanceTimeBy(61_000)
        assertTrue(host.hostPort.sendSocial(SocialKind.BUZZ, "p1"))
        // You cannot buzz yourself or a bot.
        assertFalse(host.hostPort.sendSocial(SocialKind.BUZZ, HostSession.HOST_SEAT_ID))
    }

    @Test
    fun `the shuffler shakes before the cards are dealt`() = runTest {
        val (host, acceptor) = newHost()
        host.shuffleRitual = true
        val anna = newClient(acceptor, "Anna")
        anna.connect()
        host.lobby.first { it.seats.size == 2 }

        // Round 1: the host shuffles.
        val start = async { host.startGame() }
        val shuffling = host.lobby.first { it.phase == SessionPhase.SHUFFLING }
        assertEquals(HostSession.HOST_SEAT_ID, shuffling.shufflerId)
        assertEquals(SessionPhase.SHUFFLING, anna.lobby.first { it?.phase == SessionPhase.SHUFFLING }!!.phase)
        // Anna cannot finish someone else's shuffle.
        anna.shuffled(7)
        runCurrent()
        assertEquals(SessionPhase.SHUFFLING, host.lobby.value.phase)
        host.hostPort.shuffled(1234)
        assertNull(start.await())
        assertEquals(SessionPhase.IN_GAME, host.lobby.value.phase)

        // Round 2: Anna's turn to shuffle, from her own phone.
        host.returnToLobby()
        val second = async { host.startGame() }
        assertEquals("p1", host.lobby.first { it.phase == SessionPhase.SHUFFLING }.shufflerId)
        anna.shuffled(99)
        assertNull(second.await())
        assertEquals(SessionPhase.IN_GAME, anna.lobby.first { it?.phase == SessionPhase.IN_GAME }!!.phase)
    }

    @Test
    fun `bots shuffle by themselves`() = runTest {
        val (host, _) = newHost()
        host.shuffleRitual = true
        host.addBot(BotDifficulty.EASY)
        host.hostPort.let { port ->
            val first = async { host.startGame() }
            host.lobby.first { it.phase == SessionPhase.SHUFFLING }
            port.shuffled(1)
            assertNull(first.await())
        }
        host.returnToLobby()
        val second = async { host.startGame() }
        assertEquals("bot1", host.lobby.first { it.phase == SessionPhase.SHUFFLING }.shufflerId)
        advanceTimeBy(hostOptions.botShuffleMs + 10)
        assertNull(second.await())
    }

    @Test
    fun `the host's table style travels with the lobby`() = runTest {
        val (host, acceptor) = newHost()
        val anna = newClient(acceptor, "Anna")
        anna.connect()
        host.setStyle(TableStyle("cherry", "night"))
        assertEquals(TableStyle("cherry", "night"), anna.lobby.first { it?.style != null }!!.style)
    }

    @Test
    fun `chat reaches everybody, cleaned and rate limited`() = runTest {
        val (host, acceptor) = newHost()
        val anna = newClient(acceptor, "Anna")
        anna.connect()
        host.lobby.first { it.seats.size == 2 }
        assertTrue(anna.sendChat("  hoi\nallemaal  "))
        assertEquals("hoi allemaal", host.hostPort.chat.first { it.isNotEmpty() }.last().text)
        assertTrue(host.hostPort.sendChat("hallo Anna"))
        val annaChat = anna.chat.first { it.size == 2 }
        assertEquals(listOf("Anna", "Ivo"), annaChat.map { it.fromName })
        assertFalse(anna.sendChat("   "))
        assertEquals(ChatLimits.MAX_LENGTH, ChatLimits.clean("x".repeat(500))!!.length)
        // Five lines per 10 seconds.
        repeat(ChatLimits.MESSAGES_PER_10S - 1) { assertTrue(host.hostPort.sendChat("spam $it")) }
        assertFalse(host.hostPort.sendChat("te veel"))
    }

    @Test
    fun `reactions carry an emoji from the catalog only`() = runTest {
        val (host, acceptor) = newHost()
        val anna = newClient(acceptor, "Anna")
        anna.connect()
        host.lobby.first { it.seats.size == 2 }
        val events = mutableListOf<SocialEvent>()
        backgroundScope.launch { anna.social.collect { events += it } }
        runCurrent()
        assertTrue(host.hostPort.sendSocial(SocialKind.REACTION, emoji = "🔥"))
        assertFalse(host.hostPort.sendSocial(SocialKind.REACTION, emoji = "hallo"))
        assertFalse(host.hostPort.sendSocial(SocialKind.REACTION, emoji = null))
        runCurrent()
        assertEquals(listOf("🔥"), events.map { it.emoji })
    }

    @Test
    fun `the bot level applies to all bots, but not during a game`() = runTest {
        val (host, _) = newHost()
        host.addBot(BotDifficulty.EASY)
        host.addBot(BotDifficulty.EASY)
        assertTrue(host.setBotDifficulty(BotDifficulty.NORMAL))
        assertEquals(listOf(BotDifficulty.NORMAL, BotDifficulty.NORMAL), host.lobby.value.seats.filter { it.kind == SeatKind.BOT }.map { it.difficulty })
        assertNull(host.lobby.value.seats.single { it.kind == SeatKind.HOST }.difficulty)
        assertNull(host.startGame())
        assertFalse(host.setBotDifficulty(BotDifficulty.EASY))
    }

    @Test
    fun `a table display watches and never gets a seat`() = runTest {
        val (host, acceptor) = newHost()
        host.addBot(BotDifficulty.EASY)
        val tablet = ClientSession(ZpModule, "Tablet", "token-tablet", { acceptor.connect("Tablet") }, backgroundScope, asTable = true)
        assertEquals(ConnectionStatus.Connected, tablet.connect())
        // Even in the lobby with free seats it is a display.
        val lobby = host.lobby.first { it.spectators.isNotEmpty() }
        assertTrue(lobby.spectators.single().table)
        assertEquals(2, lobby.seats.size)
        assertNull(host.startGame())
        assertTrue(tablet.view.first { it != null }!!.myHand.isEmpty())
        host.returnToLobby()
        val next = host.lobby.first { it.phase == SessionPhase.LOBBY }
        assertEquals(listOf("Tablet"), next.spectators.map { it.name })
        assertEquals(2, next.seats.size)
    }
}
