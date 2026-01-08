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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarBluetoothReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PrefsManager(context)

        // 1. MASTER SWITCH : Si l'app est désactivée, on stoppe tout
        if (!prefs.isAppEnabled()) return

        val action = intent.action
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        if (device != null) {
            val database = AppDatabase.getDatabase(context)
            val dao = database.carDao()

            CoroutineScope(Dispatchers.IO).launch {
                val savedCar = dao.getCarByMac(device.address)

                // Si ce n'est pas une de nos voitures enregistrées, on ignore
                if (savedCar == null) return@launch

                // CAS 1 : CONNEXION
                if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                    if (prefs.isConnectionNotifEnabled()) {
                        sendNotification(context,
                            context.getString(R.string.notif_connected_title, savedCar.name),
                            context.getString(R.string.notif_connected_body))
                    }
                }

                // CAS 2 : DÉCONNEXION (C'est ici que l'on enregistre le parking)
                if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
                    // Utilisation directe du FusedLocationProvider pour plus de fiabilité en tâche de fond
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val updatedCar = savedCar.copy(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = System.currentTimeMillis()
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                dao.insertOrUpdateCar(updatedCar)
                                prefs.saveCarLocation(location.latitude, location.longitude)

                                if (prefs.isParkedNotifEnabled()) {
                                    sendNotification(context,
                                        context.getString(R.string.notif_parked_title),
                                        context.getString(R.string.notif_parked_body, savedCar.name))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sendNotification(context: Context, title: String, content: String) {
        val channelId = "car_locator_events"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}