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

class ParkingService : Service() {

    companion object {
        const val ACTION_START = "com.quvntvn.carlocator.ACTION_START"
        const val EXTRA_CAR_ADDRESS = "com.quvntvn.carlocator.EXTRA_CAR_ADDRESS"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "parking_service_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) {
            val carAddress = intent.getStringExtra(EXTRA_CAR_ADDRESS)
            if (carAddress != null) {
                startForegroundWithNotification()
                processParking(carAddress)
            } else {
                stopSelf()
            }
        } else {
            stopSelf()
        }
        return START_REDELIVER_INTENT
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun processParking(carAddress: String) {
        val prefs = PrefsManager(this)
        val dao = AppDatabase.getDatabase(this).carDao()

        CoroutineScope(Dispatchers.IO).launch {
            val car = dao.getCarByMac(carAddress)
            if (car == null) {
                stopSelf()
                return@launch
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this@ParkingService)
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        saveParking(this@ParkingService, dao, car, it.latitude, it.longitude, prefs)
                    }
                    stopSelf()
                }
                .addOnFailureListener {
                    // Fallback to last known location
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                        lastLocation?.let {
                            saveParking(this@ParkingService, dao, car, it.latitude, it.longitude, prefs)
                        }
                        stopSelf()
                    }
                }
        }
    }

    private fun saveParking(context: Context, dao: CarDao, car: CarLocation, lat: Double, lon: Double, prefs: PrefsManager) {
        CoroutineScope(Dispatchers.IO).launch {
            val updatedCar = car.copy(latitude = lat, longitude = lon, timestamp = System.currentTimeMillis())
            dao.insertOrUpdateCar(updatedCar)
            prefs.saveCarLocation(lat, lon)

            if (prefs.isParkedNotifEnabled()) {
                sendNotification(context,
                    context.getString(R.string.notif_parked_title),
                    context.getString(R.string.notif_parked_body, car.name),
                    car.macAddress)
            }
        }
    }

    private fun sendNotification(context: Context, title: String, content: String, carAddress: String) {
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

        manager.notify(carAddress.hashCode(), notification)
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