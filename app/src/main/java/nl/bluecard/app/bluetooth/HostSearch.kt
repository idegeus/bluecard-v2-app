package nl.bluecard.app.bluetooth

import android.bluetooth.BluetoothClass

/** How a [searchUntilNewPhone] ended. */
enum class SearchEnd { FAILED, STOPPED_AT_NEW_PHONE, COMPLETE }

/**
 * Runs a Bluetooth search (12–20 s when left alone), but stops it at the first phone for which [isNew] holds, so that
 * phone can be checked right away instead of after the whole search (search again afterwards for the rest).
 * [onFound] sees every device found.
 */
suspend fun BluetoothController.searchUntilNewPhone(onFound: (NearbyDevice) -> Unit, isNew: (NearbyDevice) -> Boolean): SearchEnd {
    var ok = true
    var stopped = false
    discover().collect { event ->
        when (event) {
            is DiscoveryEvent.Found -> {
                onFound(event.device)
                if (!stopped && event.device.isPhone && event.device.canHostGame && isNew(event.device)) {
                    stopped = true
                    cancelDiscovery()
                }
            }
            is DiscoveryEvent.Failed -> ok = false
            DiscoveryEvent.Finished -> Unit
        }
    }
    return when {
        !ok -> SearchEnd.FAILED
        stopped -> SearchEnd.STOPPED_AT_NEW_PHONE
        else -> SearchEnd.COMPLETE
    }
}

/** A device known from a table announcement: its host is a phone that speaks Classic Bluetooth. */
fun announcedDevice(address: String, hostName: String): NearbyDevice =
    NearbyDevice(address = address, name = hostName, bonded = false, majorClass = BluetoothClass.Device.Major.PHONE)
