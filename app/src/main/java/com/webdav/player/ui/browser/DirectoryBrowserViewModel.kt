package com.webdav.player.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.DirectoryRepository
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DirectoryBrowserViewModel(
    private val serverRepository: ServerRepository,
    private val directoryRepository: DirectoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryBrowserUiState())
    val uiState: StateFlow<DirectoryBrowserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            serverRepository.getActiveServer().collect { server ->
                val previousServer = _uiState.value.activeServer
                if (server != previousServer) {
                    _uiState.update {
                        it.copy(
                            activeServer = server,
                            currentPath = "/",
                            breadcrumbs = buildBreadcrumbs(server, "/"),
                            canNavigateUp = false,
                            currentDirectory = null,
                            errorMessage = null
                        )
                    }
                    if (server != null) {
                        loadDirectory("/", forceRefresh = false)
                    }
                }
            }
        }
    }

    fun onDirectoryClicked(directory: RemoteDirectory) {
        loadDirectory(directory.path, forceRefresh = false)
    }

    fun onBreadcrumbClicked(breadcrumb: Breadcrumb) {
        if (breadcrumb.path == _uiState.value.currentPath) return
        loadDirectory(breadcrumb.path, forceRefresh = false)
    }

    fun onNavigateUp(): Boolean {
        val current = _uiState.value.currentPath
        if (current == "/" || current.isBlank()) {
            return false
        }

        val trimmed = current.trim('/')
        val segments = trimmed.split('/')
        val parentPath = if (segments.size <= 1) {
            "/"
        } else {
            "/" + segments.dropLast(1).joinToString("/") + "/"
        }

        loadDirectory(parentPath, forceRefresh = false)
        return true
    }

    fun onRefresh() {
        loadDirectory(_uiState.value.currentPath, forceRefresh = true)
    }

    fun onRetry() {
        loadDirectory(_uiState.value.currentPath, forceRefresh = true)
    }

    private fun loadDirectory(path: String, forceRefresh: Boolean) {
        val server = _uiState.value.activeServer ?: return
        val normalizedPath = normalizeDirectoryPath(path)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentPath = normalizedPath,
                    breadcrumbs = buildBreadcrumbs(server, normalizedPath),
                    canNavigateUp = normalizedPath != "/",
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    errorMessage = null
                )
            }

            val result = directoryRepository.listDirectory(server, normalizedPath, forceRefresh)

            _uiState.update { current ->
                when (result) {
                    is ListDirectoryResult.Success -> {
                        current.copy(
                            currentDirectory = result.directory,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                    is ListDirectoryResult.Failure -> {
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun buildBreadcrumbs(server: WebDavServer?, path: String): List<Breadcrumb> {
        val rootName = server?.name?.ifBlank { "根目录" } ?: "根目录"
        val list = mutableListOf(Breadcrumb(name = rootName, path = "/"))
        val cleanPath = path.trim('/')
        if (cleanPath.isEmpty()) return list

        val segments = cleanPath.split('/')
        var accumulated = ""
        for (segment in segments) {
            accumulated += "/$segment"
            list.add(Breadcrumb(name = segment, path = "$accumulated/"))
        }
        return list
    }

    private fun normalizeDirectoryPath(path: String): String {
        var p = path.replace('\\', '/')
        if (!p.startsWith("/")) p = "/$p"
        if (!p.endsWith("/")) p = "$p/"
        return p
    }
}
