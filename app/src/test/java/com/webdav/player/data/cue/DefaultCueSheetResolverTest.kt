package com.webdav.player.data.cue

import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCueSheetResolverTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeWebDavClient: FakeWebDavClient
    private lateinit var resolver: DefaultCueSheetResolver

    private val sampleServer =
        WebDavServer(
            id = 1L,
            name = "Test NAS",
            url = "http://nas.local:5005",
            port = 5005,
        )

    private class FakeWebDavClient : WebDavClient {
        val textResponses = mutableMapOf<String, String>()
        val fetchTextCalls = mutableListOf<String>()
        var fetchTextError: Exception? = null

        override suspend fun testConnection(server: WebDavServer): ConnectionResult = ConnectionResult.Success

        override suspend fun listDirectory(
            server: WebDavServer,
            path: String,
        ): ListDirectoryResult = ListDirectoryResult.Failure("Not implemented")

        override suspend fun fetchRange(
            server: WebDavServer,
            remotePath: String,
            startByte: Long,
            endByte: Long,
        ): ByteArray? = null

        override suspend fun fetchText(
            server: WebDavServer,
            remotePath: String,
        ): String? {
            fetchTextCalls.add(remotePath)
            fetchTextError?.let { throw it }
            return textResponses[remotePath]
        }
    }

    @Before
    fun setUp() {
        fakeWebDavClient = FakeWebDavClient()
        resolver =
            DefaultCueSheetResolver(
                webDavClient = fakeWebDavClient,
                ioDispatcher = testDispatcher,
                cacheCapacity = 50,
            )
    }

    @Test
    fun resolveDirectoryCueAlbums_whenNoCueFiles_returnsEmptyList() =
        runTest(testDispatcher) {
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files =
                        listOf(
                            RemoteFile(name = "song.mp3", path = "/Music/song.mp3", size = 1000L),
                        ),
                )

            val result = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertTrue(result.isEmpty())
            assertTrue(fakeWebDavClient.fetchTextCalls.isEmpty())
        }

    @Test
    fun resolveDirectoryCueAlbums_withValidCue_resolvesAlbumAndVirtualTracks() =
        runTest(testDispatcher) {
            val cueContent =
                """
                PERFORMER "Pink Floyd"
                TITLE "The Dark Side of the Moon"
                FILE "DarkSide.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Speak to Me"
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    TITLE "Breathe"
                    INDEX 01 01:07:25
                """.trimIndent()

            val cueFile = RemoteFile(name = "DarkSide.cue", path = "/Music/PinkFloyd/DarkSide.cue", size = 500L)
            val audioFile = RemoteFile(name = "DarkSide.flac", path = "/Music/PinkFloyd/DarkSide.flac", size = 200_000_000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/PinkFloyd/",
                    name = "PinkFloyd",
                    files = listOf(audioFile, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            val album = albums.first()
            assertEquals(cueFile, album.cueFile)
            assertEquals(audioFile, album.audioFile)
            assertNull(album.errorMessage)
            assertFalse(album.isLoading)
            assertEquals(2, album.tracks.size)
            assertEquals("Speak to Me", album.tracks[0].title)
            assertEquals(0L, album.tracks[0].startTimeMs)
            assertEquals(67333L, album.tracks[0].endTimeMs)
            assertEquals("Breathe", album.tracks[1].title)
            assertEquals(67333L, album.tracks[1].startTimeMs)
        }

    @Test
    fun resolveDirectoryCueAlbums_withParentDurations_populatesLastTrackEndTime() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "CDImage.wav" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    TITLE "Track 2"
                    INDEX 01 03:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Album/Album.cue", size = 500L)
            val audioFile = RemoteFile(name = "CDImage.flac", path = "/Music/Album/CDImage.flac", size = 200_000_000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/Album/",
                    name = "Album",
                    files = listOf(audioFile, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val parentDurations = mapOf(audioFile.path to 360_000L)
            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir, parentDurations)

            assertEquals(1, albums.size)
            val tracks = albums.first().tracks
            assertEquals(2, tracks.size)
            assertEquals(180_000L, tracks[0].endTimeMs)
            assertEquals(360_000L, tracks[1].endTimeMs)
        }

    @Test
    fun resolveDirectoryCueAlbums_whenAudioNotFound_returnsErrorAlbumWithNullAudio() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "NonExistent.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Missing/Album.cue", size = 500L)
            val unrelatedAudio = RemoteFile(name = "Unrelated.mp3", path = "/Music/Missing/Unrelated.mp3", size = 5000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/Missing/",
                    name = "Missing",
                    files = listOf(unrelatedAudio, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            val album = albums.first()
            assertNull(album.audioFile)
            assertEquals("未找到 CUE 指向的目标音频文件", album.errorMessage)
            assertTrue(album.tracks.isEmpty())
        }

    @Test
    fun resolveDirectoryCueAlbums_whenCueEmptyOrUnreadable_returnsErrorAlbum() =
        runTest(testDispatcher) {
            val cueFile = RemoteFile(name = "Empty.cue", path = "/Music/Empty.cue", size = 0L)
            val audioFile = RemoteFile(name = "Empty.flac", path = "/Music/Empty.flac", size = 1000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(audioFile, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = ""

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            val album = albums.first()
            assertNull(album.audioFile)
            assertEquals("无法读取 CUE 文件内容", album.errorMessage)
            assertTrue(album.tracks.isEmpty())
        }

    @Test
    fun resolveDirectoryCueAlbums_whenCueHasNoValidTracks_returnsErrorAlbumWithMatchedAudio() =
        runTest(testDispatcher) {
            val cueContent =
                """
                REM Invalid CUE with no tracks
                TITLE "Broken"
                FILE "Album.flac" WAVE
                """.trimIndent()

            val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Album.cue", size = 200L)
            val audioFile = RemoteFile(name = "Album.flac", path = "/Music/Album.flac", size = 1000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(audioFile, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            val album = albums.first()
            assertEquals(audioFile, album.audioFile)
            assertEquals("CUE 文件未包含有效分轨", album.errorMessage)
            assertTrue(album.tracks.isEmpty())
        }

    @Test
    fun resolveDirectoryCueAlbums_cachesCueText_avoidsRedundantNetworkRequests() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "Album.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Album.cue", size = 200L)
            val audioFile = RemoteFile(name = "Album.flac", path = "/Music/Album.flac", size = 1000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(audioFile, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            // First call: fetches from network
            val albums1 = resolver.resolveDirectoryCueAlbums(sampleServer, dir)
            assertEquals(1, albums1.size)
            assertEquals(1, fakeWebDavClient.fetchTextCalls.size)

            // Second call: served from LRU cache
            val albums2 = resolver.resolveDirectoryCueAlbums(sampleServer, dir)
            assertEquals(1, albums2.size)
            assertEquals(1, fakeWebDavClient.fetchTextCalls.size)
        }

    @Test
    fun resolveDirectoryCueAlbums_evictsOldestEntry_whenCapacityExceeded() =
        runTest(testDispatcher) {
            val smallResolver =
                DefaultCueSheetResolver(
                    webDavClient = fakeWebDavClient,
                    ioDispatcher = testDispatcher,
                    cacheCapacity = 2,
                )

            val cue1 =
                """
                FILE "1.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()
            val cue2 =
                """
                FILE "2.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 2"
                    INDEX 01 00:00:00
                """.trimIndent()
            val cue3 =
                """
                FILE "3.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 3"
                    INDEX 01 00:00:00
                """.trimIndent()

            val file1 = RemoteFile(name = "1.cue", path = "/Music/1.cue", size = 100L)
            val audio1 = RemoteFile(name = "1.flac", path = "/Music/1.flac", size = 1000L)
            val dir1 = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(audio1, file1))

            val file2 = RemoteFile(name = "2.cue", path = "/Music/2.cue", size = 100L)
            val audio2 = RemoteFile(name = "2.flac", path = "/Music/2.flac", size = 1000L)
            val dir2 = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(audio2, file2))

            val file3 = RemoteFile(name = "3.cue", path = "/Music/3.cue", size = 100L)
            val audio3 = RemoteFile(name = "3.flac", path = "/Music/3.flac", size = 1000L)
            val dir3 = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(audio3, file3))

            fakeWebDavClient.textResponses[file1.path] = cue1
            fakeWebDavClient.textResponses[file2.path] = cue2
            fakeWebDavClient.textResponses[file3.path] = cue3

            // Load 1 and 2 (capacity is 2)
            smallResolver.resolveDirectoryCueAlbums(sampleServer, dir1)
            smallResolver.resolveDirectoryCueAlbums(sampleServer, dir2)
            assertEquals(2, fakeWebDavClient.fetchTextCalls.size)

            // Load 3 -> item 1 should be evicted from LRU cache
            smallResolver.resolveDirectoryCueAlbums(sampleServer, dir3)
            assertEquals(3, fakeWebDavClient.fetchTextCalls.size)

            // Re-requesting 2 should be a cache hit (was item 2)
            smallResolver.resolveDirectoryCueAlbums(sampleServer, dir2)
            assertEquals(3, fakeWebDavClient.fetchTextCalls.size)

            // Re-requesting 1 should be a cache miss (was evicted) -> triggers 4th network fetch
            smallResolver.resolveDirectoryCueAlbums(sampleServer, dir1)
            assertEquals(4, fakeWebDavClient.fetchTextCalls.size)
        }

    @Test
    fun resolveVirtualTracks_withValidCue_returnsTracksWithParentPath() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "Album.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    PERFORMER "Artist 1"
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    TITLE "Track 2"
                    PERFORMER "Artist 2"
                    INDEX 01 02:30:00
                """.trimIndent()

            fakeWebDavClient.textResponses["/Music/Album.cue"] = cueContent

            val tracks =
                resolver.resolveVirtualTracks(
                    server = sampleServer,
                    cuePath = "/Music/Album.cue",
                    parentAudioPath = "/Music/Album.flac",
                    totalDurationMs = 300_000L,
                )

            assertEquals(2, tracks.size)
            assertEquals("Track 1", tracks[0].title)
            assertEquals("Artist 1", tracks[0].performer)
            assertEquals(0L, tracks[0].startTimeMs)
            assertEquals(150_000L, tracks[0].endTimeMs)
            assertEquals("/Music/Album.flac", tracks[0].parentAudioPath)

            assertEquals("Track 2", tracks[1].title)
            assertEquals("Artist 2", tracks[1].performer)
            assertEquals(150_000L, tracks[1].startTimeMs)
            assertEquals(300_000L, tracks[1].endTimeMs)
            assertEquals("/Music/Album.flac", tracks[1].parentAudioPath)
        }

    @Test
    fun resolveVirtualTracks_withMissingFileTag_stillParsesWhenParentPathProvided() =
        runTest(testDispatcher) {
            val cueContent =
                """
                TRACK 01 AUDIO
                  TITLE "No File Tag Track"
                  INDEX 01 00:00:00
                """.trimIndent()

            fakeWebDavClient.textResponses["/Music/NoFile.cue"] = cueContent

            val tracks =
                resolver.resolveVirtualTracks(
                    server = sampleServer,
                    cuePath = "/Music/NoFile.cue",
                    parentAudioPath = "/Music/Audio.flac",
                )

            assertEquals(1, tracks.size)
            assertEquals("No File Tag Track", tracks[0].title)
            assertEquals("/Music/Audio.flac", tracks[0].parentAudioPath)
        }

    @Test
    fun resolveVirtualTracks_whenNetworkFails_returnsEmptyList() =
        runTest(testDispatcher) {
            val tracks =
                resolver.resolveVirtualTracks(
                    server = sampleServer,
                    cuePath = "/Music/NonExistent.cue",
                    parentAudioPath = "/Music/Audio.flac",
                )

            assertTrue(tracks.isEmpty())
        }

    @Test
    fun clearCache_clearsLruCacheEntries_subsequentCallRefetchesFromNetwork() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "Album.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()

            fakeWebDavClient.textResponses["/Music/Album.cue"] = cueContent

            resolver.resolveVirtualTracks(sampleServer, "/Music/Album.cue", "/Music/Album.flac")
            assertEquals(1, fakeWebDavClient.fetchTextCalls.size)

            resolver.clearCache()

            resolver.resolveVirtualTracks(sampleServer, "/Music/Album.cue", "/Music/Album.flac")
            assertEquals(2, fakeWebDavClient.fetchTextCalls.size)
        }

    @Test
    fun resolveDirectoryCueAlbums_singlePairFallback_matchesWhenReferenceEmpty() =
        runTest(testDispatcher) {
            val cueContent =
                """
                REM Cue without FILE directive
                TRACK 01 AUDIO
                  TITLE "Track 1"
                  INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Album.cue", size = 200L)
            val audioFile = RemoteFile(name = "LoneTrack.flac", path = "/Music/LoneTrack.flac", size = 1000L)
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(audioFile, cueFile),
                )

            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            val album = albums.first()
            assertEquals(audioFile, album.audioFile)
            assertEquals(1, album.tracks.size)
        }

    @Test
    fun resolveDirectoryCueAlbums_handlesNetworkExceptionGracefully() =
        runTest(testDispatcher) {
            val cueFile = RemoteFile(name = "Error.cue", path = "/Music/Error.cue", size = 200L)
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile),
                )

            fakeWebDavClient.fetchTextError = RuntimeException("Connection timed out")

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            val album = albums.first()
            assertEquals("Connection timed out", album.errorMessage)
            assertFalse(album.isLoading)
            assertTrue(album.tracks.isEmpty())
        }

    @Test
    fun resolveDirectoryCueAlbums_exactBaseNameMatch_associatesCorrectAudioFile() =
        runTest(testDispatcher) {
            val cueContent =
                """
                TRACK 01 AUDIO
                  TITLE "Hotel California"
                  INDEX 01 00:00:00
                """.trimIndent()

            val cueFile =
                RemoteFile(name = "Hotel_California.cue", path = "/Music/Hotel_California.cue", size = 200L, fileType = RemoteFileType.Cue)
            val matchingAudio =
                RemoteFile(
                    name = "Hotel_California.flac",
                    path = "/Music/Hotel_California.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val otherAudio =
                RemoteFile(
                    name = "Bonus_Track.mp3",
                    path = "/Music/Bonus_Track.mp3",
                    size = 5_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, matchingAudio, otherAudio),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("Hotel_California.flac", albums[0].audioFile?.name)
            assertNull(albums[0].errorMessage)
        }

    @Test
    fun resolveDirectoryCueAlbums_exactBaseNameMatch_isCaseInsensitive() =
        runTest(testDispatcher) {
            val cueContent =
                """
                TRACK 01 AUDIO
                  TITLE "Track 1"
                  INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "ALBUM.CUE", path = "/Music/ALBUM.CUE", size = 200L, fileType = RemoteFileType.Cue)
            val audioFile =
                RemoteFile(
                    name = "album.flac",
                    path = "/Music/album.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, audioFile),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("album.flac", albums[0].audioFile?.name)
        }

    @Test
    fun resolveDirectoryCueAlbums_referencedFileExactMatch_matchesEvenWhenCueNameDiffers() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "The_Wall_CD1.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "In the Flesh?"
                    INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "disc1.cue", path = "/Music/disc1.cue", size = 200L, fileType = RemoteFileType.Cue)
            val cd1Audio =
                RemoteFile(
                    name = "The_Wall_CD1.flac",
                    path = "/Music/The_Wall_CD1.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val cd2Audio =
                RemoteFile(
                    name = "The_Wall_CD2.flac",
                    path = "/Music/The_Wall_CD2.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, cd1Audio, cd2Audio),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("The_Wall_CD1.flac", albums[0].audioFile?.name)
        }

    @Test
    fun resolveDirectoryCueAlbums_referencedFile_handlesDifferentExtensionWhenTranscoded() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "CDImage.wav" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "CDImage.cue", path = "/Music/CDImage.cue", size = 200L, fileType = RemoteFileType.Cue)
            val audioFile =
                RemoteFile(
                    name = "CDImage.flac",
                    path = "/Music/CDImage.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, audioFile),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("CDImage.flac", albums[0].audioFile?.name)
        }

    @Test
    fun resolveDirectoryCueAlbums_referencedFile_handlesWindowsBackslashPath() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "C:\Music\Rips\SideA.wav" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Album.cue", size = 200L, fileType = RemoteFileType.Cue)
            val audioFile =
                RemoteFile(
                    name = "SideA.flac",
                    path = "/Music/SideA.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, audioFile),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("SideA.flac", albums[0].audioFile?.name)
        }

    @Test
    fun resolveDirectoryCueAlbums_prefixMatch_associatesWhenNoReferencedFile() =
        runTest(testDispatcher) {
            val cueContent =
                """
                REM Cue without FILE tag
                TRACK 01 AUDIO
                  TITLE "Symphony 5"
                  INDEX 01 00:00:00
                """.trimIndent()

            val cueFile =
                RemoteFile(
                    name = "Beethoven_Sym5_Part1.cue",
                    path = "/Music/Beethoven_Sym5_Part1.cue",
                    size = 200L,
                    fileType = RemoteFileType.Cue,
                )
            val audioFile =
                RemoteFile(
                    name = "Beethoven_Sym5.flac",
                    path = "/Music/Beethoven_Sym5.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, audioFile),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("Beethoven_Sym5.flac", albums[0].audioFile?.name)
        }

    @Test
    fun resolveDirectoryCueAlbums_apeAudioFormat_isCorrectlyMatched() =
        runTest(testDispatcher) {
            val cueContent =
                """
                TRACK 01 AUDIO
                  TITLE "Ode to Joy"
                  INDEX 01 00:00:00
                """.trimIndent()

            val cueFile =
                RemoteFile(name = "Beethoven_Sym9.cue", path = "/Music/Beethoven_Sym9.cue", size = 200L, fileType = RemoteFileType.Cue)
            val audioFile =
                RemoteFile(
                    name = "Beethoven_Sym9.ape",
                    path = "/Music/Beethoven_Sym9.ape",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.APE),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, audioFile),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("Beethoven_Sym9.ape", albums[0].audioFile?.name)
            assertEquals(AudioFormat.APE, (albums[0].audioFile?.fileType as? RemoteFileType.Audio)?.format)
        }

    @Test
    fun resolveDirectoryCueAlbums_whenNoMatchingAudio_returnsErrorItem() =
        runTest(testDispatcher) {
            val cueContent =
                """
                FILE "NonExistent.wav" WAVE
                  TRACK 01 AUDIO
                    TITLE "Track 1"
                    INDEX 01 00:00:00
                """.trimIndent()

            val cueFile = RemoteFile(name = "Orphan.cue", path = "/Music/Orphan.cue", size = 200L, fileType = RemoteFileType.Cue)
            val unrelatedAudio =
                RemoteFile(
                    name = "Unrelated.flac",
                    path = "/Music/Unrelated.flac",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, unrelatedAudio),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertNull(albums[0].audioFile)
            assertEquals("未找到 CUE 指向的目标音频文件", albums[0].errorMessage)
        }

    @Test
    fun resolveInitialCueAlbums_matchesAudioByBaseName() {
        val cueFile = RemoteFile(name = "Album.cue", path = "/Music/Album.cue", size = 200L, fileType = RemoteFileType.Cue)
        val audioFile =
            RemoteFile(
                name = "Album.flac",
                path = "/Music/Album.flac",
                size = 50_000_000L,
                fileType = RemoteFileType.Audio(AudioFormat.FLAC),
            )

        val dir =
            RemoteDirectory(
                path = "/Music/",
                name = "Music",
                files = listOf(cueFile, audioFile),
            )

        val albums = resolver.resolveInitialCueAlbums(dir)

        assertEquals(1, albums.size)
        assertEquals("Album.flac", albums[0].audioFile?.name)
        assertTrue(albums[0].isLoading)
    }

    @Test
    fun resolveInitialCueAlbums_singlePairFallback_associatesWhenOnlyOneCueAndAudio() {
        val cueFile = RemoteFile(name = "playlist.cue", path = "/Music/playlist.cue", size = 200L, fileType = RemoteFileType.Cue)
        val audioFile =
            RemoteFile(
                name = "entire_concert.wav",
                path = "/Music/entire_concert.wav",
                size = 50_000_000L,
                fileType = RemoteFileType.Audio(AudioFormat.WAV),
            )

        val dir =
            RemoteDirectory(
                path = "/Music/",
                name = "Music",
                files = listOf(cueFile, audioFile),
            )

        val albums = resolver.resolveInitialCueAlbums(dir)

        assertEquals(1, albums.size)
        assertEquals("entire_concert.wav", albums[0].audioFile?.name)
    }

    @Test
    fun resolveDirectoryCueAlbums_singlePairFallback_associatesWhenDifferentNamesAndNoReference() =
        runTest(testDispatcher) {
            val cueContent =
                """
                TRACK 01 AUDIO
                  TITLE "Concert Part 1"
                  INDEX 01 00:00:00
                """.trimIndent()

            val cueFile =
                RemoteFile(
                    name = "playlist.cue",
                    path = "/Music/playlist.cue",
                    size = 200L,
                    fileType = RemoteFileType.Cue,
                )
            val audioFile =
                RemoteFile(
                    name = "entire_concert.wav",
                    path = "/Music/entire_concert.wav",
                    size = 50_000_000L,
                    fileType = RemoteFileType.Audio(AudioFormat.WAV),
                )

            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, audioFile),
                )
            fakeWebDavClient.textResponses[cueFile.path] = cueContent

            val albums = resolver.resolveDirectoryCueAlbums(sampleServer, dir)

            assertEquals(1, albums.size)
            assertEquals("entire_concert.wav", albums[0].audioFile?.name)
            assertEquals(1, albums[0].tracks.size)
        }
}
