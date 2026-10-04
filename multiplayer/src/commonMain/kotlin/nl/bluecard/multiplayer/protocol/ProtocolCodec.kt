package nl.bluecard.multiplayer.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Shared JSON configuration for the protocol and for game payloads inside it. */
object ProtocolJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }
}

/** Result of decoding one incoming line. Decoding never throws. */
sealed interface DecodeResult {
    data class Ok(val envelope: Envelope) : DecodeResult
    data class VersionMismatch(val remoteVersion: Int) : DecodeResult
    data class UnknownType(val type: String) : DecodeResult
    data class Malformed(val reason: String) : DecodeResult
}

/**
 * Encodes messages to single-line JSON and decodes them defensively: version first, then the message type,
 * then the full message. Corrupt or unknown input yields a [DecodeResult] instead of an exception.
 */
class ProtocolCodec(private val json: Json = ProtocolJson.json) {

    fun encode(seq: Long, message: NetMessage): String =
        json.encodeToString(Envelope.serializer(), Envelope(ProtocolVersion.CURRENT, seq, message))

    fun decode(line: String): DecodeResult {
        if (line.length > MAX_MESSAGE_CHARS) return DecodeResult.Malformed("message too long (${line.length})")
        val root = try {
            json.parseToJsonElement(line)
        } catch (e: SerializationException) {
            return DecodeResult.Malformed("invalid json: ${e.message?.take(80)}")
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Malformed("invalid json: ${e.message?.take(80)}")
        }
        val obj = root as? JsonObject ?: return DecodeResult.Malformed("not an object")
        val version = runCatching { obj["v"]?.jsonPrimitive?.intOrNull }.getOrNull()
            ?: return DecodeResult.Malformed("missing version")
        if (!ProtocolVersion.isSupported(version)) return DecodeResult.VersionMismatch(version)
        val msg = obj["msg"] as? JsonObject ?: return DecodeResult.Malformed("missing msg")
        val type = runCatching { msg["type"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            ?: return DecodeResult.Malformed("missing type")
        if (type !in MessageTypes.ALL) return DecodeResult.UnknownType(type)
        return try {
            DecodeResult.Ok(json.decodeFromJsonElement(Envelope.serializer(), obj))
        } catch (e: SerializationException) {
            DecodeResult.Malformed("bad $type: ${e.message?.take(120)}")
        } catch (e: IllegalArgumentException) {
            DecodeResult.Malformed("bad $type: ${e.message?.take(120)}")
        }
    }

    companion object {
        /** Upper bound for one message; a full game view is a few KB. */
        const val MAX_MESSAGE_CHARS = 256 * 1024
    }
}
