package nl.bluecard.app

import nl.bluecard.app.bluetooth.BluetoothController
import nl.bluecard.app.platform.AndroidPlatform

/** The Android-only parts of the container. */
val AppContainer.android: AndroidPlatform get() = platform as AndroidPlatform

val AppContainer.bluetooth: BluetoothController get() = android.bluetooth
