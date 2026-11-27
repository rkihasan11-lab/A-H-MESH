package com.ahmesh.app.core.localization

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AppLanguageManager @Inject constructor(
    @ApplicationContext private val context: Context
) : LanguageManager {

    private val _currentLanguage = MutableStateFlow(resolveInitialLanguage())
    override val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    override fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        val locale = Locale.forLanguageTag(language.code)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = LocaleList.forLanguageTags(language.code)
        }
    }

    private fun resolveInitialLanguage(): AppLanguage {
        val activeTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales?.get(0)?.language
        } else {
            Locale.getDefault().language
        } ?: Locale.getDefault().language

        return AppLanguage.fromCode(activeTag)
    }
}
