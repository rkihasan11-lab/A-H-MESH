package com.ahmesh.app.core.theme

import kotlinx.coroutines.flow.StateFlow

enum class AppThemeMode {
    DARK,
    LIGHT
}

interface ThemeManager {
    val themeMode: StateFlow<AppThemeMode>

    val isDarkMode: Boolean
        get() = themeMode.value == AppThemeMode.DARK

    fun setThemeMode(mode: AppThemeMode)

    fun setDarkMode(enabled: Boolean) {
        setThemeMode(if (enabled) AppThemeMode.DARK else AppThemeMode.LIGHT)
    }

    fun toggleTheme() {
        setThemeMode(
            if (themeMode.value == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
        )
    }
}
