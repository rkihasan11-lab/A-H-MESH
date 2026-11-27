package com.ahmesh.app.core.localization

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    BENGALI("bn");

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
