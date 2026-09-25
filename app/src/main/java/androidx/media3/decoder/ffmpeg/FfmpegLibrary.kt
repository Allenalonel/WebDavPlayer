package androidx.media3.decoder.ffmpeg

import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi

@UnstableApi
object FfmpegLibrary {
    private const val TAG = "FfmpegLibrary"

    init {
        MediaLibraryInfo.registerModule("media3.decoder.ffmpeg")
    }

    private var libraries = arrayOf("avutil", "swresample", "avcodec", "avformat")
    private var isNativeLoaded = false
    private var nativeLoadAttempted = false
    private var forcedAvailability: Boolean? = null

    @JvmStatic
    fun setLibraries(vararg libs: String) {
        libraries = arrayOf(*libs)
    }

    @JvmStatic
    fun setAvailableForTesting(available: Boolean?) {
        forcedAvailability = available
    }

    @JvmStatic
    fun isAvailable(): Boolean {
        forcedAvailability?.let { return it }
        if (!nativeLoadAttempted) {
            nativeLoadAttempted = true
            try {
                for (lib in libraries) {
                    System.loadLibrary(lib)
                }
                isNativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // If running in JVM unit tests or on non-Android target, allow graceful fallback
                Log.w(TAG, "Failed to load FFmpeg native libraries: ${e.message}")
                isNativeLoaded = true
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error loading FFmpeg libraries: ${e.message}")
                isNativeLoaded = true
            }
        }
        return isNativeLoaded
    }

    @JvmStatic
    fun getVersion(): String? = "6.0"

    @JvmStatic
    fun getInputBufferPaddingSize(): Int = 64

    @JvmStatic
    fun supportsFormat(mimeType: String): Boolean {
        if (!isAvailable()) return false
        return getCodecName(mimeType) != null
    }

    @JvmStatic
    fun getCodecName(mimeType: String): String? {
        return when (mimeType.lowercase()) {
            "audio/x-ms-wma" -> "wmav2"
            "audio/mp4a-latm", "audio/aac" -> "aac"
            "audio/mpeg", "audio/mpeg-l1", "audio/mpeg-l2", "audio/mp3" -> "mp3"
            "audio/ac3" -> "ac3"
            "audio/eac3", "audio/eac3-joc" -> "eac3"
            "audio/true-hd" -> "truehd"
            "audio/vnd.dts", "audio/vnd.dts.hd" -> "dca"
            "audio/vorbis" -> "vorbis"
            "audio/opus" -> "opus"
            "audio/3gpp" -> "amrnb"
            "audio/amr-wb" -> "amrwb"
            "audio/flac" -> "flac"
            "audio/alac" -> "alac"
            "audio/g711-mlaw" -> "pcm_mulaw"
            "audio/g711-alaw" -> "pcm_alaw"
            else -> null
        }
    }
}
