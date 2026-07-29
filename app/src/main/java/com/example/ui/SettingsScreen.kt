package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuditLog
import com.example.ui.theme.*
import com.example.viewmodel.ArtJournalViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen(viewModel: ArtJournalViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Observe logs
    val logs by viewModel.auditLogs.collectAsState()

    var showClearWarning by remember { mutableStateOf(false) }
    var csvImportVal by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .padding(12.dp)
    ) {
        // --- TITLE ---
        Column {
            Text("Настройки Журнала", color = PrimaryYellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Резервное копирование и история изменений", color = MutedGray, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SHOW ARCHIVE SETTING ---
        val showArchived by viewModel.showArchivedStudents.collectAsState()
        ArtCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Показать архив", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Отображать архивированных учеников в списках групп журнала", color = MutedGray, fontSize = 12.sp)
                }
                Switch(
                    checked = showArchived,
                    onCheckedChange = { viewModel.toggleArchivedStudents(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepBlack,
                        checkedTrackColor = PrimaryYellow,
                        uncheckedThumbColor = MutedGray,
                        uncheckedTrackColor = DarkCard
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION 1: DATABASE CONSOLE ---
        ArtCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("База данных & Тесты", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Инициализация приложения тестовым расписанием художественной школы.", color = MutedGray, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtButton(
                        text = "Заполнить демо-данными",
                        onClick = { viewModel.loadTestData() },
                        modifier = Modifier.weight(1f)
                    )
                    ArtOutlinedButton(
                        text = "Полный сброс",
                        onClick = { showClearWarning = true },
                        modifier = Modifier.weight(1f),
                        borderColor = SoftRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION 2: CSV IMPORTER & EXPORTER ---
        ArtCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Резервное копирование (CSV)", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Экспортируйте или импортируйте журнал в один текстовый CSV клипборд.", color = MutedGray, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArtButton(
                        text = "Экспорт в буфер",
                        onClick = {
                            val csv = viewModel.exportToCSVString()
                            clipboard.setText(AnnotatedString(csv))
                            Toast.makeText(context, "Резервная копия скопирована в буфер!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ArtOutlinedButton(
                        text = "Импорт из буфера",
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 3: AUDIT ACTION HISTORY LOG LOGS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Журнал действий", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Последние 100 операций за учебный год", color = MutedGray, fontSize = 11.sp)
            }

            // Global Quick Undo layout
            if (logs.isNotEmpty() && logs.first().revertData != null) {
                ArtOutlinedButton(text = "Отменить последн.", onClick = { viewModel.triggerUndo() })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("История событий отсутствует", color = MutedGray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { log ->
                    val dateFormatted = remember(log.timestamp) {
                        try {
                            val sdf = SimpleDateFormat("HH:mm:ss dd.MM", Locale.getDefault())
                            sdf.format(Date(log.timestamp))
                        } catch (e: Exception) {
                            ""
                        }
                    }

                    ArtCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.action, color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(dateFormatted, color = MutedGray, fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(log.details, color = PureWhite, fontSize = 12.sp)
                            }

                            // Undo action item trigger if serialised revert details exist
                            if (log.revertData != null && log.id == logs.first().id) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.triggerUndo() },
                                    modifier = Modifier.background(DarkMutedYellow.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).size(32.dp)
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = "Отменить действие", tint = AccentYellow)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- WARNING DIALOGS ---

    // 1. CLEAR TABLES DIALOG
    if (showClearWarning) {
        AlertDialog(
            onDismissRequest = { showClearWarning = false },
            containerColor = DarkSurface,
            title = { Text("Очистить все данные?", color = SoftRed) },
            text = { Text("Принятие удалит все учебные группы, учеников и оценки безвозвратно. Рекомендуем сначала сделать резервный экспорт.", color = PureWhite) },
            confirmButton = {
                ArtButton(
                    text = "Подтверждаю удаление",
                    onClick = {
                        viewModel.clearAllData()
                        showClearWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed, contentColor = DeepBlack)
                )
            },
            dismissButton = {
                TextButton(onClick = { showClearWarning = false }) {
                    Text("Отмена", color = PrimaryYellow)
                }
            }
        )
    }

    // 2. CSV IMPORT PROMPT DIALOG
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            containerColor = DarkSurface,
            title = { Text("Импортировать резервную копию", color = PrimaryYellow) },
            text = {
                Column {
                    Text("Вставьте сюда скопированный ранее текст CSV резервной копии:", color = MutedGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtTextField(
                        value = csvImportVal,
                        onValueChange = { csvImportVal = it },
                        label = "Текст CSV",
                        singleLine = false,
                        modifier = Modifier.height(140.dp)
                    )
                }
            },
            confirmButton = {
                ArtButton(text = "Импортировать", onClick = {
                    if (csvImportVal.isNotBlank()) {
                        viewModel.importFromCSVString(csvImportVal)
                        showImportDialog = false
                    } else {
                        Toast.makeText(context, "Буфер пуст", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Закрыть", color = MutedGray)
                }
            }
        )
    }
}
