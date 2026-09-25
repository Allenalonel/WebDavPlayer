package com.webdav.player.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.webdav.player.domain.model.AudioFormat
import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.PlaybackMode
import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.repository.PlaybackSessionStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_session_prefs")

class DataStorePlaybackSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val fileProvider: (() -> File)? = null
) : PlaybackSessionStore {

    constructor(context: Context) : this(
        context.sessionDataStore,
        { context.preferencesDataStoreFile("playback_session_prefs") }
    )

    companion object {
        private val KEY_ACTIVE_SERVER_ID = longPreferencesKey("active_server_id")
        private val KEY_CURRENT_DIRECTORY_PATH = stringPreferencesKey("current_directory_path")
        private val KEY_QUEUE_TRACKS_JSON = stringPreferencesKey("queue_tracks_json")
        private val KEY_CURRENT_TRACK_INDEX = intPreferencesKey("current_track_index")
        private val KEY_POSITION_MS = longPreferencesKey("position_ms")
        private val KEY_PLAYBACK_MODE = stringPreferencesKey("playback_mode")
    }

    override suspend fun saveSession(sessionData: PlaybackSessionData) {
        val tracksJson = serializeTracks(sessionData.queueTracks)
        dataStore.edit { prefs ->
            fileProvider?.invoke()?.let { file -> if (file.exists()) file.delete() }
            if (sessionData.activeServerId != null) {
                prefs[KEY_ACTIVE_SERVER_ID] = sessionData.activeServerId
            } else {
                prefs.remove(KEY_ACTIVE_SERVER_ID)
            }
            prefs[KEY_CURRENT_DIRECTORY_PATH] = sessionData.currentDirectoryPath
            prefs[KEY_QUEUE_TRACKS_JSON] = tracksJson
            prefs[KEY_CURRENT_TRACK_INDEX] = sessionData.currentTrackIndex
            prefs[KEY_POSITION_MS] = sessionData.positionMs
            prefs[KEY_PLAYBACK_MODE] = sessionData.playbackMode.name
        }
    }

    override suspend fun getSavedSession(): PlaybackSessionData? {
        val prefs = dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .first()

        // If no server and no directory path and no tracks, consider it empty
        val serverId = prefs[KEY_ACTIVE_SERVER_ID]
        val dirPath = prefs[KEY_CURRENT_DIRECTORY_PATH]
        val tracksJson = prefs[KEY_QUEUE_TRACKS_JSON]

        if (serverId == null && dirPath == null && tracksJson == null) {
            return null
        }

        val tracks = deserializeTracks(tracksJson)
        val trackIndex = prefs[KEY_CURRENT_TRACK_INDEX] ?: -1
        val positionMs = prefs[KEY_POSITION_MS] ?: 0L
        val modeStr = prefs[KEY_PLAYBACK_MODE]
        val mode = modeStr?.let {
            try {
                PlaybackMode.valueOf(it)
            } catch (e: Exception) {
                PlaybackMode.LIST_LOOP
            }
        } ?: PlaybackMode.LIST_LOOP

        return PlaybackSessionData(
            activeServerId = serverId,
            currentDirectoryPath = dirPath ?: "/",
            queueTracks = tracks,
            currentTrackIndex = trackIndex,
            positionMs = positionMs,
            playbackMode = mode
        )
    }

    override suspend fun clearSession() {
        dataStore.edit { prefs ->
            fileProvider?.invoke()?.let { file -> if (file.exists()) file.delete() }
            prefs.clear()
        }
    }

    @VisibleForTesting
    suspend fun saveRawForTesting(
        serverId: Long,
        dirPath: String,
        tracksJson: String,
        trackIndex: Int,
        posMs: Long,
        mode: String
    ) {
        dataStore.edit { prefs ->
            fileProvider?.invoke()?.let { file -> if (file.exists()) file.delete() }
            prefs[KEY_ACTIVE_SERVER_ID] = serverId
            prefs[KEY_CURRENT_DIRECTORY_PATH] = dirPath
            prefs[KEY_QUEUE_TRACKS_JSON] = tracksJson
            prefs[KEY_CURRENT_TRACK_INDEX] = trackIndex
            prefs[KEY_POSITION_MS] = posMs
            prefs[KEY_PLAYBACK_MODE] = mode
        }
    }

    private fun serializeTracks(tracks: List<AudioTrack>): String {
        val array = JSONArray()
        for (track in tracks) {
            val obj = JSONObject().apply {
                put("id", track.id)
                put("serverId", track.serverId)
                put("remotePath", track.remotePath)
                put("title", track.title)
                if (track.artist != null) put("artist", track.artist)
                if (track.album != null) put("album", track.album)
                put("durationMs", track.durationMs)
                put("size", track.size)
                put("format", track.format.name)
                if (track.coverThumbnailPath != null) put("coverThumbnailPath", track.coverThumbnailPath)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeTracks(json: String?): List<AudioTrack> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<AudioTrack>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val formatStr = obj.optString("format", "MP3")
                val format = try {
                    AudioFormat.valueOf(formatStr)
                } catch (e: Exception) {
                    AudioFormat.MP3
                }
                list.add(
                    AudioTrack(
                        id = obj.getString("id"),
                        serverId = obj.getLong("serverId"),
                        remotePath = obj.getString("remotePath"),
                        title = obj.getString("title"),
                        artist = if (obj.has("artist") && !obj.isNull("artist")) obj.getString("artist") else null,
                        album = if (obj.has("album") && !obj.isNull("album")) obj.getString("album") else null,
                        durationMs = obj.optLong("durationMs", 0L),
                        size = obj.optLong("size", 0L),
                        format = format,
                        coverThumbnailPath = if (obj.has("coverThumbnailPath") && !obj.isNull("coverThumbnailPath")) {
                            obj.getString("coverThumbnailPath")
                        } else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
