package nl.bluecard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.R
import nl.bluecard.app.res.LocalResources
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.store.Products
import nl.bluecard.app.store.StoreEvent
import nl.bluecard.app.ui.components.FeltDivider
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.theme.TableColors

/**
 * The shop: no ads, the all-in-one pack and tips for the maker (premium skins are bought from their own tile). Prices
 * come from the store; before the app is in the store the panel only says so.
 */
@Composable
fun ShopPanel() {
    val store = appContainer().store
    if (!store.supported) return
    val ready by store.ready.collectAsStateWithLifecycle()
    val products by store.products.collectAsStateWithLifecycle()
    val entitlements by appContainer().entitlements.collectAsStateWithLifecycle()
    FeltPanel(title = stringResource(R.string.shop_title)) {
        if (!ready) {
            Text(stringResource(R.string.shop_unavailable), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
            return@FeltPanel
        }
        ShopRow(
            title = stringResource(R.string.shop_no_ads),
            detail = stringResource(R.string.shop_no_ads_detail),
            price = products[Products.NO_ADS]?.price,
            owned = entitlements.adsRemoved,
        ) { store.buy(Products.NO_ADS) }
        FeltDivider()
        ShopRow(
            title = stringResource(R.string.shop_bundle),
            detail = stringResource(R.string.shop_bundle_detail),
            price = products[Products.BUNDLE_ALL]?.price,
            owned = Products.BUNDLE_ALL in entitlements.owned,
        ) { store.buy(Products.BUNDLE_ALL) }
        FeltDivider()
        Text(stringResource(R.string.shop_tip_title), color = TableColors.OnFelt, fontWeight = FontWeight.Bold)
        Text(
            stringResource(if (entitlements.supporter) R.string.shop_supporter else R.string.shop_tip_detail),
            color = if (entitlements.supporter) TableColors.Highlight else TableColors.OnFeltMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (tip in Products.TIPS) {
                val price = products[tip]?.price ?: continue
                PricePill(price) { store.buy(tip) }
            }
        }
        TextButton(onClick = store::restore) {
            Text(stringResource(R.string.shop_restore), color = TableColors.OnFeltMuted)
        }
    }
}

@Composable
private fun ShopRow(title: String, detail: String, price: String?, owned: Boolean, onBuy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TableColors.OnFelt, fontWeight = FontWeight.Bold)
            Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
        when {
            owned -> Text("✓ " + stringResource(R.string.shop_owned), color = TableColors.TurnGlow, fontWeight = FontWeight.Bold)
            price != null -> PricePill(price, onBuy)
        }
    }
}

/** A yellow pill with the price; tapping it starts the purchase. */
@Composable
fun PricePill(price: String, onClick: () -> Unit) {
    Text(
        price,
        color = TableColors.CardBlack,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TableColors.TurnGlow)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** What the store reports after a purchase attempt, as a short message. */
@Composable
fun StoreMessages(snackbar: SnackbarHostState) {
    val store = appContainer().store
    val res = LocalResources.current
    LaunchedEffect(store) {
        store.events.collect { event ->
            val text = when (event) {
                is StoreEvent.Bought -> res.getString(R.string.shop_thanks)
                is StoreEvent.Pending -> res.getString(R.string.shop_pending)
                is StoreEvent.Failed -> res.getString(R.string.shop_failed)
                StoreEvent.Cancelled -> null
            }
            if (text != null) snackbar.showSnackbar(text)
        }
    }
}
