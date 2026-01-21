package com.quvntvn.carlocator.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.quvntvn.carlocator.R
import com.quvntvn.carlocator.data.AppDatabase
import com.quvntvn.carlocator.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarBluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleReceive(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReceive(context: Context, intent: Intent) {
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

        if (!prefs.isAppEnabled()) {
            return
        }

        if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                val lastConnectedMac = prefs.getLastConnectedCarMac()
                val fallbackMac = prefs.getLastSelectedCarMac()
                val resolvedMac = lastConnectedMac ?: fallbackMac
                if (!isTrackedCar(context, resolvedMac)) {
                    return
                }
                val serviceIntent = Intent(context, TripService::class.java).apply {
                    this.action = TripService.ACTION_STOP_AND_SAVE
                    putExtra(TripService.EXTRA_DEVICE_MAC, resolvedMac)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            return
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED == action ||
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED == action ||
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED == action
        ) {
            val connectionState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            if (action != BluetoothDevice.ACTION_ACL_CONNECTED && connectionState != BluetoothProfile.STATE_CONNECTED) {
                return
            }
            if (!hasBluetoothConnectPermission || device?.address == null) {
                return
            }
            if (!isTrackedCar(context, device.address)) {
                return
            }
            prefs.saveLastConnectedCarMac(device.address)
            // Démarrer le service de trajet (Notif Puce Verte)
            val serviceIntent = Intent(context, TripService::class.java).apply {
                // 'this.action' refers to the Intent's action property
                this.action = TripService.ACTION_START
                putExtra(
                    TripService.EXTRA_DEVICE_NAME,
                    device.name ?: context.getString(R.string.trip_default_car_name)
                )
                putExtra(TripService.EXTRA_DEVICE_MAC, device.address)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action ||
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED == action ||
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED == action
        ) {
            val connectionState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
            if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
                connectionState != BluetoothProfile.STATE_DISCONNECTED
            ) {
                return
            }
            if (!hasBluetoothConnectPermission || device?.address == null) {
                return
            }
            if (!isTrackedCar(context, device.address)) {
                return
            }
            prefs.saveLastConnectedCarMac(device.address)
            // Ordonner au service de sauvegarder et de s'arrêter
            val serviceIntent = Intent(context, TripService::class.java).apply {
                // 'this.action' refers to the Intent's action property
                this.action = TripService.ACTION_STOP_AND_SAVE
                putExtra(TripService.EXTRA_DEVICE_MAC, device.address)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    private suspend fun isTrackedCar(context: Context, macAddress: String?): Boolean {
        if (macAddress.isNullOrBlank()) {
            return false
        }
        val db = AppDatabase.getInstance(context)
        val savedCars = db.carDao().getAllCarsOnce()
        return savedCars.any { it.macAddress.equals(macAddress, ignoreCase = true) }
    }
}
