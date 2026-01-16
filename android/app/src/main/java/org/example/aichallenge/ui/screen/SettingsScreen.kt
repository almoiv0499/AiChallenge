package org.example.aichallenge.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.aichallenge.ui.viewmodel.SettingsViewModel

/**
 * Экран настроек приложения.
 * Позволяет включать/выключать MCP серверы и RAG.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val isIndexing by viewModel.isIndexing.collectAsState()
    val indexingProgress by viewModel.indexingProgress.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var showIndexDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Заголовок MCP серверов
            Text(
                text = "MCP Серверы",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Divider()
            
            // Notion MCP
            SettingsItem(
                title = "Notion MCP",
                description = "Инструменты для работы с Notion (задачи, страницы)",
                enabled = settings.notionMcpEnabled,
                onToggle = { viewModel.toggleNotionMcp(it) }
            )
            
            // Weather MCP
            SettingsItem(
                title = "Weather MCP",
                description = "Получение информации о погоде",
                enabled = settings.weatherMcpEnabled,
                onToggle = { viewModel.toggleWeatherMcp(it) }
            )
            
            // Git MCP
            SettingsItem(
                title = "Git MCP",
                description = "Инструменты для работы с Git (ветки, коммиты, статус)",
                enabled = settings.gitMcpEnabled,
                onToggle = { viewModel.toggleGitMcp(it) }
            )
            
            // Team MCP (Project Task API)
            SettingsItem(
                title = "Team MCP",
                description = "Инструменты для работы с задачами команды (Project Task API)",
                enabled = settings.teamMcpEnabled,
                onToggle = { viewModel.toggleTeamMcp(it) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Заголовок RAG
            Text(
                text = "RAG (Поиск по документации)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Divider()
            
            // RAG
            SettingsItem(
                title = "RAG поиск",
                description = "Поиск ответов в документации проекта",
                enabled = settings.ragEnabled,
                onToggle = { viewModel.toggleRag(it) }
            )
            
            // Статус RAG
            if (settings.ragEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Статус RAG",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = if (settings.ragIndexedCount > 0) {
                                "Индексировано документов: ${settings.ragIndexedCount}"
                            } else {
                                "Документы не проиндексированы"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (settings.ragIndexedCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        
                        // Кнопка индексации
                        Button(
                            onClick = { showIndexDialog = true },
                            enabled = !isIndexing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isIndexing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Индексация... $indexingProgress")
                            } else {
                                Text("Индексировать документацию")
                            }
                        }
                    }
                }
            }
            
            // Диалог индексации
            if (showIndexDialog) {
                AlertDialog(
                    onDismissRequest = { showIndexDialog = false },
                    title = { Text("Индексация документации") },
                    text = {
                        Column {
                            Text("Индексировать документацию проекта для RAG поиска?")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Это займет некоторое время и потребует API запросов для генерации эмбеддингов.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showIndexDialog = false
                                coroutineScope.launch {
                                    viewModel.indexDocumentation()
                                }
                            }
                        ) {
                            Text("Начать индексацию")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showIndexDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
