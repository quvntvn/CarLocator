package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull

class CarBluetoothReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

        if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action && device != null) {
            // L'appareil s'est déconnecté. On vérifie si c'est notre voiture.
            val database = AppDatabase.getDatabase(context)
            val dao = database.carDao()
            val prefs = PrefsManager(context)

            // On lance une coroutine car on ne peut pas faire de base de données directement dans onReceive
            CoroutineScope(Dispatchers.IO).launch {
                // On récupère la voiture enregistrée avec cette adresse MAC
                val savedCar = dao.getCarByMac(device.address)

                if (savedCar != null) {
                    // C'est bien notre voiture ! On enregistre la position actuelle.
                    val gpsTracker = GpsTracker(context)
                    val location = gpsTracker.getLocation()

                    if (location != null) {
                        // On met à jour la position dans la base de données
                        val updatedCar = savedCar.copy(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                        dao.insertOrUpdateCar(updatedCar)

                        // Optionnel : Sauvegarder aussi dans les préférences pour un accès rapide
                        prefs.saveCarLocation(location.latitude, location.longitude)
                    }
                }
            }
        }
    }
}