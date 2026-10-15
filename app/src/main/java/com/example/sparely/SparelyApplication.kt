package com.example.sparely

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.example.sparely.notifications.NotificationHelper
import com.example.sparely.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Locale

class SparelyApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)
        NotificationHelper.ensureChannels(this)
        container = DefaultAppContainer(this)
        
        // Apply saved locale
        applicationScope.launch {
            applyStoredLocale()
        }
    }
    
    private suspend fun applyStoredLocale() {
        val preferencesRepository = UserPreferencesRepository(this)
        val settings = preferencesRepository.settingsFlow.firstOrNull()

        settings?.regionalSettings?.let { regional ->
            // Use both language and country when available so Android will prefer
            // region-specific resource qualifiers (e.g. values-fr-rCA) and fall
            // back to the base language automatically if the regionized
            // resources are not present.
            setAppLocale(regional.languageCode, regional.countryCode)
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applicationScope.launch {
            applyStoredLocale()
        }
    }
}

fun Context.setAppLocale(languageCode: String, countryCode: String? = null) {
    // Accept a language and optional country to construct Locale properly.
    // This ensures Android will match values-<lang>-r<COUNTRY> if present.
    val locale = try {
        if (!countryCode.isNullOrBlank()) {
            Locale.Builder()
                .setLanguage(languageCode)
                .setRegion(countryCode)
                .build()
        } else {
            // languageCode might itself be a BCP-47 tag like "fr-CA" or "fr_CA".
            val tag = languageCode.replace('_', '-')
            Locale.forLanguageTag(tag)
        }
    } catch (e: Exception) {
        Locale.getDefault()
    }

    Locale.setDefault(locale)

    val config = Configuration(resources.configuration)
    config.setLocale(locale)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        createConfigurationContext(config)
    }

    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)
}
