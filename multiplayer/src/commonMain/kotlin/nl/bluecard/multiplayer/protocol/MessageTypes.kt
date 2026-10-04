package nl.bluecard.multiplayer.protocol

/** Wire names of all message types (the `type` field inside `msg`). */
object MessageTypes {
    const val HELLO = "HELLO"
    const val LOBBY_INFO = "LOBBY_INFO"
    const val JOIN_ACCEPTED = "JOIN_ACCEPTED"
    const val JOIN_REJECTED = "JOIN_REJECTED"
    const val PLAYER_LIST = "PLAYER_LIST"
    const val GAME_START = "GAME_START"
    const val GAME_STATE = "GAME_STATE"
    const val PLAYER_ACTION = "PLAYER_ACTION"
    const val ACTION_RESULT = "ACTION_RESULT"
    const val GAME_END = "GAME_END"
    const val SYNC_REQUEST = "SYNC_REQUEST"
    const val PING = "PING"
    const val PONG = "PONG"
    const val DISCONNECT = "DISCONNECT"
    const val ERROR = "ERROR"
    const val MATCH_RECORDED = "MATCH_RECORDED"
    const val MATCH_HISTORY = "MATCH_HISTORY"
    const val SWITCH_GAME = "SWITCH_GAME"
    const val SOCIAL = "SOCIAL"
    const val SHUFFLED = "SHUFFLED"
    const val SUCCESSOR = "SUCCESSOR"
    const val HANDOVER = "HANDOVER"
    const val CHAT = "CHAT"

    val ALL: Set<String> = setOf(
        HELLO, LOBBY_INFO, JOIN_ACCEPTED, JOIN_REJECTED, PLAYER_LIST, GAME_START, GAME_STATE,
        PLAYER_ACTION, ACTION_RESULT, GAME_END, SYNC_REQUEST, PING, PONG, DISCONNECT, ERROR, MATCH_RECORDED,
        MATCH_HISTORY, SWITCH_GAME, SOCIAL, SHUFFLED, SUCCESSOR, HANDOVER, CHAT,
    )
}
