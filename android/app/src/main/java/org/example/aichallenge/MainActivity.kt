package org.example.aichallenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.example.aichallenge.ui.screen.ChatScreen
import org.example.aichallenge.ui.screen.SettingsScreen
import org.example.aichallenge.ui.theme.AiChallengeTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.example.aichallenge.ui.viewmodel.ChatViewModel
import org.example.aichallenge.ui.viewmodel.SettingsViewModel

enum class Screen {
    CHAT, SETTINGS
}

/**
 * Главная Activity приложения.
 * Инициализирует Koin DI и отображает экран чата.
 */
class MainActivity : ComponentActivity() {
    
    private val chatViewModel: ChatViewModel by viewModel()
    private val settingsViewModel: SettingsViewModel by viewModel()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            var currentScreen by remember { mutableStateOf(Screen.CHAT) }
            
            AiChallengeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        Screen.CHAT -> ChatScreen(
                            viewModel = chatViewModel,
                            onSettingsClick = { currentScreen = Screen.SETTINGS }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            viewModel = settingsViewModel,
                            onBackClick = { currentScreen = Screen.CHAT }
                        )
                    }
                }
            }
        }
    }
}
