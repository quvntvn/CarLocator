package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.S) // CDM Service est surtout utile pour Android 12+
class CarCompanionService : CompanionDeviceService() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        // LA VOITURE EST LÀ !
        // On lance le Foreground Service pour s'assurer que l'app ne soit pas tuée pendant le trajet
        Log.d("CarLocator", "Voiture détectée par le système !")

        val intent = Intent(this, ParkingService::class.java)
        intent.putExtra("TARGET_MAC", associationInfo.deviceMacAddress?.toString())
        ContextCompat.startForegroundService(this, intent)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        // LA VOITURE VIENT DE PARTIR !
        Log.d("CarLocator", "Voiture déconnectée (Système) !")

        // 1. On sauvegarde la position immédiatement
        val db = AppDatabase.getInstance(applicationContext)
        val mac = associationInfo.deviceMacAddress?.toString()

        if (mac != null) {
            CoroutineScope(Dispatchers.IO).launch {
                // On récupère la voiture connue
                val car = db.carDao().getCarByMac(mac) // Il faudra peut-être ajouter cette méthode dans le DAO
                if (car != null) {
                    // On lance la logique de sauvegarde (similaire à saveCurrentLocation)
                    // Note: Ici on est en background, il faut utiliser un LocationManager silencieux ou
                    // se baser sur la dernière position connue si le GPS n'est pas chaud.

                    // Pour simplifier, on arrête le service de parking qui va faire le travail de sauvegarde dans son onDestroy ou sa logique
                    val intent = Intent(applicationContext, ParkingService::class.java)
                    stopService(intent)
                }
            }
        }
    }
}