package com.pulsecast.app

import android.app.Application
import com.pulsecast.app.data.local.PulseCastDatabase

/**
 * Point d'entrée applicatif. Détient le singleton de base de données Room.
 * Les phases suivantes brancheront ici l'injection de dépendances (manuelle
 * ou Hilt) pour exposer repositories et use cases aux ViewModels.
 */
class PulseCastApplication : Application() {

    val database: PulseCastDatabase by lazy {
        PulseCastDatabase.getInstance(this)
    }
}
