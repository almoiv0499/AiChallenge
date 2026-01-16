package org.example.aichallenge.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.example.aichallenge.data.Settings
import org.example.aichallenge.domain.SettingsRepository
import org.example.chatai.domain.usecase.IndexDocumentationUseCase
import org.example.chatai.domain.embedding.RagService
import org.example.chatai.domain.project.TeamMcpService

private const val TAG = "SettingsViewModel"

/**
 * ViewModel для экрана настроек.
 * Управляет состояниями MCP серверов и RAG.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val indexDocumentationUseCase: IndexDocumentationUseCase? = null,
    private val ragService: RagService? = null,
    private val teamMcpService: TeamMcpService? = null
) : ViewModel() {
    
    private val _settings = MutableStateFlow<Settings>(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()
    
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()
    
    private val _indexingProgress = MutableStateFlow("")
    val indexingProgress: StateFlow<String> = _indexingProgress.asStateFlow()
    
    init {
        loadSettings()
        loadRagDocumentCount()
    }
    
    /**
     * Загружает настройки из репозитория
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val loadedSettings = settingsRepository.getSettings()
                // Обновляем количество индексированных документов, если нужно
                // (можно получить из RAG сервиса, но для простоты оставляем в настройках)
                _settings.value = loadedSettings
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при загрузке настроек", e)
            }
        }
    }
    
    /**
     * Загружает количество проиндексированных документов из RAG сервиса
     */
    private fun loadRagDocumentCount() {
        viewModelScope.launch {
            try {
                val count = ragService?.getDocumentCount() ?: 0
                val updated = _settings.value.copy(ragIndexedCount = count)
                _settings.value = updated
                settingsRepository.saveSettings(updated)
                Log.d(TAG, "Загружено количество индексированных документов: $count")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при загрузке количества индексированных документов", e)
            }
        }
    }
    
    /**
     * Обновляет количество проиндексированных документов
     */
    fun updateRagIndexedCount(count: Int) {
        viewModelScope.launch {
            try {
                val updated = _settings.value.copy(ragIndexedCount = count)
                _settings.value = updated
                settingsRepository.saveSettings(updated)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обновлении количества индексированных документов", e)
            }
        }
    }
    
    /**
     * Переключает состояние Notion MCP
     */
    fun toggleNotionMcp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = _settings.value.copy(notionMcpEnabled = enabled)
                _settings.value = updated
                settingsRepository.saveSettings(updated)
                Log.d(TAG, "Notion MCP: ${if (enabled) "включен" else "выключен"}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при сохранении настроек Notion MCP", e)
            }
        }
    }
    
    /**
     * Переключает состояние Weather MCP
     */
    fun toggleWeatherMcp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = _settings.value.copy(weatherMcpEnabled = enabled)
                _settings.value = updated
                settingsRepository.saveSettings(updated)
                Log.d(TAG, "Weather MCP: ${if (enabled) "включен" else "выключен"}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при сохранении настроек Weather MCP", e)
            }
        }
    }
    
    /**
     * Переключает состояние Git MCP
     */
    fun toggleGitMcp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = _settings.value.copy(gitMcpEnabled = enabled)
                _settings.value = updated
                settingsRepository.saveSettings(updated)
                Log.d(TAG, "Git MCP: ${if (enabled) "включен" else "выключен"}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при сохранении настроек Git MCP", e)
            }
        }
    }
    
    /**
     * Переключает состояние Team MCP
     */
    fun toggleTeamMcp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) {
                    // Регистрируем инструменты Team MCP
                    val success = teamMcpService?.registerTeamMcpTools() ?: false
                    if (success) {
                        val updated = _settings.value.copy(teamMcpEnabled = true)
                        _settings.value = updated
                        settingsRepository.saveSettings(updated)
                        Log.d(TAG, "Team MCP включен и инструменты зарегистрированы")
                    } else {
                        Log.e(TAG, "Не удалось зарегистрировать Team MCP инструменты")
                        val updated = _settings.value.copy(teamMcpEnabled = false)
                        _settings.value = updated
                        settingsRepository.saveSettings(updated)
                    }
                } else {
                    // Отключаем инструменты Team MCP
                    teamMcpService?.unregisterTeamMcpTools()
                    val updated = _settings.value.copy(teamMcpEnabled = false)
                    _settings.value = updated
                    settingsRepository.saveSettings(updated)
                    Log.d(TAG, "Team MCP выключен")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при сохранении настроек Team MCP", e)
            }
        }
    }
    
    /**
     * Переключает состояние RAG
     */
    fun toggleRag(enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = _settings.value.copy(ragEnabled = enabled)
                _settings.value = updated
                settingsRepository.saveSettings(updated)
                Log.d(TAG, "RAG: ${if (enabled) "включен" else "выключен"}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при сохранении настроек RAG", e)
            }
        }
    }
    
    /**
     * Запускает индексацию документации проекта
     */
    fun indexDocumentation() {
        if (indexDocumentationUseCase == null) {
            Log.w(TAG, "IndexDocumentationUseCase не доступен")
            return
        }
        
        viewModelScope.launch {
            try {
                _isIndexing.value = true
                _indexingProgress.value = "Начало индексации..."
                
                // Здесь можно добавить конкретные документы для индексации
                // Для примера - индексируем тестовый текст
                val testDocuments = listOf(
                    Triple(
                        "README.md",
                        """
                        # OpenRouter Agent
                        
                        AI-агент на Kotlin с использованием OpenRouter API.
                        
                        ## Функции агента
                        
                        - get_current_time - получение текущего времени
                        - calculator - математические вычисления
                        - search - поиск информации
                        - random_number - генерация случайного числа
                        """.trimIndent(),
                        "README"
                    )
                )
                
                val result = indexDocumentationUseCase.indexDocuments(testDocuments)
                
                result.onSuccess { totalChunks ->
                    _indexingProgress.value = "Индексация завершена: $totalChunks чанков"
                    
                    // Обновляем количество проиндексированных документов из базы данных
                    loadRagDocumentCount()
                    
                    Log.d(TAG, "Индексация завершена: $totalChunks чанков")
                }.onFailure { e ->
                    _indexingProgress.value = "Ошибка: ${e.message}"
                    Log.e(TAG, "Ошибка индексации", e)
                }
            } catch (e: Exception) {
                _indexingProgress.value = "Ошибка: ${e.message}"
                Log.e(TAG, "Ошибка индексации", e)
            } finally {
                _isIndexing.value = false
            }
        }
    }
}
