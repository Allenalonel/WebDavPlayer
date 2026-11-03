package com.webdav.player.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive Continuous Fluid Left-Aligned Collapsing Top App Bar.
 *
 * Implements:
 * 1. Native exit-until-collapsed continuous height collapse to 0 with strictly configured heightOffsetLimit.
 * 2. Strict left-aligned horizontal baseline (Consistent Left Alignment) with zero horizontal jumping.
 * 3. Atmospheric glass blur / translucent surface container diffusion when scrolled.
 * 4. Pure minimal layout with no redundant buttons.
 */
object FluidTopAppBarDefaults {
    val ExpandedHeight: Dp = 64.dp
    val CollapsedHeight: Dp = 56.dp
    val LeftBaselineNoNav: Dp = 16.dp
    val LeftBaselineWithNav: Dp = 56.dp

    fun calculateHeightOffsetLimit(expandedHeightPx: Float): Float = if (expandedHeightPx > 0f) -expandedHeightPx else 0f

    fun calculateCompactTitleAlpha(
        fraction: Float,
        isExpandedTitlePresent: Boolean,
    ): Float {
        if (!isExpandedTitlePresent) return 1f
        return ((fraction - 0.4f) / 0.6f).coerceIn(0f, 1f)
    }

    fun calculateExpandedTitleAlpha(fraction: Float): Float = ((0.6f - fraction) / 0.6f).coerceIn(0f, 1f)

    fun calculateExpandedHeight(
        baseHeight: Dp,
        fraction: Float,
    ): Dp = lerp(baseHeight, 0.dp, fraction)

    fun calculateLeftPadding(hasNavigationIcon: Boolean): Dp = if (hasNavigationIcon) LeftBaselineWithNav else LeftBaselineNoNav

    fun isScrolled(
        fraction: Float,
        contentOffset: Float,
    ): Boolean = fraction > 0.01f || contentOffset < -1f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    expandedHeight: Dp = FluidTopAppBarDefaults.ExpandedHeight,
    containerColor: Color? = null,
) {
    val density = LocalDensity.current
    val hasExpandedTitle = expandedHeight > 0.dp
    val expandedHeightPx = with(density) { expandedHeight.toPx() }

    // Enforce Compose nested scroll protocol contract:
    // Explicitly configure heightOffsetLimit so scrollBehavior never swallows downstream LazyColumn gestures.
    SideEffect {
        if (scrollBehavior != null) {
            val limit = FluidTopAppBarDefaults.calculateHeightOffsetLimit(expandedHeightPx)
            if (scrollBehavior.state.heightOffsetLimit != limit) {
                scrollBehavior.state.heightOffsetLimit = limit
            }
        }
    }

    val fraction = scrollBehavior?.state?.collapsedFraction ?: 0f
    val contentOffset = scrollBehavior?.state?.contentOffset ?: 0f
    val isScrolled = FluidTopAppBarDefaults.isScrolled(fraction, contentOffset)

    val compactTitleAlpha = FluidTopAppBarDefaults.calculateCompactTitleAlpha(fraction, hasExpandedTitle)
    val expandedTitleAlpha = FluidTopAppBarDefaults.calculateExpandedTitleAlpha(fraction)
    val currentExpandedHeight = FluidTopAppBarDefaults.calculateExpandedHeight(expandedHeight, fraction)

    val scrollProgress =
        if (hasExpandedTitle) {
            fraction
        } else if (isScrolled) {
            1f
        } else {
            0f
        }

    // Scrolled atmospheric background: translucent surfaceContainer (88% alpha) with dynamic elevation
    val resolvedContainerColor =
        containerColor ?: run {
            val scrolledBase = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
            if (isScrolled) {
                if (hasExpandedTitle) {
                    scrolledBase.copy(alpha = (scrollProgress * 0.88f).coerceIn(0f, 0.88f))
                } else {
                    scrolledBase
                }
            } else {
                Color.Transparent
            }
        }

    val elevation = lerp(0.dp, 3.dp, scrollProgress)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = resolvedContainerColor,
        shadowElevation = elevation,
        tonalElevation = lerp(0.dp, 2.dp, scrollProgress),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
        ) {
            // Compact 56dp Top Row
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(FluidTopAppBarDefaults.CollapsedHeight)
                        .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // Compact Title (Strictly left-aligned to 16dp baseline without nav icon, 56dp with nav icon)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = if (navigationIcon != null) 4.dp else 0.dp, end = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .alpha(compactTitleAlpha)
                                .graphicsLayer {
                                    translationY = (1f - compactTitleAlpha) * 8f
                                },
                    )
                }

                if (actions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            // Continuous Fluid Collapsing Title Container (Strictly left-aligned matching baseline)
            if (hasExpandedTitle && currentExpandedHeight > 0.dp) {
                val leftPadding = FluidTopAppBarDefaults.calculateLeftPadding(navigationIcon != null)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(currentExpandedHeight)
                            .padding(start = leftPadding, end = 16.dp)
                            .alpha(expandedTitleAlpha)
                            .graphicsLayer {
                                translationY = -fraction * 10f
                            },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style =
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp,
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
