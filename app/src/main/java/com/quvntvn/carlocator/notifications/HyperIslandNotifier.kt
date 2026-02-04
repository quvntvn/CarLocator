package com.quvntvn.carlocator.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.quvntvn.carlocator.R
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.Template

object HyperIslandNotifier {
    private const val CHANNEL_ID = "hyperisland_connection"
    private const val NOTIFICATION_ID = 4101

    fun showCarConnectionNotification(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(context)

        val notification = HyperIslandNotification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setTitle("Voiture Connectée")
            .setSubtitle("Garée à l'instant")
            .setTemplate(Template.CONNECTION)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.hyperisland_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.hyperisland_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
