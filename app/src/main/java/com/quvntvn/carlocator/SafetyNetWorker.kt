package com.quvntvn.carlocator

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.core.app.ActivityCompat

class SafetyNetWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Vérifie si le Bluetooth est allumé
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return Result.success()
        }

        // Vérifie si un appareil Audio (A2DP) ou Casque (Headset) est connecté globalement
        val a2dpState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP)
        val headsetState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)

        val isConnected = (a2dpState == BluetoothProfile.STATE_CONNECTED || headsetState == BluetoothProfile.STATE_CONNECTED)

        if (isConnected) {
            Log.d("CarLocator", "SafetyNet: Bluetooth connecté détecté. Relance du service si nécessaire.")
            val prefs = PrefsManager(applicationContext)
            val macAddress = prefs.getLastSelectedCarMac()
            if (macAddress == null) {
                return Result.success()
            }

            // On lance le service en mode "startForeground" (le service gère lui-même s'il tourne déjà)
            val intent = Intent(applicationContext, TripService::class.java).apply {
                action = TripService.ACTION_START
                putExtra(TripService.EXTRA_DEVICE_MAC, macAddress)
                putExtra(TripService.EXTRA_NOTIFY_CONNECTED, true)
            }
            // Important : sur Android 8+, on doit utiliser startForegroundService si l'app est en background
            try {
                applicationContext.startForegroundService(intent)
            } catch (e: Exception) {
                // Fallback pour les anciennes versions ou cas particuliers
                applicationContext.startService(intent)
            }
        }

        return Result.success()
    }
}
