package com.ajuia.artjournal

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.ajuia.artjournal.ui.*
import com.ajuia.artjournal.ui.theme.*
import com.ajuia.artjournal.data.session.WorkspaceMode
import com.ajuia.artjournal.viewmodel.ArtJournalViewModel
import com.ajuia.artjournal.viewmodel.ArtJournalViewModelFactory
import com.ajuia.artjournal.viewmodel.ServerWorkspaceViewModel
import com.ajuia.artjournal.viewmodel.ServerWorkspaceViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ArtJournalApplication
        val viewModel = ViewModelProvider(
            this,
            ArtJournalViewModelFactory(
                application = app,
                repository = app.container.localJournalRepository,
                backupExporter = app.container.backupExporter
            )
        )[ArtJournalViewModel::class.java]
        val serverWorkspaceViewModel = ViewModelProvider(
            this,
            ServerWorkspaceViewModelFactory(app.container.serverSessionRepository)
        )[ServerWorkspaceViewModel::class.java]

        setContent {
            MyApplicationTheme {
                var workspaceMode by remember {
                    mutableStateOf(app.container.workspacePreferences.readMode())
                }
                val selectWorkspace: (WorkspaceMode) -> Unit = { mode ->
                    app.container.workspacePreferences.writeMode(mode)
                    workspaceMode = mode
                }
                val clearWorkspace = {
                    app.container.workspacePreferences.clearMode()
                    workspaceMode = null
                }

                when (workspaceMode) {
                    null -> WorkspaceChooserScreen(
                        onSelectLocal = { selectWorkspace(WorkspaceMode.LOCAL_LEGACY) },
                        onSelectServer = { selectWorkspace(WorkspaceMode.SERVER) }
                    )
                    WorkspaceMode.LOCAL_LEGACY -> LocalLegacyApp(
                        viewModel = viewModel,
                        onSwitchWorkspace = clearWorkspace
                    )
                    WorkspaceMode.SERVER -> {
                        val serverState by serverWorkspaceViewModel.uiState.collectAsState()
                        LaunchedEffect(Unit) { serverWorkspaceViewModel.activate() }
                        ServerWorkspaceScreen(
                            state = serverState,
                            onLogin = serverWorkspaceViewModel::login,
                            onChooseSchool = serverWorkspaceViewModel::chooseSchool,
                            onShowSchoolChooser = serverWorkspaceViewModel::showSchoolChooser,
                            onRetry = serverWorkspaceViewModel::retrySessionRestore,
                            onShowLogin = serverWorkspaceViewModel::showLogin,
                            onLogout = serverWorkspaceViewModel::logout,
                            onSwitchWorkspace = clearWorkspace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalLegacyApp(
    viewModel: ArtJournalViewModel,
    onSwitchWorkspace: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(color = DarkSurface, border = BorderStroke(1.dp, BorderGray)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Локальный журнал", color = PureWhite, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onSwitchWorkspace) {
                        Text("Сменить режим", color = PrimaryYellow)
                    }
                }
            }
        },
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
                testTag = "journal",
                label = "Журнал",
                icon = Icons.Default.Book,
                isSelected = currentTab == "journal",
                onClick = { onTabSelected("journal") }
            )
            BottomBarItem(
                testTag = "themes",
                label = "Темы",
                icon = Icons.Default.Assignment,
                isSelected = currentTab == "themes",
                onClick = { onTabSelected("themes") }
            )
            BottomBarItem(
                testTag = "schedule",
                label = "Календарь",
                icon = Icons.Default.CalendarMonth,
                isSelected = currentTab == "schedule",
                onClick = { onTabSelected("schedule") }
            )
            BottomBarItem(
                testTag = "tracker",
                label = "Аналитика",
                icon = Icons.Default.TrendingUp,
                isSelected = currentTab == "tracker",
                onClick = { onTabSelected("tracker") }
            )
            BottomBarItem(
                testTag = "settings",
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
    testTag: String,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .testTag("bottom-nav-$testTag")
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
