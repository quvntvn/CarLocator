package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

        // Constante pour l'action personnalisée
        const val ACTION_FORCE_CONNECT = "com.quvntvn.carlocator.ACTION_FORCE_CONNECT"
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PrefsManager(context)
        if (!prefs.isAppEnabled()) return

        val intentAction = intent.action
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        if (device != null && intentAction != null) {
            // Logique anti-doublon : Ignorer si même appareil + même action en moins de 2 secondes
            val currentTime = System.currentTimeMillis()
            if (device.address == lastProcessedAddress &&
                intentAction == lastProcessedAction &&
                (currentTime - lastProcessedTime) < 2000) {
                return
            }

            // Mémoriser le dernier événement traité
            lastProcessedAddress = device.address
            lastProcessedAction = intentAction
            lastProcessedTime = currentTime

            val database = AppDatabase.getDatabase(context)
            val dao = database.carDao()

            CoroutineScope(Dispatchers.IO).launch {
                val savedCar = dao.getCarByMac(device.address)
                if (savedCar == null) return@launch

                // CAS 1 : CONNEXION
                // On accepte soit l'événement système officiel, soit notre action forcée manuellement
                if (BluetoothDevice.ACTION_ACL_CONNECTED == intentAction || ACTION_FORCE_CONNECT == intentAction) {
                    if (prefs.isConnectionNotifEnabled()) {
                        sendNotification(context,
                            context.getString(R.string.notif_connected_title, savedCar.name),
                            context.getString(R.string.notif_connected_body))
                    }
                }

                // CAS 2 : DÉCONNEXION (SAUVEGARDE PARKING) -> Géré par CompanionDeviceService
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

        manager.notify(deviceAddressToId(lastProcessedAddress), notification)
    }

    // Génère un ID unique par voiture pour éviter d'écraser les notifs si plusieurs voitures bougent
    private fun deviceAddressToId(address: String?): Int {
        return address?.hashCode() ?: System.currentTimeMillis().toInt()
    }
}