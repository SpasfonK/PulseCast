package com.pulsecast.app.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.themeDataStore by preferencesDataStore(name = "pulsecast_theme_prefs")

/**
 * Persistance du style visuel choisi, via DataStore Preferences. Une seule
 * clé pour l'instant ([THEME_KEY]) ; d'autres préférences d'affichage
 * pourront rejoindre le même DataStore par la suite sans migration lourde.
 */
class ThemePreferencesRepository(context: Context) {

    private val appContext = context.applicationContext

    val selectedTheme: Flow<AppTheme> = appContext.themeDataStore.data
        .catch { exception ->
            // Un DataStore corrompu ou illisible ne doit pas faire planter
            // l'application : on retombe sur des préférences vides, donc sur
            // le thème par défaut.
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> AppTheme.fromId(preferences[THEME_KEY]) }

    suspend fun setTheme(theme: AppTheme) {
        appContext.themeDataStore.edit { preferences ->
            preferences[THEME_KEY] = theme.id
        }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("selected_theme")
    }
}
