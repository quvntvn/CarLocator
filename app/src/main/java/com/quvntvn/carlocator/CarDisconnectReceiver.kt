package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarDisconnectReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val disconnectedMac = device?.address

            if (disconnectedMac != null) {
                // On vérifie en base de données si ce MAC correspond à une de nos voitures
                saveLocationIfCarExists(context, disconnectedMac)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveLocationIfCarExists(context: Context, macAddress: String) {
        val db = AppDatabase.getDatabase(context)
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            // 1. Est-ce que cette voiture est dans notre garage ?
            val car = db.carDao().getCarByMac(macAddress)

            if (car != null) {
                Log.d("CarLocator", "🚗 ${car.name} vient de se déconnecter ! Recherche GPS...")

                // 2. On récupère la position GPS
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        scope.launch {
                            // 3. On met à jour SEULEMENT la position et l'heure, en gardant le nom
                            val updatedCar = car.copy(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = System.currentTimeMillis()
                            )
                            db.carDao().insertOrUpdateCar(updatedCar)
                            Log.d("CarLocator", "✅ Position de ${car.name} mise à jour !")
                        }
                    }
                }
            }
        }
    }
}