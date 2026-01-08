package com.quvntvn.carlocator

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("car_locator_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LATITUDE = "last_car_lat"
        private const val KEY_LONGITUDE = "last_car_lng"

        // Nouveaux réglages
        private const val KEY_APP_ENABLED = "app_enabled"
        private const val KEY_NOTIF_CONNECTION = "notif_connection"
        private const val KEY_NOTIF_PARKED = "notif_parked"

        private const val KEY_SELECTED_CAR_MAC = "selected_car_mac"
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    fun setFirstLaunchDone() = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

    fun saveCarLocation(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LATITUDE, lat.toFloat())
            .putFloat(KEY_LONGITUDE, lng.toFloat())
            .apply()
    }

    fun getLastCarLocation(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_LATITUDE)) return null
        val lat = prefs.getFloat(KEY_LATITUDE, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble()
        return Pair(lat, lng)
    }

    // --- GESTION DES PARAMÈTRES ---

    // Activer/Désactiver l'application
    fun isAppEnabled(): Boolean = prefs.getBoolean(KEY_APP_ENABLED, true)
    fun setAppEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_APP_ENABLED, enabled).apply()

    // Notifs Connexion
    fun isConnectionNotifEnabled(): Boolean = prefs.getBoolean(KEY_NOTIF_CONNECTION, true)
    fun setConnectionNotifEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_NOTIF_CONNECTION, enabled).apply()

    // Notifs Stationnement
    fun isParkedNotifEnabled(): Boolean = prefs.getBoolean(KEY_NOTIF_PARKED, true)
    fun setParkedNotifEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_NOTIF_PARKED, enabled).apply()

    // --- NOUVEAU : Sauvegarde de la sélection ---
    fun saveLastSelectedCarMac(mac: String) {
        prefs.edit().putString(KEY_SELECTED_CAR_MAC, mac).apply()
    }

    fun getLastSelectedCarMac(): String? {
        return prefs.getString(KEY_SELECTED_CAR_MAC, null)
    }
}