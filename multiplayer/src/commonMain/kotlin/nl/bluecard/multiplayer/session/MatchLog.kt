package nl.bluecard.multiplayer.session

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.Serializable

/** One player in a finished game. Bots have no [deviceId]. */
@Serializable
data class MatchPlayer(
    val deviceId: String? = null,
    val name: String,
    val position: Int,
    val human: Boolean,
    /** Gave up or left during the game (a bot finished it): counts as a loss whatever the bot achieved. */
    val gaveUp: Boolean = false,
    /** "Vals!" called rightly / wrongly, and caught cheating, in this game. */
    val rightCalls: Int = 0,
    val wrongCalls: Int = 0,
    val caught: Int = 0,
    /** Cheated without anybody calling "Vals!" (the ninja). */
    val unnoticed: Int = 0,
)

/**
 * The outcome of one finished game. [id] is created by the host and is the same on every phone at the table,
 * so the same game is never counted twice when phones exchange their history.
 */
@Serializable
data class MatchRecord(
    val id: String,
    val gameId: String,
    val playedAt: Long,
    val players: List<MatchPlayer>,
) {
    /** Games between at least two people are shared with other phones; games against bots stay private. */
    val shareable: Boolean get() = players.count { it.human } >= 2
}

/**
 * Where finished games are kept. Phones at the same table exchange their shareable records when they connect
 * (a grow-only set keyed by [MatchRecord.id]), which keeps everybody's leaderboard in sync.
 */
interface MatchLog {
    suspend fun shareable(): List<MatchRecord>

    suspend fun merge(records: List<MatchRecord>)

    companion object {
        /** Most records exchanged at once (the newest). */
        const val MAX_EXCHANGE = 300

        val NONE: MatchLog = object : MatchLog {
            override suspend fun shareable(): List<MatchRecord> = emptyList()
            override suspend fun merge(records: List<MatchRecord>) = Unit
        }
    }
}

/** A [MatchLog] in memory (tests, and as reference behaviour). */
class InMemoryMatchLog : MatchLog {
    private val lock = SynchronizedObject()
    private val records = linkedMapOf<String, MatchRecord>()

    val all: List<MatchRecord> get() = synchronized(lock) { records.values.toList() }

    override suspend fun shareable(): List<MatchRecord> =
        synchronized(lock) { records.values.filter { it.shareable }.sortedByDescending { it.playedAt }.take(MatchLog.MAX_EXCHANGE) }

    override suspend fun merge(records: List<MatchRecord>) = synchronized(lock) {
        records.forEach { if (it.id !in this.records) this.records[it.id] = it }
    }
}
