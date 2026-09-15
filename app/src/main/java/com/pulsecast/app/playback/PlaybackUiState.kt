package com.pulsecast.app.playback

/**
 * État de lecture tel qu'exposé à l'UI Compose par [PlaybackViewModel].
 * Reflète ce que rapporte le [androidx.media3.session.MediaController],
 * rafraîchi par les callbacks de [androidx.media3.common.Player.Listener]
 * et par un ticker de position pendant la lecture active.
 */
data class PlaybackUiState(
    val isConnected: Boolean = false,
    val currentEpisodeId: Long? = null,
    val title: String = "",
    val podcastTitle: String = "",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val errorMessage: String? = null
)
