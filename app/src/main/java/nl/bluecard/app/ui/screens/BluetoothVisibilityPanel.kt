package nl.bluecard.app.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.bluecard.app.R
import nl.bluecard.app.bluetooth
import nl.bluecard.app.platform.nowMillis
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.stringResource
import nl.bluecard.app.ui.components.BannerTone
import nl.bluecard.app.ui.components.FeltBanner
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltSecondaryButton
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.theme.TableColors

/**
 * Host lobby on Android: other phones only find an unpaired host while it is discoverable, so we ask once to
 * become visible and show how long it lasts.
 */
@Composable
fun BluetoothVisibilityPanel() {
    val bluetooth = appContainer().bluetooth
    // Asked each time the lobby comes into view (also when the host comes back to it after a game), unless the phone
    // is still visible from before.
    var askedVisible by remember { mutableStateOf(false) }
    var visibilityDenied by remember { mutableStateOf(false) }
    val visibleUntil by bluetooth.visibleUntil.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when {
            result.resultCode > 0 && result.resultCode != Activity.RESULT_OK -> {
                bluetooth.visibleUntil.value = nowMillis() + result.resultCode * 1000L
                visibilityDenied = false
            }
            result.resultCode == Activity.RESULT_OK -> bluetooth.visibleUntil.value = nowMillis() + 120_000L
            else -> visibilityDenied = true
        }
    }

    fun requestVisible() {
        try {
            launcher.launch(bluetooth.discoverableIntent())
        } catch (_: ActivityNotFoundException) {
            visibilityDenied = true
        } catch (_: SecurityException) {
            visibilityDenied = true
        }
    }

    // Once phones have reached this host, its announcement carries its address and others connect without a search:
    // then being visible is no longer needed and we don't ask.
    val context = androidx.compose.ui.platform.LocalContext.current
    val announcesAddress = remember { nl.bluecard.app.nearby.NearbyTableAlerts.ownAddress(context) != null }
    LaunchedEffect(Unit) {
        if (!announcesAddress && !askedVisible && bluetooth.visibleUntil.value <= nowMillis()) {
            askedVisible = true
            requestVisible()
        }
    }

    FeltPanel(title = stringResource(R.string.lobby_visibility)) {
        val now by produceNow()
        val remainingMin = ((visibleUntil - now + 59_999L) / 60_000L).toInt()
        if (visibleUntil > now) {
            FeltBanner(
                stringResource(R.string.host_visible_on, remainingMin),
                tone = BannerTone.SUCCESS,
                icon = { Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, tint = TableColors.Highlight) },
            )
        } else {
            Text(
                stringResource(if (announcesAddress) R.string.host_visible_not_needed else R.string.host_visible_detail),
                color = TableColors.OnFeltMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            FeltSecondaryButton(
                stringResource(R.string.host_visible),
                onClick = ::requestVisible,
                iconContent = { Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null, modifier = Modifier.size(20.dp)) },
            )
            if (visibilityDenied && !announcesAddress) {
                Text(stringResource(R.string.host_visible_denied), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        bluetooth.localName()?.let {
            Text(stringResource(R.string.host_device_name, it), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * The host's "make visible" button at the table: a Bluetooth icon that asks Android to make this phone discoverable,
 * so a phone that comes in later can find the table (to watch and play from the next round). While visible it
 * shows the minutes left.
 */
@Composable
fun BluetoothVisibilityButton() {
    val bluetooth = appContainer().bluetooth
    val visibleUntil by bluetooth.visibleUntil.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when {
            result.resultCode > 0 && result.resultCode != Activity.RESULT_OK ->
                bluetooth.visibleUntil.value = nowMillis() + result.resultCode * 1000L
            result.resultCode == Activity.RESULT_OK -> bluetooth.visibleUntil.value = nowMillis() + 120_000L
        }
    }
    val now by produceNow()
    val visible = visibleUntil > now
    androidx.compose.material3.IconButton(
        onClick = {
            try {
                launcher.launch(bluetooth.discoverableIntent())
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        },
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_bluetooth),
                contentDescription = stringResource(R.string.host_visible_button),
                tint = if (visible) TableColors.TurnGlow else TableColors.OnFeltMuted,
                modifier = Modifier.size(22.dp),
            )
            if (visible) {
                Text(
                    ((visibleUntil - now + 59_999L) / 60_000L).toString(),
                    color = TableColors.CardBlack,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomEnd)
                        .androidxBadge(),
                )
            }
        }
    }
}

private fun Modifier.androidxBadge(): Modifier =
    this.then(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(TableColors.TurnGlow).padding(horizontal = 3.dp))
