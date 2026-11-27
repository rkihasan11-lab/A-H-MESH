package com.ahmesh.app.core.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ahmesh_theme_prefs"
)

@Singleton
class DataStoreThemeManager @Inject constructor(
    @ApplicationContext context: Context
) : ThemeManager {

    private val dataStore = context.themePreferencesDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var hasExplicitUpdate = false

    private val _themeMode = MutableStateFlow(AppThemeMode.DARK)
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    init {
        scope.launch {
            val storedIsDark = runCatching {
                dataStore.data.firstOrNull()?.get(IS_DARK_MODE_KEY)
            }.getOrNull()
            if (!hasExplicitUpdate && storedIsDark != null) {
                _themeMode.value = if (storedIsDark) AppThemeMode.DARK else AppThemeMode.LIGHT
            }
        }
    }

    override fun setThemeMode(mode: AppThemeMode) {
        hasExplicitUpdate = true
        _themeMode.value = mode
        val isDark = mode == AppThemeMode.DARK
        scope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[IS_DARK_MODE_KEY] = isDark
                }
            }
        }
    }

    private companion object {
        val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
    }
}
