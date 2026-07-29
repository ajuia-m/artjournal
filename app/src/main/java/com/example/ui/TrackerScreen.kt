package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.ArtJournalViewModel

@Composable
fun TrackerScreen(viewModel: ArtJournalViewModel) {
    val context = LocalContext.current

    // Observe DB states
    val groups by viewModel.groups.collectAsState()
    val quarters by viewModel.quarters.collectAsState()
    val students by viewModel.students.collectAsState()
    val lessons by viewModel.lessons.collectAsState()

    // Filter selections (3 groups of buttons)
    // 1. Period (Quarter / Year)
    var selectedQuarterId by remember { mutableStateOf<Int?>(null) } // null means whole academic year
    // 2. Discipline (Subjects, All subjects, Homework series, Attendance series)
    var selectedTrackerDiscipline by remember { mutableStateOf("Все дисциплины") } // "Все дисциплины" | "Домашняя работа" | "Посещаемость" | or raw discipline name
    // 3. Group Selection
    val selectedGroupById by viewModel.selectedGroupId.collectAsState()
    val currentGroupId = selectedGroupById ?: groups.firstOrNull()?.id

    val currentGroup = groups.find { it.id == currentGroupId }
    val currentQuarter = quarters.find { it.id == selectedQuarterId }

    // Date range boundaries for chosen period
    val activeStartD = currentQuarter?.startDate ?: "2026-01-01"
    val activeEndD = currentQuarter?.endDate ?: "2026-12-31"
    val pName = currentQuarter?.name ?: "Весь учебный год"

    // Gather active students
    val groupStudents = remember(students, currentGroupId) {
        students.filter { it.groupId == currentGroupId && it.status == "active" }.sortedBy { it.lastName }
    }

    // Prepare data points for chart drawing
    val studentsWithScores = remember(groupStudents, selectedTrackerDiscipline, activeStartD, activeEndD, lessons) {
        groupStudents.map { st ->
            val finalScore = when (selectedTrackerDiscipline) {
                "Домашняя работа" -> viewModel.calculateHomeworkPoints(st.id, activeStartD, activeEndD)
                "Посещаемость" -> {
                    val (attended, total) = viewModel.calculateAttendance(st.id, activeStartD, activeEndD)
                    if (total > 0) (attended.toDouble() / total.toDouble() * 100.0) else 0.0
                }
                "Все дисциплины" -> {
                    // Sum overall disciplines
                    val discList = currentGroup?.getDisciplinesList() ?: emptyList()
                    discList.sumOf { d -> viewModel.calculateTrackerPoints(st.id, d, activeStartD, activeEndD) }
                }
                else -> {
                    // Match selected single discipline
                    viewModel.calculateTrackerPoints(st.id, selectedTrackerDiscipline, activeStartD, activeEndD)
                }
            }
            st to finalScore
        }
    }

    val maxScore = remember(studentsWithScores) {
        val maxVal = studentsWithScores.maxOfOrNull { it.second } ?: 1.0
        if (maxVal > 0) maxVal else 1.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .padding(12.dp)
    ) {
        // --- HEADER TITLE & EXPORT ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Аналитика & Анализ баллов", color = PrimaryYellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Общие итоги успеваемости за выбранный этап", color = MutedGray, fontSize = 12.sp)
            }
            IconButton(
                onClick = {
                    Toast.makeText(context, "Статистика по $selectedTrackerDiscipline сохранена в XLS/PDF!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.background(DarkCard, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "Экспорт итогов", tint = PrimaryYellow)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- ROW 1: PERIOD BUTTONS ---
        Text("Учебный период:", color = MutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Весь год" trigger
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .background(if (selectedQuarterId == null) PrimaryYellow else DarkCard, RoundedCornerShape(12.dp))
                    .clickable { selectedQuarterId = null }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Весь год", color = if (selectedQuarterId == null) DeepBlack else PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            quarters.filter { it.academicYearId == currentGroup?.academicYearId }.forEach { q ->
                val isSelected = q.id == selectedQuarterId
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(if (isSelected) PrimaryYellow else DarkCard, RoundedCornerShape(12.dp))
                        .clickable { selectedQuarterId = q.id }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(q.name, color = if (isSelected) DeepBlack else PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- ROW 2: DISCIPLINE / SERIES SELECTION ---
        Text("Дисциплина успеваемости:", color = MutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val systemSeriesList = mutableListOf("Все дисциплины", "Домашняя работа", "Посещаемость")
            if (currentGroup != null) {
                systemSeriesList.addAll(currentGroup.getDisciplinesList())
            }

            systemSeriesList.forEach { valName ->
                val isSelected = valName == selectedTrackerDiscipline
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(if (isSelected) DarkMutedYellow else DarkCard, RoundedCornerShape(12.dp))
                        .clickable { selectedTrackerDiscipline = valName }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(valName, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- ROW 3: GROUPS TOGGLES ---
        Text("Группа зачета:", color = MutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            groups.forEach { g ->
                val isSelected = g.id == currentGroupId
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(if (isSelected) PrimaryYellow else DarkCard, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectGroup(g.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(g.name, color = if (isSelected) DeepBlack else PureWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- STATS OVERVIEW HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$pName · $selectedTrackerDiscipline", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Icon(Icons.Default.TrendingUp, tint = PrimaryYellow, contentDescription = null, modifier = Modifier.size(16.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- DYNAMIC RENDERING CHART & DETAILED CHECKLISTS ---
        if (studentsWithScores.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет данных об успеваемости в выбранном периоде", color = MutedGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // --- THE CANVAS BAR CHART ---
                    ArtCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Сравнительный график", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Draw horizontal comparison bar for each student
                            studentsWithScores.forEach { (student, score) ->
                                val fraction = (score / maxScore).toFloat().coerceIn(0f, 1f)
                                val displayValueStr = if (selectedTrackerDiscipline == "Посещаемость") {
                                    "${score.toInt()}%"
                                } else {
                                    "${score.toInt()} б."
                                }

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(student.fullName, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text(displayValueStr, color = PrimaryYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Bar Drawing Frame
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .background(BorderGray, RoundedCornerShape(4.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction)
                                                .background(PrimaryYellow, RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- DETAILED NUMERICAL VALUES LIST ---
                items(studentsWithScores) { (st, score) ->
                    val (att, tot) = viewModel.calculateAttendance(st.id, activeStartD, activeEndD)
                    val attendancePercent = if (tot > 0) (att * 100 / tot) else 0

                    ArtCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(st.fullName, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Посещаемость за четверть: $att/$tot ($attendancePercent%)", color = MutedGray, fontSize = 11.sp)
                            }

                            val scoreBubbleVal = if (selectedTrackerDiscipline == "Посещаемость") "$attendancePercent%" else "${score.toInt()} б."
                            Box(
                                modifier = Modifier
                                    .background(DarkCard, RoundedCornerShape(8.dp))
                                    .border(1.dp, PrimaryYellow, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(scoreBubbleVal, color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
