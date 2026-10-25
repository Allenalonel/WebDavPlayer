package com.webdav.player

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.service.AudioFocusHandler
import com.webdav.player.data.player.CanonicalResamplingAudioProcessor
import com.webdav.player.data.player.Media3AudioPlayerEngine
import com.webdav.player.data.service.PlaybackSessionHost
import com.webdav.player.data.service.WebDavMediaService
import com.webdav.player.data.service.WebDavNotificationProvider
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackQueue
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.repository.ServerRepository
import com.webdav.player.domain.session.FakePlaybackSessionStore
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EndToEndSystemFocusAndRouteRegressionTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var hostJob: CompletableJob
    private lateinit var hostScope: CoroutineScope
    private lateinit var sessionJob: CompletableJob
    private lateinit var sessionScope: CoroutineScope

    private lateinit var context: Context
    private lateinit var fakeEngine: FakeAudioPlayerEngine
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioFocusHandler: AudioFocusHandler
    private lateinit var host: PlaybackSessionHost
    private lateinit var sessionStore: FakePlaybackSessionStore
    private lateinit var serverRepository: TestServerRepository
    private lateinit var appSession: MusicPlayerAppSessionImpl
    private lateinit var serviceController: ServiceController<WebDavMediaService>
    private lateinit var service: WebDavMediaService

    private var previousHost: PlaybackSessionHost? = null

    private val testServer =
        WebDavServer(
            id = 1L,
            name = "NAS Server",
            url = "http://192.168.1.100:8080/music",
            port = 8080,
            pathPrefix = "/music",
            isDefault = true,
        )

    private val sampleAudioFile =
        RemoteFile(
            name = "Track01.flac",
            path = "/music/Track01.flac",
            size = 45_000_000L,
            fileType = RemoteFileType.Audio(AudioFormat.FLAC),
        )

    private val sampleDirectory =
        RemoteDirectory(
            path = "/music/",
            name = "music",
            files = listOf(sampleAudioFile),
        )

    private class TestServerRepository(
        initialServers: List<WebDavServer> = emptyList(),
    ) : ServerRepository {
        private val serversFlow = MutableStateFlow(initialServers)
        private val activeServerFlow =
            MutableStateFlow(initialServers.firstOrNull { it.isDefault } ?: initialServers.firstOrNull())

        override fun getAllServers(): Flow<List<WebDavServer>> = serversFlow

        override fun getActiveServer(): Flow<WebDavServer?> = activeServerFlow

        override suspend fun getServerById(id: Long): WebDavServer? = serversFlow.value.find { it.id == id }

        override suspend fun saveServer(server: WebDavServer): Long = server.id

        override suspend fun deleteServer(id: Long) {
            serversFlow.value = serversFlow.value.filter { it.id != id }
        }

        override suspend fun setActiveServer(id: Long) {
            activeServerFlow.value = serversFlow.value.find { it.id == id }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        previousHost = PlaybackSessionHost.currentInstanceForTesting()

        hostJob = SupervisorJob()
        hostScope = CoroutineScope(testDispatcher + hostJob)
        sessionJob = SupervisorJob()
        sessionScope = CoroutineScope(testDispatcher + sessionJob)

        fakeEngine = FakeAudioPlayerEngine()
        exoPlayer = ExoPlayer.Builder(context).build()
        audioFocusHandler = AudioFocusHandler(context, appSession = null)

        mediaSession =
            MediaSession
                .Builder(context, exoPlayer)
                .setId("test_session_${System.currentTimeMillis()}")
                .build()

        host =
            PlaybackSessionHost(
                context = context,
                playerEngine = fakeEngine,
                mediaSession = mediaSession,
                audioFocusHandler = audioFocusHandler,
                notificationProvider = WebDavNotificationProvider(context),
                mediaSessionCallback = null,
                coroutineScope = hostScope,
            )
        PlaybackSessionHost.setInstanceForTesting(host)

        sessionStore = FakePlaybackSessionStore()
        serverRepository = TestServerRepository(listOf(testServer))

        appSession =
            MusicPlayerAppSessionImpl(
                playerEngine = host,
                serverRepository = serverRepository,
                sessionStore = sessionStore,
                coroutineScope = sessionScope,
                progressDispatcher = testDispatcher,
            )

        host.bindAppSession(appSession)

        serviceController = Robolectric.buildService(WebDavMediaService::class.java)
        service = serviceController.create().get()
    }

    @After
    fun tearDown() {
        if (::serviceController.isInitialized) {
            serviceController.destroy()
        }
        appSession.release()
        sessionJob.cancel()
        host.release()
        hostJob.cancel()
        mediaSession.release()
        exoPlayer.release()
        PlaybackSessionHost.setInstanceForTesting(previousHost)
        Dispatchers.resetMain()
    }

    private fun sendBecomingNoisyBroadcast() {
        val noisyIntent = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisyIntent)
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.runCurrent()
    }

    private fun triggerNotificationPause() {
        val pauseIntent = Intent(WebDavNotificationProvider.ACTION_PAUSE)
        service.onStartCommand(pauseIntent, 0, 1)
        ShadowLooper.idleMainLooper()
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun bufferingBecomingNoisy_unplugHeadphonesDuringBuffering_interceptsAndSafelyDegradesToPaused() =
        runTest(testDispatcher) {
            appSession.setActiveServer(testServer)
            advanceUntilIdle()

            // 1. User starts playing a track from remote directory
            appSession.playDirectoryTrack(sampleDirectory, sampleAudioFile)
            advanceUntilIdle()

            // 2. Underlying stream enters Buffering state with playWhenReady = true
            fakeEngine.customPlayWhenReady = true
            fakeEngine._playbackState.value = PlaybackState.Buffering
            host.handlePlaybackStateChanged(PlaybackState.Buffering)
            runCurrent()
            ShadowLooper.idleMainLooper()

            // Verify: Becoming-noisy receiver is actively registered during buffering
            assertTrue(
                "BecomingNoisy receiver must be registered during buffering with playWhenReady=true",
                host.isBecomingNoisyRegistered,
            )

            // 3. System broadcasts ACTION_AUDIO_BECOMING_NOISY (e.g. wired headphones unplugged or Bluetooth speaker dies)
            sendBecomingNoisyBroadcast()

            // 4. Safe degradation verification:
            // AppSession transitioned to Paused
            assertEquals(
                "Session state must degrade to Paused",
                PlaybackState.Paused,
                appSession.sessionState.value.playbackState,
            )
            // Underlying engine has playWhenReady set to false
            assertFalse("Engine playWhenReady must be false after safe pause", fakeEngine.playWhenReady)
            // Receiver is unregistered to avoid duplicate firings
            assertFalse(
                "BecomingNoisy receiver must be unregistered after degradation",
                host.isBecomingNoisyRegistered,
            )

            val playCountAfterPause = fakeEngine.playCount

            // 5. Buffer finish simulation: the underlying media buffer finishes loading
            // and transitions to Ready
            fakeEngine.customPlayWhenReady = false
            fakeEngine._playbackState.value = PlaybackState.Paused
            host.handlePlaybackStateChanged(PlaybackState.Paused)
            advanceUntilIdle()

            // 6. Assert audio silence: playback remains strictly Paused without sound emission
            assertEquals(
                "Playback state must remain Paused after buffer completes",
                PlaybackState.Paused,
                appSession.sessionState.value.playbackState,
            )
            assertEquals(
                "Engine play() must NOT be invoked upon buffer readiness",
                playCountAfterPause,
                fakeEngine.playCount,
            )
        }

    @Test
    fun ghostResumptionGuard_incomingCall_userPausesInNotification_callEnds_blocksGhostResumption() =
        runTest(testDispatcher) {
            appSession.setActiveServer(testServer)
            advanceUntilIdle()

            // 1. Actively playing music
            appSession.playDirectoryTrack(sampleDirectory, sampleAudioFile)
            advanceUntilIdle()
            assertEquals(PlaybackState.Playing, appSession.sessionState.value.playbackState)
            val playCountInitial = fakeEngine.playCount

            // 2. Incoming phone call triggers AUDIOFOCUS_LOSS_TRANSIENT
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            runCurrent()
            ShadowLooper.idleMainLooper()

            // Verify: session transitions to Paused and transient resume is queued
            assertEquals(PlaybackState.Paused, appSession.sessionState.value.playbackState)
            assertTrue("Resume on focus gain must be set by default on transient loss", host.audioFocusHandler.resumeOnFocusGain)

            // 3. User actively pauses via Notification Bar Action while on the phone call
            triggerNotificationPause()

            // Verify: user intervention atomically resets the transient resume flag
            assertFalse(
                "Explicit pause must clear resumeOnFocusGain to guard against ghost resumption",
                host.audioFocusHandler.resumeOnFocusGain,
            )

            // 4. Phone call ends -> system issues AUDIOFOCUS_GAIN
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            runCurrent()
            ShadowLooper.idleMainLooper()

            // 5. Ghost resumption is strictly blocked: play() is NEVER called
            assertEquals(
                "Playback must strictly remain Paused, blocking ghost resumption",
                PlaybackState.Paused,
                appSession.sessionState.value.playbackState,
            )
            assertEquals(
                "Engine playCount must not increase on focus gain when user explicitly paused",
                playCountInitial,
                fakeEngine.playCount,
            )
            assertFalse(host.audioFocusHandler.resumeOnFocusGain)
        }

    @Test
    fun transientFocusLoss_withoutUserIntervention_automaticallyResumesPlayback() =
        runTest(testDispatcher) {
            appSession.setActiveServer(testServer)
            advanceUntilIdle()

            // 1. Actively playing
            appSession.playDirectoryTrack(sampleDirectory, sampleAudioFile)
            advanceUntilIdle()
            val playCountBefore = fakeEngine.playCount

            // 2. Short interruption (e.g. phone call or navigation prompt without ducking)
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            runCurrent()
            ShadowLooper.idleMainLooper()

            assertEquals(PlaybackState.Paused, appSession.sessionState.value.playbackState)

            // 3. Call ends without any user intervention
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            runCurrent()
            ShadowLooper.idleMainLooper()

            // 4. Playback smoothly resumes
            assertEquals(
                "Playback should automatically resume when no user intervention occurred",
                PlaybackState.Playing,
                appSession.sessionState.value.playbackState,
            )
            assertEquals(
                "Engine play() must be invoked to resume playback",
                playCountBefore + 1,
                fakeEngine.playCount,
            )
        }

    @Test
    fun permanentAudioFocusLoss_pausesSession_abandonsFocus_andIgnoresSubsequentFocusGain() =
        runTest(testDispatcher) {
            appSession.setActiveServer(testServer)
            advanceUntilIdle()

            // 1. Actively playing
            appSession.playDirectoryTrack(sampleDirectory, sampleAudioFile)
            advanceUntilIdle()
            assertEquals(PlaybackState.Playing, appSession.sessionState.value.playbackState)
            val playCountBefore = fakeEngine.playCount

            // 2. System audio focus is held
            val granted = host.audioFocusHandler.requestAudioFocus()
            assertTrue("System audio focus must be granted initially", granted)

            // 3. Permanent focus loss (e.g. third-party music or video app starts playback)
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            runCurrent()
            ShadowLooper.idleMainLooper()

            // 4. Assert full pause and state stability
            assertEquals(
                "Session state must become Paused on permanent focus loss",
                PlaybackState.Paused,
                appSession.sessionState.value.playbackState,
            )
            assertFalse(
                "resumeOnFocusGain must be false on permanent loss",
                host.audioFocusHandler.resumeOnFocusGain,
            )

            // 5. If system subsequently sends AUDIOFOCUS_GAIN, playback must NOT automatically resume
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            runCurrent()
            ShadowLooper.idleMainLooper()

            assertEquals(
                "Playback must remain Paused after permanent focus loss even if gain is received",
                PlaybackState.Paused,
                appSession.sessionState.value.playbackState,
            )
            assertEquals(
                "Engine playCount must not increase after permanent loss",
                playCountBefore,
                fakeEngine.playCount,
            )
        }

    @Test
    fun coldStartFocusRecovery_incomingCallEnds_reloadsAndResumesPlayback() =
        runTest(testDispatcher) {
            // User Story 6: Cold start session with track in queue, empty physical stream in player engine (Idle)
            val testTrack =
                AudioTrack(
                    id = "1:/music/Track01.flac",
                    serverId = testServer.id,
                    remotePath = "/music/Track01.flac",
                    title = "Track01.flac",
                    format = AudioFormat.FLAC,
                    size = 45_000_000L,
                )

            val savedSessionData =
                PlaybackSessionData(
                    activeServerId = testServer.id,
                    queueTracks = listOf(testTrack),
                    currentTrackIndex = 0,
                    positionMs = 15_000L,
                    cleanExit = false,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    currentDirectoryPath = "/music/",
                )
            sessionStore.saveSession(savedSessionData)

            // Recreate session simulating cold start restore
            val coldSessionJob = SupervisorJob()
            val coldSessionScope = CoroutineScope(testDispatcher + coldSessionJob)
            val coldAppSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = host,
                    serverRepository = serverRepository,
                    sessionStore = sessionStore,
                    coroutineScope = coldSessionScope,
                    progressDispatcher = testDispatcher,
                )

            val focusHandler = AudioFocusHandler(context, appSession = coldAppSession)
            try {
                coldAppSession.restoreSession()
                advanceUntilIdle()

                // Session restored in Paused state with cleanExit=false, underlying physical stream is empty (Idle)
                assertEquals(PlaybackState.Paused, coldAppSession.sessionState.value.playbackState)
                assertEquals(PlaybackState.Idle, fakeEngine.playbackState.value)
                assertEquals(0, fakeEngine.loadTrackCount)
                assertTrue(coldAppSession.sessionState.value.wasKilledBySystem)

                // User initiates play from cold start before phone call interrupts
                coldAppSession.play()
                advanceUntilIdle()

                // Phone call interrupts -> transient focus loss
                focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                runCurrent()
                ShadowLooper.idleMainLooper()
                assertTrue(
                    "Cold recovery session interrupted by call must remember transient focus loss",
                    coldAppSession.pausedByTransientAudioFocus,
                )

                // Call ends -> system grants AUDIOFOCUS_GAIN
                focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                advanceUntilIdle()
                ShadowLooper.idleMainLooper()

                // Playback resumes cleanly by reloading track into the empty physical stream
                assertEquals(
                    "Cold start recovery must seamlessly reload empty physical stream and resume playback",
                    PlaybackState.Playing,
                    coldAppSession.sessionState.value.playbackState,
                )
                assertEquals(1, fakeEngine.loadTrackCount)
                assertEquals(15_000L, fakeEngine.lastStartPositionMs)
            } finally {
                coldAppSession.release()
                coldSessionJob.cancel()
            }
        }

    @Test
    fun idleRestoredSession_incomingCallEnds_doesNotTriggerGhostResumption() =
        runTest(testDispatcher) {
            val testTrack =
                AudioTrack(
                    id = "1:/music/Track01.flac",
                    serverId = testServer.id,
                    remotePath = "/music/Track01.flac",
                    title = "Track01.flac",
                    format = AudioFormat.FLAC,
                    size = 45_000_000L,
                )

            val savedSessionData =
                PlaybackSessionData(
                    activeServerId = testServer.id,
                    queueTracks = listOf(testTrack),
                    currentTrackIndex = 0,
                    positionMs = 15_000L,
                    cleanExit = false,
                    playbackMode = PlaybackMode.LIST_LOOP,
                    currentDirectoryPath = "/music/",
                )
            sessionStore.saveSession(savedSessionData)

            val coldSessionJob = SupervisorJob()
            val coldSessionScope = CoroutineScope(testDispatcher + coldSessionJob)
            val coldAppSession =
                MusicPlayerAppSessionImpl(
                    playerEngine = host,
                    serverRepository = serverRepository,
                    sessionStore = sessionStore,
                    coroutineScope = coldSessionScope,
                    progressDispatcher = testDispatcher,
                )

            val focusHandler = AudioFocusHandler(context, appSession = coldAppSession)
            try {
                coldAppSession.restoreSession()
                advanceUntilIdle()

                // Session restored in Paused state, user does NOT tap play
                assertEquals(PlaybackState.Paused, coldAppSession.sessionState.value.playbackState)
                assertEquals(PlaybackState.Idle, fakeEngine.playbackState.value)
                assertEquals(0, fakeEngine.loadTrackCount)

                // Phone call interrupts idle restored session
                focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                runCurrent()
                ShadowLooper.idleMainLooper()

                // Must NOT mark as paused by transient focus loss because session was idle/paused
                assertFalse(
                    "Idle restored session must not record transient focus loss without active playback",
                    coldAppSession.pausedByTransientAudioFocus,
                )

                // Call ends -> AUDIOFOCUS_GAIN
                focusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
                advanceUntilIdle()
                ShadowLooper.idleMainLooper()

                // Ghost resumption strictly blocked: session remains Paused, no track loaded
                assertEquals(
                    "Session must strictly remain Paused after call ends without spontaneous playback",
                    PlaybackState.Paused,
                    coldAppSession.sessionState.value.playbackState,
                )
                assertEquals(0, fakeEngine.loadTrackCount)
                assertEquals(0, fakeEngine.playCount)
            } finally {
                coldAppSession.release()
                coldSessionJob.cancel()
            }
        }

    @Test
    fun dynamicAudioRouting_systemMediaOutputSwitcherTransfer_flushesActiveSinkViaHost() =
        runTest(testDispatcher) {
            var flushCount = 0
            val underlyingSink = DefaultAudioSink.Builder(context).build()
            val trackingSink =
                object : ForwardingAudioSink(underlyingSink) {
                    override fun flush() {
                        flushCount++
                        super.flush()
                    }
                }

            val media3Engine = Media3AudioPlayerEngine(context, customAudioSink = trackingSink)
            val realHost =
                PlaybackSessionHost(
                    context = context,
                    playerEngine = media3Engine,
                    mediaSession = media3Engine.mediaSession,
                    audioFocusHandler = AudioFocusHandler(context),
                    notificationProvider = WebDavNotificationProvider(context),
                    coroutineScope = hostScope,
                )
            try {
                assertEquals(0, flushCount)

                // 1. User switches media output in System Control Center / Volume Switcher
                realHost.onAudioRoutingChanged()
                ShadowLooper.idleMainLooper()

                // 2. Underlying AudioSink is flushed to eliminate stale PCM data and resync driver clock
                assertEquals(1, flushCount)
            } finally {
                realHost.release()
                media3Engine.release()
            }
        }

    @Test
    fun nonStandardSampleRate_resamplingAndRouteResync_preservesStability() {
        val processor = CanonicalResamplingAudioProcessor()

        // 1. Non-standard 22,050 Hz 16-bit PCM (e.g. vintage audiobook / old encoding)
        val nonStandardInput =
            AudioProcessor.AudioFormat(
                // sampleRate =
                22050,
                // channelCount =
                2,
                // encoding =
                C.ENCODING_PCM_16BIT,
            )

        val outputFormat = processor.configure(nonStandardInput)
        processor.flush()
        assertTrue("Processor must be active for 22050 Hz input", processor.isActive)
        assertEquals("Sample rate must be canonicalized to 44100 Hz", 44100, outputFormat.sampleRate)

        // 2. Process audio buffer
        val inputBuffer = ByteBuffer.allocateDirect(1024).order(ByteOrder.nativeOrder())
        while (inputBuffer.hasRemaining()) {
            inputBuffer.putShort(100)
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output
        assertNotNull(outputBuffer)

        // 3. System audio routing change triggers flush
        processor.flush()
        assertTrue("Processor remains active after routing change flush", processor.isActive)
        processor.reset()
        assertFalse("Processor becomes inactive on reset", processor.isActive)
    }

    @Test
    fun multiBoundaryAcousticJitter_racingInterruptionAndRoutingEvents_convergesSafely() =
        runTest(testDispatcher) {
            appSession.setActiveServer(testServer)
            advanceUntilIdle()

            // 1. Start playback and enter Buffering state
            appSession.playDirectoryTrack(sampleDirectory, sampleAudioFile)
            advanceUntilIdle()

            fakeEngine.customPlayWhenReady = true
            fakeEngine._playbackState.value = PlaybackState.Buffering
            host.handlePlaybackStateChanged(PlaybackState.Buffering)
            runCurrent()
            ShadowLooper.idleMainLooper()
            assertTrue(host.isBecomingNoisyRegistered)

            // 2. Incoming call hits during buffering
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            runCurrent()
            ShadowLooper.idleMainLooper()
            assertEquals(PlaybackState.Paused, appSession.sessionState.value.playbackState)

            // 3. During call, headphones unplugged (becoming noisy)
            sendBecomingNoisyBroadcast()

            // 4. User also taps notification pause button
            triggerNotificationPause()

            // 5. System audio route jitter occurs (e.g. bluetooth fallback to speaker)
            host.onAudioRoutingChanged()
            runCurrent()
            ShadowLooper.idleMainLooper()

            // 6. Call ends -> AUDIOFOCUS_GAIN
            host.audioFocusHandler.handleFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            runCurrent()
            ShadowLooper.idleMainLooper()

            // Assert absolute silence: no audio leaks, remains firmly Paused
            assertEquals(PlaybackState.Paused, appSession.sessionState.value.playbackState)
            assertFalse(host.audioFocusHandler.resumeOnFocusGain)

            // 7. Explicit user play starts playback cleanly
            appSession.play()
            runCurrent()
            ShadowLooper.idleMainLooper()
            assertEquals(PlaybackState.Playing, appSession.sessionState.value.playbackState)
        }
}
