package com.example.aiadvent_1.mcp

import android.util.Log
import com.example.aiadvent_1.FunctionDefinition
import com.example.aiadvent_1.Tool
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Сервис для интеграции MCP инструментов с LLM
 * Поддерживает несколько MCP клиентов одновременно
 */
class McpIntegrationService(
    private val mcpClients: List<McpClient>
) {
    private val gson = Gson()
    
    // Кэш для маппинга инструментов к клиентам
    private val toolToClientMap = mutableMapOf<String, McpClient>()
    
    /**
     * Получает список MCP инструментов из всех клиентов и преобразует их в формат для LLM API
     */
    suspend fun getToolsForLlm(): Result<List<Tool>> = withContext(Dispatchers.IO) {
        try {
            val allTools = mutableListOf<Tool>()
            toolToClientMap.clear() // Очищаем кэш при обновлении инструментов
            
            // Получаем инструменты от всех клиентов
            for (client in mcpClients) {
                try {
                    val mcpToolsResult = client.getTools()
                    
                    mcpToolsResult.onSuccess { mcpTools ->
                        val tools = mcpTools.mapNotNull { mcpTool ->
                            try {
                                // Сохраняем маппинг инструмента к клиенту
                                toolToClientMap[mcpTool.name] = client
                                
                                // Преобразуем inputSchema из MCP в формат для LLM
                                val parameters = mcpTool.inputSchema?.let { schema ->
                                    // Преобразуем Map<String, Any> в JSON объект
                                    val schemaJson = gson.toJsonTree(schema).asJsonObject
                                    // Преобразуем обратно в Map для сериализации
                                    gson.fromJson(schemaJson, Map::class.java) as? Map<String, Any>
                                } ?: emptyMap<String, Any>()
                                
                                Tool(
                                    type = "function",
                                    function = FunctionDefinition(
                                        name = mcpTool.name,
                                        description = mcpTool.description ?: "MCP инструмент: ${mcpTool.name}",
                                        parameters = parameters
                                    )
                                )
                            } catch (e: Exception) {
                                Log.e("McpIntegrationService", "Ошибка преобразования инструмента ${mcpTool.name}", e)
                                null
                            }
                        }
                        
                        allTools.addAll(tools)
                        Log.d("McpIntegrationService", "Получено ${tools.size} инструментов от клиента")
                    }.onFailure { error ->
                        Log.w("McpIntegrationService", "Ошибка получения MCP инструментов от клиента: ${error.message}")
                        // Продолжаем работу с другими клиентами
                    }
                } catch (e: Exception) {
                    Log.w("McpIntegrationService", "Исключение при получении инструментов от клиента: ${e.message}")
                    // Продолжаем работу с другими клиентами
                }
            }
            
            Log.d("McpIntegrationService", "Всего получено ${allTools.size} инструментов от ${mcpClients.size} клиентов")
            return@withContext Result.success(allTools)
            
        } catch (e: Exception) {
            Log.e("McpIntegrationService", "Ошибка при получении инструментов", e)
            Result.failure(e)
        }
    }
    
    /**
     * Вызывает MCP инструмент по имени с аргументами
     * Автоматически определяет, какой клиент нужно использовать
     */
    suspend fun callTool(toolName: String, argumentsJson: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Находим клиента для данного инструмента
            val client = toolToClientMap[toolName]
            if (client == null) {
                Log.e("McpIntegrationService", "Инструмент $toolName не найден ни в одном клиенте")
                return@withContext Result.failure(Exception("Инструмент $toolName не найден. Доступные инструменты: ${toolToClientMap.keys.joinToString(", ")}"))
            }
            
            // Парсим JSON аргументы
            val arguments = try {
                val jsonObject = JsonParser().parse(argumentsJson).asJsonObject
                gson.fromJson(jsonObject, Map::class.java) as? Map<String, Any> ?: emptyMap()
            } catch (e: Exception) {
                Log.w("McpIntegrationService", "Ошибка парсинга аргументов, используем пустую карту", e)
                emptyMap<String, Any>()
            }
            
            Log.d("McpIntegrationService", "Вызов инструмента: $toolName с аргументами: $arguments")
            
            val result = client.callTool(toolName, arguments)
            
            result.onSuccess { response ->
                Log.d("McpIntegrationService", "Инструмент $toolName выполнен успешно")
            }.onFailure { error ->
                Log.e("McpIntegrationService", "Ошибка выполнения инструмента $toolName", error)
            }
            
            result
        } catch (e: Exception) {
            Log.e("McpIntegrationService", "Ошибка при вызове инструмента $toolName", e)
            Result.failure(e)
        }
    }
    
    /**
     * Инициализирует все MCP клиенты
     */
    suspend fun initializeAll(): Result<Unit> = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        
        for (client in mcpClients) {
            try {
                val initResult = client.initialize()
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    errors.add(error)
                    Log.w("McpIntegrationService", "Не удалось инициализировать клиент: $error")
                } else {
                    Log.d("McpIntegrationService", "Клиент успешно инициализирован")
                }
            } catch (e: Exception) {
                errors.add(e.message ?: "Исключение при инициализации")
                Log.w("McpIntegrationService", "Исключение при инициализации клиента: ${e.message}")
            }
        }
        
        if (errors.isEmpty()) {
            Result.success(Unit)
        } else {
            // Возвращаем предупреждение, но не ошибку, если хотя бы один клиент инициализирован
            Log.w("McpIntegrationService", "Некоторые клиенты не удалось инициализировать: ${errors.joinToString("; ")}")
            Result.success(Unit) // Все равно возвращаем success, чтобы продолжить работу
        }
    }
    
    /**
     * Закрывает все MCP клиенты
     */
    fun closeAll() {
        for (client in mcpClients) {
            try {
                client.close()
            } catch (e: Exception) {
                Log.w("McpIntegrationService", "Ошибка при закрытии клиента: ${e.message}")
            }
        }
        toolToClientMap.clear()
    }
}

