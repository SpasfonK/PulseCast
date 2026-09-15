package com.pulsecast.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Jetons de design propres à PulseCast, en complément de ce que couvrent
 * déjà [androidx.compose.material3.ColorScheme] et [androidx.compose.material3.Shapes].
 * Chaque thème construit sa propre instance dans `ThemeSpecs.kt`.
 */
data class PulseCastThemeExtras(
    /** Épaisseur de bordure : micro-bordure OLED, bordure épaisse néo-brutaliste... */
    val borderWidth: Dp,

    /** Néo-Brutaliste : ombre dure et nette, décalée, sans flou — par opposition à une élévation Material classique. */
    val useHardShadow: Boolean,
    val hardShadowOffset: DpOffset,
    val hardShadowColor: Color,

    /** Verre Dépoli : surfaces translucides destinées à être posées sur une pochette floutée en arrière-plan. */
    val useGlassBackground: Boolean,
    val glassBlurRadius: Dp,

    /** Synthwave : halo lumineux diffus autour des surfaces, teinté par deux couleurs néon. */
    val useNeonGlow: Boolean,
    val neonGlowPrimary: Color,
    val neonGlowSecondary: Color,
    val neonGlowRadius: Dp,

    /** OLED Deep Minimal : la couleur d'accentuation provient de la pochette (Palette API) plutôt que d'être fixe. */
    val useDynamicAccentFromArtwork: Boolean
) {
    companion object {
        /** Valeur de repli neutre pour le CompositionLocal ; jamais censée être lue en pratique. */
        val Undefined = PulseCastThemeExtras(
            borderWidth = 1.dp,
            useHardShadow = false,
            hardShadowOffset = DpOffset.Zero,
            hardShadowColor = Color.Black,
            useGlassBackground = false,
            glassBlurRadius = 0.dp,
            useNeonGlow = false,
            neonGlowPrimary = Color.Transparent,
            neonGlowSecondary = Color.Transparent,
            neonGlowRadius = 0.dp,
            useDynamicAccentFromArtwork = false
        )
    }
}
