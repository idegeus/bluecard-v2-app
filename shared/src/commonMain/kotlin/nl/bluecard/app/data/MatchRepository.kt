package nl.bluecard.app.data

import kotlinx.io.IOException
import nl.bluecard.app.platform.FileStore
import nl.bluecard.app.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import nl.bluecard.multiplayer.protocol.ProtocolJson
import nl.bluecard.multiplayer.session.MatchLog
import nl.bluecard.multiplayer.session.MatchRecord

/**
 * All finished games this phone knows: its own games (also against bots) and the shared games it got from other
 * phones. Feeds the leaderboard and the skin unlocks; exchanged with other phones through [MatchLog].
 */
class MatchRepository(private val files: FileStore, scope: CoroutineScope, private val platform: Platform) : MatchLog {
    private val json = ProtocolJson.json
    private val serializer = ListSerializer(MatchRecord.serializer())
    private val mutex = Mutex()
    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    private val _records = MutableStateFlow<List<MatchRecord>>(emptyList())
    val records: StateFlow<List<MatchRecord>> = _records.asStateFlow()

    init {
        scope.launch {
            _records.value = read()
            loaded.complete(Unit)
        }
    }

    override suspend fun shareable(): List<MatchRecord> {
        loaded.await()
        return _records.value.filter { it.shareable }.sortedByDescending { it.playedAt }.take(MatchLog.MAX_EXCHANGE)
    }

    override suspend fun merge(records: List<MatchRecord>) {
        loaded.await()
        mutex.withLock {
            val known = _records.value.map { it.id }.toSet()
            val fresh = records.filter { it.id !in known }.distinctBy { it.id }
            if (fresh.isEmpty()) return
            val updated = (_records.value + fresh).sortedByDescending { it.playedAt }.take(MAX_RECORDS)
            _records.value = updated
            write(updated)
        }
    }

    private suspend fun read(): List<MatchRecord> {
        val text = try {
            files.read(FILE_NAME)
        } catch (e: IOException) {
            platform.log(TAG, "cannot read match history", e)
            null
        } ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: IllegalArgumentException) {
            platform.log(TAG, "match history corrupt, starting over", e)
            emptyList()
        }
    }

    private suspend fun write(records: List<MatchRecord>) {
        try {
            files.write(FILE_NAME, json.encodeToString(serializer, records))
        } catch (e: IOException) {
            platform.log(TAG, "cannot save match history", e)
        }
    }

    private companion object {
        const val TAG = "MatchRepository"
        const val FILE_NAME = "matches.json"
        const val MAX_RECORDS = 1_000
    }
}
