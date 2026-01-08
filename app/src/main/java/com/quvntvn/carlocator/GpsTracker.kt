package com.quvntvn.carlocator

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class GpsTracker(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // On suppose que la permission est demandée dans l'interface
    suspend fun getLocation(): Location? {
        return try {
            // Tente de récupérer la position exacte actuelle (nécessite Google Play Services)
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}