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
import kotlinx.coroutines.tasks.await

class CarBluetoothReceiver : BroadcastReceiver() {

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

        if (device != null) {
            val dao = AppDatabase.getDatabase(context).carDao()

            if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val savedCar = dao.getCarByMac(device.address) ?: return@launch

                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()

                        val updatedCar = savedCar.copy(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertOrUpdateCar(updatedCar)

                        if (prefs.isParkedNotifEnabled()) {
                            sendNotification(
                                context,
                                context.getString(R.string.notif_parked_title),
                                context.getString(R.string.notif_parked_body, savedCar.name)
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                CoroutineScope(Dispatchers.IO).launch {
                    val savedCar = dao.getCarByMac(device.address)
                    if (savedCar != null && prefs.isConnectionNotifEnabled()) {
                        sendNotification(
                            context,
                            context.getString(R.string.notif_connected_title, savedCar.name),
                            context.getString(R.string.notif_connected_body)
                        )
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