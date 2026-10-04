package nl.bluecard.app

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.bluecard.app.bluetooth.AdapterState
import nl.bluecard.app.nearby.NearbyTableAlerts
import nl.bluecard.app.session.ActiveSession
import nl.bluecard.multiplayer.session.SessionPhase

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import nl.bluecard.app.platform.AndroidPlatform
import nl.bluecard.app.res.Resources
import nl.bluecard.app.service.MultiplayerService
import nl.bluecard.app.session.BuzzAlerts
import nl.bluecard.app.session.TurnAlerts

class BlueCardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val platform = AndroidPlatform(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        container = AppContainer(platform)
        // Notifications may be built before any screen is shown.
        Resources.useLanguage(platform.language.effective())
        TurnAlerts(this, container.appScope, container.sessions).start()
        BuzzAlerts(this, container.appScope, container.sessions).start()
        MultiplayerService.createChannel(this)
        watchNearbyTables()
    }

    /**
     * Tables nearby: listen in the background (re-registered whenever Bluetooth comes on or the setting changes), and
     * announce our own table while we host one (in the lobby for the notification, during a game for those who want
     * to watch).
     */
    private fun watchNearbyTables() {
        val scope = container.appScope
        val bluetoothOn = container.android.bluetooth.state.map { it == AdapterState.ON }.distinctUntilChanged()
        scope.launch {
            combine(container.settings.map { it.nearbyAlerts }.distinctUntilChanged(), bluetoothOn) { enabled, on -> enabled && on }
                .collect { listen -> NearbyTableAlerts.setListening(this@BlueCardApp, listen) }
        }
        scope.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            container.sessions.active.flatMapLatest { session ->
                if (session is ActiveSession.Hosting) {
                    combine(session.host.lobby, bluetoothOn, session.host.ownAddress) { lobby, on, address ->
                        // A phone that connected told us our own address: from now on the announcement carries it.
                        address?.let { NearbyTableAlerts.rememberOwnAddress(this@BlueCardApp, it) }
                        if (!on) {
                            null
                        } else {
                            Announcement(
                                hostName = lobby.hostName,
                                gameId = session.game.id,
                                // Stable for this table, so phones nearby recognise it between rounds.
                                tableId = System.identityHashCode(session.host),
                                inGame = lobby.phase != SessionPhase.LOBBY,
                                address = NearbyTableAlerts.ownAddress(this@BlueCardApp),
                            )
                        }
                    }
                } else {
                    flowOf(null)
                }
            }.distinctUntilChanged().collect { table ->
                if (table == null) {
                    NearbyTableAlerts.stopAnnouncing(this@BlueCardApp)
                } else {
                    NearbyTableAlerts.startAnnouncing(this@BlueCardApp, table.hostName, table.gameId, table.tableId, table.inGame)
                }
            }
        }
    }
}

/** What this phone announces about its table (see [NearbyTableAlerts.startAnnouncing]). */
private data class Announcement(val hostName: String, val gameId: String, val tableId: Int, val inGame: Boolean, val address: String?)
