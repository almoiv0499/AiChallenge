package org.example.aichallenge.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.domain.usecase.LoadChatHistoryUseCase
import org.example.chatai.domain.usecase.SendMessageUseCase
import org.example.aichallenge.domain.SettingsRepository
import org.example.chatai.domain.project.TeamMcpService

private const val TAG = "ChatViewModel"

/**
 * ViewModel для экрана чата.
 * Управляет состоянием UI и координацией между UseCase и Repository.
 * Использует StateFlow для реактивного UI.
 */
class ChatViewModel(
    private val sendMessageUseCase: SendMessageUseCase,
    private val loadChatHistoryUseCase: LoadChatHistoryUseCase,
    private val settingsRepository: SettingsRepository? = null,
    private val teamMcpService: TeamMcpService? = null
) : ViewModel() {
    
    // Состояние загрузки истории
    private val _messages: MutableStateFlow<List<ChatMessage>> = MutableStateFlow(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    // Состояние загрузки
    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // Состояние ошибки
    private val _error: MutableStateFlow<String?> = MutableStateFlow(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        loadChatHistory()
        // Регистрируем Team MCP инструменты при инициализации, если они включены в настройках
        viewModelScope.launch {
            val settings = settingsRepository?.getSettings() ?: org.example.aichallenge.data.Settings()
            if (settings.teamMcpEnabled && !teamMcpService?.isTeamMcpRegistered()!!) {
                teamMcpService?.registerTeamMcpTools()
            }
        }
    }
    
    /**
     * Загружает историю чата из базы данных
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                loadChatHistoryUseCase.execute()
                    .collect { messageList ->
                        _messages.value = messageList
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при загрузке истории чата", e)
            }
        }
    }
    
    /**
     * Отправляет сообщение пользователя
     * Использует RAG и MCP настройки из SettingsRepository
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            // Получаем настройки для передачи в UseCase
            val settings = settingsRepository?.getSettings() ?: org.example.aichallenge.data.Settings()
            
            sendMessageUseCase.execute(message, ragEnabled = settings.ragEnabled)
                .onSuccess {
                    // Успех - сообщения автоматически обновятся через Flow
                    Log.d(TAG, "Сообщение успешно отправлено: ${message.take(50)}...")
                    _isLoading.value = false
                }
                .onFailure { exception ->
                    val errorMessage = exception.message ?: "Произошла ошибка при отправке сообщения"
                    Log.e(TAG, "Ошибка при отправке сообщения: $errorMessage", exception)
                    _error.value = errorMessage
                    _isLoading.value = false
                }
        }
    }
    
    /**
     * Очищает ошибку
     */
    fun clearError() {
        _error.value = null
    }
}
