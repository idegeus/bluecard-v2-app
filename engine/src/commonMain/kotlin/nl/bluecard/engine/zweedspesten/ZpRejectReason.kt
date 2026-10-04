package nl.bluecard.engine.zweedspesten

/** Stable reason codes for rejected actions. The app maps these to user-facing messages. */
enum class ZpRejectReason {
    UNKNOWN_PLAYER,
    GAME_FINISHED,
    WRONG_PHASE,
    NOT_YOUR_TURN,
    ALREADY_READY,
    CARD_NOT_AVAILABLE,
    EMPTY_SELECTION,
    DUPLICATE_CARDS,
    MIXED_RANKS,
    MULTIPLE_NOT_ALLOWED,
    MUST_PLAY_FACE_DOWN,
    WRONG_SOURCE,
    CARD_TOO_LOW,
    CARD_TOO_HIGH,
    INVALID_BLIND_INDEX,
    PILE_EMPTY,
    GAMBLE_NOT_ALLOWED,
    DRAW_PILE_EMPTY,
    TOO_FEW_PLAYERS,
    TOO_MANY_PLAYERS,
    NOT_ENOUGH_CARDS,
    INVALID_HAND_SIZE,
    NOTHING_TO_CHALLENGE,
    CANNOT_CHALLENGE_SELF,
    LAST_CARD_MUST_FIT,
    /** Only players still in the game may call "Vals!" (not who is already out). */
    NOT_PLAYING,
    /** The card just picked up may not be laid straight back again (too often): avoids an endless back-and-forth. */
    REPLAY_LIMIT,
    EXCHANGE_PENDING,
    NOT_THE_WINNER,
}
