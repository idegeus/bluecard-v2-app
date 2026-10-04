package nl.bluecard.app.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private class SeenTable(val beacon: TableBeaconFormat.Beacon, val seenAt: Long)

/**
 * While somebody is looking for a table (start screen, join screen): an active BLE scan for table announcements.
 * Hosts are seen within a second or two; with the host's address in the announcement a phone connects straight
 * away, without a Bluetooth search. Stops as soon as the flow is no longer collected.
 */
object TableBeaconScanner {
    private const val TAG = "TableBeacons"

    /** An announcement not seen again for this long counts as gone (hosts send several per second). */
    private const val FORGET_MS = 15_000L

    /**
     * The tables announced recently (newest information per table), emitted when that list changes. Empty (and
     * nothing scanned) without permission or with Bluetooth off.
     */
    @SuppressLint("MissingPermission") // checked in canScan()
    fun tables(context: Context): Flow<List<TableBeaconFormat.Beacon>> = callbackFlow {
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { runCatching { it.isEnabled }.getOrDefault(false) }?.bluetoothLeScanner
        if (scanner == null || !canScan(context)) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        val seen = linkedMapOf<Int, SeenTable>()
        fun publish() {
            val now = SystemClock.elapsedRealtime()
            seen.values.removeAll { now - it.seenAt > FORGET_MS }
            trySend(seen.values.map { it.beacon })
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = onBatchScanResults(listOf(result))

            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    val bytes = result.scanRecord?.getManufacturerSpecificData(TableBeaconFormat.COMPANY_ID)
                    val beacon = TableBeaconFormat.decode(bytes) ?: continue
                    seen[beacon.tableId] = SeenTable(beacon, SystemClock.elapsedRealtime())
                }
                publish()
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "scan failed: $errorCode")
            }
        }
        val filter = ScanFilter.Builder()
            .setManufacturerData(TableBeaconFormat.COMPANY_ID, TableBeaconFormat.MAGIC, byteArrayOf(-1, -1))
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission to scan", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth off", e)
        }
        trySend(emptyList())
        // Tables that stopped announcing drop off even when nothing new comes in.
        val sweeper = launch {
            while (true) {
                delay(FORGET_MS / 3)
                publish()
            }
        }
        awaitClose {
            sweeper.cancel()
            try {
                scanner.stopScan(callback)
            } catch (e: SecurityException) {
                Log.w(TAG, "cannot stop scan", e)
            } catch (e: IllegalStateException) {
                // Bluetooth went off: the scan is gone already.
            }
        }
    }.distinctUntilChanged()

    private fun canScan(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
