package com.example.aiadvent_1

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen {
    object Chat : Screen()
    object StepByStepChat : Screen()
    object LogicalTaskChat : Screen()
    object ExpertsGroupChat : Screen()
}

@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    
    when (currentScreen) {
        is Screen.Chat -> {
            ChatScreenWithNavigation(
                onNavigateToStepByStep = {
                    currentScreen = Screen.StepByStepChat
                },
                onNavigateToLogicalTask = {
                    currentScreen = Screen.LogicalTaskChat
                },
                onNavigateToExpertsGroup = {
                    currentScreen = Screen.ExpertsGroupChat
                }
            )
        }
        is Screen.StepByStepChat -> {
            StepByStepChatScreen(
                onBackClick = {
                    currentScreen = Screen.Chat
                }
            )
        }
        is Screen.LogicalTaskChat -> {
            LogicalTaskChatScreen(
                onBackClick = {
                    currentScreen = Screen.Chat
                }
            )
        }
        is Screen.ExpertsGroupChat -> {
            ExpertsGroupChatScreen(
                onBackClick = {
                    currentScreen = Screen.Chat
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenWithNavigation(
    modifier: Modifier = Modifier,
    onNavigateToStepByStep: () -> Unit,
    onNavigateToLogicalTask: () -> Unit,
    onNavigateToExpertsGroup: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Chat", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToExpertsGroup) {
                        Icon(Icons.Default.Group, contentDescription = "Группа экспертов")
                    }
                    IconButton(onClick = onNavigateToLogicalTask) {
                        Icon(Icons.Default.Psychology, contentDescription = "Логические задачи")
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить чат")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToStepByStep,
                icon = {
                    Icon(Icons.Default.List, contentDescription = null)
                },
                text = { Text("Пошаговый анализ") }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                InputField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    onSendClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    isLoading = isLoading
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
            
            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            }
        }
    }
}

