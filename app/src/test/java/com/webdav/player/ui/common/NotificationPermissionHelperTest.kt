package com.webdav.player.ui.common

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // Android 12 (API 31)
    fun belowAndroid13_permissionIsAlwaysGranted() {
        assertTrue(NotificationPermissionHelper.hasPermission(context))
        assertFalse(NotificationPermissionHelper.shouldRequestPermission(context))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // Android 13 (API 33)
    fun android13_whenNotGranted_shouldRequestReturnsTrue() {
        assertFalse(NotificationPermissionHelper.hasPermission(context))
        assertTrue(NotificationPermissionHelper.shouldRequestPermission(context))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // Android 13 (API 33)
    fun android13_whenGranted_returnsTrue() {
        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertTrue(NotificationPermissionHelper.hasPermission(context))
        assertFalse(NotificationPermissionHelper.shouldRequestPermission(context))
    }
}
