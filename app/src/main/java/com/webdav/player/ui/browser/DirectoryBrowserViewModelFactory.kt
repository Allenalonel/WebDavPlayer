package com.webdav.player.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.DirectoryRepositoryImpl
import com.webdav.player.data.repository.ServerRepositoryImpl

class DirectoryBrowserViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DirectoryBrowserViewModel::class.java)) {
            val database = AppDatabase.getInstance(context)
            val serverRepository = ServerRepositoryImpl(database.webDavServerDao())
            val client = OkHttpWebDavClient()
            val directoryRepository = DirectoryRepositoryImpl(client)
            return DirectoryBrowserViewModel(serverRepository, directoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
