package org.example.aichallenge.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.example.aichallenge.data.Settings

private const val TAG = "SettingsRepository"
private const val PREFS_NAME = "chat_settings"
private const val KEY_NOTION_MCP = "notion_mcp_enabled"
private const val KEY_WEATHER_MCP = "weather_mcp_enabled"
private const val KEY_GIT_MCP = "git_mcp_enabled"
private const val KEY_TEAM_MCP = "team_mcp_enabled"
private const val KEY_RAG_ENABLED = "rag_enabled"
private const val KEY_RAG_INDEXED_COUNT = "rag_indexed_count"

/**
 * Репозиторий для управления настройками приложения.
 * Использует SharedPreferences для сохранения состояний.
 */
class SettingsRepository(
    private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Получает текущие настройки
     */
    fun getSettings(): Settings {
        return try {
            Settings(
                notionMcpEnabled = prefs.getBoolean(KEY_NOTION_MCP, false),
                weatherMcpEnabled = prefs.getBoolean(KEY_WEATHER_MCP, false),
                gitMcpEnabled = prefs.getBoolean(KEY_GIT_MCP, false),
                teamMcpEnabled = prefs.getBoolean(KEY_TEAM_MCP, false),
                ragEnabled = prefs.getBoolean(KEY_RAG_ENABLED, false),
                ragIndexedCount = prefs.getInt(KEY_RAG_INDEXED_COUNT, 0)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при чтении настроек", e)
            Settings()
        }
    }
    
    /**
     * Сохраняет настройки
     */
    suspend fun saveSettings(settings: Settings) {
        try {
            prefs.edit()
                .putBoolean(KEY_NOTION_MCP, settings.notionMcpEnabled)
                .putBoolean(KEY_WEATHER_MCP, settings.weatherMcpEnabled)
                .putBoolean(KEY_GIT_MCP, settings.gitMcpEnabled)
                .putBoolean(KEY_TEAM_MCP, settings.teamMcpEnabled)
                .putBoolean(KEY_RAG_ENABLED, settings.ragEnabled)
                .putInt(KEY_RAG_INDEXED_COUNT, settings.ragIndexedCount)
                .apply()
            Log.d(TAG, "Настройки сохранены")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении настроек", e)
        }
    }
}
