package com.quvntvn.carlocator.service

import android.Manifest
import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.quvntvn.carlocator.data.PrefsManager

@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
class MyCompanionDeviceService : CompanionDeviceService() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture détectée (CDM) : ${associationInfo.deviceMacAddress}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!PrefsManager(this).isAppEnabled()) {
            return
        }

        val serviceIntent = Intent(this, TripService::class.java).apply {
            action = TripService.ACTION_START
            putExtra(TripService.EXTRA_DEVICE_MAC, associationInfo.deviceMacAddress?.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture perdue (CDM) : ${associationInfo.deviceMacAddress}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!PrefsManager(this).isAppEnabled()) {
            return
        }

        val serviceIntent = Intent(this, TripService::class.java).apply {
            action = TripService.ACTION_STOP_AND_SAVE
            putExtra(TripService.EXTRA_DEVICE_MAC, associationInfo.deviceMacAddress?.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
