package com.webdav.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webdav.player.domain.model.LyricLine
import com.webdav.player.domain.model.Lyrics
import com.webdav.player.domain.model.PlaybackProgress
import com.webdav.player.ui.theme.AppIcons

object LyricsViewDefaults {
    val ActiveMainFontSize: TextUnit = 22.sp
    val InactiveMainFontSize: TextUnit = 17.sp
    val ActiveTranslationFontSize: TextUnit = 14.sp
    val InactiveTranslationFontSize: TextUnit = 12.sp

    val ActiveMainFontWeight: FontWeight = FontWeight.ExtraBold
    val InactiveMainFontWeight: FontWeight = FontWeight.Bold
    val ActiveTranslationFontWeight: FontWeight = FontWeight.SemiBold
    val InactiveTranslationFontWeight: FontWeight = FontWeight.Medium

    const val ActiveMainAlpha: Float = 1.0f
    const val ActiveTranslationAlpha: Float = 0.88f
    const val InactiveMainAlpha: Float = 0.38f
    const val InactiveTranslationAlpha: Float = 0.30f
    const val UnsyncedMainAlpha: Float = 1.0f
    const val UnsyncedTranslationAlpha: Float = 0.70f

    val BilingualSpacing: Dp = 4.dp
    val ItemVerticalPadding: Dp = 8.dp
    val ItemHorizontalPadding: Dp = 12.dp
    val LineSpacing: Dp = 16.dp

    fun resolveMainAlpha(
        isActive: Boolean,
        isSynchronized: Boolean,
    ): Float =
        when {
            isActive -> ActiveMainAlpha
            isSynchronized -> InactiveMainAlpha
            else -> UnsyncedMainAlpha
        }

    fun resolveTranslationAlpha(
        isActive: Boolean,
        isSynchronized: Boolean,
    ): Float =
        when {
            isActive -> ActiveTranslationAlpha
            isSynchronized -> InactiveTranslationAlpha
            else -> UnsyncedTranslationAlpha
        }
}

@Composable
fun LyricsView(
    lyrics: Lyrics?,
    isLoading: Boolean,
    currentPositionMs: Long = 0L,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    playbackProgress: PlaybackProgress = PlaybackProgress.ZERO,
    onToggleCover: () -> Unit = {},
) {
    val effectivePositionMs =
        if (playbackProgress.durationMs > 0L || playbackProgress.currentPositionMs > 0L) {
            playbackProgress.currentPositionMs
        } else {
            currentPositionMs
        }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val halfViewport = ((maxHeight / 2) - 24.dp).coerceAtLeast(0.dp)
        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在加载歌词...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lyrics == null || lyrics.isEmpty -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleCover,
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Audiotrack,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无歌词",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                val lazyListState = rememberLazyListState()
                val activeIndex = lyrics.findActiveLineIndex(effectivePositionMs)

                // Smoothly scroll to active line
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0 && lyrics.isSynchronized && !lazyListState.isScrollInProgress) {
                        lazyListState.animateScrollToItem(
                            index = activeIndex,
                            scrollOffset = 0,
                        )
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = halfViewport, bottom = halfViewport, start = 0.dp, end = 0.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(LyricsViewDefaults.LineSpacing),
                ) {
                    itemsIndexed(
                        items = lyrics.lines,
                        key = { index, line -> "$index:${line.timestampMs}:${line.text}:${line.translation.orEmpty()}" },
                    ) { index, line ->
                        val isActive = index == activeIndex && lyrics.isSynchronized

                        LyricLineItem(
                            line = line,
                            isActive = isActive,
                            isSynchronized = lyrics.isSynchronized,
                            onClick = {
                                handleLyricLineClick(
                                    line = line,
                                    isSynchronized = lyrics.isSynchronized,
                                    onSeekTo = onSeekTo,
                                    onToggleCover = onToggleCover,
                                )
                            },
                            modifier = Modifier.testTag("lyric_line_$index"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isSynchronized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainTextColor by animateColorAsState(
        targetValue =
            when {
                isActive -> MaterialTheme.colorScheme.primary
                isSynchronized -> MaterialTheme.colorScheme.onSurface.copy(alpha = LyricsViewDefaults.InactiveMainAlpha)
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = LyricsViewDefaults.UnsyncedMainAlpha)
            },
        animationSpec = tween(durationMillis = 250),
        label = "LyricMainTextColor",
    )

    val translationTextColor by animateColorAsState(
        targetValue =
            when {
                isActive -> MaterialTheme.colorScheme.primary.copy(alpha = LyricsViewDefaults.ActiveTranslationAlpha)
                isSynchronized -> MaterialTheme.colorScheme.onSurface.copy(alpha = LyricsViewDefaults.InactiveTranslationAlpha)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = LyricsViewDefaults.UnsyncedTranslationAlpha)
            },
        animationSpec = tween(durationMillis = 250),
        label = "LyricTranslationTextColor",
    )

    val mainFontSize = if (isActive) LyricsViewDefaults.ActiveMainFontSize else LyricsViewDefaults.InactiveMainFontSize
    val mainFontWeight = if (isActive) LyricsViewDefaults.ActiveMainFontWeight else LyricsViewDefaults.InactiveMainFontWeight

    val translationFontSize = if (isActive) LyricsViewDefaults.ActiveTranslationFontSize else LyricsViewDefaults.InactiveTranslationFontSize
    val translationFontWeight = if (isActive) LyricsViewDefaults.ActiveTranslationFontWeight else LyricsViewDefaults.InactiveTranslationFontWeight

    val activeBackgroundColor by animateColorAsState(
        targetValue =
            if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
        animationSpec = tween(durationMillis = 250),
        label = "LyricActiveBackground",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(activeBackgroundColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ).padding(
                    vertical = LyricsViewDefaults.ItemVerticalPadding,
                    horizontal = LyricsViewDefaults.ItemHorizontalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isActive) {
            Box(
                modifier =
                    Modifier
                        .width(3.5.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = line.mainText,
                color = mainTextColor,
                fontSize = mainFontSize,
                fontWeight = mainFontWeight,
                textAlign = TextAlign.Start,
                lineHeight = if (isActive) 30.sp else 24.sp,
                modifier = Modifier.fillMaxWidth().testTag("lyric_main_text"),
            )

            if (line.hasTranslation && !line.translation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(LyricsViewDefaults.BilingualSpacing))
                Text(
                    text = line.translation,
                    color = translationTextColor,
                    fontSize = translationFontSize,
                    fontWeight = translationFontWeight,
                    textAlign = TextAlign.Start,
                    lineHeight = if (isActive) 20.sp else 18.sp,
                    modifier = Modifier.fillMaxWidth().testTag("lyric_translation_text"),
                )
            }
        }
    }
}

/**
 * Dispatches lyric interaction (tap-to-seek or toggle cover).
 */
fun handleLyricLineClick(
    line: LyricLine,
    isSynchronized: Boolean,
    onSeekTo: (Long) -> Unit,
    onToggleCover: () -> Unit,
) {
    if (isSynchronized) {
        onSeekTo(line.timestampMs)
    } else {
        onToggleCover()
    }
}
