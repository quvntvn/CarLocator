package com.quvntvn.carlocator

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarDisconnectReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val disconnectedMac = device?.address

            if (disconnectedMac != null) {
                saveLocationIfCarExists(context, disconnectedMac)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveLocationIfCarExists(context: Context, macAddress: String) {
        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            // 1. On vérifie si c'est une de nos voitures
            val car = db.carDao().getCarByMac(macAddress)

            if (car != null) {
                Log.d("CarLocator", "🚗 Déconnexion de ${car.name}. Recherche GPS...")

                // 2. On récupère la position GPS
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        scope.launch {
                            // 3. Sauvegarde en DB
                            val updatedCar = car.copy(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = System.currentTimeMillis()
                            )
                            db.carDao().insertOrUpdateCar(updatedCar)
                            Log.d("CarLocator", "✅ Position sauvegardée !")

                            // 4. ENVOI DE LA NOTIFICATION
                            sendParkingNotification(context, updatedCar)
                        }
                    }
                }
            }
        }
    }

    private fun sendParkingNotification(context: Context, car: CarLocation) {
        val channelId = "car_locator_channel"

        // 1. Créer le canal de notification (Obligatoire pour Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Position Voiture"
            val descriptionText = "Notifications quand la voiture est garée"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Créer l'action du bouton "Voir" (Ouvrir l'app)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Construire la notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map) // Icône par défaut (tu pourras mettre la tienne)
            .setContentTitle("Voiture Garée 📍")
            .setContentText("La position de ${car.name} a été enregistrée.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Clic sur la notif ouvre l'app
            .addAction(android.R.drawable.ic_menu_directions, "VOIR L'EMPLACEMENT", pendingIntent) // Le bouton d'action

        // 4. Afficher la notification (Si permission accordée)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(car.macAddress.hashCode(), builder.build())
        }
    }
}