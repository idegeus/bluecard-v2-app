package nl.bluecard.app.bluetooth

import java.util.UUID

object BluetoothConstants {
    /** RFCOMM service UUID of BlueCard. Hosts register it; clients connect to it. Never change it. */
    val SERVICE_UUID: UUID = UUID.fromString("5b1ec4a2-6f0e-4c1b-9a77-b1ecca7d0001")
    const val SERVICE_NAME = "BlueCard Zweeds Pesten"

    /** How long the host asks to be discoverable (seconds; Android caps this at 300 by default). */
    const val DISCOVERABLE_SECONDS = 300

    /** Upper bound for one incoming line, protects against corrupt streams. */
    const val MAX_LINE_CHARS = 256 * 1024
}
