package com.example.aiadvent_1.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiadvent_1.AiAdventApp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    modifier: Modifier = Modifier
) {
    val application = LocalContext.current.applicationContext as? AiAdventApp
        ?: error("Application must extend AiAdventApp")
    val viewModel: MemoryViewModel = viewModel(factory = MemoryViewModelFactory(application))
    val records by viewModel.records.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Память") },
                actions = {
                    IconButton(onClick = { viewModel.refreshSnapshot() }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить снимок")
                    }
                    IconButton(onClick = { showClearDialog = true }, enabled = records.isNotEmpty() && !isLoading) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить память")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            if (isLoading) {
                FloatingActionButton(onClick = {}) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                FloatingActionButton(onClick = { viewModel.refreshSnapshot() }) {
                    Icon(Icons.Default.History, contentDescription = "Сохранить снимок")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        MemoryContent(
            records = records,
            isLoading = isLoading,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить память") },
            text = { Text("Все сохраненные сообщения и метаданные будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearMemory()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Память очищена")
                        }
                    }
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun MemoryContent(
    records: List<MemoryRecord>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    when {
        records.isEmpty() && isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        records.isEmpty() -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Память пуста",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                items(records) { record ->
                    MemoryRecordCard(record)
                }
            }
        }
    }
}

@Composable
private fun MemoryRecordCard(record: MemoryRecord) {
    val isUser = record.role.equals("user", ignoreCase = true)
    val roleLabel = when {
        record.role.equals("assistant", ignoreCase = true) -> "AI"
        record.role.equals("system", ignoreCase = true) -> "System"
        else -> "Вы"
    }
    val timestampText = remember(record.timestamp) {
        val formatter = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
        formatter.format(Date(record.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = roleLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = timestampText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            record.metadata?.let { metadata ->
                Spacer(modifier = Modifier.height(8.dp))
                MetadataBlock(metadata = metadata)
            }
        }
    }
}

@Composable
private fun MetadataBlock(metadata: MemoryMetadata) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        metadata.responseTimeMs?.let {
            Text("Время ответа: ${it} мс", style = MaterialTheme.typography.bodySmall)
        }
        val tokens = listOfNotNull(
            metadata.promptTokens?.let { "Вход: $it" },
            metadata.completionTokens?.let { "Выход: $it" },
            metadata.totalTokens?.let { "Всего: $it" }
        )
        if (tokens.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tokens.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

