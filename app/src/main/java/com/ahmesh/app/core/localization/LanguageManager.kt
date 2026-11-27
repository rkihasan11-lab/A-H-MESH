package com.ahmesh.app.core.localization

import kotlinx.coroutines.flow.StateFlow

interface LanguageManager {
    val currentLanguage: StateFlow<AppLanguage>
    fun setLanguage(language: AppLanguage)
}
