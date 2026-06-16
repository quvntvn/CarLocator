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

    /**
     * Extra système Android 16 (API 36) qui demande la promotion d'une notif ongoing en
     * "Live Update" / "Promoted Ongoing". Clé publique stable :
     * Notification.EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing".
     * Référencée en littéral car compileSdk = 35 (la constante n'existe qu'à partir d'API 36).
     */
    private const val EXTRA_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** Disponibilité de l'API publique Live Updates (Android 16+ ; HyperOS 3.1 l'honore). */
    fun supportsLiveUpdate(): Boolean = Build.VERSION.SDK_INT >= 36

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

    /**
     * Chemin MODERNE (Android 16+ / HyperOS 3.1+) : demande la promotion en Live Update via
     * l'API publique Google. Contrairement à [applyFocusExtras], pas besoin d'être whitelisté
     * par Xiaomi — fonctionne aussi sur Pixel, Samsung One UI 8, etc.
     *
     * Prérequis (déjà remplis par la notif de trajet) : ongoing, contentTitle non vide,
     * canal non IMPORTANCE_MIN, pas de custom RemoteViews, pas de setColorized(true).
     * Nécessite la permission POST_PROMOTED_NOTIFICATIONS dans le manifest.
     * No-op sous API 36.
     */
    fun applyLiveUpdate(builder: NotificationCompat.Builder) {
        if (!supportsLiveUpdate()) return
        try {
            builder.addExtras(Bundle().apply {
                putBoolean(EXTRA_PROMOTED_ONGOING, true)
            })
        } catch (e: Throwable) {
            // Amélioration cosmétique : ne jamais crasher si l'extra est rejeté.
        }
    }
}
