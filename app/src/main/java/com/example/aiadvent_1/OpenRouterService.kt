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

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageRequest>
)

data class ChatMessageRequest(
    val role: String,
    val content: String
)

data class ChatCompletionResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessageResponse
)

data class ChatMessageResponse(
    val role: String? = null,
    val content: String
)

interface OpenRouterApi {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Header("HTTP-Referer") httpReferer: String? = null,
        @Header("X-Title") xTitle: String? = null,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

class OpenRouterService {
    private val apiKey = "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"
    private val baseUrl = "https://openrouter.ai/api/"
   private val model = "deepseek/deepseek-chat-v3.1:free"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(OpenRouterApi::class.java)
    
    suspend fun generateResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = ChatCompletionRequest(
                    model = model,
                    messages = listOf(
                        ChatMessageRequest(
                            role = "user",
                            content = userMessage
                        )
                    )
                )
                
                val response = api.createChatCompletion(
                    authorization = "Bearer $apiKey",
                    contentType = "application/json",
                    request = request
                )
                
                response.choices.firstOrNull()?.message?.content
                    ?: "Извините, не удалось получить ответ."
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()

                when {
                    errorMessage.contains("No allowed providers", ignoreCase = true) -> {
                        "Ошибка: $errorMessage"
                    }
                    errorMessage.contains("data policy", ignoreCase = true) -> {
                        "⚠️ Требуется настройка политики данных:\n" +
                        "Ошибка: $errorMessage"
                    }
                    else -> "Произошла ошибка HTTP ${e.code()}: $errorMessage"
                }
            } catch (e: Exception) {
                "Произошла ошибка: ${e.message}"
            }
        }
    }
}

