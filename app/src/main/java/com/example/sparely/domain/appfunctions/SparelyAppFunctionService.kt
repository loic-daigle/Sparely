package com.example.sparely.domain.appfunctions

import android.content.Context
import com.example.sparely.DefaultAppContainer

/**
 * Service provider for SparelyAppFunctions
 *
 * This service makes the SparelyAppFunctions instance available to the rest of the app
 * and to external services that may want to invoke the exposed functions.
 */
class SparelyAppFunctionService(context: Context) {
    private val container = (context.applicationContext as? com.example.sparely.SparelyApplication)?.container
        ?: DefaultAppContainer(context)

    /**
     * Provides the SparelyAppFunctions instance
     */
    fun getAppFunctions(): SparelyAppFunctions = SparelyAppFunctions(container.savingsRepository)
}
