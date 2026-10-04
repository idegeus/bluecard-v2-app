package nl.bluecard.multiplayer.session

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import nl.bluecard.engine.core.BotDifficulty

@Serializable
enum class SeatKind { HOST, REMOTE, BOT }

/** A seat at the table as everyone sees it. */
@Serializable
data class SeatInfo(
    val id: String,
    val name: String,
    val kind: SeatKind,
    val connected: Boolean = true,
    /** True when a bot plays for this seat (always for BOT seats; for REMOTE seats after a takeover). */
    val botControlled: Boolean = false,
    val difficulty: BotDifficulty? = null,
    /** Public id of the human player's phone (null for bots), for the shared leaderboard. */
    val deviceId: String? = null,
    /**
     * How the player looks: "emoji:<emoji>|#RRGGBB" or "photo:<base64 JPEG>" (small), null for the default
     * initial. Interpreted by the app only.
     */
    val avatar: String? = null,
    /** A table display (tablet): always watching, never seated. */
    val table: Boolean = false,
)

/**
 * LOBBY → (SHUFFLING: one player shakes their phone to shuffle) → IN_GAME → FINISHED. SHUFFLING only happens when
 * the host turned the shuffle ritual on.
 */
@Serializable
enum class SessionPhase { LOBBY, SHUFFLING, IN_GAME, FINISHED }

/** The host's table look (ids of the card back and the cloth); every phone at the table uses it during the session. */
@Serializable
data class TableStyle(val cardBack: String = "", val table: String = "")

/** Lobby information broadcast to all clients. [config] is the game's house rules as JSON. */
@Serializable
data class LobbySnapshot(
    val gameId: String,
    val gameName: String,
    val hostName: String,
    val seats: List<SeatInfo>,
    val minPlayers: Int,
    val maxPlayers: Int,
    val phase: SessionPhase,
    val config: JsonElement,
    /** People watching: joined while a game was running or the table was full. They get a seat in the next round. */
    val spectators: List<SeatInfo> = emptyList(),
    val style: TableStyle? = null,
    /** While [phase] is SHUFFLING: the seat that shuffles. */
    val shufflerId: String? = null,
)

sealed interface SubmitResult {
    data object Accepted : SubmitResult

    /** The host/engine refused the action; [reason] is a reject code such as "CARD_TOO_LOW". */
    data class Rejected(val reason: String) : SubmitResult

    /** The action could not be delivered or confirmed (no connection, timeout). */
    data class Failed(val reason: String) : SubmitResult
}

/** Connection state as shown to the player. */
sealed interface ConnectionStatus {
    /** Local game or this device is the host. */
    data object Local : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ConnectionStatus
    data class Lost(val reason: LostReason) : ConnectionStatus
    data class Rejected(val reason: String) : ConnectionStatus
}

enum class LostReason { CONNECTION_LOST, HOST_CLOSED, KICKED, VERSION_MISMATCH, LEFT, GAME_SWITCHED, HOST_MOVED }

/** One-off notifications for snackbars / banners. */
sealed interface SessionNotice {
    data class PlayerJoined(val name: String) : SessionNotice

    /** Someone joined to watch; they play from the next round. */
    data class SpectatorJoined(val name: String) : SessionNotice
    data class PlayerLeft(val name: String) : SessionNotice
    data class PlayerDisconnected(val name: String) : SessionNotice
    data class PlayerReconnected(val name: String) : SessionNotice
    data class BotTookOver(val name: String) : SessionNotice
    data class ActionRejected(val reason: String) : SessionNotice
    data class ProtocolProblem(val detail: String) : SessionNotice
    data class AcceptingStopped(val detail: String) : SessionNotice

    /** The host switched the table to another game; the app reconnects with that game. */
    data class GameSwitched(val gameId: String) : SessionNotice
}

/** Little social signals at the table. */
enum class SocialKind(val isReaction: Boolean) {
    /** Hurry up! Shakes and vibrates the phone of the player it is aimed at. */
    BUZZ(false),
    HEART(true),
    THUMBS_UP(true),
    CRY(true),
    LAUGH(true),

    /** Any emoji from [EmojiCatalog] (unlocked by winning). */
    REACTION(true),
    ;

    companion object {
        fun parse(name: String): SocialKind? = entries.firstOrNull { it.name == name }
    }
}

/** A buzz or reaction someone sent; [toId] is the buzzed player (null for reactions). */
data class SocialEvent(val fromId: String, val fromName: String, val kind: SocialKind, val toId: String? = null, val emoji: String? = null)

/** Who takes over as host when the host goes away. */
data class SuccessorInfo(val seatId: String, val address: String, val name: String)

/**
 * The whole table as the host has it, so the [SuccessorInfo] can carry on as host: game state, seats, the tokens
 * players use to reclaim their seats, and the rules.
 */
@Serializable
data class HostSnapshot(
    val gameId: String,
    val rulesVersion: Int,
    val config: JsonElement,
    /** Null while no game has been started. */
    val state: JsonElement? = null,
    val seats: List<SeatInfo>,
    val spectators: List<SeatInfo> = emptyList(),
    val tokens: Map<String, String> = emptyMap(),
    val phase: SessionPhase,
    val lastResult: nl.bluecard.engine.core.GameResult? = null,
    val shuffleTurn: Int = 0,
    val style: TableStyle? = null,
)
