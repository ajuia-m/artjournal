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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.ArtJournalViewModel

@Composable
fun ThemesScreen(viewModel: ArtJournalViewModel) {
    val context = LocalContext.current

    // Collect DB states
    val topics by viewModel.topics.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val quarters by viewModel.quarters.collectAsState()
    val students by viewModel.students.collectAsState()
    val progressList by viewModel.studentTopicProgress.collectAsState()
    val lessons by viewModel.lessons.collectAsState()
    val activeYear by viewModel.activeYear.collectAsState()

    val currentYearGroups = remember(groups, activeYear) { groups.filter { it.academicYearId == activeYear?.id } }
    val availableDisciplines = remember(currentYearGroups) { currentYearGroups.flatMap { it.getDisciplinesList() }.distinct().sorted() }

    // UI Dialog selectors
    var showCreateTopicDialog by remember { mutableStateOf(false) }
    var editingTopic by remember { mutableStateOf<Topic?>(null) }
    var scoringTopic by remember { mutableStateOf<Topic?>(null) }

    // Helper: priority weight calculation for topic stages
    // Stages 61..99 are prioritised on top (weight 1), 1..60 are medium (weight 2), 0 or 100 go below (weight 3)
    fun getTopicPriorityWeight(topicId: Int): Int {
        val topicProg = progressList.filter { it.topicId == topicId }
        val avgStage = if (topicProg.isNotEmpty()) topicProg.map { it.stage }.average().toInt() else 0
        return when {
            avgStage in 61..99 -> 1
            avgStage in 1..60 -> 2
            else -> 3
        }
    }

    // Sort and Group topics by discipline
    val groupedTopics = remember(topics, progressList) {
        topics.sortedWith(
            compareBy<Topic> { it.discipline }
                .thenBy { getTopicPriorityWeight(it.id) }
                .thenBy { t ->
                    // average stage descending
                    val p = progressList.filter { it.topicId == t.id }
                    if (p.isNotEmpty()) -p.map { it.stage }.average() else 0.0
                }
        ).groupBy { it.discipline }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .padding(12.dp)
    ) {
        // Upper Title & Plus Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Темы зачетов & Проекты", color = PrimaryYellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Критерии художественной оценки", color = MutedGray, fontSize = 12.sp)
            }
            IconButton(
                onClick = { showCreateTopicDialog = true },
                modifier = Modifier.background(DarkCard, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.PostAdd, contentDescription = "Создать тему", tint = PrimaryYellow)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (groupedTopics.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(64.dp), tint = MutedGray)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Список учебных тем пуст", color = MutedGray)
                Spacer(modifier = Modifier.height(8.dp))
                ArtButton(text = "Создать Первую Тему", onClick = { showCreateTopicDialog = true })
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                groupedTopics.forEach { (discipline, topicList) ->
                    // Discipline Header Item
                    item {
                        Surface(
                            color = DarkCard,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = discipline.uppercase(),
                                color = PrimaryYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Topics Cards List
                    items(topicList) { topic ->
                        // Calculate average progress stage
                        val topicProg = progressList.filter { it.topicId == topic.id }
                        val avgProgress = if (topicProg.isNotEmpty()) topicProg.map { it.stage }.average().toInt() else 0

                        // Calculate bound lesson dates (earliest and latest date)
                        val boundLessons = lessons.filter { it.topicId == topic.id }.sortedBy { it.date }
                        val dateRangeStr = if (boundLessons.isNotEmpty()) {
                            val startD = boundLessons.first().date.substring(5).replace("-", ".")
                            val endD = boundLessons.last().date.substring(5).replace("-", ".")
                            "Период занятий: $startD — $endD"
                        } else {
                            "Календарные даты не привязаны"
                        }

                        ArtCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(topic.name, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text(dateRangeStr, color = MutedGray, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                                    }

                                    // Stage bubble on right
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when {
                                                    avgProgress > 60 -> DarkGreenBg
                                                    avgProgress in 1..60 -> DarkOrangeBg
                                                    else -> DarkCard
                                                },
                                                RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$avgProgress%",
                                            color = when {
                                                avgProgress > 60 -> SoftGreen
                                                avgProgress in 1..60 -> SoftOrange
                                                else -> MutedGray
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Criteria metrics listings
                                Text(
                                    text = "Критерии оценивания: " + topic.getCriteriaList().joinToString { "${it.first} (${it.second}б)" },
                                    color = PrimaryYellow,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Operations Action row (Edit, Rank, Duplicate, Delete)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.duplicateTopic(topic) }) {
                                        Icon(Icons.Default.CopyAll, contentDescription = "Дублировать", tint = PureWhite, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { editingTopic = topic }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = PureWhite, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { scoringTopic = topic }) {
                                        Icon(Icons.Default.Assessment, contentDescription = "Оценить учеников", tint = PrimaryYellow, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { viewModel.deleteTopic(topic) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить тему", tint = SoftRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. CREATE TOPIC MODAL
    if (showCreateTopicDialog) {
        var tName by remember { mutableStateOf("") }
        var discipline by remember { mutableStateOf("Живопись") }
        val criteriaList = remember { mutableStateListOf("Композиция" to 10, "Цвет" to 10, "Объем" to 5) }

        val boundGList = remember { mutableStateListOf<Int>() }
        val boundQList = remember { mutableStateListOf<Int>() }

        AlertDialog(
            onDismissRequest = { showCreateTopicDialog = false },
            containerColor = DarkSurface,
            title = { Text("Создать зачетную тему", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArtTextField(value = tName, onValueChange = { tName = it }, label = "Название темы")
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Дисциплина", color = MutedGray, fontSize = 11.sp)
                    var expandedDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ArtTextField(
                            value = discipline,
                            onValueChange = { discipline = it },
                            label = "Дисциплина (выберите или введите)"
                        )
                        IconButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryYellow)
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            availableDisciplines.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d, color = PureWhite) },
                                    onClick = {
                                        discipline = d
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Критерии оценивания (в столбик):", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    criteriaList.forEachIndexed { index, crit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            var cName by remember(crit.first) { mutableStateOf(crit.first) }
                            var cMaxStr by remember(crit.second) { mutableStateOf(crit.second.toString()) }

                            ArtTextField(
                                value = cName,
                                onValueChange = { newVal ->
                                    cName = newVal
                                    criteriaList[index] = newVal to (cMaxStr.toIntOrNull() ?: 10)
                                },
                                label = "Критерий ${index + 1}",
                                modifier = Modifier.weight(2f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            ArtTextField(
                                value = cMaxStr,
                                onValueChange = { newVal ->
                                    cMaxStr = newVal
                                    criteriaList[index] = cName to (newVal.toIntOrNull() ?: 10)
                                },
                                label = "Макс. балл",
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (criteriaList.size > 1) {
                                    criteriaList.removeAt(index)
                                } else {
                                    Toast.makeText(context, "Должен быть хотя бы один критерий", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Улить критерий", tint = SoftRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtOutlinedButton(text = "Добавить критерий", onClick = {
                        criteriaList.add("" to 10)
                    })
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Привязать к группам:", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    groups.forEach { g ->
                        val checked = boundGList.contains(g.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { if (it) boundGList.add(g.id) else boundGList.remove(g.id) },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryYellow)
                            )
                            Text(g.name, color = PureWhite, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Создать", onClick = {
                    if (tName.isNotBlank()) {
                        val buildCriteria = criteriaList.filter { it.first.isNotBlank() }
                        viewModel.addTopic(
                            name = tName.trim(),
                            discipline = discipline,
                            criteriaList = buildCriteria,
                            boundGroups = boundGList.toList(),
                            boundQuarters = boundQList.toList()
                        )
                        showCreateTopicDialog = false
                    } else {
                        Toast.makeText(context, "Имя темы не может быть пустым", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { showCreateTopicDialog = false }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 2. SCORING METHOD DIALOG
    if (scoringTopic != null) {
        val topic = scoringTopic!!
        val parsedCriteria = topic.getCriteriaList()

        // Gather student items belonging to topic bound groups
        val activeGroupIds = remember(topic) {
            topic.groupIds.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        val boundStudents = remember(students, activeGroupIds) {
            students.filter { it.status == "active" && activeGroupIds.contains(it.groupId) }
        }

        AlertDialog(
            onDismissRequest = { scoringTopic = null },
            containerColor = DarkSurface,
            title = { Text("Оценка учеников по критериям темы", color = PrimaryYellow, fontSize = 15.sp) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(topic.name, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Divider(color = BorderGray, modifier = Modifier.padding(vertical = 8.dp))

                    if (boundStudents.isEmpty()) {
                        Text("Нет учеников в привязанных к теме группах", color = MutedGray, fontSize = 12.sp)
                    }

                    boundStudents.forEach { st ->
                        val prog = progressList.find { it.studentId == st.id && it.topicId == topic.id }
                        var stageVal by remember(st, prog) { mutableStateOf(prog?.stage?.toString() ?: "0") }

                        // Score state mappings per metric
                        val gradesMap = remember(st, prog) { mutableStateMapOf<String, String>().apply {
                            val existing = prog?.getGradesMap() ?: emptyMap()
                            parsedCriteria.forEach { crit ->
                                put(crit.first, (existing[crit.first] ?: 0).toString())
                            }
                        }}

                        val maxPossibleTotal = parsedCriteria.sumOf { it.second }
                        val currentTotal = gradesMap.entries.sumOf { entry -> entry.value.toIntOrNull() ?: 0 }

                        ArtCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(st.fullName, color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Сумма баллов: $currentTotal из $maxPossibleTotal", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // Stage manual + slider input
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ArtTextField(
                                        value = stageVal,
                                        onValueChange = { newVal ->
                                            stageVal = newVal
                                            val num = newVal.toIntOrNull()
                                            if (num != null) {
                                                val checkedStage = num.coerceIn(0, 100)
                                                val finalGrades = gradesMap.mapValues { it.value.toIntOrNull() ?: 0 }
                                                viewModel.saveStudentTopicProgress(st.id, topic.id, checkedStage, finalGrades)
                                            }
                                        },
                                        label = "Стадия %",
                                        modifier = Modifier.width(90.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Slider(
                                        value = stageVal.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f,
                                        onValueChange = { newValFloat ->
                                            val newValInt = newValFloat.toInt()
                                            stageVal = newValInt.toString()
                                            val finalGrades = gradesMap.mapValues { it.value.toIntOrNull() ?: 0 }
                                            viewModel.saveStudentTopicProgress(st.id, topic.id, newValInt, finalGrades)
                                        },
                                        valueRange = 0f..100f,
                                        modifier = Modifier.weight(1f),
                                        colors = SliderDefaults.colors(
                                            thumbColor = PrimaryYellow,
                                            activeTrackColor = PrimaryYellow
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Критерии оценивания:", color = MutedGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                // Render criteria metrics inputs with slider
                                parsedCriteria.forEach { crit ->
                                    val currentVal = gradesMap[crit.first] ?: "0"
                                    val currentValInt = currentVal.toIntOrNull() ?: 0
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text("${crit.first} (макс. ${crit.second}б):", color = PureWhite, fontSize = 11.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ArtTextField(
                                                    value = currentVal,
                                                    onValueChange = { newVal ->
                                                        gradesMap[crit.first] = newVal
                                                        val parsedVal = newVal.toIntOrNull()
                                                        if (parsedVal != null) {
                                                            val checkedStage = stageVal.toIntOrNull()?.coerceIn(0, 100) ?: 0
                                                            val finalGrades = gradesMap.mapValues { it.value.toIntOrNull() ?: 0 }.toMutableMap()
                                                            finalGrades[crit.first] = parsedVal.coerceIn(0, crit.second)
                                                            viewModel.saveStudentTopicProgress(st.id, topic.id, checkedStage, finalGrades)
                                                        }
                                                    },
                                                    label = "Балл",
                                                    modifier = Modifier.width(90.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Slider(
                                                    value = currentValInt.toFloat().coerceIn(0f, crit.second.toFloat()),
                                                    onValueChange = { sliderVal ->
                                                        val newValInt = sliderVal.toInt()
                                                        gradesMap[crit.first] = newValInt.toString()
                                                        val checkedStage = stageVal.toIntOrNull()?.coerceIn(0, 100) ?: 0
                                                        val finalGrades = gradesMap.mapValues { it.value.toIntOrNull() ?: 0 }.toMutableMap()
                                                        finalGrades[crit.first] = newValInt
                                                        viewModel.saveStudentTopicProgress(st.id, topic.id, checkedStage, finalGrades)
                                                    },
                                                    valueRange = 0f..crit.second.toFloat(),
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = PrimaryYellow,
                                                        activeTrackColor = PrimaryYellow
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Завершить оценивание", onClick = { scoringTopic = null })
            }
        )
    }

    // 3. EDIT TOPIC METRICS DIALOG
    if (editingTopic != null) {
        val topic = editingTopic!!
        var tName by remember(topic) { mutableStateOf(topic.name) }
        var discipline by remember(topic) { mutableStateOf(topic.discipline) }
        val criteriaList = remember(topic) {
            mutableStateListOf<Pair<String, Int>>().apply {
                addAll(topic.getCriteriaList())
            }
        }

        AlertDialog(
            onDismissRequest = { editingTopic = null },
            containerColor = DarkSurface,
            title = { Text("Параметры темы зачета", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArtTextField(value = tName, onValueChange = { tName = it }, label = "Имя темы")
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Дисциплина", color = MutedGray, fontSize = 11.sp)
                    var expandedDropdown by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ArtTextField(
                            value = discipline,
                            onValueChange = { discipline = it },
                            label = "Дисциплина (выберите или введите)"
                        )
                        IconButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryYellow)
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.background(DarkCard)
                        ) {
                            availableDisciplines.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d, color = PureWhite) },
                                    onClick = {
                                        discipline = d
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Критерии оценивания (в столбик):", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    criteriaList.forEachIndexed { index, crit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            var cName by remember(crit.first) { mutableStateOf(crit.first) }
                            var cMaxStr by remember(crit.second) { mutableStateOf(crit.second.toString()) }

                            ArtTextField(
                                value = cName,
                                onValueChange = { newVal ->
                                    cName = newVal
                                    criteriaList[index] = newVal to (cMaxStr.toIntOrNull() ?: 10)
                                },
                                label = "Критерий ${index + 1}",
                                modifier = Modifier.weight(2f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            ArtTextField(
                                value = cMaxStr,
                                onValueChange = { newVal ->
                                    cMaxStr = newVal
                                    criteriaList[index] = cName to (newVal.toIntOrNull() ?: 10)
                                },
                                label = "Макс. балл",
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (criteriaList.size > 1) {
                                    criteriaList.removeAt(index)
                                } else {
                                    Toast.makeText(context, "Должен быть хотя бы один критерий", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить критерий", tint = SoftRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtOutlinedButton(text = "Добавить критерий", onClick = {
                        criteriaList.add("" to 10)
                    })
                }
            },
            confirmButton = {
                ArtButton(text = "Обновить параметры", onClick = {
                    if (tName.isNotBlank()) {
                        val buildCriteria = criteriaList.filter { it.first.isNotBlank() }
                        val criteriaString = buildCriteria.joinToString(",") { "${it.first}:${it.second}" }
                        viewModel.modifyTopic(topic.copy(name = tName.trim(), discipline = discipline.trim(), criteria = criteriaString))
                        editingTopic = null
                    } else {
                        Toast.makeText(context, "Имя темы не может быть пустым", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { editingTopic = null }) {
                    Text("Закрыть", color = MutedGray)
                }
            }
        )
    }
}

