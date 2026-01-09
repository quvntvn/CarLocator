package com.quvntvn.carlocator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quvntvn.carlocator.ui.theme.CarLocatorTheme
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this)
        setContent {
            CarLocatorTheme {
                MainScreen(db = db)
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
