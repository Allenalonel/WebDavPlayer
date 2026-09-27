package com.webdav.player.ui.theme

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AdaptiveLauncherIconTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun manifestRegistersAdaptiveAndRoundLauncherIcons() {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )

        assertNotEquals("App icon must not be 0", 0, appInfo.icon)
        val iconResName = context.resources.getResourceEntryName(appInfo.icon)
        val iconResType = context.resources.getResourceTypeName(appInfo.icon)
        assertEquals("ic_launcher", iconResName)
        assertEquals("mipmap", iconResType)
    }

    @Test
    fun adaptiveIconResourcesArePresentAndResolved() {
        assertNotEquals(0, R.mipmap.ic_launcher)
        assertNotEquals(0, R.mipmap.ic_launcher_round)
        assertNotEquals(0, R.drawable.ic_launcher_background)
        assertNotEquals(0, R.drawable.ic_launcher_foreground)
        assertNotEquals(0, R.drawable.ic_launcher_monochrome)
    }

    @Test
    fun adaptiveIconXmlDefinesBackgroundForegroundAndMonochromeLayers() {
        val mipmapDir = File("src/main/res/mipmap-anydpi-v26")
        val resolvedDir = if (mipmapDir.isDirectory) mipmapDir else File("app/src/main/res/mipmap-anydpi-v26")
        assertTrue("mipmap-anydpi-v26 directory must exist", resolvedDir.isDirectory)

        val launcherXml = File(resolvedDir, "ic_launcher.xml")
        assertTrue("ic_launcher.xml must exist", launcherXml.isFile)
        val launcherContent = launcherXml.readText()
        assertTrue("ic_launcher must reference background", launcherContent.contains("@drawable/ic_launcher_background"))
        assertTrue("ic_launcher must reference foreground", launcherContent.contains("@drawable/ic_launcher_foreground"))
        assertTrue("ic_launcher must reference monochrome", launcherContent.contains("@drawable/ic_launcher_monochrome"))

        val roundXml = File(resolvedDir, "ic_launcher_round.xml")
        assertTrue("ic_launcher_round.xml must exist", roundXml.isFile)
        val roundContent = roundXml.readText()
        assertTrue("ic_launcher_round must reference background", roundContent.contains("@drawable/ic_launcher_background"))
        assertTrue("ic_launcher_round must reference foreground", roundContent.contains("@drawable/ic_launcher_foreground"))
        assertTrue("ic_launcher_round must reference monochrome", roundContent.contains("@drawable/ic_launcher_monochrome"))
    }

    @Test
    fun drawableVectorsHaveCompliant108dpViewports() {
        val drawableDir = File("src/main/res/drawable")
        val resolvedDir = if (drawableDir.isDirectory) drawableDir else File("app/src/main/res/drawable")
        val bgXml = File(resolvedDir, "ic_launcher_background.xml")
        val fgXml = File(resolvedDir, "ic_launcher_foreground.xml")
        val monoXml = File(resolvedDir, "ic_launcher_monochrome.xml")

        assertTrue("ic_launcher_background.xml must exist", bgXml.isFile)
        assertTrue("ic_launcher_foreground.xml must exist", fgXml.isFile)
        assertTrue("ic_launcher_monochrome.xml must exist", monoXml.isFile)

        val bgContent = bgXml.readText()
        val fgContent = fgXml.readText()
        val monoContent = monoXml.readText()

        assertTrue(bgContent.contains("viewportWidth=\"108\""))
        assertTrue(bgContent.contains("viewportHeight=\"108\""))

        assertTrue(fgContent.contains("viewportWidth=\"108\""))
        assertTrue(fgContent.contains("viewportHeight=\"108\""))

        assertTrue(monoContent.contains("viewportWidth=\"108\""))
        assertTrue(monoContent.contains("viewportHeight=\"108\""))
    }

    @Test
    fun manifestDirectlyDeclaresIconAndRoundIcon() {
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.isFile) it else File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist", manifestFile.isFile)
        val content = manifestFile.readText()
        assertTrue("Manifest must declare standard launcher icon", content.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue("Manifest must declare round launcher icon", content.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
    }
}
