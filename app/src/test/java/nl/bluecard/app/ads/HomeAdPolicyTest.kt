package nl.bluecard.app.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAdPolicyTest {
    private val now = 1_000_000_000L
    private val minute = 60_000L

    @Test
    fun `very first start screen has no banner`() {
        assertFalse(HomeAdPolicy.showBanner(lastShownAt = null, now = now))
    }

    @Test
    fun `returning to the start screen within half an hour shows the banner`() {
        assertTrue(HomeAdPolicy.showBanner(now - 5 * minute, now))
        assertTrue(HomeAdPolicy.showBanner(now - 30 * minute, now))
    }

    @Test
    fun `first start screen after a quiet half hour has no banner`() {
        assertFalse(HomeAdPolicy.showBanner(now - 31 * minute, now))
    }

    @Test
    fun `a clock that jumped back does not count as recent`() {
        assertFalse(HomeAdPolicy.showBanner(now + minute, now))
    }
}
