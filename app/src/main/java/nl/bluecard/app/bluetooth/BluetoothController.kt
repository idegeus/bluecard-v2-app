package nl.bluecard.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import nl.bluecard.multiplayer.transport.Link
import nl.bluecard.multiplayer.transport.LinkAcceptor
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AdapterState { UNSUPPORTED, OFF, TURNING_ON, ON, TURNING_OFF }

/** A device seen during discovery or known from pairing. */
data class NearbyDevice(
    val address: String,
    val name: String?,
    val bonded: Boolean,
    /** [BluetoothClass.Device.Major] value, or null when unknown. */
    val majorClass: Int? = null,
    /** [BluetoothDevice.DEVICE_TYPE_CLASSIC] etc.; [BluetoothDevice.DEVICE_TYPE_UNKNOWN] when unknown. */
    val type: Int = BluetoothDevice.DEVICE_TYPE_UNKNOWN,
) {
    val isPhone: Boolean get() = majorClass == BluetoothClass.Device.Major.PHONE

    /**
     * Whether it makes sense to look for a BlueCard game on this device: it must be a phone, tablet/computer
     * or unclassified device; unpaired devices must also speak Classic Bluetooth (BLE-only gadgets cannot host
     * RFCOMM) and have a name. Probing everything would waste seconds per headphone or watch.
     */
    val canHostGame: Boolean
        get() {
            val classOk = majorClass == null || majorClass in HOST_CAPABLE_CLASSES
            if (bonded) return classOk
            // Phones always announce a name during discovery; nameless entries are BLE beacons and the like.
            return type != BluetoothDevice.DEVICE_TYPE_LE && !name.isNullOrBlank() && classOk
        }

    private companion object {
        val HOST_CAPABLE_CLASSES = setOf(
            BluetoothClass.Device.Major.PHONE,
            BluetoothClass.Device.Major.COMPUTER,
            BluetoothClass.Device.Major.UNCATEGORIZED,
            BluetoothClass.Device.Major.MISC,
        )
    }
}

sealed interface DiscoveryEvent {
    data class Found(val device: NearbyDevice) : DiscoveryEvent
    data object Finished : DiscoveryEvent
    data class Failed(val reason: String) : DiscoveryEvent
}

/**
 * Thin wrapper around the Android Bluetooth APIs. Every call checks permissions first and converts
 * [SecurityException]s into normal failures, so a revoked permission can never crash the app.
 */
@SuppressLint("MissingPermission") // permissions are checked via BluetoothPermissions before each call
class BluetoothController(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<AdapterState> = _state.asStateFlow()

    val isSupported: Boolean get() = adapter != null

    init {
        if (adapter != null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    _state.value = readState()
                }
            }
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                BLUETOOTH_BROADCAST_FLAGS,
            )
        }
    }

    /** Until when this phone is discoverable (as reported by the system after asking), 0 = not. */
    val visibleUntil = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun refreshState() {
        _state.value = readState()
    }

    private fun readState(): AdapterState {
        val a = adapter ?: return AdapterState.UNSUPPORTED
        return when (a.state) {
            BluetoothAdapter.STATE_ON -> AdapterState.ON
            BluetoothAdapter.STATE_TURNING_ON -> AdapterState.TURNING_ON
            BluetoothAdapter.STATE_TURNING_OFF -> AdapterState.TURNING_OFF
            else -> AdapterState.OFF
        }
    }

    /** The phone's own Bluetooth name, shown to the host so they can tell others what to look for. */
    fun localName(): String? {
        if (!BluetoothPermissions.hasConnect(context)) return null
        return try {
            adapter?.name
        } catch (e: SecurityException) {
            null
        }
    }

    fun enableIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

    fun discoverableIntent(): Intent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BluetoothConstants.DISCOVERABLE_SECONDS)

    fun bondedDevices(): List<NearbyDevice> {
        val a = adapter ?: return emptyList()
        if (!BluetoothPermissions.hasConnect(context)) return emptyList()
        return try {
            a.bondedDevices.orEmpty().map { describe(it, bonded = true, deviceClass = null) }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /** Classic Bluetooth discovery (~12 s). Collecting starts it, cancelling the collection stops it. */
    fun discover(): Flow<DiscoveryEvent> = callbackFlow {
        val a = adapter
        if (a == null || !BluetoothPermissions.hasScan(context)) {
            trySend(DiscoveryEvent.Failed("permission"))
            close()
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = IntentCompat.getParcelableExtra(
                            intent,
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java,
                        ) ?: return
                        val deviceClass = IntentCompat.getParcelableExtra(
                            intent,
                            BluetoothDevice.EXTRA_CLASS,
                            BluetoothClass::class.java,
                        )
                        val bonded = try {
                            device.bondState == BluetoothDevice.BOND_BONDED
                        } catch (e: SecurityException) {
                            false
                        }
                        val found = describe(device, bonded, deviceClass)
                        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: found.name
                        trySend(DiscoveryEvent.Found(found.copy(name = name)))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        trySend(DiscoveryEvent.Finished)
                        close()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, BLUETOOTH_BROADCAST_FLAGS)
        val started = try {
            if (a.isDiscovering) a.cancelDiscovery()
            a.startDiscovery()
        } catch (e: SecurityException) {
            false
        }
        if (!started) {
            trySend(DiscoveryEvent.Failed("start"))
            close()
        }
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            cancelDiscovery()
        }
    }

    fun cancelDiscovery() {
        if (!BluetoothPermissions.hasScan(context)) return
        try {
            adapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.w(TAG, "cancelDiscovery not permitted", e)
        }
    }

    /** Opens the RFCOMM server socket for hosting. Throws [IOException] when Bluetooth is unavailable. */
    fun openServer(): LinkAcceptor {
        val a = adapter ?: throw IOException("no bluetooth")
        if (!BluetoothPermissions.hasConnect(context)) throw IOException("permission")
        return try {
            BluetoothLinkAcceptor(
                a.listenUsingInsecureRfcommWithServiceRecord(BluetoothConstants.SERVICE_NAME, BluetoothConstants.SERVICE_UUID),
            )
        } catch (e: SecurityException) {
            throw IOException("permission", e)
        }
    }

    /**
     * Connects to the BlueCard service on [address]. Uses an insecure RFCOMM socket, so no pairing dialog
     * is needed. Cancelling the coroutine aborts the connection attempt.
     */
    suspend fun connect(address: String): Link {
        val a = adapter ?: throw IOException("no bluetooth")
        if (!BluetoothPermissions.hasConnect(context)) throw IOException("permission")
        if (!BluetoothAdapter.checkBluetoothAddress(address)) throw IOException("invalid address")
        val device = a.getRemoteDevice(address)
        // Discovery slows down connections considerably; always stop it first.
        cancelDiscovery()
        val socket = try {
            device.createInsecureRfcommSocketToServiceRecord(BluetoothConstants.SERVICE_UUID)
        } catch (e: SecurityException) {
            throw IOException("permission", e)
        }
        connectSocket(socket)
        return BluetoothLink(socket, safeName(device) ?: address)
    }

    /** Runs the blocking [BluetoothSocket.connect] on its own thread so it can be aborted by closing the socket. */
    private suspend fun connectSocket(socket: BluetoothSocket) = suspendCancellableCoroutine { cont ->
        val thread = Thread({
            try {
                socket.connect()
                cont.resume(Unit)
            } catch (e: IOException) {
                runCatching { socket.close() }
                cont.resumeWithException(e)
            } catch (e: SecurityException) {
                runCatching { socket.close() }
                cont.resumeWithException(IOException("permission", e))
            }
        }, "bt-connect")
        cont.invokeOnCancellation { runCatching { socket.close() } }
        thread.start()
    }

    private fun describe(device: BluetoothDevice, bonded: Boolean, deviceClass: BluetoothClass?): NearbyDevice {
        val (type, major) = try {
            device.type to (deviceClass ?: device.bluetoothClass)?.majorDeviceClass
        } catch (e: SecurityException) {
            BluetoothDevice.DEVICE_TYPE_UNKNOWN to deviceClass?.majorDeviceClass
        }
        return NearbyDevice(device.address, safeName(device), bonded, major, type)
    }

    private fun safeName(device: BluetoothDevice): String? =
        try {
            device.name
        } catch (e: SecurityException) {
            null
        }

    private companion object {
        const val TAG = "BluetoothController"

        /**
         * Bluetooth broadcasts are sent by the Bluetooth process (uid 1002), not by system_server, so a
         * NOT_EXPORTED receiver never gets them ("Exported Denial" in logcat). They are protected
         * broadcasts that ordinary apps cannot send, so exporting the receiver is safe.
         */
        const val BLUETOOTH_BROADCAST_FLAGS = ContextCompat.RECEIVER_EXPORTED
    }
}
