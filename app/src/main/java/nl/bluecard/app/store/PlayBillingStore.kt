package nl.bluecard.app.store

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * In-app purchases through Google Play Billing. Everything permanent is acknowledged (or Play refunds it after three
 * days) and comes back from Play on every start, so a reinstall keeps it; tips are consumed so they can be given
 * again, and remembered locally as "supporter". Purchases are checked on the phone only (there is no server).
 */
class PlayBillingStore(
    context: Context,
    private val scope: CoroutineScope,
    private val activity: () -> Activity?,
) : AppStore, PurchasesUpdatedListener {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val details = mutableMapOf<String, ProductDetails>()
    private val _ready = MutableStateFlow(false)
    private val _products = MutableStateFlow<Map<String, StoreProduct>>(emptyMap())
    private val _owned = MutableStateFlow<Set<String>>(emptySet())
    private val _supporter = MutableStateFlow(prefs.getBoolean(KEY_SUPPORTER, false))
    private val _events = MutableSharedFlow<StoreEvent>(extraBufferCapacity = 8)

    override val ready: StateFlow<Boolean> = _ready.asStateFlow()
    override val products: StateFlow<Map<String, StoreProduct>> = _products.asStateFlow()
    override val owned: StateFlow<Set<String>> = _owned.asStateFlow()
    override val supporter: StateFlow<Boolean> = _supporter.asStateFlow()
    override val events: SharedFlow<StoreEvent> = _events.asSharedFlow()

    fun start() = connect(attempt = 0)

    private fun connect(attempt: Int) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch {
                        loadProducts()
                        loadPurchases()
                    }
                } else {
                    Log.i(TAG, "billing unavailable: ${result.responseCode} ${result.debugMessage}")
                    retryLater(attempt)
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection reconnects on the next call; nothing to do here.
            }
        })
    }

    private fun retryLater(attempt: Int) {
        if (attempt >= MAX_CONNECT_ATTEMPTS) return
        scope.launch {
            delay(RETRY_MS * (attempt + 1))
            connect(attempt + 1)
        }
    }

    private suspend fun loadProducts() {
        val query = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Products.all.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it).setProductType(BillingClient.ProductType.INAPP).build()
                },
            )
            .build()
        val result = client.queryProductDetails(query)
        val list = result.productDetailsList.orEmpty()
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.i(TAG, "products: ${result.billingResult.responseCode} ${result.billingResult.debugMessage}")
        }
        details.clear()
        list.forEach { details[it.productId] = it }
        _products.value = list.mapNotNull { product ->
            val price = product.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
            product.productId to StoreProduct(product.productId, product.name, price)
        }.toMap()
        // Only a store that knows our products can sell anything (not before the app is in Play, or offline).
        _ready.value = _products.value.isNotEmpty()
    }

    private suspend fun loadPurchases() {
        val result = client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        val purchased = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        _owned.value = purchased.flatMap { it.products }.filter { !Products.isTip(it) }.toSet()
        purchased.forEach { handle(it, announce = false) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { purchase -> scope.launch { handle(purchase, announce = true) } }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(StoreEvent.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { loadPurchases() }
            else -> _events.tryEmit(StoreEvent.Failed("${result.responseCode} ${result.debugMessage}"))
        }
    }

    private suspend fun handle(purchase: Purchase, announce: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                if (announce) purchase.products.firstOrNull()?.let { _events.tryEmit(StoreEvent.Pending(it)) }
                return
            }
            Purchase.PurchaseState.PURCHASED -> Unit
            else -> return
        }
        val tips = purchase.products.filter(Products::isTip)
        if (tips.isNotEmpty()) {
            // A tip can be given again: consume it, and remember the thank-you on this phone.
            val consumed = client.consumePurchase(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
            if (consumed.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                prefs.edit { putBoolean(KEY_SUPPORTER, true) }
                _supporter.value = true
            }
        } else {
            _owned.update { it + purchase.products }
            if (!purchase.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
            }
        }
        if (announce) purchase.products.firstOrNull()?.let { _events.tryEmit(StoreEvent.Bought(it)) }
    }

    override fun buy(productId: String) {
        val product = details[productId]
        val screen = activity()
        if (product == null || screen == null) {
            _events.tryEmit(StoreEvent.Failed("unavailable"))
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply { product.oneTimePurchaseOfferDetails?.offerToken?.let { setOfferToken(it) } }
            .build()
        val result = client.launchBillingFlow(screen, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) onPurchasesUpdated(result, null)
    }

    override fun restore() {
        scope.launch {
            if (details.isEmpty()) loadProducts()
            loadPurchases()
        }
    }

    private companion object {
        const val TAG = "PlayBilling"
        const val PREFS = "store"
        const val KEY_SUPPORTER = "supporter"
        const val MAX_CONNECT_ATTEMPTS = 5
        const val RETRY_MS = 5_000L
    }
}
