package com.webdav.player.domain.model

enum class AudioFormat(val extension: String, val mimeType: String) {
    MP3("mp3", "audio/mpeg"),
    FLAC("flac", "audio/flac"),
    WAV("wav", "audio/wav"),
    WMA("wma", "audio/x-ms-wma"),
    AAC("aac", "audio/aac"),
    OGG("ogg", "audio/ogg"),
    M4A("m4a", "audio/mp4");

    companion object {
        fun fromExtension(ext: String): AudioFormat? =
            entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) }

        fun fromFileName(fileName: String): AudioFormat? {
            val ext = fileName.substringAfterLast('.', "")
            return fromExtension(ext)
        }
    }
}

sealed interface RemoteFileType {
    data class Audio(val format: AudioFormat) : RemoteFileType
    data object Lyrics : RemoteFileType
    data object Other : RemoteFileType

    companion object {
        fun fromFileName(fileName: String): RemoteFileType {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val audioFormat = AudioFormat.fromExtension(ext)
            return when {
                audioFormat != null -> Audio(audioFormat)
                ext == "lrc" -> Lyrics
                else -> Other
            }
        }
    }
}
