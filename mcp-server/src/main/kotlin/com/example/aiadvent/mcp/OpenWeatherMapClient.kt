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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

/**
 * Клиент для взаимодействия с OpenWeatherMap API
 */
class OpenWeatherMapClient(private val apiKey: String) {
    private val baseUrl = "https://api.openweathermap.org/data/2.5"
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    /**
     * Получить текущую погоду по названию города
     */
    suspend fun getCurrentWeatherByCity(
        city: String,
        units: String = "metric",
        lang: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/weather"
        
        val queryParams = buildString {
            append("q=${URLEncoder.encode(city, "UTF-8")}")
            append("&appid=$apiKey")
            append("&units=$units")
            lang?.let { append("&lang=${URLEncoder.encode(it, "UTF-8")}") }
        }
        
        System.err.println("[DEBUG] API Request: $url?$queryParams")
        
        val response = client.get("$url?$queryParams") {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "MCP-OpenWeatherMap-Server-Kotlin/1.0.0")
            }
        }
        
        val responseBody = response.body<String>()
        System.err.println("[DEBUG] API Response Status: ${response.status}")
        System.err.println("[DEBUG] API Response size: ${responseBody.length} bytes")
        
        val json = Json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw Exception("Invalid JSON response")
        
        // Логируем основную информацию
        json["name"]?.let { System.err.println("[DEBUG] City: $it") }
        val mainObj = json["main"] as? JsonObject
        mainObj?.get("temp")?.let { System.err.println("[DEBUG] Temperature: $it") }
        val weatherArray = json["weather"] as? JsonArray
        weatherArray?.firstOrNull()?.let { first ->
            val firstObj = first as? JsonObject
            firstObj?.get("description")?.let {
                System.err.println("[DEBUG] Weather description: $it")
            }
        }
        
        // Логируем превью ответа
        val preview = responseBody.take(1000)
        System.err.println("[DEBUG] Response preview (first 1000 chars):")
        System.err.println(preview)
        
        json
    }
    
    /**
     * Получить текущую погоду по координатам
     */
    suspend fun getCurrentWeatherByCoordinates(
        lat: Double,
        lon: Double,
        units: String = "metric",
        lang: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/weather"
        
        val queryParams = buildString {
            append("lat=$lat")
            append("&lon=$lon")
            append("&appid=$apiKey")
            append("&units=$units")
            lang?.let { append("&lang=${URLEncoder.encode(it, "UTF-8")}") }
        }
        
        System.err.println("[DEBUG] API Request: $url?$queryParams")
        
        val response = client.get("$url?$queryParams") {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "MCP-OpenWeatherMap-Server-Kotlin/1.0.0")
            }
        }
        
        val responseBody = response.body<String>()
        System.err.println("[DEBUG] API Response Status: ${response.status}")
        System.err.println("[DEBUG] API Response size: ${responseBody.length} bytes")
        
        val json = Json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw Exception("Invalid JSON response")
        
        // Логируем основную информацию
        json["name"]?.let { System.err.println("[DEBUG] City: $it") }
        val mainObj = json["main"] as? JsonObject
        mainObj?.get("temp")?.let { System.err.println("[DEBUG] Temperature: $it") }
        val weatherArray = json["weather"] as? JsonArray
        weatherArray?.firstOrNull()?.let { first ->
            val firstObj = first as? JsonObject
            firstObj?.get("description")?.let {
                System.err.println("[DEBUG] Weather description: $it")
            }
        }
        
        // Логируем превью ответа
        val preview = responseBody.take(1000)
        System.err.println("[DEBUG] Response preview (first 1000 chars):")
        System.err.println(preview)
        
        json
    }
    
    /**
     * Получить текущую погоду по ZIP коду
     */
    suspend fun getCurrentWeatherByZip(
        zip: String,
        countryCode: String? = null,
        units: String = "metric",
        lang: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/weather"
        
        val zipCode = if (countryCode != null) "$zip,$countryCode" else zip
        val queryParams = buildString {
            append("zip=${URLEncoder.encode(zipCode, "UTF-8")}")
            append("&appid=$apiKey")
            append("&units=$units")
            lang?.let { append("&lang=${URLEncoder.encode(it, "UTF-8")}") }
        }
        
        System.err.println("[DEBUG] API Request: $url?$queryParams")
        
        val response = client.get("$url?$queryParams") {
            headers {
                append("Accept", "application/json")
                append("User-Agent", "MCP-OpenWeatherMap-Server-Kotlin/1.0.0")
            }
        }
        
        val responseBody = response.body<String>()
        System.err.println("[DEBUG] API Response Status: ${response.status}")
        System.err.println("[DEBUG] API Response size: ${responseBody.length} bytes")
        
        val json = Json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw Exception("Invalid JSON response")
        
        // Логируем основную информацию
        json["name"]?.let { System.err.println("[DEBUG] City: $it") }
        val mainObj = json["main"] as? JsonObject
        mainObj?.get("temp")?.let { System.err.println("[DEBUG] Temperature: $it") }
        val weatherArray = json["weather"] as? JsonArray
        weatherArray?.firstOrNull()?.let { first ->
            val firstObj = first as? JsonObject
            firstObj?.get("description")?.let {
                System.err.println("[DEBUG] Weather description: $it")
            }
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

