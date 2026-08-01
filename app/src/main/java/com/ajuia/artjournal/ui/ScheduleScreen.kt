package com.ajuia.artjournal.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ajuia.artjournal.data.*
import com.ajuia.artjournal.ui.theme.*
import com.ajuia.artjournal.viewmodel.ArtJournalViewModel
import java.util.*

@Composable
fun ScheduleScreen(viewModel: ArtJournalViewModel) {
    val context = LocalContext.current

    // Observe DB States
    val academicYears by viewModel.academicYears.collectAsState()
    val activeYear by viewModel.activeYear.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val quarters by viewModel.quarters.collectAsState()
    val students by viewModel.students.collectAsState()

    // Dialog Toggles / States
    var showCreateYearDialog by remember { mutableStateOf(false) }
    var showCreateQuarterDialog by remember { mutableStateOf(false) }
    var showHolidayDialog by remember { mutableStateOf(false) }
    
    var editingQuarter by remember { mutableStateOf<Quarter?>(null) }
    var editingGroupSchedule by remember { mutableStateOf<Group?>(null) }
    var deletingYear by remember { mutableStateOf<AcademicYear?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Upper Title
        Column {
            Text("Календарь & Расписание", color = PrimaryYellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Учебный год, каникулы и четвертные рамки", color = MutedGray, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ACADEMIC YEAR ACTIVE SETUP
        ArtCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Активный учебный год", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { showCreateYearDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Создать год", tint = PrimaryYellow)
                    }
                }

                if (activeYear == null) {
                    Text("Учебный год не активирован. Создайте или выберите год.", color = SoftOrange, fontSize = 13.sp)
                } else {
                    Text("Активен сейчас: ${activeYear!!.name}", color = PureWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Глобальные праздники: " + if (activeYear!!.holidays.isNotBlank()) activeYear!!.holidays else "праздники не заполнены",
                        color = MutedGray,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    ArtOutlinedButton(text = "Задать нерабочие праздники") {
                        showHolidayDialog = true
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ACADEMIC YEARS LIST & REPOSITORY STORAGE
        ArtCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Все учебные года в приложении", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Переключайтесь между годами или удаляйте архивные", color = MutedGray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (academicYears.isEmpty()) {
                    Text("Список пуст", color = MutedGray, fontSize = 12.sp)
                } else {
                    academicYears.forEach { yr ->
                        val isCurrent = yr.id == activeYear?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCurrent) {
                                        viewModel.selectAcademicYear(yr.id)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isCurrent,
                                    onClick = { viewModel.selectAcademicYear(yr.id) },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryYellow)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = yr.name,
                                        color = if (isCurrent) PrimaryYellow else PureWhite,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                    if (isCurrent) {
                                        Text("Активный", color = SoftGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            IconButton(
                                onClick = { deletingYear = yr },
                                enabled = academicYears.size > 1
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Удалить учебный год",
                                    tint = if (isCurrent) MutedGray else SoftRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Divider(color = BorderGray, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // QUARTERS SCHEDULE CONTAINER SETUP (дни-метки с которых начинается новая четверть)
        ArtCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Учебные четверти / периоды", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Даты начала и окончания четвертей", color = MutedGray, fontSize = 11.sp)
                    }
                    IconButton(onClick = { if (activeYear != null) showCreateQuarterDialog = true }) {
                        Icon(Icons.Default.AddBox, contentDescription = null, tint = PrimaryYellow)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val activeQuarters = quarters.filter { it.academicYearId == activeYear?.id }
                if (activeQuarters.isEmpty()) {
                    Text("Периоды обучения (Четверти) не заданы. Привязка к четвертям неактивна.", color = MutedGray, fontSize = 12.sp)
                } else {
                    activeQuarters.forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(q.name, color = PureWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Даты: с ${q.startDate} по ${q.endDate}", color = MutedGray, fontSize = 11.sp)
                            }
                            Row {
                                IconButton(onClick = { editingQuarter = q }) {
                                    Icon(Icons.Default.Edit, tint = PrimaryYellow, contentDescription = "Редактировать четверть", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { viewModel.deleteQuarter(q) }) {
                                    Icon(Icons.Default.DeleteForever, tint = SoftRed, contentDescription = "Удалить четверть", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Divider(color = BorderGray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // GROUPS LISTING & REGULAR TEMPLATE
        Text("Шаблоны сетки расписания групп", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
        Text("Нажмите на группу, чтобы настроить дисциплины и расписание занятий", color = MutedGray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
        
        val activeGroups = groups.filter { it.academicYearId == activeYear?.id }
        if (activeGroups.isEmpty()) {
            Text("Нет зарегистрированных групп за этот год", color = MutedGray, fontSize = 12.sp)
        } else {
            activeGroups.forEach { grp ->
                val activeGrpStudentsCount = students.count { it.groupId == grp.id && it.status == "active" }
                ArtCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { editingGroupSchedule = grp }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(grp.name, color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = PrimaryYellow, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Предметы группы: " + grp.getDisciplinesList().joinToString(", "), color = MutedGray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Расписание: " + if (grp.schedule.isNotBlank()) grp.schedule else "шаблон не заполнен",
                            color = PrimaryYellow,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS SECTION ---

    // 1. ADD ACADEMIC YEAR DIALOG
    if (showCreateYearDialog) {
        var yName by remember { mutableStateOf("2026-2027") }
        var copyPrev by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateYearDialog = false },
            containerColor = DarkSurface,
            title = { Text("Создать новый учебный год", color = PrimaryYellow) },
            text = {
                Column {
                    ArtTextField(value = yName, onValueChange = { yName = it }, label = "Название периода (напр. 2026-2027)")
                    Spacer(modifier = Modifier.height(14.dp))

                    if (activeYear != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = copyPrev,
                                onCheckedChange = { copyPrev = it },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryYellow)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Скопировать группы прошлого года?", color = PureWhite, fontSize = 13.sp)
                        }
                        
                        if (copyPrev) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Будет скопирован список учебных групп предыдущего активного года со своим расписанием и предметами. Личные данные учеников, темы, баллы оценок, заметки, замечания и оплата обучения скопированы НЕ будут.",
                                color = SoftOrange,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    } else {
                        Text("Нет предыдущего учебного года для копирования групп.", color = MutedGray, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Создать", onClick = {
                    if (yName.isNotBlank()) {
                        viewModel.saveAcademicYear(name = yName.trim(), active = true, copyPreviousYearData = copyPrev)
                        showCreateYearDialog = false
                    } else {
                        Toast.makeText(context, "Укажите верное название", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { showCreateYearDialog = false }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 2. DETELETE ACADEMIC YEAR CONFRIM DIALOG
    if (deletingYear != null) {
        val yr = deletingYear!!
        AlertDialog(
            onDismissRequest = { deletingYear = null },
            containerColor = DarkSurface,
            title = { Text("Удалить учебный год?", color = SoftRed) },
            text = {
                Text(
                    text = "Вы собираетесь безвозвратно удалить учебный год \"${yr.name}\" и ВСЕ привязанные к нему группы, оценки, зачеты, каникулы и периоды! Это действие отменить нельзя. Вы уверены?",
                    color = PureWhite,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                ArtButton(
                    text = "Да, удалить всё",
                    colors = ButtonDefaults.buttonColors(containerColor = SoftRed, contentColor = DeepBlack),
                    onClick = {
                        viewModel.deleteAcademicYear(yr)
                        deletingYear = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { deletingYear = null }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 3. CREATE QUARTER CALENDAR BOUNDS DIALOG
    if (showCreateQuarterDialog && activeYear != null) {
        var name by remember { mutableStateOf("I четверть") }
        var startD by remember { mutableStateOf("2026-09-01") }
        var endD by remember { mutableStateOf("2026-10-25") }

        AlertDialog(
            onDismissRequest = { showCreateQuarterDialog = false },
            containerColor = DarkSurface,
            title = { Text("Создать учебную четверть", color = PrimaryYellow) },
            text = {
                Column {
                    ArtTextField(value = name, onValueChange = { name = it }, label = "Название периода")
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Начало: $startD", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        ArtOutlinedButton(text = "Выбрать") {
                            showDatePicker(context, startD) { startD = it }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Конец: $endD", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        ArtOutlinedButton(text = "Выбрать") {
                            showDatePicker(context, endD) { endD = it }
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Добавить", onClick = {
                    if (name.isNotBlank()) {
                        viewModel.saveQuarter(Quarter(academicYearId = activeYear!!.id, name = name.trim(), startDate = startD, endDate = endD))
                        showCreateQuarterDialog = false
                    } else {
                        Toast.makeText(context, "Заполните имя четверти", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { showCreateQuarterDialog = false }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 4. EDIT QUARTER CALENDAR BOUNDS DIALOG
    if (editingQuarter != null) {
        val quarter = editingQuarter!!
        var name by remember(quarter) { mutableStateOf(quarter.name) }
        var startD by remember(quarter) { mutableStateOf(quarter.startDate) }
        var endD by remember(quarter) { mutableStateOf(quarter.endDate) }

        AlertDialog(
            onDismissRequest = { editingQuarter = null },
            containerColor = DarkSurface,
            title = { Text("Редактировать четверть", color = PrimaryYellow) },
            text = {
                Column {
                    ArtTextField(value = name, onValueChange = { name = it }, label = "Название периода")
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Начало: $startD", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        ArtOutlinedButton(text = "Выбрать") {
                            showDatePicker(context, startD) { startD = it }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Конец: $endD", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        ArtOutlinedButton(text = "Выбрать") {
                            showDatePicker(context, endD) { endD = it }
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Сохранить", onClick = {
                    if (name.isNotBlank()) {
                        viewModel.updateQuarter(quarter.copy(name = name.trim(), startDate = startD, endDate = endD))
                        editingQuarter = null
                    } else {
                        Toast.makeText(context, "Имя не может быть пустым", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { editingQuarter = null }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 5. GLOBAL INTERACTIVE HOLIDAY CALENDAR BUILDER DIALOG
    if (showHolidayDialog && activeYear != null) {
        var holidaysCVS by remember { mutableStateOf(activeYear!!.holidays) }
        
        val parsedDates = remember(holidaysCVS) {
            holidaysCVS.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

        // Calendar visual state variables
        val rightNow = remember { Calendar.getInstance() }
        var currentYear by remember { mutableStateOf(rightNow.get(Calendar.YEAR)) }
        var currentMonth by remember { mutableStateOf(rightNow.get(Calendar.MONTH)) }

        val monthNames = remember {
            listOf(
                "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
            )
        }

        AlertDialog(
            onDismissRequest = { showHolidayDialog = false },
            containerColor = DarkSurface,
            title = { Text("Праздники & Каникулы (Глобальные)", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Введите даты нерабочих дней текстом через запятую или нажмите на них в интерактивном календаре:", color = MutedGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    ArtTextField(
                        value = holidaysCVS,
                        onValueChange = { holidaysCVS = it },
                        label = "Даты праздников (ГГГГ-ММ-ДД)"
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Выбрать на календаре:", color = PrimaryYellow, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    // Calendar controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear -= 1
                            } else {
                                currentMonth -= 1
                            }
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий месяц", tint = PrimaryYellow)
                        }
                        Text(
                            text = "${monthNames[currentMonth]} $currentYear",
                            color = PureWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        IconButton(onClick = {
                            if (currentMonth == 11) {
                                currentMonth = 0
                                currentYear += 1
                            } else {
                                currentMonth += 1
                            }
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Следующий месяц", tint = PrimaryYellow)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Days of week header Row
                    val daysHeader = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        daysHeader.forEach { h ->
                            Text(
                                text = h,
                                color = if (h == "Сб" || h == "Вс") SoftOrange else MutedGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(34.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Generate Monthly Grid Rows
                    val tempCal = remember(currentYear, currentMonth) {
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, currentYear)
                            set(Calendar.MONTH, currentMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    val firstDayOffset = (tempCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                    val maxDays = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val totalSlots = firstDayOffset + maxDays
                    val totalRows = (totalSlots + 6) / 7

                    Column {
                        for (r in 0 until totalRows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                for (c in 0 until 7) {
                                    val cellIndex = r * 7 + c
                                    if (cellIndex < firstDayOffset || cellIndex >= firstDayOffset + maxDays) {
                                        Box(modifier = Modifier.size(34.dp))
                                    } else {
                                        val dayNum = cellIndex - firstDayOffset + 1
                                        val dateStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, dayNum)
                                        val isSelected = parsedDates.contains(dateStr)

                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(
                                                    if (isSelected) PrimaryYellow else Color.Transparent,
                                                    shape = RoundedCornerShape(17.dp)
                                                )
                                                .clickable {
                                                    val parts = holidaysCVS.split(",")
                                                        .map { it.trim() }
                                                        .filter { it.isNotEmpty() }
                                                        .toMutableSet()
                                                    if (parts.contains(dateStr)) {
                                                        parts.remove(dateStr)
                                                    } else {
                                                        parts.add(dateStr)
                                                    }
                                                    holidaysCVS = parts.sorted().joinToString(", ")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                color = if (isSelected) DeepBlack else PureWhite,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Сохранить", onClick = {
                    viewModel.updateAcademicYearHolidays(activeYear!!, holidaysCVS.trim())
                    showHolidayDialog = false
                })
            },
            dismissButton = {
                TextButton(onClick = { showHolidayDialog = false }) {
                    Text("Закрыть", color = MutedGray)
                }
            }
        )
    }

    // 6. EDIT GROUP SCHEDULE & TIME GRID DIALOG
    if (editingGroupSchedule != null) {
        val groupToEdit = editingGroupSchedule!!
        var gName by remember(groupToEdit) { mutableStateOf(groupToEdit.name) }
        val disciplinesList = remember(groupToEdit) {
            mutableStateListOf<String>().apply {
                addAll(groupToEdit.getDisciplinesList())
            }
        }
        val selectedDaysSchedule = remember(groupToEdit) {
            val map = mutableStateMapOf<Int, String>()
            groupToEdit.schedule.split(",").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val day = parts[0].trim().toIntOrNull()
                    if (day != null) {
                        map[day] = parts[1].trim()
                    }
                }
            }
            map
        }

        AlertDialog(
            onDismissRequest = { editingGroupSchedule = null },
            containerColor = DarkSurface,
            title = { Text("Настройки расписания группы", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArtTextField(value = gName, onValueChange = { gName = it }, label = "Название группы")
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Учебные дисциплины:", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    disciplinesList.forEachIndexed { idx, disc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            var tempDisc by remember(disc) { mutableStateOf(disc) }
                            ArtTextField(
                                value = tempDisc,
                                onValueChange = { newVal ->
                                    tempDisc = newVal
                                    disciplinesList[idx] = newVal
                                },
                                label = "Предмет ${idx + 1}",
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (disciplinesList.size > 1) {
                                    val removedName = disciplinesList[idx]
                                    disciplinesList.removeAt(idx)
                                    selectedDaysSchedule.entries.forEach { entry ->
                                        if (entry.value == removedName) {
                                            val firstRemaining = disciplinesList.firstOrNull { it.isNotBlank() }
                                            if (firstRemaining != null) {
                                                selectedDaysSchedule[entry.key] = firstRemaining
                                            } else {
                                                selectedDaysSchedule.remove(entry.key)
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Нужна хотя бы одна дисциплина", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить предмет", tint = SoftRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtOutlinedButton(text = "Добавить предмет", onClick = {
                        disciplinesList.add("")
                    })
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Дни недели (шаблон расписания):", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val daysOfWeek = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    daysOfWeek.forEachIndexed { idx, name ->
                        val dayNum = idx + 1
                        val activeDisc = selectedDaysSchedule[dayNum]
                        var showDaySelector by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = activeDisc != null,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val firstNonNull = disciplinesList.firstOrNull { it.isNotBlank() } ?: "Живопись"
                                        selectedDaysSchedule[dayNum] = firstNonNull
                                    } else {
                                        selectedDaysSchedule.remove(dayNum)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryYellow)
                            )
                            Text(name, color = PureWhite, modifier = Modifier.width(36.dp), fontSize = 13.sp)

                            if (activeDisc != null) {
                                Box(
                                    modifier = Modifier
                                        .background(DarkCard, RoundedCornerShape(8.dp))
                                        .clickable { showDaySelector = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(activeDisc, color = PrimaryYellow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                DropdownMenu(
                                    expanded = showDaySelector,
                                    onDismissRequest = { showDaySelector = false },
                                    modifier = Modifier.background(DarkCard)
                                ) {
                                    disciplinesList.filter { it.isNotBlank() }.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option, color = PureWhite) },
                                            onClick = {
                                                selectedDaysSchedule[dayNum] = option
                                                showDaySelector = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Сохранить", onClick = {
                    if (gName.isNotBlank()) {
                        val dList = disciplinesList.map { it.trim() }.filter { it.isNotEmpty() }
                        viewModel.updateGroup(groupToEdit.id, gName.trim(), dList, selectedDaysSchedule.toMap())
                        editingGroupSchedule = null
                    } else {
                        Toast.makeText(context, "Имя группы пусто", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.deleteGroup(groupToEdit)
                        editingGroupSchedule = null
                    }) {
                        Text("Удалить группу", color = SoftRed)
                    }
                    TextButton(onClick = { editingGroupSchedule = null }) {
                        Text("Отмена", color = MutedGray)
                    }
                }
            }
        )
    }
}
