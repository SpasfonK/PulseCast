package com.pulsecast.app.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

/**
 * Extrait une couleur d'accentuation depuis la pochette d'un podcast ou d'un
 * épisode : c'est cette couleur qui alimente [AppTheme.OLED_DEEP_MINIMAL] en
 * tant que `dynamicAccent` (cahier des charges : "couleur d'accentuation
 * extraite dynamiquement de la pochette via Palette API").
 *
 * Ordre de préférence des swatches, du plus au moins expressif visuellement :
 * Vibrant > LightVibrant > DarkVibrant > Muted, puis une couleur de repli si
 * la pochette ne fournit aucun swatch exploitable (image trop uniforme,
 * illustration en niveaux de gris...).
 *
 * `Palette.from(bitmap).generate()` est un calcul CPU synchrone : à appeler
 * depuis un contexte hors thread principal (ex. `Dispatchers.Default`) une
 * fois la pochette chargée — ce que fera l'écran du lecteur en Phase 4.
 */
object PaletteAccentExtractor {

    private val FALLBACK_ACCENT = Color(0xFF7C4DFF)

    fun extractAccent(bitmap: Bitmap): Color {
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.mutedSwatch
        return swatch?.let { Color(it.rgb) } ?: FALLBACK_ACCENT
    }
}
