package com.webdav.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics

@Composable
fun LyricsView(
    lyrics: Lyrics?,
    isLoading: Boolean,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onToggleCover: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在加载歌词...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            lyrics == null || lyrics.isEmpty -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleCover
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Audiotrack,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无歌词",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                val lazyListState = rememberLazyListState()
                val activeIndex = lyrics.findActiveLineIndex(currentPositionMs)

                // Smoothly scroll to active line
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0 && lyrics.isSynchronized && !lazyListState.isScrollInProgress) {
                        lazyListState.animateScrollToItem(
                            index = activeIndex,
                            scrollOffset = 0
                        )
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 180.dp, bottom = 220.dp, start = 16.dp, end = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { index, line -> "$index:${line.timestampMs}:${line.text}" }
                    ) { index, line ->
                        val isActive = index == activeIndex && lyrics.isSynchronized

                        LyricLineItem(
                            line = line,
                            isActive = isActive,
                            isSynchronized = lyrics.isSynchronized,
                            onClick = {
                                if (lyrics.isSynchronized) {
                                    onSeekTo(line.timestampMs)
                                } else {
                                    onToggleCover()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isSynchronized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primary
            isSynchronized -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 250),
        label = "LyricTextColor"
    )

    val fontSize = if (isActive) 20.sp else 16.sp
    val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

    Text(
        text = line.text,
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        lineHeight = 28.sp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp, horizontal = 16.dp)
    )
}

/**
 * Dispatches lyric interaction (tap-to-seek or toggle cover).
 */
fun handleLyricLineClick(
    line: LyricLine,
    isSynchronized: Boolean,
    onSeekTo: (Long) -> Unit,
    onToggleCover: () -> Unit
) {
    if (isSynchronized) {
        onSeekTo(line.timestampMs)
    } else {
        onToggleCover()
    }
}
