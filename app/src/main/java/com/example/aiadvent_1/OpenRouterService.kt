package com.example.aiadvent_1

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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

// Yandex GPT API request/response models
data class YandexCompletionRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<YandexMessage>,
    val jsonObject: Boolean? = null, // Для форсирования JSON ответа
    val generationOptions: GenerationOptions? = null // Для структурированного вывода
)

data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.6,
    val maxTokens: String = "2000"
)

data class GenerationOptions(
    val structuredOutput: StructuredOutput? = null
)

data class StructuredOutput(
    val schema: JsonSchema
)

data class JsonSchema(
    val type: String = "object",
    val properties: Map<String, JsonProperty>? = null,
    val required: List<String>? = null
)

data class JsonProperty(
    val type: String,
    val description: String? = null
)

data class YandexMessage(
    val role: String,
    val text: String
)

data class YandexCompletionResponse(
    val result: YandexResult
)

data class YandexResult(
    val alternatives: List<YandexAlternative>
)

data class YandexAlternative(
    val message: YandexMessageResponse,
    val status: String? = null
)

data class YandexMessageResponse(
    val role: String,
    val text: String
)

interface YandexGPTApi {
    @POST("foundationModels/v1/completion")
    suspend fun createCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Body request: YandexCompletionRequest
    ): YandexCompletionResponse
}

class YandexGPTService {
    private val apiKey = BuildConfig.YANDEX_API_KEY
    private val baseUrl = "https://llm.api.cloud.yandex.net/"
    private val catalogId = "b1giajju0rlo3qq6dtev"
    
    private val modelUri = "gpt://$catalogId/yandexgpt-lite"
    
    val gson = Gson()

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
    
    private val api = retrofit.create(YandexGPTApi::class.java)
    
    suspend fun generateResponse(userMessage: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val request = YandexCompletionRequest(
                    modelUri = modelUri,
                    completionOptions = CompletionOptions(
                        stream = false,
                        temperature = 0.6,
                        maxTokens = "2000"
                    ),
                    messages = listOf(
                        YandexMessage(
                            role = "user",
                            text = userMessage
                        )
                    )
                )
                
                val response = api.createCompletion(
                    authorization = "Api-Key $apiKey",
                    contentType = "application/json",
                    request = request
                )
                
                response.result.alternatives.firstOrNull()?.message?.text
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

    
    private val defaultSystemPrompt = """
        Ты помощник. ВСЕГДА отвечай ТОЛЬКО валидным JSON в таком формате:
        {
            "status": "success",
            "message": "Твой ответ пользователю",
            "data": {
                "ключ1": "значение1",
                "ключ2": ["список", "значений"]
            }
        }
        
        ВАЖНО:
        - Поле "status" всегда "success" (если нет ошибки)
        - Поле "message" - твой основной ответ
        - Поле "data" - объект с дополнительной информацией (обязательно заполняй!)
        - НЕ используй markdown, НЕ оборачивай в ```json
        - Отвечай ТОЛЬКО JSON, без дополнительного текста
        
        Пример правильного ответа:
        {"status":"success","message":"Привет! Как дела?","data":{"greeting":"Приветствие","time":"утро"}}
    """.trimIndent()

    suspend fun generateJsonResponse(userMessage: String, systemPrompt: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val messages = listOf(
                    YandexMessage("system", systemPrompt ?: defaultSystemPrompt),
                    YandexMessage("user", userMessage)
                )
                
                val jsonSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "status" to JsonProperty("string", "Статус ответа"),
                        "message" to JsonProperty("string", "Основное сообщение"),
                        "data" to JsonProperty("object", "Дополнительные данные")
                    ),
                    required = listOf("status", "message", "data")
                )
                
                val request = YandexCompletionRequest(
                    modelUri = modelUri,
                    completionOptions = CompletionOptions(
                        stream = false, 
                        temperature = 0.1,
                        maxTokens = "2000"
                    ),
                    messages = messages,
                    jsonObject = true,
                    generationOptions = GenerationOptions(StructuredOutput(jsonSchema))
                )
                
                api.createCompletion("Api-Key $apiKey", "application/json", request)
                    .result.alternatives.firstOrNull()?.message?.text
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun cleanJson(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
            if (cleaned.endsWith("```")) cleaned = cleaned.removeSuffix("```").trim()
        }
        return cleaned.replace("\\n", "").replace("\\\"", "\"")
    }
    
    fun parseAgentResponse(rawResponse: String?): AgentResponse? {
        if (rawResponse == null) return null
        
        return try {
            val cleaned = cleanJson(rawResponse)
            
            // Проверяем, что это похоже на JSON
            if (!cleaned.trim().startsWith("{") || !cleaned.trim().endsWith("}")) {
                println("⚠️ Ответ не является JSON объектом: ${cleaned.take(100)}")
                return null
            }
            
            val jsonObject = gson.fromJson(cleaned, JsonObject::class.java)
            
            // Проверяем обязательные поля
            if (!jsonObject.has("status") || !jsonObject.has("message")) {
                println("⚠️ Отсутствуют обязательные поля в JSON")
                return null
            }
            
            val dataMap = jsonObject.get("data")?.asJsonObject?.let { obj ->
                obj.entrySet().associate { (key, value) -> key to parseValue(value) }
            } ?: emptyMap()
            
            AgentResponse(
                status = jsonObject.get("status")?.asString ?: "error",
                message = jsonObject.get("message")?.asString ?: "",
                data = dataMap,
                error = jsonObject.get("error")?.asString
            )
        } catch (e: Exception) {
            println("❌ Ошибка парсинга JSON: ${e.message}")
            println("Исходный ответ: ${rawResponse.take(200)}")
            null
        }
    }
    
    private fun parseValue(value: JsonElement): Any = when {
        value.isJsonPrimitive -> {
            val p = value.asJsonPrimitive
            when {
                p.isString -> p.asString
                p.isNumber -> if (p.asNumber.toDouble() % 1 == 0.0) p.asLong else p.asDouble
                p.isBoolean -> p.asBoolean
                else -> p.toString()
            }
        }
        value.isJsonArray -> value.asJsonArray.map { parseValue(it) }
        value.isJsonObject -> value.asJsonObject.entrySet().associate { (k, v) -> k to parseValue(v) }
        else -> value.toString()
    }

}

