package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class MyCompanionDeviceService : CompanionDeviceService() {

    // Scope pour lancer des coroutines (GPS, BDD) qui ne bloquent pas l'interface
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture détectée (CDM) : ${associationInfo.deviceMacAddress}")

        // On lance le service de notification pour dire "Connecté"
        val serviceIntent = Intent(this, ParkingService::class.java).apply {
            action = "ACTION_CONNECTED"
            putExtra("EXTRA_MAC_ADDRESS", associationInfo.deviceMacAddress?.toString())
        }
        startForegroundService(serviceIntent)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.d("CarLocator", "Voiture perdue (CDM) : ${associationInfo.deviceMacAddress}")

        // CORRECTION 1 : On lance une coroutine pour appeler le GPS (suspend)
        serviceScope.launch {
            try {
                // Récupération de la position (peut prendre du temps)
                val tracker = GpsTracker(this@MyCompanionDeviceService)
                val location = tracker.getLocation()

                if (location != null) {
                    // CORRECTION 2 : Appel de la fonction de sauvegarde qu'on a créée plus bas
                    saveParkingLocation(location, associationInfo.deviceMacAddress?.toString())
                } else {
                    Log.e("CarLocator", "Impossible de récupérer la localisation GPS à la déconnexion.")
                }
            } catch (e: Exception) {
                Log.e("CarLocator", "Erreur lors de la sauvegarde : ${e.message}")
            } finally {
                // On arrête le service de notification "Connecté"
                stopService(Intent(this@MyCompanionDeviceService, ParkingService::class.java))
            }
        }
    }

    // CORRECTION 2 (Implémentation) : Sauvegarde dans la BDD
    private suspend fun saveParkingLocation(location: Location, macAddress: String?) {
        if (macAddress == null) return

        // Récupération de l'instance de la BDD
        // Assure-toi que ta classe AppDatabase a bien une méthode singleton 'getDatabase(context)'
        // Si tu utilises Hilt/Dagger, injecte le DAO autrement.
        val db = AppDatabase.getDatabase(applicationContext)
        val carDao = db.carDao()

        // On récupère la liste des voitures pour trouver la bonne par MAC
        // 'first()' permet de prendre la première valeur émise par le Flow (si ton DAO renvoie un Flow)
        val allCars = carDao.getAllCars().first()

        val car = allCars.find { it.macAddress.equals(macAddress, ignoreCase = true) }

        if (car != null) {
            val updatedCar = car.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                timestamp = System.currentTimeMillis()
            )
            carDao.insertOrUpdateCar(updatedCar)
            Log.d("CarLocator", "Position sauvegardée pour ${car.name} à [${location.latitude}, ${location.longitude}]")
        } else {
            Log.w("CarLocator", "Aucune voiture trouvée en base pour l'adresse MAC : $macAddress")
        }
    }
}