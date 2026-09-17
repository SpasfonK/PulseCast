package com.pulsecast.app.ui.foldable

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map

/**
 * Expose la [FoldingFeature] actuelle de la fenêtre (charnière physique),
 * pour adapter l'UI aux appareils pliables comme le Honor Magic V2.
 *
 * Renvoie `null` en continu sur un appareil non pliable — code purement
 * additif, sans impact sur l'immense majorité des utilisateurs. Le suivi
 * est réattaché à chaque changement d'Activity ([LaunchedEffect]) plutôt
 * que retenu à travers les recréations, conformément à la documentation
 * Jetpack WindowManager (le Flow est associé à l'Activity courante).
 */
@Composable
fun rememberFoldingFeature(): FoldingFeature? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var foldingFeature by remember { mutableStateOf<FoldingFeature?>(null) }

    LaunchedEffect(activity) {
        val currentActivity = activity ?: return@LaunchedEffect
        WindowInfoTracker.getOrCreate(currentActivity)
            .windowLayoutInfo(currentActivity)
            .map { layoutInfo ->
                layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
            .collect { feature -> foldingFeature = feature }
    }

    return foldingFeature
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Postures de lecteur pertinentes, dérivées de la [FoldingFeature] courante. */
sealed interface PlayerLayoutMode {
    /** Téléphone classique, pliable complètement ouvert à plat, ou complètement replié. */
    data object Normal : PlayerLayoutMode

    /**
     * Pliable à moitié ouvert avec une charnière horizontale : posture
     * "tente"/tabletop (Honor Magic V2 posé, écran cassé en deux par la
     * charnière). Le lecteur y répartit illustration au-dessus de la
     * charnière, contrôles en-dessous. Les bornes sont en pixels, dans le
     * repère de la fenêtre — à convertir en Dp par l'appelant.
     */
    data class Tabletop(val hingeTopPx: Int, val hingeBottomPx: Int) : PlayerLayoutMode
}

/** Traduit une [FoldingFeature] en mode de mise en page pour le grand lecteur. */
fun FoldingFeature?.toPlayerLayoutMode(): PlayerLayoutMode {
    if (this == null) return PlayerLayoutMode.Normal
    val isTabletop = state == FoldingFeature.State.HALF_OPENED &&
        orientation == FoldingFeature.Orientation.HORIZONTAL
    return if (isTabletop) {
        PlayerLayoutMode.Tabletop(hingeTopPx = bounds.top, hingeBottomPx = bounds.bottom)
    } else {
        PlayerLayoutMode.Normal
    }
}
