package com.quvntvn.carlocator

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BluetoothForegroundService : Service() {

    private val receiver = CarBluetoothReceiver()
    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        startMyForeground()
        // On n'enregistre le receiver que si on a réussi à démarrer le service foreground
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Si le service est tué, on essaie de le redémarrer
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startMyForeground() {
        // SÉCURITÉ ANDROID 14 : Vérification de la permission AVANT de lancer startForeground
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Si on n'a pas la permission, on arrête le service immédiatement pour éviter le crash
            stopSelf()
            return
        }

        val channelId = "car_locator_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "CarLocator Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CarLocator Actif")
            .setContentText("Surveillance Bluetooth en cours...")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Sur Android 10+, on doit spécifier le type de service (Location)
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }

            // Si on arrive ici, le service tourne, on peut écouter le Bluetooth
            registerBluetoothReceiver()

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun registerBluetoothReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            registerReceiver(receiver, filter)
            isReceiverRegistered = true
        }
    }
}