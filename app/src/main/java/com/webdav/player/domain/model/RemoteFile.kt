package com.webdav.player.domain.model

data class RemoteFile(
    val name: String,
    val path: String,
    val size: Long = 0L,
    val lastModified: String? = null,
    val contentType: String? = null,
    val fileType: RemoteFileType = RemoteFileType.fromFileName(name),
) {
    val isAudio: Boolean get() = fileType is RemoteFileType.Audio
    val isLyrics: Boolean get() = fileType is RemoteFileType.Lyrics
    val isCue: Boolean get() = fileType is RemoteFileType.Cue || name.endsWith(".cue", ignoreCase = true)

    val qualityBadge: AudioQualityBadge?
        get() = resolveBadge(null)

    val qualitySummary: String
        get() = formatQualitySummary(null)

    val audiophileSpecs: String
        get() = formatAudiophileSpecs(null)

    fun resolveBadge(metadata: TrackMetadata? = null): AudioQualityBadge? = AudioQuality.resolveBadge(this, metadata)

    fun formatQualitySummary(metadata: TrackMetadata? = null): String = AudioQuality.formatQualitySummary(this, metadata)

    fun formatAudiophileSpecs(metadata: TrackMetadata? = null): String = AudioQuality.formatAudiophileSpecs(this, metadata)

    fun estimateBitrateKbps(durationMs: Long = 0L): Int? = AudioQuality.estimateBitrateKbps(name, size, durationMs)
}
