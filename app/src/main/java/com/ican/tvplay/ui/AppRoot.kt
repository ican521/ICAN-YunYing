package com.ican.tvplay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ican.tvplay.ui.collection.FavoritesScreen
import com.ican.tvplay.ui.collection.HistoryScreen
import com.ican.tvplay.ui.components.GlassCapsuleBar
import com.ican.tvplay.ui.detail.DetailScreen
import com.ican.tvplay.ui.home.HomeScreen
import com.ican.tvplay.ui.nav.Routes
import com.ican.tvplay.ui.nav.topDestinations
import com.ican.tvplay.ui.nav.topLevelRoutes
import com.ican.tvplay.ui.player.PlayerScreen
import com.ican.tvplay.ui.search.SearchScreen
import com.ican.tvplay.ui.settings.SettingsScreen
import com.ican.tvplay.ui.theme.TvPlayTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.tv.material3.MaterialTheme

@Composable
fun AppRoot() {
    val appViewModel = appViewModel { AppViewModel(this) }
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()

    TvPlayTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // 整个 NavHost 作为毛玻璃内容源，底部胶囊条实时模糊其背后的内容
        val hazeState = rememberHazeState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onVideoClick = { video ->
                            navController.navigate(Routes.detail(video.id))
                        },
                        onSearchClick = { navController.navigate(Routes.SEARCH) },
                        onHistoryClick = { navController.navigate(Routes.HISTORY) },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        onVideoClick = { video ->
                            navController.navigate(Routes.detail(video.id))
                        },
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Routes.detail(videoId))
                        },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onVideoClick = { videoId ->
                            navController.navigate(Routes.detail(videoId))
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen()
                }

                composable(
                    route = Routes.DETAIL_PATTERN,
                    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                ) { entry ->
                    val videoId = entry.arguments?.getString("videoId").orEmpty()
                    DetailScreen(
                        videoId = videoId,
                        onBack = { navController.popBackStack() },
                        onPlay = { id, episodeIndex ->
                            navController.navigate(Routes.player(id, episodeIndex))
                        },
                    )
                }

                composable(
                    route = Routes.PLAYER_PATTERN,
                    arguments = listOf(
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("episodeIndex") { type = NavType.IntType },
                    ),
                ) { entry ->
                    PlayerScreen(
                        videoId = entry.arguments?.getString("videoId").orEmpty(),
                        startEpisode = entry.arguments?.getInt("episodeIndex") ?: 0,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // 底部悬浮胶囊导航：仅一级页面显示
            AnimatedVisibility(
                visible = currentRoute in topLevelRoutes,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding() + 14.dp,
                    ),
            ) {
                GlassCapsuleBar(
                    items = topDestinations,
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    hazeState = hazeState,
                )
            }
        }
    }
}
