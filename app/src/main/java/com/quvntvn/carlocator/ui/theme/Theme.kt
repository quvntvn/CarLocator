package com.quvntvn.carlocator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Ton thème sombre personnalisé
private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = SuccessGreen,
    tertiary = ErrorRed,
    background = DeepBlack,
    surface = SurfaceBlack,
    onPrimary = TextWhite,
    onSecondary = DeepBlack,
    onTertiary = DeepBlack,
    onBackground = TextWhite,
    onSurface = TextWhite
)

// On force le thème sombre même en mode clair pour garder le style "Neon"
private val LightColorScheme = DarkColorScheme

@Composable
fun CarLocatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // On désactive les couleurs dynamiques (Android 12+) pour garder TON style bleu/noir
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // On met la barre de statut de la couleur du fond (DeepBlack)
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}