package nl.bluecard.multiplayer.protocol

/**
 * Version of the Bluetooth message protocol.
 *
 * Compatibility contract: the envelope (`v`, `seq`, `msg.type`) and the HELLO / JOIN_REJECTED / DISCONNECT
 * messages are frozen, so that two app versions can always at least tell each other they are incompatible.
 * Additive changes (new optional fields) keep the version; incompatible changes bump [CURRENT].
 *
 * v2: buzzes/reactions (SOCIAL), spectators, the shuffle phase (SHUFFLED) and the host's table style.
 */
object ProtocolVersion {
    const val CURRENT = 2
    const val MIN_SUPPORTED = 2

    fun isSupported(version: Int): Boolean = version in MIN_SUPPORTED..CURRENT
}
