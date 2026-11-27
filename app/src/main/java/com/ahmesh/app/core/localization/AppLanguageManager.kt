package com.ahmesh.app.core.localization

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLanguageManager @Inject constructor() : LanguageManager {

    private val _currentLanguage = MutableStateFlow(resolveInitialLanguage())
    override val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    override fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(language.code)
        )
    }

    private fun resolveInitialLanguage(): AppLanguage {
        val activeTag = AppCompatDelegate.getApplicationLocales().get(0)?.language
        return AppLanguage.fromCode(activeTag)
    }
}
