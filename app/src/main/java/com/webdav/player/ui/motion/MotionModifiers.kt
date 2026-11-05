package com.webdav.player.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive tactile modifiers for interactive surfaces and buttons.
 */

/**
 * Applies a tactile press scale micro-interaction using [graphicsLayer].
 *
 * When pressed, the element scales down to [scaleFactor] (default 0.965f).
 * Upon release, it springs back to 1.0f using [MotionTokens.FastSpatialSpring].
 * When reduce-motion is enabled, the scale remains 1.0f with no bouncing.
 *
 * @param interactionSource Optional [MutableInteractionSource] to observe press state. If null,
 *                          pointer input is observed directly without consuming pointer events.
 * @param enabled Whether this press interaction is enabled.
 * @param scaleFactor Target scale when pressed (default 0.965f).
 */
fun Modifier.expressivePress(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    scaleFactor: Float = MotionTokens.DefaultPressScale,
): Modifier =
    composed(
        inspectorInfo =
            debugInspectorInfo {
                name = "expressivePress"
                properties["enabled"] = enabled
                properties["scaleFactor"] = scaleFactor
                properties["interactionSource"] = interactionSource
            },
    ) {
        val isReduceMotion = isReduceMotionEnabled()

        var isPointerPressed by remember { mutableStateOf(false) }
        val isInteractionPressed = interactionSource?.collectIsPressedAsState()?.value ?: false

        val isPressed =
            if (interactionSource != null) {
                isInteractionPressed
            } else {
                isPointerPressed
            }

        val targetScale = if (isPressed && enabled && !isReduceMotion) scaleFactor else 1.0f
        val springSpec = MotionTokens.resolveSpec(MotionTokens.FastSpatialSpring, isReduceMotion)

        val scale by animateFloatAsState(
            targetValue = targetScale,
            animationSpec = springSpec,
            label = "expressivePressScale",
        )

        val pointerMod =
            if (interactionSource == null && enabled) {
                Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        isPointerPressed = true
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        isPointerPressed = false
                    }
                }
            } else {
                Modifier
            }

        this
            .then(pointerMod)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    }

/**
 * Clickable modifier combining click / long-click handling with Material 3 Expressive bouncy press feedback.
 *
 * Automatically provides physical spring feedback ([expressivePress]) during touch and release.
 *
 * @param enabled Whether this component is clickable.
 * @param scaleFactor Scale factor when pressed (default 0.965f).
 * @param onClickLabel Optional semantic label for the click action.
 * @param role Optional accessibility role.
 * @param onLongClickLabel Optional semantic label for the long-click action.
 * @param onLongClick Optional long click callback.
 * @param onDoubleClick Optional double click callback.
 * @param onClick Click callback.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleFactor: Float = MotionTokens.DefaultPressScale,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
): Modifier =
    composed(
        inspectorInfo =
            debugInspectorInfo {
                name = "bouncyClickable"
                properties["enabled"] = enabled
                properties["scaleFactor"] = scaleFactor
                properties["onClick"] = onClick
                properties["onLongClick"] = onLongClick
            },
    ) {
        val interactionSource = remember { MutableInteractionSource() }

        val clickModifier =
            if (onLongClick != null || onDoubleClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                    onClick = onClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onClick = onClick,
                )
            }

        this
            .expressivePress(
                interactionSource = interactionSource,
                enabled = enabled,
                scaleFactor = scaleFactor,
            ).then(clickModifier)
    }

/**
 * Clickable modifier with explicit [MutableInteractionSource] and [Indication].
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncyClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    scaleFactor: Float = MotionTokens.DefaultPressScale,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit = {},
): Modifier =
    composed(
        inspectorInfo =
            debugInspectorInfo {
                name = "bouncyClickable"
                properties["interactionSource"] = interactionSource
                properties["enabled"] = enabled
                properties["scaleFactor"] = scaleFactor
                properties["onClick"] = onClick
                properties["onLongClick"] = onLongClick
            },
    ) {
        val clickModifier =
            if (onLongClick != null || onDoubleClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick,
                    onClick = onClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onClick = onClick,
                )
            }

        this
            .expressivePress(
                interactionSource = interactionSource,
                enabled = enabled,
                scaleFactor = scaleFactor,
            ).then(clickModifier)
    }

/**
 * Applies an elastic pill stretch micro-interaction on [scaleX] when [isSelected] becomes true.
 *
 * When newly selected, horizontal scale compresses and bounces back to 1.0f with an expressive spring,
 * creating a tactile fluid pill sensation.
 *
 * @param isSelected Whether the associated tab/destination is currently selected.
 * @param initialScale Starting horizontal scale when newly selected (default 0.7f).
 * @param targetScale Final resting horizontal scale (default 1.0f).
 */
fun Modifier.fluidIndicator(
    isSelected: Boolean,
    initialScale: Float = 0.7f,
    targetScale: Float = 1.0f,
): Modifier =
    composed(
        inspectorInfo =
            debugInspectorInfo {
                name = "fluidIndicator"
                properties["isSelected"] = isSelected
                properties["initialScale"] = initialScale
                properties["targetScale"] = targetScale
            },
    ) {
        val isReduceMotion = isReduceMotionEnabled()
        val scaleX = remember { Animatable(targetScale) }
        var isInitialComposition by remember { mutableStateOf(true) }

        LaunchedEffect(isSelected, isReduceMotion) {
            if (isInitialComposition) {
                isInitialComposition = false
                return@LaunchedEffect
            }
            if (isSelected && !isReduceMotion) {
                scaleX.snapTo(initialScale)
                scaleX.animateTo(
                    targetValue = targetScale,
                    animationSpec =
                        spring(
                            dampingRatio = MotionTokens.FastSpatialDampingRatio,
                            stiffness = MotionTokens.SpatialStiffness,
                        ),
                )
            } else {
                scaleX.snapTo(targetScale)
            }
        }

        this.graphicsLayer {
            this.scaleX = scaleX.value
        }
    }

/**
 * Modifier for dialog windows to apply M3 Expressive gentle spring scale-in from 0.92f
 * and fade in upon presentation, collapsing immediately to 1.0f when reduceMotion is active.
 */
fun Modifier.expressiveDialog(reduceMotion: Boolean = false): Modifier =
    composed(
        inspectorInfo =
            debugInspectorInfo {
                name = "expressiveDialog"
                properties["reduceMotion"] = reduceMotion
            },
    ) {
        val systemReduceMotion = isReduceMotionEnabled()
        val isReduceMotion = reduceMotion || systemReduceMotion
        val scale = remember { Animatable(if (isReduceMotion) 1.0f else MotionTransitions.DialogInitialScale) }
        val alpha = remember { Animatable(if (isReduceMotion) 1.0f else 0.0f) }

        LaunchedEffect(isReduceMotion) {
            if (isReduceMotion) {
                scale.snapTo(1.0f)
                alpha.snapTo(1.0f)
            } else {
                launch {
                    scale.animateTo(
                        targetValue = 1.0f,
                        animationSpec = MotionTokens.spatialSpec(),
                    )
                }
                launch {
                    alpha.animateTo(
                        targetValue = 1.0f,
                        animationSpec = MotionTokens.effectsSpec(),
                    )
                }
            }
        }

        this.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
    }
