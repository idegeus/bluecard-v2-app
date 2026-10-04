package nl.bluecard.multiplayer.transport

import kotlinx.io.IOException

/**
 * A bidirectional, ordered, line-based text connection to one remote device.
 * Implemented on top of Bluetooth RFCOMM in the app and in memory for tests.
 */
interface Link {
    /** Human-readable description of the remote end (device name / address). */
    val description: String

    /**
     * How the remote end can be reached again (Bluetooth address, peer name), as accepted by the transport's connect;
     * null when unknown. Used to tell everybody where the table moves when the host leaves.
     */
    val remoteAddress: String? get() = null

    /** Next line, or null when the remote side closed the connection. Throws [IOException] on failure. */
    suspend fun readLine(): String?

    /** Sends one line (without line separator). Throws [IOException] when the connection is broken. */
    suspend fun writeLine(line: String)

    /** Closes the connection. Safe to call multiple times and from any thread. */
    fun close()
}

/** Server side: hands out incoming connections. */
interface LinkAcceptor {
    /** Suspends until a client connects. Throws [IOException] once the acceptor is closed or broken. */
    suspend fun accept(): Link

    fun close()
}
