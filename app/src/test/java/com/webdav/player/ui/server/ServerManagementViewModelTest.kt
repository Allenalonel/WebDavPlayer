package com.webdav.player.ui.server

import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.ServerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeServerRepository
    private lateinit var fakeClient: FakeWebDavClient
    private lateinit var viewModel: ServerManagementViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeServerRepository()
        fakeClient = FakeWebDavClient()
        viewModel = ServerManagementViewModel(fakeRepository, fakeClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsServersAndActiveServer() = runTest {
        val server = WebDavServer(id = 1L, name = "My NAS", url = "http://nas", isDefault = true)
        fakeRepository.addServerDirectly(server)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.servers.size)
        assertEquals("My NAS", state.servers.first().name)
        assertEquals(1L, state.activeServer?.id)
    }

    @Test
    fun openAddServerDialog_showsDialogWithNullServerToEdit() = runTest {
        viewModel.onAddServerClicked()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isAddEditDialogVisible)
        assertNull(state.serverToEdit)
    }

    @Test
    fun openEditServerDialog_showsDialogWithServerToEdit() = runTest {
        val server = WebDavServer(id = 2L, name = "AList", url = "http://alist")
        viewModel.onEditServerClicked(server)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isAddEditDialogVisible)
        assertEquals(server, state.serverToEdit)
    }

    @Test
    fun saveServer_persistsServerAndDismissesDialog() = runTest {
        viewModel.onAddServerClicked()
        val newServer = WebDavServer(name = "Cloud DAV", url = "https://dav.box.com", port = 443)

        viewModel.onSaveServer(newServer)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAddEditDialogVisible)
        assertEquals(1, fakeRepository.savedServers.size)
        assertEquals("Cloud DAV", fakeRepository.savedServers.first().name)
    }

    @Test
    fun deleteConfirmationFlow_confirmsAndDeleteServer() = runTest {
        val server = WebDavServer(id = 5L, name = "Old Server", url = "http://old")
        fakeRepository.addServerDirectly(server)
        advanceUntilIdle()

        viewModel.onRequestDeleteServer(server)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.serverToDelete == server)

        viewModel.onConfirmDeleteServer()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.serverToDelete)
        assertTrue(fakeRepository.savedServers.none { it.id == 5L })
    }

    @Test
    fun selectActiveServer_updatesActiveServerInRepository() = runTest {
        val s1 = WebDavServer(id = 10L, name = "S1", url = "http://s1", isDefault = true)
        val s2 = WebDavServer(id = 20L, name = "S2", url = "http://s2", isDefault = false)
        fakeRepository.addServerDirectly(s1)
        fakeRepository.addServerDirectly(s2)
        advanceUntilIdle()

        viewModel.onSelectActiveServer(20L)
        advanceUntilIdle()

        assertEquals(20L, viewModel.uiState.value.activeServer?.id)
    }

    @Test
    fun testConnection_updatesTestingState_andEmitsResult() = runTest {
        fakeClient.resultToReturn = ConnectionResult.Success

        val candidate = WebDavServer(name = "Test Server", url = "http://test", port = 80)
        viewModel.onTestConnection(candidate)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.testingConnection)
        assertTrue(state.connectionTestResult is ConnectionResult.Success)
    }

    @Test
    fun testConnection_withFailure_emitsFailureResult() = runTest {
        fakeClient.resultToReturn = ConnectionResult.Failure("401 Unauthorized", statusCode = 401)

        val candidate = WebDavServer(name = "Wrong Auth", url = "http://test", port = 80)
        viewModel.onTestConnection(candidate)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.testingConnection)
        assertTrue(state.connectionTestResult is ConnectionResult.Failure)
        assertEquals(401, (state.connectionTestResult as ConnectionResult.Failure).statusCode)
    }
}

private class FakeServerRepository : ServerRepository {
    private val serversFlow = MutableStateFlow<List<WebDavServer>>(emptyList())
    val savedServers get() = serversFlow.value

    fun addServerDirectly(server: WebDavServer) {
        serversFlow.value = serversFlow.value + server
    }

    override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow

    override fun getActiveServer(): Flow<WebDavServer?> = serversFlow.map { list ->
        list.firstOrNull { it.isDefault }
    }

    override suspend fun getServerById(id: Long): WebDavServer? =
        serversFlow.value.firstOrNull { it.id == id }

    override suspend fun saveServer(server: WebDavServer): Long {
        val current = serversFlow.value.toMutableList()
        val id = if (server.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1L else server.id
        val isFirst = current.isEmpty()
        val updatedServer = server.copy(id = id, isDefault = server.isDefault || isFirst)

        if (updatedServer.isDefault) {
            for (i in current.indices) {
                current[i] = current[i].copy(isDefault = false)
            }
        }

        val existingIndex = current.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            current[existingIndex] = updatedServer
        } else {
            current.add(updatedServer)
        }
        serversFlow.value = current
        return id
    }

    override suspend fun deleteServer(id: Long) {
        serversFlow.value = serversFlow.value.filterNot { it.id == id }
    }

    override suspend fun setActiveServer(id: Long) {
        serversFlow.value = serversFlow.value.map {
            it.copy(isDefault = (it.id == id))
        }
    }
}

private class FakeWebDavClient : WebDavClient {
    var resultToReturn: ConnectionResult = ConnectionResult.Success

    override suspend fun testConnection(server: WebDavServer): ConnectionResult {
        return resultToReturn
    }

    override suspend fun listDirectory(
        server: WebDavServer,
        path: String
    ): com.webdav.player.domain.model.ListDirectoryResult {
        return com.webdav.player.domain.model.ListDirectoryResult.Success(
            com.webdav.player.domain.model.RemoteDirectory(path = path, name = "root")
        )
    }

    override suspend fun fetchRange(
        server: WebDavServer,
        remotePath: String,
        startByte: Long,
        endByte: Long
    ): ByteArray? = null
}
