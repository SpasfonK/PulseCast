package com.pulsecast.app.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rassemble tout ce qu'un thème PulseCast détermine : Material3 (couleurs,
 * typographie, formes) + jetons de design étendus ([PulseCastThemeExtras]).
 */
data class PulseCastThemeSpec(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val extras: PulseCastThemeExtras
)

/**
 * Construit la spécification complète d'un [AppTheme].
 *
 * [dynamicAccent] n'est exploité que par [AppTheme.OLED_DEEP_MINIMAL] (cahier
 * des charges : couleur d'accentuation extraite de la pochette via Palette
 * API) ; les 3 autres thèmes ont une identité colorée fixe et l'ignorent
 * volontairement — leur esthétique doit rester reconnaissable quel que soit
 * l'épisode en cours.
 */
fun AppTheme.buildSpec(dynamicAccent: Color?): PulseCastThemeSpec = when (this) {
    AppTheme.OLED_DEEP_MINIMAL -> oledSpec(dynamicAccent)
    AppTheme.GLASSMORPHISM -> glassmorphismSpec()
    AppTheme.NEO_BRUTALIST -> neoBrutalistSpec()
    AppTheme.SYNTHWAVE -> synthwaveSpec()
}

private val sharpCorners = RoundedCornerShape(0.dp)

/** Choisit noir ou blanc selon la luminance du fond, pour un texte toujours lisible sur un accent extrait dynamiquement. */
private fun contrastingOnColor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)

// --- 1. OLED Deep Minimal ---------------------------------------------

private fun oledSpec(dynamicAccent: Color?): PulseCastThemeSpec {
    val accent = dynamicAccent ?: Color(0xFF7C4DFF)
    val onAccent = contrastingOnColor(accent)

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.24f),
        onPrimaryContainer = Color(0xFFEDEDED),
        secondary = Color(0xFF2A2A2A),
        onSecondary = Color(0xFFEDEDED),
        tertiary = Color(0xFF3D3D3D),
        onTertiary = Color(0xFFEDEDED),
        background = Color(0xFF000000),
        onBackground = Color(0xFFEDEDED),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = Color(0xFF0A0A0A),
        onSurfaceVariant = Color(0xFFB3B3B3),
        outline = Color(0xFF1F1F1F)
    )

    return PulseCastThemeSpec(
        colorScheme = colorScheme,
        typography = buildTypography(
            fontFamily = FontFamily.Default,
            headlineWeight = FontWeight.SemiBold,
            bodyWeight = FontWeight.Normal,
            letterSpacing = (-0.2).sp
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        extras = PulseCastThemeExtras(
            borderWidth = 0.5.dp,
            useHardShadow = false,
            hardShadowOffset = DpOffset.Zero,
            hardShadowColor = Color.Transparent,
            useGlassBackground = false,
            glassBlurRadius = 0.dp,
            useNeonGlow = false,
            neonGlowPrimary = Color.Transparent,
            neonGlowSecondary = Color.Transparent,
            neonGlowRadius = 0.dp,
            useDynamicAccentFromArtwork = true
        )
    )
}

// --- 2. Verre Dépoli (Glassmorphism) -----------------------------------

private fun glassmorphismSpec(): PulseCastThemeSpec {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF8FD3FF),
        onPrimary = Color(0xFF001B2E),
        primaryContainer = Color(0x408FD3FF),
        onPrimaryContainer = Color(0xFFF2F6FF),
        secondary = Color(0xFFB388FF),
        onSecondary = Color(0xFF1B0033),
        tertiary = Color(0xFF80FFD8),
        onTertiary = Color(0xFF00291D),
        background = Color(0xFF0D0F14),
        onBackground = Color(0xFFF2F6FF),
        // Surfaces volontairement translucides : posées en Phase 4 par-dessus
        // une pochette floutée (Modifier.blur), elles laissent transparaître
        // l'illustration derrière elles.
        surface = Color(0x33FFFFFF),
        onSurface = Color(0xFFF2F6FF),
        surfaceVariant = Color(0x1FFFFFFF),
        onSurfaceVariant = Color(0xFFD3DCEA),
        outline = Color(0x59FFFFFF)
    )

    return PulseCastThemeSpec(
        colorScheme = colorScheme,
        typography = buildTypography(
            fontFamily = FontFamily.Default,
            headlineWeight = FontWeight.Light,
            bodyWeight = FontWeight.Normal,
            letterSpacing = 0.4.sp
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(32.dp),
            extraLarge = RoundedCornerShape(40.dp)
        ),
        extras = PulseCastThemeExtras(
            borderWidth = 1.dp,
            useHardShadow = false,
            hardShadowOffset = DpOffset.Zero,
            hardShadowColor = Color.Transparent,
            useGlassBackground = true,
            glassBlurRadius = 28.dp,
            useNeonGlow = false,
            neonGlowPrimary = Color.Transparent,
            neonGlowSecondary = Color.Transparent,
            neonGlowRadius = 0.dp,
            useDynamicAccentFromArtwork = false
        )
    )
}

// --- 3. Néo-Brutaliste ---------------------------------------------------

private fun neoBrutalistSpec(): PulseCastThemeSpec {
    // Contrairement aux 3 autres thèmes (sombres), le néo-brutalisme est
    // volontairement clair et criard : c'est fidèle au mouvement de design
    // "brutalist web design" (fonds crème/blancs, aplats de couleurs vives,
    // texte noir pur) et ça crée un vrai contraste d'ambiance dans le
    // sélecteur de styles.
    val colorScheme = lightColorScheme(
        primary = Color(0xFFFFDE59),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFFFFF1B8),
        onPrimaryContainer = Color(0xFF000000),
        secondary = Color(0xFFFF5C8A),
        onSecondary = Color(0xFF000000),
        tertiary = Color(0xFF5CE1FF),
        onTertiary = Color(0xFF000000),
        background = Color(0xFFFFFDF5),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFF0EEE2),
        onSurfaceVariant = Color(0xFF000000),
        outline = Color(0xFF000000)
    )

    return PulseCastThemeSpec(
        colorScheme = colorScheme,
        typography = buildTypography(
            fontFamily = FontFamily.SansSerif,
            headlineWeight = FontWeight.Black,
            bodyWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        ),
        shapes = Shapes(
            extraSmall = sharpCorners,
            small = sharpCorners,
            medium = sharpCorners,
            large = sharpCorners,
            extraLarge = sharpCorners
        ),
        extras = PulseCastThemeExtras(
            borderWidth = 2.dp,
            useHardShadow = true,
            hardShadowOffset = DpOffset(4.dp, 4.dp),
            hardShadowColor = Color(0xFF000000),
            useGlassBackground = false,
            glassBlurRadius = 0.dp,
            useNeonGlow = false,
            neonGlowPrimary = Color.Transparent,
            neonGlowSecondary = Color.Transparent,
            neonGlowRadius = 0.dp,
            useDynamicAccentFromArtwork = false
        )
    )
}

// --- 4. Synthwave / Cyberpunk --------------------------------------------

private fun synthwaveSpec(): PulseCastThemeSpec {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF00F0FF),
        onPrimary = Color(0xFF00131A),
        primaryContainer = Color(0xFF00444D),
        onPrimaryContainer = Color(0xFFF5E9FF),
        secondary = Color(0xFFFF2E88),
        onSecondary = Color(0xFF1A0010),
        tertiary = Color(0xFFB026FF),
        onTertiary = Color(0xFF12002B),
        background = Color(0xFF0B0221),
        onBackground = Color(0xFFF5E9FF),
        surface = Color(0xFF1A0B3D),
        onSurface = Color(0xFFF5E9FF),
        surfaceVariant = Color(0xFF241150),
        onSurfaceVariant = Color(0xFFD8C8FF),
        outline = Color(0xFF4B2E83)
    )

    return PulseCastThemeSpec(
        colorScheme = colorScheme,
        typography = buildTypography(
            fontFamily = FontFamily.Monospace,
            headlineWeight = FontWeight.Bold,
            bodyWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp
        ),
        shapes = Shapes(
            extraSmall = CutCornerShape(2.dp),
            small = CutCornerShape(4.dp),
            medium = CutCornerShape(8.dp),
            large = CutCornerShape(12.dp),
            extraLarge = CutCornerShape(16.dp)
        ),
        extras = PulseCastThemeExtras(
            borderWidth = 1.dp,
            useHardShadow = false,
            hardShadowOffset = DpOffset.Zero,
            hardShadowColor = Color.Transparent,
            useGlassBackground = false,
            glassBlurRadius = 0.dp,
            useNeonGlow = true,
            neonGlowPrimary = Color(0xFF00F0FF),
            neonGlowSecondary = Color(0xFFFF2E88),
            neonGlowRadius = 18.dp,
            useDynamicAccentFromArtwork = false
        )
    )
}

// --- Typographie -----------------------------------------------------------

/**
 * Dérive un [Typography] Material3 complet à partir de la valeur par défaut,
 * en ne modifiant que la famille de police, la graisse et l'espacement des
 * lettres — évite de réécrire à la main les 15 styles du type scale pour
 * chacun des 4 thèmes.
 */
private fun buildTypography(
    fontFamily: FontFamily,
    headlineWeight: FontWeight,
    bodyWeight: FontWeight,
    letterSpacing: TextUnit
): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily, fontWeight = headlineWeight, letterSpacing = letterSpacing),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily, fontWeight = headlineWeight, letterSpacing = letterSpacing),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily, fontWeight = headlineWeight, letterSpacing = letterSpacing),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontWeight = headlineWeight, letterSpacing = letterSpacing),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontWeight = headlineWeight, letterSpacing = letterSpacing),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontWeight = headlineWeight, letterSpacing = letterSpacing),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontWeight = headlineWeight),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontWeight = bodyWeight),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontWeight = bodyWeight)
    )
}
