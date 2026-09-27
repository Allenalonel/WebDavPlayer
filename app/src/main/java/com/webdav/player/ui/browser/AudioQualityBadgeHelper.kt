package com.webdav.player.ui.browser

import com.webdav.player.domain.model.AudioQuality
import com.webdav.player.domain.model.AudioQualityBadge
import com.webdav.player.domain.model.AudioQualityLevel
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.TrackMetadata

typealias AudioQualityLevel = com.webdav.player.domain.model.AudioQualityLevel
typealias AudioQualityBadge = com.webdav.player.domain.model.AudioQualityBadge

/**
 * UI presentation adapter that delegates audio quality evaluation and formatting
 * to the domain model ([AudioQuality]).
 */
object AudioQualityBadgeHelper {
    fun estimateBitrateKbps(
        fileName: String,
        fileSize: Long,
        durationMs: Long,
    ): Int? = AudioQuality.estimateBitrateKbps(fileName, fileSize, durationMs)

    fun getBadge(
        file: RemoteFile,
        metadata: TrackMetadata? = null,
    ): AudioQualityBadge? = AudioQuality.resolveBadge(file, metadata)

    fun formatQualitySummary(
        file: RemoteFile,
        metadata: TrackMetadata? = null,
    ): String = AudioQuality.formatQualitySummary(file, metadata)
}
