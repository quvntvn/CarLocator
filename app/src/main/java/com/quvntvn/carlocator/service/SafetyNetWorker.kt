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
import com.quvntvn.carlocator.data.AppDatabase
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
        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return Result.success()
        val connectedDevices = bluetoothManager.getConnectedDevices(BluetoothProfile.A2DP) +
            bluetoothManager.getConnectedDevices(BluetoothProfile.HEADSET)
        if (connectedDevices.isEmpty()) {
            return Result.success()
        }

        val db = AppDatabase.getInstance(applicationContext)
        val savedCars = db.carDao().getAllCarsOnce()
        if (savedCars.isEmpty()) {
            return Result.success()
        }

        val matchedDevice = connectedDevices.firstOrNull { device ->
            savedCars.any { it.macAddress.equals(device.address, ignoreCase = true) }
        } ?: return Result.success()

        val matchedCar = savedCars.first { it.macAddress.equals(matchedDevice.address, ignoreCase = true) }
        prefs.saveLastConnectedCarMac(matchedCar.macAddress)

        Log.d("CarLocator", "SafetyNet: Voiture connectée détectée. Relance du service si nécessaire.")
        // On lance le service en mode "startForeground" (le service gère lui-même s'il tourne déjà)
        val intent = Intent(applicationContext, TripService::class.java).apply {
            action = TripService.ACTION_START
            putExtra(TripService.EXTRA_DEVICE_MAC, matchedCar.macAddress)
            putExtra(TripService.EXTRA_DEVICE_NAME, matchedCar.name)
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
