package com.example.aiadvent_1

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class Screen {
    object Main : Screen()
    object MiniMaxChat : Screen()
    object NeuralChat : Screen()
    object OpenHandsChat : Screen()
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    
    when (currentScreen) {
        is Screen.Main -> {
            ChatSelectionScreen(
                onMiniMaxChatClick = { currentScreen = Screen.MiniMaxChat },
                onNeuralChatClick = { currentScreen = Screen.NeuralChat },
                onOpenHandsChatClick = { currentScreen = Screen.OpenHandsChat }
            )
        }
        is Screen.MiniMaxChat -> {
            ChatScreen(
                onBackClick = { currentScreen = Screen.Main }
            )
        }
        is Screen.NeuralChat -> {
            NeuralChatScreen(
                onBackClick = { currentScreen = Screen.Main }
            )
        }
        is Screen.OpenHandsChat -> {
            OpenHandsChatScreen(
                onBackClick = { currentScreen = Screen.Main }
            )
        }
    }
}

@Composable
fun ChatSelectionScreen(
    onMiniMaxChatClick: () -> Unit,
    onNeuralChatClick: () -> Unit,
    onOpenHandsChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Выберите чат",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ChatOptionCard(
            title = "MiniMax Chat",
            description = "MiniMaxAI/MiniMax-M2:novita",
            onClick = onMiniMaxChatClick,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ChatOptionCard(
            title = "Neural Chat",
            description = "Intel/neural-chat-7b-v3-1:featherless-ai",
            onClick = onNeuralChatClick,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        ChatOptionCard(
            title = "Cogito Chat",
            description = "deepcogito/cogito-v1-preview-llama-8B:featherless-ai",
            onClick = onOpenHandsChatClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

