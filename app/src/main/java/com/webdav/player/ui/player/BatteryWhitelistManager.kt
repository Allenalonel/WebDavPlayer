package com.webdav.player.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

interface BatteryWhitelistManager {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean

    fun isXiaomiDevice(): Boolean

    fun createIgnoreBatteryOptimizationIntent(context: Context): Intent

    fun createAppDetailsSettingsIntent(context: Context): Intent

    fun createOemAppSettingsIntent(context: Context): Intent
}

class DefaultBatteryWhitelistManager(
    private val powerManagerProvider: (Context) -> PowerManager? = { context ->
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    },
    private val manufacturerProvider: () -> String = { Build.MANUFACTURER.orEmpty() },
    private val brandProvider: () -> String = { Build.BRAND.orEmpty() },
) : BatteryWhitelistManager {
    override fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        try {
            val pm = powerManagerProvider(context)
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } catch (_: Throwable) {
            true
        }

    override fun isXiaomiDevice(): Boolean {
        val m = manufacturerProvider()
        val b = brandProvider()
        return m.contains("Xiaomi", ignoreCase = true) ||
            m.contains("Redmi", ignoreCase = true) ||
            b.contains("Xiaomi", ignoreCase = true) ||
            b.contains("Redmi", ignoreCase = true) ||
            b.contains("POCO", ignoreCase = true)
    }

    override fun createIgnoreBatteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

    override fun createOemAppSettingsIntent(context: Context): Intent {
        val miuiIntent =
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        return try {
            if (context.packageManager.resolveActivity(miuiIntent, 0) != null) {
                miuiIntent
            } else {
                createAppDetailsSettingsIntent(context)
            }
        } catch (_: Throwable) {
            createAppDetailsSettingsIntent(context)
        }
    }

    override fun createAppDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
}
