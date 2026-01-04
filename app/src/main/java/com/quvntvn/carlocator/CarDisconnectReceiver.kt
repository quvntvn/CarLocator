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

    @SuppressLint("MissingPermission") // On suppose que les perms sont déjà là
    override fun onReceive(context: Context, intent: Intent) {
        // 1. On vérifie si l'événement est bien une déconnexion
        if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {

            // 2. Quel appareil s'est déconnecté ?
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val disconnectedMac = device?.address

            // 3. Est-ce que c'est NOTRE voiture ?
            val prefs = PrefsManager(context)
            val myCarMac = prefs.getCarDeviceId()

            if (disconnectedMac != null && disconnectedMac == myCarMac) {
                Log.d("CarLocator", "🚗 La voiture s'est déconnectée ! Enregistrement...")

                // 4. On lance l'enregistrement de la position GPS
                saveLocation(context)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun saveLocation(context: Context) {
        // On utilise LocationServices pour choper la dernière position connue rapidement
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                // On doit lancer une coroutine car la DB ne peut pas être touchée sur le thread principal
                val db = AppDatabase.getDatabase(context)
                val scope = CoroutineScope(Dispatchers.IO)

                scope.launch {
                    db.carDao().saveCarLocation(
                        CarLocation(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    Log.d("CarLocator", "✅ Position sauvegardée automatiquement : ${location.latitude}, ${location.longitude}")
                }
            } else {
                Log.e("CarLocator", "❌ Impossible de trouver la position GPS au moment de la déconnexion")
            }
        }
    }
}