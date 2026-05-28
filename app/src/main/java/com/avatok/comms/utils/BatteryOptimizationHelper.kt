/*
 *  Copyright (C) 2026 AvaTok contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 */
package com.avatok.comms.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Push-reliability helpers: keep AvaTok exempt from battery optimization so
 * high-priority FCM wake-ups reach an idle / killed app.
 *
 * Two distinct "battery killers" exist:
 *  - Stock Android Doze / App Standby — togglable via the public one-tap
 *    ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog.
 *  - OEM auto-start managers (MIUI, Samsung, Huawei, Oppo/ColorOS, Vivo,
 *    OnePlus, ...) — NO public API. We can only deep-link the user to the
 *    relevant settings screen; component names vary per OEM/version so each
 *    attempt is best-effort with a fallback to the app's details page.
 *
 * Nothing here runs the app in the background — it only stops the OS from
 * killing the process / deferring our occasional push. The app stays idle
 * between pushes, so this carries no battery cost.
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Fire the one-tap system dialog asking to ignore battery optimization.
     *  Requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS in the manifest. */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Request-exemption dialog unavailable; opening the settings list", e)
        }
        return try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "No battery-optimization settings screen available", e)
            false
        }
    }

    /** Manufacturers known to kill background apps unless auto-start is on. */
    fun isAggressiveOem(): Boolean = Build.MANUFACTURER.lowercase() in AGGRESSIVE_OEMS

    /** Best-effort deep link into the OEM auto-start / app-launch manager.
     *  Falls back to the app's details page (where most OEMs surface it). */
    fun openOemAutoStartSettings(context: Context): Boolean {
        for (component in OEM_AUTOSTART_COMPONENTS) {
            try {
                context.startActivity(
                    Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (e: Exception) {
                // Not this OEM / activity not exported on this version — try next.
            }
        }
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Cannot open app details settings", e)
            false
        }
    }

    private val AGGRESSIVE_OEMS = setOf(
        "xiaomi", "redmi", "poco", "samsung", "huawei", "honor",
        "oppo", "vivo", "oneplus", "realme", "meizu", "asus", "letv"
    )

    // Best-effort; tried in order, each guarded. Not exhaustive across versions.
    private val OEM_AUTOSTART_COMPONENTS = listOf(
        ComponentName("com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.samsung.android.sm",
            "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ComponentName("com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager",
            "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe",
            "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure",
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.oneplus.security",
            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ComponentName("com.letv.android.letvsafe",
            "com.letv.android.letvsafe.AutobootManageActivity"),
        ComponentName("com.asus.mobilemanager",
            "com.asus.mobilemanager.MainActivity")
    )
}
