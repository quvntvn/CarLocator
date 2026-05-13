package com.quvntvn.carlocator.service

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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.quvntvn.carlocator.R
import com.quvntvn.carlocator.data.AppDatabase
import com.quvntvn.carlocator.data.PrefsManager
import com.quvntvn.carlocator.ui.MainActivity
import com.quvntvn.carlocator.utils.GpsTracker
import com.quvntvn.carlocator.utils.HyperIslandHelper
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
        private const val CHANNEL_ID_HIDDEN = "trip_hidden_channel"
        private const val CHANNEL_ID_PARKED = "car_parked_v2"
        @Volatile
        private var isTripActive = false
        private const val EVENT_DEDUP_WINDOW_MS = 2_000L
        private var lastEvent: TripEvent? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext.cancelChildren()
        isTripActive = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = PrefsManager(applicationContext)
        if (!prefs.isAppEnabled()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent?.action ?: ACTION_START
        val macAddress = intent?.getStringExtra(EXTRA_DEVICE_MAC)
        if (action == ACTION_START && macAddress != null) {
            prefs.saveLastConnectedCarMac(macAddress)
        }
        if (!shouldProcessEvent(action, macAddress)) {
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP_AND_SAVE) {
            startForegroundWithTypes(createParkingNotification())
            serviceScope.launch {
                handleDisconnection(macAddress)
            }
            return START_NOT_STICKY
        }

        // Récupérer le nom de la voiture passé par le Receiver
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        val resolvedName = deviceName ?: getString(R.string.trip_default_car_name)
        val wasActive = isTripActive
        if (!wasActive) {
            isTripActive = true
        }
        startForegroundWithTypes(createNotification(resolvedName))

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
        val prefs = PrefsManager(applicationContext)
        val tripVisible = prefs.isTripNotifEnabled()
        val hyperIslandWanted = prefs.isHyperIslandEnabled() && HyperIslandHelper.isAvailable()

        ensureTripChannels()

        // Choix du canal : Focus (HyperIsland) > visible LOW > caché MIN.
        val targetChannelId = when {
            tripVisible && hyperIslandWanted -> {
                HyperIslandHelper.ensureFocusChannel(this, getString(R.string.trip_notification_channel_name))
                HyperIslandHelper.FOCUS_CHANNEL_ID
            }
            tripVisible -> CHANNEL_ID
            else -> CHANNEL_ID_HIDDEN
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopTripIntent = Intent(this, TripService::class.java).apply {
            action = ACTION_STOP_AND_SAVE
        }
        val stopTripPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, 1, stopTripIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(this, 1, stopTripIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val titleText = if (tripVisible) {
            getString(R.string.trip_notif_title, deviceName)
        } else {
            getString(R.string.trip_silent_title)
        }
        val bodyText = if (tripVisible) {
            getString(R.string.trip_notif_body)
        } else {
            getString(R.string.trip_silent_body)
        }

        val builder = NotificationCompat.Builder(this, targetChannelId)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setSmallIcon(R.drawable.ic_notif_car)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(if (tripVisible) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)

        if (tripVisible) {
            builder.addAction(R.drawable.ic_notif_car, getString(R.string.trip_stop_action), stopTripPendingIntent)
        }

        if (tripVisible && hyperIslandWanted) {
            HyperIslandHelper.applyFocusExtras(builder, titleText, bodyText, ongoing = true)
        }

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        return notification
    }

    private fun ensureTripChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.trip_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_ID_HIDDEN) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_HIDDEN,
                    getString(R.string.trip_hidden_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }

    private fun startForegroundWithTypes(notification: Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val baseType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                baseType
            } else {
                baseType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            }
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
    }

    private fun createParkingNotification(): Notification {
        val channelId = "parking_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.parking_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.parking_service_notif_title))
            .setContentText(getString(R.string.parking_service_notif_body))
            .setSmallIcon(R.drawable.ic_notif_car)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private suspend fun handleDisconnection(macAddress: String?) {
        val prefs = PrefsManager(applicationContext)
        val resolvedMac = macAddress ?: prefs.getLastConnectedCarMac() ?: prefs.getLastSelectedCarMac()
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

        val tracker = GpsTracker(this, requireBackgroundPermission = false)
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
        val prefs = PrefsManager(applicationContext)
        if (!prefs.isParkedNotifEnabled()) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID_PARKED) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_PARKED,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_PARKED)
            .setSmallIcon(R.drawable.ic_notif_car)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (prefs.isHyperIslandEnabled()) {
            HyperIslandHelper.applyFocusExtras(builder, title, content, ongoing = false)
        }

        manager.notify(notificationId, builder.build())
    }

    @Synchronized
    private fun shouldProcessEvent(action: String?, macAddress: String?): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastEvent
        if (last != null &&
            last.action == action &&
            last.macAddress == macAddress &&
            now - last.timestampMs < EVENT_DEDUP_WINDOW_MS
        ) {
            return false
        }
        lastEvent = TripEvent(action, macAddress, now)
        return true
    }

    private data class TripEvent(
        val action: String?,
        val macAddress: String?,
        val timestampMs: Long
    )
}
