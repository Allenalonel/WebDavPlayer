package com.webdav.player.domain.model

enum class AudioQualityLevel {
    LOSSLESS,
    HIGH_QUALITY,
    STANDARD,
    COMPRESSED,
}

data class AudioQualityBadge(
    val label: String,
    val format: AudioFormat? = null,
    val isLossless: Boolean = false,
    val estimatedBitrateKbps: Int? = null,
    val qualityLevel: AudioQualityLevel = AudioQualityLevel.STANDARD,
)

object AudioQuality {
    private val BITRATE_PATTERN = Regex("""(?:\[|\(|\b)(\d{2,4})\s*k(?:bps)?(?:\]|\)|\b)""", RegexOption.IGNORE_CASE)

    fun estimateBitrateKbps(
        fileName: String,
        fileSize: Long,
        durationMs: Long,
    ): Int? {
        // 1. Check if filename contains explicit bitrate tag like [320k] or (256kbps)
        val match = BITRATE_PATTERN.find(fileName)
        if (match != null) {
            val rate = match.groupValues[1].toIntOrNull()
            if (rate != null && rate in 32..1411) {
                return rate
            }
        }

        // 2. Estimate from file size and duration
        if (fileSize > 0L && durationMs >= 1000L) {
            val raw = (fileSize * 8L) / durationMs
            return when (raw) {
                in 300..360 -> 320
                in 240..275 -> 256
                in 210..235 -> 224
                in 180..205 -> 192
                in 150..175 -> 160
                in 115..140 -> 128
                in 85..110 -> 96
                in 55..75 -> 64
                in 32..999 -> raw.toInt()
                else -> null
            }
        }

        return null
    }

    fun resolveBadge(
        format: AudioFormat,
        fileName: String,
        fileSize: Long = 0L,
        durationMs: Long = 0L,
    ): AudioQualityBadge =
        when (format) {
            AudioFormat.FLAC -> {
                AudioQualityBadge(
                    label = "FLAC",
                    format = AudioFormat.FLAC,
                    isLossless = true,
                    estimatedBitrateKbps = null,
                    qualityLevel = AudioQualityLevel.LOSSLESS,
                )
            }

            AudioFormat.WAV -> {
                AudioQualityBadge(
                    label = "WAV",
                    format = AudioFormat.WAV,
                    isLossless = true,
                    estimatedBitrateKbps = null,
                    qualityLevel = AudioQualityLevel.LOSSLESS,
                )
            }

            AudioFormat.MP3 -> {
                val bitrate = estimateBitrateKbps(fileName, fileSize, durationMs)
                val label = if (bitrate != null) "MP3 ${bitrate}k" else "MP3"
                val level =
                    when {
                        bitrate != null && bitrate >= 300 -> AudioQualityLevel.HIGH_QUALITY
                        bitrate != null && bitrate >= 180 -> AudioQualityLevel.STANDARD
                        bitrate != null && bitrate < 180 -> AudioQualityLevel.COMPRESSED
                        else -> AudioQualityLevel.STANDARD
                    }
                AudioQualityBadge(
                    label = label,
                    format = AudioFormat.MP3,
                    isLossless = false,
                    estimatedBitrateKbps = bitrate,
                    qualityLevel = level,
                )
            }

            AudioFormat.AAC -> {
                val bitrate = estimateBitrateKbps(fileName, fileSize, durationMs)
                val label = if (bitrate != null) "AAC ${bitrate}k" else "AAC"
                val level = if (bitrate != null && bitrate >= 256) AudioQualityLevel.HIGH_QUALITY else AudioQualityLevel.STANDARD
                AudioQualityBadge(
                    label = label,
                    format = AudioFormat.AAC,
                    isLossless = false,
                    estimatedBitrateKbps = bitrate,
                    qualityLevel = level,
                )
            }

            AudioFormat.OGG -> {
                val bitrate = estimateBitrateKbps(fileName, fileSize, durationMs)
                val label = if (bitrate != null) "OGG ${bitrate}k" else "OGG"
                AudioQualityBadge(
                    label = label,
                    format = AudioFormat.OGG,
                    isLossless = false,
                    estimatedBitrateKbps = bitrate,
                    qualityLevel = AudioQualityLevel.STANDARD,
                )
            }

            AudioFormat.M4A -> {
                val bitrate = estimateBitrateKbps(fileName, fileSize, durationMs)
                val label = if (bitrate != null) "M4A ${bitrate}k" else "M4A"
                AudioQualityBadge(
                    label = label,
                    format = AudioFormat.M4A,
                    isLossless = false,
                    estimatedBitrateKbps = bitrate,
                    qualityLevel = AudioQualityLevel.STANDARD,
                )
            }

            AudioFormat.WMA -> {
                val bitrate = estimateBitrateKbps(fileName, fileSize, durationMs)
                val label = if (bitrate != null) "WMA ${bitrate}k" else "WMA"
                AudioQualityBadge(
                    label = label,
                    format = AudioFormat.WMA,
                    isLossless = false,
                    estimatedBitrateKbps = bitrate,
                    qualityLevel = AudioQualityLevel.COMPRESSED,
                )
            }
        }

    fun resolveBadge(
        file: RemoteFile,
        metadata: TrackMetadata? = null,
    ): AudioQualityBadge? =
        when (val type = file.fileType) {
            is RemoteFileType.Audio -> {
                resolveBadge(
                    format = type.format,
                    fileName = file.name,
                    fileSize = file.size,
                    durationMs = metadata?.durationMs ?: 0L,
                )
            }

            is RemoteFileType.Lyrics -> {
                AudioQualityBadge(
                    label = "LRC",
                    format = null,
                    isLossless = false,
                    qualityLevel = AudioQualityLevel.STANDARD,
                )
            }

            is RemoteFileType.Other -> {
                val ext =
                    file.name
                        .substringAfterLast('.', "")
                        .uppercase()
                        .take(4)
                if (ext.isNotBlank()) {
                    AudioQualityBadge(
                        label = ext,
                        format = null,
                        isLossless = false,
                        qualityLevel = AudioQualityLevel.COMPRESSED,
                    )
                } else {
                    null
                }
            }
        }

    fun formatQualitySummary(
        badge: AudioQualityBadge,
        isLyrics: Boolean = false,
    ): String {
        val bitrateStr = badge.estimatedBitrateKbps?.let { " ($it kbps)" } ?: ""
        return when {
            badge.isLossless -> "${badge.label} 无损音频"
            badge.qualityLevel == AudioQualityLevel.HIGH_QUALITY -> "${badge.format?.extension?.uppercase() ?: badge.label} 极高品质$bitrateStr"
            badge.qualityLevel == AudioQualityLevel.STANDARD && badge.format != null -> "${badge.format.extension.uppercase()} 标准音质$bitrateStr"
            badge.qualityLevel == AudioQualityLevel.COMPRESSED && badge.format != null -> "${badge.format.extension.uppercase()} 压缩音频$bitrateStr"
            isLyrics -> "LRC 歌词文件"
            else -> "${badge.label} 文件"
        }
    }

    fun formatQualitySummary(
        file: RemoteFile,
        metadata: TrackMetadata? = null,
    ): String {
        val badge = resolveBadge(file, metadata) ?: return "未知文件"
        return formatQualitySummary(badge, isLyrics = file.isLyrics)
    }
}

// Top-level convenience functions
fun estimateBitrateKbps(
    fileName: String,
    fileSize: Long,
    durationMs: Long,
): Int? = AudioQuality.estimateBitrateKbps(fileName, fileSize, durationMs)

fun resolveBadge(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
): AudioQualityBadge? = AudioQuality.resolveBadge(file, metadata)

fun resolveBadge(
    format: AudioFormat,
    fileName: String,
    fileSize: Long = 0L,
    durationMs: Long = 0L,
): AudioQualityBadge = AudioQuality.resolveBadge(format, fileName, fileSize, durationMs)

fun formatQualitySummary(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
): String = AudioQuality.formatQualitySummary(file, metadata)

fun formatQualitySummary(
    badge: AudioQualityBadge,
    isLyrics: Boolean = false,
): String = AudioQuality.formatQualitySummary(badge, isLyrics)
