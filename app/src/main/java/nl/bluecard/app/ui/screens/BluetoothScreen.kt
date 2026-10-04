package nl.bluecard.app.ui.screens

import nl.bluecard.app.bluetooth
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import nl.bluecard.app.res.painterResource
import nl.bluecard.app.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.bluecard.app.R
import nl.bluecard.app.bluetooth.AdapterState
import nl.bluecard.app.bluetooth.BluetoothPermissions
import nl.bluecard.app.bluetooth.BluetoothRole
import nl.bluecard.app.ui.components.BannerTone
import nl.bluecard.app.ui.components.FeltBanner
import nl.bluecard.app.ui.components.FeltPanel
import nl.bluecard.app.ui.components.FeltScreen
import nl.bluecard.app.ui.theme.TableColors
import nl.bluecard.engine.model.Card
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import nl.bluecard.app.ui.components.appContainer

/**
 * Joining a Bluetooth game (as a player or as the table display). Takes care of the prerequisites in order:
 * Bluetooth present → runtime permissions → Bluetooth switched on. Only then it navigates on. Opening a table
 * yourself starts from "Spel starten" on the start screen.
 */
@Composable
fun BluetoothScreen(onBack: () -> Unit, onJoin: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val settings by container.settings.collectAsStateWithLifecycle()
    val adapterState by container.bluetooth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var pendingRole by remember { mutableStateOf<BluetoothRole?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var showSettingsButton by remember { mutableStateOf(false) }

    val permissionDenied = stringResource(R.string.bt_permission_denied)
    val enableDenied = stringResource(R.string.bt_enable_denied)

    fun proceed() {
        pendingRole = null
        problem = null
        onJoin()
    }

    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        container.bluetooth.refreshState()
        if (pendingRole == null) return@rememberLauncherForActivityResult
        if (container.bluetooth.state.value == AdapterState.ON || it.resultCode == Activity.RESULT_OK) {
            proceed()
        } else {
            problem = enableDenied
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val role = pendingRole ?: return@rememberLauncherForActivityResult
        val missing = BluetoothPermissions.missing(context, role)
        if (missing.isEmpty()) {
            if (container.bluetooth.state.value == AdapterState.ON) proceed() else enableLauncher.launch(container.bluetooth.enableIntent())
        } else {
            problem = permissionDenied
            // No rationale after a denial means "don't ask again": only the system settings can help now.
            val activity = context.findActivity()
            showSettingsButton = activity != null && missing.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }
    }

    fun start(role: BluetoothRole) {
        if (!container.bluetooth.isSupported) return
        pendingRole = role
        problem = null
        showSettingsButton = false
        val missing = BluetoothPermissions.missing(context, role)
        if (missing.isNotEmpty()) {
            val request = missing + notificationPermissionIfNeeded(context)
            permissionLauncher.launch(request.toTypedArray())
        } else if (container.bluetooth.state.value != AdapterState.ON) {
            enableLauncher.launch(container.bluetooth.enableIntent())
        } else {
            proceed()
        }
    }

    LaunchedEffect(Unit) { container.bluetooth.refreshState() }

    FeltScreen(title = stringResource(R.string.bt_title), onBack = onBack) {
        FeltHeader(
            title = stringResource(R.string.bt_header),
            subtitle = stringResource(R.string.bt_intro),
            cards = listOf(Card.of("QS"), Card.of("AH"), Card.of("JD")),
        )
        if (!container.bluetooth.isSupported) {
            FeltBanner(stringResource(R.string.bt_not_supported), tone = BannerTone.ERROR)
            return@FeltScreen
        }
        if (adapterState == AdapterState.OFF) {
            FeltBanner(stringResource(R.string.bt_disabled))
        }
        if (BluetoothPermissions.needsLocationForScan) {
            Text(stringResource(R.string.bt_location_needed), color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodySmall)
        }

        problem?.let { text ->
            FeltBanner(
                text,
                tone = BannerTone.ERROR,
                action = if (showSettingsButton) {
                    { FeltTextAction(stringResource(R.string.open_settings)) { context.openAppSettings() } }
                } else {
                    null
                },
            )
        }

        // Joining is what most people come here for: right at the top, no scrolling.
        RoleCard(
            title = stringResource(R.string.bt_join),
            detail = stringResource(R.string.bt_join_detail),
            primary = true,
            onClick = {
                container.sessions.joinAsTable.value = false
                start(BluetoothRole.JOIN)
            },
        )
        TableDisplayCard {
            container.sessions.joinAsTable.value = true
            start(BluetoothRole.JOIN)
        }
        FeltPanel(title = stringResource(R.string.settings_player)) {
            PlayerNameField(settings.playerName, label = stringResource(R.string.bt_your_name), onFelt = true) { name ->
                scope.launch { container.settingsRepository.update { it.copy(playerName = name) } }
            }
        }
    }
}

/**
 * Before opening a table: asks for the Bluetooth permissions (one "nearby devices" question on Android 12+) when
 * they are missing, then calls [onReady] whatever the answer — without them the table simply stays offline.
 */
@Composable
fun rememberHostPermissions(onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onReady() }
    return {
        val missing = (
            BluetoothPermissions.missing(context, BluetoothRole.HOST) +
                // Same permission group on Android 12+, so no extra question: lets the start screen find tables too.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BluetoothPermissions.missing(context, BluetoothRole.JOIN) else emptyList()
            ).distinct()
        if (missing.isEmpty()) onReady() else launcher.launch((missing + notificationPermissionIfNeeded(context)).toTypedArray())
    }
}

/** Big tappable choice on the felt (host or join). */
@Composable
private fun RoleCard(title: String, detail: String, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) TableColors.FeltLight.copy(alpha = 0.75f) else TableColors.FeltDark.copy(alpha = 0.55f))
            .border(1.dp, if (primary) TableColors.TurnGlow.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (primary) TableColors.TurnGlow else Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                tint = if (primary) TableColors.CardBlack else TableColors.OnFelt,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = TableColors.OnFelt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(detail, color = TableColors.OnFeltMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Text("›", color = TableColors.OnFelt, fontSize = 30.sp, fontWeight = FontWeight.Light)
    }
}

/** Notifications are optional (they only show the "game active" notice), so they ride along once. */
private fun notificationPermissionIfNeeded(context: Context): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    return if (granted) emptyList() else listOf(Manifest.permission.POST_NOTIFICATIONS)
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        // Nothing sensible to do on devices without an app-settings screen.
    }
}
