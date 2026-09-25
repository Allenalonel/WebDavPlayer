package com.webdav.player.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.DirectoryRepositoryImpl
import com.webdav.player.data.repository.ServerRepositoryImpl

class DirectoryBrowserViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DirectoryBrowserViewModel::class.java)) {
            val app = context.applicationContext as? WebDavApplication
            val serverRepository = app?.serverRepository ?: run {
                val database = AppDatabase.getInstance(context)
                ServerRepositoryImpl(database.webDavServerDao())
            }
            val client = OkHttpWebDavClient()
            val directoryRepository = DirectoryRepositoryImpl(client)
            val session = app?.musicPlayerAppSession
            return DirectoryBrowserViewModel(serverRepository, directoryRepository, session) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
