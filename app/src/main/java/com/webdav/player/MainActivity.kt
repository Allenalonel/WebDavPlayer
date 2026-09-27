package com.webdav.player

import android.os.Bundle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.domain.model.PlayerSessionState
import com.webdav.player.ui.browser.DirectoryBrowserScreen
import com.webdav.player.ui.browser.DirectoryBrowserViewModel
import com.webdav.player.ui.browser.DirectoryBrowserViewModelFactory
import com.webdav.player.ui.common.RequestNotificationPermissionEffect
import com.webdav.player.ui.navigation.AppDestination
import com.webdav.player.ui.navigation.tabAccessibilityGuard
import com.webdav.player.ui.player.DockedMiniPlayer
import com.webdav.player.ui.player.FullPlayerView
import com.webdav.player.ui.server.ServerListScreen
import com.webdav.player.ui.server.ServerManagementViewModel
import com.webdav.player.ui.server.ServerManagementViewModelFactory
import com.webdav.player.ui.theme.WebDavPlayerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val serverViewModel: ServerManagementViewModel by viewModels {
        ServerManagementViewModelFactory(applicationContext)
    }

    private val browserViewModel: DirectoryBrowserViewModel by viewModels {
        DirectoryBrowserViewModelFactory(applicationContext)
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
                var destination by rememberSaveable {
                    mutableStateOf(AppDestination.DIRECTORY_BROWSER)
                }

                var isFullPlayerExpanded by rememberSaveable { mutableStateOf(false) }

                // If active server is confirmed absent once loaded, fall back to server list
                LaunchedEffect(serverState.isLoading, serverState.activeServer) {
                    if (!serverState.isLoading && serverState.activeServer == null && destination == AppDestination.DIRECTORY_BROWSER) {
                        destination = AppDestination.SERVER_LIST
                    }
                }

                LaunchedEffect(playerSessionState.hasTrack) {
                    if (!playerSessionState.hasTrack) {
                        isFullPlayerExpanded = false
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Hoisted Docked Mini-Player floating above navigation bar
                                AnimatedVisibility(
                                    visible = playerSessionState.hasTrack,
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
                                        onMiniPlayerClick = { isFullPlayerExpanded = true },
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
                                        selected = destination == AppDestination.DIRECTORY_BROWSER,
                                        onClick = { destination = AppDestination.DIRECTORY_BROWSER },
                                        icon = {
                                            Icon(
                                                imageVector =
                                                    if (destination == AppDestination.DIRECTORY_BROWSER) {
                                                        Icons.Filled.FolderOpen
                                                    } else {
                                                        Icons.Filled.Folder
                                                    },
                                                contentDescription = "媒体库",
                                            )
                                        },
                                        label = { Text("媒体库") },
                                    )
                                    NavigationBarItem(
                                        selected = destination == AppDestination.SERVER_LIST,
                                        onClick = { destination = AppDestination.SERVER_LIST },
                                        icon = {
                                            Icon(
                                                imageVector = Icons.Filled.Storage,
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
                            val isBrowser = destination == AppDestination.DIRECTORY_BROWSER
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
                                        destination = AppDestination.SERVER_LIST
                                    },
                                    isCurrentTab = isBrowser,
                                    onOpenFullPlayer = {
                                        isFullPlayerExpanded = true
                                    },
                                )
                            }

                            val isServers = destination == AppDestination.SERVER_LIST
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
                                        destination = AppDestination.DIRECTORY_BROWSER
                                    },
                                    onSelectServerAndNavigate = { server ->
                                        serverViewModel.onSelectActiveServer(server.id)
                                        destination = AppDestination.DIRECTORY_BROWSER
                                    },
                                )
                            }
                        }
                    }

                    // Hoisted Full Player View Overlay
                    AnimatedVisibility(
                        visible = isFullPlayerExpanded && playerSessionState.hasTrack,
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
                            onCollapse = { isFullPlayerExpanded = false },
                            onTogglePlayPause = { musicSession?.togglePlayPause() },
                            onSeek = { musicSession?.seekTo(it) },
                            onSkipToNext = { musicSession?.skipToNext() },
                            onSkipToPrevious = { musicSession?.skipToPrevious() },
                            onCyclePlaybackMode = { musicSession?.cyclePlaybackMode() },
                            onPlayQueueIndex = { musicSession?.playQueueIndex(it) },
                            onRemoveQueueTrack = { musicSession?.removeQueueTrack(it) },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        flushPlaybackSession()
    }

    override fun onStop() {
        super.onStop()
        flushPlaybackSession()
    }

    private fun flushPlaybackSession() {
        val app = application as? WebDavApplication ?: return
        app.musicPlayerAppSession.flushSessionAsync()
    }
}
