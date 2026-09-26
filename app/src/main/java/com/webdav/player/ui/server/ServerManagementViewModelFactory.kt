package com.webdav.player.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.CoverArtStorageImpl
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.ServerRepositoryImpl

class ServerManagementViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServerManagementViewModel::class.java)) {
            val app = context.applicationContext as? WebDavApplication
            val repository = app?.serverRepository ?: run {
                val database = AppDatabase.getInstance(context)
                val storage = CoverArtStorageImpl(context)
                ServerRepositoryImpl(database.webDavServerDao(), storage)
            }
            val client = app?.webDavClient ?: OkHttpWebDavClient()
            return ServerManagementViewModel(repository, client) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
