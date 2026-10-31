package com.webdav.player.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.webdav.player.WebDavApplication
import com.webdav.player.di.AppViewModelFactory

class DirectoryBrowserViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app =
            requireNotNull(context.applicationContext as? WebDavApplication) {
                "Application context must be an instance of WebDavApplication"
            }
        return AppViewModelFactory(app).create(modelClass)
    }
}
