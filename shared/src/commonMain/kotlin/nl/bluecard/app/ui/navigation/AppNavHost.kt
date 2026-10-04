package nl.bluecard.app.ui.navigation

import nl.bluecard.app.session.ActiveSession
import androidx.navigation.NavOptionsBuilder

import androidx.navigation.NavDestination.Companion.hasRoute

import androidx.compose.runtime.getValue

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.bluecard.app.session.GameKind
import nl.bluecard.app.ui.components.appContainer
import nl.bluecard.app.ui.components.platformUi
import nl.bluecard.app.ui.screens.LeaderboardScreen
import nl.bluecard.app.ui.screens.HeartsGameScreen
import nl.bluecard.app.ui.screens.HeartsHouseRulesScreen
import nl.bluecard.app.ui.screens.PestenGameScreen
import nl.bluecard.app.ui.screens.PresidentGameScreen
import nl.bluecard.app.ui.screens.PresidentHouseRulesScreen
import nl.bluecard.app.ui.screens.SkinsScreen
import nl.bluecard.app.ui.screens.PestenHouseRulesScreen
import nl.bluecard.app.ui.screens.GameScreen
import nl.bluecard.app.ui.screens.HostLobbyScreen
import nl.bluecard.app.ui.screens.HouseRulesScreen
import nl.bluecard.app.ui.screens.MenuScreen
import nl.bluecard.app.ui.screens.ResultScreen
import nl.bluecard.app.ui.screens.RulesScreen
import nl.bluecard.app.ui.screens.SettingsScreen
import nl.bluecard.multiplayer.session.SessionPhase

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    // Screens slide in from the side (and back out to it), like turning to the next page.
    // Tapped "a table nearby": the start screen searches for it and joins.
    val pending by appContainer().pendingJoin.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        if (pending != null && navController.currentDestination?.hasRoute(MenuRoute::class) != true) {
            navController.navigate(MenuRoute) {
                popUpTo(MenuRoute) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
    NavHost(
        navController = navController,
        startDestination = MenuRoute,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(SLIDE_MS)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(SLIDE_MS)) },
    ) {
        composable<MenuRoute> {
            MenuScreen(
                onStartGame = { navController.navigate(HostLobbyRoute) { launchSingleTop = true } },
                onJoin = { navController.navigate(BluetoothRoute) },
                onRules = { navController.navigate(RulesRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onLeaderboard = { navController.navigate(LeaderboardRoute) },
                onSkins = { navController.navigate(SkinsRoute) },
                onResumed = { navController.navigate(GameRoute) { launchSingleTop = true } },
                onReturnToSession = { phase, hosting ->
                    when {
                        phase == SessionPhase.LOBBY && hosting -> navController.navigate(HostLobbyRoute) { launchSingleTop = true }
                        phase == SessionPhase.LOBBY -> navController.navigate(JoinRoute) { launchSingleTop = true }
                        else -> navController.navigate(GameRoute) { launchSingleTop = true }
                    }
                },
            )
        }
        composable<BluetoothRoute> {
            platformUi().MultiplayerScreen(
                onBack = { navController.popBackStack() },
                onJoin = { navController.navigate(JoinRoute) { launchSingleTop = true } },
            )
        }
        composable<HostLobbyRoute> {
            HostLobbyScreen(
                onLeave = { navController.backToBluetooth() },
                onEditRules = { game -> navController.navigate(HouseRulesRoute(game.id)) },
                onGameStarted = { navController.navigate(GameRoute) { launchSingleTop = true } },
                // Only bots at the table: it became an ordinary game against the bots, the lobby is gone.
                onLocalStarted = {
                    navController.navigate(GameRoute) {
                        popUpTo(MenuRoute)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<JoinRoute> {
            platformUi().JoinScreen(
                onBack = { navController.backToBluetooth() },
                onGameStarted = { navController.navigate(GameRoute) { launchSingleTop = true } },
            )
        }
        composable<GameRoute> {
            val onFinished = {
                navController.navigate(ResultRoute) {
                    popUpTo<GameRoute> { inclusive = true }
                    launchSingleTop = true
                }
            }
            // Each game has its own table; the running session decides which one.
            val sessions = appContainer().sessions
            val game = sessions.active.collectAsStateWithLifecycle().value?.game
            val toMenu = { navController.backToMenu() }
            val toLobby = { navController.backToLobby(sessions.active.value) }
            when (game) {
                GameKind.PESTEN -> PestenGameScreen(onFinished, toMenu, toLobby)
                GameKind.PRESIDENTEN -> PresidentGameScreen(onFinished, toMenu, toLobby)
                GameKind.HARTENJAGEN -> HeartsGameScreen(onFinished, toMenu, toLobby)
                else -> GameScreen(onFinished, toMenu, toLobby)
            }
        }
        composable<ResultRoute> {
            val sessions = appContainer().sessions
            ResultScreen(
                onNewGame = {
                    navController.navigate(GameRoute) {
                        popUpTo<ResultRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onExitToMenu = { navController.backToMenu() },
                onBackToLobby = { navController.backToLobby(sessions.active.value) },
            )
        }
        composable<LeaderboardRoute> {
            LeaderboardScreen(onBack = { navController.popBackStack() })
        }
        composable<SkinsRoute> {
            SkinsScreen(onBack = { navController.popBackStack() })
        }
        composable<RulesRoute> {
            RulesScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEditRules = { game -> navController.navigate(HouseRulesRoute(game.id)) },
            )
        }
        composable<HouseRulesRoute> { entry ->
            val route = entry.toRoute<HouseRulesRoute>()
            when (GameKind.fromId(route.gameId)) {
                GameKind.PESTEN -> PestenHouseRulesScreen(onBack = { navController.popBackStack() })
                GameKind.PRESIDENTEN -> PresidentHouseRulesScreen(onBack = { navController.popBackStack() })
                GameKind.HARTENJAGEN -> HeartsHouseRulesScreen(onBack = { navController.popBackStack() })
                else -> HouseRulesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private const val SLIDE_MS = 320

private fun NavHostController.backToMenu() {
    if (!popBackStack(MenuRoute, inclusive = false)) {
        navigate(MenuRoute) { launchSingleTop = true }
    }
}

/** Out of a lobby: back to the Bluetooth screen, or to the menu when the lobby was reached from there (nearby tables, a notification). */
private fun NavHostController.backToBluetooth() {
    if (!popBackStack(BluetoothRoute, inclusive = false)) backToMenu()
}

/**
 * Back to the lobby screen of the current Bluetooth session (host or client). A table joined from the start screen
 * (nearby tables, a notification) has no lobby screen below it yet: open one on top of the menu.
 */
private fun NavHostController.backToLobby(session: ActiveSession?) {
    val popped = popBackStack(HostLobbyRoute, inclusive = false) || popBackStack(JoinRoute, inclusive = false)
    if (popped) return
    val onMenu: NavOptionsBuilder.() -> Unit = {
        popUpTo(MenuRoute)
        launchSingleTop = true
    }
    when (session) {
        is ActiveSession.Hosting -> navigate(HostLobbyRoute, onMenu)
        is ActiveSession.Joined -> navigate(JoinRoute, onMenu)
        else -> backToMenu()
    }
}
