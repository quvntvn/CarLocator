package com.quvntvn.carlocator.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.quvntvn.carlocator.R
import com.quvntvn.carlocator.data.AppDatabase
import com.quvntvn.carlocator.data.PrefsManager
import com.quvntvn.carlocator.ui.MainActivity
import com.quvntvn.carlocator.utils.GpsTracker
import com.quvntvn.carlocator.utils.HyperIslandHelper
import com.quvntvn.carlocator.utils.SpeedFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
        // Rafraîchissement de la vitesse dans la pastille (~2x/s).
        private const val SPEED_UPDATE_INTERVAL_MS = 500L
        // Durée d'affichage de la confirmation "📍 Garée" dans la pastille après sauvegarde.
        private const val SAVED_PILL_DURATION_MS = 2_000L
        // Indicateur "connecté" affiché dans la pastille quand la vitesse est masquée (option Off).
        private const val CONNECTED_DOT = "🟢"
        @Volatile
        private var isTripActive = false
        private val _isActive = MutableStateFlow(false)
        /** État réactif du suivi de trajet, observé par l'UI (bouton « Reprendre le trajet »). */
        val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
        private const val EVENT_DEDUP_WINDOW_MS = 2_000L
        private var lastEvent: TripEvent? = null
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }
    @Volatile
    private var currentSpeedMs: Float? = null
    @Volatile
    private var tripDeviceName: String? = null
    private var locationUpdatesActive = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentSpeedMs = if (location.hasSpeed()) location.speed else null
            // Écran éteint / Always-On Display : on ne rafraîchit pas la pastille pour éviter
            // qu'elle s'actualise en continu sur l'AOD. La maj reprend dès l'écran rallumé.
            if (isScreenInteractive()) {
                refreshTripNotification()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.coroutineContext.cancelChildren()
        isTripActive = false
        _isActive.value = false
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
            stopLocationUpdates()
            startForegroundWithTypes(createParkingNotification())
            serviceScope.launch {
                handleDisconnection(macAddress)
            }
            return START_NOT_STICKY
        }

        // Récupérer le nom de la voiture passé par le Receiver
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME)
        val resolvedName = deviceName ?: getString(R.string.trip_default_car_name)
        tripDeviceName = resolvedName
        val wasActive = isTripActive
        if (!wasActive) {
            isTripActive = true
        }
        _isActive.value = true
        startForegroundWithTypes(createNotification(resolvedName))

        // Vitesse en direct dans la pastille uniquement quand la feature est active.
        if (shouldTrackSpeed(prefs)) {
            startLocationUpdates()
        }

        if (deviceName == null && macAddress != null) {
            serviceScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val car = db.carDao().getCarByMac(macAddress)
                if (car != null) {
                    tripDeviceName = car.name
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
        val islandEnabled = prefs.isHyperIslandEnabled()
        // Chemin moderne : Live Update Android 16 (toutes marques, HyperOS 3.1+, sans whitelist Xiaomi).
        val liveUpdateWanted = tripVisible && islandEnabled && HyperIslandHelper.supportsLiveUpdate()
        // Chemin legacy : extras miui.focus.* pour HyperOS < 3.1 (nécessite whitelist Xiaomi).
        val legacyFocusWanted = tripVisible && islandEnabled && HyperIslandHelper.isAvailable()

        val speedSetting = SpeedFormatter.Setting.fromPref(prefs.getSpeedUnit())
        val showSpeed = liveUpdateWanted && speedSetting != SpeedFormatter.Setting.OFF
        val speedUnit = SpeedFormatter.resolveUnit(speedSetting, Locale.getDefault())

        ensureTripChannels()

        // Choix du canal : Focus (HyperIsland/Live Update) > visible LOW > caché MIN.
        val targetChannelId = when {
            tripVisible && (liveUpdateWanted || legacyFocusWanted) -> {
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
        // En direct : la vue déployée montre "42 km/h". Sinon le corps habituel.
        val bodyText = when {
            !tripVisible -> getString(R.string.trip_silent_body)
            showSpeed -> SpeedFormatter.full(currentSpeedMs, speedUnit)
            else -> getString(R.string.trip_notif_body)
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

        // Android 16+ : API publique (Pixel, Samsung, HyperOS 3.1…).
        // Texte court = vitesse + unité ; option "Off" -> pas de texte (icône seule dans la pastille).
        if (liveUpdateWanted) {
            HyperIslandHelper.applyLiveUpdate(
                builder,
                // Vitesse affichée -> "42 km/h" ; option Off -> juste le point "connecté" 🟢.
                shortText = if (showSpeed) SpeedFormatter.pill(currentSpeedMs, speedUnit) else CONNECTED_DOT
            )
        }
        // Fallback HyperOS plus ancien : extras propriétaires miui.focus.*.
        if (legacyFocusWanted) {
            HyperIslandHelper.applyFocusExtras(builder, titleText, bodyText, ongoing = true)
        }

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        return notification
    }

    /** Vrai quand la notif de trajet en direct (Live Update) est active : on suit alors la vitesse. */
    private fun shouldTrackSpeed(prefs: PrefsManager): Boolean =
        prefs.isTripNotifEnabled() && prefs.isHyperIslandEnabled() &&
            HyperIslandHelper.supportsLiveUpdate() &&
            SpeedFormatter.Setting.fromPref(prefs.getSpeedUnit()) != SpeedFormatter.Setting.OFF

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun startLocationUpdates() {
        if (locationUpdatesActive) return
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SPEED_UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(SPEED_UPDATE_INTERVAL_MS)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            locationUpdatesActive = true
        } catch (e: SecurityException) {
            // Permission révoquée entre-temps : on reste sans vitesse, sans crasher.
        }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesActive) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        locationUpdatesActive = false
        currentSpeedMs = null
    }

    private fun refreshTripNotification() {
        if (!isTripActive) return
        val name = tripDeviceName ?: getString(R.string.trip_default_car_name)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(name))
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
            .setSmallIcon(R.drawable.ic_notif_pin)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /** Notification promue (pastille) "📍 Garée" affichée brièvement après la sauvegarde. */
    private fun buildSavedPill(name: String): Notification {
        val prefs = PrefsManager(applicationContext)
        val promote = prefs.isTripNotifEnabled() && prefs.isHyperIslandEnabled() &&
            HyperIslandHelper.supportsLiveUpdate()
        ensureTripChannels()
        val channelId = if (promote) {
            HyperIslandHelper.ensureFocusChannel(this, getString(R.string.trip_notification_channel_name))
            HyperIslandHelper.FOCUS_CHANNEL_ID
        } else {
            CHANNEL_ID
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_parked_title))
            .setContentText(getString(R.string.notif_parked_body, name))
            .setSmallIcon(R.drawable.ic_notif_pin)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (promote) {
            HyperIslandHelper.applyLiveUpdate(builder, shortText = getString(R.string.notif_parked_pill))
        }
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
        return notification
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

            // Confirmation brève "📍 Garée" dans la pastille, puis arrêt du service.
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildSavedPill(car.name))
            delay(SAVED_PILL_DURATION_MS)
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
            .setSmallIcon(R.drawable.ic_notif_pin)
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
