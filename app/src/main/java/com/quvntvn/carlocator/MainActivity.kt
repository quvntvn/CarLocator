package com.quvntvn.carlocator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quvntvn.carlocator.ui.theme.CarLocatorTheme // Si cette ligne est rouge, supprime-la, c'est pas grave

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On initialise la base de données
        val db = AppDatabase.getDatabase(applicationContext)

        setContent {
            // On lance notre écran principal
            // Note: Si CarLocatorTheme est rouge, enlève les balises CarLocatorTheme { } et garde juste MainScreen
            CarLocatorTheme {
                MainScreen(db = db)
            }
        }
    }
}