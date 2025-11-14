package com.example.aiadvent_1.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MemoryViewModel(
    private val repository: ExternalMemoryRepository
) : ViewModel() {

    private val _records = MutableStateFlow<List<MemoryRecord>>(emptyList())
    val records: StateFlow<List<MemoryRecord>> = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        _isLoading.value = true
        observeMemory()
    }

    private fun observeMemory() {
        viewModelScope.launch {
            repository.observeAll().collect { entries ->
                _records.value = entries
                _isLoading.value = false
            }
        }
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.syncSnapshot()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.clearMemory()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeError() {
        _errorMessage.value = null
    }
}

