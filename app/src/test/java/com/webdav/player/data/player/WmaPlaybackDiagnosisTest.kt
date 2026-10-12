package com.webdav.player.data.player

import com.webdav.player.data.metadata.AsfParser
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackState
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.player.FakeAudioPlayerEngine
import com.webdav.player.domain.session.MusicPlayerAppSessionImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class WmaPlaybackDiagnosisTest {
    /**
     * Builds a binary ASF Header matching Microsoft ASF Specification:
     * - File Properties Object:
     *   - playDuration: total timeline including preroll
     *   - sendDuration: transmission duration
     *   - preroll: preroll buffering in ms (standard is typically 3100ms)
     * The actual playable media audio duration must be: playDuration - preroll.
     */
    private fun buildAsfHeaderWithPreroll(
        actualAudioDurationMs: Long,
        prerollMs: Long,
    ): ByteArray {
        val totalPlayDuration100ns = (actualAudioDurationMs + prerollMs) * 10000L
        val filePropsSize = 104
        val totalHeaderSize = 30L + filePropsSize

        val buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)

        // 1. ASF Header Object (30 bytes)
        buffer.put(AsfExtractor.ASF_HEADER_GUID)
        buffer.putLong(totalHeaderSize)
        buffer.putInt(1) // num sub-objects
        buffer.put(1) // reserved 1
        buffer.put(2) // reserved 2

        // 2. File Properties Object (104 bytes)
        buffer.put(AsfExtractor.FILE_PROPERTIES_GUID)
        buffer.putLong(filePropsSize.toLong())
        buffer.put(ByteArray(16)) // file id
        buffer.putLong(100_000L) // file size
        buffer.putLong(0L) // creation date
        buffer.putLong(100L) // data packets count
        buffer.putLong(totalPlayDuration100ns) // play duration in 100ns units
        buffer.putLong(totalPlayDuration100ns) // send duration in 100ns units
        buffer.putLong(prerollMs) // preroll in ms (uint64)
        buffer.putInt(2) // flags
        buffer.putInt(1024) // min packet size
        buffer.putInt(1024) // max packet size
        buffer.putInt(32000) // max bitrate

        val headerData = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(headerData)
        return headerData
    }

    @Test
    fun asfParser_shouldSubtractPrerollFromPlayDuration_toGetAccuratePlayableDuration() {
        val expectedActualDurationMs = 120_000L // 2 minutes
        val standardPrerollMs = 3_100L // Standard ASF preroll

        val asfBytes = buildAsfHeaderWithPreroll(expectedActualDurationMs, standardPrerollMs)
        val metadata = AsfParser.parse(asfBytes)

        requireNotNull(metadata)

        // BUG REPRODUCTION:
        // Currently AsfParser ignores prerollMs, so metadata.durationMs is 123,100L instead of 120,000L!
        assertEquals(
            "WMA duration must subtract preroll (${standardPrerollMs}ms) to reflect actual audio duration",
            expectedActualDurationMs,
            metadata.durationMs,
        )
    }

    @Test
    fun playbackEnded_whenWmaDurationIncludesPreroll_falselyTriggersDurationGuardAndFailsToAdvance() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val testScope = TestScope(testDispatcher)

            val fakeEngine = FakeAudioPlayerEngine()
            val server = WebDavServer(id = 1L, name = "Test", url = "http://localhost")

            val session =
                MusicPlayerAppSessionImpl(
                    playerEngine = fakeEngine,
                    coroutineScope = testScope,
                    progressDispatcher = testDispatcher,
                    periodicDispatcher = testDispatcher,
                )

            session.setActiveServer(server)
            val file1 = RemoteFile(name = "01.wma", path = "/Music/01.wma", size = 1_000_000L)
            val file2 = RemoteFile(name = "02.wma", path = "/Music/02.wma", size = 1_000_000L)
            val dir = RemoteDirectory(path = "/Music", name = "Music", files = listOf(file1, file2))

            session.playDirectoryTrack(dir, file1)
            advanceUntilIdle()

            // Track 1 metadata duration was parsed as 123,100ms because 3,100ms preroll was not subtracted.
            // During playback, fakeEngine._durationMs or currentTrack.durationMs is 123,100ms.
            fakeEngine._durationMs.value = 123_100L

            // Track 1 audio stream plays until real audio EOF: exactly 120,000ms.
            fakeEngine._currentPositionMs.value = 120_000L

            // Natural EOF reached
            fakeEngine._playbackState.value = PlaybackState.Ended
            advanceUntilIdle()

            // BUG REPRODUCTION:
            // Expected behavior: queue advances to file 2 (index 1).
            // Actual buggy behavior: 120,000ms < (123,100ms - 3,000ms tolerance = 120,100ms),
            // Duration Guard treats it as Premature EOF, sets state to Buffering, seeks to 120,000ms,
            // and refuses to advance queue!
            assertEquals(
                "Playback session must advance to next track when WMA track reaches natural audio EOF",
                1,
                session.sessionState.value.queue.currentIndex,
            )
            assertNotEquals(
                "Playback session must not be stuck in Buffering at EOF due to bloated duration",
                PlaybackState.Buffering,
                session.sessionState.value.playbackState,
            )
        }
}
