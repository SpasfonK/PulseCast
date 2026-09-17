package com.pulsecast.app.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Expose le thème sélectionné sous forme de [StateFlow] prêt pour Compose
 * (`collectAsState()`), et centralise l'écriture des changements de choix
 * dans [ThemePreferencesRepository].
 *
 * `AndroidViewModel` (plutôt que `ViewModel` nu) permet d'obtenir un
 * [Context][android.content.Context] applicatif sans Factory personnalisée :
 * `viewModel()` sait instancier ce constructeur nativement.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ThemePreferencesRepository(application)

    val selectedTheme: StateFlow<AppTheme> = repository.selectedTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppTheme.Default
        )

    fun selectTheme(theme: AppTheme) {
        viewModelScope.launch {
            repository.setTheme(theme)
        }
    }
}
