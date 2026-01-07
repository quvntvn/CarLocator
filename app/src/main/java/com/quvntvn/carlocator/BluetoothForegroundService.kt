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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class BluetoothForegroundService : Service() {

    private val receiver = CarBluetoothReceiver()
    private var isReceiverRegistered = false

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
            unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

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
                // IMPORTANT : IMPORTANCE_MIN rend la notif silencieuse et minimisée
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CarLocator")
            .setContentText("Protection active") // Texte plus court et moins "technique"
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            // Priorité minimale pour ne pas déranger l'utilisateur
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
                registerReceiver(receiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.e("CarLocator", "Erreur registerReceiver: ${e.message}")
            }
        }
    }
}