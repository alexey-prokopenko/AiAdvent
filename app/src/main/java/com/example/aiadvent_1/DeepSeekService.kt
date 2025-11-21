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
    private val onReminderStarted: (() -> Unit)? = null,
    private val onIntermediateResponse: ((String) -> Unit)? = null
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
        
        ВАЖНО: Ты можешь вызывать инструменты последовательно (цепочкой) для выполнения сложных задач.
        
        У ТЕБЯ ДОСТУПНЫ ДВА MCP СЕРВЕРА:
        1. NewsAPI - для поиска новостей (инструменты: search_news, get_top_headlines, get_sources, reminder)
        2. OpenWeatherMap - для получения погоды (инструменты: get_current_weather_by_city, get_current_weather_by_coordinates, get_current_weather_by_zip)
        
        АВТОМАТИЧЕСКАЯ ЦЕПОЧКА ВЫЗОВОВ ДЛЯ НОВОСТЕЙ И ПОГОДЫ:
        Когда пользователь просит найти новости, интересные темы, или просто "найди новости", автоматически выполни следующую цепочку действий:
        
        ШАГ 1: Запроси новости за вчерашний день
        - Вычисли вчерашнюю дату в формате YYYY-MM-DD (например, если сегодня 2025-11-20, то вчера было 2025-11-19)
        - Вызови инструмент search_news с параметрами:
          * q="news" или q="*" (для получения общих новостей)
          * from=вчерашняя_дата
          * to=вчерашняя_дата
          * sortBy="popularity"
          * pageSize=20
        
        ШАГ 2: Проанализируй результаты первого вызова
        - Изучи полученные новости из ответа инструмента
        - Из ПЕРВОЙ новости (самой популярной) извлеки информацию о стране
          * Ищи упоминания стран в заголовке, описании или источнике новости
          * Используй коды стран ISO (например, "us" для США, "ru" для России, "gb" для Великобритании)
        - Определи столицу этой страны (см. маппинг стран ниже)
        - Если страну определить невозможно, используй страну источника новости или пропусти этот шаг
        
        ШАГ 3: Запроси погоду в столице страны из первой новости
        - После определения страны и её столицы, вызови инструмент get_current_weather_by_city:
          * city=название_столицы (например, "Moscow", "London", "Washington")
          * units="metric" (для градусов Цельсия)
          * lang="ru" (для русского языка описания погоды)
        - Выведи информацию о погоде в столице этой страны
        
        ШАГ 4: Запроси дополнительные новости по выбранной теме (опционально)
        - Если нужно, запроси дополнительные новости по выбранной теме:
          * q=выбранная_тема
          * from=вчерашняя_дата
          * to=вчерашняя_дата
          * sortBy="popularity"
          * pageSize=2
        
        МАППИНГ СТРАН И ИХ СТОЛИЦ:
        США (us) -> Washington или Washington, DC
        Россия (ru) -> Moscow
        Великобритания (gb, uk) -> London
        Германия (de) -> Berlin
        Франция (fr) -> Paris
        Япония (jp) -> Tokyo
        Китай (cn) -> Beijing
        Индия (in) -> New Delhi
        Бразилия (br) -> Brasilia
        Австралия (au) -> Canberra
        Италия (it) -> Rome
        Испания (es) -> Madrid
        Канада (ca) -> Ottawa
        Южная Корея (kr) -> Seoul
        Мексика (mx) -> Mexico City
        
        Если страна не найдена в списке, попробуй определить столицу по названию страны или используй основной город страны.
        
        ВАЖНО: Выполняй эти шаги строго последовательно и ВЫВОДИ РЕЗУЛЬТАТЫ КАЖДОГО ШАГА СРАЗУ после его выполнения:
        
        После ШАГА 1 (получения новостей за вчера):
        - СРАЗУ выведи в чат результат: "📰 ШАГ 1: Новости за вчерашний день"
        - Покажи краткую сводку полученных новостей (3-5 самых интересных заголовков)
        - Особое внимание обрати на первую новость и определи из неё страну
        
        После ШАГА 2 (анализа страны):
        - Выведи: "🌍 Определена страна из первой новости: [название страны] (столица: [название столицы])"
        
        После ШАГА 3 (получения погоды):
        - СРАЗУ выведи в чат результат: "🌤️ ШАГ 3: Погода в столице [название столицы], [название страны]"
        - Покажи ключевую информацию о погоде: температура, описание, влажность, скорость ветра
        
        После ШАГА 4 (дополнительные новости - если выполнялся):
        - СРАЗУ выведи в чат результат: "🔍 ШАГ 4: Дополнительные новости по теме '[название темы]'"
        - Покажи найденные новости с их заголовками и кратким описанием
        
        В конце (если нужно) предоставь краткую итоговую сводку, но НЕ дублируй информацию, которая уже была выведена в промежуточных результатах.
        
        КРИТИЧЕСКИ ВАЖНО: 
        - После каждого вызова инструмента и получения результата, ТЫ ДОЛЖЕН СРАЗУ вывести промежуточный результат в чат
        - НЕ накапливай результаты - выводи их сразу после каждого шага
        - Пользователь должен видеть прогресс выполнения каждого шага в реальном времени, как только он выполнен
        - Каждый промежуточный результат должен быть отдельным сообщением
        - После получения новостей ОБЯЗАТЕЛЬНО извлеки страну из первой новости и запроси погоду в столице этой страны
        
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
                
                // Максимальное количество итераций для вызова инструментов (увеличено для цепочек вызовов)
                val maxIterations = 10
                var iteration = 0
                val intermediateResponses = mutableListOf<String>() // Собираем промежуточные ответы
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
                    
                    // Сохраняем промежуточный ответ, если он есть (даже если будут еще вызовы инструментов)
                    if (message.content != null && message.content.isNotBlank()) {
                        intermediateResponses.add(message.content)
                        Log.d("DeepSeekService", "Получен промежуточный ответ на итерации $iteration: ${message.content.take(100)}...")
                        
                        // Отправляем промежуточный ответ сразу пользователю
                        onIntermediateResponse?.invoke(message.content)
                    }
                    
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
                
                // Возвращаем финальный ответ только если он отличается от последнего промежуточного
                // (промежуточные уже отправлены через callback)
                val lastIntermediate = intermediateResponses.lastOrNull()
                val content = when {
                    finalResponse != null && finalResponse != lastIntermediate -> finalResponse
                    finalResponse != null -> "" // Финальный ответ совпадает с промежуточным, не дублируем
                    lastIntermediate != null -> "" // Используем только промежуточные
                    else -> "Извините, не удалось получить ответ."
                }
                
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

