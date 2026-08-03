package com.ajuia.artjournal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajuia.artjournal.data.session.ServerSchool
import com.ajuia.artjournal.ui.theme.BorderGray
import com.ajuia.artjournal.ui.theme.DarkCard
import com.ajuia.artjournal.ui.theme.DeepBlack
import com.ajuia.artjournal.ui.theme.MutedGray
import com.ajuia.artjournal.ui.theme.PrimaryYellow
import com.ajuia.artjournal.ui.theme.PureWhite
import com.ajuia.artjournal.ui.theme.SoftRed
import com.ajuia.artjournal.viewmodel.ServerWorkspaceUiState

@Composable
fun WorkspaceChooserScreen(
    onSelectLocal: () -> Unit,
    onSelectServer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Художка Журнал",
            color = PrimaryYellow,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Выберите независимое рабочее пространство. Данные двух режимов не смешиваются.",
            color = MutedGray,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(28.dp))
        WorkspaceCard(
            title = "Сервер школы",
            description = "Вход по учётной записи, роли и выбор доступной школы.",
            testTag = "workspace-server",
            onClick = onSelectServer
        )
        Spacer(Modifier.height(12.dp))
        WorkspaceCard(
            title = "Локальный журнал",
            description = "Существующая автономная Room-база на этом устройстве.",
            testTag = "workspace-local",
            onClick = onSelectLocal
        )
    }
}

@Composable
private fun WorkspaceCard(
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, BorderGray),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(description, color = MutedGray, fontSize = 14.sp)
        }
    }
}

@Composable
fun ServerWorkspaceScreen(
    state: ServerWorkspaceUiState,
    onLogin: (String, String) -> Unit,
    onChooseSchool: (ServerSchool) -> Unit,
    onShowSchoolChooser: () -> Unit,
    onRetry: () -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
    onSwitchWorkspace: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .statusBarsPadding()
    ) {
        when (state) {
            ServerWorkspaceUiState.LoggedOut -> LoginScreen(
                onLogin = onLogin,
                onSwitchWorkspace = onSwitchWorkspace
            )
            ServerWorkspaceUiState.Loading -> CircularProgressIndicator(
                color = PrimaryYellow,
                modifier = Modifier.align(Alignment.Center).testTag("server-loading")
            )
            is ServerWorkspaceUiState.ChooseSchool -> SchoolChooser(
                state = state,
                onChooseSchool = onChooseSchool,
                onLogout = onLogout,
                onSwitchWorkspace = onSwitchWorkspace
            )
            is ServerWorkspaceUiState.Ready -> ServerHome(
                state = state,
                onShowSchoolChooser = onShowSchoolChooser,
                onLogout = onLogout,
                onSwitchWorkspace = onSwitchWorkspace
            )
            is ServerWorkspaceUiState.Error -> ErrorScreen(
                state = state,
                onRetry = onRetry,
                onShowLogin = onShowLogin,
                onSwitchWorkspace = onSwitchWorkspace
            )
        }
    }
}

@Composable
private fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onSwitchWorkspace: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Сервер школы", color = PrimaryYellow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Войдите в Django backend. Локальная база при этом не изменяется.",
            color = MutedGray
        )
        Spacer(Modifier.height(24.dp))
        ArtTextField(
            value = username,
            onValueChange = { username = it },
            label = "Логин",
            modifier = Modifier.testTag("server-username")
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryYellow,
                unfocusedBorderColor = BorderGray,
                focusedTextColor = PureWhite,
                unfocusedTextColor = PureWhite,
                cursorColor = PrimaryYellow
            ),
            modifier = Modifier.fillMaxWidth().testTag("server-password")
        )
        Spacer(Modifier.height(20.dp))
        ArtButton(
            text = "Войти",
            enabled = username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("server-login"),
            onClick = { onLogin(username, password) }
        )
        Spacer(Modifier.height(10.dp))
        ArtOutlinedButton(
            text = "Выбрать другое пространство",
            modifier = Modifier.fillMaxWidth(),
            onClick = onSwitchWorkspace
        )
    }
}

@Composable
private fun SchoolChooser(
    state: ServerWorkspaceUiState.ChooseSchool,
    onChooseSchool: (ServerSchool) -> Unit,
    onLogout: () -> Unit,
    onSwitchWorkspace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Выберите школу", color = PrimaryYellow, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Пользователь: ${state.session.user.displayName}", color = MutedGray)
        Spacer(Modifier.height(20.dp))
        state.session.schools.forEach { school ->
            WorkspaceCard(
                title = school.name,
                description = school.role.displayRole(),
                testTag = "school-${school.id}",
                onClick = { onChooseSchool(school) }
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(10.dp))
        ArtOutlinedButton("Выйти из учётной записи", Modifier.fillMaxWidth(), onClick = onLogout)
        Spacer(Modifier.height(8.dp))
        ArtOutlinedButton("Другое пространство", Modifier.fillMaxWidth(), onClick = onSwitchWorkspace)
    }
}

@Composable
private fun ServerHome(
    state: ServerWorkspaceUiState.Ready,
    onShowSchoolChooser: () -> Unit,
    onLogout: () -> Unit,
    onSwitchWorkspace: () -> Unit
) {
    val school = requireNotNull(state.session.selectedSchool)
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Сервер подключён", color = PrimaryYellow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        ArtCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(school.name, color = PureWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(school.role.displayRole(), color = MutedGray)
                Spacer(Modifier.height(12.dp))
                Text("Пользователь: ${state.session.user.displayName}", color = PureWhite)
                Text("Валюта: ${school.defaultCurrency}", color = MutedGray)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Чтение журнала и offline-синхронизация будут подключены следующим отдельным этапом.",
            color = MutedGray,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(20.dp))
        ArtButton("Сменить школу", Modifier.fillMaxWidth(), onClick = onShowSchoolChooser)
        Spacer(Modifier.height(8.dp))
        ArtOutlinedButton("Выйти", Modifier.fillMaxWidth(), onClick = onLogout)
        Spacer(Modifier.height(8.dp))
        ArtOutlinedButton("Другое пространство", Modifier.fillMaxWidth(), onClick = onSwitchWorkspace)
    }
}

@Composable
private fun ErrorScreen(
    state: ServerWorkspaceUiState.Error,
    onRetry: () -> Unit,
    onShowLogin: () -> Unit,
    onSwitchWorkspace: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Не удалось открыть сервер", color = SoftRed, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(state.message, color = PureWhite, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        if (state.canRetrySessionRestore) {
            ArtButton("Повторить", Modifier.fillMaxWidth(), onClick = onRetry)
        }
        Spacer(Modifier.height(8.dp))
        ArtOutlinedButton("Войти заново", Modifier.fillMaxWidth(), onClick = onShowLogin)
        Spacer(Modifier.height(8.dp))
        ArtOutlinedButton("Другое пространство", Modifier.fillMaxWidth(), onClick = onSwitchWorkspace)
    }
}

private fun String?.displayRole(): String = when (this) {
    "admin" -> "Администратор"
    "teacher" -> "Преподаватель"
    null -> "Доступ к школе"
    else -> this
}
