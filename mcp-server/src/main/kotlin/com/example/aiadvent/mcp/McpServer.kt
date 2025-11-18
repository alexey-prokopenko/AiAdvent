package com.example.aiadvent.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * MCP сервер для NewsAPI
 */
class McpServer(private val newsApiClient: NewsApiClient) {
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
                put("name", "newsapi-mcp-server-kotlin")
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
                // search_news
                add(buildJsonObject {
                    put("name", "search_news")
                    put("description", "Поиск новостных статей по ключевым словам или теме. Ищет среди всех статей, опубликованных за последние 5 лет более чем 150,000 новостными источниками.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("q") {
                                put("type", "string")
                                put("description", "Ключевые слова или фраза для поиска (например, \"Apple\", \"технологии\", \"политика\")")
                            }
                            putJsonObject("from") {
                                put("type", "string")
                                put("description", "Дата начала поиска в формате YYYY-MM-DD (например, \"2025-11-18\")")
                            }
                            putJsonObject("to") {
                                put("type", "string")
                                put("description", "Дата окончания поиска в формате YYYY-MM-DD")
                            }
                            putJsonObject("sortBy") {
                                put("type", "string")
                                put("description", "Сортировка результатов: \"relevancy\", \"popularity\", \"publishedAt\"")
                                putJsonArray("enum") {
                                    add("relevancy")
                                    add("popularity")
                                    add("publishedAt")
                                }
                                put("default", "popularity")
                            }
                            putJsonObject("language") {
                                put("type", "string")
                                put("description", "Язык статей (например, \"ru\" для русского, \"en\" для английского)")
                            }
                            putJsonObject("page") {
                                put("type", "number")
                                put("description", "Номер страницы (начиная с 1)")
                                put("default", 1)
                            }
                            putJsonObject("pageSize") {
                                put("type", "number")
                                put("description", "Количество результатов на странице (максимум 100)")
                                put("default", 20)
                            }
                        }
                        putJsonArray("required") {
                            add("q")
                        }
                    }
                })
                
                // get_top_headlines
                add(buildJsonObject {
                    put("name", "get_top_headlines")
                    put("description", "Получить топ новостных заголовков для страны, категории или источника. Возвращает актуальные новости в реальном времени.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("country") {
                                put("type", "string")
                                put("description", "Код страны (например, \"ru\" для России, \"us\" для США, \"gb\" для Великобритании)")
                            }
                            putJsonObject("category") {
                                put("type", "string")
                                put("description", "Категория новостей")
                                putJsonArray("enum") {
                                    add("business")
                                    add("entertainment")
                                    add("general")
                                    add("health")
                                    add("science")
                                    add("sports")
                                    add("technology")
                                }
                            }
                            putJsonObject("sources") {
                                put("type", "string")
                                put("description", "Идентификатор источника новостей (например, \"bbc-news\", \"techcrunch\"). Можно указать несколько через запятую.")
                            }
                            putJsonObject("q") {
                                put("type", "string")
                                put("description", "Ключевые слова для фильтрации заголовков")
                            }
                            putJsonObject("page") {
                                put("type", "number")
                                put("description", "Номер страницы (начиная с 1)")
                                put("default", 1)
                            }
                            putJsonObject("pageSize") {
                                put("type", "number")
                                put("description", "Количество результатов на странице (максимум 100)")
                                put("default", 20)
                            }
                        }
                    }
                })
                
                // get_sources
                add(buildJsonObject {
                    put("name", "get_sources")
                    put("description", "Получить список доступных источников новостей. Можно фильтровать по категории, языку и стране.")
                    putJsonObject("inputSchema") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("category") {
                                put("type", "string")
                                put("description", "Категория источников")
                                putJsonArray("enum") {
                                    add("business")
                                    add("entertainment")
                                    add("general")
                                    add("health")
                                    add("science")
                                    add("sports")
                                    add("technology")
                                }
                            }
                            putJsonObject("language") {
                                put("type", "string")
                                put("description", "Язык источников (например, \"ru\", \"en\")")
                            }
                            putJsonObject("country") {
                                put("type", "string")
                                put("description", "Код страны (например, \"ru\", \"us\", \"gb\")")
                            }
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
            "search_news" -> {
                val q = arguments["q"]?.jsonPrimitive?.content
                    ?: throw Exception("Параметр q (ключевые слова) обязателен")
                
                val from = arguments["from"]?.jsonPrimitive?.content
                val to = arguments["to"]?.jsonPrimitive?.content
                val sortBy = arguments["sortBy"]?.jsonPrimitive?.content ?: "popularity"
                val language = arguments["language"]?.jsonPrimitive?.content
                val page = arguments["page"]?.jsonPrimitive?.intOrNull ?: 1
                val pageSize = arguments["pageSize"]?.jsonPrimitive?.intOrNull ?: 20
                
                System.err.println("[DEBUG] Calling NewsAPI /everything with params: q=$q, from=$from, to=$to, sortBy=$sortBy, language=$language, page=$page, pageSize=$pageSize")
                
                val result = newsApiClient.searchNews(
                    q = q,
                    from = from,
                    to = to,
                    sortBy = sortBy,
                    language = language,
                    page = page,
                    pageSize = pageSize
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
            
            "get_top_headlines" -> {
                val country = arguments["country"]?.jsonPrimitive?.content
                val category = arguments["category"]?.jsonPrimitive?.content
                val sources = arguments["sources"]?.jsonPrimitive?.content
                val q = arguments["q"]?.jsonPrimitive?.content
                val page = arguments["page"]?.jsonPrimitive?.intOrNull ?: 1
                val pageSize = arguments["pageSize"]?.jsonPrimitive?.intOrNull ?: 20
                
                if (country == null && category == null && sources == null) {
                    throw Exception("Необходимо указать хотя бы один из параметров: country, category или sources")
                }
                
                System.err.println("[DEBUG] Calling NewsAPI /top-headlines with params: country=$country, category=$category, sources=$sources, q=$q, page=$page, pageSize=$pageSize")
                
                val result = newsApiClient.getTopHeadlines(
                    country = country,
                    category = category,
                    sources = sources,
                    q = q,
                    page = page,
                    pageSize = pageSize
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
            
            "get_sources" -> {
                val category = arguments["category"]?.jsonPrimitive?.content
                val language = arguments["language"]?.jsonPrimitive?.content
                val country = arguments["country"]?.jsonPrimitive?.content
                
                System.err.println("[DEBUG] Calling NewsAPI /sources with params: category=$category, language=$language, country=$country")
                
                val result = newsApiClient.getSources(
                    category = category,
                    language = language,
                    country = country
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
 * Главная функция - точка входа MCP сервера
 */
fun main() = runBlocking {
    // API ключ NewsAPI
    val apiKey = "07fab6c9eca5436ba1b7f939c5528e1e"
    
    val newsApiClient = NewsApiClient(apiKey)
    val mcpServer = McpServer(newsApiClient)
    
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
                    val response = mcpServer.handleRequest(request)
                    
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
        newsApiClient.close()
    }
}
