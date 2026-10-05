package nl.bluecard.app.store

import nl.bluecard.app.stats.Progress
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    private val nothing = Entitlements()

    @Test
    fun `product ids are the ones to create in the Play Console`() {
        assertEquals(
            listOf("no_ads", "bundle_all", "skin_diamond_holo", "tip_small", "tip_medium", "tip_large"),
            Products.all,
        )
        assertTrue(Products.isTip("tip_medium"))
        assertFalse(Products.isTip("no_ads"))
    }

    @Test
    fun `no ads comes with the no-ads purchase and with the bundle`() {
        assertFalse(nothing.adsRemoved)
        assertTrue(Entitlements(setOf(Products.NO_ADS)).adsRemoved)
        assertTrue(Entitlements(setOf(Products.BUNDLE_ALL)).adsRemoved)
        assertFalse(Entitlements(setOf(Products.skin("diamond_holo"))).adsRemoved)
    }

    @Test
    fun `a premium skin is unlocked by buying it or the bundle, never by playing`() {
        val holo = CardBackSkin.DIAMOND_HOLO
        val veteran = Progress(wins = 500, played = 1000)
        assertFalse(Skins.isUnlocked(holo, veteran, nothing))
        assertTrue(Skins.isUnlocked(holo, Progress(), Entitlements(setOf(Products.skin(holo.id)))))
        assertTrue(Skins.isUnlocked(holo, Progress(), Entitlements(setOf(Products.BUNDLE_ALL))))
        // Skins you earn by playing are not for sale and not affected.
        assertFalse(Skins.isUnlocked(CardBackSkin.RAINBOW_ROAD, Progress(), Entitlements(setOf(Products.BUNDLE_ALL))))
    }

    @Test
    fun `a bought skin stays chosen, an unbought one falls back`() {
        Skins.apply(CardBackSkin.DIAMOND_HOLO.id, "green", Progress(), Entitlements(setOf(Products.BUNDLE_ALL)))
        assertEquals(CardBackSkin.DIAMOND_HOLO, Skins.cardBack)
        Skins.apply(CardBackSkin.DIAMOND_HOLO.id, "green", Progress(), nothing)
        assertEquals(CardBackSkin.CLASSIC, Skins.cardBack)
    }
}
