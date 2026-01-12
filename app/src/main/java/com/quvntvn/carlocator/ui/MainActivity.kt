package com.quvntvn.carlocator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quvntvn.carlocator.ui.theme.CarLocatorTheme
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quvntvn.carlocator.service.SafetyNetWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarLocatorTheme {
                MainScreen()
            }
        }
        // 1. Démarrage de la ceinture de sécurité (SafetyNet)
        val workRequest = PeriodicWorkRequestBuilder<SafetyNetWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CarLocatorSafetyNet",
            ExistingPeriodicWorkPolicy.KEEP, // KEEP = ne redémarre pas si existe déjà
            workRequest
        )
    }
}
