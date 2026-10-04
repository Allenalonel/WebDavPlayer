package com.webdav.player.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppIconsTest {
    @Test
    fun appIcons_standardIcons_areResolved() {
        val standardIcons =
            listOf(
                AppIcons.Add,
                AppIcons.ArrowBack,
                AppIcons.Check,
                AppIcons.CheckCircle,
                AppIcons.Close,
                AppIcons.Delete,
                AppIcons.Edit,
                AppIcons.Home,
                AppIcons.Info,
                AppIcons.Lock,
                AppIcons.MoreVert,
                AppIcons.Person,
                AppIcons.PlayArrow,
                AppIcons.Refresh,
                AppIcons.Warning,
            )

        for (icon in standardIcons) {
            assertNotNull("Icon should not be null", icon)
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
        }
    }

    @Test
    fun appIcons_localizedExtendedIcons_areInstantiatedAndCached() {
        val extendedIcons =
            listOf(
                AppIcons.Folder,
                AppIcons.FolderOpen,
                AppIcons.Storage,
                AppIcons.InsertDriveFile,
                AppIcons.QueueMusic,
                AppIcons.Audiotrack,
                AppIcons.Description,
                AppIcons.Cloud,
                AppIcons.ErrorOutline,
                AppIcons.ChevronRight,
                AppIcons.ArrowForwardIos,
                AppIcons.Pause,
                AppIcons.Repeat,
                AppIcons.RepeatOne,
                AppIcons.Shuffle,
                AppIcons.SkipNext,
                AppIcons.SkipPrevious,
                AppIcons.DeleteOutline,
                AppIcons.GraphicEq,
                AppIcons.Error,
                AppIcons.Visibility,
                AppIcons.VisibilityOff,
                AppIcons.Dns,
                AppIcons.Language,
                AppIcons.Security,
                AppIcons.NetworkCheck,
            )

        for (icon in extendedIcons) {
            assertNotNull("Icon should not be null", icon)
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
        }

        // Verify caching returns the same instance on second access
        assertSame(AppIcons.Folder, AppIcons.Folder)
        assertSame(AppIcons.Pause, AppIcons.Pause)
        assertSame(AppIcons.Audiotrack, AppIcons.Audiotrack)
    }
}
