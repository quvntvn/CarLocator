package com.quvntvn.carlocator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quvntvn.carlocator.ui.theme.CarLocatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(this)
        setContent {
            CarLocatorTheme {
                MainScreen(db = db)
            }
        }
    }
}
