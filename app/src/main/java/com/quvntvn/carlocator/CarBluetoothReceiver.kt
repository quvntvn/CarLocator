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

        // Vérifiez ici si c'est bien votre voiture (par nom ou adresse MAC)
        // if (device?.name != "MaVoiture") return

        if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
            // Démarrer le service de trajet (Notif Puce Verte)
            val serviceIntent = Intent(context, TripService::class.java).apply {
                putExtra("DEVICE_NAME", device?.name ?: "Votre voiture")
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
                this.action = "STOP_AND_SAVE"
            }
            context.startService(serviceIntent)
        }
    }
}