package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.ArtJournalViewModel
import java.util.*

@Composable
fun JournalScreen(viewModel: ArtJournalViewModel) {
    val context = LocalContext.current
    val asOfDate = viewModel.getCurrentDateString()

    // Observe DB States
    val groups by viewModel.groups.collectAsState()
    val activeGroupIds by remember(groups) { derivedStateOf { groups.map { it.id } } }
    val selectedGroupById by viewModel.selectedGroupId.collectAsState()
    val rawStudents by viewModel.students.collectAsState()
    val rawLessons by viewModel.lessons.collectAsState()
    val rawStates by viewModel.studentLessonStates.collectAsState()
    val rawPayments by viewModel.payments.collectAsState()
    val topics by viewModel.topics.collectAsState()
    val activeYear by viewModel.activeYear.collectAsState()

    // Filters UI State
    val disciplineFilter by viewModel.selectedDisciplineFilter.collectAsState()
    val showArchived by viewModel.showArchivedStudents.collectAsState()
    val dateFilterType by viewModel.dateFilterType.collectAsState()
    val selectedMonthFilter by viewModel.selectedMonthFilter.collectAsState()

    // Toggle collapsing names
    var isNamesCollapsed by remember { mutableStateOf(false) }

    // Dialog toggles
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var showAddLessonDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var editingLesson by remember { mutableStateOf<Lesson?>(null) }
    var gradingCell by remember { mutableStateOf<Pair<Student, Lesson>?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var editingGroupForJournal by remember { mutableStateOf<Group?>(null) }

    // Filter students belonging to active group and matching status
    val currentGroupId = selectedGroupById ?: groups.firstOrNull()?.id
    val groupStudents = remember(rawStudents, currentGroupId, showArchived) {
        rawStudents.filter { st ->
            st.groupId == currentGroupId && (st.status == "active" || (showArchived && st.status == "archived"))
        }.sortedBy { it.lastName }
    }

    // Filter group lessons
    val groupLessons = remember(rawLessons, currentGroupId, disciplineFilter, dateFilterType, selectedMonthFilter) {
        rawLessons.filter { les ->
            les.groupId == currentGroupId &&
            !les.isNonSchoolDay &&
            (disciplineFilter == null || les.discipline.equals(disciplineFilter, ignoreCase = true)) &&
            when (dateFilterType) {
                "today" -> viewModel.isDateToday(les.date)
                "week" -> viewModel.isDateThisWeek(les.date)
                "month" -> les.date.startsWith(selectedMonthFilter)
                else -> true
            }
        }.sortedBy { it.date }
    }

    val currentGroup = groups.find { it.id == currentGroupId }

    Column(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
        // --- Tab View with Groups ---
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            groups.forEach { g ->
                val isSelected = g.id == currentGroupId
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .background(if (isSelected) PrimaryYellow else DarkCard, RoundedCornerShape(20.dp))
                        .clickable {
                            if (isSelected) {
                                editingGroupForJournal = g
                            } else {
                                viewModel.selectGroup(g.id)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = g.name,
                        color = if (isSelected) DeepBlack else PureWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            // Button to quickly add group
            IconButton(
                onClick = { showScheduleDialog = true },
                modifier = Modifier.background(DarkCard, CircleShape).size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить группу", tint = PrimaryYellow)
            }
        }

        // --- Action controls above table ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dropdown month and custom quick toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { viewModel.setDateFilter("all") },
                    label = { Text("Все", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (dateFilterType == "all") DarkMutedYellow else DarkCard,
                        labelColor = PureWhite
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
                AssistChip(
                    onClick = { viewModel.setDateFilter("today") },
                    label = { Text("Сегодня", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (dateFilterType == "today") DarkMutedYellow else DarkCard,
                        labelColor = PureWhite
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
                AssistChip(
                    onClick = { viewModel.setDateFilter("week") },
                    label = { Text("Эта неделя", fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (dateFilterType == "week") DarkMutedYellow else DarkCard,
                        labelColor = PureWhite
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Quick month selector dropdown helper
                var showMonthMenu by remember { mutableStateOf(false) }
                val monthsList = listOf("2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-09", "2026-10", "2026-11", "2026-12")
                Box {
                    AssistChip(
                        onClick = { showMonthMenu = true },
                        label = { Text(if (dateFilterType == "month" && selectedMonthFilter.isNotBlank()) selectedMonthFilter else "Выбрать месяц", fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (dateFilterType == "month") DarkMutedYellow else DarkCard,
                            labelColor = PureWhite
                        )
                    )
                    DropdownMenu(
                        expanded = showMonthMenu,
                        onDismissRequest = { showMonthMenu = false },
                        modifier = Modifier.background(DarkCard)
                    ) {
                        monthsList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m, color = PureWhite) },
                                onClick = {
                                    viewModel.setDateFilter("month", m)
                                    showMonthMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Export to PDF button
            IconButton(
                onClick = { if (currentGroupId != null) viewModel.exportGroupJournalToPDF(currentGroupId) },
                modifier = Modifier.background(DarkCard, CircleShape)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = "Экспорт PDF", tint = PrimaryYellow)
            }
        }

        // --- Discipline Sub-Filters row ---
        if (currentGroup != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Дисциплины: ", color = MutedGray, fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(if (disciplineFilter == null) DarkMutedYellow else DarkCard, RoundedCornerShape(12.dp))
                        .clickable { viewModel.setDisciplineFilter(null) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Все", color = PureWhite, fontSize = 11.sp)
                }

                currentGroup.getDisciplinesList().forEach { d ->
                    val isSelected = d == disciplineFilter
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .background(if (isSelected) DarkMutedYellow else DarkCard, RoundedCornerShape(12.dp))
                            .clickable { viewModel.setDisciplineFilter(d) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(d, color = PureWhite, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- THE SPREADSHEET JOURNAL COMPONENT ---
        val horizontalScrollState = rememberScrollState()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (currentGroupId == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(64.dp), tint = MutedGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Создайте или выберите группу зачета", color = MutedGray)
                    Spacer(modifier = Modifier.height(8.dp))
                    ArtButton(text = "Добавить Группу", onClick = { showScheduleDialog = true })
                }
            } else {
                // We render a grid of (Sticky student column) alongside (scrollable date cells)
                Row(modifier = Modifier.fillMaxSize()) {
                    // 1. STICKY STUDENT COLUMNS (Frozen column)
                    Column(
                        modifier = Modifier
                            .width(if (isNamesCollapsed) 80.dp else 165.dp)
                            .border(1.dp, BorderGray)
                            .background(DarkSurface)
                    ) {
                        // Header cell: "Уч./Ученики" with togglable collapse
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(DarkSurface)
                                .clickable { isNamesCollapsed = !isNamesCollapsed }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isNamesCollapsed) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                                    contentDescription = null,
                                    tint = PrimaryYellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isNamesCollapsed) "Уч." else "Ученики",
                                    color = PrimaryYellow,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Divider(color = BorderGray)

                        // Student items vertical scrolling
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(groupStudents) { student ->
                                // Signals are deterministic for the same snapshot and reference date.
                                val hasAbsenceWarning = viewModel.hasConsecutiveAbsences(
                                    studentId = student.id,
                                    groupId = currentGroupId,
                                    asOfDate = asOfDate
                                )
                                val hasPaymentWarning = viewModel.hasStalePaymentRecord(
                                    studentId = student.id,
                                    asOfDate = asOfDate
                                )

                                val bgColor = when {
                                    hasAbsenceWarning -> DarkRedBg
                                    hasPaymentWarning -> DarkOrangeBg
                                    student.status == "archived" -> DarkCard
                                    else -> DarkSurface
                                }
                                val borderStroke = BorderStroke(
                                    1.dp,
                                    when {
                                        hasAbsenceWarning -> SoftRed
                                        hasPaymentWarning -> SoftOrange
                                        else -> BorderGray
                                    }
                                )

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = bgColor),
                                    border = borderStroke,
                                    shape = RoundedCornerShape(0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clickable { editingStudent = student }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isNamesCollapsed) {
                                            // Render abbreviations e.g. "И.М."
                                            val lastInitial = student.lastName.firstOrNull()?.toString() ?: ""
                                            val firstInitial = student.firstName.firstOrNull()?.toString() ?: ""
                                            Text(
                                                text = "$lastInitial.$firstInitial.",
                                                color = PureWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = student.fullName,
                                                    color = if (student.status == "archived") MutedGray else PureWhite,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (hasAbsenceWarning || hasPaymentWarning) {
                                                    Row {
                                                        if (hasAbsenceWarning) WarningBadge("2П", SoftRed, DeepBlack)
                                                        if (hasPaymentWarning) WarningBadge("Опл?", SoftOrange, DeepBlack)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Divider(color = BorderGray)
                            }

                            // Dynamic "+" button at end of list
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .background(DeepBlack)
                                        .clickable { showAddStudentDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = "Добавить ученика", tint = PrimaryYellow)
                                }
                                Divider(color = BorderGray)
                            }
                        }
                    }

                    // 2. SCROLLABLE CALENDAR LESSON DATA
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                            .border(1.dp, BorderGray)
                    ) {
                        // Headers Row: dates list & "+" triggers
                        Row(modifier = Modifier.height(56.dp).background(DarkSurface), verticalAlignment = Alignment.CenterVertically) {
                            // Left "+" before dates
                            IconButton(onClick = { showAddLessonDialog = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Добавить урок", tint = PrimaryYellow)
                            }

                            // Render Dates
                            groupLessons.forEach { les ->
                                val dateParts = les.date.split("-")
                                val displayDate = if (dateParts.size == 3) "${dateParts[2]}.${dateParts[1]}" else les.date
                                Column(
                                    modifier = Modifier
                                        .width(76.dp)
                                        .fillMaxHeight()
                                        .border(0.5.dp, BorderGray)
                                        .clickable { editingLesson = les }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = displayDate,
                                        color = PrimaryYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = les.displayDisciplineAbbreviation,
                                        color = PureWhite,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Quick All Present checker in header
                                    Text(
                                        text = "Все",
                                        color = SoftGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(DarkGreenBg, RoundedCornerShape(4.dp))
                                            .clickable { viewModel.markAllPresent(les.id) }
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            // Right "+" after dates
                            IconButton(onClick = { showAddLessonDialog = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.AddCircle, contentDescription = "Добавить урок", tint = PrimaryYellow)
                            }
                        }
                        Divider(color = BorderGray)

                        // Cell States Grid vertical columns aligning with student rows
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            // Align rows to students list
                            items(groupStudents) { student ->
                                Row(modifier = Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // Placeholder spacing to balance left "+"
                                    Spacer(modifier = Modifier.width(40.dp))

                                    groupLessons.forEach { les ->
                                        val state = rawStates.find { it.studentId == student.id && it.lessonId == les.id }
                                        val bubbleColor = when {
                                            state == null -> Color.Transparent
                                            !state.isPresent -> SoftRed
                                            state.grade != null -> PrimaryYellow
                                            else -> PureWhite
                                        }
                                        val displayChar = when {
                                            state == null -> ""
                                            !state.isPresent -> "Н"
                                            state.grade != null -> state.grade.toString()
                                            else -> "•"
                                        }

                                        Box(
                                            modifier = Modifier
                                                .width(76.dp)
                                                .fillMaxHeight()
                                                .border(0.5.dp, BorderGray)
                                                .background(if (state?.homeworkPoints != null) Color(0xFF1C2204) else Color.Transparent)
                                                .clickable { gradingCell = student to les },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (displayChar.isNotBlank()) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = bubbleColor.copy(alpha = 0.2f),
                                                        border = BorderStroke(1.dp, bubbleColor),
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = displayChar,
                                                                color = if (bubbleColor == Color.Transparent) PureWhite else bubbleColor,
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                // HW marker icon
                                                if (state?.homeworkPoints != null) {
                                                    Text(
                                                        text = "дз:${state.homeworkPoints}",
                                                        color = SoftGreen,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Spacing for right side alignment
                                    Spacer(modifier = Modifier.width(40.dp))
                                }
                                Divider(color = BorderGray)
                            }

                            // Match "+" column cells at the bottom row (add students row alignment)
                            item {
                                Row(modifier = Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(40.dp))
                                    groupLessons.forEach { _ ->
                                        Box(
                                            modifier = Modifier
                                                .width(76.dp)
                                                .fillMaxHeight()
                                                .border(0.5.dp, BorderGray)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(40.dp))
                                }
                                Divider(color = BorderGray)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS SECTION ---

    // 1. ADD STUDENT DIALOG
    if (showAddStudentDialog && currentGroupId != null) {
        var lastN by remember { mutableStateOf("") }
        var firstN by remember { mutableStateOf("") }
        var birthday by remember { mutableStateOf("2015-01-01") }
        var enrollD by remember { mutableStateOf("2026-05-29") }
        var contractN by remember { mutableStateOf("СТ-") }
        var paperD by remember { mutableStateOf<String?>(null) }
        var paperAmt by remember { mutableStateOf<String>("") }

        AlertDialog(
            onDismissRequest = { showAddStudentDialog = false },
            containerColor = DarkSurface,
            title = { Text("Добавить ученика", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArtTextField(value = lastN, onValueChange = { lastN = it }, label = "Фамилия")
                    Spacer(modifier = Modifier.height(8.dp))
                    ArtTextField(value = firstN, onValueChange = { firstN = it }, label = "Имя")
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Дата рождения ($birthday)", color = PureWhite, fontSize = 13.sp)
                    IconButton(onClick = { showDatePicker(context, birthday) { birthday = it } }) {
                        Icon(Icons.Default.CalendarMonth, tint = PrimaryYellow, contentDescription = null)
                    }

                    Text("Дата зачисления ($enrollD)", color = PureWhite, fontSize = 13.sp)
                    IconButton(onClick = { showDatePicker(context, enrollD) { enrollD = it } }) {
                        Icon(Icons.Default.CalendarMonth, tint = PrimaryYellow, contentDescription = null)
                    }

                    ArtTextField(value = contractN, onValueChange = { contractN = it }, label = "Номер договора")
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Оплата материалов (Бумага)", color = PrimaryYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Дата оплаты: ${paperD ?: "не оплачено"}", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        ArtOutlinedButton(text = "Выбрать дату") {
                            showDatePicker(context, paperD ?: "2026-05-29") { paperD = it }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ArtTextField(value = paperAmt, onValueChange = { paperAmt = it }, label = "Сумма оплаты (бумага) руб")
                }
            },
            confirmButton = {
                ArtButton(text = "Сохранить", onClick = {
                    if (lastN.isNotBlank() && firstN.isNotBlank()) {
                        viewModel.addStudent(
                            lastName = lastN.trim(),
                            firstName = firstN.trim(),
                            birthday = birthday,
                            enrollmentDate = enrollD,
                            contractNumber = contractN.trim(),
                            paperPayDate = paperD,
                            paperPayAmt = paperAmt.toDoubleOrNull(),
                            groupId = currentGroupId
                        )
                        showAddStudentDialog = false
                    } else {
                        Toast.makeText(context, "Заполните ФИО", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { showAddStudentDialog = false }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 2. ADD / EDIT GENERAL DAY LESSON DIALOG
    if (showAddLessonDialog && currentGroup != null) {
        var disc by remember { mutableStateOf(currentGroup.getDisciplinesList().firstOrNull() ?: "Рисунок") }
        var topicId by remember { mutableStateOf<Int?>(null) }
        var dateVal by remember { mutableStateOf("2026-05-29") }
        var hasDoubleCheck by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddLessonDialog = false },
            containerColor = DarkSurface,
            title = { Text("Создать учебный день", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Выберите дату учебного дня ($dateVal)", color = PureWhite, fontSize = 13.sp)
                    IconButton(onClick = {
                        showDatePicker(context, dateVal) {
                            dateVal = it
                            // Double lesson detector
                            val duplicates = rawLessons.filter { l -> l.groupId == currentGroup.id && l.date == it && !l.isNonSchoolDay }
                            if (duplicates.isNotEmpty()) {
                                hasDoubleCheck = true
                            }
                        }
                    }) {
                        Icon(Icons.Default.CalendarMonth, tint = PrimaryYellow, contentDescription = null)
                    }

                    if (hasDoubleCheck) {
                        Text("В этот день уже есть занятие! Добавить второе занятие в этот день по иной дисциплине?", color = SoftOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text("Дисциплина", color = MutedGray, fontSize = 12.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        currentGroup.getDisciplinesList().forEach { d ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .background(if (disc == d) PrimaryYellow else DarkCard, RoundedCornerShape(8.dp))
                                    .clickable { disc = d }
                                    .padding(8.dp)
                            ) {
                                Text(d, color = if (disc == d) DeepBlack else PureWhite, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Привязать к теме (необязательно)", color = MutedGray, fontSize = 12.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        topics.filter { it.discipline.equals(disc, ignoreCase = true) }.forEach { t ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .background(if (topicId == t.id) DarkMutedYellow else DarkCard, RoundedCornerShape(8.dp))
                                    .clickable { topicId = if (topicId == t.id) null else t.id }
                                    .padding(8.dp)
                            ) {
                                Text(t.name, color = PureWhite, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                ArtButton(text = "Создать", onClick = {
                    viewModel.addLesson(groupId = currentGroup.id, date = dateVal, discipline = disc, topicId = topicId)
                    showAddLessonDialog = false
                })
            },
            dismissButton = {
                TextButton(onClick = { showAddLessonDialog = false }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 3. EDIT LESSON HEADER DIALOG
    if (editingLesson != null && currentGroup != null) {
        val les = editingLesson!!
        var disc by remember { mutableStateOf(les.discipline) }
        var customTopic by remember { mutableStateOf(les.customTopicName ?: "") }
        var isSpecialHoliday by remember { mutableStateOf(les.isNonSchoolDay) }

        var showDoubleWarning by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingLesson = null },
            containerColor = DarkSurface,
            title = { Text("Настройка столбца дат: ${les.date}", color = PrimaryYellow) },
            text = {
                Column {
                    Text("Изменить Дисциплину", color = MutedGray, fontSize = 11.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        currentGroup.getDisciplinesList().forEach { d ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .background(if (disc == d) PrimaryYellow else DarkCard, RoundedCornerShape(8.dp))
                                    .clickable { disc = d }
                                    .padding(8.dp)
                            ) {
                                Text(d, color = if (disc == d) DeepBlack else PureWhite)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ArtTextField(value = customTopic, onValueChange = { customTopic = it }, label = "Тема урока за день")
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isSpecialHoliday,
                            onCheckedChange = { isSpecialHoliday = it },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryYellow)
                        )
                        Text("Неучебный день (исключить)", color = PureWhite, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Row {
                    // Safe Deletion Button
                    IconButton(
                        onClick = {
                            viewModel.deleteLessonConfirm(les, force = false) { completed, lost ->
                                if (completed) {
                                    editingLesson = null
                                } else {
                                    showDoubleWarning = true
                                }
                            }
                        },
                        modifier = Modifier.background(DarkRedBg, CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить занятие", tint = SoftRed)
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    ArtButton(text = "Сохранить", onClick = {
                        viewModel.modifyLesson(les.copy(discipline = disc, customTopicName = customTopic.ifBlank { null }, isNonSchoolDay = isSpecialHoliday))
                        editingLesson = null
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLesson = null }) {
                    Text("Закрыть", color = MutedGray)
                }
            }
        )

        // Custom safety double check dialog if lessons contain grades
        if (showDoubleWarning) {
            AlertDialog(
                onDismissRequest = { showDoubleWarning = false },
                containerColor = DarkSurface,
                title = { Text("Внимание!", color = SoftRed) },
                text = { Text("Это занятие содержит оценки. Удаление удалит все оценки успеваемости за эту дату безвозвратно. Продолжить?", color = PureWhite) },
                confirmButton = {
                    ArtButton(text = "Удалить принудительно", onClick = {
                        viewModel.deleteLessonConfirm(les, force = true) { _, _ -> }
                        showDoubleWarning = false
                        editingLesson = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = SoftRed, contentColor = DeepBlack))
                },
                dismissButton = {
                    TextButton(onClick = { showDoubleWarning = false }) {
                        Text("Отмена", color = MutedGray)
                    }
                }
            )
        }
    }

    // 4. GRADE CELL COMPRESSED SHEET DIALOG
    if (gradingCell != null) {
        val (student, lesson) = gradingCell!!
        val state = rawStates.find { it.studentId == student.id && it.lessonId == lesson.id }

        var isPresent by remember { mutableStateOf(state?.isPresent ?: true) }
        var isExcused by remember { mutableStateOf(state?.isExcusedAbsence ?: false) }
        var grade by remember { mutableStateOf<Int?>(state?.grade) }
        var homework by remember { mutableStateOf(state?.homeworkPoints?.toString() ?: "") }
        var comment by remember { mutableStateOf(state?.comment ?: "") }
        var note by remember { mutableStateOf(state?.note ?: "") }

        AlertDialog(
            onDismissRequest = { gradingCell = null },
            containerColor = DarkSurface,
            title = { Text("Оценка: ${student.lastName} · ${lesson.date}", color = PrimaryYellow, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // Attendance toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isPresent,
                            onCheckedChange = { isPresent = it },
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryYellow)
                        )
                        Text("Присутствовал", color = PureWhite, fontSize = 13.sp)
                    }

                    if (!isPresent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isExcused,
                                onCheckedChange = { isExcused = it },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryYellow)
                            )
                            Text("Уважительная причина пропуска", color = SoftOrange, fontSize = 13.sp)
                        }
                    }

                    if (isPresent) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Оценка (0..5 баллов)", color = MutedGray, fontSize = 11.sp)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            (0..5).forEach { score ->
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(36.dp)
                                        .background(if (grade == score) PrimaryYellow else DarkCard, CircleShape)
                                        .clickable { grade = if (grade == score) null else score },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(score.toString(), color = if (grade == score) DeepBlack else PureWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        ArtTextField(value = homework, onValueChange = { homework = it }, label = "Домашнее задание (0..101)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    ArtTextField(value = comment, onValueChange = { comment = it }, label = "Замечание (кратко)")
                    Spacer(modifier = Modifier.height(8.dp))
                    ArtTextField(value = note, onValueChange = { note = it }, label = "Заметка по занятию")
                }
            },
            confirmButton = {
                ArtButton(text = "Записать", onClick = {
                    val hwVal = homework.toIntOrNull()?.coerceIn(0, 101)
                    viewModel.saveStudentLessonState(
                        studentId = student.id,
                        lessonId = lesson.id,
                        grade = if (isPresent) grade else null,
                        isPresent = isPresent,
                        isExcused = if (!isPresent) isExcused else false,
                        homework = if (isPresent) hwVal else null,
                        comment = comment.ifBlank { null },
                        note = note.ifBlank { null }
                    )
                    gradingCell = null
                })
            },
            dismissButton = {
                TextButton(onClick = { gradingCell = null }) {
                    Text("Закрыть", color = MutedGray)
                }
            }
        )
    }

    // 5. EDIT/VIEW STUDENT PROFILE MODAL
    if (editingStudent != null && currentGroupId != null) {
        val st = editingStudent!!
        var lastN by remember { mutableStateOf(st.lastName) }
        var firstN by remember { mutableStateOf(st.firstName) }
        var birthday by remember { mutableStateOf(st.birthday) }
        var enrollD by remember { mutableStateOf(st.enrollmentDate) }
        var contractN by remember { mutableStateOf(st.contractNumber) }

        // Materials setup
        var paperD by remember { mutableStateOf(st.paperPaymentDate) }
        var paperAmt by remember { mutableStateOf(st.paperPaymentAmount?.toString() ?: "") }

        // Dynamic properties additions
        var tempKey by remember { mutableStateOf("") }
        var tempVal by remember { mutableStateOf("") }
        val dynamicProps = remember { mutableStateMapOf<String, String>().apply { putAll(st.getCustomFieldsMap()) } }

        // Education Payments logs section
        val studentPayments = rawPayments.filter { it.studentId == st.id }
        var payD by remember { mutableStateOf("2026-05-29") }
        var payAmt by remember { mutableStateOf("") }
        var payComm by remember { mutableStateOf("") }

        var showDeleteWarning by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingStudent = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Карточка ученика", color = PrimaryYellow, modifier = Modifier.weight(1f))
                    // Archive student toggle button
                    if (st.status == "active") {
                        IconButton(onClick = {
                            viewModel.archiveStudent(st, "Архивировано преподавателем")
                            editingStudent = null
                        }) {
                            Icon(Icons.Default.Archive, contentDescription = "В архив", tint = SoftOrange)
                        }
                    } else if (st.status == "archived") {
                        IconButton(onClick = {
                            viewModel.restoreStudent(st)
                            editingStudent = null
                        }) {
                            Icon(Icons.Default.Unarchive, contentDescription = "Разархивировать", tint = SoftGreen)
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Основная информация", color = PrimaryYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    ArtTextField(value = lastN, onValueChange = { lastN = it }, label = "Фамилия")
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtTextField(value = firstN, onValueChange = { firstN = it }, label = "Имя")
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Дата рождения: $birthday", color = PureWhite, fontSize = 13.sp)
                    IconButton(onClick = { showDatePicker(context, birthday) { birthday = it } }) {
                        Icon(Icons.Default.CalendarMonth, tint = PrimaryYellow, contentDescription = null)
                    }

                    Text("Дата зачисления: $enrollD", color = PureWhite, fontSize = 13.sp)
                    IconButton(onClick = { showDatePicker(context, enrollD) { enrollD = it } }) {
                        Icon(Icons.Default.CalendarMonth, tint = PrimaryYellow, contentDescription = null)
                    }

                    ArtTextField(value = contractN, onValueChange = { contractN = it }, label = "Номер договора")
                    Spacer(modifier = Modifier.height(12.dp))

                    // Transfer to different groups module
                    Text("Перевод в другую группу", color = PrimaryYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    groups.filter { it.id != currentGroupId }.forEach { gOther ->
                        ArtOutlinedButton(text = "Перевести в ${gOther.name}", modifier = Modifier.padding(vertical = 2.dp)) {
                            viewModel.transferStudent(st, gOther.id, viewModel.getCurrentDateString())
                            editingStudent = null
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic property generator
                    Text("Дополнительные свойства", color = PrimaryYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    dynamicProps.forEach { (k, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$k: $v", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { dynamicProps.remove(k) }) {
                                Icon(Icons.Default.Delete, tint = SoftRed, contentDescription = null)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtTextField(value = tempKey, onValueChange = { tempKey = it }, label = "Свойство", modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(4.dp))
                        ArtTextField(value = tempVal, onValueChange = { tempVal = it }, label = "Значение", modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            if (tempKey.isNotBlank()) {
                                dynamicProps[tempKey.trim()] = tempVal.trim()
                                tempKey = ""
                                tempVal = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, tint = PrimaryYellow, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Materials
                    Text("Оплата материалов (бумага)", color = PrimaryYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Дата: ${paperD ?: "не оплачено"}", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        ArtOutlinedButton(text = "Выбрать дату") {
                            showDatePicker(context, paperD ?: "2026-05-29") { paperD = it }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ArtTextField(value = paperAmt, onValueChange = { paperAmt = it }, label = "Сумма по материалам руб")
                    Spacer(modifier = Modifier.height(12.dp))

                    // History of education fees payment
                    Text("Оплата обучения (регулярная)", color = PrimaryYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    for (pay in studentPayments) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${pay.date}: ${pay.amount} руб (${pay.comment})", color = PureWhite, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.deletePayment(pay) }) {
                                Icon(Icons.Default.Close, tint = SoftRed, contentDescription = null)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Новая транзакция оплаты", color = MutedGray, fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Дата: $payD", color = PureWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showDatePicker(context, payD) { payD = it } }) {
                            Icon(Icons.Default.CalendarMonth, tint = PrimaryYellow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    ArtTextField(value = payAmt, onValueChange = { payAmt = it }, label = "Сумма в рублях")
                    Spacer(modifier = Modifier.height(4.dp))
                    ArtTextField(value = payComm, onValueChange = { payComm = it }, label = "Комментарий (напр. май)")
                    Spacer(modifier = Modifier.height(4.dp))
                    ArtButton(text = "Зачислить оплату") {
                        val numAmt = payAmt.toDoubleOrNull()
                        if (numAmt != null) {
                            viewModel.addPayment(st.id, payD, numAmt, payComm)
                            payAmt = ""
                            payComm = ""
                        } else {
                            Toast.makeText(context, "Укажите верную сумму", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Historical aggregated notes & comments
                    Text("Замечания & Заметки", color = PrimaryYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val studentStates = rawStates.filter { it.studentId == st.id }
                    studentStates.forEach { item ->
                        if (!item.comment.isNullOrBlank() || !item.note.isNullOrBlank()) {
                            val les = rawLessons.find { it.id == item.lessonId }
                            val dateStr = les?.date ?: "Архив"
                            val bulletText = StringBuilder()
                            if (!item.comment.isNullOrBlank()) bulletText.append("Замечание: ${item.comment} ")
                            if (!item.note.isNullOrBlank()) bulletText.append("Заметка: ${item.note}")
                            Text("- $dateStr: $bulletText", color = SoftOrange, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Safe delete action
                    IconButton(
                        onClick = { showDeleteWarning = true },
                        modifier = Modifier.background(DarkRedBg, CircleShape)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Полное удаление", tint = SoftRed)
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    ArtButton(text = "Записать изменения", onClick = {
                        viewModel.updateStudent(
                            student = st.copy(
                                lastName = lastN.trim(),
                                firstName = firstN.trim(),
                                birthday = birthday,
                                enrollmentDate = enrollD,
                                contractNumber = contractN.trim(),
                                paperPaymentDate = paperD,
                                paperPaymentAmount = paperAmt.toDoubleOrNull()
                            ),
                            customFieldsMap = dynamicProps.toMap()
                        )
                        editingStudent = null
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStudent = null }) {
                    Text("Закрыть", color = MutedGray)
                }
            }
        )

        // Confirm Delete warning dialog
        if (showDeleteWarning) {
            AlertDialog(
                onDismissRequest = { showDeleteWarning = false },
                containerColor = DarkSurface,
                title = { Text("Удалить ученика?", color = SoftRed) },
                text = { Text("Ученик может быть удален только если у него нет сохраненных оценок за занятия в журнале. В противном случае доступно только архивирование.", color = PureWhite) },
                confirmButton = {
                    ArtButton(
                        text = "Подтвердить удаление",
                        onClick = {
                            viewModel.deleteStudentConfirm(st) { completed ->
                                if (completed) {
                                    showDeleteWarning = false
                                    editingStudent = null
                                } else {
                                    Toast.makeText(context, "Нельзя удалить ученика с оценками! Возможна только архивация.", Toast.LENGTH_LONG).show()
                                    showDeleteWarning = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SoftRed, contentColor = DeepBlack)
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteWarning = false }) {
                        Text("Отмена", color = MutedGray)
                    }
                }
            )
        }
    }

    // 6. CREATE GROUP MODAL WITH QUICK DAYS SCHEDULE
    if (showScheduleDialog) {
        var gName by remember { mutableStateOf("") }
        val disciplinesList = remember { mutableStateListOf("Рисунок", "Живопись", "Композиция") }

        // Scheduled week days and selection mapping (1=Monday ... 7=Sunday)
        val selectedDaysSchedule = remember { mutableStateMapOf<Int, String>() }

        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            containerColor = DarkSurface,
            title = { Text("Добавить группу и настроить расписание", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArtTextField(value = gName, onValueChange = { gName = it }, label = "Название группы")
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Список Дисциплин (редактируемый):", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                label = "Дисциплина ${idx + 1}",
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
                                    Toast.makeText(context, "Должна остаться хотя бы одна дисциплина", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить дисциплину", tint = SoftRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtOutlinedButton(text = "Добавить дисциплину", onClick = {
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
                                        val firstNonNull = disciplinesList.firstOrNull { it.isNotBlank() } ?: "Рисунок"
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
                ArtButton(text = "Создать", onClick = {
                    if (gName.isNotBlank()) {
                        val dList = disciplinesList.map { it.trim() }.filter { it.isNotEmpty() }
                        viewModel.addGroup(gName.trim(), dList, selectedDaysSchedule.toMap())
                        showScheduleDialog = false
                    } else {
                        Toast.makeText(context, "Имя группы пусто", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text("Отмена", color = MutedGray)
                }
            }
        )
    }

    // 7. EDIT GROUP MODAL WITH QUICK DAYS SCHEDULE
    if (editingGroupForJournal != null) {
        val groupToEdit = editingGroupForJournal!!
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
            onDismissRequest = { editingGroupForJournal = null },
            containerColor = DarkSurface,
            title = { Text("Редактировать группу: ${groupToEdit.name}", color = PrimaryYellow) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ArtTextField(value = gName, onValueChange = { gName = it }, label = "Название группы")
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Список Дисциплин (редактируемый):", color = PrimaryYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                label = "Дисциплина ${idx + 1}",
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
                                    Toast.makeText(context, "Должна остаться хотя бы одна дисциплина", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить дисциплину", tint = SoftRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ArtOutlinedButton(text = "Добавить дисциплину", onClick = {
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
                                        val firstNonNull = disciplinesList.firstOrNull { it.isNotBlank() } ?: "Рисунок"
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
                        editingGroupForJournal = null
                    } else {
                        Toast.makeText(context, "Имя группы пусто", Toast.LENGTH_SHORT).show()
                    }
                })
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.deleteGroup(groupToEdit)
                        editingGroupForJournal = null
                    }) {
                        Text("Удалить", color = SoftRed)
                    }
                    TextButton(onClick = { editingGroupForJournal = null }) {
                        Text("Отмена", color = MutedGray)
                    }
                }
            }
        )
    }
}
