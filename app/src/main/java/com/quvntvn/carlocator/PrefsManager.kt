package com.quvntvn.carlocator

import android.content.Context

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("car_prefs", Context.MODE_PRIVATE)

    // --- Gestion du premier lancement (Tutoriel) ---
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean("is_first_launch", true)
    }

    fun setFirstLaunchDone() {
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    // --- Gestion Id Appareil (Si utilisé ailleurs) ---
    fun saveCarDeviceId(address: String) {
        prefs.edit().putString("car_mac_address", address).apply()
    }

    fun getCarDeviceId(): String? {
        return prefs.getString("car_mac_address", null)
    }
}