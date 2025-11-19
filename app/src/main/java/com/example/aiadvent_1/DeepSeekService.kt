package com.example.aiadvent_1

import com.example.aiadvent_1.mcp.McpIntegrationService
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

class DeepSeekService(
    private val mcpIntegrationService: McpIntegrationService? = null,
    private val onReminderStarted: (() -> Unit)? = null
) {
    companion object {
        private const val REMINDER_TAG = "NewsReminder"
    }
    private val apiKey = "sk-6cf38ad6d447491a91dd431618a5e150"
    private val baseUrl = "https://api.deepseek.com/"
    private val model = "deepseek-chat"
    
    // Системный промпт с инструкциями для модели
    private val systemPrompt = """
        Ты - полезный ассистент. Отвечай на вопросы пользователя дружелюбно и информативно.
        
        Когда ты получаешь данные от инструмента reminder (содержащие JSON с новостями из разных стран), 
        ты должна создать краткую и информативную summary на русском языке. Summary должна:
        - Группировать новости по странам
        - Выделять самые важные и интересные новости
        - Учитывать контекст предыдущих новостей, если он предоставлен
        - Быть структурированной и легко читаемой
        - Использовать эмодзи для визуального разделения (📰 для заголовка, 🌍 для стран, • для новостей)
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
            val startTime = System.currentTimeMillis()
            try {
                // Получаем инструменты MCP, если доступны
                val tools = mcpIntegrationService?.getToolsForLlm()?.getOrNull()
                
                // Формируем список сообщений для API
                val apiMessages = mutableListOf<ChatMessageRequest>(
                    ChatMessageRequest(role = "system", content = systemPrompt),
                    ChatMessageRequest(role = "user", content = userMessage)
                )
                
                // Максимальное количество итераций для вызова инструментов
                val maxIterations = 5
                var iteration = 0
                var finalResponse: String? = null
                var lastResponse: ChatCompletionResponse? = null
                
                while (iteration < maxIterations) {
                    val request = ChatCompletionRequest(
                        model = model,
                        messages = apiMessages,
                        max_tokens = 2000,
                        tools = if (tools != null && tools.isNotEmpty()) tools else null,
                        tool_choice = if (tools != null && tools.isNotEmpty()) "auto" else null
                    )
                    
                    val response = api.createChatCompletion(
                        authorization = "Bearer $apiKey",
                        contentType = "application/json",
                        request = request
                    )
                    
                    lastResponse = response
                    val message = response.choices.firstOrNull()?.message
                        ?: break
                    
                    // Добавляем ответ модели в историю
                    apiMessages.add(
                        ChatMessageRequest(
                            role = message.role ?: "assistant",
                            content = message.content,
                            tool_calls = message.tool_calls
                        )
                    )
                    
                    // Проверяем, есть ли вызовы инструментов
                    val toolCalls = message.tool_calls
                    if (toolCalls != null && toolCalls.isNotEmpty() && mcpIntegrationService != null) {
                        Log.d("DeepSeekService", "Обнаружено ${toolCalls.size} вызовов инструментов")
                        
                        // Выполняем все вызовы инструментов
                        for (toolCall in toolCalls) {
                            val toolName = toolCall.function.name
                            val arguments = toolCall.function.arguments
                            
                            Log.d("DeepSeekService", "Вызов инструмента: $toolName с аргументами: $arguments")
                            
                            // Проверяем, был ли вызван reminder с action="start"
                            if (toolName == "reminder") {
                                val hasStartAction = arguments.contains("\"action\"") && 
                                                   (arguments.contains("\"start\"") || 
                                                    arguments.contains("start") ||
                                                    arguments.contains("'start'"))
                                if (hasStartAction) {
                                    Log.d(REMINDER_TAG, "Обнаружен запуск reminder через инструмент (arguments: $arguments)")
                                    onReminderStarted?.invoke()
                                }
                            }
                            
                            val toolResult = mcpIntegrationService.callTool(toolName, arguments)
                            
                            // Добавляем результат вызова инструмента в историю
                            apiMessages.add(
                                ChatMessageRequest(
                                    role = "tool",
                                    content = toolResult.getOrElse { error ->
                                        "Ошибка выполнения инструмента: ${error.message}"
                                    },
                                    tool_call_id = toolCall.id
                                )
                            )
                        }
                        
                        iteration++
                        continue // Продолжаем цикл для получения финального ответа
                    } else {
                        // Получен финальный ответ без вызовов инструментов
                        finalResponse = message.content ?: "Извините, не удалось получить ответ."
                        break
                    }
                }
                
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val responseTimeSeconds = responseTime / 1000.0
                
                val content = finalResponse ?: "Извините, не удалось получить ответ."
                
                // Получаем информацию о токенах из последнего ответа API
                val usage = lastResponse?.usage
                val promptTokens = usage?.prompt_tokens ?: 0
                val completionTokens = usage?.completion_tokens ?: 0
                val totalTokens = usage?.total_tokens ?: (promptTokens + completionTokens)
                
                // Логируем время ответа и токены
                Log.d("DeepSeekService", "Время ответа: ${String.format("%.2f", responseTimeSeconds)}s, Токены: $totalTokens, Итераций: $iteration")
                
                // Формируем строку с информацией о токенах
                val tokensInfo = if (totalTokens > 0) {
                    "🔢 Токены: входные $promptTokens, выходные $completionTokens, всего $totalTokens"
                } else {
                    "🔢 Токены: информация недоступна"
                }
                
                val toolsInfo = if (iteration > 0) {
                    "\n🔧 Вызвано инструментов: $iteration"
                } else {
                    ""
                }
                
                // Добавляем время ответа и информацию о токенах в конец сообщения для отображения в UI
                "$content\n\n⏱ Время ответа: ${String.format("%.2f", responseTimeSeconds)}s$toolsInfo\n$tokensInfo"
            } catch (e: HttpException) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val errorBody = e.response()?.errorBody()?.string()
                val errorMessage = errorBody ?: e.message()
                Log.e("DeepSeekService", "Ошибка HTTP ${e.code()} за ${responseTime}ms: $errorMessage")
                
                // Специальная обработка ошибки "Content Exists Risk"
                if (e.code() == 400 && errorBody?.contains("Content Exists Risk", ignoreCase = true) == true) {
                    "⚠️ Контент содержит недопустимые данные согласно политике безопасности API. Попробуйте запросить другие новости или сократить объем данных."
                } else {
                    "Произошла ошибка HTTP ${e.code()}: $errorMessage"
                }
            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                Log.e("DeepSeekService", "Ошибка за ${responseTime}ms: ${e.message}", e)
                "Произошла ошибка: ${e.message}"
            }
        }
    }
}

