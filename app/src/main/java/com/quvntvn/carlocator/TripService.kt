package com.quvntvn.carlocator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TripService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "STOP_AND_SAVE") {
            handleDisconnection()
            return START_NOT_STICKY
        }

        // Récupérer le nom de la voiture passé par le Receiver
        val deviceName = intent?.getStringExtra("DEVICE_NAME") ?: "Voiture"
        startForeground(1, createNotification(deviceName))

        return START_STICKY // Redémarre le service si le système le tue
    }

    private fun createNotification(deviceName: String): Notification {
        val channelId = "trip_channel"
        val channelName = "Trajet en cours"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Intent pour ouvrir l'app si on clique sur la notif
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("🟢 Connecté à $deviceName") // La puce verte demandée
            .setContentText("Trajet en cours... Localisation prête à être sauvegardée.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Remplacez par votre icône de voiture
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Rend la notif "non enlevable" par l'utilisateur (swipe)
            .build()
    }

    private fun handleDisconnection() {
        // 1. Sauvegarder la position
        val gpsTracker = GpsTracker(this) // Assurez-vous que GpsTracker accepte le context
        gpsTracker.saveParkingLocation()

        // 2. Arrêter le service (ce qui retire la notification)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}