package com.pulsecast.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pulsecast.app.ui.PulseCastApp

/**
 * Unique Activity de l'application (architecture single-Activity +
 * Navigation Compose). Deux responsabilités propres à une Activity, qui ne
 * pouvaient pas être prises en charge par PlaybackService :
 * - activer l'affichage bord-à-bord ;
 * - demander la permission de notifications (Android 13+) nécessaire à
 *   l'affichage du contrôle de lecture dans la barre système — seule une
 *   Activity peut afficher ce dialogue.
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Résultat ignoré : la lecture fonctionne dans tous les cas, seule la
          notification visuelle en dépend sur Android 13+. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        setContent {
            PulseCastApp()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
