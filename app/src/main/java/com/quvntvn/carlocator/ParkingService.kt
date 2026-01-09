package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParkingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var car: CarLocation? = null

    companion object {
        const val ACTION_CONNECTED = "ACTION_CONNECTED"
        const val ACTION_DISCONNECTED = "ACTION_DISCONNECTED"
        const val EXTRA_MAC_ADDRESS = "EXTRA_MAC_ADDRESS"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "parking_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val macAddress = intent?.getStringExtra(EXTRA_MAC_ADDRESS)

        if (macAddress == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            car = db.carDao().getCarByMac(macAddress)

            if (car == null) {
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
                return@launch
            }

            when (action) {
                ACTION_CONNECTED -> handleConnection()
                ACTION_DISCONNECTED -> handleDisconnection()
            }
        }

        return START_REDELIVER_INTENT
    }

    private fun handleConnection() {
        val prefs = PrefsManager(applicationContext)
        if (prefs.isConnectionNotifEnabled()) {
            sendNotification(
                context = this,
                title = getString(R.string.notif_connected_title, car!!.name),
                content = getString(R.string.notif_connected_body),
                notificationId = car!!.macAddress.hashCode()
            )
        }
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleDisconnection() {
        startForegroundWithNotification()

        val tracker = GpsTracker(this)
        val location = tracker.getLocation()

        if (location != null) {
            val db = AppDatabase.getDatabase(applicationContext)
            val updatedCar = car!!.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )
            db.carDao().insertOrUpdateCar(updatedCar)

            val prefs = PrefsManager(applicationContext)
            if (prefs.isParkedNotifEnabled()) {
                sendNotification(
                    context = this,
                    title = getString(R.string.notif_parked_title),
                    content = getString(R.string.notif_parked_body, car!!.name),
                    notificationId = car!!.macAddress.hashCode()
                )
            }
        }

        withContext(Dispatchers.Main) {
            stopSelf()
        }
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.parking_service_notif_title))
            .setContentText(getString(R.string.parking_service_notif_body))
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
    }

    private fun sendNotification(context: Context, title: String, content: String, notificationId: Int) {
        val channelId = "car_locator_events"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Parking Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
}