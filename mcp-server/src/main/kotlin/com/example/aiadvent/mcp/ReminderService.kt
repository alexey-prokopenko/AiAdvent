package com.example.aiadvent.mcp

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Сервис для управления напоминаниями о новостях
 */
class ReminderService(
    private val newsApiClient: NewsApiClient
) {
    private var reminderJob: Job? = null
    private var isRunning = false
    private val newsData = mutableListOf<NewsData>()
    private val countries = listOf("us", "ru", "gb", "de", "fr", "jp", "cn", "in", "br", "au")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    data class NewsData(
        val timestamp: String,
        val countries: List<String>,
        val newsByCountry: Map<String, JsonObject> // Ключ - код страны, значение - JSON ответ от NewsAPI
    )
    
    /**
     * Запустить reminder
     */
    fun startReminder(intervalSeconds: Long = 40) {
        if (isRunning) {
            return
        }
        
        isRunning = true
        reminderJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    // Получаем новости из разных стран
                    val newsData = collectNewsData()
                    this@ReminderService.newsData.add(newsData)
                    
                    // Ограничиваем размер истории (храним последние 10 наборов данных)
                    if (this@ReminderService.newsData.size > 10) {
                        this@ReminderService.newsData.removeAt(0)
                    }
                    
                    System.err.println("[REMINDER] Collected news data at ${newsData.timestamp}")
                    System.err.println("[REMINDER] Countries: ${newsData.countries.joinToString(", ")}")
                    System.err.println("[REMINDER] Total articles: ${newsData.newsByCountry.values.sumOf { 
                        it["articles"]?.jsonArray?.size ?: 0 
                    }}")
                    
                } catch (e: Exception) {
                    System.err.println("[REMINDER] Error collecting news data: ${e.message}")
                    e.printStackTrace()
                }
                
                delay(intervalSeconds * 1000)
            }
        }
        
        System.err.println("[REMINDER] Started with interval ${intervalSeconds}s")
    }
    
    /**
     * Остановить reminder
     */
    fun stopReminder() {
        isRunning = false
        reminderJob?.cancel()
        reminderJob = null
        System.err.println("[REMINDER] Stopped")
    }
    
    /**
     * Получить последние данные новостей
     */
    fun getLatestNewsData(): NewsData? {
        return newsData.lastOrNull()
    }
    
    /**
     * Получить последние данные новостей в формате JSON для LLM
     */
    fun getLatestNewsDataAsJson(): JsonObject? {
        val latest = newsData.lastOrNull() ?: return null
        
        return buildJsonObject {
            put("timestamp", latest.timestamp)
            putJsonArray("countries") {
                latest.countries.forEach { add(it) }
            }
            putJsonObject("news") {
                latest.newsByCountry.forEach { (country, data) ->
                    put(country, data)
                }
            }
            // Добавляем контекст предыдущих данных, если есть
            if (newsData.size > 1) {
                val previous = newsData[newsData.size - 2]
                putJsonObject("previousContext") {
                    put("timestamp", previous.timestamp)
                    putJsonArray("countries") {
                        previous.countries.forEach { add(it) }
                    }
                }
            }
        }
    }
    
    /**
     * Проверить, запущен ли reminder
     */
    fun isReminderRunning(): Boolean {
        return isRunning
    }
    
    /**
     * Сбор данных новостей из разных стран
     */
    private suspend fun collectNewsData(): NewsData {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val selectedCountries = countries.shuffled().take(3) // Берем 3 случайные страны
        
        // Расширенный диапазон дат вокруг 19 ноября 2025 года (несколько дней для большего охвата)
        val fromDate = "2025-11-17"
        val toDate = "2025-11-21"
        
        System.err.println("[REMINDER] Requesting news for date range: $fromDate to $toDate (around November 19, 2025)")
        
        val newsByCountry = mutableMapOf<String, JsonObject>()
        
        // Получаем новости из каждой страны с расширенными запросами
        for (country in selectedCountries) {
            try {
                // Используем более конкретные запросы для каждой страны
                val (query, language) = when (country) {
                    "ru" -> "Россия OR Москва OR политика OR экономика" to "ru"
                    "us" -> "United States OR USA OR America OR politics OR economy" to "en"
                    "gb" -> "United Kingdom OR UK OR Britain OR London OR politics" to "en"
                    "de" -> "Germany OR Deutschland OR Berlin OR politics OR economy" to "de"
                    "fr" -> "France OR Paris OR politique OR économie" to "fr"
                    "jp" -> "Japan OR Tokyo OR 日本 OR politics OR economy" to "ja"
                    "cn" -> "China OR Beijing OR 中国 OR politics OR economy" to "zh"
                    "in" -> "India OR New Delhi OR politics OR economy" to "en"
                    "br" -> "Brazil OR Brasil OR São Paulo OR política OR economia" to "pt"
                    "au" -> "Australia OR Sydney OR Melbourne OR politics OR economy" to "en"
                    else -> "world OR international OR news" to "en"
                }
                
                val headlines = newsApiClient.searchNews(
                    q = query,
                    from = fromDate,
                    to = toDate,
                    sortBy = "publishedAt",
                    language = language,
                    pageSize = 10
                )
                
                // Фильтруем удаленные статьи
                val articles = headlines["articles"]?.jsonArray
                if (articles != null) {
                    val filteredArticles = articles.filter { article ->
                        val title = article.jsonObject["title"]?.jsonPrimitive?.content
                        title != null && !title.contains("Removed", ignoreCase = true)
                    }
                    
                    // Создаем новый JsonObject с отфильтрованными статьями
                    val filteredHeadlines = buildJsonObject {
                        headlines.forEach { (key, value) ->
                            if (key == "articles") {
                                putJsonArray(key) {
                                    filteredArticles.forEach { add(it) }
                                }
                            } else {
                                put(key, value)
                            }
                        }
                    }
                    
                    newsByCountry[country] = filteredHeadlines
                } else {
                    newsByCountry[country] = headlines
                }
                
                // Небольшая задержка между запросами
                delay(500)
            } catch (e: Exception) {
                System.err.println("[REMINDER] Error fetching news for $country: ${e.message}")
                // Добавляем пустой объект в случае ошибки
                newsByCountry[country] = buildJsonObject {
                    put("status", "error")
                    put("message", e.message ?: "Unknown error")
                    putJsonArray("articles") {}
                }
            }
        }
        
        return NewsData(
            timestamp = timestamp,
            countries = selectedCountries,
            newsByCountry = newsByCountry
        )
    }
    
    /**
     * Очистка ресурсов
     */
    fun close() {
        stopReminder()
        scope.cancel()
    }
}

