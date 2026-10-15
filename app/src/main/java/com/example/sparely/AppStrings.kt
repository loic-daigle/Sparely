package com.example.sparely

import android.content.Context
import androidx.annotation.StringRes

/**
 * Translated text for code that has no Context of its own, like the display names of domain enums.
 * Uses the application context, which follows the language chosen in Settings (see setAppLocale).
 * Outside the app (unit tests) there is no context, so the English [fallback] is returned.
 */
object AppStrings {
    @Volatile
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun get(@StringRes id: Int, fallback: String, vararg formatArgs: Any): String =
        context?.getString(id, *formatArgs) ?: fallback
}
