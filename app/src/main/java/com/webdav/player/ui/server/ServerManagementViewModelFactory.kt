package com.webdav.player.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.remote.OkHttpWebDavClient
import com.webdav.player.data.repository.ServerRepositoryImpl

class ServerManagementViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ServerManagementViewModel::class.java)) {
            val database = AppDatabase.getInstance(context)
            val repository = ServerRepositoryImpl(database.webDavServerDao())
            val client = OkHttpWebDavClient()
            return ServerManagementViewModel(repository, client) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
