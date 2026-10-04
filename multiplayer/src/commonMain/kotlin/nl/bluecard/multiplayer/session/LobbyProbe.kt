package nl.bluecard.multiplayer.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import nl.bluecard.multiplayer.protocol.DecodeResult
import nl.bluecard.multiplayer.protocol.HelloIntent
import nl.bluecard.multiplayer.protocol.JoinRejectReason
import nl.bluecard.multiplayer.protocol.NetMessage
import nl.bluecard.multiplayer.protocol.ProtocolCodec
import nl.bluecard.multiplayer.transport.Link
import kotlinx.io.IOException

sealed interface ProbeResult {
    data class Found(val info: NetMessage.LobbyInfo) : ProbeResult
    data object Incompatible : ProbeResult
    data class NoGame(val reason: String) : ProbeResult
}

/**
 * Asks a host for its lobby information without joining (HELLO with intent QUERY). Used while searching,
 * so players see "Zweeds Pesten — host Ivo — 2/5" even when all phones have the same Bluetooth name.
 */
object LobbyProbe {

    /** [hostAddress]: the address used to reach the host, so it learns its own (see [NetMessage.Hello.hostAddress]). */
    suspend fun query(link: Link, playerName: String, playerToken: String, timeoutMs: Long = 6_000, hostAddress: String = ""): ProbeResult {
        val codec = ProtocolCodec()
        return try {
            withTimeout(timeoutMs) {
                link.writeLine(codec.encode(1, NetMessage.Hello(playerName, playerToken, HelloIntent.QUERY, hostAddress = hostAddress)))
                var result: ProbeResult = ProbeResult.NoGame("no answer")
                for (i in 0 until MAX_MESSAGES) {
                    val line = link.readLine() ?: break
                    when (val decoded = codec.decode(line)) {
                        is DecodeResult.Ok -> when (val msg = decoded.envelope.msg) {
                            is NetMessage.LobbyInfo -> {
                                result = ProbeResult.Found(msg)
                                break
                            }
                            is NetMessage.JoinRejected -> {
                                result = if (msg.reason == JoinRejectReason.VERSION_MISMATCH) {
                                    ProbeResult.Incompatible
                                } else {
                                    ProbeResult.NoGame(msg.reason.name)
                                }
                                break
                            }
                            else -> Unit // e.g. PING; keep reading
                        }
                        is DecodeResult.VersionMismatch -> {
                            result = ProbeResult.Incompatible
                            break
                        }
                        else -> Unit
                    }
                }
                result
            }
        } catch (e: TimeoutCancellationException) {
            ProbeResult.NoGame("timeout")
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ProbeResult.NoGame(e.message ?: "io")
        } finally {
            link.close()
        }
    }

    private const val MAX_MESSAGES = 5
}
