package com.webdav.player.domain.repository

import com.webdav.player.domain.model.PlaybackSessionData

interface PlaybackSessionStore {
    suspend fun saveSession(sessionData: PlaybackSessionData)
    suspend fun savePosition(positionMs: Long) {}
    suspend fun savePosition(positionMs: Long, virtualPositionMs: Long?) {
        savePosition(positionMs)
    }
    suspend fun getSavedSession(): PlaybackSessionData?
    suspend fun clearSession()
}
