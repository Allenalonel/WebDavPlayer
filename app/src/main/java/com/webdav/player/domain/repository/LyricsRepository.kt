package com.webdav.player.domain.repository

import com.webdav.player.domain.model.AudioTrack
import com.webdav.player.domain.model.Lyrics
import com.webdav.player.domain.model.WebDavServer

interface LyricsRepository {
    /**
     * Resolves lyrics for an audio track using dual-source priority:
     * 1. Probes remote directory for ${baseName}.lrc via WebDAV GET.
     * 2. If remote .lrc is not found (404/error), falls back to embedded metadata
     *    (ID3 USLT/SYLT, Vorbis comment, ASF WM/Lyrics).
     * 3. Returns Lyrics.EMPTY if neither source provides lyrics.
     */
    suspend fun resolveLyrics(server: WebDavServer, track: AudioTrack): Lyrics
}
