package com.quvntvn.carlocator.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.quvntvn.carlocator.data.AppDatabase
import com.quvntvn.carlocator.data.PrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        if (!PrefsManager(context).isAppEnabled()) {
            return
        }
        Log.d("CarLocator", "System event ($action): scheduling SafetyNet worker.")
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val deviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? android.companion.CompanionDeviceManager
                    if (deviceManager != null) {
                        val cars = AppDatabase.getInstance(context).carDao().getAllCarsOnce()
                        cars.forEach { car ->
                            try {
                                deviceManager.startObservingDevicePresence(car.macAddress)
                            } catch (e: Exception) {
                                // Ignorer si déjà surveillé ou non disponible
                            }
                        }
                    }
                }
                SafetyNetScheduler.schedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
