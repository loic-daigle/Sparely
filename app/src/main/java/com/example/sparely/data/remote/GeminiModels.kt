package com.example.sparely.data.remote

/**
 * Request/Response models for Google Gemini API
 */

// Gemini API Request
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null
)

data class Content(
    val role: String = "user",
    val parts: List<Part>
)

data class Part(
    val text: String
)

data class GenerationConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val maxOutputTokens: Int = 1024,
    val stopSequences: List<String> = emptyList()
)

data class SafetySetting(
    val category: String,
    val threshold: String
)

// Gemini API Response
data class GeminiResponse(
    val candidates: List<Candidate>,
    val usageMetadata: UsageMetadata? = null
)

data class Candidate(
    val content: Content,
    val finishReason: String? = null,
    val safetyRatings: List<SafetyRating>? = null
)

data class SafetyRating(
    val category: String,
    val probability: String
)

data class UsageMetadata(
    val promptTokenCount: Int,
    val candidatesTokenCount: Int,
    val totalTokenCount: Int
)

// Error response
data class GeminiError(
    val error: ErrorDetail
)

data class ErrorDetail(
    val code: Int,
    val message: String,
    val status: String
)
