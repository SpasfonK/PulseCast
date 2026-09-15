package com.pulsecast.app.ui.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsecast.app.theme.AppTheme
import com.pulsecast.app.theme.PulseCastTheme
import com.pulsecast.app.theme.ThemeViewModel
import com.pulsecast.app.ui.components.ThemeSelector

/**
 * Composable de démonstration reliant [ThemeViewModel], [PulseCastTheme] et
 * [ThemeSelector] de bout en bout.
 *
 * Ce n'est pas un écran de l'application (ceux-ci arrivent en Phase 4) :
 * c'est le banc d'essai qui prouve que le changement de style est bien
 * instantané (Gauntlet Check 3), et qui servira de base à l'écran de
 * réglages en Phase 4.
 */
@Composable
fun ThemeShowcase(modifier: Modifier = Modifier) {
    val viewModel: ThemeViewModel = viewModel()
    val selectedTheme by viewModel.selectedTheme.collectAsState()

    PulseCastTheme(appTheme = selectedTheme) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Style visuel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                ThemeSelector(
                    selectedTheme = selectedTheme,
                    onThemeSelected = viewModel::selectTheme
                )
            }
        }
    }
}

/**
 * Aperçu statique (Android Studio Preview) fixé sur Synthwave, sans
 * dépendance à [ThemeViewModel] (qui a besoin d'un vrai contexte
 * applicatif) : garantit un rendu de preview fiable et reproductible.
 */
@Preview(showBackground = true)
@Composable
private fun ThemeShowcasePreview() {
    PulseCastTheme(appTheme = AppTheme.SYNTHWAVE) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Style visuel",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                ThemeSelector(
                    selectedTheme = AppTheme.SYNTHWAVE,
                    onThemeSelected = {}
                )
            }
        }
    }
}
