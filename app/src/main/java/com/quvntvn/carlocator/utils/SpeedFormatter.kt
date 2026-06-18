package com.quvntvn.carlocator.utils

import java.util.Locale

/**
 * Conversion et formatage de la vitesse pour la pastille Hyper Island / Live Update Android 16.
 */
object SpeedFormatter {

    /** Choix utilisateur stocké en préférence. */
    enum class Setting {
        AUTO, KMH, MPH;

        fun toPref(): String = name.lowercase(Locale.ROOT)

        companion object {
            fun fromPref(value: String?): Setting = when (value?.lowercase(Locale.ROOT)) {
                "kmh" -> KMH
                "mph" -> MPH
                else -> AUTO
            }
        }
    }

    /** Unité réellement affichée. */
    enum class Unit { KMH, MPH }

    // Sous ce seuil (~3 km/h), la vitesse GPS n'est que du bruit à l'arrêt -> on affiche 0.
    private const val NOISE_THRESHOLD_MS = 0.85f

    // Espace "figure" (U+2007) : même largeur qu'un chiffre -> pastille de largeur stable
    // quelle que soit la vitesse (1, 2 ou 3 chiffres).
    private const val FIGURE_SPACE = ' '

    // Pays roulant en miles par heure.
    private val MPH_REGIONS = setOf("US", "GB", "MM", "LR")

    fun resolveUnit(setting: Setting, locale: Locale): Unit = when (setting) {
        Setting.KMH -> Unit.KMH
        Setting.MPH -> Unit.MPH
        Setting.AUTO ->
            if (locale.country.uppercase(Locale.ROOT) in MPH_REGIONS) Unit.MPH else Unit.KMH
    }

    fun label(unit: Unit): String = if (unit == Unit.MPH) "mph" else "km/h"

    private fun value(speedMs: Float?, unit: Unit): Int {
        val ms = speedMs ?: return 0
        if (ms.isNaN() || ms < NOISE_THRESHOLD_MS) return 0
        val factor = if (unit == Unit.MPH) 2.236936f else 3.6f
        return (ms * factor).toInt().coerceIn(0, 999)
    }

    /** Nombre seul, cadré à 3 caractères (ex. "  0", " 42", "150") pour une pastille stable. */
    fun pill(speedMs: Float?, unit: Unit): String =
        value(speedMs, unit).toString().padStart(3, FIGURE_SPACE)

    /** Vitesse + unité pour la vue déployée (ex. "42 km/h"). */
    fun full(speedMs: Float?, unit: Unit): String =
        "${value(speedMs, unit)} ${label(unit)}"
}
