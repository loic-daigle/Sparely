package com.example.sparely.ai

import com.google.mlkit.genai.common.GenAiException

/**
 * True when AICore says this phone doesn't offer the feature. Phones with Gemini Nano but without
 * a given ML Kit API throw this from the status check instead of returning UNAVAILABLE.
 */
internal fun isNotAvailable(e: GenAiException): Boolean =
    e.errorCode == GenAiException.ErrorCode.NOT_AVAILABLE ||
        e.message?.contains("not available", ignoreCase = true) == true

/** Error code plus message, shown in Settings so a failure can be reported precisely. */
internal fun describe(e: GenAiException): String = "AICore error ${e.errorCode}: ${e.message}"
