package com.example.aiadvent.mcp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

class NewsApiClient(private val apiKey: String) {
    private val baseUrl = "https://newsapi.org/v2"
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    /**
     * Поиск новостей
     */
    suspend fun searchNews(
        q: String,
        from: String? = null,
        to: String? = null,
        sortBy: String = "popularity",
        language: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/everything"
        
        val queryParams = buildString {
            append("q=${URLEncoder.encode(q, "UTF-8")}")
            append("&sortBy=$sortBy")
            append("&page=$page")
            append("&pageSize=${minOf(pageSize, 100)}")
            append("&apiKey=$apiKey")
            from?.let { append("&from=$it") }
            to?.let { append("&to=$it") }
            language?.let { append("&language=$it") }
        }
        
        System.err.println("[DEBUG] API Request: $url?$queryParams")
        
        val response = client.get("$url?$queryParams") {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "MCP-NewsAPI-Server-Kotlin/1.0.0")
            }
        }
        
        val responseBody = response.body<String>()
        System.err.println("[DEBUG] API Response Status: ${response.status}")
        System.err.println("[DEBUG] API Response size: ${responseBody.length} bytes")
        
        val json = Json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw Exception("Invalid JSON response")
        
        // Логируем метаданные
        json["status"]?.let { System.err.println("[DEBUG] Status: $it") }
        json["totalResults"]?.let { System.err.println("[DEBUG] Total results: $it") }
        json["articles"]?.let { articles ->
            val articlesList = articles.toString()
            System.err.println("[DEBUG] Articles count: ${articlesList.split("\"title\"").size - 1}")
        }
        
        // Логируем превью ответа
        val preview = responseBody.take(1000)
        System.err.println("[DEBUG] Response preview (first 1000 chars):")
        System.err.println(preview)
        
        json
    }
    
    /**
     * Получить топ новости
     */
    suspend fun getTopHeadlines(
        country: String? = null,
        category: String? = null,
        sources: String? = null,
        q: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/top-headlines"
        
        val queryParams = buildString {
            append("page=$page")
            append("&pageSize=${minOf(pageSize, 100)}")
            append("&apiKey=$apiKey")
            country?.let { append("&country=$it") }
            category?.let { append("&category=$it") }
            sources?.let { append("&sources=$it") }
            q?.let { append("&q=${URLEncoder.encode(it, "UTF-8")}") }
        }
        
        System.err.println("[DEBUG] API Request: $url?$queryParams")
        
        val response = client.get("$url?$queryParams") {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "MCP-NewsAPI-Server-Kotlin/1.0.0")
            }
        }
        
        val responseBody = response.body<String>()
        System.err.println("[DEBUG] API Response Status: ${response.status}")
        System.err.println("[DEBUG] API Response size: ${responseBody.length} bytes")
        
        val json = Json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw Exception("Invalid JSON response")
        
        // Логируем метаданные
        json["status"]?.let { System.err.println("[DEBUG] Status: $it") }
        json["totalResults"]?.let { System.err.println("[DEBUG] Total results: $it") }
        json["articles"]?.let { articles ->
            val articlesList = articles.toString()
            System.err.println("[DEBUG] Articles count: ${articlesList.split("\"title\"").size - 1}")
        }
        
        // Логируем превью ответа
        val preview = responseBody.take(1000)
        System.err.println("[DEBUG] Response preview (first 1000 chars):")
        System.err.println(preview)
        
        json
    }
    
    /**
     * Получить источники новостей
     */
    suspend fun getSources(
        category: String? = null,
        language: String? = null,
        country: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/sources"
        
        val queryParams = buildString {
            append("apiKey=$apiKey")
            category?.let { append("&category=$it") }
            language?.let { append("&language=$it") }
            country?.let { append("&country=$it") }
        }
        
        System.err.println("[DEBUG] API Request: $url?$queryParams")
        
        val response = client.get("$url?$queryParams") {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "MCP-NewsAPI-Server-Kotlin/1.0.0")
            }
        }
        
        val responseBody = response.body<String>()
        System.err.println("[DEBUG] API Response Status: ${response.status}")
        System.err.println("[DEBUG] API Response size: ${responseBody.length} bytes")
        
        val json = Json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw Exception("Invalid JSON response")
        
        // Логируем метаданные
        json["status"]?.let { System.err.println("[DEBUG] Status: $it") }
        json["sources"]?.let { sources ->
            val sourcesList = sources.toString()
            System.err.println("[DEBUG] Sources count: ${sourcesList.split("\"id\"").size - 1}")
        }
        
        // Логируем превью ответа
        val preview = responseBody.take(1000)
        System.err.println("[DEBUG] Response preview (first 1000 chars):")
        System.err.println(preview)
        
        json
    }
    
    fun close() {
        client.close()
    }
}

