package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarBluetoothReceiver : BroadcastReceiver() {

    // Sécurité pour éviter les doublons (certains appareils envoient 2 signaux à la suite)
    companion object {
        private var lastProcessedAddress: String? = null
        private var lastProcessedAction: String? = null
        private var lastProcessedTime: Long = 0
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PrefsManager(context)
        if (!prefs.isAppEnabled()) return

        val action = intent.action
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        if (device != null && action != null) {
            // Logique anti-doublon : Ignorer si même appareil + même action en moins de 2 secondes
            val currentTime = System.currentTimeMillis()
            if (device.address == lastProcessedAddress &&
                action == lastProcessedAction &&
                (currentTime - lastProcessedTime) < 2000) {
                return
            }

            // Mémoriser le dernier événement traité
            lastProcessedAddress = device.address
            lastProcessedAction = action
            lastProcessedTime = currentTime

            val database = AppDatabase.getDatabase(context)
            val dao = database.carDao()

            CoroutineScope(Dispatchers.IO).launch {
                val savedCar = dao.getCarByMac(device.address)
                if (savedCar == null) return@launch

                // CAS 1 : CONNEXION
                if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                    if (prefs.isConnectionNotifEnabled()) {
                        sendNotification(context,
                            context.getString(R.string.notif_connected_title, savedCar.name),
                            context.getString(R.string.notif_connected_body))
                    }
                }

                // CAS 2 : DÉCONNEXION (SAUVEGARDE PARKING)
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                    // On demande la dernière position connue (plus rapide)
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            saveParking(context, dao, savedCar, location.latitude, location.longitude, prefs)
                        } else {
                            // Si lastLocation est nulle, on tente une requête de position fraîche
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { freshLocation ->
                                    freshLocation?.let {
                                        saveParking(context, dao, savedCar, it.latitude, it.longitude, prefs)
                                    }
                                }
                        }
                    }
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
                    context.getString(R.string.notif_parked_body, car.name))
            }
        }
    }

    private fun sendNotification(context: Context, title: String, content: String) {
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(deviceAddressToId(lastProcessedAddress), notification)
    }

    // Génère un ID unique par voiture pour éviter d'écraser les notifs si plusieurs voitures bougent
    private fun deviceAddressToId(address: String?): Int {
        return address?.hashCode() ?: System.currentTimeMillis().toInt()
    }
}