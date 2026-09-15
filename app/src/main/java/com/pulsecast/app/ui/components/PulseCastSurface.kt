package com.pulsecast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.pulsecast.app.theme.PulseCastTheme

/**
 * Conteneur commun (Composant Partagé, Phase 3) qui traduit visuellement les
 * jetons de design du thème actif :
 * - Néo-Brutaliste : ombre dure décalée derrière la surface (aplat solide,
 *   pas un flou Material classique) ;
 * - Synthwave : halo néon diffus autour de la surface ;
 * - OLED / Verre Dépoli : bordure fine et surface (éventuellement
 *   translucide) sans effet supplémentaire.
 *
 * Toute l'UI des Phases 4+ construite avec ce composant change donc
 * automatiquement d'apparence avec le thème actif, sans code spécifique par
 * écran.
 *
 * Note de portabilité : `Modifier.blur()` s'appuie sur `RenderEffect`,
 * disponible à partir d'API 31 ; en dessous (jusqu'à minSdk 26), le halo
 * néon reste visible mais sans flou logiciel de repli pour l'instant — pas
 * bloquant, juste un peu moins doux visuellement sur les appareils plus
 * anciens.
 *
 * Règle de mesure importante : seules les décorations (ombre dure, halo néon)
 * utilisent `matchParentSize()`, car un enfant en `matchParentSize` ne
 * participe **pas** au calcul de la taille du `Box`. Le contenu, lui, est
 * mesuré normalement (et étiré en largeur) : c'est ce qui lui donne sa
 * hauteur. Sans cela la surface se réduit à 0 px et son contenu — liste
 * d'épisodes, sélecteur de style, lignes de la bibliothèque — disparaît.
 */
@Composable
fun PulseCastSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable BoxScope.() -> Unit
) {
    val extras = PulseCastTheme.extras
    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        if (extras.useHardShadow) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = extras.hardShadowOffset.x, y = extras.hardShadowOffset.y)
                    .clip(shape)
                    .background(extras.hardShadowColor)
            )
        }

        if (extras.useNeonGlow) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(extras.neonGlowRadius)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                extras.neonGlowPrimary.copy(alpha = 0.55f),
                                extras.neonGlowSecondary.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
        }

        // Le contenu participe à la mesure : c'est lui qui donne sa taille au
        // Box. Un `matchParentSize()` ici (et non sur les décorations) ferait
        // s'effondrer toute la surface à 0 px, car un enfant
        // `matchParentSize` ne définit jamais la taille de son parent.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colorScheme.surface)
                .border(extras.borderWidth, colorScheme.outline, shape),
            content = content
        )
    }
}
