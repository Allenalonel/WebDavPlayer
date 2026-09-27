package com.webdav.player.ui.browser

import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.WebDavServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreadcrumbNavigationHelperTest {

    private val sampleServer = WebDavServer(
        id = 1L,
        name = "My NAS",
        url = "http://nas.local",
        port = 5005,
        isDefault = true
    )

    @Test
    fun buildBreadcrumbs_forRoot_returnsSingleItemWithServerName() {
        val crumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(sampleServer, "/")
        assertEquals(1, crumbs.size)
        assertEquals("My NAS", crumbs[0].name)
        assertEquals("/", crumbs[0].path)
        assertEquals(RemoteDirectory.buildBreadcrumbs(sampleServer, "/"), crumbs)
    }

    @Test
    fun buildBreadcrumbs_withoutServer_fallsBackToDefaultRootName() {
        val crumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(null, "/")
        assertEquals(1, crumbs.size)
        assertEquals("根目录", crumbs[0].name)
        assertEquals("/", crumbs[0].path)
        assertEquals(RemoteDirectory.buildBreadcrumbs(null, "/"), crumbs)
    }

    @Test
    fun buildBreadcrumbs_forDeepPath_generatesHierarchyWithNormalizedPaths() {
        val crumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(sampleServer, "/Music/Rock/Queen/")
        assertEquals(4, crumbs.size)

        assertEquals("My NAS", crumbs[0].name)
        assertEquals("/", crumbs[0].path)

        assertEquals("Music", crumbs[1].name)
        assertEquals("/Music/", crumbs[1].path)

        assertEquals("Rock", crumbs[2].name)
        assertEquals("/Music/Rock/", crumbs[2].path)

        assertEquals("Queen", crumbs[3].name)
        assertEquals("/Music/Rock/Queen/", crumbs[3].path)
        assertEquals(RemoteDirectory.buildBreadcrumbs(sampleServer, "/Music/Rock/Queen/"), crumbs)
    }

    @Test
    fun buildBreadcrumbs_handlesMissingTrailingSlashAndMultipleSlashes() {
        val crumbs = BreadcrumbNavigationHelper.buildBreadcrumbs(sampleServer, "//Music///Rock//")
        assertEquals(3, crumbs.size)
        assertEquals("/", crumbs[0].path)
        assertEquals("/Music/", crumbs[1].path)
        assertEquals("/Music/Rock/", crumbs[2].path)
        assertEquals(RemoteDirectory.buildBreadcrumbs(sampleServer, "//Music///Rock//"), crumbs)
    }

    @Test
    fun getParentPath_returnsImmediateParentDirectory() {
        assertEquals("/Music/Rock/", BreadcrumbNavigationHelper.getParentPath("/Music/Rock/Queen/"))
        assertEquals("/Music/", BreadcrumbNavigationHelper.getParentPath("/Music/Rock/"))
        assertEquals("/", BreadcrumbNavigationHelper.getParentPath("/Music/"))
        assertNull(BreadcrumbNavigationHelper.getParentPath("/"))
        assertNull(BreadcrumbNavigationHelper.getParentPath(""))

        assertEquals(RemoteDirectory.getParentPath("/Music/Rock/Queen/"), BreadcrumbNavigationHelper.getParentPath("/Music/Rock/Queen/"))
        assertEquals(RemoteDirectory.getParentPath("/Music/"), BreadcrumbNavigationHelper.getParentPath("/Music/"))
        assertEquals(RemoteDirectory.getParentPath("/"), BreadcrumbNavigationHelper.getParentPath("/"))
    }

    @Test
    fun isAncestor_evaluatesHierarchyCorrectly() {
        assertTrue(BreadcrumbNavigationHelper.isAncestor("/", "/Music/Rock/"))
        assertTrue(BreadcrumbNavigationHelper.isAncestor("/Music/", "/Music/Rock/Queen/"))
        assertFalse(BreadcrumbNavigationHelper.isAncestor("/Music/Rock/", "/Music/Rock/"))
        assertFalse(BreadcrumbNavigationHelper.isAncestor("/Music/Rock/", "/Music/Jazz/"))

        assertEquals(RemoteDirectory.isAncestor("/", "/Music/Rock/"), BreadcrumbNavigationHelper.isAncestor("/", "/Music/Rock/"))
        assertEquals(RemoteDirectory.isAncestor("/Music/Rock/", "/Music/Jazz/"), BreadcrumbNavigationHelper.isAncestor("/Music/Rock/", "/Music/Jazz/"))
    }
}
