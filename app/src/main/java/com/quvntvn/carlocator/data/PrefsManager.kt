package com.quvntvn.carlocator.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("car_locator_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        // Nouveaux réglages
        private const val KEY_APP_ENABLED = "app_enabled"

        private const val KEY_SELECTED_CAR_MAC = "selected_car_mac"
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    fun setFirstLaunchDone() = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

    // --- GESTION DES PARAMÈTRES ---

    // Activer/Désactiver l'application
    fun isAppEnabled(): Boolean = prefs.getBoolean(KEY_APP_ENABLED, true)
    fun setAppEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_APP_ENABLED, enabled).apply()

    // --- NOUVEAU : Sauvegarde de la sélection ---
    fun saveLastSelectedCarMac(mac: String) {
        prefs.edit().putString(KEY_SELECTED_CAR_MAC, mac).apply()
    }

    fun getLastSelectedCarMac(): String? {
        return prefs.getString(KEY_SELECTED_CAR_MAC, null)
    }
}
