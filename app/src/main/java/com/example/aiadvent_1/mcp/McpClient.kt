package com.example.aiadvent_1.mcp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

/**
 * Тип транспорта для MCP соединения
 */
sealed class McpTransport {
    data class Stdio(
        val command: String,
        val args: List<String> = emptyList()
    ) : McpTransport()
    
    data class Http(
        val url: String
    ) : McpTransport()
}

class McpClient(
    private val transport: McpTransport
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Увеличено для медленных сетей
        .readTimeout(60, TimeUnit.SECONDS) // Увеличено для инициализации MCP сервера
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private var requestId = 1
    private var process: Process? = null
    private var processWriter: BufferedWriter? = null
    private var processReader: BufferedReader? = null
    private var initialized = false
    
    /**
     * Отправляет JSON-RPC уведомление (notification) без ожидания ответа
     */
    private suspend fun sendNotification(request: JsonRpcRequest) = withContext(Dispatchers.IO) {
        try {
            require(request.id == null) { "Notifications must not have an id" }
            val jsonRequest = gson.toJson(request)
            Log.d("McpClient", "Отправка уведомления: $jsonRequest")
            
            when (transport) {
                is McpTransport.Http -> {
                    val url = transport.url
                    val mediaType = "application/json".toMediaType()
                    val requestBody = jsonRequest.toRequestBody(mediaType)
                    
                    val httpRequest = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("Content-Type", "application/json")
                        .build()
                    
                    // Для уведомлений не ждём ответа
                    httpClient.newCall(httpRequest).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            Log.w("McpClient", "Ошибка отправки уведомления (игнорируется)", e)
                        }
                        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                            response.close() // Закрываем ответ, так как не нужен
                        }
                    })
                }
                is McpTransport.Stdio -> {
                    val writer = processWriter ?: return@withContext
                    writer.write("$jsonRequest\n")
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.w("McpClient", "Ошибка отправки уведомления (игнорируется)", e)
        }
    }
    
    /**
     * Отправляет JSON-RPC запрос через выбранный транспорт
     */
    private suspend fun sendRequest(request: JsonRpcRequest): Result<JsonRpcResponse> = withContext(Dispatchers.IO) {
        require(request.id != null) { "Requests must have an id" }
        try {
            val jsonRequest = gson.toJson(request)
            Log.d("McpClient", "Отправка запроса: $jsonRequest")
            
            val responseBody = when (transport) {
                is McpTransport.Http -> sendHttpRequest(jsonRequest)
                is McpTransport.Stdio -> sendStdioRequest(jsonRequest)
            }
            
            responseBody.getOrNull()?.let { body ->
                Log.d("McpClient", "Получен ответ: $body")
                val jsonResponse = JsonParser().parse(body).asJsonObject
                val jsonRpcResponse = gson.fromJson(jsonResponse, JsonRpcResponse::class.java)
                
                if (jsonRpcResponse.error != null) {
                    Result.failure(Exception("MCP ошибка: ${jsonRpcResponse.error.message}"))
                } else {
                    Result.success(jsonRpcResponse)
                }
            } ?: responseBody.map { JsonRpcResponse("2.0", null, null, null) }
            
        } catch (e: Exception) {
            Log.e("McpClient", "Ошибка отправки запроса", e)
            Result.failure(e)
        }
    }
    
    private suspend fun sendHttpRequest(jsonRequest: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = (transport as McpTransport.Http).url
            val mediaType = "application/json".toMediaType()
            val requestBody = jsonRequest.toRequestBody(mediaType)
            
            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            val response = httpClient.newCall(httpRequest).execute()
            val body = response.body?.string()
            
            if (!response.isSuccessful || body == null) {
                Result.failure(Exception("Ошибка HTTP: ${response.code} - $body"))
            } else {
                Result.success(body)
            }
        } catch (e: java.net.ConnectException) {
            val url = (transport as McpTransport.Http).url
            val errorMessage = when {
                e.message?.contains("Connection refused") == true -> {
                    "Не удалось подключиться к серверу $url\n\n" +
                    "Возможные причины:\n" +
                    "• MCP сервер не запущен на компьютере\n" +
                    "• Сервер не слушает на 0.0.0.0 (только на localhost)\n" +
                    "• Брандмауэр блокирует подключения\n" +
                    "• Неправильный IP адрес или порт\n\n" +
                    "💡 Для эмулятора Android используйте: http://10.0.2.2:3000\n" +
                    "💡 Для реального устройства используйте IP компьютера: http://192.168.x.x:3000"
                }
                e.message?.contains("timeout") == true || e.message?.contains("timed out") == true -> {
                    "Таймаут подключения к серверу $url\n\n" +
                    "Проверьте:\n" +
                    "• Сервер запущен и доступен\n" +
                    "• Устройства в одной сети\n" +
                    "• Брандмауэр разрешает подключения"
                }
                else -> {
                    "Ошибка подключения к $url: ${e.message}"
                }
            }
            Log.e("McpClient", "Ошибка подключения: $errorMessage", e)
            Result.failure(Exception(errorMessage, e))
        } catch (e: java.net.UnknownHostException) {
            val url = (transport as McpTransport.Http).url
            val errorMessage = "Не удалось найти сервер: $url\n\n" +
                    "Проверьте правильность IP адреса или доменного имени"
            Log.e("McpClient", "Ошибка DNS: $errorMessage", e)
            Result.failure(Exception(errorMessage, e))
        } catch (e: Exception) {
            val url = if (transport is McpTransport.Http) (transport as McpTransport.Http).url else "unknown"
            val errorMessage = "Ошибка HTTP запроса к $url: ${e.message}"
            Log.e("McpClient", errorMessage, e)
            Result.failure(Exception(errorMessage, e))
        }
    }
    
    private suspend fun sendStdioRequest(jsonRequest: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val writer = processWriter ?: return@withContext Result.failure(
                Exception("Процесс не запущен")
            )
            
            // Отправляем запрос с переносом строки
            writer.write("$jsonRequest\n")
            writer.flush()
            
            // Читаем ответ (ждём строку с JSON)
            val reader = processReader ?: return@withContext Result.failure(
                Exception("Процесс не запущен")
            )
            
            val response = reader.readLine()
            if (response.isNullOrBlank()) {
                Result.failure(Exception("Пустой ответ от сервера"))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Создаёт соединение с MCP сервером и получает список доступных инструментов
     */
    suspend fun getTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        try {
            if (!initialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return@withContext initResult.map { emptyList() }
                }
            }
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/list",
                params = null
            )
            
            val responseResult = sendRequest(request)
            val response = responseResult.getOrNull() ?: return@withContext Result.failure(
                responseResult.exceptionOrNull() ?: Exception("Неизвестная ошибка")
            )
            
            // Извлекаем список инструментов из результата
            val result = response.result as? Map<*, *>
            val toolsJson = result?.get("tools") as? List<*>
            
            val tools = toolsJson?.mapNotNull { toolMap ->
                try {
                    val toolJson = gson.toJsonTree(toolMap).asJsonObject
                    McpTool(
                        name = toolJson.get("name").asString,
                        description = toolJson.get("description")?.asString,
                        inputSchema = toolJson.get("inputSchema")?.asJsonObject?.let {
                            gson.fromJson(it, Map::class.java) as? Map<String, Any>
                        }
                    )
                } catch (e: Exception) {
                    Log.e("McpClient", "Ошибка парсинга инструмента", e)
                    null
                }
            } ?: emptyList()
            
            Result.success(tools)
            
        } catch (e: Exception) {
            Log.e("McpClient", "Ошибка при получении инструментов", e)
            Result.failure(e)
        }
    }
    
    /**
     * Инициализирует соединение с MCP сервером
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Для stdio транспорта запускаем процесс
            if (transport is McpTransport.Stdio && process == null) {
                val processBuilder = ProcessBuilder(
                    listOf(transport.command) + transport.args
                )
                process = processBuilder.start()
                processWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream))
                processReader = BufferedReader(InputStreamReader(process!!.inputStream))
                Log.d("McpClient", "Процесс MCP сервера запущен: ${transport.command}")
            }
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "initialize",
                params = mapOf(
                    "protocolVersion" to "2024-11-05",
                    "capabilities" to emptyMap<String, Any>(),
                    "clientInfo" to mapOf(
                        "name" to "AiAdvent",
                        "version" to "1.0.0"
                    )
                )
            )
            
            val responseResult = sendRequest(request)
            
            return@withContext if (responseResult.isSuccess) {
                // После initialize нужно отправить initialized (notification без id)
                val initializedRequest = JsonRpcRequest(
                    id = null, // null для notifications
                    method = "initialized",
                    params = null
                )
                sendNotification(initializedRequest) // Отправляем как уведомление
                
                initialized = true
                Log.d("McpClient", "Инициализация успешна")
                Result.success(Unit)
            } else {
                val error = responseResult.exceptionOrNull() ?: Exception("Ошибка инициализации")
                Log.e("McpClient", "Ошибка инициализации", error)
                Result.failure(error)
            }
            
        } catch (e: java.io.IOException) {
            val errorMessage = when {
                e.message?.contains("Cannot run program") == true -> {
                    val command = if (transport is McpTransport.Stdio) transport.command else "unknown"
                    "Команда '$command' не найдена. На Android stdio транспорт может не работать. " +
                    "Используйте HTTP транспорт для подключения к MCP серверу."
                }
                else -> e.message ?: "Ошибка запуска процесса"
            }
            Log.e("McpClient", "Ошибка инициализации: $errorMessage", e)
            Result.failure(Exception(errorMessage, e))
        } catch (e: Exception) {
            Log.e("McpClient", "Ошибка инициализации", e)
            Result.failure(e)
        }
    }
    
    /**
     * Вызывает MCP инструмент с заданными параметрами
     */
    suspend fun callTool(toolName: String, arguments: Map<String, Any>): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!initialized) {
                val initResult = initialize()
                if (initResult.isFailure) {
                    return@withContext Result.failure(
                        initResult.exceptionOrNull() ?: Exception("Не удалось инициализировать MCP клиент")
                    )
                }
            }
            
            val request = JsonRpcRequest(
                id = requestId++,
                method = "tools/call",
                params = mapOf(
                    "name" to toolName,
                    "arguments" to arguments
                )
            )
            
            val responseResult = sendRequest(request)
            val response = responseResult.getOrNull() ?: return@withContext Result.failure(
                responseResult.exceptionOrNull() ?: Exception("Неизвестная ошибка")
            )
            
            // Извлекаем результат из ответа
            val result = response.result as? Map<*, *>
            val content = result?.get("content") as? List<*>
            val firstContent = content?.firstOrNull() as? Map<*, *>
            val text = firstContent?.get("text") as? String
            
            if (text != null) {
                Result.success(text)
            } else {
                // Если структура отличается, возвращаем весь результат как JSON
                val jsonResult = gson.toJson(result)
                Result.success(jsonResult)
            }
            
        } catch (e: Exception) {
            Log.e("McpClient", "Ошибка при вызове инструмента $toolName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Закрывает соединение и освобождает ресурсы
     */
    fun close() {
        processWriter?.close()
        processReader?.close()
        process?.destroy()
        process = null
        processWriter = null
        processReader = null
        initialized = false
    }
}
