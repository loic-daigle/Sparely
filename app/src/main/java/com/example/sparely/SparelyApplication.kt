package com.example.sparely

import android.app.Application
import android.content.Context
import com.example.sparely.notifications.NotificationHelper

class SparelyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppStrings.init(this)
        NotificationHelper.ensureChannels(this)
        container = DefaultAppContainer(this)
    }

    override fun attachBaseContext(base: Context) {
        // Before Android 13, the language picked in Sparely (see AppLanguage); a no-op after
        super.attachBaseContext(AppLanguage.wrap(base))
    }
}
