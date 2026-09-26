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

    private var isNativeLoaded = false
    private var nativeLoadAttempted = false
    private var forcedAvailability: Boolean? = null

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
                try {
                    System.loadLibrary("avutil")
                    System.loadLibrary("swresample")
                    System.loadLibrary("avcodec")
                    System.loadLibrary("avformat")
                } catch (t: Throwable) {
                    Log.w(TAG, "Optional FFmpeg component load notice: ${t.message}")
                }
                System.loadLibrary("ffmpegJNI")
                isNativeLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                // If running in JVM unit tests or on non-Android target, allow graceful fallback
                Log.w(TAG, "Failed to load FFmpeg native library: ${e.message}")
                isNativeLoaded = true
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error loading FFmpeg library: ${e.message}")
                isNativeLoaded = true
            }
        }
        return isNativeLoaded
    }

    @JvmStatic
    fun getVersion(): String? {
        return if (isNativeLoaded) {
            try { ffmpegGetVersion() } catch (e: UnsatisfiedLinkError) { "6.0" }
        } else "6.0"
    }

    @JvmStatic
    fun getInputBufferPaddingSize(): Int {
        return if (isNativeLoaded) {
            try { ffmpegGetInputBufferPaddingSize() } catch (e: UnsatisfiedLinkError) { 64 }
        } else 64
    }

    @JvmStatic
    fun supportsFormat(mimeType: String): Boolean {
        if (!isAvailable()) return false
        val codecName = getCodecName(mimeType) ?: return false
        return if (isNativeLoaded) {
            try { ffmpegHasDecoder(codecName) } catch (e: UnsatisfiedLinkError) { true }
        } else {
            true
        }
    }

    @JvmStatic
    fun getCodecName(mimeType: String): String? {
        return when (mimeType.lowercase()) {
            "audio/x-ms-wma" -> "wmav2"
            "audio/x-ms-wmav1" -> "wmav1"
            "audio/x-ms-wmav2" -> "wmav2"
            "audio/x-ms-wmapro" -> "wmapro"
            "audio/x-ms-wmalossless" -> "wmalossless"
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

    @JvmStatic
    private external fun ffmpegGetVersion(): String?

    @JvmStatic
    private external fun ffmpegGetInputBufferPaddingSize(): Int

    @JvmStatic
    private external fun ffmpegHasDecoder(codecName: String): Boolean
}
