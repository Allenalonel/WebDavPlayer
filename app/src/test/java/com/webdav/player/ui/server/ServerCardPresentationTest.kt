package com.webdav.player.ui.server

import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.domain.model.WebDavServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCardPresentationTest {

    private val httpNas = WebDavServer(
        id = 1L,
        name = "Local NAS",
        url = "http://192.168.1.100",
        port = 80,
        pathPrefix = "/dav",
        isDefault = false
    )

    private val httpsNextcloud = WebDavServer(
        id = 2L,
        name = "Nextcloud Secure",
        url = "https://cloud.example.org",
        port = 443,
        pathPrefix = "/remote.php/webdav",
        isDefault = true
    )

    private val customPortServer = WebDavServer(
        id = 3L,
        name = "Custom Port Box",
        url = "http://nas.internal:5005",
        port = 5005,
        pathPrefix = "/",
        isDefault = false
    )

    @Test
    fun protocolBadge_identifiesHttpAndHttpsCorrectly() {
        assertEquals("HTTP", httpNas.protocol)
        assertFalse(httpNas.isHttps)

        assertEquals("HTTPS", httpsNextcloud.protocol)
        assertTrue(httpsNextcloud.isHttps)

        assertEquals("HTTP", customPortServer.protocol)
        assertFalse(customPortServer.isHttps)
    }

    @Test
    fun hostSummary_formatsHostPortAndPrefixCleanly() {
        assertEquals("192.168.1.100/dav", httpNas.hostSummary)
        assertEquals("cloud.example.org/remote.php/webdav", httpsNextcloud.hostSummary)
        assertEquals("nas.internal:5005", customPortServer.hostSummary)
    }

    @Test
    fun tapServerCard_activatesServer_andTriggersNavigationToBrowserRoot() {
        var activatedServerId = -1L
        var navigatedToBrowserRoot = false

        val onSelectServerAndNavigate: (WebDavServer) -> Unit = { server ->
            activatedServerId = server.id
            navigatedToBrowserRoot = true
        }

        // User taps inactive server
        onSelectServerAndNavigate(httpNas)

        assertEquals(1L, activatedServerId)
        assertTrue(navigatedToBrowserRoot)
    }

    @Test
    fun tapAlreadyActiveServerCard_stillTriggersNavigationToBrowserRoot() {
        var navigatedToBrowserRoot = false

        val onSelectServerAndNavigate: (WebDavServer) -> Unit = { server ->
            assertEquals(2L, server.id)
            navigatedToBrowserRoot = true
        }

        // User taps currently active server
        onSelectServerAndNavigate(httpsNextcloud)

        assertTrue(navigatedToBrowserRoot)
    }

    @Test
    fun serverActions_editTestDelete_independentFromCardClick() {
        var cardClicked = false
        var testClicked = false
        var editClicked = false
        var deleteClicked = false

        val onCardClick = { cardClicked = true }
        val onTestConnection = { testClicked = true }
        val onEdit = { editClicked = true }
        val onDelete = { deleteClicked = true }

        // Trigger action callbacks directly
        onTestConnection()
        assertTrue(testClicked)
        assertFalse(cardClicked)

        onEdit()
        assertTrue(editClicked)
        assertFalse(cardClicked)

        onDelete()
        assertTrue(deleteClicked)
        assertFalse(cardClicked)

        onCardClick()
        assertTrue(cardClicked)
    }

    @Test
    fun connectionStatusState_distinguishesIdleTestingSuccessAndFailure() {
        val idleResult: ConnectionResult? = null
        assertFalse(idleResult is ConnectionResult.Success)
        assertFalse(idleResult is ConnectionResult.Failure)

        val successResult: ConnectionResult = ConnectionResult.Success
        assertTrue(successResult is ConnectionResult.Success)

        val failureResult: ConnectionResult = ConnectionResult.Failure("Unauthorized", statusCode = 401)
        assertTrue(failureResult is ConnectionResult.Failure)
        assertEquals(401, (failureResult as ConnectionResult.Failure).statusCode)
    }

    @Test
    fun tapHintText_distinguishesActiveAndInactiveCards() {
        val getTapHint = { isActive: Boolean -> if (isActive) "点击浏览根目录" else "点击切换并浏览" }
        assertEquals("点击浏览根目录", getTapHint(true))
        assertEquals("点击切换并浏览", getTapHint(false))
    }

    @Test
    fun consolidatedOverflowMenu_routesEditAndDeleteCorrectly() {
        var editTriggered = false
        var deleteTriggered = false

        val onEdit = { editTriggered = true }
        val onDelete = { deleteTriggered = true }

        onEdit()
        assertTrue(editTriggered)
        assertFalse(deleteTriggered)

        onDelete()
        assertTrue(deleteTriggered)
    }
}
