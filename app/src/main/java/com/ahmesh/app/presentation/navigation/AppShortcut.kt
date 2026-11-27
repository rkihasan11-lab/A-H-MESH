package com.ahmesh.app.presentation.navigation

import android.content.Intent

enum class AppShortcutDestination {
    QR_SCANNER,
    ASSISTANT
}

object AppShortcutRouter {
    const val ACTION_OPEN_QR_SCANNER = "com.ahmesh.app.action.OPEN_QR_SCANNER"
    const val ACTION_OPEN_ASSISTANT = "com.ahmesh.app.action.OPEN_ASSISTANT"
    const val EXTRA_SHORTCUT_DESTINATION = "com.ahmesh.app.extra.SHORTCUT_DESTINATION"

    fun parseDestination(intent: Intent?): AppShortcutDestination? {
        if (intent == null) return null
        return when (intent.action) {
            ACTION_OPEN_QR_SCANNER -> AppShortcutDestination.QR_SCANNER
            ACTION_OPEN_ASSISTANT -> AppShortcutDestination.ASSISTANT
            else -> {
                val extra = intent.getStringExtra(EXTRA_SHORTCUT_DESTINATION) ?: return null
                runCatching { AppShortcutDestination.valueOf(extra) }.getOrNull()
            }
        }
    }
}
