package org.example.chat

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.html.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.example.client.ollama.OllamaClient
import org.example.client.ollama.OllamaChatService
import org.example.client.ollama.OllamaMessage
import org.example.config.LoadedOllamaLlmConfig

/**
 * REST API сервер для чата с локальной моделью Ollama.
 * Использует [llmConfig] для температуры, контекста, max tokens и системного промпта.
 */
class ChatApiServer(
    private val ollamaClient: OllamaClient,
    private val chatService: OllamaChatService,
    private val historyStorage: ChatHistoryStorage,
    private val model: String,
    private val llmConfig: LoadedOllamaLlmConfig? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    fun configureApiServer(application: Application) {
        application.install(ContentNegotiation) {
            json(this@ChatApiServer.json)
        }
        application.install(CORS) {
            anyHost()
            allowHeader("Content-Type")
            allowHeader("Authorization")
        }
        
        application.routing {
            // Главная страница с веб-интерфейсом
            get("/") {
                call.respondHtml {
                    head {
                        meta(charset = "UTF-8")
                        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
                        title { +"Chat with AI" }
                        style {
                            unsafe {
                                raw("""
                                    * { margin: 0; padding: 0; box-sizing: border-box; }
                                    body {
                                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                                        min-height: 100vh;
                                        display: flex;
                                        justify-content: center;
                                        align-items: center;
                                        padding: 20px;
                                    }
                                    .container {
                                        background: white;
                                        border-radius: 20px;
                                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                                        width: 100%;
                                        max-width: 800px;
                                        height: 90vh;
                                        display: flex;
                                        flex-direction: column;
                                        overflow: hidden;
                                    }
                                    .header {
                                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                                        color: white;
                                        padding: 20px;
                                        text-align: center;
                                    }
                                    .header h1 { font-size: 24px; margin-bottom: 5px; }
                                    .header p { opacity: 0.9; font-size: 14px; }
                                    .chat-area {
                                        flex: 1;
                                        overflow-y: auto;
                                        padding: 20px;
                                        background: #f5f5f5;
                                    }
                                    .message {
                                        margin-bottom: 15px;
                                        animation: fadeIn 0.3s;
                                    }
                                    @keyframes fadeIn {
                                        from { opacity: 0; transform: translateY(10px); }
                                        to { opacity: 1; transform: translateY(0); }
                                    }
                                    .message.user {
                                        text-align: right;
                                    }
                                    .message.assistant {
                                        text-align: left;
                                    }
                                    .message-content {
                                        display: inline-block;
                                        max-width: 70%;
                                        padding: 12px 16px;
                                        border-radius: 18px;
                                        word-wrap: break-word;
                                    }
                                    .message.user .message-content {
                                        background: #667eea;
                                        color: white;
                                    }
                                    .message.assistant .message-content {
                                        background: white;
                                        color: #333;
                                        box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                                    }
                                    .input-area {
                                        padding: 20px;
                                        background: white;
                                        border-top: 1px solid #e0e0e0;
                                        display: flex;
                                        gap: 10px;
                                    }
                                    .input-area input {
                                        flex: 1;
                                        padding: 12px 16px;
                                        border: 2px solid #e0e0e0;
                                        border-radius: 25px;
                                        font-size: 16px;
                                        outline: none;
                                        transition: border-color 0.3s;
                                    }
                                    .input-area input:focus {
                                        border-color: #667eea;
                                    }
                                    .input-area button {
                                        padding: 12px 24px;
                                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                                        color: white;
                                        border: none;
                                        border-radius: 25px;
                                        font-size: 16px;
                                        cursor: pointer;
                                        transition: transform 0.2s;
                                    }
                                    .input-area button:hover {
                                        transform: scale(1.05);
                                    }
                                    .input-area button:disabled {
                                        opacity: 0.6;
                                        cursor: not-allowed;
                                    }
                                    .loading {
                                        display: inline-block;
                                        width: 20px;
                                        height: 20px;
                                        border: 3px solid #f3f3f3;
                                        border-top: 3px solid #667eea;
                                        border-radius: 50%;
                                        animation: spin 1s linear infinite;
                                    }
                                    @keyframes spin {
                                        0% { transform: rotate(0deg); }
                                        100% { transform: rotate(360deg); }
                                    }
                                    .clear-btn {
                                        background: #ff6b6b !important;
                                        margin-left: 10px;
                                    }
                                """)
                            }
                        }
                    }
                    body {
                        div(classes = "container") {
                            div(classes = "header") {
                                h1 { +"💬 AI Chat" }
                                p { +"Модель: $model | Локальная модель Ollama" }
                            }
                            div(classes = "chat-area") {
                                id = "chatArea"
                            }
                            div(classes = "input-area") {
                                input {
                                    type = InputType.text
                                    id = "messageInput"
                                    placeholder = "Введите ваш вопрос..."
                                    attributes["onkeypress"] = "if(event.key==='Enter') sendMessage()"
                                }
                                button {
                                    id = "sendButton"
                                    onClick = "sendMessage()"
                                    +"Отправить"
                                }
                                button(classes = "clear-btn") {
                                    id = "clearButton"
                                    onClick = "clearHistory()"
                                    +"Очистить"
                                }
                            }
                        }
                        script {
                            unsafe {
                                raw("""
                                    const chatArea = document.getElementById('chatArea');
                                    const messageInput = document.getElementById('messageInput');
                                    const sendButton = document.getElementById('sendButton');
                                    const clearButton = document.getElementById('clearButton');
                                    
                                    // Загружаем историю при загрузке страницы
                                    window.addEventListener('load', () => {
                                        loadHistory();
                                    });
                                    
                                    async function sendMessage() {
                                        const message = messageInput.value.trim();
                                        if (!message) return;
                                        
                                        // Добавляем сообщение пользователя
                                        addMessage('user', message);
                                        messageInput.value = '';
                                        sendButton.disabled = true;
                                        
                                        // Показываем индикатор загрузки
                                        const loadingId = addMessage('assistant', '<div class="loading"></div>', true);
                                        
                                        try {
                                            const response = await fetch('/api/chat', {
                                                method: 'POST',
                                                headers: {
                                                    'Content-Type': 'application/json'
                                                },
                                                body: JSON.stringify({ message: message })
                                            });
                                            
                                            const data = await response.json();
                                            
                                            // Удаляем индикатор загрузки
                                            removeMessage(loadingId);
                                            
                                            if (data.error) {
                                                addMessage('assistant', 'Ошибка: ' + data.error);
                                            } else {
                                                addMessage('assistant', data.response);
                                            }
                                        } catch (error) {
                                            removeMessage(loadingId);
                                            addMessage('assistant', 'Ошибка соединения: ' + error.message);
                                        } finally {
                                            sendButton.disabled = false;
                                            messageInput.focus();
                                        }
                                    }
                                    
                                    async function loadHistory() {
                                        try {
                                            const response = await fetch('/api/history');
                                            const data = await response.json();
                                            
                                            if (data.messages) {
                                                data.messages.forEach(msg => {
                                                    addMessage(msg.role, msg.content, false, false);
                                                });
                                            }
                                        } catch (error) {
                                            console.error('Ошибка загрузки истории:', error);
                                        }
                                    }
                                    
                                    async function clearHistory() {
                                        if (!confirm('Очистить историю диалога?')) return;
                                        
                                        try {
                                            const response = await fetch('/api/history', {
                                                method: 'DELETE'
                                            });
                                            
                                            if (response.ok) {
                                                chatArea.innerHTML = '';
                                                addMessage('assistant', 'История очищена');
                                            }
                                        } catch (error) {
                                            console.error('Ошибка очистки истории:', error);
                                        }
                                    }
                                    
                                    function addMessage(role, content, isLoading = false, scroll = true) {
                                        const messageDiv = document.createElement('div');
                                        messageDiv.className = 'message ' + role;
                                        messageDiv.id = isLoading ? 'loading-' + Date.now() : null;
                                        
                                        const contentDiv = document.createElement('div');
                                        contentDiv.className = 'message-content';
                                        if (isLoading) {
                                            contentDiv.innerHTML = content;
                                        } else {
                                            contentDiv.textContent = content;
                                        }
                                        
                                        messageDiv.appendChild(contentDiv);
                                        chatArea.appendChild(messageDiv);
                                        
                                        if (scroll) {
                                            chatArea.scrollTop = chatArea.scrollHeight;
                                        }
                                        
                                        return messageDiv.id;
                                    }
                                    
                                    function removeMessage(id) {
                                        const element = document.getElementById(id);
                                        if (element) {
                                            element.remove();
                                        }
                                    }
                                """)
                            }
                        }
                    }
                }
            }
            
            // API endpoint для отправки сообщения
            post("/api/chat") {
                try {
                    val request = call.receive<ChatRequest>()
                    val message = request.message.trim()
                    
                    if (message.isEmpty()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            buildJsonObject {
                                put("error", "Сообщение не может быть пустым")
                            }
                        )
                        return@post
                    }
                    
                    val history = historyStorage.getHistory()
                    val ollamaMessages = history.map { msg ->
                        OllamaMessage(role = msg.role, content = msg.content)
                    }
                    
                    val opts = llmConfig?.toOllamaOptions()
                    val sysPrompt = llmConfig?.systemPrompt
                    
                    // Отправляем запрос в Ollama (с опциями и системным промптом при наличии конфига)
                    val response = ollamaClient.chat(
                        message = message,
                        model = model,
                        conversationHistory = ollamaMessages,
                        systemPrompt = sysPrompt,
                        options = opts
                    )
                    
                    val assistantMessage = response.message?.content ?: "Ошибка: пустой ответ от модели"
                    
                    // Сохраняем оба сообщения (пользователя и ассистента) в историю
                    historyStorage.saveMessage("user", message)
                    historyStorage.saveMessage("assistant", assistantMessage)
                    
                    call.respond(
                        buildJsonObject {
                            put("response", assistantMessage)
                            put("model", response.model)
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        buildJsonObject {
                            put("error", "Ошибка обработки запроса: ${e.message}")
                        }
                    )
                }
            }
            
            // API endpoint для получения истории
            get("/api/history") {
                try {
                    val sessionId = call.request.queryParameters["session_id"] ?: "default"
                    val messages = historyStorage.getHistory(sessionId)
                    
                    // Преобразуем в формат для JSON
                    val messagesJson = buildJsonArray {
                        messages.forEach { msg ->
                            add(buildJsonObject {
                                put("id", msg.id)
                                put("role", msg.role)
                                put("content", msg.content)
                                put("createdAt", msg.createdAt)
                            })
                        }
                    }
                    
                    call.respond(
                        buildJsonObject {
                            put("messages", messagesJson)
                            put("count", messages.size)
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        buildJsonObject {
                            put("error", "Ошибка загрузки истории: ${e.message}")
                        }
                    )
                }
            }
            
            // API endpoint для очистки истории
            delete("/api/history") {
                try {
                    val sessionId = call.request.queryParameters["session_id"] ?: "default"
                    val cleared = historyStorage.clearHistory(sessionId)
                    
                    call.respond(
                        buildJsonObject {
                            put("success", cleared)
                            put("message", if (cleared) "История очищена" else "Ошибка очистки истории")
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        buildJsonObject {
                            put("error", "Ошибка очистки истории: ${e.message}")
                        }
                    )
                }
            }
            
            // Health check
            get("/api/health") {
                call.respond(
                    buildJsonObject {
                        put("status", "ok")
                        put("service", "Chat API Server")
                        put("model", model)
                        put("ollama_available", ollamaClient.isAvailable())
                    }
                )
            }
        }
    }
}

/**
 * Модель запроса для чата
 */
@kotlinx.serialization.Serializable
data class ChatRequest(
    val message: String,
    val sessionId: String? = null
)
