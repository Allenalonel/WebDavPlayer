package com.webdav.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webdav.player.ui.browser.DirectoryBrowserScreen
import com.webdav.player.ui.browser.DirectoryBrowserViewModel
import com.webdav.player.ui.browser.DirectoryBrowserViewModelFactory
import com.webdav.player.ui.common.RequestNotificationPermissionEffect
import com.webdav.player.ui.server.ServerListScreen
import com.webdav.player.ui.server.ServerManagementViewModel
import com.webdav.player.ui.server.ServerManagementViewModelFactory
import com.webdav.player.ui.theme.WebDavPlayerTheme

enum class AppDestination {
    SERVER_LIST,
    DIRECTORY_BROWSER
}

class MainActivity : ComponentActivity() {

    private val serverViewModel: ServerManagementViewModel by viewModels {
        ServerManagementViewModelFactory(applicationContext)
    }

    private val browserViewModel: DirectoryBrowserViewModel by viewModels {
        DirectoryBrowserViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebDavPlayerTheme {
                RequestNotificationPermissionEffect()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val serverState by serverViewModel.uiState.collectAsStateWithLifecycle()
                    var destination by rememberSaveable {
                        mutableStateOf(AppDestination.SERVER_LIST)
                    }

                    // Auto-enter browser on first launch if there is an active server
                    var hasCheckedInitialActiveServer by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(serverState.isLoading, serverState.activeServer) {
                        if (!serverState.isLoading && !hasCheckedInitialActiveServer) {
                            hasCheckedInitialActiveServer = true
                            if (serverState.activeServer != null) {
                                destination = AppDestination.DIRECTORY_BROWSER
                            }
                        }
                    }

                    // If active server was deleted/unset while on browser, return to server list
                    if (serverState.activeServer == null && destination == AppDestination.DIRECTORY_BROWSER && !serverState.isLoading) {
                        destination = AppDestination.SERVER_LIST
                    }

                    Crossfade(
                        targetState = destination,
                        label = "AppScreenTransition"
                    ) { screen ->
                        when (screen) {
                            AppDestination.SERVER_LIST -> {
                                ServerListScreen(
                                    viewModel = serverViewModel,
                                    onNavigateToBrowser = {
                                        destination = AppDestination.DIRECTORY_BROWSER
                                    }
                                )
                            }
                            AppDestination.DIRECTORY_BROWSER -> {
                                DirectoryBrowserScreen(
                                    viewModel = browserViewModel,
                                    onNavigateToServerManagement = {
                                        destination = AppDestination.SERVER_LIST
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
