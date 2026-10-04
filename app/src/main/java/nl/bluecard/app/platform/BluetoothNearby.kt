package nl.bluecard.app.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.nearby.TableBeaconScanner
import nl.bluecard.app.nearby.TableBeaconFormat
import nl.bluecard.app.bluetooth.SearchEnd
import nl.bluecard.app.bluetooth.searchUntilNewPhone
import nl.bluecard.app.bluetooth.announcedDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import nl.bluecard.app.bluetooth.AdapterState
import nl.bluecard.app.bluetooth.BluetoothController
import nl.bluecard.app.bluetooth.BluetoothPermissions
import nl.bluecard.app.bluetooth.NearbyDevice
import nl.bluecard.multiplayer.protocol.ProtocolVersion
import nl.bluecard.multiplayer.session.LobbyProbe
import nl.bluecard.multiplayer.session.ProbeResult
import nl.bluecard.multiplayer.session.SessionPhase
import java.io.IOException

/**
 * Keeps looking for tables while collected (the home screen). Tables that announce themselves over BLE with their
 * address (see [TableBeaconFormat]) show up within a second or two and are joined directly. Otherwise: paired phones,
 * and a Bluetooth search at the start, whenever an announced table could not be reached yet, and now and then; the
 * search stops at each new phone so it is checked at once. Found tables are checked again now and then, so a closed
 * table disappears and a new one shows up by itself.
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
        val announced = MutableStateFlow<List<TableBeaconFormat.Beacon>>(emptyList())
        var scanning: Job? = null

        suspend fun publish() {
            // Announced tables are listed straight away; a check fills in the players later.
            val fromBeacons = announced.value.mapNotNull { beacon ->
                val address = beacon.address ?: return@mapNotNull null
                if (address in tables) return@mapNotNull null
                NearbyTable(address, beacon.hostName, GameKind.entries.getOrNull(beacon.gameIndex)?.id, inGame = beacon.inGame)
            }
            send(tables.values.toList() + fromBeacons)
        }

        suspend fun check(device: NearbyDevice) {
            val result = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                try {
                    LobbyProbe.query(bluetooth.connect(device.address), "BlueCard", PROBE_TOKEN, hostAddress = device.address)
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
            publish()
        }

        fun due(device: NearbyDevice, now: Long): Boolean = device.canHostGame && if (device.address in tables) {
            // A table that is there stays listed; checking it now and then is enough (a probe is a Bluetooth
            // connection to a host that may be in the middle of a game).
            now - (lastFound[device.address] ?: 0L) > RECHECK_FOUND_MS
        } else {
            now - (lastNoGame[device.address] ?: 0L) > RECHECK_NO_GAME_MS
        }

        send(emptyList())
        var round = 0
        var searchNow = true
        while (isActive) {
            if (!ready) {
                scanning?.cancel()
                scanning = null
                delay(IDLE_MS)
                continue
            }
            if (scanning == null) {
                scanning = launch {
                    TableBeaconScanner.tables(context).collect {
                        announced.value = it
                        publish()
                    }
                }
                // A Bluetooth search drowns out the announcements: give them a moment first.
                withTimeoutOrNull(ANNOUNCE_HEAD_START_MS) { announced.first { list -> list.any { it.address != null } } }
            }
            bluetooth.bondedDevices().forEach { devices[it.address] = it }
            // Announced with an address: check those first, no search needed.
            for (beacon in announced.value) {
                val address = beacon.address ?: continue
                devices[address] = devices[address] ?: announcedDevice(address, beacon.hostName)
            }
            val announcedFirst = announced.value.mapNotNull { it.address }.toSet()
            val now = System.currentTimeMillis()
            for (device in devices.values.filter { it.address in announcedFirst && due(it, now) }) {
                if (!isActive || !ready) break
                check(device)
            }
            // A table announced without an address (nobody reached that host yet) and not found: search for it.
            val unreached = announced.value.any { beacon ->
                beacon.address == null && tables.values.none { it.hostName.startsWith(beacon.hostName) }
            }
            var stoppedEarly = false
            if (searchNow || round % DISCOVER_EVERY == 0 || unreached) {
                val end = bluetooth.searchUntilNewPhone(
                    onFound = { found ->
                        devices[found.address] = devices[found.address]?.let {
                            found.copy(name = found.name ?: it.name, bonded = it.bonded || found.bonded)
                        } ?: found
                    },
                    isNew = { found -> found.address !in lastNoGame && found.address !in tables },
                )
                stoppedEarly = end == SearchEnd.STOPPED_AT_NEW_PHONE
            }
            searchNow = stoppedEarly
            val later = System.currentTimeMillis()
            val candidates = devices.values
                .filter { it.address !in announcedFirst && due(it, later) }
                .sortedWith(compareBy({ it.address !in tables }, { !it.isPhone }))
            for (device in candidates) {
                if (!isActive || !ready) break
                check(device)
            }
            round++
            // The search stopped at a new phone: search on at once. Otherwise wait, but wake up as soon as a new
            // table is announced.
            if (!stoppedEarly) {
                val seen = announced.value.map { it.tableId to it.address }.toSet()
                withTimeoutOrNull(ROUND_PAUSE_MS) { announced.first { list -> list.any { (it.tableId to it.address) !in seen } } }
            }
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
        const val ANNOUNCE_HEAD_START_MS = 3_000L
        val PROBE_TOKEN = "probe-v" + ProtocolVersion.CURRENT
    }
}
