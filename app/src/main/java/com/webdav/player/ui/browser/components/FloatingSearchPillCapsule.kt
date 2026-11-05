package com.webdav.player.ui.browser.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.webdav.player.ui.motion.MotionTokens
import com.webdav.player.ui.motion.expressivePress
import com.webdav.player.ui.motion.isReduceMotionEnabled
import com.webdav.player.ui.theme.AppIcons

/**
 * Material 3 Expressive Floating Search Pill Capsule.
 *
 * Morphs between a 38dp circular button at the top-right and a 26dp rounded floating pill
 * spanning the top area with [MotionTokens.spatialSpring].
 */
@Composable
fun FloatingSearchPillCapsule(
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onBackClick: () -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    isSearchSelectionMode: Boolean = false,
    selectedCount: Int = 0,
    isAllSelected: Boolean = false,
    onClearSelection: () -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
) {
    val isReduceMotion = isReduceMotionEnabled()

    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = MotionTokens.spatialSpec(isReduceMotion),
        label = "search_capsule_expansion",
    )

    // When completely collapsed and not expanded, do not consume layout/touches
    if (!isExpanded && expansionProgress <= 0.001f) {
        return
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp),
    ) {
        val availableWidth = maxWidth

        val currentHeight = lerp(38.dp, 52.dp, expansionProgress)
        val currentCornerRadius = lerp(19.dp, 26.dp, expansionProgress)
        val currentTopOffset = lerp(9.dp, 2.dp, expansionProgress)
        val currentEndOffset = lerp(16.dp, 12.dp, expansionProgress)

        val targetExpandedWidth = (availableWidth - 24.dp).coerceAtLeast(38.dp)
        val currentWidth = lerp(38.dp, targetExpandedWidth, expansionProgress)

        val tonalElevation = lerp(0.dp, 4.dp, expansionProgress)
        val shadowElevation = lerp(0.dp, 2.dp, expansionProgress)

        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(isExpanded, isSearchSelectionMode, expansionProgress >= 0.35f) {
            if (isExpanded && !isSearchSelectionMode && expansionProgress >= 0.35f) {
                runCatching { focusRequester.requestFocus() }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = currentTopOffset, end = currentEndOffset),
            contentAlignment = Alignment.TopEnd,
        ) {
            Surface(
                shape = RoundedCornerShape(currentCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = tonalElevation,
                shadowElevation = shadowElevation,
                modifier =
                    Modifier
                        .width(currentWidth)
                        .height(currentHeight)
                        .expressivePress(enabled = !isExpanded),
            ) {
                if (expansionProgress < 0.35f) {
                    // Collapsed state: 38dp circular magnifying glass button
                    val iconAlpha = ((0.35f - expansionProgress) / 0.35f).coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .alpha(iconAlpha)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onExpand,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = AppIcons.Search,
                            contentDescription = "搜索曲库",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    // Expanded state: full search pill capsule
                    val contentAlpha = ((expansionProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .alpha(contentAlpha),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSearchSelectionMode) {
                            // Search Multi-Selection Top Row
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onClearSelection,
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Close,
                                            contentDescription = "退出多选",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "已选 $selectedCount 项",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                TextButton(onClick = onToggleSelectAll) {
                                    Text(
                                        text = if (isAllSelected) "取消全选" else "全选",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        } else {
                            // Standard Search Input Row
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = AppIcons.ArrowBack,
                                        contentDescription = "返回",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    textStyle =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "搜索歌曲、歌手、专辑...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        innerTextField()
                                    },
                                )

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = onClearQuery,
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.Close,
                                            contentDescription = "清空输入",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
