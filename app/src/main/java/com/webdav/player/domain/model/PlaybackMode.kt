package com.webdav.player.domain.model

/**
 * 播放队列在切歌时的流转规则，包含列表循环（List Loop）、单曲循环（Single Loop）与随机播放（Shuffle）。
 * Reference: CONTEXT.md -> Playback Mode
 */
enum class PlaybackMode {
    LIST_LOOP,
    SINGLE_LOOP,
    SHUFFLE;

    val label: String
        get() = when (this) {
            LIST_LOOP -> "列表循环"
            SINGLE_LOOP -> "单曲循环"
            SHUFFLE -> "随机播放"
        }

    fun next(): PlaybackMode = when (this) {
        LIST_LOOP -> SINGLE_LOOP
        SINGLE_LOOP -> SHUFFLE
        SHUFFLE -> LIST_LOOP
    }
}
