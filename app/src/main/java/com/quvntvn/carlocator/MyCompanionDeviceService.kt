package com.quvntvn.carlocator

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.util.Log

class MyCompanionDeviceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        // La voiture est connectée !
        Log.d("CarLocator", "Voiture détectée : ${associationInfo.deviceMacAddress}")

        // Tu peux lancer ton ParkingService ici pour afficher la notif "Connecté"
        val serviceIntent = Intent(this, ParkingService::class.java).apply {
            action = "ACTION_CONNECTED" // Adapte selon ton code existant
        }
        startForegroundService(serviceIntent)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        // La voiture est déconnectée ! C'est ici qu'on sauvegarde la position.
        Log.d("CarLocator", "Voiture perdue : ${associationInfo.deviceMacAddress}")

        // 1. Récupérer la localisation GPS actuelle
        val tracker = GpsTracker(this)
        val location = tracker.getLocation()

        // 2. Sauvegarder dans la BDD (attention, on est sur le main thread ici, utilise un thread/coroutine)
        if (location != null) {
            // Logique de sauvegarde (reprend celle de ton Receiver actuel)
            saveParkingLocation(location)
        }

        // 3. Arrêter le service foreground si nécessaire
        stopService(Intent(this, ParkingService::class.java))
    }
}