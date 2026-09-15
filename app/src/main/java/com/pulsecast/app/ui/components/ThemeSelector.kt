package com.pulsecast.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pulsecast.app.theme.AppTheme
import com.pulsecast.app.theme.buildSpec

/**
 * Rangée défilante présentant les 4 styles disponibles, chacun prévisualisé
 * avec ses propres couleurs (indépendamment du thème actuellement actif).
 * Sélectionner une carte déclenche [onThemeSelected] ; c'est à l'appelant
 * (Phase 4 : écran de réglages) de répercuter ce choix dans
 * `ThemeViewModel.selectTheme`.
 */
@Composable
fun ThemeSelector(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(AppTheme.entries, key = { it.id }) { theme ->
            ThemeSwatchCard(
                theme = theme,
                isSelected = theme == selectedTheme,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Prévisualisation indépendante du thème actif : chaque carte calcule sa
    // propre spec pour afficher ses vraies couleurs, sans accent dynamique
    // (aucune pochette n'est disponible dans le sélecteur lui-même).
    val previewSpec = remember(theme) { theme.buildSpec(dynamicAccent = null) }

    // Le conteneur de la carte, lui, suit le thème ACTUELLEMENT actif : en
    // Néo-Brutaliste, le sélecteur adopte lui-même l'ombre dure décalée ; en
    // Synthwave, le halo néon — démonstration directe de PulseCastSurface.
    PulseCastSurface(
        modifier = modifier
            .width(108.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(previewSpec.colorScheme.background)
            ) {
                ColorChip(previewSpec.colorScheme.primary)
                ColorChip(previewSpec.colorScheme.secondary)
                ColorChip(previewSpec.colorScheme.tertiary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = theme.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (isSelected) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Style sélectionné",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.ColorChip(color: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(color)
    )
}
