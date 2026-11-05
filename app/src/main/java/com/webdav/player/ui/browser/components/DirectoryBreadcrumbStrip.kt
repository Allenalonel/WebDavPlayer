package com.webdav.player.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.player.domain.model.Breadcrumb
import com.webdav.player.domain.model.RemoteDirectory
import com.webdav.player.ui.motion.MotionTokens
import com.webdav.player.ui.motion.expressivePress
import com.webdav.player.ui.motion.isReduceMotionEnabled
import com.webdav.player.ui.theme.AppIcons

/**
 * Material 3 Expressive Directory Breadcrumb Strip.
 *
 * Displays navigable hierarchy trail as an MD3 AssistChip strip. Dynamically animates the
 * addition and removal of trailing chips horizontally without vertical layout jumps.
 */
@Composable
fun DirectoryBreadcrumbStrip(
    breadcrumbs: List<Breadcrumb>,
    currentPath: String,
    onBreadcrumbClicked: (Breadcrumb) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val isReduceMotion = isReduceMotionEnabled()

    LaunchedEffect(breadcrumbs, breadcrumbs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        breadcrumbs.forEachIndexed { index, breadcrumb ->
            val isTail =
                index == breadcrumbs.lastIndex ||
                    breadcrumb.path == currentPath ||
                    RemoteDirectory.normalizePath(breadcrumb.path) == RemoteDirectory.normalizePath(currentPath)

            val transitionState =
                remember(breadcrumb.path) {
                    MutableTransitionState(isReduceMotion).apply {
                        targetState = true
                    }
                }

            AnimatedVisibility(
                visibleState = transitionState,
                enter =
                    expandHorizontally(
                        animationSpec = MotionTokens.spatialSpec(isReduceMotion),
                        expandFrom = Alignment.Start,
                    ) +
                        fadeIn(
                            animationSpec = MotionTokens.effectsSpec(isReduceMotion),
                        ),
                exit =
                    shrinkHorizontally(
                        animationSpec = MotionTokens.spatialSpec(isReduceMotion),
                        shrinkTowards = Alignment.Start,
                    ) +
                        fadeOut(
                            animationSpec = MotionTokens.effectsSpec(isReduceMotion),
                        ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AssistChip(
                        onClick = { onBreadcrumbClicked(breadcrumb) },
                        modifier = Modifier.expressivePress(),
                        label = {
                            Text(
                                text = breadcrumb.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isTail) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (index == 0) {
                                Icon(
                                    imageVector = AppIcons.Home,
                                    contentDescription = "根目录",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.Folder,
                                    contentDescription = "目录",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            }
                        },
                        colors =
                            if (isTail) {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    leadingIconContentColor = MaterialTheme.colorScheme.outline,
                                )
                            },
                        border =
                            if (isTail) {
                                AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                )
                            } else {
                                AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            },
                        shape = MaterialTheme.shapes.small,
                    )

                    if (index < breadcrumbs.lastIndex) {
                        Icon(
                            imageVector = AppIcons.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility alias for DirectoryBreadcrumbStrip
 */
@Composable
fun BreadcrumbBar(
    breadcrumbs: List<Breadcrumb>,
    currentPath: String,
    onBreadcrumbClicked: (Breadcrumb) -> Unit,
    modifier: Modifier = Modifier,
) {
    DirectoryBreadcrumbStrip(
        breadcrumbs = breadcrumbs,
        currentPath = currentPath,
        onBreadcrumbClicked = onBreadcrumbClicked,
        modifier = modifier,
    )
}
