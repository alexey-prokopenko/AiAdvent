package com.example.aiadvent_1

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChatType {
    PRECISE,      // Температура 0.0
    CREATIVE,     // Температура 0.7
    EXPERIMENTAL // Температура 1.2
}

@Composable
fun MainScreen() {
    var currentChat by remember { mutableStateOf(ChatType.PRECISE) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Верхняя панель навигации
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavigationButton(
                    title = "Точный (0.0)",
                    icon = Icons.Default.Chat,
                    isSelected = currentChat == ChatType.PRECISE,
                    onClick = { currentChat = ChatType.PRECISE },
                    modifier = Modifier.weight(1f)
                )
                
                NavigationButton(
                    title = "Креативный (0.7)",
                    icon = Icons.Default.Psychology,
                    isSelected = currentChat == ChatType.CREATIVE,
                    onClick = { currentChat = ChatType.CREATIVE },
                    modifier = Modifier.weight(1f)
                )
                
                NavigationButton(
                    title = "Экспериментальный (1.2)",
                    icon = Icons.Default.AutoAwesome,
                    isSelected = currentChat == ChatType.EXPERIMENTAL,
                    onClick = { currentChat = ChatType.EXPERIMENTAL },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Контент чата
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentChat) {
                ChatType.PRECISE -> ChatScreen()
                ChatType.CREATIVE -> ChatScreenCreative()
                ChatType.EXPERIMENTAL -> ChatScreenExperimental()
            }
        }
    }
}

@Composable
fun NavigationButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

