package com.example.aiadvent.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * MCP сервер для OpenWeatherMap API
 */
class WeatherMcpServer(
    private val weatherClient: OpenWeatherMapClient
) {
    private val protocolVersion = "2024-11-05"
    
    /**
     * Обработка запроса initialize
     */
    fun handleInitialize(params: Map<String, JsonElement>?): JsonObject {
        System.err.println("[DEBUG] handleInitialize called")
        return buildJsonObject {
            put("protocolVersion", protocolVersion)
            putJsonObject("capabilities") {
                putJsonObject("tools") {}
            }
            putJsonObject("serverInfo") {
                put("name", "openweathermap-mcp-server-kotlin")
                put("version", "1.0.0")
            }
        }
    }
    
    /**
     * Обработка запроса tools/list
     */
    fun handleToolsList(): JsonObject {
        System.err.println("[DEBUG] handleToolsList called")
        return buildJsonObject {
            putJsonArray("tools") {
                // get_current_weather_by_city
                add(buildJsonObject {
                    put("name", "get_current_weather_by_city")
                    put("description", "Получить текущую погоду по названию города. Возвращает актуальные данные о температуре, влажности, давлении, скорости ветра и другие метеорологические параметры.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("city") {
                                put("type", "string")
                                put("description", "Название города (например, \"Moscow\", \"London\", \"New York\"). Можно указать город и страну через запятую (например, \"Moscow,ru\").")
                            }
                            putJsonObject("units") {
                                put("type", "string")
                                put("description", "Единицы измерения температуры: \"metric\" (градусы Цельсия), \"imperial\" (градусы Фаренгейта), \"kelvin\" (Кельвин)")
                                putJsonArray("enum") {
                                    add("metric")
                                    add("imperial")
                                    add("kelvin")
                                }
                                put("default", "metric")
                            }
                            putJsonObject("lang") {
                                put("type", "string")
                                put("description", "Язык для описания погоды (например, \"ru\" для русского, \"en\" для английского). Список доступных языков: https://openweathermap.org/api/one-call-3#multi")
                            }
                        }
                        putJsonArray("required") {
                            add("city")
                        }
                    }
                })
                
                // get_current_weather_by_coordinates
                add(buildJsonObject {
                    put("name", "get_current_weather_by_coordinates")
                    put("description", "Получить текущую погоду по географическим координатам (широта и долгота). Возвращает актуальные данные о температуре, влажности, давлении, скорости ветра и другие метеорологические параметры.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("lat") {
                                put("type", "number")
                                put("description", "Широта (latitude) в диапазоне от -90 до 90")
                            }
                            putJsonObject("lon") {
                                put("type", "number")
                                put("description", "Долгота (longitude) в диапазоне от -180 до 180")
                            }
                            putJsonObject("units") {
                                put("type", "string")
                                put("description", "Единицы измерения температуры: \"metric\" (градусы Цельсия), \"imperial\" (градусы Фаренгейта), \"kelvin\" (Кельвин)")
                                putJsonArray("enum") {
                                    add("metric")
                                    add("imperial")
                                    add("kelvin")
                                }
                                put("default", "metric")
                            }
                            putJsonObject("lang") {
                                put("type", "string")
                                put("description", "Язык для описания погоды (например, \"ru\" для русского, \"en\" для английского)")
                            }
                        }
                        putJsonArray("required") {
                            add("lat")
                            add("lon")
                        }
                    }
                })
                
                // get_current_weather_by_zip
                add(buildJsonObject {
                    put("name", "get_current_weather_by_zip")
                    put("description", "Получить текущую погоду по почтовому индексу (ZIP коду). Возвращает актуальные данные о температуре, влажности, давлении, скорости ветра и другие метеорологические параметры.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("zip") {
                                put("type", "string")
                                put("description", "Почтовый индекс (ZIP код). Для США можно указать только код, для других стран - код и код страны через запятую (например, \"94040,us\").")
                            }
                            putJsonObject("countryCode") {
                                put("type", "string")
                                put("description", "Код страны по ISO 3166 (например, \"us\", \"ru\", \"gb\"). Если не указан, используется для США.")
                            }
                            putJsonObject("units") {
                                put("type", "string")
                                put("description", "Единицы измерения температуры: \"metric\" (градусы Цельсия), \"imperial\" (градусы Фаренгейта), \"kelvin\" (Кельвин)")
                                putJsonArray("enum") {
                                    add("metric")
                                    add("imperial")
                                    add("kelvin")
                                }
                                put("default", "metric")
                            }
                            putJsonObject("lang") {
                                put("type", "string")
                                put("description", "Язык для описания погоды (например, \"ru\" для русского, \"en\" для английского)")
                            }
                        }
                        putJsonArray("required") {
                            add("zip")
                        }
                    }
                })
            }
        }
    }
    
    /**
     * Обработка вызова инструмента
     */
    suspend fun handleToolCall(toolName: String, arguments: Map<String, JsonElement>): JsonObject {
        System.err.println("[DEBUG] handleToolCall: tool=\"$toolName\", arguments=${arguments}")
        
        return when (toolName) {
            "get_current_weather_by_city" -> {
                val city = arguments["city"]?.jsonPrimitive?.content
                    ?: throw Exception("Параметр city (название города) обязателен")
                
                val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"
                val lang = arguments["lang"]?.jsonPrimitive?.content
                
                System.err.println("[DEBUG] Calling OpenWeatherMap API /weather with params: city=$city, units=$units, lang=$lang")
                
                val result = weatherClient.getCurrentWeatherByCity(
                    city = city,
                    units = units,
                    lang = lang
                )
                
                System.err.println("[DEBUG] handleToolCall completed for tool=\"$toolName\"")
                
                buildJsonObject {
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", result.toString())
                        })
                    }
                }
            }
            
            "get_current_weather_by_coordinates" -> {
                val lat = arguments["lat"]?.jsonPrimitive?.doubleOrNull
                    ?: throw Exception("Параметр lat (широта) обязателен и должен быть числом")
                
                val lon = arguments["lon"]?.jsonPrimitive?.doubleOrNull
                    ?: throw Exception("Параметр lon (долгота) обязателен и должен быть числом")
                
                val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"
                val lang = arguments["lang"]?.jsonPrimitive?.content
                
                System.err.println("[DEBUG] Calling OpenWeatherMap API /weather with params: lat=$lat, lon=$lon, units=$units, lang=$lang")
                
                val result = weatherClient.getCurrentWeatherByCoordinates(
                    lat = lat,
                    lon = lon,
                    units = units,
                    lang = lang
                )
                
                System.err.println("[DEBUG] handleToolCall completed for tool=\"$toolName\"")
                
                buildJsonObject {
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", result.toString())
                        })
                    }
                }
            }
            
            "get_current_weather_by_zip" -> {
                val zip = arguments["zip"]?.jsonPrimitive?.content
                    ?: throw Exception("Параметр zip (почтовый индекс) обязателен")
                
                val countryCode = arguments["countryCode"]?.jsonPrimitive?.content
                val units = arguments["units"]?.jsonPrimitive?.content ?: "metric"
                val lang = arguments["lang"]?.jsonPrimitive?.content
                
                System.err.println("[DEBUG] Calling OpenWeatherMap API /weather with params: zip=$zip, countryCode=$countryCode, units=$units, lang=$lang")
                
                val result = weatherClient.getCurrentWeatherByZip(
                    zip = zip,
                    countryCode = countryCode,
                    units = units,
                    lang = lang
                )
                
                System.err.println("[DEBUG] handleToolCall completed for tool=\"$toolName\"")
                
                buildJsonObject {
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", result.toString())
                        })
                    }
                }
            }
            
            else -> throw Exception("Unknown tool: $toolName")
        }
    }
    
    /**
     * Обработка JSON-RPC запроса
     */
    suspend fun handleRequest(request: JsonRpcRequest): JsonRpcResponse? {
        return try {
            // Уведомления не требуют ответа
            if (request.method == "initialized") {
                return null
            }
            
            val result = when (request.method) {
                "initialize" -> {
                    val paramsMap = request.params?.jsonObject?.entries?.associate { 
                        it.key to it.value 
                    }
                    handleInitialize(paramsMap)
                }
                
                "tools/list" -> {
                    handleToolsList()
                }
                
                "tools/call" -> {
                    val params = request.params?.jsonObject ?: throw Exception("Missing params")
                    val name = params["name"]?.jsonPrimitive?.content
                        ?: throw Exception("Missing tool name")
                    val arguments = params["arguments"]?.jsonObject
                        ?: throw Exception("Missing tool arguments")
                    
                    handleToolCall(name, arguments)
                }
                
                else -> throw Exception("Unknown method: ${request.method}")
            }
            
            JsonRpcResponse("2.0", request.id, result, null)
        } catch (e: Exception) {
            System.err.println("[DEBUG] Error handling request: ${e.message}")
            System.err.println("[DEBUG] Error stack: ${e.stackTraceToString()}")
            JsonRpcResponse(
                "2.0",
                request.id,
                null,
                JsonRpcError(-32603, e.message ?: "Internal error")
            )
        }
    }
}

/**
 * Главная функция - точка входа MCP сервера для погоды
 */
fun main() = runBlocking {
    // API ключ OpenWeatherMap
    val apiKey = "8af1b6dbfe4f032cd486d13658e1d48d"
    
    val weatherClient = OpenWeatherMapClient(apiKey)
    val weatherMcpServer = WeatherMcpServer(weatherClient)
    
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    val reader = BufferedReader(InputStreamReader(System.`in`))
    val writer = PrintWriter(System.out, true)
    val errorWriter = PrintWriter(System.err, true)
    
    var buffer = ""
    
    try {
        while (true) {
            val line = reader.readLine() ?: break
            
            if (line.isBlank()) continue
            
            buffer += line
            
            // Пытаемся распарсить JSON из буфера
            try {
                val trimmedBuffer = buffer.trim()
                if (trimmedBuffer.isNotEmpty()) {
                    val request = json.decodeFromString<JsonRpcRequest>(trimmedBuffer)
                    buffer = "" // Очищаем буфер после успешного парсинга
                    
                    // Обрабатываем запрос
                    val response = weatherMcpServer.handleRequest(request)
                    
                    // Отправляем ответ только если это не уведомление
                    if (response != null) {
                        val responseJson = json.encodeToString(JsonRpcResponse.serializer(), response)
                        writer.println(responseJson)
                        writer.flush()
                    }
                }
                
            } catch (e: kotlinx.serialization.SerializationException) {
                // Если не удалось распарсить, возможно JSON неполный - продолжаем читать
                // Но только если буфер не слишком большой
                if (buffer.length > 100000) {
                    // Защита от слишком большого буфера
                    errorWriter.println("[DEBUG] Buffer too large (${buffer.length} chars), clearing")
                    buffer = ""
                }
                // Иначе продолжаем накапливать данные
            } catch (e: Exception) {
                errorWriter.println("[DEBUG] Unexpected error: ${e.message}")
                errorWriter.println("[DEBUG] Stack: ${e.stackTraceToString()}")
                buffer = "" // Очищаем буфер при ошибке
            }
        }
    } finally {
        weatherClient.close()
    }
}

