package com.quvntvn.carlocator

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
        val prefs = PrefsManager(context)
        val selectedMac = prefs.getLastSelectedCarMac()

        if (!prefs.isAppEnabled()) {
            return
        }

        if (selectedMac != null && device?.address != null && device.address != selectedMac) {
            return
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
            // Démarrer le service de trajet (Notif Puce Verte)
            val serviceIntent = Intent(context, TripService::class.java).apply {
                // 'this.action' refers to the Intent's action property
                this.action = TripService.ACTION_START
                putExtra(TripService.EXTRA_DEVICE_NAME, device?.name ?: context.getString(R.string.trip_default_car_name))
                putExtra(TripService.EXTRA_DEVICE_MAC, device?.address)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action) {
            // Ordonner au service de sauvegarder et de s'arrêter
            val serviceIntent = Intent(context, TripService::class.java).apply {
                // 'this.action' refers to the Intent's action property
                this.action = TripService.ACTION_STOP_AND_SAVE
                putExtra(TripService.EXTRA_DEVICE_MAC, device?.address)
            }
            context.startService(serviceIntent)
        }
    }
}
