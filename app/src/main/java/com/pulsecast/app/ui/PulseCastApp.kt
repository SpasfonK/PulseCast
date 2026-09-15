package com.pulsecast.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pulsecast.app.data.local.entity.EpisodeEntity
import com.pulsecast.app.playback.PlaybackViewModel
import com.pulsecast.app.theme.AppTheme
import com.pulsecast.app.theme.PulseCastTheme
import com.pulsecast.app.theme.ThemeViewModel
import com.pulsecast.app.ui.components.MiniPlayer
import com.pulsecast.app.ui.screens.episodes.EpisodeListScreen
import com.pulsecast.app.ui.screens.library.PodcastLibraryScreen
import com.pulsecast.app.ui.screens.player.FullPlayerScreen
import kotlinx.coroutines.launch

/**
 * Composable racine : applique le thème (avec l'accent dynamique éventuel),
 * héberge le bottom sheet mini-lecteur/grand lecteur, et le NavHost de la
 * bibliothèque/liste d'épisodes en dessous.
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
        val navController = rememberNavController()
        val scaffoldState = rememberBottomSheetScaffoldState()
        val scope = rememberCoroutineScope()

        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues()
        val miniPlayerBarHeight = 68.dp
        val hasActiveEpisode = playbackUiState.currentEpisodeId != null

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = if (hasActiveEpisode) miniPlayerBarHeight + navBarBottomInset.calculateBottomPadding() else 0.dp,
            sheetDragHandle = {},
            sheetContent = {
                if (hasActiveEpisode) {
                    val isExpanded = scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
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
                PulseCastNavHost(
                    navController = navController,
                    onPlayEpisode = { episode, podcastTitle, artworkUri ->
                        playbackViewModel.playEpisode(episode, podcastTitle, artworkUri)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .windowInsetsPadding(WindowInsets.statusBars)
                )
            }
        )
    }
}

@Composable
private fun PulseCastNavHost(
    navController: NavHostController,
    onPlayEpisode: (EpisodeEntity, String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = "library", modifier = modifier) {
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
