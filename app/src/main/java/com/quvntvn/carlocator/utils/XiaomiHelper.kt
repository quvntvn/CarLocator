package com.quvntvn.carlocator.utils

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Locale

object XiaomiHelper {

    enum class AutostartStatus { GRANTED, DENIED, UNKNOWN }

    private const val OP_AUTO_START = 10008

    fun isXiaomi(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val brand = Build.BRAND.lowercase(Locale.ROOT)
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") ||
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco")
    }

    fun isHyperOS(): Boolean = getSystemProperty("ro.mi.os.version.name")?.isNotBlank() == true

    fun getMiuiVersion(): String? = getSystemProperty("ro.miui.ui.version.name")

    fun getHyperOsVersion(): String? = getSystemProperty("ro.mi.os.version.name")

    fun checkAutostartStatus(context: Context): AutostartStatus {
        if (!isXiaomi()) return AutostartStatus.UNKNOWN
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val uid = Process.myUid()
            val result = method.invoke(appOps, OP_AUTO_START, uid, context.packageName) as Int
            when (result) {
                AppOpsManager.MODE_ALLOWED -> AutostartStatus.GRANTED
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ERRORED -> AutostartStatus.DENIED
                else -> AutostartStatus.UNKNOWN
            }
        } catch (e: Throwable) {
            AutostartStatus.UNKNOWN
        }
    }

    private val autostartTargets = listOf(
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.MainAcitivty"
        ),
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.appmanager.AppManagerMainActivity"
        )
    )

    private val hiddenAppsTargets = listOf(
        ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        ),
        ComponentName(
            "com.miui.powerkeeper",
            "com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity"
        )
    )

    fun openAutostartSettings(context: Context): Boolean {
        return launchFirstResolvable(context, autostartTargets) ||
            openAppDetailsSettings(context)
    }

    fun openHiddenAppsSettings(context: Context): Boolean {
        return launchFirstResolvable(context, hiddenAppsTargets) ||
            openAppDetailsSettings(context)
    }

    private fun launchFirstResolvable(context: Context, targets: List<ComponentName>): Boolean {
        for (target in targets) {
            val intent = Intent().apply {
                component = target
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                return try {
                    context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        return false
    }

    private fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getSystemProperty(key: String): String? = try {
        val cls = Class.forName("android.os.SystemProperties")
        val method = cls.getMethod("get", String::class.java)
        (method.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        null
    }
}
