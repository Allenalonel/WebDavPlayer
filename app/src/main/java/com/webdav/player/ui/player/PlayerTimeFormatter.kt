package com.webdav.player.ui.player

import com.webdav.player.domain.model.PlaybackProgress

/**
 * 格式化播放进度与时长的辅助函数 (mm:ss 或 hh:mm:ss)。
 * 委托至 PlaybackProgress.formatMs。
 */
object PlayerTimeFormatter {
    fun formatMs(durationMs: Long): String = PlaybackProgress.formatMs(durationMs)
}
