package com.example.aiadvent_1

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiService {
    private val apiKey = BuildConfig.GEMINI_API_KEY
    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun generateResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val response = model.generateContent(userMessage)
                response.text ?: "Извините, не удалось получить ответ."
            } catch (e: Exception) {
                "Произошла ошибка: ${e.message}"
            }
        }
    }
} 