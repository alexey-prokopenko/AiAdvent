package com.example.aiadvent_1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpertsGroupChatViewModel : ViewModel() {
    private val deepSeekService = DeepSeekService()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val expertsPrompt = """
        Ты - модератор группы из трёх экспертов, которые будут решать задачу. 
        
        **Инструкция:**
        Представь, что у тебя есть группа из трёх экспертов с разными подходами и специализациями:
        
        1. **Эксперт-аналитик** - специализируется на логическом анализе, разбиении задач на части, системном подходе
        2. **Эксперт-практик** - фокусируется на практических решениях, опыте, реальных примерах и применимости
        3. **Эксперт-новатор** - предлагает креативные, нестандартные решения, думает вне рамок
        
        **Задача:**
        Для каждой задачи, которую получит пользователь, ты должен представить ответы всех трёх экспертов.
        
        **Формат ответа:**
        Для каждого эксперта предоставь:
        - Имя и специализацию эксперта
        - Его подход к решению задачи
        - Детальное решение с обоснованием
        - Ключевые выводы
        
        Структурируй ответ так:
        
        ## 👨‍🔬 Эксперт-аналитик
        [Подход и решение]
        
        ## 👨‍💼 Эксперт-практик
        [Подход и решение]
        
        ## 🚀 Эксперт-новатор
        [Подход и решение]
        
        ## 📊 Сравнение подходов
        [Краткое сравнение и общие выводы]
        
        Начни анализ задачи:
    """.trimIndent()
    
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(message, true)
        _messages.value = _messages.value + userMessage

        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Добавляем промпт для группы экспертов к сообщению пользователя
                val fullMessage = "$expertsPrompt\n\n**Задача пользователя:**\n$message"
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

