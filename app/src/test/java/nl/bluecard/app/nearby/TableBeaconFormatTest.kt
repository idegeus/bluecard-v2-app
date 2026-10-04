package nl.bluecard.app.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableBeaconFormatTest {
    @Test
    fun `round trip`() {
        val bytes = TableBeaconFormat.encode("Ivo", 2, -123456789)
        assertEquals(TableBeaconFormat.Beacon(2, -123456789, "Ivo"), TableBeaconFormat.decode(bytes))
    }

    @Test
    fun `long names are cut to fit one advertisement, never in the middle of a character`() {
        val bytes = TableBeaconFormat.encode("Ğüñtér-Ëlïsābeth van den Berg 🃏", 0, 7)
        assertTrue(bytes.size <= TableBeaconFormat.MAX_PAYLOAD)
        val name = TableBeaconFormat.decode(bytes)!!.hostName
        assertTrue(name, "Ğüñtér-Ëlïsābeth van den Berg 🃏".startsWith(name))
    }

    @Test
    fun `other data is ignored`() {
        assertNull(TableBeaconFormat.decode(byteArrayOf(1, 2, 3)))
        assertNull(TableBeaconFormat.decode("XX12345678".encodeToByteArray()))
        assertNull(TableBeaconFormat.decode(null))
    }

    @Test
    fun `emoji in a name stay whole`() {
        val name = TableBeaconFormat.decode(TableBeaconFormat.encode("🃏🃏🃏🃏🃏🃏", 0, 1))!!.hostName
        assertEquals("🃏🃏🃏🃏", name)
    }

    @Test
    fun `the host address and a running game travel along`() {
        val bytes = TableBeaconFormat.encode("Testhost", 1, 42, inGame = true, address = "74:be:f3:15:99:9b")
        assertTrue(bytes.size <= TableBeaconFormat.MAX_PAYLOAD)
        assertEquals(
            TableBeaconFormat.Beacon(1, 42, "Testhost", inGame = true, address = "74:BE:F3:15:99:9B"),
            TableBeaconFormat.decode(bytes),
        )
    }

    @Test
    fun `with an address a long name still fits`() {
        val bytes = TableBeaconFormat.encode("Elisabeth van den Berg", 0, 7, address = "AA:BB:CC:DD:EE:FF")
        assertTrue(bytes.size <= TableBeaconFormat.MAX_PAYLOAD)
        val beacon = TableBeaconFormat.decode(bytes)!!
        assertEquals("AA:BB:CC:DD:EE:FF", beacon.address)
        assertTrue("Elisabeth van den Berg".startsWith(beacon.hostName))
    }

    @Test
    fun `addresses Android hides or that are not addresses are left out`() {
        for (bad in listOf("02:00:00:00:00:00", "00:00:00:00:00:00", "nonsense", "AA:BB:CC", "GG:BB:CC:DD:EE:FF")) {
            assertNull(bad, TableBeaconFormat.decode(TableBeaconFormat.encode("Ivo", 0, 1, address = bad))!!.address)
        }
    }

    @Test
    fun `announcements of the first version are still understood`() {
        val v1 = byteArrayOf('B'.code.toByte(), 'C'.code.toByte(), 1, 2, 0, 0, 0, 9) + "Ivo".encodeToByteArray()
        assertEquals(TableBeaconFormat.Beacon(2, 9, "Ivo"), TableBeaconFormat.decode(v1))
    }
}
