package org.example.aichallenge.di

import android.content.Context
import org.example.chatai.data.local.ChatDatabase
import org.example.chatai.data.repository.ChatRepository
import org.example.chatai.domain.api.OpenRouterApiService
import org.example.chatai.domain.usecase.LoadChatHistoryUseCase
import org.example.chatai.domain.usecase.SendMessageUseCase
import org.example.chatai.domain.usecase.SendMessageWithToolsUseCase
import org.example.chatai.domain.usecase.IndexDocumentationUseCase
import org.example.chatai.domain.tools.ToolRegistry
import org.example.chatai.domain.embedding.EmbeddingClient
import org.example.chatai.domain.embedding.DocumentIndexer
import org.example.chatai.domain.embedding.RagService
import org.example.chatai.domain.project.TeamMcpService
import org.example.chatai.data.local.DocumentDao
import org.example.chatai.data.local.DocumentChunkDao
import org.example.aichallenge.ui.viewmodel.ChatViewModel
import org.example.aichallenge.ui.viewmodel.SettingsViewModel
import org.example.aichallenge.domain.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin модуль для Dependency Injection.
 * Определяет зависимости приложения и модуля chatAI.
 */
val appModule = module {
    
    // Database
    single {
        ChatDatabase.getDatabase(androidContext())
    }
    
    // DAO
    single {
        get<ChatDatabase>().chatMessageDao()
    }
    
    single {
        get<ChatDatabase>().documentDao()
    }
    
    single {
        get<ChatDatabase>().documentChunkDao()
    }
    
    // Repository
    single<ChatRepository> {
        ChatRepository(get())
    }
    
    // API Service
    single<OpenRouterApiService> {
        // API ключ получается из properties, установленных в MainActivity
        val apiKey = getProperty<String>("OPENROUTER_API_KEY") 
            ?: throw IllegalArgumentException("OPENROUTER_API_KEY не настроен")
        OpenRouterApiService(apiKey = apiKey)
    }
    
    // Tool Registry
    single<ToolRegistry> {
        ToolRegistry.createDefault()
    }
    
    // Team MCP Service
    single<TeamMcpService> {
        TeamMcpService(toolRegistry = get())
    }
    
    // Use Cases
    single<SendMessageWithToolsUseCase> {
        SendMessageWithToolsUseCase(
            chatRepository = get(),
            apiService = get(),
            toolRegistry = get(),
            ragService = get()
        )
    }
    
    single<SendMessageUseCase> {
        SendMessageUseCase(
            chatRepository = get(),
            apiService = get(),
            sendMessageWithToolsUseCase = get()
        )
    }
    
    single<LoadChatHistoryUseCase> {
        LoadChatHistoryUseCase(
            chatRepository = get()
        )
    }
    
    // Embedding Client
    single<EmbeddingClient> {
        val apiKey = getProperty<String>("OPENROUTER_API_KEY") 
            ?: throw IllegalArgumentException("OPENROUTER_API_KEY не настроен")
        EmbeddingClient(apiKey = apiKey)
    }
    
    // Document Indexer
    single<DocumentIndexer> {
        DocumentIndexer(
            embeddingClient = get(),
            documentDao = get(),
            documentChunkDao = get()
        )
    }
    
    // RAG Service
    single<RagService> {
        RagService(
            embeddingClient = get(),
            documentDao = get(),
            documentChunkDao = get()
        )
    }
    
    // Index Documentation Use Case
    single<IndexDocumentationUseCase> {
        IndexDocumentationUseCase(
            documentIndexer = get()
        )
    }
    
    // Settings Repository
    single<SettingsRepository> {
        SettingsRepository(androidContext())
    }
    
    // ViewModels
    viewModel<ChatViewModel> {
        ChatViewModel(
            sendMessageUseCase = get(),
            loadChatHistoryUseCase = get(),
            settingsRepository = get(),
            teamMcpService = get()
        )
    }
    
    viewModel<SettingsViewModel> {
        SettingsViewModel(
            settingsRepository = get(),
            indexDocumentationUseCase = get(),
            ragService = get(),
            teamMcpService = get()
        )
    }
}
