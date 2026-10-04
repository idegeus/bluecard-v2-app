package nl.bluecard.app.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */
@Serializable data object MenuRoute
@Serializable data object BluetoothRoute
@Serializable data object HostLobbyRoute
@Serializable data object JoinRoute
@Serializable data object GameRoute
@Serializable data object ResultRoute
@Serializable data object RulesRoute
@Serializable data object SettingsRoute
@Serializable data object LeaderboardRoute
@Serializable data object SkinsRoute
/** House rules of one game ([gameId] = engine game id). */
@Serializable data class HouseRulesRoute(val gameId: String)
