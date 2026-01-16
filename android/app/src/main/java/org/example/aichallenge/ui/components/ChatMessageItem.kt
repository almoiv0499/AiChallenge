package org.example.aichallenge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.chatai.data.model.ChatMessage
import org.example.chatai.data.model.MessageRole

/**
 * Компонент для отображения одного сообщения в чате.
 * Различное оформление для сообщений пользователя и ассистента.
 */
@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isUserMessage = message.role == MessageRole.USER
    val isSystemMessage = message.role == MessageRole.SYSTEM
    val isToolMessage = message.role == MessageRole.TOOL || message.role == MessageRole.MCP
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUserMessage) Arrangement.End else Arrangement.Start
    ) {
        if (!isUserMessage) {
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    when {
                        isUserMessage -> MaterialTheme.colorScheme.primaryContainer
                        isSystemMessage -> MaterialTheme.colorScheme.surfaceVariant
                        isToolMessage -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                )
                .padding(12.dp)
        ) {
            Text(
                text = getRoleLabel(message.role),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        if (isUserMessage) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

/**
 * Получить метку для роли сообщения
 */
@Composable
private fun getRoleLabel(role: MessageRole): String {
    return when (role) {
        MessageRole.SYSTEM -> "System"
        MessageRole.USER -> "You"
        MessageRole.ASSISTANT -> "Assistant"
        MessageRole.TOOL -> "Tool"
        MessageRole.MCP -> "MCP"
    }
}
