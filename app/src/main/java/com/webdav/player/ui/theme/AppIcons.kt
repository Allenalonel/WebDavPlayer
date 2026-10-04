package com.webdav.player.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Localized, explicit AppIcons catalog for WebDavPlayer.
 *
 * Consolidates all icons actually referenced across the app's composables,
 * completely eliminating dependency on `material-icons-extended` (which bloated
 * bytecode by 40+ MB DEX).
 */
object AppIcons {
    // --- Standard Material Icons (from material-icons-core) ---
    val Add: ImageVector = Icons.Filled.Add
    val ArrowBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Check: ImageVector = Icons.Filled.Check
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val Close: ImageVector = Icons.Filled.Close
    val Delete: ImageVector = Icons.Filled.Delete
    val Edit: ImageVector = Icons.Filled.Edit
    val Home: ImageVector = Icons.Filled.Home
    val Info: ImageVector = Icons.Filled.Info
    val Lock: ImageVector = Icons.Filled.Lock
    val MoreVert: ImageVector = Icons.Filled.MoreVert
    val Person: ImageVector = Icons.Filled.Person
    val PlayArrow: ImageVector = Icons.Filled.PlayArrow
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Warning: ImageVector = Icons.Filled.Warning

    // --- Localized Extended Material Icons ---
    val Folder: ImageVector
        get() {
            if (_folder != null) {
                return _folder!!
            }
            _folder =
                materialIcon(name = "Filled.Folder") {
                    materialPath {
                        moveTo(10.0f, 4.0f)
                        horizontalLineTo(4.0f)
                        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                        lineTo(2.0f, 18.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(16.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        verticalLineTo(8.0f)
                        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                        horizontalLineToRelative(-8.0f)
                        lineToRelative(-2.0f, -2.0f)
                        close()
                    }
                }
            return _folder!!
        }
    private var _folder: ImageVector? = null

    val FolderOpen: ImageVector
        get() {
            if (_folderOpen != null) {
                return _folderOpen!!
            }
            _folderOpen =
                materialIcon(name = "Filled.FolderOpen") {
                    materialPath {
                        moveTo(20.0f, 6.0f)
                        horizontalLineToRelative(-8.0f)
                        lineToRelative(-2.0f, -2.0f)
                        lineTo(4.0f, 4.0f)
                        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                        lineTo(2.0f, 18.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(16.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(22.0f, 8.0f)
                        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                        close()
                        moveTo(20.0f, 18.0f)
                        lineTo(4.0f, 18.0f)
                        lineTo(4.0f, 8.0f)
                        horizontalLineToRelative(16.0f)
                        verticalLineToRelative(10.0f)
                        close()
                    }
                }
            return _folderOpen!!
        }
    private var _folderOpen: ImageVector? = null

    val Storage: ImageVector
        get() {
            if (_storage != null) {
                return _storage!!
            }
            _storage =
                materialIcon(name = "Filled.Storage") {
                    materialPath {
                        moveTo(2.0f, 20.0f)
                        horizontalLineToRelative(20.0f)
                        verticalLineToRelative(-4.0f)
                        lineTo(2.0f, 16.0f)
                        verticalLineToRelative(4.0f)
                        close()
                        moveTo(4.0f, 17.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(2.0f)
                        lineTo(4.0f, 19.0f)
                        verticalLineToRelative(-2.0f)
                        close()
                        moveTo(2.0f, 4.0f)
                        verticalLineToRelative(4.0f)
                        horizontalLineToRelative(20.0f)
                        lineTo(22.0f, 4.0f)
                        lineTo(2.0f, 4.0f)
                        close()
                        moveTo(6.0f, 7.0f)
                        lineTo(4.0f, 7.0f)
                        lineTo(4.0f, 5.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(2.0f)
                        close()
                        moveTo(2.0f, 14.0f)
                        horizontalLineToRelative(20.0f)
                        verticalLineToRelative(-4.0f)
                        lineTo(2.0f, 10.0f)
                        verticalLineToRelative(4.0f)
                        close()
                        moveTo(4.0f, 11.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(2.0f)
                        lineTo(4.0f, 13.0f)
                        verticalLineToRelative(-2.0f)
                        close()
                    }
                }
            return _storage!!
        }
    private var _storage: ImageVector? = null

    val InsertDriveFile: ImageVector
        get() {
            if (_insertDriveFile != null) {
                return _insertDriveFile!!
            }
            _insertDriveFile =
                materialIcon(
                    name = "AutoMirrored.Filled.InsertDriveFile",
                    autoMirror =
                    true,
                ) {
                    materialPath {
                        moveTo(6.0f, 2.0f)
                        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                        lineTo(4.0f, 20.0f)
                        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
                        lineTo(18.0f, 22.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(20.0f, 8.0f)
                        lineToRelative(-6.0f, -6.0f)
                        lineTo(6.0f, 2.0f)
                        close()
                        moveTo(13.0f, 9.0f)
                        lineTo(13.0f, 3.5f)
                        lineTo(18.5f, 9.0f)
                        lineTo(13.0f, 9.0f)
                        close()
                    }
                }
            return _insertDriveFile!!
        }
    private var _insertDriveFile: ImageVector? = null

    val QueueMusic: ImageVector
        get() {
            if (_queueMusic != null) {
                return _queueMusic!!
            }
            _queueMusic =
                materialIcon(name = "AutoMirrored.Filled.QueueMusic", autoMirror = true) {
                    materialPath {
                        moveTo(15.0f, 6.0f)
                        horizontalLineTo(3.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(12.0f)
                        verticalLineTo(6.0f)
                        close()
                        moveTo(15.0f, 10.0f)
                        horizontalLineTo(3.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(12.0f)
                        verticalLineTo(10.0f)
                        close()
                        moveTo(3.0f, 16.0f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineTo(3.0f)
                        verticalLineTo(16.0f)
                        close()
                        moveTo(17.0f, 6.0f)
                        verticalLineToRelative(8.18f)
                        curveTo(16.69f, 14.07f, 16.35f, 14.0f, 16.0f, 14.0f)
                        curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                        reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                        reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                        verticalLineTo(8.0f)
                        horizontalLineToRelative(3.0f)
                        verticalLineTo(6.0f)
                        horizontalLineTo(17.0f)
                        close()
                    }
                }
            return _queueMusic!!
        }
    private var _queueMusic: ImageVector? = null

    val Audiotrack: ImageVector
        get() {
            if (_audiotrack != null) {
                return _audiotrack!!
            }
            _audiotrack =
                materialIcon(name = "Filled.Audiotrack") {
                    materialPath {
                        moveTo(12.0f, 3.0f)
                        verticalLineToRelative(9.28f)
                        curveToRelative(-0.47f, -0.17f, -0.97f, -0.28f, -1.5f, -0.28f)
                        curveTo(8.01f, 12.0f, 6.0f, 14.01f, 6.0f, 16.5f)
                        reflectiveCurveTo(8.01f, 21.0f, 10.5f, 21.0f)
                        curveToRelative(2.31f, 0.0f, 4.2f, -1.75f, 4.45f, -4.0f)
                        horizontalLineTo(15.0f)
                        verticalLineTo(6.0f)
                        horizontalLineToRelative(4.0f)
                        verticalLineTo(3.0f)
                        horizontalLineToRelative(-7.0f)
                        close()
                    }
                }
            return _audiotrack!!
        }
    private var _audiotrack: ImageVector? = null

    val Description: ImageVector
        get() {
            if (_description != null) {
                return _description!!
            }
            _description =
                materialIcon(name = "Filled.Description") {
                    materialPath {
                        moveTo(14.0f, 2.0f)
                        lineTo(6.0f, 2.0f)
                        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                        lineTo(4.0f, 20.0f)
                        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
                        lineTo(18.0f, 22.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(20.0f, 8.0f)
                        lineToRelative(-6.0f, -6.0f)
                        close()
                        moveTo(16.0f, 18.0f)
                        lineTo(8.0f, 18.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(2.0f)
                        close()
                        moveTo(16.0f, 14.0f)
                        lineTo(8.0f, 14.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(2.0f)
                        close()
                        moveTo(13.0f, 9.0f)
                        lineTo(13.0f, 3.5f)
                        lineTo(18.5f, 9.0f)
                        lineTo(13.0f, 9.0f)
                        close()
                    }
                }
            return _description!!
        }
    private var _description: ImageVector? = null

    val Cloud: ImageVector
        get() {
            if (_cloud != null) {
                return _cloud!!
            }
            _cloud =
                materialIcon(name = "Filled.Cloud") {
                    materialPath {
                        moveTo(19.35f, 10.04f)
                        curveTo(18.67f, 6.59f, 15.64f, 4.0f, 12.0f, 4.0f)
                        curveTo(9.11f, 4.0f, 6.6f, 5.64f, 5.35f, 8.04f)
                        curveTo(2.34f, 8.36f, 0.0f, 10.91f, 0.0f, 14.0f)
                        curveToRelative(0.0f, 3.31f, 2.69f, 6.0f, 6.0f, 6.0f)
                        horizontalLineToRelative(13.0f)
                        curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                        curveToRelative(0.0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                        close()
                    }
                }
            return _cloud!!
        }
    private var _cloud: ImageVector? = null

    val ErrorOutline: ImageVector
        get() {
            if (_errorOutline != null) {
                return _errorOutline!!
            }
            _errorOutline =
                materialIcon(name = "Filled.ErrorOutline") {
                    materialPath {
                        moveTo(11.0f, 15.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-2.0f)
                        close()
                        moveTo(11.0f, 7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(-2.0f)
                        close()
                        moveTo(11.99f, 2.0f)
                        curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                        reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                        reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                        close()
                        moveTo(12.0f, 20.0f)
                        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                        close()
                    }
                }
            return _errorOutline!!
        }
    private var _errorOutline: ImageVector? = null

    val ChevronRight: ImageVector
        get() {
            if (_chevronRight != null) {
                return _chevronRight!!
            }
            _chevronRight =
                materialIcon(name = "Filled.ChevronRight") {
                    materialPath {
                        moveTo(10.0f, 6.0f)
                        lineTo(8.59f, 7.41f)
                        lineTo(13.17f, 12.0f)
                        lineToRelative(-4.58f, 4.59f)
                        lineTo(10.0f, 18.0f)
                        lineToRelative(6.0f, -6.0f)
                        close()
                    }
                }
            return _chevronRight!!
        }
    private var _chevronRight: ImageVector? = null

    val ArrowForwardIos: ImageVector
        get() {
            if (_arrowForwardIos != null) {
                return _arrowForwardIos!!
            }
            _arrowForwardIos =
                materialIcon(
                    name = "AutoMirrored.Filled.ArrowForwardIos",
                    autoMirror =
                    true,
                ) {
                    materialPath {
                        moveTo(6.23f, 20.23f)
                        lineToRelative(1.77f, 1.77f)
                        lineToRelative(10.0f, -10.0f)
                        lineToRelative(-10.0f, -10.0f)
                        lineToRelative(-1.77f, 1.77f)
                        lineToRelative(8.23f, 8.23f)
                        close()
                    }
                }
            return _arrowForwardIos!!
        }
    private var _arrowForwardIos: ImageVector? = null

    val Pause: ImageVector
        get() {
            if (_pause != null) {
                return _pause!!
            }
            _pause =
                materialIcon(name = "Filled.Pause") {
                    materialPath {
                        moveTo(6.0f, 19.0f)
                        horizontalLineToRelative(4.0f)
                        lineTo(10.0f, 5.0f)
                        lineTo(6.0f, 5.0f)
                        verticalLineToRelative(14.0f)
                        close()
                        moveTo(14.0f, 5.0f)
                        verticalLineToRelative(14.0f)
                        horizontalLineToRelative(4.0f)
                        lineTo(18.0f, 5.0f)
                        horizontalLineToRelative(-4.0f)
                        close()
                    }
                }
            return _pause!!
        }
    private var _pause: ImageVector? = null

    val Repeat: ImageVector
        get() {
            if (_repeat != null) {
                return _repeat!!
            }
            _repeat =
                materialIcon(name = "Filled.Repeat") {
                    materialPath {
                        moveTo(7.0f, 7.0f)
                        horizontalLineToRelative(10.0f)
                        verticalLineToRelative(3.0f)
                        lineToRelative(4.0f, -4.0f)
                        lineToRelative(-4.0f, -4.0f)
                        verticalLineToRelative(3.0f)
                        lineTo(5.0f, 5.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(7.0f, 7.0f)
                        close()
                        moveTo(17.0f, 17.0f)
                        lineTo(7.0f, 17.0f)
                        verticalLineToRelative(-3.0f)
                        lineToRelative(-4.0f, 4.0f)
                        lineToRelative(4.0f, 4.0f)
                        verticalLineToRelative(-3.0f)
                        horizontalLineToRelative(12.0f)
                        verticalLineToRelative(-6.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(4.0f)
                        close()
                    }
                }
            return _repeat!!
        }
    private var _repeat: ImageVector? = null

    val RepeatOne: ImageVector
        get() {
            if (_repeatOne != null) {
                return _repeatOne!!
            }
            _repeatOne =
                materialIcon(name = "Filled.RepeatOne") {
                    materialPath {
                        moveTo(7.0f, 7.0f)
                        horizontalLineToRelative(10.0f)
                        verticalLineToRelative(3.0f)
                        lineToRelative(4.0f, -4.0f)
                        lineToRelative(-4.0f, -4.0f)
                        verticalLineToRelative(3.0f)
                        lineTo(5.0f, 5.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(7.0f, 7.0f)
                        close()
                        moveTo(17.0f, 17.0f)
                        lineTo(7.0f, 17.0f)
                        verticalLineToRelative(-3.0f)
                        lineToRelative(-4.0f, 4.0f)
                        lineToRelative(4.0f, 4.0f)
                        verticalLineToRelative(-3.0f)
                        horizontalLineToRelative(12.0f)
                        verticalLineToRelative(-6.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(4.0f)
                        close()
                        moveTo(13.0f, 15.0f)
                        lineTo(13.0f, 9.0f)
                        horizontalLineToRelative(-1.0f)
                        lineToRelative(-2.0f, 1.0f)
                        verticalLineToRelative(1.0f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(4.0f)
                        lineTo(13.0f, 15.0f)
                        close()
                    }
                }
            return _repeatOne!!
        }
    private var _repeatOne: ImageVector? = null

    val Shuffle: ImageVector
        get() {
            if (_shuffle != null) {
                return _shuffle!!
            }
            _shuffle =
                materialIcon(name = "Filled.Shuffle") {
                    materialPath {
                        moveTo(10.59f, 9.17f)
                        lineTo(5.41f, 4.0f)
                        lineTo(4.0f, 5.41f)
                        lineToRelative(5.17f, 5.17f)
                        lineToRelative(1.42f, -1.41f)
                        close()
                        moveTo(14.5f, 4.0f)
                        lineToRelative(2.04f, 2.04f)
                        lineTo(4.0f, 18.59f)
                        lineTo(5.41f, 20.0f)
                        lineTo(17.96f, 7.46f)
                        lineTo(20.0f, 9.5f)
                        lineTo(20.0f, 4.0f)
                        horizontalLineToRelative(-5.5f)
                        close()
                        moveTo(14.83f, 13.41f)
                        lineToRelative(-1.41f, 1.41f)
                        lineToRelative(3.13f, 3.13f)
                        lineTo(14.5f, 20.0f)
                        lineTo(20.0f, 20.0f)
                        verticalLineToRelative(-5.5f)
                        lineToRelative(-2.04f, 2.04f)
                        lineToRelative(-3.13f, -3.13f)
                        close()
                    }
                }
            return _shuffle!!
        }
    private var _shuffle: ImageVector? = null

    val SkipNext: ImageVector
        get() {
            if (_skipNext != null) {
                return _skipNext!!
            }
            _skipNext =
                materialIcon(name = "Filled.SkipNext") {
                    materialPath {
                        moveTo(6.0f, 18.0f)
                        lineToRelative(8.5f, -6.0f)
                        lineTo(6.0f, 6.0f)
                        verticalLineToRelative(12.0f)
                        close()
                        moveTo(16.0f, 6.0f)
                        verticalLineToRelative(12.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineTo(6.0f)
                        horizontalLineToRelative(-2.0f)
                        close()
                    }
                }
            return _skipNext!!
        }
    private var _skipNext: ImageVector? = null

    val SkipPrevious: ImageVector
        get() {
            if (_skipPrevious != null) {
                return _skipPrevious!!
            }
            _skipPrevious =
                materialIcon(name = "Filled.SkipPrevious") {
                    materialPath {
                        moveTo(6.0f, 6.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(12.0f)
                        lineTo(6.0f, 18.0f)
                        close()
                        moveTo(9.5f, 12.0f)
                        lineToRelative(8.5f, 6.0f)
                        lineTo(18.0f, 6.0f)
                        close()
                    }
                }
            return _skipPrevious!!
        }
    private var _skipPrevious: ImageVector? = null

    val DeleteOutline: ImageVector
        get() {
            if (_deleteOutline != null) {
                return _deleteOutline!!
            }
            _deleteOutline =
                materialIcon(name = "Filled.DeleteOutline") {
                    materialPath {
                        moveTo(6.0f, 19.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(8.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(18.0f, 7.0f)
                        lineTo(6.0f, 7.0f)
                        verticalLineToRelative(12.0f)
                        close()
                        moveTo(8.0f, 9.0f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(10.0f)
                        lineTo(8.0f, 19.0f)
                        lineTo(8.0f, 9.0f)
                        close()
                        moveTo(15.5f, 4.0f)
                        lineToRelative(-1.0f, -1.0f)
                        horizontalLineToRelative(-5.0f)
                        lineToRelative(-1.0f, 1.0f)
                        lineTo(5.0f, 4.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(14.0f)
                        lineTo(19.0f, 4.0f)
                        close()
                    }
                }
            return _deleteOutline!!
        }
    private var _deleteOutline: ImageVector? = null

    val GraphicEq: ImageVector
        get() {
            if (_graphicEq != null) {
                return _graphicEq!!
            }
            _graphicEq =
                materialIcon(name = "Filled.GraphicEq") {
                    materialPath {
                        moveTo(7.0f, 18.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(9.0f, 6.0f)
                        lineTo(7.0f, 6.0f)
                        verticalLineToRelative(12.0f)
                        close()
                        moveTo(11.0f, 22.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(13.0f, 2.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(20.0f)
                        close()
                        moveTo(3.0f, 14.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(-4.0f)
                        lineTo(3.0f, 10.0f)
                        verticalLineToRelative(4.0f)
                        close()
                        moveTo(15.0f, 18.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(17.0f, 6.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(12.0f)
                        close()
                        moveTo(19.0f, 10.0f)
                        verticalLineToRelative(4.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(-4.0f)
                        horizontalLineToRelative(-2.0f)
                        close()
                    }
                }
            return _graphicEq!!
        }
    private var _graphicEq: ImageVector? = null

    val Error: ImageVector
        get() {
            if (_error != null) {
                return _error!!
            }
            _error =
                materialIcon(name = "Filled.Error") {
                    materialPath {
                        moveTo(12.0f, 2.0f)
                        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                        close()
                        moveTo(13.0f, 17.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(2.0f)
                        close()
                        moveTo(13.0f, 13.0f)
                        horizontalLineToRelative(-2.0f)
                        lineTo(11.0f, 7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(6.0f)
                        close()
                    }
                }
            return _error!!
        }
    private var _error: ImageVector? = null

    val Visibility: ImageVector
        get() {
            if (_visibility != null) {
                return _visibility!!
            }
            _visibility =
                materialIcon(name = "Filled.Visibility") {
                    materialPath {
                        moveTo(12.0f, 4.5f)
                        curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
                        curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                        reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
                        curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                        close()
                        moveTo(12.0f, 17.0f)
                        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                        reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                        reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                        reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                        close()
                        moveTo(12.0f, 9.0f)
                        curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                        reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                        reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                        reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                        close()
                    }
                }
            return _visibility!!
        }
    private var _visibility: ImageVector? = null

    val VisibilityOff: ImageVector
        get() {
            if (_visibilityOff != null) {
                return _visibilityOff!!
            }
            _visibilityOff =
                materialIcon(name = "Filled.VisibilityOff") {
                    materialPath {
                        moveTo(12.0f, 7.0f)
                        curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                        curveToRelative(0.0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                        lineToRelative(2.92f, 2.92f)
                        curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                        curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                        curveToRelative(-1.4f, 0.0f, -2.74f, 0.25f, -3.98f, 0.7f)
                        lineToRelative(2.16f, 2.16f)
                        curveTo(10.74f, 7.13f, 11.35f, 7.0f, 12.0f, 7.0f)
                        close()
                        moveTo(2.0f, 4.27f)
                        lineToRelative(2.28f, 2.28f)
                        lineToRelative(0.46f, 0.46f)
                        curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1.0f, 12.0f)
                        curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                        curveToRelative(1.55f, 0.0f, 3.03f, -0.3f, 4.38f, -0.84f)
                        lineToRelative(0.42f, 0.42f)
                        lineTo(19.73f, 22.0f)
                        lineTo(21.0f, 20.73f)
                        lineTo(3.27f, 3.0f)
                        lineTo(2.0f, 4.27f)
                        close()
                        moveTo(7.53f, 9.8f)
                        lineToRelative(1.55f, 1.55f)
                        curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                        curveToRelative(0.22f, 0.0f, 0.44f, -0.03f, 0.65f, -0.08f)
                        lineToRelative(1.55f, 1.55f)
                        curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                        curveToRelative(0.0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                        close()
                        moveTo(11.84f, 9.02f)
                        lineToRelative(3.15f, 3.15f)
                        lineToRelative(0.02f, -0.16f)
                        curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                        lineToRelative(-0.17f, 0.01f)
                        close()
                    }
                }
            return _visibilityOff!!
        }
    private var _visibilityOff: ImageVector? = null

    val Dns: ImageVector
        get() {
            if (_dns != null) {
                return _dns!!
            }
            _dns =
                materialIcon(name = "Filled.Dns") {
                    materialPath {
                        moveTo(20.0f, 13.0f)
                        horizontalLineTo(4.0f)
                        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                        verticalLineToRelative(6.0f)
                        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                        horizontalLineToRelative(16.0f)
                        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                        verticalLineToRelative(-6.0f)
                        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                        close()
                        moveTo(7.0f, 19.0f)
                        curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                        reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                        reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                        reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                        close()
                        moveTo(20.0f, 3.0f)
                        horizontalLineTo(4.0f)
                        curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                        verticalLineToRelative(6.0f)
                        curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                        horizontalLineToRelative(16.0f)
                        curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                        verticalLineTo(4.0f)
                        curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                        close()
                        moveTo(7.0f, 9.0f)
                        curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                        reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                        reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                        reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                        close()
                    }
                }
            return _dns!!
        }
    private var _dns: ImageVector? = null

    val Language: ImageVector
        get() {
            if (_language != null) {
                return _language!!
            }
            _language =
                materialIcon(name = "Filled.Language") {
                    materialPath {
                        moveTo(11.99f, 2.0f)
                        curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                        reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                        reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                        close()
                        moveTo(18.92f, 8.0f)
                        horizontalLineToRelative(-2.95f)
                        curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
                        curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
                        close()
                        moveTo(12.0f, 4.04f)
                        curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
                        horizontalLineToRelative(-3.82f)
                        curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
                        close()
                        moveTo(4.26f, 14.0f)
                        curveTo(4.1f, 13.36f, 4.0f, 12.69f, 4.0f, 12.0f)
                        reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2.0f)
                        horizontalLineToRelative(3.38f)
                        curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2.0f)
                        curveToRelative(0.0f, 0.68f, 0.06f, 1.34f, 0.14f, 2.0f)
                        lineTo(4.26f, 14.0f)
                        close()
                        moveTo(5.08f, 16.0f)
                        horizontalLineToRelative(2.95f)
                        curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
                        curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
                        close()
                        moveTo(8.03f, 8.0f)
                        lineTo(5.08f, 8.0f)
                        curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
                        curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8.0f)
                        close()
                        moveTo(12.0f, 19.96f)
                        curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
                        horizontalLineToRelative(3.82f)
                        curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
                        close()
                        moveTo(14.34f, 14.0f)
                        lineTo(9.66f, 14.0f)
                        curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2.0f)
                        curveToRelative(0.0f, -0.68f, 0.07f, -1.35f, 0.16f, -2.0f)
                        horizontalLineToRelative(4.68f)
                        curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2.0f)
                        curveToRelative(0.0f, 0.68f, -0.07f, 1.34f, -0.16f, 2.0f)
                        close()
                        moveTo(14.59f, 19.56f)
                        curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
                        horizontalLineToRelative(2.95f)
                        curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
                        close()
                        moveTo(16.36f, 14.0f)
                        curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2.0f)
                        curveToRelative(0.0f, -0.68f, -0.06f, -1.34f, -0.14f, -2.0f)
                        horizontalLineToRelative(3.38f)
                        curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2.0f)
                        reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2.0f)
                        horizontalLineToRelative(-3.38f)
                        close()
                    }
                }
            return _language!!
        }
    private var _language: ImageVector? = null

    val Security: ImageVector
        get() {
            if (_security != null) {
                return _security!!
            }
            _security =
                materialIcon(name = "Filled.Security") {
                    materialPath {
                        moveTo(12.0f, 1.0f)
                        lineTo(3.0f, 5.0f)
                        verticalLineToRelative(6.0f)
                        curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                        curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                        lineTo(21.0f, 5.0f)
                        lineToRelative(-9.0f, -4.0f)
                        close()
                        moveTo(12.0f, 11.99f)
                        horizontalLineToRelative(7.0f)
                        curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7.0f, 8.94f)
                        lineTo(12.0f, 12.0f)
                        lineTo(5.0f, 12.0f)
                        lineTo(5.0f, 6.3f)
                        lineToRelative(7.0f, -3.11f)
                        verticalLineToRelative(8.8f)
                        close()
                    }
                }
            return _security!!
        }
    private var _security: ImageVector? = null

    val NetworkCheck: ImageVector
        get() {
            if (_networkCheck != null) {
                return _networkCheck!!
            }
            _networkCheck =
                materialIcon(name = "Filled.NetworkCheck") {
                    materialPath {
                        moveTo(15.9f, 5.0f)
                        curveToRelative(-0.17f, 0.0f, -0.32f, 0.09f, -0.41f, 0.23f)
                        lineToRelative(-0.07f, 0.15f)
                        lineToRelative(-5.18f, 11.65f)
                        curveToRelative(-0.16f, 0.29f, -0.26f, 0.61f, -0.26f, 0.96f)
                        curveToRelative(0.0f, 1.11f, 0.9f, 2.01f, 2.01f, 2.01f)
                        curveToRelative(0.96f, 0.0f, 1.77f, -0.68f, 1.96f, -1.59f)
                        lineToRelative(0.01f, -0.03f)
                        lineTo(16.4f, 5.5f)
                        curveToRelative(0.0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
                        close()
                        moveTo(1.0f, 9.0f)
                        lineToRelative(2.0f, 2.0f)
                        curveToRelative(2.88f, -2.88f, 6.79f, -4.08f, 10.53f, -3.62f)
                        lineToRelative(1.19f, -2.68f)
                        curveTo(9.89f, 3.84f, 4.74f, 5.27f, 1.0f, 9.0f)
                        close()
                        moveTo(21.0f, 11.0f)
                        lineToRelative(2.0f, -2.0f)
                        curveToRelative(-1.64f, -1.64f, -3.55f, -2.82f, -5.59f, -3.57f)
                        lineToRelative(-0.53f, 2.82f)
                        curveToRelative(1.5f, 0.62f, 2.9f, 1.53f, 4.12f, 2.75f)
                        close()
                        moveTo(17.0f, 15.0f)
                        lineToRelative(2.0f, -2.0f)
                        curveToRelative(-0.8f, -0.8f, -1.7f, -1.42f, -2.66f, -1.89f)
                        lineToRelative(-0.55f, 2.92f)
                        curveToRelative(0.42f, 0.27f, 0.83f, 0.59f, 1.21f, 0.97f)
                        close()
                        moveTo(5.0f, 13.0f)
                        lineToRelative(2.0f, 2.0f)
                        curveToRelative(1.13f, -1.13f, 2.56f, -1.79f, 4.03f, -2.0f)
                        lineToRelative(1.28f, -2.88f)
                        curveToRelative(-2.63f, -0.08f, -5.3f, 0.87f, -7.31f, 2.88f)
                        close()
                    }
                }
            return _networkCheck!!
        }
    private var _networkCheck: ImageVector? = null
}
