package com.quvntvn.carlocator.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("car_locator_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FIRST_LAUNCH = "first_launch"
        // Nouveaux réglages
        private const val KEY_APP_ENABLED = "app_enabled"

        private const val KEY_SELECTED_CAR_MAC = "selected_car_mac"
        private const val KEY_LAST_CONNECTED_CAR_MAC = "last_connected_car_mac"
    }

    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    fun setFirstLaunchDone() = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

    // --- GESTION DES PARAMÈTRES ---

    // Activer/Désactiver l'application
    fun isAppEnabled(): Boolean = prefs.getBoolean(KEY_APP_ENABLED, true)
    fun setAppEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_APP_ENABLED, enabled).apply()

    // --- NOUVEAU : Sauvegarde de la sélection ---
    fun saveLastSelectedCarMac(mac: String?) {
        saveNormalizedMac(KEY_SELECTED_CAR_MAC, mac)
    }

    fun getLastSelectedCarMac(): String? {
        return getNormalizedMac(KEY_SELECTED_CAR_MAC)
    }

    fun saveLastConnectedCarMac(mac: String?) {
        saveNormalizedMac(KEY_LAST_CONNECTED_CAR_MAC, mac)
    }

    fun getLastConnectedCarMac(): String? {
        return getNormalizedMac(KEY_LAST_CONNECTED_CAR_MAC)
    }

    private fun saveNormalizedMac(key: String, mac: String?) {
        val normalized = normalizeMac(mac)
        val editor = prefs.edit()
        if (normalized == null) {
            editor.remove(key)
        } else {
            editor.putString(key, normalized)
        }
        editor.apply()
    }

    private fun getNormalizedMac(key: String): String? {
        return normalizeMac(prefs.getString(key, null))
    }

    private fun normalizeMac(mac: String?): String? {
        val trimmed = mac?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return null
        }
        return trimmed.uppercase(Locale.ROOT)
    }
}
