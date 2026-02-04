package com.quvntvn.carlocator.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.quvntvn.carlocator.R
import com.quvntvn.carlocator.ui.MainActivity

object FluidCloudNotifier {
    private const val CHANNEL_ID = "fluid_cloud_alerts"

    fun notifyFluidCloudStyle(
        context: Context,
        title: String,
        content: String,
        notificationId: Int,
        smallIconRes: Int = R.drawable.ic_launcher_foreground,
        attemptIntentSimulation: Boolean = true
    ) {
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val caller = Person.Builder()
            .setName(title)
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // Customize the icon for "Car Parked" by passing a different smallIconRes.
            .setSmallIcon(smallIconRes)
            // Customize the text for "Car Parked" by changing title/content arguments.
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    caller,
                    contentIntent
                )
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        if (attemptIntentSimulation) {
            attemptBluetoothConnectedBroadcast(context, title)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.fluid_cloud_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.fluid_cloud_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun attemptBluetoothConnectedBroadcast(context: Context, deviceName: String) {
        // Strategy B (experimental): attempt to simulate a BT connect event locally.
        // On modern HyperOS/MIUI builds this usually won't trigger system UI,
        // but can be helpful for testing your internal receivers.
        val intent = Intent(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
            setPackage(context.packageName)
            putExtra(BluetoothDevice.EXTRA_NAME, deviceName)
        }
        context.sendBroadcast(intent)
    }
}
