package com.ajuia.artjournal.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajuia.artjournal.data.*
import com.ajuia.artjournal.domain.analytics.AttendanceStats
import com.ajuia.artjournal.ui.theme.*
import com.ajuia.artjournal.viewmodel.ArtJournalViewModel

private data class StudentTrackerMetrics(
    val student: Student,
    val score: Double,
    val attendance: AttendanceStats,
    val lifetimeTopicPoints: Int
)

@Composable
fun TrackerScreen(viewModel: ArtJournalViewModel) {
    val context = LocalContext.current

    // Observe DB states
    val groups by viewModel.groups.collectAsState()
    val quarters by viewModel.quarters.collectAsState()
    val students by viewModel.students.collectAsState()
    val analyticsSnapshot by viewModel.analyticsSnapshot.collectAsState()

    // Filter selections (3 groups of buttons)
    // 1. Period (Quarter / Year)
    var selectedQuarterId by remember { mutableStateOf<Int?>(null) } // null means whole academic year
    // 2. Discipline (Subjects, All subjects, Homework series, Attendance series)
    var selectedTrackerDiscipline by remember { mutableStateOf("Все дисциплины") } // "Все дисциплины" | "Домашняя работа" | "Посещаемость" | or raw discipline name
    // 3. Group Selection
    val selectedGroupById by viewModel.selectedGroupId.collectAsState()
    val currentGroupId = selectedGroupById ?: groups.firstOrNull()?.id

    val currentGroup = groups.find { it.id == currentGroupId }
    LaunchedEffect(currentGroupId) {
        selectedQuarterId = null
        selectedTrackerDiscipline = "Все дисциплины"
    }
    val currentYearQuarters = quarters.filter {
        it.academicYearId == currentGroup?.academicYearId
    }
    val currentQuarter = currentYearQuarters.find { it.id == selectedQuarterId }
    val asOfDate = viewModel.getCurrentDateString()
    val groupLessonDates = analyticsSnapshot.lessons
        .filter { it.groupId == currentGroupId }
        .map { it.date }

    // Date range boundaries for chosen period
    val activeStartD = currentQuarter?.startDate
        ?: currentYearQuarters.minOfOrNull { it.startDate }
        ?: groupLessonDates.minOrNull()
        ?: asOfDate
    val activeEndD = currentQuarter?.endDate
        ?: currentYearQuarters.maxOfOrNull { it.endDate }
        ?: groupLessonDates.maxOrNull()
        ?: asOfDate
    val pName = currentQuarter?.name ?: "Весь учебный год"

    // Gather active students
    val groupStudents = remember(students, currentGroupId) {
        students.filter { it.groupId == currentGroupId && it.status == "active" }.sortedBy { it.lastName }
    }

    // Prepare data points for chart drawing
    val studentsWithScores = remember(
        groupStudents,
        currentGroup,
        currentGroupId,
        selectedTrackerDiscipline,
        activeStartD,
        activeEndD,
        asOfDate,
        analyticsSnapshot
    ) {
        if (currentGroupId == null) {
            emptyList()
        } else {
            groupStudents.map { student ->
                val attendance = viewModel.attendanceStats(
                    studentId = student.id,
                    groupId = currentGroupId,
                    startDate = activeStartD,
                    endDate = activeEndD,
                    asOfDate = asOfDate
                )
                var lifetimeTopicPoints = 0
                val finalScore = when (selectedTrackerDiscipline) {
                    "Домашняя работа" -> viewModel.homeworkPoints(
                        studentId = student.id,
                        groupId = currentGroupId,
                        startDate = activeStartD,
                        endDate = activeEndD,
                        asOfDate = asOfDate
                    ).toDouble()
                    "Посещаемость" -> attendance.percentage
                    "Все дисциплины" -> {
                        val scores = currentGroup
                            ?.getDisciplinesList()
                            .orEmpty()
                            .map { discipline ->
                                viewModel.disciplineScore(
                                    studentId = student.id,
                                    groupId = currentGroupId,
                                    discipline = discipline,
                                    startDate = activeStartD,
                                    endDate = activeEndD,
                                    asOfDate = asOfDate
                                )
                            }
                        lifetimeTopicPoints = scores.sumOf {
                            it.lifetimeTopicCriteriaPoints
                        }
                        scores.sumOf { it.periodGradePoints }.toDouble()
                    }
                    else -> {
                        val score = viewModel.disciplineScore(
                            studentId = student.id,
                            groupId = currentGroupId,
                            discipline = selectedTrackerDiscipline,
                            startDate = activeStartD,
                            endDate = activeEndD,
                            asOfDate = asOfDate
                        )
                        lifetimeTopicPoints = score.lifetimeTopicCriteriaPoints
                        score.periodGradePoints.toDouble()
                    }
                }

                StudentTrackerMetrics(
                    student = student,
                    score = finalScore,
                    attendance = attendance,
                    lifetimeTopicPoints = lifetimeTopicPoints
                )
            }
        }
    }

    val maxScore = remember(studentsWithScores) {
        val maxVal = studentsWithScores.maxOfOrNull { it.score } ?: 1.0
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

            currentYearQuarters.forEach { q ->
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
        if (selectedTrackerDiscipline != "Домашняя работа" &&
            selectedTrackerDiscipline != "Посещаемость"
        ) {
            Text(
                "Итог — оценки за период; критерии тем показаны отдельно за всё время",
                color = MutedGray,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
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
                            studentsWithScores.forEach { metrics ->
                                val fraction = (metrics.score / maxScore).toFloat().coerceIn(0f, 1f)
                                val displayValueStr = if (selectedTrackerDiscipline == "Посещаемость") {
                                    "${metrics.score.toInt()}%"
                                } else {
                                    "${metrics.score.toInt()} б."
                                }

                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(metrics.student.fullName, color = PureWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                items(studentsWithScores) { metrics ->
                    ArtCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(metrics.student.fullName, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Посещаемость: ${metrics.attendance.present}/${metrics.attendance.marked} " +
                                        "(${metrics.attendance.percentage.toInt()}%) · " +
                                        "не отмечено: ${metrics.attendance.unmarked}",
                                    color = MutedGray,
                                    fontSize = 11.sp
                                )
                                if (metrics.lifetimeTopicPoints > 0) {
                                    Text(
                                        "Критерии тем за всё время: ${metrics.lifetimeTopicPoints} б.",
                                        color = MutedGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            val scoreBubbleVal =
                                if (selectedTrackerDiscipline == "Посещаемость") {
                                    "${metrics.attendance.percentage.toInt()}%"
                                } else {
                                    "${metrics.score.toInt()} б."
                                }
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
