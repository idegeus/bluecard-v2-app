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
}
