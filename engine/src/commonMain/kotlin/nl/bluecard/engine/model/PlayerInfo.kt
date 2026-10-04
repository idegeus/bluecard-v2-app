package nl.bluecard.engine.model

import kotlinx.serialization.Serializable

/** Identity of a seat at the table. [id] is unique within a game, [name] is for display. */
@Serializable
data class PlayerInfo(val id: String, val name: String)
