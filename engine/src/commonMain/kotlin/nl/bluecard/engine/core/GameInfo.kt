package nl.bluecard.engine.core

/** Static description of a card game. */
data class GameInfo(
    /** Stable identifier used in the network protocol, e.g. "zweeds-pesten". */
    val id: String,
    val displayName: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    /** Bumped whenever the serialized state/action/view format of this game changes incompatibly. */
    val rulesVersion: Int,
)
