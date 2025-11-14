package com.example.aiadvent_1

import android.util.Log
import com.example.aiadvent_1.memory.MemoryMetadata
import com.example.aiadvent_1.memory.MemoryRecord
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

interface DeepSeekApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

class DeepSeekService {
    private val apiKey = "sk-6cf38ad6d447491a91dd431618a5e150"
    private val baseUrl = "https://api.deepseek.com/"
    private val model = "deepseek-chat"
    
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
    
    private val api = retrofit.create(DeepSeekApi::class.java)
    
    suspend fun generateResponse(
        userMessage: String,
        memoryContext: List<MemoryRecord> = emptyList()
    ): ModelResponse {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val contextualMessages = memoryContext
                    .takeLast(MAX_CONTEXT_MESSAGES)
                    .map { ChatMessageRequest(role = it.role, content = it.content) }

                val apiMessages = mutableListOf(
                    ChatMessageRequest(role = "system", content = systemPrompt)
                ).apply {
                    addAll(contextualMessages)
                    if (contextualMessages.none { it.role == "user" && it.content == userMessage }) {
                        add(ChatMessageRequest(role = "user", content = userMessage))
                    }
                }
                
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
                Log.d("DeepSeekService", "Время ответа модели: ${responseTime}ms (${String.format("%.2f", responseTimeSeconds)}s)")
                Log.d("DeepSeekService", "Токены - Входные: $promptTokens, Выходные: $completionTokens, Всего: $totalTokens")
                
                // Формируем строку с информацией о токенах
                val tokensInfo = if (totalTokens > 0) {
                    "🔢 Токены: входные $promptTokens, выходные $completionTokens, всего $totalTokens"
                } else {
                    "🔢 Токены: информация недоступна"
                }
                
                val messageWithStats = "$content\n\n⏱ Время ответа: ${String.format("%.2f", responseTimeSeconds)}s\n$tokensInfo"
                ModelResponse(
                    message = messageWithStats,
                    metadata = MemoryMetadata(
                        responseTimeMs = responseTime,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens
                    )
                )
            } catch (e: HttpException) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()
                Log.e("DeepSeekService", "Ошибка HTTP ${e.code()} за ${responseTime}ms: $errorMessage")
                ModelResponse(
                    message = "Произошла ошибка HTTP ${e.code()}: $errorMessage",
                    metadata = MemoryMetadata(responseTimeMs = responseTime)
                )
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                Log.e("DeepSeekService", "Ошибка за ${responseTime}ms: ${e.message}")
                ModelResponse(
                    message = "Произошла ошибка: ${e.message}",
                    metadata = MemoryMetadata(responseTimeMs = responseTime)
                )
            }
        }
    }

    companion object {
        private const val MAX_CONTEXT_MESSAGES = 20
    }
}

data class ModelResponse(
    val message: String,
    val metadata: MemoryMetadata
)

