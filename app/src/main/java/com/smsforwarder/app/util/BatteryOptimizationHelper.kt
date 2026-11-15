package com.smsforwarder.app.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper Object Managing Android Battery Optimization Exemptions And Oem Autostart Configurations.
 * Ensures Uninterrupted Background Execution On Aggressive Handsets Like Xiaomi, Samsung, Vivo, And Oppo.
 */
object BatteryOptimizationHelper {

    private const val Tag = "BatteryOptimizationHelper"

    /**
     * Checks Whether The Application Has Been Exempted From Doze Mode And Battery Optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }
        return true
    }

    /**
     * Launches System Prompt Requesting Direct Exemption From Battery Optimization.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val directExemptionIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(directExemptionIntent)
            } catch (exception: Exception) {
                Log.e(Tag, "Cannot Launch Direct Battery Exemption Prompt", exception)
                try {
                    val genericSettingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(genericSettingsIntent)
                } catch (fallbackException: Exception) {
                    Log.e(Tag, "Cannot Launch Generic Battery Settings", fallbackException)
                }
            }
        }
    }

    /**
     * Attempts To Navigate Directly To Manufacturer-Specific Autostart And Background Permission Settings.
     */
    fun openOemAutoStartSettings(context: Context): Boolean {
        val deviceManufacturer = Build.MANUFACTURER.lowercase()
        val candidateIntents = mutableListOf<Intent>()

        when {
            // Xiaomi / Redmi / Poco Devices Running Miui Or Hyperos
            deviceManufacturer.contains("xiaomi") || deviceManufacturer.contains("redmi") || deviceManufacturer.contains("poco") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                candidateIntents.add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
            }
            // Oppo / Realme Devices Running Coloros Or Realmeui
            deviceManufacturer.contains("oppo") || deviceManufacturer.contains("realme") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")))
            }
            // Vivo / Iqoo Devices Running Funtouchos Or Originui
            deviceManufacturer.contains("vivo") || deviceManufacturer.contains("iqoo") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")))
                candidateIntents.add(Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")))
            }
            // Huawei / Honor Devices Running Emui Or Magicui
            deviceManufacturer.contains("huawei") || deviceManufacturer.contains("honor") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")))
            }
            // Samsung Devices Running Oneui
            deviceManufacturer.contains("samsung") -> {
                candidateIntents.add(Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")))
            }
        }

        // Standard Application Details Setting Used As Universal Fallback
        candidateIntents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )

        for (targetIntent in candidateIntents) {
            try {
                targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(targetIntent)
                return true
            } catch (ignored: Exception) {
                // Try Next Candidate Intent
            }
        }
        return false
    }
}
