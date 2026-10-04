package nl.bluecard.app.nearby

/**
 * The few bytes a host sends over BLE while its lobby is open, as manufacturer data under company id 0xFFFF (the id
 * reserved for test/internal use, no registration needed): "BC", a version, the game, a random table id and the host's
 * name (as much as fits in a legacy advertisement).
 */
object TableBeaconFormat {
    const val COMPANY_ID = 0xFFFF
    val MAGIC = byteArrayOf('B'.code.toByte(), 'C'.code.toByte())
    private const val VERSION: Byte = 1

    /** A legacy advertisement holds 31 bytes; the manufacturer data element takes 4 of them (length, type, company). */
    const val MAX_PAYLOAD = 27
    private const val HEADER = 8

    data class Beacon(val gameIndex: Int, val tableId: Int, val hostName: String)

    fun encode(hostName: String, gameIndex: Int, tableId: Int): ByteArray {
        val header = byteArrayOf(
            MAGIC[0], MAGIC[1], VERSION, gameIndex.toByte(),
            (tableId ushr 24).toByte(), (tableId ushr 16).toByte(), (tableId ushr 8).toByte(), tableId.toByte(),
        )
        return header + nameBytes(hostName, MAX_PAYLOAD - HEADER)
    }

    fun decode(bytes: ByteArray?): Beacon? {
        if (bytes == null || bytes.size < HEADER || bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != VERSION) return null
        val id = ((bytes[4].toInt() and 0xFF) shl 24) or ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        val name = bytes.copyOfRange(HEADER, bytes.size).decodeToString().trim()
        return Beacon(bytes[3].toInt(), id, name)
    }

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
