package nl.bluecard.multiplayer.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import nl.bluecard.multiplayer.session.MatchRecord
import nl.bluecard.engine.core.GameResult
import nl.bluecard.multiplayer.session.LobbySnapshot
import nl.bluecard.multiplayer.session.SeatInfo
import nl.bluecard.multiplayer.session.SessionPhase

/** Every message is wrapped in an envelope carrying the protocol version and a per-connection sequence number. */
@Serializable
data class Envelope(
    val v: Int,
    val seq: Long,
    val msg: NetMessage,
)

@Serializable
enum class HelloIntent {
    /** Take a seat at the table (or reclaim it when reconnecting). */
    JOIN,

    /** Only ask for lobby information (used while searching for games), then disconnect. */
    QUERY,
}

@Serializable
enum class JoinRejectReason { LOBBY_FULL, GAME_IN_PROGRESS, VERSION_MISMATCH, HOST_CLOSING }

@Serializable
enum class DisconnectReason { LEFT, HOST_CLOSED, KICKED, TIMEOUT, VERSION_MISMATCH, HOST_MOVED }

@Serializable
enum class ErrorCode { MALFORMED, UNKNOWN_TYPE, VERSION_MISMATCH, NOT_JOINED, UNEXPECTED }

/**
 * All protocol messages. Game-specific payloads (views, actions, configuration) travel as [JsonElement]
 * produced by the game's own serializers, so this protocol does not depend on any particular card game.
 */
@Serializable
sealed interface NetMessage {

    /** Client → host: first message on every connection. */
    @Serializable
    @SerialName(MessageTypes.HELLO)
    data class Hello(
        val playerName: String,
        val playerToken: String,
        val intent: HelloIntent = HelloIntent.JOIN,
        val appVersion: String = "",
        /** Public, random id of the phone, used for the shared leaderboard (unlike the secret token). */
        val deviceId: String = "",
        /** The player's avatar (see SeatInfo.avatar); empty for the default. */
        val avatar: String = "",
        /** [ROLE_TABLE]: a shared display (tablet in the middle of the table) that only watches, never plays. */
        val role: String = ROLE_PLAYER,
    ) : NetMessage {
        companion object {
            const val ROLE_PLAYER = "PLAYER"
            const val ROLE_TABLE = "TABLE"
        }
    }

    /** Host → client: answer to a QUERY hello. */
    @Serializable
    @SerialName(MessageTypes.LOBBY_INFO)
    data class LobbyInfo(
        val hostName: String,
        val gameId: String,
        val gameName: String,
        val rulesVersion: Int,
        val playerCount: Int,
        val maxPlayers: Int,
        val phase: SessionPhase,
    ) : NetMessage

    @Serializable
    @SerialName(MessageTypes.JOIN_ACCEPTED)
    data class JoinAccepted(
        val playerId: String,
        val hostName: String,
        val gameId: String,
        val rulesVersion: Int,
        val reconnected: Boolean = false,
        /** Joined as a spectator (the game was running or the table was full); plays from the next round. */
        val spectator: Boolean = false,
    ) : NetMessage

    @Serializable
    @SerialName(MessageTypes.JOIN_REJECTED)
    data class JoinRejected(val reason: JoinRejectReason, val detail: String? = null) : NetMessage

    /** Host → clients: seats, connection status, rules and phase. Sent whenever any of these change. */
    @Serializable
    @SerialName(MessageTypes.PLAYER_LIST)
    data class PlayerList(val lobby: LobbySnapshot) : NetMessage

    @Serializable
    @SerialName(MessageTypes.GAME_START)
    data class GameStart(val gameId: String, val seats: List<SeatInfo>) : NetMessage

    /** Host → client: the receiving player's personal view of the authoritative state. */
    @Serializable
    @SerialName(MessageTypes.GAME_STATE)
    data class GameState(val stateVersion: Long, val view: JsonElement) : NetMessage

    /** Client → host: an intent. [actionId] increases per player and makes duplicates detectable. */
    @Serializable
    @SerialName(MessageTypes.PLAYER_ACTION)
    data class PlayerAction(val actionId: Long, val basedOnVersion: Long, val action: JsonElement) : NetMessage

    @Serializable
    @SerialName(MessageTypes.ACTION_RESULT)
    data class ActionResult(val actionId: Long, val accepted: Boolean, val reason: String? = null) : NetMessage

    @Serializable
    @SerialName(MessageTypes.GAME_END)
    data class GameEnd(val result: GameResult) : NetMessage

    /** Client → host: please send the full lobby and game state again. */
    @Serializable
    @SerialName(MessageTypes.SYNC_REQUEST)
    data object SyncRequest : NetMessage

    @Serializable
    @SerialName(MessageTypes.PING)
    data class Ping(val nonce: Long) : NetMessage

    @Serializable
    @SerialName(MessageTypes.PONG)
    data class Pong(val nonce: Long) : NetMessage

    @Serializable
    @SerialName(MessageTypes.DISCONNECT)
    data class Disconnect(val reason: DisconnectReason) : NetMessage

    /**
     * Host → clients: the host chose another game for this table. Clients reconnect with that game's module; the
     * host opens a new table for it right away.
     */
    @Serializable
    @SerialName(MessageTypes.SWITCH_GAME)
    data class SwitchGame(val gameId: String) : NetMessage

    /** Host → clients: a game just finished; everybody stores the same record. */
    @Serializable
    @SerialName(MessageTypes.MATCH_RECORDED)
    data class MatchRecorded(val record: MatchRecord) : NetMessage

    /** Both ways after joining: the finished games a phone knows, so leaderboards get merged. */
    @Serializable
    @SerialName(MessageTypes.MATCH_HISTORY)
    data class MatchHistory(val records: List<MatchRecord>) : NetMessage

    /**
     * A buzz or reaction ([kind] is a [nl.bluecard.multiplayer.session.SocialKind] name). Client → host without
     * [from]; the host fills in the sender and relays it to everybody.
     */
    @Serializable
    @SerialName(MessageTypes.SOCIAL)
    data class Social(val kind: String, val to: String? = null, val from: String = "", val emoji: String? = null) : NetMessage

    /** A chat line: client → host without [from]/[id]; the host numbers it and relays it to everybody. */
    @Serializable
    @SerialName(MessageTypes.CHAT)
    data class Chat(val text: String, val from: String = "", val id: Long = 0) : NetMessage

    /**
     * Host → clients: who takes over the table if the host goes away ([seatId], reachable at [address]); null when
     * nobody can.
     */
    @Serializable
    @SerialName(MessageTypes.SUCCESSOR)
    data class Successor(val seatId: String? = null, val address: String? = null, val name: String? = null) : NetMessage

    /** Host → successor only: everything needed to carry on as host. Sent again whenever something changes. */
    @Serializable
    @SerialName(MessageTypes.HANDOVER)
    data class Handover(val snapshot: nl.bluecard.multiplayer.session.HostSnapshot) : NetMessage

    /** Client → host: the shuffler finished shaking. */
    @Serializable
    @SerialName(MessageTypes.SHUFFLED)
    data class Shuffled(val entropy: Long = 0) : NetMessage

    @Serializable
    @SerialName(MessageTypes.ERROR)
    data class Error(val code: ErrorCode, val detail: String? = null) : NetMessage
}
