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
 */
class McpIntegrationService(
    private val mcpClient: McpClient
) {
    private val gson = Gson()
    
    /**
     * Получает список MCP инструментов и преобразует их в формат для LLM API
     */
    suspend fun getToolsForLlm(): Result<List<Tool>> = withContext(Dispatchers.IO) {
        try {
            val mcpToolsResult = mcpClient.getTools()
            
            mcpToolsResult.onSuccess { mcpTools ->
                val tools = mcpTools.mapNotNull { mcpTool ->
                    try {
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
                
                Log.d("McpIntegrationService", "Преобразовано ${tools.size} инструментов для LLM")
                return@withContext Result.success(tools)
            }.onFailure { error ->
                Log.e("McpIntegrationService", "Ошибка получения MCP инструментов", error)
                return@withContext Result.failure(error)
            }
            
            Result.failure(Exception("Неизвестная ошибка"))
        } catch (e: Exception) {
            Log.e("McpIntegrationService", "Ошибка при получении инструментов", e)
            Result.failure(e)
        }
    }
    
    /**
     * Вызывает MCP инструмент по имени с аргументами
     */
    suspend fun callTool(toolName: String, argumentsJson: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Парсим JSON аргументы
            val arguments = try {
                val jsonObject = JsonParser().parse(argumentsJson).asJsonObject
                gson.fromJson(jsonObject, Map::class.java) as? Map<String, Any> ?: emptyMap()
            } catch (e: Exception) {
                Log.w("McpIntegrationService", "Ошибка парсинга аргументов, используем пустую карту", e)
                emptyMap<String, Any>()
            }
            
            Log.d("McpIntegrationService", "Вызов инструмента: $toolName с аргументами: $arguments")
            
            val result = mcpClient.callTool(toolName, arguments)
            
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
}

