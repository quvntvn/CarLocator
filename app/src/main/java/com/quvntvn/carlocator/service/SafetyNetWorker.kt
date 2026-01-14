package com.quvntvn.carlocator.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.quvntvn.carlocator.data.PrefsManager

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

        val prefs = PrefsManager(applicationContext)
        val macAddress = prefs.getLastSelectedCarMac() ?: return Result.success()
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return Result.success()
        val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP) +
            bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET)
        val isCarConnected = connectedDevices.any { it.address == macAddress }
        if (!isCarConnected) {
            return Result.success()
        }

        Log.d("CarLocator", "SafetyNet: Voiture connectée détectée. Relance du service si nécessaire.")
        // On lance le service en mode "startForeground" (le service gère lui-même s'il tourne déjà)
        val intent = Intent(applicationContext, TripService::class.java).apply {
            action = TripService.ACTION_START
            putExtra(TripService.EXTRA_DEVICE_MAC, macAddress)
        }
        // Important : sur Android 8+, on doit utiliser startForegroundService si l'app est en background
        try {
            applicationContext.startForegroundService(intent)
        } catch (e: Exception) {
            // Fallback pour les anciennes versions ou cas particuliers
            applicationContext.startService(intent)
        }

        return Result.success()
    }
}
