package com.example.aiadvent_1.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class McpToolsViewModel : ViewModel() {
    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private var mcpClient: McpClient? = null
    
    /**
     * Подключается к MCP серверу и загружает список инструментов
     */
    fun loadTools(transport: McpTransport) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                mcpClient = McpClient(transport)
                val result = mcpClient!!.getTools()
                
                result.onSuccess { tools ->
                    _tools.value = tools
                }.onFailure { exception ->
                    val errorMsg = exception.message ?: "Неизвестная ошибка"
                    // Сообщения об ошибках уже улучшены в McpClient
                    _error.value = errorMsg
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Ошибка подключения к MCP серверу"
                // Улучшаем сообщение об ошибке для stdio транспорта
                val improvedMsg = if (errorMsg.contains("не найдена") || errorMsg.contains("Cannot run program")) {
                    "$errorMsg\n\n💡 Совет: Используйте HTTP транспорт для подключения к MCP серверу."
                } else if (errorMsg.contains("Connection refused") || errorMsg.contains("Не удалось подключиться")) {
                    // Сообщение уже содержит подсказки
                    errorMsg
                } else {
                    errorMsg
                }
                _error.value = improvedMsg
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Обновляет список инструментов
     */
    fun refresh() {
        mcpClient?.let { client ->
            viewModelScope.launch {
                _isLoading.value = true
                _error.value = null
                
                try {
                    val result = client.getTools()
                    result.onSuccess { tools ->
                        _tools.value = tools
                    }.onFailure { exception ->
                        _error.value = exception.message ?: "Неизвестная ошибка"
                    }
                } catch (e: Exception) {
                    _error.value = e.message ?: "Ошибка обновления инструментов"
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
    
    /**
     * Очищает список инструментов и закрывает соединение
     */
    fun clear() {
        mcpClient?.close()
        mcpClient = null
        _tools.value = emptyList()
        _error.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        mcpClient?.close()
    }
}

