package nl.bluecard.app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class BluetoothRole { HOST, JOIN }

/** Which runtime permissions are needed per Android version. Only what is strictly necessary is requested. */
object BluetoothPermissions {

    fun required(role: BluetoothRole): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (role) {
                // Advertise is needed to make the phone discoverable; connect to accept connections.
                BluetoothRole.HOST -> listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
                BluetoothRole.JOIN -> listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            when (role) {
                BluetoothRole.HOST -> emptyList() // BLUETOOTH / BLUETOOTH_ADMIN are install-time permissions
                BluetoothRole.JOIN -> listOf(Manifest.permission.ACCESS_FINE_LOCATION) // needed for discovery
            }
        }

    fun missing(context: Context, role: BluetoothRole): List<String> =
        required(role).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    fun hasConnect(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun hasScan(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

    /** True when the location permission is what is being asked (Android 11 and lower). */
    val needsLocationForScan: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
}
