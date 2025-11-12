package com.example.aiadvent_1

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface OpenHandsApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

class OpenHandsService {
    private val apiKey = "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"
    private val baseUrl = "https://router.huggingface.co/"
    private val model = "deepcogito/cogito-v1-preview-llama-8B:featherless-ai"
    
    // Системный промпт с инструкциями для модели
    private val systemPrompt = """
        Ты - полезный ассистент. Отвечай на вопросы пользователя дружелюбно и информативно.
    """.trimIndent()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(OpenHandsApi::class.java)
    
    suspend fun generateResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Формируем список сообщений для API - только системный промпт и текущее сообщение пользователя
                val apiMessages = listOf(
                    ChatMessageRequest(role = "system", content = systemPrompt),
                    ChatMessageRequest(role = "user", content = userMessage)
                )
                
                val request = ChatCompletionRequest(
                    model = model,
                    messages = apiMessages,
                    max_tokens = 2000
                )
                
                val response = api.createChatCompletion(
                    authorization = "Bearer $apiKey",
                    contentType = "application/json",
                    request = request
                )
                
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val responseTimeSeconds = responseTime / 1000.0
                
                val content = response.choices.firstOrNull()?.message?.content
                    ?: "Извините, не удалось получить ответ."
                
                // Извлекаем информацию о токенах
                val usage = response.usage
                val promptTokens = usage?.prompt_tokens ?: 0
                val completionTokens = usage?.completion_tokens ?: 0
                val totalTokens = usage?.total_tokens ?: (promptTokens + completionTokens)
                
                // Логируем время ответа и токены
                Log.d("OpenHandsService", "Время ответа модели: ${responseTime}ms (${String.format("%.2f", responseTimeSeconds)}s)")
                Log.d("OpenHandsService", "Токены - Входные: $promptTokens, Выходные: $completionTokens, Всего: $totalTokens")
                
                // Формируем строку с информацией о токенах
                val tokensInfo = if (totalTokens > 0) {
                    "🔢 Токены: входные $promptTokens, выходные $completionTokens, всего $totalTokens"
                } else {
                    "🔢 Токены: информация недоступна"
                }
                
                // Добавляем время ответа и информацию о токенах в конец сообщения для отображения в UI
                "$content\n\n⏱ Время ответа: ${String.format("%.2f", responseTimeSeconds)}s\n$tokensInfo"
            } catch (e: HttpException) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()
                Log.e("OpenHandsService", "Ошибка HTTP ${e.code()} за ${responseTime}ms: $errorMessage")
                "Произошла ошибка HTTP ${e.code()}: $errorMessage"
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                Log.e("OpenHandsService", "Ошибка за ${responseTime}ms: ${e.message}")
                "Произошла ошибка: ${e.message}"
            }
        }
    }
}

