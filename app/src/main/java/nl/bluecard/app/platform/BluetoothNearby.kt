package nl.bluecard.app.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import nl.bluecard.app.bluetooth.AdapterState
import nl.bluecard.app.bluetooth.BluetoothController
import nl.bluecard.app.bluetooth.BluetoothPermissions
import nl.bluecard.app.bluetooth.DiscoveryEvent
import nl.bluecard.app.bluetooth.NearbyDevice
import nl.bluecard.multiplayer.protocol.ProtocolVersion
import nl.bluecard.multiplayer.session.LobbyProbe
import nl.bluecard.multiplayer.session.ProbeResult
import nl.bluecard.multiplayer.session.SessionPhase
import java.io.IOException

/**
 * Keeps looking for tables while collected (the home screen): paired phones first (fast), then a Bluetooth search
 * now and then; every candidate is asked whether it hosts a BlueCard table. Found tables are checked again each
 * round, so a closed table disappears and a new one shows up by itself.
 */
class BluetoothNearbyDiscovery(private val context: Context, private val bluetooth: BluetoothController) : NearbyDiscovery {

    override val ready: Boolean
        get() = bluetooth.state.value == AdapterState.ON &&
            BluetoothPermissions.hasScan(context) && BluetoothPermissions.hasConnect(context)

    override fun tables(): Flow<List<NearbyTable>> = channelFlow {
        val devices = linkedMapOf<String, NearbyDevice>()
        val tables = linkedMapOf<String, NearbyTable>()
        val lastNoGame = mutableMapOf<String, Long>()
        val lastFound = mutableMapOf<String, Long>()
        var round = 0
        send(emptyList())
        while (isActive) {
            if (!ready) {
                delay(IDLE_MS)
                continue
            }
            bluetooth.bondedDevices().forEach { devices[it.address] = it }
            // A Bluetooth search every few rounds finds phones that are not paired yet (if they are visible).
            if (round % DISCOVER_EVERY == 0) {
                bluetooth.discover().collect { event ->
                    if (event is DiscoveryEvent.Found) {
                        devices[event.device.address] = devices[event.device.address]?.let {
                            event.device.copy(name = event.device.name ?: it.name, bonded = it.bonded || event.device.bonded)
                        } ?: event.device
                    }
                }
            }
            val now = System.currentTimeMillis()
            val candidates = devices.values
                .filter {
                    it.canHostGame && if (it.address in tables) {
                        // A table that is there stays listed; checking it now and then is enough (a probe is a
                        // Bluetooth connection to a host that may be in the middle of a game).
                        now - (lastFound[it.address] ?: 0L) > RECHECK_FOUND_MS
                    } else {
                        now - (lastNoGame[it.address] ?: 0L) > RECHECK_NO_GAME_MS
                    }
                }
                .sortedWith(compareBy({ it.address !in tables }, { !it.isPhone }))
            for (device in candidates) {
                if (!isActive || !ready) break
                val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    try {
                        LobbyProbe.query(bluetooth.connect(device.address), "BlueCard", PROBE_TOKEN)
                    } catch (e: IOException) {
                        ProbeResult.NoGame(e.message ?: "io")
                    } catch (e: SecurityException) {
                        ProbeResult.NoGame("permission")
                    }
                } ?: ProbeResult.NoGame("timeout")
                Log.d(TAG, "probe ${device.address} -> $result")
                if (result is ProbeResult.Found) {
                    lastFound[device.address] = System.currentTimeMillis()
                    val info = result.info
                    tables[device.address] = NearbyTable(
                        address = device.address,
                        hostName = info.hostName,
                        gameId = info.gameId,
                        inGame = info.phase != SessionPhase.LOBBY,
                        players = info.playerCount,
                        maxPlayers = info.maxPlayers,
                    )
                } else {
                    tables.remove(device.address)
                    lastNoGame[device.address] = System.currentTimeMillis()
                    // An aborted connection keeps the controller busy for a moment.
                    delay(PROBE_PAUSE_MS)
                }
                send(tables.values.toList())
            }
            round++
            delay(ROUND_PAUSE_MS)
        }
    }

    private companion object {
        const val TAG = "NearbyTables"
        const val IDLE_MS = 3_000L
        const val DISCOVER_EVERY = 3
        const val RECHECK_NO_GAME_MS = 20_000L
        const val RECHECK_FOUND_MS = 30_000L
        const val PROBE_TIMEOUT_MS = 9_000L
        const val PROBE_PAUSE_MS = 1_200L
        const val ROUND_PAUSE_MS = 6_000L
        val PROBE_TOKEN = "probe-v" + ProtocolVersion.CURRENT
    }
}
