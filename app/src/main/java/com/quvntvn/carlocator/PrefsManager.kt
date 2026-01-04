package com.quvntvn.carlocator

import android.content.Context

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("car_prefs", Context.MODE_PRIVATE)

    fun saveCarDeviceId(address: String) {
        prefs.edit().putString("car_mac_address", address).apply()
    }

    fun getCarDeviceId(): String? {
        return prefs.getString("car_mac_address", null)
    }

    fun clearCarDevice() {
        prefs.edit().remove("car_mac_address").apply()
    }
}