package nl.bluecard.app.data

import kotlinx.io.IOException
import nl.bluecard.app.platform.FileStore
import nl.bluecard.app.platform.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import nl.bluecard.engine.zweedspesten.ZpModule
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.session.SeatInfo

/**
 * A local game against bots, saved after every move so it survives the app being closed. [state] is the
 * game's own state as JSON; [gameId] says which game it belongs to (files from version 1 are always
 * Zweeds Pesten).
 */
@Serializable
data class SavedGame(
    val formatVersion: Int = FORMAT_VERSION,
    val gameId: String = ZpModule.GAME_ID,
    val state: JsonElement,
    val seats: List<SeatInfo>,
    val savedAtMillis: Long,
) {
    companion object {
        const val FORMAT_VERSION = 2
        val READABLE_VERSIONS = 1..FORMAT_VERSION
    }
}

class SavedGameRepository(private val files: FileStore, private val platform: Platform) {

    private val json = ProtocolJson.json

    suspend fun save(game: SavedGame) {
        try {
            files.write(FILE_NAME, json.encodeToString(SavedGame.serializer(), game))
        } catch (e: IOException) {
            platform.log(TAG, "cannot save game", e)
        }
    }

    suspend fun load(): SavedGame? {
        val text = try {
            files.read(FILE_NAME)
        } catch (e: IOException) {
            platform.log(TAG, "cannot read saved game", e)
            null
        } ?: return null
        return try {
            json.decodeFromString(SavedGame.serializer(), text).takeIf { it.formatVersion in SavedGame.READABLE_VERSIONS }
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException as well.
            platform.log(TAG, "saved game corrupt, deleting", e)
            files.delete(FILE_NAME)
            null
        }
    }

    suspend fun exists(): Boolean = files.exists(FILE_NAME)

    suspend fun delete() = files.delete(FILE_NAME)

    private companion object {
        const val TAG = "SavedGameRepository"
        const val FILE_NAME = "saved_game.json"
    }
}
