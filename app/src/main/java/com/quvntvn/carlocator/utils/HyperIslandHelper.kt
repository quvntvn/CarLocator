package com.quvntvn.carlocator.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * Tente d'afficher une notification dans le HyperIsland des téléphones Xiaomi sous HyperOS.
 *
 * IMPORTANT : l'API "Focus Notification" / HyperIsland de Xiaomi n'est PAS publique.
 * Les extras ci-dessous sont issus du reverse-engineering communautaire. Sur les téléphones
 * Xiaomi non-whitelistés, ils peuvent être ignorés silencieusement — la notification s'affiche
 * alors normalement dans le tiroir, sans casser l'app. Aucun impact sur les autres marques.
 */
object HyperIslandHelper {

    const val FOCUS_CHANNEL_ID = "trip_focus_channel"

    fun isAvailable(): Boolean = XiaomiHelper.isHyperOS()

    /**
     * Crée le canal de notification "silencieux mais HIGH importance" requis pour qu'HyperOS
     * envisage la promotion vers HyperIsland. Pas de son, pas de vibration : la notif reste
     * silencieuse comme l'ancien channel LOW.
     */
    fun ensureFocusChannel(context: Context, channelName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(FOCUS_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            FOCUS_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Applique les extras HyperIsland sur un builder existant. Sans effet sur non-HyperOS.
     * @param ongoing true pour une "Live Activity" type trajet, false pour une alerte ponctuelle.
     */
    fun applyFocusExtras(
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        ongoing: Boolean
    ) {
        if (!isAvailable()) return
        try {
            val focusParam = JSONObject().apply {
                put("ticker", title)
                put("title", title)
                put("subTitle", content)
                put("isFloatTime", ongoing)
                put("type", if (ongoing) 1 else 0)
                put("enableFloat", true)
            }
            val paramJson = focusParam.toString()
            val extras = Bundle().apply {
                putBoolean("miui.focusNotification", true)
                putBoolean("miui.focus.enable", true)
                putString("miui.focus.title", title)
                putString("miui.focus.content", content)
                putString("miui.focus.param", paramJson)
                putString("miui.focus.param.v2", paramJson)
                putString("miui.focus.ticker", title)
            }
            builder.addExtras(extras)
        } catch (e: Throwable) {
            // L'app ne doit jamais crasher à cause de ce hack — extras ignorés silencieusement.
        }
    }
}
