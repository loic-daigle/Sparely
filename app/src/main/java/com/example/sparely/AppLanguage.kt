package com.example.sparely

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's language, chosen in Settings > Region or during onboarding, independent of the phone's.
 *
 * Android 13+ has per-app languages: the system stores the choice, applies it to every screen and
 * the app's own context (notifications too), restarts the visible screens, and shows it under
 * Settings > Apps > Sparely > Language. Older Android has none of that, so the tag is kept in
 * SharedPreferences, read synchronously when the app and each activity start (see [wrap]), and the
 * open screen is recreated to pick it up.
 */
object AppLanguage {
    /** The languages the app is translated into; anything else would just show English. */
    val SUPPORTED = listOf("en", "fr")

    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    /** "fr" + "CA" → "fr-CA", so region resources (values-fr-rCA) are preferred over values-fr. */
    fun tag(languageCode: String, countryCode: String?): String = try {
        Locale.Builder().setLanguage(languageCode).apply {
            if (!countryCode.isNullOrBlank()) setRegion(countryCode)
        }.build().toLanguageTag()
    } catch (e: Exception) {
        languageCode
    }

    /** The language the user picked, or null while the app still follows the phone. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeIf { !it.isEmpty }?.toLanguageTags()
        } else {
            prefs(context).getString(KEY_TAG, null)
        }

    /** Switches the app to [tag]; the open screen restarts in the new language. No-op if already set. */
    fun apply(activity: Activity, tag: String) {
        if (current(activity).equals(tag, ignoreCase = true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            prefs(activity).edit().putString(KEY_TAG, tag).commit()
            activity.recreate()
        }
    }

    /** Before Android 13: a context whose resources use the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = prefs(base).getString(KEY_TAG, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** The phone's language, when the app supports it ("fr" on a French phone), else null. */
    fun deviceLanguage(): String? =
        Locale.getDefault().language.takeIf { it in SUPPORTED }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
