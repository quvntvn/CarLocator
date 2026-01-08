package com.quvntvn.carlocator

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("car_locator_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LATITUDE = "last_car_lat"
        private const val KEY_LONGITUDE = "last_car_lng"
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchDone() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    // --- AJOUTE CES FONCTIONS ---
    fun saveCarLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LATITUDE, lat.toFloat())
            .putFloat(KEY_LONGITUDE, lng.toFloat())
            .apply()
    }

    // Optionnel : Pour récupérer la position plus tard si besoin
    fun getLastCarLocation(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_LATITUDE)) return null
        val lat = prefs.getFloat(KEY_LATITUDE, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble()
        return Pair(lat, lng)
    }
}