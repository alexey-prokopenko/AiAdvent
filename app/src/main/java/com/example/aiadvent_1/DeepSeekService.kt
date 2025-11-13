package com.example.aiadvent_1

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
import android.util.Log

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
    
    suspend fun generateResponse(userMessage: String, conversationHistory: List<ChatMessage> = emptyList()): String {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Формируем список сообщений для API
                val apiMessages = mutableListOf<ChatMessageRequest>()
                
                // Добавляем системный промпт
                apiMessages.add(ChatMessageRequest(role = "system", content = systemPrompt))
                
                // Добавляем историю диалога (только summary и последние сообщения)
                conversationHistory.forEach { message ->
                    val role = if (message.isFromUser) "user" else "assistant"
                    apiMessages.add(ChatMessageRequest(role = role, content = message.text))
                }
                
                // Добавляем текущее сообщение пользователя
                apiMessages.add(ChatMessageRequest(role = "user", content = userMessage))
                
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
                
                // Добавляем время ответа и информацию о токенах в конец сообщения для отображения в UI
                "$content\n\n⏱ Время ответа: ${String.format("%.2f", responseTimeSeconds)}s\n$tokensInfo"
            } catch (e: HttpException) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()
                Log.e("DeepSeekService", "Ошибка HTTP ${e.code()} за ${responseTime}ms: $errorMessage")
                "Произошла ошибка HTTP ${e.code()}: $errorMessage"
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                Log.e("DeepSeekService", "Ошибка за ${responseTime}ms: ${e.message}")
                "Произошла ошибка: ${e.message}"
            }
        }
    }
    
    suspend fun createSummary(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                // Формируем текст для summary
                val conversationText = messages.joinToString("\n") { message ->
                    val role = if (message.isFromUser) "Пользователь" else "Ассистент"
                    "$role: ${message.text}"
                }
                
                val summaryPrompt = """
                    Создай краткое резюме следующего диалога, сохраняя ключевые моменты и контекст.
                    Резюме должно быть на русском языке и содержать основную информацию из диалога.
                    
                    Диалог:
                    $conversationText
                    
                    Резюме:
                """.trimIndent()
                
                val apiMessages = listOf(
                    ChatMessageRequest(role = "system", content = systemPrompt),
                    ChatMessageRequest(role = "user", content = summaryPrompt)
                )
                
                val request = ChatCompletionRequest(
                    model = model,
                    messages = apiMessages,
                    max_tokens = 500
                )
                
                val response = api.createChatCompletion(
                    authorization = "Bearer $apiKey",
                    contentType = "application/json",
                    request = request
                )
                
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                
                val summary = response.choices.firstOrNull()?.message?.content
                    ?: "Не удалось создать резюме."
                
                Log.d("DeepSeekService", "Summary создан за ${responseTime}ms")
                summary
            } catch (e: HttpException) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()
                Log.e("DeepSeekService", "Ошибка HTTP ${e.code()} при создании summary за ${responseTime}ms: $errorMessage")
                "Ошибка при создании резюме: $errorMessage"
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                Log.e("DeepSeekService", "Ошибка при создании summary за ${responseTime}ms: ${e.message}")
                "Ошибка при создании резюме: ${e.message}"
            }
        }
    }
}

