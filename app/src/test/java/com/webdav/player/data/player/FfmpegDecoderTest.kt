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
}
