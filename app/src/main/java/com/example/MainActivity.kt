package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.*
import com.example.ui.theme.*
import com.example.viewmodel.ArtJournalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Construct our unified view model instance
        val viewModel = ViewModelProvider(this)[ArtJournalViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val currentTab by viewModel.currentTab.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        ArtBottomBar(
                            currentTab = currentTab,
                            onTabSelected = { viewModel.setTab(it) }
                        )
                    },
                    containerColor = DeepBlack
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(DeepBlack)
                    ) {
                        // Smooth anim crossfade between the 5 modules
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(250),
                            label = "screen_transitions"
                        ) { tab ->
                            when (tab) {
                                "journal" -> JournalScreen(viewModel)
                                "themes" -> ThemesScreen(viewModel)
                                "schedule" -> ScheduleScreen(viewModel)
                                "tracker" -> TrackerScreen(viewModel)
                                "settings" -> SettingsScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtBottomBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        color = DarkSurface,
        border = BorderStroke(1.dp, BorderGray),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // safe bottom padding area
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                label = "Журнал",
                icon = Icons.Default.Book,
                isSelected = currentTab == "journal",
                onClick = { onTabSelected("journal") }
            )
            BottomBarItem(
                label = "Темы",
                icon = Icons.Default.Assignment,
                isSelected = currentTab == "themes",
                onClick = { onTabSelected("themes") }
            )
            BottomBarItem(
                label = "Календарь",
                icon = Icons.Default.CalendarMonth,
                isSelected = currentTab == "schedule",
                onClick = { onTabSelected("schedule") }
            )
            BottomBarItem(
                label = "Аналитика",
                icon = Icons.Default.TrendingUp,
                isSelected = currentTab == "tracker",
                onClick = { onTabSelected("tracker") }
            )
            BottomBarItem(
                label = "Настройки",
                icon = Icons.Default.Settings,
                isSelected = currentTab == "settings",
                onClick = { onTabSelected("settings") }
            )
        }
    }
}

@Composable
fun RowScope.BottomBarItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) PrimaryYellow else MutedGray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isSelected) PrimaryYellow else MutedGray,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
