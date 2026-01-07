package com.quvntvn.carlocator

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BluetoothForegroundService : Service() {

    private var isReceiverRegistered = false

    // Le BroadcastReceiver est maintenant une classe interne au service.
    private val bluetoothReceiver = object : BroadcastReceiver() {
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
                                    ConnectionState.onConnected(car.name)
                                    Log.d("CarLocator", "SERVICE: 🟢 Connecté à ${car.name}")
                                    sendNotification(context, "Voiture Connectée 🟢", "Connecté à ${car.name}", car.macAddress.hashCode())
                                }
                                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                    ConnectionState.onDisconnected()
                                    Log.d("CarLocator", "SERVICE: 🔴 Déconnecté de ${car.name}. Recherche GPS...")
                                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

                                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                        pendingResult.finish()
                                        return@launch
                                    }

                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                                        .addOnSuccessListener { location: Location? ->
                                            if (location != null) {
                                                scope.launch {
                                                    try {
                                                        val updatedCar = car.copy(
                                                            latitude = location.latitude,
                                                            longitude = location.longitude,
                                                            timestamp = System.currentTimeMillis()
                                                        )
                                                        db.carDao().insertOrUpdateCar(updatedCar)
                                                        sendNotification(context, "Voiture Garée 📍", "Position de ${car.name} enregistrée.", car.macAddress.hashCode(), true)
                                                    } finally {
                                                        pendingResult.finish()
                                                    }
                                                }
                                            } else {
                                                pendingResult.finish()
                                            }
                                        }
                                        .addOnFailureListener {
                                            pendingResult.finish()
                                        }
                                }
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
    }

    override fun onCreate() {
        super.onCreate()
        startMyForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            isReceiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMyForeground() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        val channelId = "car_locator_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CarLocator Service",
                NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CarLocator")
            .setContentText("Protection active")
            .setSmallIcon(R.drawable.ic_map_pin) // Utilisons une icône plus appropriée
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
            registerBluetoothReceiver()
        } catch (e: Exception) {
            Log.e("CarLocator", "Erreur fatale startForeground : ${e.message}")
            stopSelf()
        }
    }

    private fun registerBluetoothReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                }
                registerReceiver(bluetoothReceiver, filter)
                isReceiverRegistered = true
                Log.d("CarLocator", "SERVICE: Récepteur Bluetooth enregistré.")
            } catch (e: Exception) {
                Log.e("CarLocator", "Erreur registerReceiver: ${e.message}")
            }
        }
    }

    private fun sendNotification(context: Context, title: String, content: String, notifId: Int, showAction: Boolean = false) {
        val channelId = "car_locator_channel_v2"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Statut Voiture", NotificationManager.IMPORTANCE_DEFAULT)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_map_pin)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
