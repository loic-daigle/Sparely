package com.example.sparely.ai

import com.google.mlkit.genai.common.GenAiException

/**
 * True when AICore says this phone doesn't offer the feature. Phones with Gemini Nano but without
 * a given ML Kit API throw this from the status check instead of returning UNAVAILABLE.
 */
internal fun isNotAvailable(e: GenAiException): Boolean =
    e.errorCode == GenAiException.ErrorCode.NOT_AVAILABLE ||
        e.message?.contains("not available", ignoreCase = true) == true

/**
 * Error code plus message, shown in Settings so a failure can be reported precisely. AICore often
 * puts the useful part ("COMPUTE_ERROR", a missing file) in a cause, so causes are included.
 */
internal fun describe(e: GenAiException): String {
    val causes = generateSequence(e.cause) { it.cause }
        .take(4)
        .mapNotNull { it.message }
        .filter { it.isNotBlank() && it != e.message }
        .distinct()
        .toList()
    val detail = if (causes.isEmpty()) "" else " (caused by: ${causes.joinToString(" / ")})"
    return "AICore error ${e.errorCode}: ${e.message}$detail"
}
