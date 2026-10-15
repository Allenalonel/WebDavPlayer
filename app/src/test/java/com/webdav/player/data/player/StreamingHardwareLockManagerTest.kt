package com.webdav.player.data.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamingHardwareLockManagerTest {
    private lateinit var context: Context
    private lateinit var lockManager: StreamingHardwareLockManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        lockManager = DefaultStreamingHardwareLockManager(context)
    }

    @After
    fun tearDown() {
        lockManager.release()
    }

    @Test
    fun initial_locksAreNotHeld() {
        assertFalse(lockManager.isWakeLockHeld)
        assertFalse(lockManager.isWifiLockHeld)
    }

    @Test
    fun acquireLocks_holdsBothWakeLockAndWifiLock() {
        lockManager.acquireLocks()
        assertTrue(lockManager.isWakeLockHeld)
        assertTrue(lockManager.isWifiLockHeld)
    }

    @Test
    fun releaseLocks_releasesBothLocksImmediately() {
        lockManager.acquireLocks()
        assertTrue(lockManager.isWakeLockHeld)
        assertTrue(lockManager.isWifiLockHeld)

        lockManager.releaseLocks()
        assertFalse(lockManager.isWakeLockHeld)
        assertFalse(lockManager.isWifiLockHeld)
    }

    @Test
    fun acquireLocks_isIdempotent() {
        lockManager.acquireLocks()
        lockManager.acquireLocks()
        assertTrue(lockManager.isWakeLockHeld)
        assertTrue(lockManager.isWifiLockHeld)

        lockManager.releaseLocks()
        assertFalse(lockManager.isWakeLockHeld)
        assertFalse(lockManager.isWifiLockHeld)
    }

    @Test
    fun releaseLocks_whenNotHeld_isGracefulNoOp() {
        lockManager.releaseLocks()
        assertFalse(lockManager.isWakeLockHeld)
        assertFalse(lockManager.isWifiLockHeld)
    }

    @Test
    fun reacquireLocks_afterRelease_holdsLocksAgain() {
        lockManager.acquireLocks()
        lockManager.releaseLocks()
        assertFalse(lockManager.isWakeLockHeld)
        assertFalse(lockManager.isWifiLockHeld)

        lockManager.acquireLocks()
        assertTrue(lockManager.isWakeLockHeld)
        assertTrue(lockManager.isWifiLockHeld)
    }
}
