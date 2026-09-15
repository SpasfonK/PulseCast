package com.pulsecast.app.theme

/**
 * Les 4 identités visuelles proposées par PulseCast.
 *
 * `id` est la valeur persistée en DataStore ([ThemePreferencesRepository]) :
 * elle ne doit jamais changer une fois publiée, sous peine de réinitialiser
 * silencieusement le choix des utilisateurs existants au thème par défaut.
 */
enum class AppTheme(val id: String, val label: String) {
    OLED_DEEP_MINIMAL(id = "oled_deep_minimal", label = "OLED Deep Minimal"),
    GLASSMORPHISM(id = "glassmorphism", label = "Verre Dépoli"),
    NEO_BRUTALIST(id = "neo_brutalist", label = "Néo-Brutaliste"),
    SYNTHWAVE(id = "synthwave", label = "Synthwave / Cyberpunk");

    companion object {
        val Default = OLED_DEEP_MINIMAL

        fun fromId(id: String?): AppTheme = entries.find { it.id == id } ?: Default
    }
}
