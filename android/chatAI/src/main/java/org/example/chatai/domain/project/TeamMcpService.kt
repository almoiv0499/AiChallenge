package org.example.chatai.domain.project

import android.util.Log
import org.example.chatai.domain.tools.AgentTool
import org.example.chatai.domain.tools.ToolRegistry

private const val TAG = "TeamMcpService"

/**
 * Сервис для управления регистрацией инструментов Team MCP (Project Task API).
 * Регистрирует инструменты в ToolRegistry при включении настройки.
 */
class TeamMcpService(
    private val toolRegistry: ToolRegistry
) {
    private var isRegistered = false
    private var projectTaskClient: ProjectTaskClient? = null
    private val registeredTools = mutableListOf<AgentTool>()
    
    /**
     * Регистрирует инструменты Team MCP в ToolRegistry
     */
    fun registerTeamMcpTools(): Boolean {
        if (isRegistered) {
            Log.d(TAG, "Team MCP инструменты уже зарегистрированы")
            return true
        }
        
        return try {
            projectTaskClient = ProjectTaskClient()
            val client = projectTaskClient!!
            
            val tools = listOf(
                CreateProjectTaskTool(client),
                GetProjectTasksTool(client),
                UpdateProjectTaskTool(client),
                GetProjectStatusTool(client),
                GetTeamCapacityTool(client)
            )
            
            tools.forEach { tool ->
                toolRegistry.register(tool)
                registeredTools.add(tool)
            }
            
            isRegistered = true
            Log.d(TAG, "Team MCP инструменты зарегистрированы (${tools.size} инструментов)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка регистрации Team MCP инструментов", e)
            false
        }
    }
    
    /**
     * Удаляет инструменты Team MCP из ToolRegistry
     * 
     * Примечание: ToolRegistry не имеет метода удаления, поэтому инструменты остаются в реестре
     * до следующего перезапуска приложения. Для полного удаления нужно пересоздать ToolRegistry.
     */
    fun unregisterTeamMcpTools() {
        if (!isRegistered) {
            Log.d(TAG, "Team MCP инструменты не были зарегистрированы")
            return
        }
        
        try {
            // Закрываем клиент
            projectTaskClient?.close()
            projectTaskClient = null
            
            isRegistered = false
            registeredTools.clear()
            
            Log.d(TAG, "Team MCP инструменты отключены (примечание: они остаются в ToolRegistry до перезапуска)")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при отключении Team MCP инструментов", e)
        }
    }
    
    /**
     * Проверяет, зарегистрированы ли инструменты Team MCP
     */
    fun isTeamMcpRegistered(): Boolean = isRegistered
}
