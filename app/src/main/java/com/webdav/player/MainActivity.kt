package com.webdav.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.webdav.player.ui.server.ServerListScreen
import com.webdav.player.ui.server.ServerManagementViewModel
import com.webdav.player.ui.server.ServerManagementViewModelFactory
import com.webdav.player.ui.theme.WebDavPlayerTheme

class MainActivity : ComponentActivity() {

    private val serverViewModel: ServerManagementViewModel by viewModels {
        ServerManagementViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebDavPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ServerListScreen(viewModel = serverViewModel)
                }
            }
        }
    }
}
