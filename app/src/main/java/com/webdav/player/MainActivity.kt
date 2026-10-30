package com.webdav.player

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.webdav.player.data.service.PlaybackSessionHost
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.browser.DirectoryBrowserScreen
import com.webdav.player.ui.browser.DirectoryBrowserViewModel
import com.webdav.player.ui.browser.DirectoryBrowserViewModelFactory
import com.webdav.player.ui.common.RequestNotificationPermissionEffect
import com.webdav.player.ui.common.ThumbnailMemoryCache
import com.webdav.player.ui.navigation.AppDestination
import com.webdav.player.ui.navigation.MainNavigationCoordinator
import com.webdav.player.ui.navigation.tabAccessibilityGuard
import com.webdav.player.ui.player.DockedMiniPlayer
import com.webdav.player.ui.player.FullPlayerView
import com.webdav.player.ui.playlist.PlaylistDetailScreen
import com.webdav.player.ui.playlist.PlaylistDetailViewModel
import com.webdav.player.ui.playlist.PlaylistDetailViewModelFactory
import com.webdav.player.ui.playlist.PlaylistListScreen
import com.webdav.player.ui.playlist.PlaylistListViewModel
import com.webdav.player.ui.playlist.PlaylistListViewModelFactory
import com.webdav.player.ui.playlist.components.AddToPlaylistDialog
import com.webdav.player.ui.server.ServerListScreen
import com.webdav.player.ui.server.ServerManagementViewModel
import com.webdav.player.ui.server.ServerManagementViewModelFactory
import com.webdav.player.ui.theme.AppIcons
import com.webdav.player.ui.theme.WebDavPlayerTheme
import kotlinx.coroutines.launch

/**
 * Main application activity hosting edge-to-edge navigation and playback sheets.
 */
class MainActivity : ComponentActivity() {
    private val serverViewModel: ServerManagementViewModel by viewModels {
        ServerManagementViewModelFactory(applicationContext)
    }

    private val browserViewModel: DirectoryBrowserViewModel by viewModels {
        DirectoryBrowserViewModelFactory(applicationContext)
    }

    private val playlistViewModel: PlaylistListViewModel by viewModels {
        PlaylistListViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebDavPlayerTheme {
                RequestNotificationPermissionEffect()

                val app = applicationContext as? WebDavApplication
                val musicSession = app?.musicPlayerAppSession ?: browserViewModel.musicPlayerAppSession
                val playerSessionState by (
                    musicSession?.sessionState?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(PlayerSessionState()) }
                )
                val playbackProgress by (
                    musicSession?.playbackProgress?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(PlaybackProgress.ZERO) }
                )

                val serverState by serverViewModel.uiState.collectAsStateWithLifecycle()
                val playlistState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                val context = LocalContext.current
                val navPrefs = remember { context.getSharedPreferences("webdav_nav_prefs", Context.MODE_PRIVATE) }
                val savedTab = remember {
                    navPrefs.getString("last_active_tab", null)?.let {
                        runCatching { AppDestination.valueOf(it) }.getOrNull()
                    }
                }
                val coordinator =
                    remember(musicSession) {
                        MainNavigationCoordinator(
                            musicPlayerAppSession = musicSession,
                            initialDestination = savedTab ?: AppDestination.SERVER_LIST,
                            hasExplicitInitialDestination = (savedTab != null),
                            onDestinationChanged = { destination ->
                                navPrefs.edit().putString("last_active_tab", destination.name).apply()
                            },
                        )
                    }
                val navState by coordinator.uiState.collectAsStateWithLifecycle()

                var trackToAddToPlaylist by remember { mutableStateOf<AudioTrack?>(null) }

                LaunchedEffect(serverState.activeServer, serverState.isLoading) {
                    if (!serverState.isLoading) {
                        coordinator.onActiveServerLoaded(serverState.activeServer)
                    }
                }

                LaunchedEffect(playerSessionState) {
                    coordinator.onSessionStateChanged(playerSessionState)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Hoisted Docked Mini-Player floating above navigation bar
                                AnimatedVisibility(
                                    visible = navState.isMiniPlayerVisible,
                                    enter =
                                        slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = tween(250),
                                        ) + fadeIn(animationSpec = tween(250)),
                                    exit =
                                        slideOutVertically(
                                            targetOffsetY = { it },
                                            animationSpec = tween(200),
                                        ) + fadeOut(animationSpec = tween(200)),
                                ) {
                                    DockedMiniPlayer(
                                        sessionState = playerSessionState,
                                        playbackProgress = playbackProgress,
                                        onTogglePlayPause = { musicSession?.togglePlayPause() },
                                        onSkipToNext = { musicSession?.skipToNext() },
                                        onMiniPlayerClick = { coordinator.expandFullPlayer() },
                                        onRetryPlayback = { musicSession?.retryPlayback() },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }

                                // Persistent Primary Navigation Bar
                                NavigationBar(
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    tonalElevation = 3.dp,
                                ) {
                                    NavigationBarItem(
                                        selected = navState.currentDestination == AppDestination.PLAYLISTS,
                                        onClick = { coordinator.selectDestination(AppDestination.PLAYLISTS) },
                                        icon = {
                                            Icon(
                                                imageVector = AppIcons.QueueMusic,
                                                contentDescription = "歌单",
                                            )
                                        },
                                        label = { Text("歌单") },
                                    )
                                    NavigationBarItem(
                                        selected = navState.currentDestination == AppDestination.DIRECTORY_BROWSER,
                                        onClick = { coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER) },
                                        icon = {
                                            Icon(
                                                imageVector =
                                                    if (navState.currentDestination == AppDestination.DIRECTORY_BROWSER) {
                                                        AppIcons.FolderOpen
                                                    } else {
                                                        AppIcons.Folder
                                                    },
                                                contentDescription = "媒体库",
                                            )
                                        },
                                        label = { Text("媒体库") },
                                    )
                                    NavigationBarItem(
                                        selected = navState.currentDestination == AppDestination.SERVER_LIST,
                                        onClick = { coordinator.selectDestination(AppDestination.SERVER_LIST) },
                                        icon = {
                                            Icon(
                                                imageVector = AppIcons.Storage,
                                                contentDescription = "服务器",
                                            )
                                        },
                                        label = { Text("服务器") },
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        // Preserved view hierarchy across tab transitions
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding()),
                        ) {
                            val isBrowser = navState.currentDestination == AppDestination.DIRECTORY_BROWSER
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .tabAccessibilityGuard(isBrowser)
                                        .graphicsLayer {
                                            alpha = if (isBrowser) 1f else 0f
                                            translationX = if (isBrowser) 0f else 99999f
                                        },
                            ) {
                                DirectoryBrowserScreen(
                                    viewModel = browserViewModel,
                                    onNavigateToServerManagement = {
                                        coordinator.selectDestination(AppDestination.SERVER_LIST)
                                    },
                                    isCurrentTab = isBrowser,
                                    onOpenFullPlayer = {
                                        coordinator.expandFullPlayer()
                                    },
                                    onAddToPlaylist = { track ->
                                        trackToAddToPlaylist = track
                                    },
                                )
                            }

                            val isPlaylists = navState.currentDestination == AppDestination.PLAYLISTS
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .tabAccessibilityGuard(isPlaylists)
                                        .graphicsLayer {
                                            alpha = if (isPlaylists) 1f else 0f
                                            translationX = if (isPlaylists) 0f else 99999f
                                        },
                            ) {
                                if (navState.selectedPlaylistId != null) {
                                    val selectedId = navState.selectedPlaylistId!!
                                    val detailViewModel: PlaylistDetailViewModel =
                                        androidx.lifecycle.viewmodel.compose.viewModel(
                                            key = "playlist_detail_$selectedId",
                                            factory = PlaylistDetailViewModelFactory(context, selectedId),
                                        )
                                    PlaylistDetailScreen(
                                        viewModel = detailViewModel,
                                        onBackClick = { coordinator.navigateBackFromPlaylistDetail() },
                                        onTrackClick = { track, index ->
                                            detailViewModel.onTrackClick(track, index)
                                        },
                                        onPlayAll = {
                                            detailViewModel.playAll()
                                        },
                                    )
                                } else {
                                    PlaylistListScreen(
                                        viewModel = playlistViewModel,
                                        onPlaylistClick = { playlist ->
                                            coordinator.navigateToPlaylistDetail(playlist.id)
                                        },
                                    )
                                }
                            }

                            val isServers = navState.currentDestination == AppDestination.SERVER_LIST
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .tabAccessibilityGuard(isServers)
                                        .graphicsLayer {
                                            alpha = if (isServers) 1f else 0f
                                            translationX = if (isServers) 0f else 99999f
                                        },
                            ) {
                                ServerListScreen(
                                    viewModel = serverViewModel,
                                    onNavigateToBrowser = {
                                        coordinator.selectDestination(AppDestination.DIRECTORY_BROWSER)
                                    },
                                    onSelectServerAndNavigate = { server ->
                                        serverViewModel.onSelectActiveServer(server.id)
                                        coordinator.onServerCardClicked(server)
                                    },
                                )
                            }
                        }
                    }

                    // Hoisted Full Player View Overlay
                    AnimatedVisibility(
                        visible = navState.isFullPlayerExpanded && playerSessionState.hasTrack,
                        enter =
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300),
                            ) + fadeIn(animationSpec = tween(300)),
                        exit =
                            slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300),
                            ) + fadeOut(animationSpec = tween(300)),
                    ) {
                        FullPlayerView(
                            sessionState = playerSessionState,
                            playbackProgress = playbackProgress,
                            onCollapse = { coordinator.collapseFullPlayer() },
                            onTogglePlayPause = { musicSession?.togglePlayPause() },
                            onSeek = { musicSession?.seekTo(it) },
                            onSkipToNext = { musicSession?.skipToNext() },
                            onSkipToPrevious = { musicSession?.skipToPrevious() },
                            onCyclePlaybackMode = { musicSession?.cyclePlaybackMode() },
                            onPlayQueueIndex = { musicSession?.playQueueIndex(it) },
                            onRemoveQueueTrack = { musicSession?.removeQueueTrack(it) },
                            onRetryPlayback = { musicSession?.retryPlayback() },
                            onDismissBatteryBanner = { musicSession?.clearKilledBySystemFlag() },
                            onConfirmUnsupportedRangeReplay = { musicSession?.confirmUnsupportedRangeReplay() },
                            onDismissUnsupportedRange = { musicSession?.dismissUnsupportedRange() },
                            onAddToPlaylist = { track ->
                                trackToAddToPlaylist = track
                            },
                        )
                    }

                    if (trackToAddToPlaylist != null) {
                        val track = trackToAddToPlaylist!!
                        AddToPlaylistDialog(
                            track = track,
                            playlists = playlistState.playlists,
                            onDismissRequest = { trackToAddToPlaylist = null },
                            onSelectPlaylist = { playlist ->
                                val trackName = track.title
                                playlistViewModel.addTrackToPlaylist(playlist.id, track) {
                                    Toast
                                        .makeText(
                                            context,
                                            "已将 “$trackName” 添加到歌单 “${playlist.name}”",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                                trackToAddToPlaylist = null
                            },
                            onCreatePlaylistAndAdd = { name ->
                                val trackName = track.title
                                playlistViewModel.createPlaylistAndAddTrack(name, track) {
                                    Toast
                                        .makeText(
                                            context,
                                            "已新建歌单 “$name” 并添加 “$trackName”",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                                trackToAddToPlaylist = null
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackSessionHost.getInstance(applicationContext).prewarmService()
    }

    override fun onPause() {
        super.onPause()
        flushPlaybackSession()
    }

    override fun onStop() {
        super.onStop()
        flushPlaybackSession()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            ThumbnailMemoryCache.clear()
        }
    }

    private fun flushPlaybackSession() {
        val app = application as? WebDavApplication ?: return
        app.musicPlayerAppSession.flushSessionAsync()
    }
}
