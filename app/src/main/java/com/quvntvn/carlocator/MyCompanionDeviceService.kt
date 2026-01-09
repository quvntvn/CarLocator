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

        val serviceIntent = Intent(this, ParkingService::class.java).apply {
            action = ParkingService.ACTION_CONNECTED
            putExtra(ParkingService.EXTRA_MAC_ADDRESS, associationInfo.deviceMacAddress?.toString())
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

        val serviceIntent = Intent(this, ParkingService::class.java).apply {
            action = ParkingService.ACTION_DISCONNECTED
            putExtra(ParkingService.EXTRA_MAC_ADDRESS, associationInfo.deviceMacAddress?.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
