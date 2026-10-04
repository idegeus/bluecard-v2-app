package nl.bluecard.app.nearby

/**
 * The few bytes a host sends over BLE while it has a table open, as manufacturer data under company id 0xFFFF (the id
 * reserved for test/internal use, no registration needed): "BC", a version, the game, a random table id, flags
 * (game running; address included), the host's Bluetooth address when known (a phone cannot read its own; the host
 * learns it from the phones that connect, see `NetMessage.Hello.hostAddress`) and the host's name (as much as fits
 * in a legacy advertisement). With the address, other phones connect straight away: no Bluetooth search and the host
 * need not be visible.
 */
object TableBeaconFormat {
    const val COMPANY_ID = 0xFFFF
    val MAGIC = byteArrayOf('B'.code.toByte(), 'C'.code.toByte())
    private const val VERSION_1: Byte = 1
    private const val VERSION: Byte = 2

    /** A legacy advertisement holds 31 bytes; the manufacturer data element takes 4 of them (length, type, company). */
    const val MAX_PAYLOAD = 27
    private const val HEADER_1 = 8
    private const val HEADER = 9
    private const val ADDRESS_BYTES = 6
    private const val FLAG_IN_GAME = 1
    private const val FLAG_ADDRESS = 2

    data class Beacon(
        val gameIndex: Int,
        val tableId: Int,
        val hostName: String,
        /** A game is running (the lobby is closed): you can watch and play from the next round. */
        val inGame: Boolean = false,
        /** The host's Bluetooth address ("AA:BB:CC:DD:EE:FF"), when it knows it. */
        val address: String? = null,
    )

    fun encode(hostName: String, gameIndex: Int, tableId: Int, inGame: Boolean = false, address: String? = null): ByteArray {
        val addressBytes = address?.let(::parseAddress)
        val flags = (if (inGame) FLAG_IN_GAME else 0) or (if (addressBytes != null) FLAG_ADDRESS else 0)
        val header = byteArrayOf(
            MAGIC[0], MAGIC[1], VERSION, gameIndex.toByte(),
            (tableId ushr 24).toByte(), (tableId ushr 16).toByte(), (tableId ushr 8).toByte(), tableId.toByte(),
            flags.toByte(),
        ) + (addressBytes ?: ByteArray(0))
        return header + nameBytes(hostName, MAX_PAYLOAD - header.size)
    }

    fun decode(bytes: ByteArray?): Beacon? {
        if (bytes == null || bytes.size < HEADER_1 || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1]) return null
        val id = ((bytes[4].toInt() and 0xFF) shl 24) or ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        return when (bytes[2]) {
            VERSION_1 -> Beacon(bytes[3].toInt(), id, bytes.copyOfRange(HEADER_1, bytes.size).decodeToString().trim())
            VERSION -> {
                if (bytes.size < HEADER) return null
                val flags = bytes[8].toInt()
                val hasAddress = flags and FLAG_ADDRESS != 0
                val nameStart = HEADER + if (hasAddress) ADDRESS_BYTES else 0
                if (bytes.size < nameStart) return null
                Beacon(
                    gameIndex = bytes[3].toInt(),
                    tableId = id,
                    hostName = bytes.copyOfRange(nameStart, bytes.size).decodeToString().trim(),
                    inGame = flags and FLAG_IN_GAME != 0,
                    address = if (hasAddress) formatAddress(bytes.copyOfRange(HEADER, HEADER + ADDRESS_BYTES)) else null,
                )
            }
            else -> null
        }
    }

    /** "AA:BB:CC:DD:EE:FF" → 6 bytes, or null when it is not a usable Bluetooth address. */
    fun parseAddress(address: String): ByteArray? {
        val parts = address.split(':')
        if (parts.size != ADDRESS_BYTES || parts.any { it.length != 2 }) return null
        val bytes = parts.map { it.toIntOrNull(16) ?: return null }.map { it.toByte() }.toByteArray()
        // The placeholder Android hands out instead of the real address, and an all-zero address, are no use.
        if (bytes.all { it == 0.toByte() } || address.equals(HIDDEN_ADDRESS, ignoreCase = true)) return null
        return bytes
    }

    private fun formatAddress(bytes: ByteArray): String = bytes.joinToString(":") { (it.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0') }

    private const val HIDDEN_ADDRESS = "02:00:00:00:00:00"

    /** UTF-8 bytes of [name], cut at a character boundary so they fit in [max]. */
    private fun nameBytes(name: String, max: Int): ByteArray {
        var result = ByteArray(0)
        val text = name.trim()
        var i = 0
        while (i < text.length) {
            // Whole characters only: an emoji is two chars (a surrogate pair).
            val end = if (text[i].isHighSurrogate() && i + 1 < text.length) i + 2 else i + 1
            val next = result + text.substring(i, end).encodeToByteArray()
            if (next.size > max) break
            result = next
            i = end
        }
        return result
    }
}
