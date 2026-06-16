package com.quvntvn.carlocator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quvntvn.carlocator.ui.theme.CarLocatorTheme
import com.quvntvn.carlocator.service.SafetyNetScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarLocatorTheme {
                MainScreen()
            }
        }
        // 1. Démarrage de la ceinture de sécurité (SafetyNet)
        SafetyNetScheduler.schedule(this)
    }
}
