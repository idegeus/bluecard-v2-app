package nl.bluecard.multiplayer.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nl.bluecard.engine.core.BotDifficulty
import nl.bluecard.engine.core.GameResult
import nl.bluecard.engine.core.RankingEntry
import nl.bluecard.engine.model.Card
import nl.bluecard.engine.zweedspesten.ZpAction
import nl.bluecard.engine.zweedspesten.ZpHouseRules
import nl.bluecard.multiplayer.session.LobbySnapshot
import nl.bluecard.multiplayer.session.MatchPlayer
import nl.bluecard.multiplayer.session.MatchRecord
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SeatKind
import nl.bluecard.multiplayer.session.SessionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {

    private val codec = ProtocolCodec()
    private val json = ProtocolJson.json

    private val seats = listOf(
        SeatInfo("host", "Ivo", SeatKind.HOST),
        SeatInfo("p1", "Anna", SeatKind.REMOTE, connected = false),
        SeatInfo("bot2", "Bot Bas", SeatKind.BOT, botControlled = true, difficulty = BotDifficulty.NORMAL),
    )

    private val record = MatchRecord(
        id = "dev-a-1",
        gameId = "pesten",
        playedAt = 1_700_000_000_000,
        players = listOf(
            MatchPlayer("dev-a", "Ivo", 1, human = true),
            MatchPlayer("dev-b", "Anna", 2, human = true, gaveUp = true),
            MatchPlayer(null, "Bot Bas", 3, human = false),
        ),
    )

    private val allMessages: List<NetMessage> = listOf(
        NetMessage.Hello("Anna", "token-1", HelloIntent.JOIN, "1.0"),
        NetMessage.Hello("Anna", "token-1", HelloIntent.QUERY),
        NetMessage.LobbyInfo("Ivo", "zweeds-pesten", "Zweeds Pesten", 1, 2, 5, SessionPhase.LOBBY),
        NetMessage.JoinAccepted("p1", "Ivo", "zweeds-pesten", 1, reconnected = true),
        NetMessage.JoinRejected(JoinRejectReason.LOBBY_FULL, "5/5"),
        NetMessage.PlayerList(
            LobbySnapshot(
                gameId = "zweeds-pesten",
                gameName = "Zweeds Pesten",
                hostName = "Ivo",
                seats = seats,
                minPlayers = 2,
                maxPlayers = 5,
                phase = SessionPhase.IN_GAME,
                config = json.encodeToJsonElement(ZpHouseRules.serializer(), ZpHouseRules()),
            ),
        ),
        NetMessage.GameStart("zweeds-pesten", seats),
        NetMessage.GameState(12, buildJsonObject { put("anything", 1) }),
        NetMessage.PlayerAction(
            3,
            12,
            json.encodeToJsonElement(ZpAction.serializer(), ZpAction.Play(listOf(Card.of("8C"), Card.of("8D")))),
        ),
        NetMessage.ActionResult(3, accepted = false, reason = "CARD_TOO_LOW"),
        NetMessage.GameEnd(GameResult(listOf(RankingEntry("p1", "Anna", 1, 0), RankingEntry("host", "Ivo", 2, 4)))),
        NetMessage.SyncRequest,
        NetMessage.Ping(7),
        NetMessage.Pong(7),
        NetMessage.Disconnect(DisconnectReason.HOST_CLOSED),
        NetMessage.Error(ErrorCode.MALFORMED, "bad"),
        NetMessage.MatchRecorded(record),
        NetMessage.SwitchGame("pesten"),
        NetMessage.Social("HEART", from = "p1"),
        NetMessage.Social("BUZZ", to = "host", from = "p1"),
        NetMessage.Shuffled(77),
        NetMessage.Successor("p1", "AA:BB", "Anna"),
        NetMessage.Chat("hoi", from = "p1", id = 3),
        NetMessage.Social("REACTION", from = "p1", emoji = "🔥"),
        NetMessage.Handover(
            nl.bluecard.multiplayer.session.HostSnapshot(
                gameId = "zweeds-pesten",
                rulesVersion = 7,
                config = kotlinx.serialization.json.JsonObject(emptyMap()),
                seats = emptyList(),
                phase = SessionPhase.LOBBY,
                tokens = mapOf("p1" to "t"),
            ),
        ),
        NetMessage.MatchHistory(listOf(record, record.copy(id = "dev-a-2"))),
    )

    @Test
    fun `every message type round trips`() {
        val seen = mutableSetOf<String>()
        allMessages.forEachIndexed { i, message ->
            val line = codec.encode(i.toLong(), message)
            val decoded = codec.decode(line)
            assertTrue("$message -> $decoded", decoded is DecodeResult.Ok)
            val envelope = (decoded as DecodeResult.Ok).envelope
            assertEquals(message, envelope.msg)
            assertEquals(i.toLong(), envelope.seq)
            assertEquals(ProtocolVersion.CURRENT, envelope.v)
            seen += json.parseToJsonElement(line).let { (it as kotlinx.serialization.json.JsonObject)["msg"] }
                .let { (it as kotlinx.serialization.json.JsonObject)["type"] }
                .let { (it as JsonPrimitive).content }
        }
        assertEquals("all protocol types covered", MessageTypes.ALL, seen)
    }

    @Test
    fun `encoded messages are single readable lines`() {
        for (message in allMessages) {
            val line = codec.encode(1, message)
            assertFalse(line.contains('\n'))
        }
        val hello = codec.encode(5, NetMessage.Hello("Anna", "t", HelloIntent.JOIN, "1.0", "dev-a"))
        assertEquals(
            "{\"v\":2,\"seq\":5,\"msg\":{\"type\":\"HELLO\",\"playerName\":\"Anna\",\"playerToken\":\"t\",\"intent\":\"JOIN\",\"appVersion\":\"1.0\",\"deviceId\":\"dev-a\",\"avatar\":\"\",\"role\":\"PLAYER\",\"hostAddress\":\"\"}}",
            hello,
        )
    }

    @Test
    fun `names with newlines and quotes stay on one line`() {
        val line = codec.encode(1, NetMessage.Hello("A\"n\nna", "t"))
        assertFalse(line.contains('\n'))
        assertEquals("A\"n\nna", ((codec.decode(line) as DecodeResult.Ok).envelope.msg as NetMessage.Hello).playerName)
    }

    @Test
    fun `other protocol versions are detected before decoding the body`() {
        val line = "{\"v\":99,\"seq\":1,\"msg\":{\"type\":\"HELLO_V99\",\"whatever\":true}}"
        assertEquals(DecodeResult.VersionMismatch(99), codec.decode(line))
    }

    @Test
    fun `unknown message types are reported`() {
        val line = "{\"v\":2,\"seq\":1,\"msg\":{\"type\":\"TELEPORT\"}}"
        assertEquals(DecodeResult.UnknownType("TELEPORT"), codec.decode(line))
    }

    @Test
    fun `corrupt input never throws`() {
        val inputs = listOf(
            "",
            "garbage",
            "{",
            "[]",
            "42",
            "{\"seq\":1}",
            "{\"v\":\"one\",\"msg\":{}}",
            "{\"v\":2}",
            "{\"v\":2,\"msg\":{}}",
            "{\"v\":2,\"msg\":{\"type\":42}}",
            "{\"v\":2,\"seq\":1,\"msg\":{\"type\":\"PING\"}}",
            "{\"v\":2,\"seq\":1,\"msg\":{\"type\":\"PLAYER_ACTION\",\"actionId\":\"x\"}}",
            "{\"v\":2,\"seq\":1,\"msg\":{\"type\":\"JOIN_REJECTED\",\"reason\":\"NOPE\"}}",
            "\u0000\u0001\u0002",
            "x".repeat(ProtocolCodec.MAX_MESSAGE_CHARS + 1),
        )
        for (input in inputs) {
            val result = codec.decode(input)
            assertTrue("$input -> $result", result is DecodeResult.Malformed || result is DecodeResult.UnknownType)
        }
    }

    @Test
    fun `unknown extra fields from newer minor versions are ignored`() {
        val line = "{\"v\":2,\"seq\":1,\"extra\":1,\"msg\":{\"type\":\"PING\",\"nonce\":3,\"newField\":\"x\"}}"
        assertEquals(NetMessage.Ping(3), (codec.decode(line) as DecodeResult.Ok).envelope.msg)
    }
}
