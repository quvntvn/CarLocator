package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.os.Build
import android.util.Log

@SuppressLint("MissingPermission")
class MyCompanionDeviceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture détectée (CDM) : ${associationInfo.deviceMacAddress}")

        val serviceIntent = Intent(this, ParkingService::class.java).apply {
            action = "ACTION_CONNECTED"
            putExtra("EXTRA_MAC_ADDRESS", associationInfo.deviceMacAddress?.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture perdue (CDM) : ${associationInfo.deviceMacAddress}")

        val serviceIntent = Intent(this, ParkingService::class.java).apply {
            action = "ACTION_DISCONNECTED"
            putExtra("EXTRA_MAC_ADDRESS", associationInfo.deviceMacAddress?.toString())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}