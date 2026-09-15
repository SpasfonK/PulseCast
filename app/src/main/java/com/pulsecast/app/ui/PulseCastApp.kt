package com.pulsecast.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.playback.PlaybackViewModel
import com.pulsecast.app.theme.AppTheme
import com.pulsecast.app.theme.PulseCastTheme
import com.pulsecast.app.theme.ThemeViewModel
import com.pulsecast.app.ui.components.MiniPlayer
import com.pulsecast.app.ui.screens.episodes.EpisodeListScreen
import com.pulsecast.app.ui.screens.home.HomeScreen
import com.pulsecast.app.ui.screens.library.PodcastLibraryScreen
import com.pulsecast.app.ui.screens.player.FullPlayerScreen
import kotlinx.coroutines.launch

/**
 * Composable racine : applique le thème (avec l'accent dynamique éventuel),
 * peint le fond du thème sur toute la fenêtre, héberge le bottom sheet
 * mini-lecteur/grand lecteur et la barre de navigation Accueil/Bibliothèque.
 *
 * Le sélecteur de style visuel vit dans l'écran Accueil (section repliable),
 * pas dans une feuille modale : le rendu dans la composition normale est
 * fiable et le choix s'applique immédiatement à toute la hiérarchie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseCastApp() {
    val themeViewModel: ThemeViewModel = viewModel()
    val playbackViewModel: PlaybackViewModel = viewModel()

    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
    val dynamicAccent by playbackViewModel.dynamicAccent.collectAsState()
    val playbackUiState by playbackViewModel.uiState.collectAsState()

    // Seul le thème OLED Deep Minimal exploite la couleur extraite de la
    // pochette (cahier des charges, Phase 3) : les autres thèmes gardent
    // leur identité colorée fixe.
    val accentForActiveTheme = if (selectedTheme == AppTheme.OLED_DEEP_MINIMAL) dynamicAccent else null

    PulseCastTheme(appTheme = selectedTheme, dynamicAccent = accentForActiveTheme) {
        // Peint le fond du thème sur toute la fenêtre : sans cette Surface, le
        // fond de fenêtre Android (noir) resterait visible quelle que soit
        // l'identité visuelle choisie.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val scaffoldState = rememberBottomSheetScaffoldState()
            val scope = rememberCoroutineScope()

            val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues()
            val miniPlayerBarHeight = 68.dp
            val hasActiveEpisode = playbackUiState.currentEpisodeId != null

            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val showBottomBar = currentRoute == "home" || currentRoute == "library"

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = if (hasActiveEpisode) {
                    miniPlayerBarHeight + navBarBottomInset.calculateBottomPadding()
                } else {
                    0.dp
                },
                sheetDragHandle = {},
                sheetContent = {
                    if (hasActiveEpisode) {
                        val isExpanded =
                            scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
                        if (isExpanded) {
                            FullPlayerScreen(
                                uiState = playbackUiState,
                                onCollapse = { scope.launch { scaffoldState.bottomSheetState.partialExpand() } },
                                onPlayPause = playbackViewModel::togglePlayPause,
                                onSeekCommand = playbackViewModel::sendSeekCommand,
                                onSpeedChange = playbackViewModel::setSpeed,
                                onSeekTo = playbackViewModel::seekTo,
                                modifier = Modifier.fillMaxWidth().fillMaxHeight()
                            )
                        } else {
                            MiniPlayer(
                                uiState = playbackUiState,
                                onPlayPause = playbackViewModel::togglePlayPause,
                                onExpand = { scope.launch { scaffoldState.bottomSheetState.expand() } },
                                barHeight = miniPlayerBarHeight,
                                navBarInset = navBarBottomInset.calculateBottomPadding(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                content = { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        PulseCastNavHost(
                            navController = navController,
                            onPlayEpisode = { episode, podcastTitle, artworkUri ->
                                playbackViewModel.playEpisode(episode, podcastTitle, artworkUri)
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        if (showBottomBar) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // Le mini-lecteur réserve déjà l'encoche de
                                    // navigation système dans sa hauteur : on ne
                                    // l'applique qu'en son absence.
                                    .then(
                                        if (hasActiveEpisode) {
                                            Modifier
                                        } else {
                                            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                                        }
                                    )
                            ) {
                                PulseCastBottomBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { route ->
                                        navController.navigate(route) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PulseCastBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { onNavigate("home") },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Accueil") },
            colors = pulseCastNavItemColors()
        )
        NavigationBarItem(
            selected = currentRoute == "library",
            onClick = { onNavigate("library") },
            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
            label = { Text("Bibliothèque") },
            colors = pulseCastNavItemColors()
        )
    }
}

@Composable
private fun pulseCastNavItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun PulseCastNavHost(
    navController: NavHostController,
    onPlayEpisode: (EpisodeEntity, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable("home") {
            HomeScreen(
                onOpenEpisodes = { podcastId -> navController.navigate("episodes/$podcastId") },
                onOpenLibrary = { navController.navigate("library") { launchSingleTop = true } }
            )
        }
        composable("library") {
            PodcastLibraryScreen(
                onPodcastClick = { podcastId -> navController.navigate("episodes/$podcastId") }
            )
        }
        composable(
            route = "episodes/{podcastId}",
            arguments = listOf(navArgument("podcastId") { type = NavType.LongType })
        ) { backStackEntry ->
            val podcastId = backStackEntry.arguments?.getLong("podcastId") ?: return@composable
            EpisodeListScreen(
                podcastId = podcastId,
                onPlayEpisode = onPlayEpisode,
                onBack = { navController.popBackStack() }
            )
        }
    }
}