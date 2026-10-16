package com.webdav.player.ui.player

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.webdav.player.ui.theme.AppIcons

@Composable
fun BatteryWhitelistBanner(
    wasKilledBySystem: Boolean,
    modifier: Modifier = Modifier,
    whitelistManager: BatteryWhitelistManager = remember { DefaultBatteryWhitelistManager() },
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isIgnoringBatteryOptimizationsState =
        remember {
            mutableStateOf(whitelistManager.isIgnoringBatteryOptimizations(context))
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isIgnoringBatteryOptimizationsState.value = whitelistManager.isIgnoringBatteryOptimizations(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isDismissedState = rememberSaveable { mutableStateOf(false) }

    val isVisible = !isIgnoringBatteryOptimizationsState.value && !isDismissedState.value

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier.testTag("BatteryWhitelistBanner"),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = if (wasKilledBySystem) AppIcons.Warning else AppIcons.Info,
                        contentDescription = "电池优化提示",
                        tint =
                            if (wasKilledBySystem) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        modifier =
                            Modifier
                                .size(24.dp)
                                .padding(top = 2.dp),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text =
                                if (wasKilledBySystem) {
                                    "检测到上次锁屏被系统强制终止"
                                } else {
                                    "建议开启后台播放保护"
                                },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (wasKilledBySystem) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        val description =
                            if (whitelistManager.isXiaomiDevice()) {
                                "为防息屏断流，请点击「快速授权」允许弹窗，并在「电池设置」开启自启动且省电策略设为无限制"
                            } else {
                                "为防息屏断流，请点击「快速授权」允许弹窗，或在「电池设置」中将电池使用设为「无限制」"
                            }

                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }

                    IconButton(
                        onClick = {
                            isDismissedState.value = true
                            onDismiss()
                        },
                        modifier = Modifier.size(24.dp).testTag("BatteryWhitelistBannerDismissButton"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = "关闭提示",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                context.startActivity(whitelistManager.createIgnoreBatteryOptimizationIntent(context))
                            } catch (_: Throwable) {
                                try {
                                    context.startActivity(whitelistManager.createAppDetailsSettingsIntent(context))
                                } catch (_: Throwable) {
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp).testTag("BatteryWhitelistBannerActionButton"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(text = "快速授权", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = {
                            try {
                                Toast.makeText(context, "请点击「电池」并勾选「无限制」", Toast.LENGTH_LONG).show()
                                val intent =
                                    if (whitelistManager.isXiaomiDevice()) {
                                        whitelistManager.createOemAppSettingsIntent(context)
                                    } else {
                                        whitelistManager.createAppDetailsSettingsIntent(context)
                                    }
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                                try {
                                    context.startActivity(whitelistManager.createAppDetailsSettingsIntent(context))
                                } catch (_: Throwable) {
                                }
                            }
                        },
                        modifier = Modifier.testTag("BatteryWhitelistBannerOemButton"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                    ) {
                        Text(text = "电池设置", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
