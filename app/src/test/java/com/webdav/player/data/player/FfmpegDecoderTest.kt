package com.webdav.player.data.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.domain.model.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class FfmpegDecoderTest {

    private lateinit var context: Context

    private class TestRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
        fun callBuildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: Handler,
            eventListener: AudioRendererEventListener,
            out: ArrayList<Renderer>
        ) {
            buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                audioSink,
                eventHandler,
                eventListener,
                out
            )
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun ffmpegLibrary_supportsWmaMimeType() {
        assertTrue(
            "FfmpegLibrary should support WMA MIME type",
            FfmpegLibrary.supportsFormat(AudioFormat.WMA.mimeType)
        )
        assertEquals("wmav2", FfmpegLibrary.getCodecName(AudioFormat.WMA.mimeType))
    }

    @Test
    fun defaultRenderersFactory_instantiatesFfmpegAudioRenderer_whenModeOn() {
        val factory = TestRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        val out = ArrayList<Renderer>()
        val handler = Handler(Looper.getMainLooper())
        val listener = object : AudioRendererEventListener {}
        val audioSink = DefaultAudioSink.Builder(context).build()

        factory.callBuildAudioRenderers(
            context,
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
            MediaCodecSelector.DEFAULT,
            /* enableDecoderFallback = */ false,
            audioSink,
            handler,
            listener,
            out
        )

        val ffmpegRenderer = out.filterIsInstance<FfmpegAudioRenderer>().firstOrNull()
        assertNotNull("FfmpegAudioRenderer should be loaded into renderers list", ffmpegRenderer)
    }

    @Test
    fun ffmpegAudioRenderer_supportsWmaFormat() {
        val renderer = FfmpegAudioRenderer()
        val format = Format.Builder()
            .setSampleMimeType(AudioFormat.WMA.mimeType)
            .setChannelCount(2)
            .setSampleRate(44100)
            .build()

        val support = renderer.supportsFormat(format)
        val formatSupport = RendererCapabilities.getFormatSupport(support)
        assertEquals(
            "WMA format should be supported by FfmpegAudioRenderer",
            C.FORMAT_HANDLED,
            formatSupport
        )
    }

    @Test
    fun ffmpegAudioRenderer_createsDecoderForWmaMimeType() {
        val renderer = FfmpegAudioRenderer()
        val format = Format.Builder()
            .setSampleMimeType(AudioFormat.WMA.mimeType)
            .setChannelCount(2)
            .setSampleRate(44100)
            .build()

        val decoder = renderer.createDecoder(format, null)
        assertNotNull("FfmpegAudioDecoder should be created for WMA", decoder)
        assertTrue(
            "Created decoder should be instance of FfmpegAudioDecoder",
            decoder is FfmpegAudioDecoder
        )
    }

    @Test
    fun nativeLibrary_arm64_meets16KbElfAlignmentRequirement() {
        val soFile = File("src/main/jniLibs/arm64-v8a/libffmpegJNI.so").let {
            if (it.exists()) it else File("app/src/main/jniLibs/arm64-v8a/libffmpegJNI.so")
        }
        assertTrue("libffmpegJNI.so for arm64-v8a must exist", soFile.exists())
        val bytes = soFile.readBytes()

        // Verify ELF64 magic
        assertEquals(0x7F.toByte(), bytes[0])
        assertEquals('E'.code.toByte(), bytes[1])
        assertEquals('L'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        assertEquals(2.toByte(), bytes[4]) // ELFCLASS64
        assertEquals(1.toByte(), bytes[5]) // ELFDATA2LSB (Little Endian)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ePhOff = buffer.getLong(32)
        val ePhentSize = buffer.getShort(54).toInt() and 0xFFFF
        val ePhNum = buffer.getShort(56).toInt() and 0xFFFF

        assertTrue("Program headers must exist", ePhNum > 0)
        var loadSegmentCount = 0

        for (i in 0 until ePhNum) {
            val offset = (ePhOff + i * ePhentSize).toInt()
            val pType = buffer.getInt(offset)
            if (pType == 1) { // PT_LOAD
                loadSegmentCount++
                val pOffset = buffer.getLong(offset + 8)
                val pVaddr = buffer.getLong(offset + 16)
                val pAlign = buffer.getLong(offset + 48)

                assertTrue(
                    "PT_LOAD segment #$loadSegmentCount must have p_align >= 16384 (16KB), but was $pAlign",
                    pAlign >= 16384L
                )
                assertEquals(
                    "PT_LOAD segment #$loadSegmentCount must have (p_vaddr % p_align) == (p_offset % p_align)",
                    (pVaddr % pAlign),
                    (pOffset % pAlign)
                )
            }
        }

        assertTrue("Must have at least one PT_LOAD segment", loadSegmentCount > 0)
    }

    @Test
    fun ffmpegAudioDecoder_decodesRealisticWmaSample_producesPcmOutput() {
        // Realistic WMA sample parameters from real Android 16 device logs:
        // sampleRate=11025Hz, channels=2, extraDataSize=10, packetSize=3200
        val extraData = byteArrayOf(
            0x00, 0x04, 0x00, 0x00, // nSamplesPerBlock: 1024
            0x00, 0x00,             // encodeOptions
            0x1F, 0x00, 0x00, 0x00  // superframe / channel mask
        )

        val format = Format.Builder()
            .setSampleMimeType(AudioFormat.WMA.mimeType)
            .setSampleRate(11025)
            .setChannelCount(2)
            .setInitializationData(listOf(extraData))
            .build()

        val decoder = FfmpegAudioDecoder(format)
        assertEquals("wmav2", decoder.codecName)
        assertEquals(11025, decoder.sampleRate)
        assertEquals(2, decoder.channelCount)

        val inputBuffer = decoder.createInputBuffer()
        inputBuffer.data = ByteBuffer.allocateDirect(3200)
        // Populate synthetic WMA packet payload
        val packetData = ByteArray(3200) { (it % 127).toByte() }
        inputBuffer.data!!.put(packetData)
        inputBuffer.data!!.flip()
        inputBuffer.timeUs = 0L

        val outputBuffer = decoder.createOutputBuffer()
        val exception = decoder.decode(inputBuffer, outputBuffer, reset = false)

        assertEquals("Decode should not produce exception", null, exception)
        val pcmData = outputBuffer.data
        assertNotNull("Output data should be populated with decoded PCM", pcmData)
        assertTrue("Output PCM size should be greater than 0", pcmData!!.remaining() > 0)

        // Decode subsequent frame with seek reset = true
        inputBuffer.clear()
        inputBuffer.data!!.put(packetData)
        inputBuffer.data!!.flip()
        inputBuffer.timeUs = 500_000L // 0.5s seek

        val seekOutputBuffer = decoder.createOutputBuffer()
        val seekException = decoder.decode(inputBuffer, seekOutputBuffer, reset = true)
        assertEquals("Seek reset decode should succeed", null, seekException)
        assertTrue(
            "Seek output PCM size should be greater than 0",
            seekOutputBuffer.data!!.remaining() > 0
        )

        decoder.release()
    }

    @Test
    fun nativeLibrary_arm64_exportsRequiredFfmpegSymbols() {
        val soFile = File("src/main/jniLibs/arm64-v8a/libffmpegJNI.so").let {
            if (it.exists()) it else File("app/src/main/jniLibs/arm64-v8a/libffmpegJNI.so")
        }
        assertTrue("libffmpegJNI.so for arm64-v8a must exist", soFile.exists())
        val content = soFile.readBytes().toString(Charsets.ISO_8859_1)

        val requiredSymbols = listOf(
            "avcodec_find_decoder_by_name",
            "Java_androidx_media3_decoder_ffmpeg_FfmpegLibrary_ffmpegHasDecoder",
            "Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_ffmpegInitialize",
            "Java_androidx_media3_decoder_ffmpeg_FfmpegAudioDecoder_ffmpegDecode",
            "wmav2"
        )

        for (sym in requiredSymbols) {
            assertTrue(
                "arm64-v8a binary must contain symbol '$sym'",
                content.contains(sym)
            )
        }
    }

    @Test
    fun nativeLibrary_armeabiV7a_isValidElfAndContainsSymbols() {
        val soFile = File("src/main/jniLibs/armeabi-v7a/libffmpegJNI.so").let {
            if (it.exists()) it else File("app/src/main/jniLibs/armeabi-v7a/libffmpegJNI.so")
        }
        assertTrue("libffmpegJNI.so for armeabi-v7a must exist", soFile.exists())
        val bytes = soFile.readBytes()

        assertEquals(0x7F.toByte(), bytes[0])
        assertEquals('E'.code.toByte(), bytes[1])
        assertEquals('L'.code.toByte(), bytes[2])
        assertEquals('F'.code.toByte(), bytes[3])
        assertEquals(1.toByte(), bytes[4]) // ELFCLASS32

        val content = bytes.toString(Charsets.ISO_8859_1)
        assertTrue(content.contains("avcodec_find_decoder_by_name"))
        assertTrue(content.contains("wmav2"))
    }

    @Test
    fun ffmpegAudioRenderer_supportsFormat_handles11025HzLowSampleRate() {
        val renderer = FfmpegAudioRenderer()
        val format = Format.Builder()
            .setSampleMimeType(AudioFormat.WMA.mimeType)
            .setChannelCount(2)
            .setSampleRate(11025)
            .build()

        val support = renderer.supportsFormat(format)
        val formatSupport = RendererCapabilities.getFormatSupport(support)
        assertEquals(
            "Low sample-rate (11025Hz) WMA format should be FORMAT_HANDLED",
            C.FORMAT_HANDLED,
            formatSupport
        )
    }
}
