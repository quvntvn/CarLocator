package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("MissingPermission")
class MyCompanionDeviceService : CompanionDeviceService() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture détectée (CDM) : ${associationInfo.deviceMacAddress}")
        if (!PrefsManager(this).isAppEnabled()) {
            return
        }

        val serviceIntent = Intent(this, TripService::class.java).apply {
            action = TripService.ACTION_START
            putExtra(TripService.EXTRA_DEVICE_MAC, associationInfo.deviceMacAddress?.toString())
            putExtra(TripService.EXTRA_NOTIFY_CONNECTED, true)
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
