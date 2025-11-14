package com.example.aiadvent_1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.WindowCompat
import com.example.aiadvent_1.memory.MemoryScreen
import com.example.aiadvent_1.ui.theme.AiAdvent_1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            AiAdvent_1Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AiAdventAppRoot()
                }
            }
        }
    }
}

@Composable
fun AiAdventAppRoot() {
    var currentSection by rememberSaveable { mutableStateOf(MainSection.Chat) }

    Scaffold(
        bottomBar = {
            AppBottomBar(
                currentSection = currentSection,
                onSectionSelected = { currentSection = it }
            )
        }
    ) { innerPadding ->
        when (currentSection) {
            MainSection.Chat -> ChatScreen(modifier = Modifier.padding(innerPadding))
            MainSection.Memory -> MemoryScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun AppBottomBar(
    currentSection: MainSection,
    onSectionSelected: (MainSection) -> Unit
) {
    NavigationBar {
        MainSection.entries.forEach { section ->
            NavigationBarItem(
                selected = section == currentSection,
                onClick = { onSectionSelected(section) },
                icon = { Icon(section.icon, contentDescription = section.title) },
                label = { Text(section.title) }
            )
        }
    }
}

enum class MainSection(
    val title: String,
    val icon: ImageVector
) {
    Chat("Чат", Icons.Filled.Chat),
    Memory("Память", Icons.Filled.History)
}