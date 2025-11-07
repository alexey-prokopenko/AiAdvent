package com.example.aiadvent_1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogicalTaskChatViewModel : ViewModel() {
    private val deepSeekService = DeepSeekService()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val logicalTaskPrompt = """
        Реши логическую задачу пошагово:

        **Условия:**
        - Две двери: одна ведёт к свободе, другая — к гибели
        - Два стража: один всегда говорит правду, другой всегда лжёт
        - Неизвестно, какой страж у какой двери и кто из них лжец
        - Можно задать только один вопрос только одному стражу

        **Задание:**
        Сформулируй вопрос, который гарантированно позволит выбрать дверь к свободе.

        **Требования к решению:**
        1. Объясни логику построения вопроса
        2. Докажи, почему этот вопрос работает в обоих случаях (если спросить правдивого стража и если спросить лжеца)
        3. Покажи, какой ответ получишь в каждом случае
        4. Сформулируй финальное правило: как интерпретировать ответ для выбора правильной двери
    """.trimIndent()
    
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(message, true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Добавляем промпт для логических задач к сообщению пользователя
                val fullMessage = "$logicalTaskPrompt\n\n$message"
                val response = deepSeekService.generateResponse(fullMessage, stepByStep = true)
                val aiMessage = ChatMessage(response, false)
                _messages.value = _messages.value + aiMessage
            } catch (e: Exception) {
                val errorMessage = ChatMessage("Произошла ошибка: ${e.message}", false)
                _messages.value = _messages.value + errorMessage
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearChat() {
        _messages.value = emptyList()
    }
}

