package com.pulsecast.app.playback

/**
 * Constantes partagées entre [PlaybackService] et les futurs contrôleurs UI
 * (Phases 3/4) : identifiants des commandes personnalisées de la
 * MediaSession, valeurs de saut temporel, bornes de vitesse et cadence de
 * sauvegarde de la position de lecture.
 */
object PlaybackConstants {

    // Identifiants des SessionCommand personnalisées. Doivent rester
    // stables dans le temps : ce sont ces chaînes que l'UI enverra via
    // MediaController.sendCustomCommand().
    const val CUSTOM_COMMAND_SEEK_BACK_10 = "com.pulsecast.app.SEEK_BACK_10"
    const val CUSTOM_COMMAND_SEEK_BACK_30 = "com.pulsecast.app.SEEK_BACK_30"
    const val CUSTOM_COMMAND_SEEK_FORWARD_20 = "com.pulsecast.app.SEEK_FORWARD_20"
    const val CUSTOM_COMMAND_SEEK_FORWARD_40 = "com.pulsecast.app.SEEK_FORWARD_40"
    const val CUSTOM_COMMAND_SEEK_FORWARD_60 = "com.pulsecast.app.SEEK_FORWARD_60"
    const val CUSTOM_COMMAND_SET_SPEED = "com.pulsecast.app.SET_SPEED"

    /** Clé du Bundle transportant la vitesse demandée (Float) avec [CUSTOM_COMMAND_SET_SPEED]. */
    const val EXTRA_SPEED = "extra_speed"

    const val SEEK_BACK_10_MS = 10_000L
    const val SEEK_BACK_30_MS = 30_000L
    const val SEEK_FORWARD_20_MS = 20_000L
    const val SEEK_FORWARD_40_MS = 40_000L
    const val SEEK_FORWARD_60_MS = 60_000L

    const val MIN_SPEED = 0.8f
    const val MAX_SPEED = 2.5f

    /** Cadence de sauvegarde de la position de lecture pendant la lecture active. */
    const val POSITION_SAVE_INTERVAL_MS = 5_000L
}
