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

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageRequest>,
    val max_tokens: Int? = null,
    val stop: List<String>? = null
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
        Ты - ассистент, который помогает собирать информацию и структурировать её в виде результата.
        
        Твоя задача:
        1. В процессе диалога собирать информацию от пользователя
        2. Задавать уточняющие вопросы, если информации недостаточно
        3. КРИТИЧЕСКИ ВАЖНО: Задавай ТОЛЬКО ОДИН вопрос за раз. НИКОГДА не задавай несколько вопросов одновременно.
        4. После каждого вопроса жди ответа пользователя перед тем, как задать следующий вопрос
        5. Когда соберешь достаточно информации, САМОСТОЯТЕЛЬНО остановись и выдай финальный результат
        
        Пример работы:
        - Если пользователь просит составить ТЗ (техническое задание), ты должен:
          * Собрать все требования к проекту
          * Задавать вопросы ПОСЛЕДОВАТЕЛЬНО: сначала один вопрос, дождаться ответа, затем следующий вопрос
          * Уточнить детали (функционал, технологии, сроки, бюджет и т.д.) - но по одному вопросу за раз
          * Когда информации будет достаточно, САМОСТОЯТЕЛЬНО выдать готовое ТЗ в структурированном виде
          * Не задавай больше вопросов после того, как собрал достаточно информации
        
        Правила для вопросов:
        - В каждом ответе задавай МАКСИМУМ ОДИН вопрос
        - НЕ перечисляй несколько вопросов списком
        - НЕ используй фразы типа "У меня несколько вопросов:" или "Вот вопросы:"
        - Задай один вопрос, дождись ответа, затем задай следующий
        
        Формат финального результата:
        Когда ты готов выдать финальный результат, начни свой ответ с маркера: "[РЕЗУЛЬТАТ]"
        После маркера [РЕЗУЛЬТАТ] выведи только финальный результат в структурированном виде без дополнительных комментариев или вопросов.
        
        Важно:
        - Ты должен САМОСТОЯТЕЛЬНО определить момент, когда информации достаточно
        - После выдачи результата с маркером [РЕЗУЛЬТАТ] не продолжай диалог
        - Если информации недостаточно, задавай вопросы до тех пор, пока не соберешь всё необходимое
        - ЗАПОМНИ: Один ответ = один вопрос. Никогда не задавай несколько вопросов в одном сообщении
        
        Ограничения:
        - Максимальная длина ответа: 2000 токенов
        - Если достигнут лимит токенов, остановись и выдай то, что успел собрать с маркером [РЕЗУЛЬТАТ]
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
    
    suspend fun generateResponse(messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            try {
                // Формируем список сообщений для API
                val apiMessages = mutableListOf<ChatMessageRequest>().apply {
                    // Добавляем системный промпт в начало
                    add(ChatMessageRequest(role = "system", content = systemPrompt))
                    
                    // Добавляем историю сообщений
                    messages.forEach { message ->
                        add(
                            ChatMessageRequest(
                                role = if (message.isFromUser) "user" else "assistant",
                                content = message.text
                            )
                        )
                    }
                }
                
                val request = ChatCompletionRequest(
                    model = model,
                    messages = apiMessages,
                    max_tokens = 2000 // Ограничение на длину ответа - модель остановится автоматически
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

