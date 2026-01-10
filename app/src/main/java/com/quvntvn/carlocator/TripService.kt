package com.quvntvn.carlocator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TripService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP_AND_SAVE = "ACTION_STOP_AND_SAVE"
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
        const val EXTRA_DEVICE_MAC = "EXTRA_DEVICE_MAC"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trip_channel"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext.cancelChildren()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = PrefsManager(applicationContext)
        if (!prefs.isAppEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP_AND_SAVE) {
            val macAddress = intent?.getStringExtra(EXTRA_DEVICE_MAC)
            serviceScope.launch {
                handleDisconnection(macAddress)
            }
            return START_NOT_STICKY
        }

        // Récupérer le nom de la voiture passé par le Receiver
        val macAddress = intent?.getStringExtra(EXTRA_DEVICE_MAC)
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        val resolvedName = deviceName ?: getString(R.string.trip_default_car_name)
        startForeground(NOTIFICATION_ID, createNotification(resolvedName))

        if (deviceName == null && macAddress != null) {
            serviceScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val car = db.carDao().getCarByMac(macAddress)
                if (car != null) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, createNotification(car.name))
                }
            }
        }

        return START_STICKY
    }

    private fun createNotification(deviceName: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.trip_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Intent pour ouvrir l'app si on clique sur la notif
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.trip_notif_title, deviceName))
            .setContentText(getString(R.string.trip_notif_body))
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Remplacez par votre icône de voiture
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Rend la notif "non enlevable" par l'utilisateur (swipe)
            .build()
    }

    private suspend fun handleDisconnection(macAddress: String?) {
        val prefs = PrefsManager(applicationContext)
        val resolvedMac = macAddress ?: prefs.getLastSelectedCarMac()
        if (resolvedMac == null) {
            stopTripService()
            return
        }

        val db = AppDatabase.getInstance(applicationContext)
        val car = db.carDao().getCarByMac(resolvedMac)
        if (car == null) {
            stopTripService()
            return
        }

        val tracker = GpsTracker(this)
        val location = tracker.getLocation()
        if (location != null) {
            val updatedCar = car.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )
            db.carDao().insertOrUpdateCar(updatedCar)

            sendNotification(
                title = getString(R.string.notif_parked_title),
                content = getString(R.string.notif_parked_body, car.name),
                notificationId = car.macAddress.hashCode()
            )
        }

        stopTripService()
    }

    private suspend fun stopTripService() {
        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun sendNotification(title: String, content: String, notificationId: Int) {
        val channelId = "car_locator_events"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }
}
