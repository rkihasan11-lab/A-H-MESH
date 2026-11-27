package com.ahmesh.app.core.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DataStoreThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) : ThemeManager {

    private val prefs = context.getSharedPreferences("ahmesh_theme_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var hasExplicitUpdate = false

    private val _themeMode = MutableStateFlow(resolveInitialTheme())
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    override fun setThemeMode(mode: AppThemeMode) {
        hasExplicitUpdate = true
        _themeMode.value = mode
        val isDark = mode == AppThemeMode.DARK
        prefs.edit().putBoolean(KEY_IS_DARK_MODE, isDark).apply()
    }

    private fun resolveInitialTheme(): AppThemeMode {
        if (hasExplicitUpdate) {
            return _themeMode.value
        }
        return if (prefs.contains(KEY_IS_DARK_MODE)) {
            if (prefs.getBoolean(KEY_IS_DARK_MODE, true)) {
                AppThemeMode.DARK
            } else {
                AppThemeMode.LIGHT
            }
        } else {
            AppThemeMode.DARK
        }
    }

    private companion object {
        const val KEY_IS_DARK_MODE = "is_dark_mode"
    }
}
