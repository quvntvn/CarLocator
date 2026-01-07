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
import android.bluetooth.BluetoothManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Ce Receiver gère TOUT : Connexion (Notif) et Déconnexion (GPS + Notif)
class CarBluetoothReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        if (device != null) {
            val pendingResult = goAsync()
            val db = AppDatabase.getDatabase(context)
            val scope = CoroutineScope(Dispatchers.IO)

            scope.launch {
                try {
                    val allCars = db.carDao().getAllCarsList()
                    val car = allCars.find { it.macAddress.equals(device.address, ignoreCase = true) }

                    if (car != null) {
                        when (action) {
                            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                                Log.d("CarLocator", "🟢 Connecté à ${car.name}")
                                sendNotification(context, "Voiture Connectée 🟢", "Connecté à ${car.name}", car.macAddress.hashCode())
                                pendingResult.finish()
                            }
                            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                Log.d("CarLocator", "🔴 Déconnecté de ${car.name}. Recherche GPS...")
                                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                                    if (location != null) {
                                        scope.launch {
                                            val updatedCar = car.copy(
                                                latitude = location.latitude,
                                                longitude = location.longitude,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            db.carDao().insertOrUpdateCar(updatedCar)
                                            sendNotification(context, "Voiture Garée 📍", "Position de ${car.name} enregistrée.", car.macAddress.hashCode(), true)
                                            pendingResult.finish()
                                        }
                                    } else {
                                        pendingResult.finish()
                                    }
                                }.addOnFailureListener { e ->
                                    Log.e("CarLocator", "Erreur de localisation: ${e.message}")
                                    pendingResult.finish()
                                }
                            }
                            else -> pendingResult.finish()
                        }
                    } else {
                        pendingResult.finish()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    pendingResult.finish()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkInitialConnectionState(context: Context, cars: List<CarLocation>) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) return

        val connectedDevices = adapter.bondedDevices
        for (device in connectedDevices) {
            val car = cars.find { it.macAddress.equals(device.address, ignoreCase = true) }
            if (car != null) {
                // If a car is found among bonded (and likely connected) devices,
                // consider it connected.
                Log.d("CarLocator", "Vérification initiale: ${car.name} est déjà connecté.")
                // Optionally send a notification or update UI state here
            }
        }
    }
    private fun sendNotification(context: Context, title: String, content: String, notifId: Int, showAction: Boolean = false) {
        val channelId = "car_locator_channel_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Statut Voiture", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Priorité standard (pas haute)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Affiche le contenu sur l'écran verrouillé
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (showAction) {
            builder.addAction(android.R.drawable.ic_menu_directions, "VOIR", pendingIntent)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }


}
