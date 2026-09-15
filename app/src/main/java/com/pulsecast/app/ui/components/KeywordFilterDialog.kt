package com.pulsecast.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialogue de filtrage par mot-clé (cahier des charges : "possibilité
 * d'appliquer un filtre par podcast"). Le filtre s'applique aux deux
 * onglets (Lus et Non-lus) de l'écran d'épisodes.
 */
@Composable
fun KeywordFilterDialog(
    currentPattern: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var text by rememberSaveable(currentPattern) { mutableStateOf(currentPattern.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtrer par mot-clé") },
        text = {
            Column {
                Text(
                    text = "Seuls les épisodes dont le titre contient ce mot-clé seront affichés (onglets Lus et Non-lus). Laisse vide pour tout afficher.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Mot-clé, ex. \u201cIntégrale\u201d") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().takeIf { it.isNotBlank() }) }) {
                Text("Appliquer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
