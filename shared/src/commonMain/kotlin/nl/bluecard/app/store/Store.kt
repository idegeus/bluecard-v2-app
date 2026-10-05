package nl.bluecard.app.store

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import nl.bluecard.app.ui.theme.CardBackSkin
import nl.bluecard.app.ui.theme.TableSkin

/**
 * What is for sale. The ids must match the in-app products in the Play Console (and later App Store Connect)
 * exactly; prices are set there, the app shows whatever the store reports.
 */
object Products {
    /** No more banners on the start and end screens. Bought once. */
    const val NO_ADS = "no_ads"

    /** No ads and every premium skin, now and later. Bought once. */
    const val BUNDLE_ALL = "bundle_all"

    /** Tips for the maker: can be bought again and again; any tip makes you a supporter. */
    const val TIP_SMALL = "tip_small"
    const val TIP_MEDIUM = "tip_medium"
    const val TIP_LARGE = "tip_large"
    val TIPS = listOf(TIP_SMALL, TIP_MEDIUM, TIP_LARGE)

    /** A premium skin, bought on its own: "skin_<skin id>" (e.g. skin_diamond_holo). */
    fun skin(skinId: String): String = "skin_$skinId"

    /** Everything that, once bought, stays yours (and comes back after reinstalling). */
    val permanent: List<String>
        get() = listOf(NO_ADS, BUNDLE_ALL) +
            CardBackSkin.entries.filter { it.isPremium }.map { skin(it.id) } +
            TableSkin.entries.filter { it.isPremium }.map { skin(it.id) }

    val all: List<String> get() = permanent + TIPS

    fun isTip(productId: String): Boolean = productId in TIPS
}

/** A product as the store offers it: its name and the price in the buyer's currency, ready to show. */
data class StoreProduct(val id: String, val title: String, val price: String)

sealed interface StoreEvent {
    data class Bought(val productId: String) : StoreEvent

    /** Waiting for the payment (e.g. paying cash at a shop); it unlocks once the store confirms. */
    data class Pending(val productId: String) : StoreEvent
    data object Cancelled : StoreEvent
    data class Failed(val reason: String) : StoreEvent
}

/** The platform's store (Google Play Billing on Android; StoreKit on iOS later). */
interface AppStore {
    /** True once the store answered with products: false before the app is in the store, offline or unsupported. */
    val ready: StateFlow<Boolean>
    val products: StateFlow<Map<String, StoreProduct>>

    /** Permanent products this account owns, as the store reports them (so they survive a reinstall). */
    val owned: StateFlow<Set<String>>

    /** Whether this phone ever gave a tip. */
    val supporter: StateFlow<Boolean>
    val events: SharedFlow<StoreEvent>

    /** Starts the store's purchase screen for [productId]. */
    fun buy(productId: String)

    /** Asks the store again what this account owns. */
    fun restore()
}

/** No store on this platform (yet): nothing for sale, nothing owned. */
object NoStore : AppStore {
    override val ready: StateFlow<Boolean> = MutableStateFlow(false)
    override val products: StateFlow<Map<String, StoreProduct>> = MutableStateFlow(emptyMap())
    override val owned: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override val supporter: StateFlow<Boolean> = MutableStateFlow(false)
    override val events: SharedFlow<StoreEvent> = MutableSharedFlow()
    override fun buy(productId: String) {}
    override fun restore() {}
}

/** What the purchases unlock. */
data class Entitlements(val owned: Set<String> = emptySet(), val supporter: Boolean = false) {
    val adsRemoved: Boolean get() = Products.NO_ADS in owned || Products.BUNDLE_ALL in owned

    fun ownsSkin(skinId: String): Boolean = Products.BUNDLE_ALL in owned || Products.skin(skinId) in owned
}
