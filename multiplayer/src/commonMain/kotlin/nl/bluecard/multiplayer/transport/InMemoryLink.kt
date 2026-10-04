package nl.bluecard.multiplayer.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.io.IOException

/** In-memory [Link] pair, used by unit tests and for simulating connections without Bluetooth. */
class InMemoryLink private constructor(
    override val description: String,
    private val incoming: Channel<String>,
    private val outgoing: Channel<String>,
) : Link {

    override val remoteAddress: String? get() = description

    override suspend fun readLine(): String? = try {
        incoming.receive()
    } catch (_: ClosedReceiveChannelException) {
        null
    }

    override suspend fun writeLine(line: String) {
        try {
            outgoing.send(line)
        } catch (e: ClosedSendChannelException) {
            throw IOException("link closed", e)
        }
    }

    override fun close() {
        outgoing.close()
        incoming.close()
    }

    companion object {
        /** Returns two connected ends: what one writes, the other reads. */
        fun pair(a: String = "A", b: String = "B"): Pair<InMemoryLink, InMemoryLink> {
            val ab = Channel<String>(Channel.UNLIMITED)
            val ba = Channel<String>(Channel.UNLIMITED)
            return InMemoryLink(b, incoming = ba, outgoing = ab) to InMemoryLink(a, incoming = ab, outgoing = ba)
        }
    }
}

/** In-memory server: [connect] creates a link pair and queues the server end for [accept]. */
class InMemoryAcceptor : LinkAcceptor {
    private val pending = Channel<Link>(Channel.UNLIMITED)

    fun connect(clientName: String = "client"): Link {
        val (clientEnd, serverEnd) = InMemoryLink.pair(a = clientName, b = "host")
        if (pending.trySend(serverEnd).isFailure) throw IOException("acceptor closed")
        return clientEnd
    }

    override suspend fun accept(): Link = try {
        pending.receive()
    } catch (e: ClosedReceiveChannelException) {
        throw IOException("acceptor closed", e)
    }

    override fun close() {
        pending.close()
    }
}
