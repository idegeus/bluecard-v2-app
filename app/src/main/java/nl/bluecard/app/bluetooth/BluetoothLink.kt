package nl.bluecard.app.bluetooth

import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.bluecard.multiplayer.transport.Link
import nl.bluecard.multiplayer.transport.LinkAcceptor
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * [Link] over a connected RFCOMM socket: UTF-8 text, one JSON message per line.
 * Reads and writes block, so they run on the IO dispatcher. [close] unblocks a pending read.
 */
class BluetoothLink(
    private val socket: BluetoothSocket,
    override val description: String,
) : Link {

    /** The other phone's Bluetooth address (to reach it again, e.g. when it takes over as host). */
    override val remoteAddress: String? = runCatching { socket.remoteDevice?.address }.getOrNull()

    private val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
    private val writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))
    private val writeLock = Any()
    private val line = StringBuilder()

    @Volatile private var closed = false

    override suspend fun readLine(): String? = withContext(Dispatchers.IO) { readLineBlocking() }

    private fun readLineBlocking(): String? {
        line.setLength(0)
        while (true) {
            val c = try {
                reader.read()
            } catch (e: IOException) {
                if (closed) return null
                throw e
            }
            when (c) {
                -1 -> return if (line.isEmpty()) null else line.toString()
                '\n'.code -> return line.toString()
                '\r'.code -> Unit
                else -> {
                    if (line.length >= BluetoothConstants.MAX_LINE_CHARS) throw IOException("incoming line too long")
                    line.append(c.toChar())
                }
            }
        }
    }

    override suspend fun writeLine(line: String) = withContext(Dispatchers.IO) {
        if (closed) throw IOException("link closed")
        synchronized(writeLock) {
            writer.write(line)
            writer.write('\n'.code)
            writer.flush()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            socket.close()
        } catch (_: IOException) {
            // already closed
        }
    }
}

/** [LinkAcceptor] over an RFCOMM server socket. Closing it makes a blocked [accept] throw. */
class BluetoothLinkAcceptor(private val serverSocket: BluetoothServerSocket) : LinkAcceptor {

    @Volatile private var closed = false

    override suspend fun accept(): Link = withContext(Dispatchers.IO) {
        if (closed) throw IOException("acceptor closed")
        val socket = serverSocket.accept()
        val name = describe(socket)
        BluetoothLink(socket, name)
    }

    @Suppress("MissingPermission")
    private fun describe(socket: BluetoothSocket): String =
        try {
            socket.remoteDevice?.let { it.name ?: it.address } ?: "?"
        } catch (_: SecurityException) {
            socket.remoteDevice?.address ?: "?"
        }

    override fun close() {
        closed = true
        try {
            serverSocket.close()
        } catch (_: IOException) {
            // already closed
        }
    }
}
