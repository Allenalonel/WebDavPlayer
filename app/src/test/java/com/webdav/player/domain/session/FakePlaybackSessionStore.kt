package com.webdav.player.domain.session

import com.webdav.player.domain.model.PlaybackSessionData
import com.webdav.player.domain.repository.PlaybackSessionStore

class FakePlaybackSessionStore(
    private var initialSession: PlaybackSessionData? = null
) : PlaybackSessionStore {

    var savedSession: PlaybackSessionData? = initialSession
    var saveSessionCount = 0
    var clearSessionCount = 0

    override suspend fun saveSession(sessionData: PlaybackSessionData) {
        savedSession = sessionData
        saveSessionCount++
    }

    override suspend fun savePosition(positionMs: Long) {
        savePosition(positionMs, null)
    }

    override suspend fun savePosition(positionMs: Long, virtualPositionMs: Long?) {
        saveSessionCount++
        savedSession = savedSession?.copy(
            positionMs = positionMs,
            virtualPositionMs = virtualPositionMs ?: savedSession?.virtualPositionMs,
        )
    }

    override suspend fun getSavedSession(): PlaybackSessionData? {
        return savedSession
    }

    override suspend fun clearSession() {
        savedSession = null
        clearSessionCount++
    }
}
