package com.webdav.player.domain.model

data class AudioTrack(
    val id: String,
    val serverId: Long,
    val remotePath: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val size: Long = 0L,
    val format: AudioFormat,
    val coverThumbnailPath: String? = null,
) {
    val fileName: String
        get() = remotePath.substringAfterLast('/').ifEmpty { title }

    val qualityBadge: AudioQualityBadge
        get() = AudioQuality.resolveBadge(format, fileName, size, durationMs)

    val badge: AudioQualityBadge
        get() = qualityBadge

    val qualityLevel: AudioQualityLevel
        get() = qualityBadge.qualityLevel

    val isLossless: Boolean
        get() = qualityBadge.isLossless

    val estimatedBitrateKbps: Int?
        get() = qualityBadge.estimatedBitrateKbps

    val qualitySummary: String
        get() = AudioQuality.formatQualitySummary(qualityBadge)

    val audiophileSpecs: String
        get() = audiophileSpecsModel.formatted

    val audiophileSpecsModel: AudiophileSpecs
        get() = AudioQuality.resolveAudiophileSpecs(format, fileName, size, durationMs)

    fun resolveBadge(): AudioQualityBadge = qualityBadge

    fun formatQualitySummary(): String = qualitySummary

    fun streamUrl(server: WebDavServer): String = server.resolveFileUrl(remotePath)

    fun withMetadata(metadata: TrackMetadata): AudioTrack =
        copy(
            title = metadata.title?.takeIf { it.isNotBlank() } ?: this.title,
            artist = metadata.artist?.takeIf { it.isNotBlank() } ?: this.artist,
            album = metadata.album?.takeIf { it.isNotBlank() } ?: this.album,
            durationMs = if (metadata.durationMs > 0L) metadata.durationMs else this.durationMs,
            coverThumbnailPath = metadata.coverThumbnailPath ?: this.coverThumbnailPath,
        )

    companion object {
        fun fromRemoteFile(
            server: WebDavServer,
            file: RemoteFile,
            metadata: TrackMetadata? = null,
        ): AudioTrack? {
            val format = (file.fileType as? RemoteFileType.Audio)?.format ?: return null
            val baseTrack =
                AudioTrack(
                    id = "${server.id}:${file.path}",
                    serverId = server.id,
                    remotePath = file.path,
                    title = metadata?.displayTitle(file.name) ?: file.name,
                    artist = metadata?.displayArtist(),
                    album = metadata?.album,
                    durationMs = metadata?.durationMs ?: 0L,
                    size = file.size,
                    format = format,
                    coverThumbnailPath = metadata?.coverThumbnailPath,
                )
            return baseTrack
        }
    }
}
