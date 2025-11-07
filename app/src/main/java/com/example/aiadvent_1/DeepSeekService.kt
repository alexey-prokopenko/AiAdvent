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
    
    suspend fun generateResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
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
                
                response.choices.firstOrNull()?.message?.content
                    ?: "Извините, не удалось получить ответ."
            } catch (e: HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()
                "Произошла ошибка HTTP ${e.code()}: $errorMessage"
            } catch (e: Exception) {
                "Произошла ошибка: ${e.message}"
            }
        }
    }
}

