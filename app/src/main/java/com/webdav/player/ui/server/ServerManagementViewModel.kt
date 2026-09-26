package com.webdav.player.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerManagementUiState(
    val servers: List<WebDavServer> = emptyList(),
    val activeServer: WebDavServer? = null,
    val isLoading: Boolean = false,
    val isAddEditDialogVisible: Boolean = false,
    val serverToEdit: WebDavServer? = null,
    val serverToDelete: WebDavServer? = null,
    val testingConnection: Boolean = false,
    val connectionTestResult: ConnectionResult? = null,
    val testingServerId: Long? = null,
    val serverConnectionResults: Map<Long, ConnectionResult> = emptyMap(),
    val userMessage: String? = null
)

class ServerManagementViewModel(
    private val repository: ServerRepository,
    private val webDavClient: WebDavClient
) : ViewModel() {

    private val _dialogAndActionState = MutableStateFlow(DialogAndActionState())

    private data class DialogAndActionState(
        val isAddEditDialogVisible: Boolean = false,
        val serverToEdit: WebDavServer? = null,
        val serverToDelete: WebDavServer? = null,
        val testingConnection: Boolean = false,
        val connectionTestResult: ConnectionResult? = null,
        val testingServerId: Long? = null,
        val serverConnectionResults: Map<Long, ConnectionResult> = emptyMap(),
        val userMessage: String? = null
    )

    val uiState: StateFlow<ServerManagementUiState> = combine(
        repository.getAllServers(),
        repository.getActiveServer(),
        _dialogAndActionState
    ) { servers, activeServer, dialogState ->
        ServerManagementUiState(
            servers = servers,
            activeServer = activeServer,
            isLoading = false,
            isAddEditDialogVisible = dialogState.isAddEditDialogVisible,
            serverToEdit = dialogState.serverToEdit,
            serverToDelete = dialogState.serverToDelete,
            testingConnection = dialogState.testingConnection,
            connectionTestResult = dialogState.connectionTestResult,
            testingServerId = dialogState.testingServerId,
            serverConnectionResults = dialogState.serverConnectionResults,
            userMessage = dialogState.userMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ServerManagementUiState(isLoading = true)
    )

    fun onAddServerClicked() {
        _dialogAndActionState.update {
            it.copy(
                isAddEditDialogVisible = true,
                serverToEdit = null,
                connectionTestResult = null
            )
        }
    }

    fun onEditServerClicked(server: WebDavServer) {
        _dialogAndActionState.update {
            it.copy(
                isAddEditDialogVisible = true,
                serverToEdit = server,
                connectionTestResult = null
            )
        }
    }

    fun onDismissAddEditDialog() {
        _dialogAndActionState.update {
            it.copy(
                isAddEditDialogVisible = false,
                serverToEdit = null,
                connectionTestResult = null
            )
        }
    }

    fun onRequestDeleteServer(server: WebDavServer) {
        _dialogAndActionState.update {
            it.copy(serverToDelete = server)
        }
    }

    fun onDismissDeleteDialog() {
        _dialogAndActionState.update {
            it.copy(serverToDelete = null)
        }
    }

    fun onConfirmDeleteServer() {
        val server = _dialogAndActionState.value.serverToDelete ?: return
        viewModelScope.launch {
            repository.deleteServer(server.id)
            _dialogAndActionState.update {
                it.copy(
                    serverToDelete = null,
                    serverConnectionResults = it.serverConnectionResults - server.id
                )
            }
        }
    }

    fun onSelectActiveServer(id: Long) {
        viewModelScope.launch {
            repository.setActiveServer(id)
        }
    }

    fun onTestConnection(server: WebDavServer) {
        val serverId = if (server.id != 0L) server.id else null
        viewModelScope.launch {
            _dialogAndActionState.update {
                it.copy(
                    testingConnection = true,
                    connectionTestResult = null,
                    testingServerId = serverId
                )
            }
            val result = webDavClient.testConnection(server)
            _dialogAndActionState.update {
                val updatedResults = if (serverId != null) {
                    it.serverConnectionResults + (serverId to result)
                } else {
                    it.serverConnectionResults
                }
                it.copy(
                    testingConnection = false,
                    connectionTestResult = result,
                    testingServerId = null,
                    serverConnectionResults = updatedResults
                )
            }
        }
    }

    fun onSaveServer(server: WebDavServer) {
        viewModelScope.launch {
            repository.saveServer(server)
            _dialogAndActionState.update {
                it.copy(
                    isAddEditDialogVisible = false,
                    serverToEdit = null,
                    connectionTestResult = null
                )
            }
        }
    }

    fun onClearConnectionTestResult() {
        _dialogAndActionState.update {
            it.copy(connectionTestResult = null)
        }
    }
}
