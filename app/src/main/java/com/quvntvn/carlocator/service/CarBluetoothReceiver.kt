package com.quvntvn.carlocator.service

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.quvntvn.carlocator.R
import com.quvntvn.carlocator.data.PrefsManager

class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hasBluetoothConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        val action = intent.action
        val device = if (hasBluetoothConnectPermission) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        } else {
            null
        }
        val prefs = PrefsManager(context)
        val selectedMac = prefs.getLastSelectedCarMac()

        if (!prefs.isAppEnabled()) {
            return
        }

        if (selectedMac != null && hasBluetoothConnectPermission && device?.address != null && device.address != selectedMac) {
            return
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
            // Démarrer le service de trajet (Notif Puce Verte)
            val serviceIntent = Intent(context, TripService::class.java).apply {
                // 'this.action' refers to the Intent's action property
                this.action = TripService.ACTION_START
                putExtra(
                    TripService.EXTRA_DEVICE_NAME,
                    if (hasBluetoothConnectPermission) {
                        device?.name
                    } else {
                        null
                    } ?: context.getString(R.string.trip_default_car_name)
                )
                putExtra(TripService.EXTRA_DEVICE_MAC, if (hasBluetoothConnectPermission) device?.address else null)
                putExtra(TripService.EXTRA_NOTIFY_CONNECTED, true)
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
                putExtra(TripService.EXTRA_DEVICE_MAC, if (hasBluetoothConnectPermission) device?.address else null)
            }
            context.startService(serviceIntent)
        }
    }
}
