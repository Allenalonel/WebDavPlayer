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

data class AudiophileSpecs(
    val format: AudioFormat,
    val sampleRate: String? = null,
    val bitDepth: String? = null,
    val bitrateKbps: Int? = null,
    val isHiRes: Boolean = false,
    val isLossless: Boolean = false,
    val formatted: String,
)

object AudioQuality {
    private val BITRATE_PATTERN = Regex("""(?:\[|\(|\b)(\d{2,4})\s*k(?:bps)?(?:\]|\)|\b)""", RegexOption.IGNORE_CASE)
    private val BITRATE_EXTENDED_PATTERN = Regex("""(?:\[|\(|\b)(\d{2,5})\s*k(?:bps)?(?:\]|\)|\b)""", RegexOption.IGNORE_CASE)

    private val SAMPLE_RATE_KHZ_PATTERN =
        Regex("""\b(44\.1|48|88\.2|96|176\.4|192|352\.8|384)\s*kHz\b""", RegexOption.IGNORE_CASE)
    private val SAMPLE_RATE_44_1K_PATTERN =
        Regex("""\b(44\.1)\s*k(?:Hz)?\b""", RegexOption.IGNORE_CASE)
    private val SAMPLE_RATE_HZ_PATTERN =
        Regex("""\b(44100|48000|88200|96000|176400|192000)\s*Hz\b""", RegexOption.IGNORE_CASE)
    private val SAMPLE_RATE_COMBO_PATTERN =
        Regex("""\b(44\.1|48|88\.2|96|176\.4|192)k\b""", RegexOption.IGNORE_CASE)

    private val BIT_DEPTH_PATTERN =
        Regex("""\b(16|24|32)\s*[-_ ]?bit\b""", RegexOption.IGNORE_CASE)
    private val BIT_DEPTH_SHORT_PATTERN =
        Regex("""\b(16|24|32)b\b""", RegexOption.IGNORE_CASE)

    private val HI_RES_SAMPLE_RATES =
        setOf("88.2kHz", "96kHz", "176.4kHz", "192kHz", "352.8kHz", "384kHz")
    private val HI_RES_BIT_DEPTHS =
        setOf("24-bit", "32-bit")

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

            AudioFormat.APE -> {
                AudioQualityBadge(
                    label = "APE",
                    format = AudioFormat.APE,
                    isLossless = true,
                    estimatedBitrateKbps = null,
                    qualityLevel = AudioQualityLevel.LOSSLESS,
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

            is RemoteFileType.Cue -> {
                AudioQualityBadge(
                    label = "CUE",
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
            badge.label == "CUE" -> "CUE 分轨索引"
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

    fun parseSampleRate(text: String): String? {
        val khzMatch = SAMPLE_RATE_KHZ_PATTERN.find(text)
        if (khzMatch != null) {
            return "${khzMatch.groupValues[1]}kHz"
        }
        val rate441Match = SAMPLE_RATE_44_1K_PATTERN.find(text)
        if (rate441Match != null) {
            return "${rate441Match.groupValues[1]}kHz"
        }
        val hzMatch = SAMPLE_RATE_HZ_PATTERN.find(text)
        if (hzMatch != null) {
            return when (hzMatch.groupValues[1]) {
                "44100" -> "44.1kHz"
                "48000" -> "48kHz"
                "88200" -> "88.2kHz"
                "96000" -> "96kHz"
                "176400" -> "176.4kHz"
                "192000" -> "192kHz"
                else -> null
            }
        }
        val comboMatch = SAMPLE_RATE_COMBO_PATTERN.find(text)
        if (comboMatch != null &&
            (text.contains("bit", ignoreCase = true) || text.contains("24b", ignoreCase = true) || text.contains("16b", ignoreCase = true))
        ) {
            return "${comboMatch.groupValues[1]}kHz"
        }
        return null
    }

    fun parseBitDepth(text: String): String? {
        val match = BIT_DEPTH_PATTERN.find(text)
        if (match != null) {
            return "${match.groupValues[1]}-bit"
        }
        val shortMatch = BIT_DEPTH_SHORT_PATTERN.find(text)
        if (shortMatch != null &&
            (
                text.contains(
                    "kHz",
                    ignoreCase = true,
                ) || text.contains("96k", ignoreCase = true) || text.contains("192k", ignoreCase = true) ||
                    text.contains("44.1k", ignoreCase = true) ||
                    text.contains("48k", ignoreCase = true)
            )
        ) {
            return "${shortMatch.groupValues[1]}-bit"
        }
        return null
    }

    fun calculateBitrateKbps(
        fileName: String,
        fileSize: Long,
        durationMs: Long,
    ): Int? {
        val match = BITRATE_EXTENDED_PATTERN.find(fileName)
        if (match != null) {
            val rate = match.groupValues[1].toIntOrNull()
            if (rate != null && rate in 32..9999) {
                return rate
            }
        }

        val standard = estimateBitrateKbps(fileName, fileSize, durationMs)
        if (standard != null) return standard

        if (fileSize > 0L && durationMs >= 1000L) {
            val raw = (fileSize * 8L) / durationMs
            if (raw in 1380..1440) return 1411
            if (raw in 32..9999) return raw.toInt()
        }
        return null
    }

    fun resolveAudiophileSpecs(
        format: AudioFormat,
        fileName: String,
        fileSize: Long = 0L,
        durationMs: Long = 0L,
    ): AudiophileSpecs {
        val formatName = format.extension.uppercase()
        val parsedSampleRate = parseSampleRate(fileName)
        val parsedBitDepth = parseBitDepth(fileName)
        val bitrate = calculateBitrateKbps(fileName, fileSize, durationMs)
        val isLossless = format == AudioFormat.FLAC || format == AudioFormat.WAV

        if (isLossless) {
            val sampleRate =
                parsedSampleRate ?: when {
                    format == AudioFormat.FLAC && bitrate != null && bitrate >= 2000 -> "96kHz"
                    format == AudioFormat.FLAC && bitrate != null && bitrate >= 1500 -> "48kHz"
                    format == AudioFormat.WAV && bitrate != null && bitrate >= 4000 -> "96kHz"
                    format == AudioFormat.WAV && bitrate != null && bitrate >= 2000 -> "48kHz"
                    else -> "44.1kHz"
                }

            val bitDepth =
                parsedBitDepth ?: when {
                    sampleRate in HI_RES_SAMPLE_RATES -> "24-bit"
                    bitrate != null && bitrate >= 2000 -> "24-bit"
                    else -> "16-bit"
                }

            val isHiRes =
                bitDepth in HI_RES_BIT_DEPTHS ||
                    sampleRate in HI_RES_SAMPLE_RATES ||
                    (bitrate != null && bitrate >= 2000) ||
                    fileName.contains("Hi-Res", ignoreCase = true) ||
                    fileName.contains("HiRes", ignoreCase = true)

            val prefix = if (isHiRes) "⚡ " else ""
            val formatted =
                if (bitrate != null) {
                    "${prefix}$formatName · $sampleRate / $bitDepth · $bitrate kbps"
                } else {
                    "${prefix}$formatName · $sampleRate / $bitDepth"
                }

            return AudiophileSpecs(
                format = format,
                sampleRate = sampleRate,
                bitDepth = bitDepth,
                bitrateKbps = bitrate,
                isHiRes = isHiRes,
                isLossless = true,
                formatted = formatted,
            )
        } else {
            val formatted =
                if (bitrate != null) {
                    "$formatName · $bitrate kbps"
                } else {
                    formatName
                }
            return AudiophileSpecs(
                format = format,
                sampleRate = parsedSampleRate,
                bitDepth = parsedBitDepth,
                bitrateKbps = bitrate,
                isHiRes = false,
                isLossless = false,
                formatted = formatted,
            )
        }
    }

    fun resolveAudiophileSpecs(track: AudioTrack?): AudiophileSpecs? {
        if (track == null) return null
        return resolveAudiophileSpecs(
            format = track.format,
            fileName = track.fileName,
            fileSize = track.size,
            durationMs = track.durationMs,
        )
    }

    fun formatAudiophileSpecs(track: AudioTrack?): String {
        if (track == null) return ""
        return resolveAudiophileSpecs(track)?.formatted.orEmpty()
    }

    fun formatAudiophileSpecs(
        file: RemoteFile,
        metadata: TrackMetadata? = null,
    ): String {
        val audioType = file.fileType as? RemoteFileType.Audio ?: return ""
        return resolveAudiophileSpecs(
            format = audioType.format,
            fileName = file.name,
            fileSize = file.size,
            durationMs = metadata?.durationMs ?: 0L,
        ).formatted
    }
}

// Top-level convenience functions
fun resolveAudiophileSpecs(track: AudioTrack?): AudiophileSpecs? = AudioQuality.resolveAudiophileSpecs(track)

fun formatAudiophileSpecs(track: AudioTrack?): String = AudioQuality.formatAudiophileSpecs(track)

fun formatAudiophileSpecs(
    file: RemoteFile,
    metadata: TrackMetadata? = null,
): String = AudioQuality.formatAudiophileSpecs(file, metadata)

fun resolveAudiophileSpecs(
    format: AudioFormat,
    fileName: String,
    fileSize: Long = 0L,
    durationMs: Long = 0L,
): AudiophileSpecs = AudioQuality.resolveAudiophileSpecs(format, fileName, fileSize, durationMs)

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
