package nl.bluecard.app.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import nl.bluecard.app.BlueCardApp
import nl.bluecard.app.MainActivity
import nl.bluecard.app.R
import nl.bluecard.app.res.Resources
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.ui.text.GameTexts
import nl.bluecard.app.android.R as AndroidR

/**
 * "Somebody nearby opened a table" without draining batteries:
 *
 * * the host advertises a tiny BLE packet ([TableBeaconFormat]) only while its lobby is open, in the low-power mode
 *   (about once a second, low transmit power, not connectable);
 * * other phones register one *filtered* background scan with a PendingIntent: the Bluetooth chip matches the packet
 *   (hardware filter), the app is not running meanwhile and is only woken up for a match, then shows one notification
 *   per table.
 */
object NearbyTableAlerts {
    private const val TAG = "NearbyTableAlerts"
    private const val CHANNEL_ID = "nearby_tables"
    private const val ACTION_FOUND = "nl.bluecard.app.TABLE_NEARBY"
    private const val PREFS = "nearby_tables"
    private const val SEEN_KEEP_MS = 3 * 60 * 60 * 1000L

    /**
     * A notification lives this long after the table was last heard; while the lobby is open the beacon keeps renewing
     * it (quietly, at most every [REFRESH_MS]), so it disappears by itself soon after the table closes or starts.
     */
    private const val ALIVE_MS = 2 * 60 * 1000L
    private const val KEY_OWN_ADDRESS = "own_address"
    private const val REFRESH_MS = 60 * 1000L

    // ---------------------------------------------------------------- host: advertise

    private var advertising: AdvertiseCallback? = null

    @SuppressLint("MissingPermission") // checked in canAdvertise()
    fun startAnnouncing(context: Context, hostName: String, gameId: String, tableId: Int, inGame: Boolean) {
        stopAnnouncing(context)
        if (!canAdvertise(context)) return
        val advertiser = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser ?: return
        val gameIndex = GameKind.entries.indexOfFirst { it.id == gameId }.coerceAtLeast(0)
        val payload = TableBeaconFormat.encode(hostName, gameIndex, tableId, inGame, ownAddress(context))
        val settings = AdvertiseSettings.Builder()
            // A few times a second: phones looking for a table see it within a second (the background scan of the
            // others is low-power anyway). Only while this phone hosts a table.
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(TableBeaconFormat.COMPANY_ID, payload)
            .build()
        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                Log.w(TAG, "advertising failed: $errorCode")
            }
        }
        try {
            advertiser.startAdvertising(settings, data, callback)
            advertising = callback
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission to advertise", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth off", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAnnouncing(context: Context) {
        val callback = advertising ?: return
        advertising = null
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot stop advertising", e)
        } catch (e: IllegalStateException) {
            // Bluetooth already off: nothing is being sent.
        }
    }

    /** This phone's own Bluetooth address, as phones that connected to it reported it (null until one did). */
    fun ownAddress(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_OWN_ADDRESS, null)?.takeIf { TableBeaconFormat.parseAddress(it) != null }

    /** Remembers [address] as this phone's own; true when it is new (the announcement should then be renewed). */
    fun rememberOwnAddress(context: Context, address: String): Boolean {
        if (TableBeaconFormat.parseAddress(address) == null || address.equals(ownAddress(context), ignoreCase = true)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_OWN_ADDRESS, address.uppercase()) }
        return true
    }

    private fun canAdvertise(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(context, Manifest.permission.BLUETOOTH_ADVERTISE)

    // ---------------------------------------------------------------- everybody: listen in the background

    /** Registers (or removes) the background scan. Safe to call often: the PendingIntent identifies the one scan. */
    @SuppressLint("MissingPermission") // checked in canScan()
    fun setListening(context: Context, enabled: Boolean) {
        val scanner = scanner(context) ?: return
        val intent = scanIntent(context)
        try {
            scanner.stopScan(intent)
            if (!enabled || !canScan(context)) return
            val filter = ScanFilter.Builder()
                .setManufacturerData(TableBeaconFormat.COMPANY_ID, TableBeaconFormat.MAGIC, byteArrayOf(-1, -1))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
            val result = scanner.startScan(listOf(filter), settings, intent)
            Log.d(TAG, "background scan registered: $result")
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission to scan", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth off", e)
        }
    }

    private fun scanner(context: Context): BluetoothLeScanner? =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { runCatching { it.isEnabled }.getOrDefault(false) }?.bluetoothLeScanner

    private fun scanIntent(context: Context): PendingIntent {
        val intent = Intent(context, TableFoundReceiver::class.java).setAction(ACTION_FOUND)
        // Mutable: the system adds the scan results to it.
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    private fun canScan(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ---------------------------------------------------------------- a table was seen

    internal fun onResults(context: Context, results: List<ScanResult>) {
        Log.d(TAG, "scan results: ${results.size}")
        val app = context.applicationContext as? BlueCardApp ?: return
        // Already at a table (or hosting one): no need to tell.
        if (app.container.sessions.active.value != null) return
        if (!app.container.settings.value.nearbyAlerts) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        for (result in results) {
            val beacon = TableBeaconFormat.decode(result.scanRecord?.getManufacturerSpecificData(TableBeaconFormat.COMPANY_ID)) ?: continue
            // Only an open lobby is news; a running game is announced for those looking for it (to watch along).
            if (beacon.inGame) continue
            val key = "seen_${beacon.tableId}"
            val refreshKey = "refresh_${beacon.tableId}"
            val first = now - prefs.getLong(key, 0L) >= SEEN_KEEP_MS
            val dismissed = prefs.getBoolean("gone_${beacon.tableId}", false)
            if (!first && (dismissed || now - prefs.getLong(refreshKey, 0L) < REFRESH_MS)) continue
            prefs.edit {
                // Forget old tables so the file stays small.
                prefs.all.filter { (k, v) -> k.startsWith("seen_") && (v as? Long ?: 0L) < now - SEEN_KEEP_MS }.keys.forEach { old ->
                    remove(old)
                    remove(old.replace("seen_", "refresh_"))
                    remove(old.replace("seen_", "gone_"))
                }
                if (first) putLong(key, now)
                putLong(refreshKey, now)
            }
            // The first time with sound; afterwards only keeping it alive.
            notify(context, beacon, alert = first)
        }
    }

    @SuppressLint("MissingPermission") // checked just before
    private fun notify(context: Context, beacon: TableBeaconFormat.Beacon, alert: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) return
        createChannel(context)
        val game = GameKind.entries.getOrNull(beacon.gameIndex)
        val gameName = game?.let { GameTexts.gameName(Resources, it) } ?: ""
        val open = PendingIntent.getActivity(
            context,
            beacon.tableId,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_JOIN_HOST, beacon.hostName)
                .putExtra(EXTRA_JOIN_GAME, game?.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val host = beacon.hostName.ifBlank { "BlueCard" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(AndroidR.drawable.ic_notification)
            .setContentTitle(Resources.getString(R.string.nearby_alert_title, host))
            .setContentText(Resources.getString(R.string.nearby_alert_text, gameName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setTimeoutAfter(ALIVE_MS)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .setDeleteIntent(dismissIntent(context, beacon.tableId))
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE + (beacon.tableId and 0xFFF), notification)
    }

    /** Swiped away: then it stays away for this table (also while it keeps sending). */
    private fun dismissIntent(context: Context, tableId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        tableId,
        Intent(context, TableFoundReceiver::class.java).setAction(ACTION_DISMISSED).putExtra(EXTRA_TABLE, tableId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun onDismissed(context: Context, tableId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("gone_$tableId", true) }
    }

    internal const val ACTION_DISMISSED = "nl.bluecard.app.TABLE_DISMISSED"
    const val EXTRA_JOIN_HOST = "join_host"
    const val EXTRA_JOIN_GAME = "join_game"
    internal const val EXTRA_TABLE = "table"

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, Resources.getString(R.string.nearby_alert_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = Resources.getString(R.string.nearby_alert_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private const val NOTIFICATION_BASE = 3000
}

/** Woken by the system when the background scan sees a table (the app process starts if needed). */
class TableFoundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NearbyTableAlerts.ACTION_DISMISSED) {
            NearbyTableAlerts.onDismissed(context, intent.getIntExtra(NearbyTableAlerts.EXTRA_TABLE, 0))
            return
        }
        val results = IntentCompat.getParcelableArrayListExtra(intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
        if (!results.isNullOrEmpty()) NearbyTableAlerts.onResults(context, results)
    }
}

/**
 * After a restart of the phone or an app update the background scan is gone; receiving this starts the app process,
 * whose start-up registers it again (see BlueCardApp).
 */
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Only the system's restart/update broadcasts; the work (registering the scan) happened in BlueCardApp.onCreate.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.d("NearbyTableAlerts", "restarted: background scan registered at app start")
    }
}
