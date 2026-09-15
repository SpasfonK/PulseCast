package com.pulsecast.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

val LocalPulseCastThemeExtras: ProvidableCompositionLocal<PulseCastThemeExtras> =
    compositionLocalOf { PulseCastThemeExtras.Undefined }

/**
 * Point d'accès pratique aux jetons de design PulseCast, à l'image de
 * `MaterialTheme.colorScheme` — usage : `PulseCastTheme.extras.borderWidth`.
 */
object PulseCastTheme {
    val extras: PulseCastThemeExtras
        @Composable
        get() = LocalPulseCastThemeExtras.current
}

/**
 * Racine de theming de l'application : applique en un seul geste le
 * ColorScheme / Typography / Shapes Material3 du style choisi, ainsi que les
 * jetons étendus ([PulseCastThemeExtras]) via [LocalPulseCastThemeExtras].
 *
 * Changer [appTheme] (ou [dynamicAccent]) ne fait que recalculer un
 * [PulseCastThemeSpec] — un calcul pur, sans I/O — et recomposer les
 * composables qui lisent effectivement `MaterialTheme.colorScheme` ou
 * `PulseCastTheme.extras`. Aucun redémarrage d'Activity n'est impliqué :
 * voir PHASE3_NOTES.md (Gauntlet Check 3) pour le détail de cette garantie.
 */
@Composable
fun PulseCastTheme(
    appTheme: AppTheme,
    dynamicAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val spec = remember(appTheme, dynamicAccent) { appTheme.buildSpec(dynamicAccent) }

    CompositionLocalProvider(LocalPulseCastThemeExtras provides spec.extras) {
        MaterialTheme(
            colorScheme = spec.colorScheme,
            typography = spec.typography,
            shapes = spec.shapes,
            content = content
        )
    }
}
