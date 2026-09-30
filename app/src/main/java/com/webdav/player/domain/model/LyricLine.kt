package com.webdav.player.domain.model

data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val translation: String? = null
) {
    val mainText: String get() = text
    val hasTranslation: Boolean get() = !translation.isNullOrBlank()
}
