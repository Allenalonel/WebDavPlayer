package com.webdav.player.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.webdav.player.data.cue.DefaultCueSheetResolver
import com.webdav.player.data.local.AppDatabase
import com.webdav.player.data.local.DirectoryCacheDao
import com.webdav.player.data.local.DirectoryCacheEntity
import com.webdav.player.data.local.RemoteDirectoryJsonSerializer
import com.webdav.player.data.local.TrackMetadataDao
import com.webdav.player.data.local.TrackMetadataEntity
import com.webdav.player.data.remote.ConnectionResult
import com.webdav.player.data.remote.WebDavClient
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.ListDirectoryResult
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.domain.model.RemoteFile
import com.webdav.player.domain.model.RemoteFileType
import com.webdav.player.domain.model.WebDavServer
import com.webdav.player.domain.repository.LibrarySearchRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibrarySearchRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var directoryCacheDao: DirectoryCacheDao
    private lateinit var trackMetadataDao: TrackMetadataDao
    private lateinit var repository: LibrarySearchRepository

    private val fakeWebDavClient =
        object : WebDavClient {
            val textResponses = mutableMapOf<String, String>()

            override suspend fun testConnection(server: WebDavServer): ConnectionResult =
                ConnectionResult.Success

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
            ): String? = textResponses["${server.id}:$remotePath"] ?: textResponses[remotePath]
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        directoryCacheDao = database.directoryCacheDao()
        trackMetadataDao = database.trackMetadataDao()
        fakeWebDavClient.textResponses.clear()

        val cueSheetResolver = DefaultCueSheetResolver(fakeWebDavClient)

        repository =
            LibrarySearchRepositoryImpl(
                directoryCacheDao = directoryCacheDao,
                trackMetadataDao = trackMetadataDao,
                cueSheetResolver = cueSheetResolver,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun search_withEmptyOrBlankQuery_returnsEmptyResult() =
        runTest {
            val dir = RemoteDirectory(path = "/Music/Rock/", name = "Rock")
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/Rock/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )

            val resultEmpty = repository.search("").first()
            assertTrue(resultEmpty.isEmpty)
            assertTrue(resultEmpty.matchedDirectories.isEmpty())
            assertTrue(resultEmpty.matchedTracks.isEmpty())

            val resultSpaces = repository.search("   ").first()
            assertTrue(resultSpaces.isEmpty)

            val resultTabs = repository.search("\t\n").first()
            assertTrue(resultTabs.isEmpty)
        }

    @Test
    fun search_withDirectoryNameMatch_caseInsensitive() =
        runTest {
            val rockDir = RemoteDirectory(path = "/Music/Rock/", name = "Rock")
            val jazzDir = RemoteDirectory(path = "/Music/Jazz/", name = "Jazz")

            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/Rock/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(rockDir),
                    lastUpdatedMs = 1000L,
                ),
            )
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/Jazz/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(jazzDir),
                    lastUpdatedMs = 1000L,
                ),
            )

            // Lowercase query
            val resultLower = repository.search("rock").first()
            assertEquals(1, resultLower.matchedDirectories.size)
            assertEquals("/Music/Rock/", resultLower.matchedDirectories.first().path)

            // Uppercase query
            val resultUpper = repository.search("ROCK").first()
            assertEquals(1, resultUpper.matchedDirectories.size)
            assertEquals("Rock", resultUpper.matchedDirectories.first().name)

            // Mixed case query
            val resultMixed = repository.search("rOcK").first()
            assertEquals(1, resultMixed.matchedDirectories.size)
        }

    @Test
    fun search_withDirectoryPathMatch_matchesSubpathSegments() =
        runTest {
            val dir = RemoteDirectory(path = "/Classical/Beethoven/Symphony/", name = "Symphony")
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Classical/Beethoven/Symphony/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )

            val result = repository.search("beethoven").first()
            assertEquals(1, result.matchedDirectories.size)
            assertEquals("/Classical/Beethoven/Symphony/", result.matchedDirectories.first().path)
        }

    @Test
    fun search_withSubDirectoriesInCachedDirectory_matchesSubDirectories() =
        runTest {
            val rootDir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    subDirectories =
                        listOf(
                            RemoteDirectory(path = "/Music/Metal/", name = "Metal"),
                            RemoteDirectory(path = "/Music/Pop/", name = "Pop"),
                        ),
                )
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(rootDir),
                    lastUpdatedMs = 1000L,
                ),
            )

            val result = repository.search("metal").first()
            assertEquals(1, result.matchedDirectories.size)
            assertEquals("/Music/Metal/", result.matchedDirectories.first().path)
            assertEquals("Metal", result.matchedDirectories.first().name)
        }

    @Test
    fun search_withTrackMetadataMatch_titleArtistAlbum() =
        runTest {
            val file1 =
                RemoteFile(
                    name = "bohemian.mp3",
                    path = "/Music/bohemian.mp3",
                    size = 5000000L,
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val file2 =
                RemoteFile(
                    name = "hotel_california.flac",
                    path = "/Music/hotel_california.flac",
                    size = 25000000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(file1, file2))
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )

            trackMetadataDao.insertOrUpdateAll(
                listOf(
                    TrackMetadataEntity(
                        serverId = 1L,
                        remotePath = "/Music/bohemian.mp3",
                        title = "Bohemian Rhapsody",
                        artist = "Queen",
                        album = "A Night at the Opera",
                        trackNumber = 4,
                        durationMs = 354000L,
                        coverThumbnailPath = null,
                    ),
                    TrackMetadataEntity(
                        serverId = 1L,
                        remotePath = "/Music/hotel_california.flac",
                        title = "Hotel California",
                        artist = "Eagles",
                        album = "Hotel California",
                        trackNumber = 1,
                        durationMs = 390000L,
                        coverThumbnailPath = null,
                    ),
                ),
            )

            // Match by artist
            val artistResult = repository.search("queen").first()
            assertEquals(1, artistResult.matchedTracks.size)
            assertEquals("Bohemian Rhapsody", artistResult.matchedTracks.first().title)
            assertEquals("Queen", artistResult.matchedTracks.first().artist)

            // Match by album
            val albumResult = repository.search("opera").first()
            assertEquals(1, albumResult.matchedTracks.size)
            assertEquals("Bohemian Rhapsody", albumResult.matchedTracks.first().title)

            // Match by title
            val titleResult = repository.search("california").first()
            assertEquals(1, titleResult.matchedTracks.size)
            assertEquals("Hotel California", titleResult.matchedTracks.first().title)
        }

    @Test
    fun search_withTrackFileNameMatch_whenMetadataNotPresent() =
        runTest {
            val file =
                RemoteFile(
                    name = "stairway_to_heaven.mp3",
                    path = "/Music/stairway_to_heaven.mp3",
                    size = 8000000L,
                    fileType = RemoteFileType.Audio(AudioFormat.MP3),
                )
            val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(file))
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )

            val result = repository.search("stairway").first()
            assertEquals(1, result.matchedTracks.size)
            val track = result.matchedTracks.first()
            assertEquals("stairway_to_heaven.mp3", track.title)
            assertEquals(1L, track.serverId)
            assertEquals("/Music/stairway_to_heaven.mp3", track.remotePath)
        }

    @Test
    fun search_withCueVirtualTracks_matchesVirtualTrackAndPerformer() =
        runTest {
            val cueFile =
                RemoteFile(
                    name = "Symphony.cue",
                    path = "/Music/Symphony.cue",
                    size = 1024L,
                    fileType = RemoteFileType.Cue,
                )
            val flacFile =
                RemoteFile(
                    name = "Symphony.flac",
                    path = "/Music/Symphony.flac",
                    size = 100000000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val dir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    files = listOf(cueFile, flacFile),
                )
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )

            val cueContent =
                """
                PERFORMER "Herbert von Karajan"
                TITLE "Beethoven Symphony No. 9"
                FILE "Symphony.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Allegro ma non troppo"
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    TITLE "Molto vivace"
                    PERFORMER "Berliner Philharmoniker"
                    INDEX 01 15:30:00
                """.trimIndent()
            fakeWebDavClient.textResponses["/Music/Symphony.cue"] = cueContent

            // Search by virtual track title
            val vivaceResult = repository.search("vivace").first()
            assertEquals(1, vivaceResult.matchedTracks.size)
            val vtTrack = vivaceResult.matchedTracks.first()
            assertEquals("Molto vivace", vtTrack.title)
            assertEquals("Berliner Philharmoniker", vtTrack.artist)
            assertTrue(vtTrack.isVirtualTrack)
            assertTrue(vtTrack.id.endsWith("#cue_2"))

            // Search by performer from CUE
            val karajanResult = repository.search("karajan").first()
            // Karajan is album performer, so Track 1 inherits it
            assertTrue(karajanResult.matchedTracks.any { it.title == "Allegro ma non troppo" })
        }

    @Test
    fun search_withSpecialCharacters_doesNotCrashAndMatchesLiterally() =
        runTest {
            val file1 =
                RemoteFile(
                    name = "[FLAC] Best of 2024 (Live) [100%].flac",
                    path = "/Music/[FLAC] Best of 2024 (Live) [100%].flac",
                    size = 30000000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val dir = RemoteDirectory(path = "/Music/", name = "Music", files = listOf(file1))
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )

            // Query with brackets
            val resultBrackets = repository.search("[FLAC]").first()
            assertEquals(1, resultBrackets.matchedTracks.size)

            // Query with percent
            val resultPercent = repository.search("100%").first()
            assertEquals(1, resultPercent.matchedTracks.size)

            // Query with parenthesis
            val resultParen = repository.search("(Live)").first()
            assertEquals(1, resultParen.matchedTracks.size)
        }

    @Test
    fun search_withChineseAndMixedCharacters_matchesCorrectly() =
        runTest {
            val file =
                RemoteFile(
                    name = "晴天.flac",
                    path = "/Jay/晴天.flac",
                    size = 20000000L,
                    fileType = RemoteFileType.Audio(AudioFormat.FLAC),
                )
            val dir = RemoteDirectory(path = "/Jay/", name = "周杰伦", files = listOf(file))
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Jay/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir),
                    lastUpdatedMs = 1000L,
                ),
            )
            trackMetadataDao.insertOrUpdate(
                TrackMetadataEntity(
                    serverId = 1L,
                    remotePath = "/Jay/晴天.flac",
                    title = "晴天",
                    artist = "周杰伦",
                    album = "叶惠美",
                    trackNumber = 3,
                    durationMs = 269000L,
                    coverThumbnailPath = null,
                ),
            )

            // Search directory by Chinese name
            val dirResult = repository.search("周杰伦").first()
            assertEquals(1, dirResult.matchedDirectories.size)
            assertEquals("周杰伦", dirResult.matchedDirectories.first().name)
            assertEquals(1, dirResult.matchedTracks.size)
            assertEquals("晴天", dirResult.matchedTracks.first().title)

            // Search track by Chinese album
            val albumResult = repository.search("叶惠美").first()
            assertEquals(1, albumResult.matchedTracks.size)
            assertEquals("晴天", albumResult.matchedTracks.first().title)

            // Search track by Chinese partial title
            val titleResult = repository.search("晴").first()
            assertEquals(1, titleResult.matchedTracks.size)
        }

    @Test
    fun search_withServerIdFilter_onlyReturnsTargetServerItems() =
        runTest {
            val dir1 = RemoteDirectory(path = "/Music/Pop/", name = "Pop")
            val dir2 = RemoteDirectory(path = "/Music/Pop/", name = "Pop")
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/Pop/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir1),
                    lastUpdatedMs = 1000L,
                ),
            )
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 2L,
                    path = "/Music/Pop/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(dir2),
                    lastUpdatedMs = 1000L,
                ),
            )

            // Query with serverId = 1
            val result1 = repository.search("Pop", serverId = 1L).first()
            assertEquals(1, result1.matchedDirectories.size)

            // Query with serverId = 2
            val result2 = repository.search("Pop", serverId = 2L).first()
            assertEquals(1, result2.matchedDirectories.size)

            // Query without serverId: matches both
            val resultAll = repository.search("Pop", serverId = null).first()
            assertEquals(2, resultAll.matchedDirectories.size)
        }

    @Test
    fun search_deduplicatesDirectoriesAndTracks() =
        runTest {
            // Root has subfolder /Music/Rock/
            val rootDir =
                RemoteDirectory(
                    path = "/Music/",
                    name = "Music",
                    subDirectories = listOf(RemoteDirectory(path = "/Music/Rock/", name = "Rock")),
                )
            // And /Music/Rock/ is also cached as its own entity
            val rockDir =
                RemoteDirectory(
                    path = "/Music/Rock/",
                    name = "Rock",
                )
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(rootDir),
                    lastUpdatedMs = 1000L,
                ),
            )
            directoryCacheDao.insertOrUpdate(
                DirectoryCacheEntity(
                    serverId = 1L,
                    path = "/Music/Rock/",
                    dataJson = RemoteDirectoryJsonSerializer.serialize(rockDir),
                    lastUpdatedMs = 1000L,
                ),
            )

            val result = repository.search("Rock").first()
            // Deduplicated: should be exactly 1 Rock directory for server 1
            assertEquals(1, result.matchedDirectories.size)
            assertEquals("/Music/Rock/", result.matchedDirectories.first().path)
        }
}
