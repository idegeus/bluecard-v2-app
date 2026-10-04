package nl.bluecard.app.bluetooth

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyDeviceTest {

    private fun device(
        name: String? = "moto e13",
        bonded: Boolean = false,
        major: Int? = BluetoothClass.Device.Major.PHONE,
        type: Int = BluetoothDevice.DEVICE_TYPE_DUAL,
    ) = NearbyDevice("74:BE:F3:17:50:B0", name, bonded, major, type)

    @Test
    fun `named classic phones are probed`() {
        assertTrue(device().canHostGame)
        assertTrue(device(type = BluetoothDevice.DEVICE_TYPE_CLASSIC).canHostGame)
        assertTrue(device(major = BluetoothClass.Device.Major.COMPUTER).canHostGame)
        assertTrue(device().isPhone)
    }

    @Test
    fun `ble gadgets and nameless devices are skipped`() {
        assertFalse(device(type = BluetoothDevice.DEVICE_TYPE_LE).canHostGame)
        assertFalse(device(name = null, major = null, type = BluetoothDevice.DEVICE_TYPE_UNKNOWN).canHostGame)
        assertFalse(device(name = " ").canHostGame)
        assertFalse(device(major = BluetoothClass.Device.Major.AUDIO_VIDEO).canHostGame)
        assertFalse(device(major = BluetoothClass.Device.Major.WEARABLE).canHostGame)
    }

    @Test
    fun `paired phones are probed even without discovery data, paired headsets are not`() {
        assertTrue(device(name = null, bonded = true, major = null, type = BluetoothDevice.DEVICE_TYPE_UNKNOWN).canHostGame)
        assertFalse(device(bonded = true, major = BluetoothClass.Device.Major.AUDIO_VIDEO).canHostGame)
    }
}
