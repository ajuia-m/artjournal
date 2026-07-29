package com.example.viewmodel

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ArtJournalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ArtJournalDatabase.getDatabase(application)
    val repository = ArtJournalRepository(db.artJournalDao())

    // --- Active Selected Tabs/Filters UI State ---
    private val _currentTab = MutableStateFlow("journal") // "journal" | "themes" | "schedule" | "tracker" | "settings"
    val currentTab = _currentTab.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Int?>(null)
    val selectedGroupId = _selectedGroupId.asStateFlow()

    private val _selectedDisciplineFilter = MutableStateFlow<String?>(null) // Show only selected discipline columns
    val selectedDisciplineFilter = _selectedDisciplineFilter.asStateFlow()

    private val _showArchivedStudents = MutableStateFlow(false)
    val showArchivedStudents = _showArchivedStudents.asStateFlow()

    // Date navigation quick-jumps ("сегодня", "эта неделя", month selection e.g., "YYYY-MM")
    private val _dateFilterType = MutableStateFlow("all") // "all" | "today" | "week" | "month"
    val dateFilterType = _dateFilterType.asStateFlow()

    private val _selectedMonthFilter = MutableStateFlow("") // "YYYY-MM"
    val selectedMonthFilter = _selectedMonthFilter.asStateFlow()

    // --- DB Flow Hooks ---
    val academicYears = repository.academicYears.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val groups = repository.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val students = repository.students.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val payments = repository.payments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val quarters = repository.quarters.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val lessons = repository.lessons.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val studentLessonStates = repository.studentLessonStates.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val topics = repository.topics.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val studentTopicProgress = repository.studentTopicProgress.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val auditLogs = repository.auditLogs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Keep active Academic Year reference
    val activeYear = academicYears.map { list ->
        list.find { it.isActive } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // Automatically default selected group or active academic year
        viewModelScope.launch {
            combine(groups, activeYear) { grps, yr ->
                if (_selectedGroupId.value == null && grps.isNotEmpty()) {
                    val matching = if (yr != null) grps.find { it.academicYearId == yr.id } else null
                    _selectedGroupId.value = matching?.id ?: grps.first().id
                }
            }.collect()
        }
        // Audit log cleaning (rentention 1 month)
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            repository.deleteAuditLogsOlderThan(cutoff)
        }
    }

    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    fun selectGroup(groupId: Int) {
        _selectedGroupId.value = groupId
    }

    fun setDisciplineFilter(discipline: String?) {
        _selectedDisciplineFilter.value = discipline
    }

    fun toggleArchivedStudents(show: Boolean) {
        _showArchivedStudents.value = show
    }

    fun setDateFilter(filter: String, month: String = "") {
        _dateFilterType.value = filter
        _selectedMonthFilter.value = month
    }

    // --- Helper date parsing ---
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun getCurrentDateString(): String {
        return dateFormat.format(Date())
    }

    fun isDateToday(dateStr: String): Boolean {
        return dateStr == getCurrentDateString()
    }

    fun isDateThisWeek(dateStr: String): Boolean {
        try {
            val date = dateFormat.parse(dateStr) ?: return false
            val cal = Calendar.getInstance()
            val currentWeek = cal.get(Calendar.WEEK_OF_YEAR)
            val currentYear = cal.get(Calendar.YEAR)
            cal.time = date
            return cal.get(Calendar.WEEK_OF_YEAR) == currentWeek && cal.get(Calendar.YEAR) == currentYear
        } catch (e: Exception) {
            return false
        }
    }

    // --- Logging & Reverting Actions ---
    fun logAction(action: String, details: String, revertData: String? = null) {
        viewModelScope.launch {
            repository.insertAuditLog(
                AuditLog(
                    action = action,
                    details = details,
                    revertData = revertData
                )
            )
        }
    }

    fun triggerUndo() {
        viewModelScope.launch {
            val logs = auditLogs.value
            if (logs.isEmpty()) {
                Toast.makeText(getApplication(), "Нет действий для отмены", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val latest = logs.first()
            val data = latest.revertData
            if (data != null && data.startsWith("REV:")) {
                try {
                    val parts = data.removePrefix("REV:").split("||")
                    val cmd = parts[0]
                    when (cmd) {
                        "STUDENT_RESTORE" -> {
                            val id = parts[1].toInt()
                            val originalStatus = parts[2]
                            val student = repository.getStudentById(id)
                            if (student != null) {
                                repository.updateStudent(student.copy(status = originalStatus))
                            }
                        }
                        "STUDENT_STATE_CHANGE" -> {
                            val id = parts[1].toInt()
                            val origGrade = parts[2].toIntOrNull()
                            val origPresent = parts[3].toBoolean()
                            val origExcused = parts[4].toBoolean()
                            val studentId = parts[5].toInt()
                            val lessonId = parts[6].toInt()
                            val origHw = parts[7].toIntOrNull()
                            val origComment = if (parts[8] == "null") null else parts[8]
                            val origNote = if (parts[9] == "null") null else parts[9]

                            repository.insertStudentLessonState(
                                StudentLessonState(
                                    id = id,
                                    studentId = studentId,
                                    lessonId = lessonId,
                                    grade = origGrade,
                                    isPresent = origPresent,
                                    isExcusedAbsence = origExcused,
                                    homeworkPoints = origHw,
                                    comment = origComment,
                                    note = origNote
                                )
                            )
                        }
                        "LESSON_DELETE" -> {
                            // Recreate deleted lesson
                            val groupId = parts[1].toInt()
                            val date = parts[2]
                            val disc = parts[3]
                            val topId = parts[4].toIntOrNull()
                            val customTop = if (parts[5] == "null") null else parts[5]
                            // Just save a new one or warn
                            repository.insertLesson(
                                Lesson(
                                    groupId = groupId,
                                    date = date,
                                    discipline = disc,
                                    topicId = topId,
                                    customTopicName = customTop
                                )
                            )
                        }
                        else -> {
                            Toast.makeText(getApplication(), "Сложные действия нельзя отменить автоматически", Toast.LENGTH_SHORT).show()
                        }
                    }
                    repository.deleteAuditLogById(latest.id)
                    Toast.makeText(getApplication(), "Действие отменено: ${latest.action}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(getApplication(), "Ошибка отмены", Toast.LENGTH_SHORT).show()
                }
            } else {
                repository.deleteAuditLogById(latest.id)
                Toast.makeText(getApplication(), "Действие удалено из истории", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Operations: Group ---
    fun addGroup(name: String, disciplinesList: List<String>, daysSchedule: Map<Int, String>) {
        viewModelScope.launch {
            val activeYrId = activeYear.value?.id
            if (activeYrId == null) {
                Toast.makeText(getApplication(), "Создайте сначала учебный год", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val discString = disciplinesList.joinToString(",")
            val schedString = daysSchedule.entries.joinToString(",") { "${it.key}:${it.value}" }
            val newId = repository.insertGroup(
                Group(
                    name = name,
                    academicYearId = activeYrId,
                    disciplines = discString,
                    schedule = schedString
                )
            ).toInt()
            logAction("Создана группа", "Группа \"$name\" добавлена в расписание")
            _selectedGroupId.value = newId
        }
    }

    fun updateGroup(id: Int, name: String, disciplinesList: List<String>, daysSchedule: Map<Int, String>) {
        viewModelScope.launch {
            val targetGroup = groups.value.find { it.id == id } ?: return@launch
            val discString = disciplinesList.joinToString(",")
            val schedString = daysSchedule.entries.joinToString(",") { "${it.key}:${it.value}" }
            repository.updateGroup(
                targetGroup.copy(
                    name = name,
                    disciplines = discString,
                    schedule = schedString
                )
            )
            logAction("Изменена группа", "Группа \"$name\" обновлена")
        }
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            repository.deleteGroup(group)
            if (_selectedGroupId.value == group.id) {
                _selectedGroupId.value = null
            }
            logAction("Удалена группа", "Группа \"${group.name}\" удалена")
        }
    }

    // --- Operations: Students ---
    fun addStudent(
        lastName: String,
        firstName: String,
        birthday: String,
        enrollmentDate: String,
        contractNumber: String,
        paperPayDate: String?,
        paperPayAmt: Double?,
        groupId: Int,
        customFieldsMap: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val customFieldsStr = customFieldsMap.entries.joinToString("||") { "${it.key}::${it.value}" }
            repository.insertStudent(
                Student(
                    lastName = lastName,
                    firstName = firstName,
                    birthday = birthday,
                    enrollmentDate = enrollmentDate,
                    contractNumber = contractNumber,
                    paperPaymentDate = paperPayDate,
                    paperPaymentAmount = paperPayAmt,
                    groupId = groupId,
                    customFields = customFieldsStr,
                    status = "active"
                )
            )
            logAction("Добавлен ученик", "Добавлен ученик $lastName $firstName в группу")
        }
    }

    fun updateStudent(student: Student, customFieldsMap: Map<String, String>? = null) {
        viewModelScope.launch {
            val toSave = if (customFieldsMap != null) {
                val str = customFieldsMap.entries.joinToString("||") { "${it.key}::${it.value}" }
                student.copy(customFields = str)
            } else student
            repository.updateStudent(toSave)
            logAction("Изменен ученик", "Данные ученика ${student.fullName} обновлены")
        }
    }

    fun archiveStudent(student: Student, reason: String?) {
        viewModelScope.launch {
            repository.updateStudent(
                student.copy(
                    status = "archived",
                    archiveDate = getCurrentDateString(),
                    archiveReason = reason
                )
            )
            logAction(
                "Ученик архивирован",
                "Ученик ${student.fullName} перенесен в архив",
                revertData = "REV:STUDENT_RESTORE||${student.id}||${student.status}"
            )
        }
    }

    fun restoreStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(
                student.copy(
                    status = "active",
                    archiveDate = null,
                    archiveReason = null
                )
            )
            logAction(
                "Ученик восстановлен",
                "Ученик ${student.fullName} возвращен в группу",
                revertData = "REV:STUDENT_RESTORE||${student.id}||archived"
            )
        }
    }

    fun deleteStudentConfirm(student: Student, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Check if student has absolutely any grades
            val states = studentLessonStates.value.filter { it.studentId == student.id && (it.grade != null || !it.isPresent || it.homeworkPoints != null) }
            if (states.isNotEmpty()) {
                onDone(false) // cannot delete, only archive!
            } else {
                repository.updateStudent(student.copy(status = "deleted"))
                logAction(
                    "Ученик удален",
                    "Ученик ${student.fullName} удален из базы",
                    revertData = "REV:STUDENT_RESTORE||${student.id}||${student.status}"
                )
                onDone(true)
            }
        }
    }

    // Transfers student to new group
    fun transferStudent(student: Student, newGroupId: Int, date: String) {
        viewModelScope.launch {
            repository.updateStudent(
                student.copy(
                    groupId = newGroupId,
                    enrollmentDate = date
                )
            )
            logAction("Перевод ученика", "Ученик ${student.fullName} переведен в другую группу с $date")
        }
    }

    // --- Payments ---
    fun addPayment(studentId: Int, date: String, amount: Double, comment: String) {
        viewModelScope.launch {
            repository.insertPayment(
                Payment(studentId = studentId, date = date, amount = amount, comment = comment)
            )
            val stName = students.value.find { it.id == studentId }?.fullName ?: "ID $studentId"
            logAction("Добавлена оплата", "Зарегистрирована оплата $amount руб для $stName")
        }
    }

    fun deletePayment(payment: Payment) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            logAction("Удалена оплата", "Удалена транзакция оплаты от ${payment.date}")
        }
    }

    // --- Lessons & Journal Core ---
    fun addLesson(groupId: Int, date: String, discipline: String, topicId: Int? = null, customTopic: String? = null) {
        viewModelScope.launch {
            repository.insertLesson(
                Lesson(groupId = groupId, date = date, discipline = discipline, topicId = topicId, customTopicName = customTopic)
            )
            logAction("Создано занятие", "Тема занятия: ${customTopic ?: "не указана"} ($discipline, $date)")
        }
    }

    fun modifyLesson(lesson: Lesson) {
        viewModelScope.launch {
            repository.updateLesson(lesson)
            logAction("Изменено занятие", "Параметры занятия изменены ($lesson.date, $lesson.discipline)")
        }
    }

    fun deleteLessonConfirm(lesson: Lesson, force: Boolean, onDone: (Boolean, Int) -> Unit) {
        viewModelScope.launch {
            val relatedStates = studentLessonStates.value.filter { it.lessonId == lesson.id && (it.grade != null || !it.isPresent) }
            if (relatedStates.isNotEmpty() && !force) {
                onDone(false, relatedStates.size) // Has grades, requires explicit confirm
            } else {
                val totalLost = relatedStates.size
                repository.deleteLesson(lesson)
                logAction(
                    "Удалена дата занятия",
                    "Дата ${lesson.date} ($lesson.discipline) удалена из журнала ($totalLost оценок потеряно)",
                    revertData = "REV:LESSON_DELETE||${lesson.groupId}||${lesson.date}||${lesson.discipline}||${lesson.topicId}||${lesson.customTopicName}"
                )
                onDone(true, totalLost)
            }
        }
    }

    fun getLessonQuarter(lessonDate: String): Quarter? {
        val qrs = quarters.value
        if (qrs.isEmpty()) return null
        return qrs.find { q ->
            lessonDate >= q.startDate && lessonDate <= q.endDate
        }
    }

    fun saveStudentLessonState(
        studentId: Int,
        lessonId: Int,
        grade: Int?,
        isPresent: Boolean,
        isExcused: Boolean = false,
        homework: Int? = null,
        comment: String? = null,
        note: String? = null
    ) {
        viewModelScope.launch {
            val existing = studentLessonStates.value.find { it.studentId == studentId && it.lessonId == lessonId }
            val newState = existing?.copy(
                grade = grade,
                isPresent = isPresent,
                isExcusedAbsence = isExcused,
                homeworkPoints = homework,
                comment = comment,
                note = note
            ) ?: StudentLessonState(
                studentId = studentId,
                lessonId = lessonId,
                grade = grade,
                isPresent = isPresent,
                isExcusedAbsence = isExcused,
                homeworkPoints = homework,
                comment = comment,
                note = note
            )
            val st = students.value.find { it.id == studentId }
            val les = lessons.value.find { it.id == lessonId }

            val origData = if (existing != null) {
                "REV:STUDENT_STATE_CHANGE||${existing.id}||${existing.grade}||${existing.isPresent}||${existing.isExcusedAbsence}||${existing.studentId}||${existing.lessonId}||${existing.homeworkPoints}||${existing.comment}||${existing.note}"
            } else null

            repository.insertStudentLessonState(newState)

            val detailsStr = "Ученик ${st?.fullName ?: "ID $studentId"}: оценка=${grade ?: "нет"} Прис=${if (isPresent) "+" else "-"}"
            logAction("Изменение успеваемости", detailsStr, revertData = origData)
        }
    }

    // Marks all active students present for a lesson Column
    fun markAllPresent(lessonId: Int) {
        viewModelScope.launch {
            val les = lessons.value.find { it.id == lessonId } ?: return@launch
            val relatedStudents = students.value.filter { it.groupId == les.groupId && it.status == "active" }
            for (st in relatedStudents) {
                val existing = studentLessonStates.value.find { it.studentId == st.id && it.lessonId == lessonId }
                val update = existing?.copy(isPresent = true) ?: StudentLessonState(studentId = st.id, lessonId = lessonId, isPresent = true)
                repository.insertStudentLessonState(update)
            }
            logAction("Все присутствовали", "Вся колонка занятия за ${les.date} (${les.discipline}) отмечена присутствующими")
        }
    }

    // --- Topics & Criteria ---
    fun addTopic(name: String, discipline: String, criteriaList: List<Pair<String, Int>>, boundGroups: List<Int>, boundQuarters: List<Int>) {
        viewModelScope.launch {
            val criteriaStr = criteriaList.joinToString(",") { "${it.first}:${it.second}" }
            val groupIdsStr = boundGroups.joinToString(",")
            val qIdsStr = boundQuarters.joinToString(",")
            repository.insertTopic(
                Topic(
                    name = name,
                    discipline = discipline,
                    criteria = criteriaStr,
                    groupIds = groupIdsStr,
                    quarterIds = qIdsStr
                )
            )
            logAction("Создана тема зачета", "Добавлена тема \"$name\" по дисциплине $discipline")
        }
    }

    fun modifyTopic(topic: Topic) {
        viewModelScope.launch {
            repository.updateTopic(topic)
            logAction("Изменена тема", "Параметры темы \"${topic.name}\" обновлены")
        }
    }

    fun deleteTopic(topic: Topic) {
        viewModelScope.launch {
            repository.deleteTopic(topic)
            logAction("Удалена тема", "Тема \"${topic.name}\" удалена")
        }
    }

    fun duplicateTopic(topic: Topic) {
        viewModelScope.launch {
            // Does not copy lessons/dates as per instructions
            repository.insertTopic(
                Topic(
                    name = "${topic.name} (Копия)",
                    discipline = topic.discipline,
                    criteria = topic.criteria,
                    groupIds = topic.groupIds,
                    quarterIds = topic.quarterIds
                )
            )
            logAction("Копирована тема", "Тема \"${topic.name}\" продублирована без привязки к календарным датам")
        }
    }

    fun saveStudentTopicProgress(studentId: Int, topicId: Int, stage: Int, criteriaScores: Map<String, Int>) {
        viewModelScope.launch {
            val existing = studentTopicProgress.value.find { it.studentId == studentId && it.topicId == topicId }
            val capStage = stage.coerceIn(0, 100)
            val criteriaGradesStr = criteriaScores.entries.joinToString(",") { "${it.key}:${it.value}" }
            val newProgress = existing?.copy(stage = capStage, criteriaGrades = criteriaGradesStr)
                ?: StudentTopicProgress(studentId = studentId, topicId = topicId, stage = capStage, criteriaGrades = criteriaGradesStr)

            repository.insertStudentTopicProgress(newProgress)
            logAction("Прогресс по теме", "Обновлен прогресс/критерии ученика для темы ID $topicId")
        }
    }

    // --- Schedule Setup ---
    fun saveQuarter(quarter: Quarter) {
        viewModelScope.launch {
            repository.insertQuarter(quarter)
            logAction("Настройка четвертей", "Задан период \"${quarter.name}\" (${quarter.startDate} - ${quarter.endDate})")
        }
    }

    fun updateQuarter(quarter: Quarter) {
        viewModelScope.launch {
            repository.updateQuarter(quarter)
            logAction("Обновление четверти", "Изменен период \"${quarter.name}\" (${quarter.startDate} - ${quarter.endDate})")
        }
    }

    fun saveAcademicYear(name: String, active: Boolean = true, copyPreviousYearData: Boolean = false) {
        viewModelScope.launch {
            val previousActiveId = activeYear.value?.id
            // Uncheck other active years
            if (active) {
                val yrs = academicYears.value
                for (yr in yrs) {
                    if (yr.isActive) {
                        repository.updateAcademicYear(yr.copy(isActive = false))
                    }
                }
            }
            val newYearId = repository.insertAcademicYear(AcademicYear(name = name, isActive = active)).toInt()
            logAction("Новый учебный год", "Создан учебный год \"$name\"")

            if (copyPreviousYearData && previousActiveId != null) {
                val oldGroups = groups.value.filter { it.academicYearId == previousActiveId }
                for (grp in oldGroups) {
                    repository.insertGroup(
                        Group(
                            name = grp.name,
                            academicYearId = newYearId,
                            disciplines = grp.disciplines,
                            schedule = grp.schedule
                        )
                    )
                }
                logAction("Копирование года", "Скопирована информация о группах (${oldGroups.size} шт.) из предыдущего года")
            }
        }
    }

    fun selectAcademicYear(yearId: Int) {
        viewModelScope.launch {
            val yrs = academicYears.value
            for (yr in yrs) {
                val expectActive = (yr.id == yearId)
                if (yr.isActive != expectActive) {
                    repository.updateAcademicYear(yr.copy(isActive = expectActive))
                }
            }
            logAction("Переключение года", "Учебный год изменен")
            _selectedGroupId.value = null
        }
    }

    fun deleteAcademicYear(year: AcademicYear) {
        viewModelScope.launch {
            repository.deleteAcademicYear(year)
            logAction("Удаление года", "Удален учебный год \"${year.name}\"")
            if (activeYear.value?.id == year.id) {
                _selectedGroupId.value = null
            }
        }
    }

    // Clones groups into a new academic year
    fun cloneYearConfirm(sourceYearId: Int, destYearName: String, includeStudents: Boolean, studentSelections: Map<Int, Boolean>) {
        viewModelScope.launch {
            // 1. Create destinations year
            val targetYearId = repository.insertAcademicYear(AcademicYear(name = destYearName, isActive = true)).toInt()

            // 2. Query groups from source year
            val oldGroups = groups.value.filter { it.academicYearId == sourceYearId }
            for (grp in oldGroups) {
                val newGroupId = repository.insertGroup(
                    Group(
                        name = grp.name,
                        academicYearId = targetYearId,
                        disciplines = grp.disciplines,
                        schedule = grp.schedule
                    )
                ).toInt()

                // Copy students if checked
                if (includeStudents) {
                    val grpStudents = students.value.filter { it.groupId == grp.id && it.status == "active" }
                    for (st in grpStudents) {
                        val toKeep = studentSelections[st.id] ?: true
                        if (toKeep) {
                            repository.insertStudent(
                                st.copy(
                                    id = 0,
                                    groupId = newGroupId,
                                    enrollmentDate = getCurrentDateString(),
                                    paperPaymentDate = null,
                                    paperPaymentAmount = null
                                )
                            )
                        } else {
                            // Send unselected to archive
                            repository.updateStudent(
                                st.copy(
                                    status = "archived",
                                    archiveDate = getCurrentDateString(),
                                    archiveReason = "Выпускник при переносе группы на новый учебный год"
                                )
                            )
                        }
                    }
                }
            }

            logAction("Перенос учебного года", "Создан учебный год \"$destYearName\". Структуры групп и выбранные ученики перенесены.")
        }
    }


    // --- ADVANCED CALCULATIONS & LOGIC CHECKS ---

    // 1. Checks if student has 2 consecutive absences in group's calendar
    fun isStudentConsecutiveAbsences(studentId: Int, groupId: Int): Boolean {
        val groupLessons = lessons.value.filter { it.groupId == groupId && !it.isNonSchoolDay }.sortedBy { it.date }
        if (groupLessons.size < 2) return false
        val states = studentLessonStates.value

        var consecutive = 0
        // Parse states backwards in time in reverse chronological order
        for (les in groupLessons.reversed()) {
            val state = states.find { it.studentId == studentId && it.lessonId == les.id }
            if (state != null && !state.isPresent) {
                consecutive++
                if (consecutive >= 2) return true
            } else if (state != null && state.isPresent) {
                // He was present, break loop
                break
            }
        }
        return false
    }

    // 2. Checks if student has NOT paid education fees for over a month (30 days)
    // Educational pay is regular! Check difference between last payment date and today.
    fun isStudentUnpaidOverMonth(studentId: Int): Boolean {
        val studentPayments = payments.value.filter { it.studentId == studentId }.sortedBy { it.date }
        val st = students.value.find { it.id == studentId } ?: return false

        val baseDateStr = if (studentPayments.isNotEmpty()) {
            studentPayments.last().date
        } else {
            st.enrollmentDate.ifBlank { "2026-05-01" } // default date fallback
        }

        try {
            val date = dateFormat.parse(baseDateStr) ?: return false
            val diffMs = Date().time - date.time
            val diffDays = diffMs / (1000 * 60 * 60 * 24)
            return diffDays > 30
        } catch (e: Exception) {
            return false
        }
    }

    // Tracker Scoring: Sum grades in period + Topic criteria points
    fun calculateTrackerPoints(studentId: Int, discipline: String, start: String, end: String): Double {
        // A. Sum of daily lesson grades (0..5)
        val groupLessons = lessons.value.filter {
            it.date in start..end && it.discipline.equals(discipline, ignoreCase = true) && !it.isNonSchoolDay
        }
        val lessonIds = groupLessons.map { it.id }
        val dailyGradesSum = studentLessonStates.value.filter {
            it.studentId == studentId && it.lessonId in lessonIds && it.grade != null
        }.sumOf { it.grade ?: 0 }

        // B. Sum of topic criteria points
        val tps = topics.value.filter { it.discipline.equals(discipline, ignoreCase = true) }
        val topicIds = tps.map { it.id }
        val prog = studentTopicProgress.value.filter { it.studentId == studentId && it.topicId in topicIds }
        var criteriaSum = 0
        for (p in prog) {
            criteriaSum += p.getGradesMap().values.sum()
        }

        return dailyGradesSum.toDouble() + criteriaSum
    }

    // Aggregate homework score for meta "Домашняя работа" calculation
    fun calculateHomeworkPoints(studentId: Int, start: String, end: String): Double {
        val groupLessons = lessons.value.filter { it.date in start..end }
        val lessonIds = groupLessons.map { it.id }
        return studentLessonStates.value.filter {
            it.studentId == studentId && it.lessonId in lessonIds && it.homeworkPoints != null
        }.sumOf { it.homeworkPoints ?: 0 }.toDouble()
    }

    // Attendance stats calculator
    // Returns Pair(lessons attended, lessons total)
    fun calculateAttendance(studentId: Int, start: String, end: String): Pair<Int, Int> {
        val groupLessons = lessons.value.filter { it.date in start..end && !it.isNonSchoolDay }
        val lessonIds = groupLessons.map { it.id }
        val states = studentLessonStates.value.filter { it.studentId == studentId && it.lessonId in lessonIds }
        val total = groupLessons.size
        val missed = states.count { !it.isPresent }
        val attended = total - missed
        return Pair(attended, total)
    }

    // --- CSV IMPORTER / EXPORTER ---
    fun exportToCSVString(): String {
        val s = StringBuilder()
        s.append("TYPE,ID,DATA1,DATA2,DATA3,DATA4,DATA5,DATA6,DATA7,DATA8\n")

        // 1. Academic Years
        for (yr in academicYears.value) {
            s.append("YEAR,${yr.id},${yr.name},${yr.holidays},${yr.isActive}\n")
        }
        // 2. Groups
        for (g in groups.value) {
            s.append("GROUP,${g.id},${g.name},${g.academicYearId},${g.disciplines},${g.schedule}\n")
        }
        // 3. Students
        for (st in students.value) {
            s.append("STUDENT,${st.id},${st.lastName},${st.firstName},${st.birthday},${st.enrollmentDate},${st.contractNumber},${st.status},${st.groupId}\n")
        }
        // 4. Payments
        for (p in payments.value) {
            s.append("PAYMENT,${p.id},${p.studentId},${p.date},${p.amount},${p.comment}\n")
        }
        // 5. Lessons
        for (l in lessons.value) {
            s.append("LESSON,${l.id},${l.groupId},${l.date},${l.discipline},${l.topicId},${l.customTopicName},${l.isNonSchoolDay}\n")
        }
        // 6. LessonStates
        for (ls in studentLessonStates.value) {
            s.append("STATE,${ls.id},${ls.studentId},${ls.lessonId},${ls.grade},${ls.isPresent},${ls.isExcusedAbsence},${ls.homeworkPoints},${ls.comment}\n")
        }
        return s.toString()
    }

    fun importFromCSVString(csv: String) {
        viewModelScope.launch {
            try {
                val lines = csv.split("\n")
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("TYPE")) continue
                    val parts = line.split(",")
                    if (parts.size < 3) continue
                    val type = parts[0]
                    when (type) {
                        "YEAR" -> {
                            val id = parts[1].toInt()
                            repository.insertAcademicYear(
                                AcademicYear(id = id, name = parts[2], holidays = parts[3], isActive = parts[4].toBoolean())
                            )
                        }
                        "GROUP" -> {
                            val id = parts[1].toInt()
                            repository.insertGroup(
                                Group(id = id, name = parts[2], academicYearId = parts[3].toInt(), disciplines = parts[4], schedule = parts.getOrNull(5) ?: "")
                            )
                        }
                        "STUDENT" -> {
                            val id = parts[1].toInt()
                            repository.insertStudent(
                                Student(
                                    id = id,
                                    lastName = parts[2],
                                    firstName = parts[3],
                                    birthday = parts[4],
                                    enrollmentDate = parts[5],
                                    contractNumber = parts[6],
                                    status = parts[7],
                                    groupId = parts[8].toInt()
                                )
                            )
                        }
                    }
                }
                logAction("Импорт CSV", "База данных успешно обновлена из файла CSV")
                Toast.makeText(getApplication(), "Импорт завершен!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Ошибка импорта CSV", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- PDF JOURNAL GENERATION (CANVAS WRITING) ---
    // Habitual visual table containing student names as rows, dates as columns and grades
    fun exportGroupJournalToPDF(groupId: Int) {
        viewModelScope.launch {
            try {
                val grp = groups.value.find { it.id == groupId } ?: return@launch
                val grpStudents = students.value.filter { it.groupId == groupId && it.status == "active" }.sortedBy { it.lastName }
                val grpLessons = lessons.value.filter { it.groupId == groupId && !it.isNonSchoolDay }.sortedBy { it.date }
                val states = studentLessonStates.value

                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(1000, 800, 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = AndroidColor.BLACK
                    textSize = 24f
                    isFakeBoldText = true
                }
                val textPaint = Paint().apply {
                    color = AndroidColor.DKGRAY
                    textSize = 12f
                }
                val cellPaint = Paint().apply {
                    color = AndroidColor.BLACK
                    textSize = 11f
                }
                val outlinePaint = Paint().apply {
                    color = AndroidColor.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                }

                // Header
                canvas.drawText("Журнал группы: ${grp.name}", 40f, 50f, titlePaint)
                canvas.drawText("Сгенерировано: ${getCurrentDateString()} | Художественная школа", 40f, 75f, textPaint)

                // Render table coordinates
                val startX = 40f
                val startY = 120f
                val rowHeight = 30f
                val nameWidth = 200f
                val colWidth = 60f

                // Draw header row dates
                canvas.drawText("Ученики", startX + 10, startY + 20, cellPaint)
                canvas.drawRect(startX, startY, startX + nameWidth, startY + rowHeight, outlinePaint)

                var curX = startX + nameWidth
                for ((index, les) in grpLessons.take(12).withIndex()) { // fit max 12 columns gracefully onto page
                    val shortDate = les.date.substring(5).replace("-", ".") // MM.DD
                    canvas.drawText("${shortDate}·${les.displayDisciplineAbbreviation}", curX + 5, startY + 20, cellPaint)
                    canvas.drawRect(curX, startY, curX + colWidth, startY + rowHeight, outlinePaint)
                    curX += colWidth
                }

                // Draw student items
                var curY = startY + rowHeight
                for (st in grpStudents) {
                    canvas.drawText(st.fullName, startX + 10, curY + 20, cellPaint)
                    canvas.drawRect(startX, curY, startX + nameWidth, curY + rowHeight, outlinePaint)

                    var cellX = startX + nameWidth
                    for (les in grpLessons.take(12)) {
                        val state = states.find { it.studentId == st.id && it.lessonId == les.id }
                        val symbol = when {
                            state == null -> ""
                            !state.isPresent -> "Н"
                            state.grade != null -> state.grade.toString()
                            else -> "+"
                        }
                        canvas.drawText(symbol, cellX + 15, curY + 20, cellPaint)
                        canvas.drawRect(cellX, curY, cellX + colWidth, curY + rowHeight, outlinePaint)
                        cellX += colWidth
                    }
                    curY += rowHeight
                }

                pdfDocument.finishPage(page)

                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "Journal_${grp.name.replace(" ", "_")}.pdf")
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                Toast.makeText(getApplication(), "Журнал экспортирован в Загрузки: ${file.name}", Toast.LENGTH_LONG).show()
                logAction("Экспорт PDF", "Журнал группы ${grp.name} экспортирован в PDF")
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Ошибка создания PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    // --- DATA SEEDER FOR ART SCHOOL ---
    fun loadTestData() {
        viewModelScope.launch {
            // Clear existing tables
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }

            // 1. Core Academic Year
            val yearId = repository.insertAcademicYear(
                AcademicYear(name = "2025-2026", holidays = "2026-01-01,2026-05-01,2026-05-09", isActive = true)
            ).toInt()

            // 2. Groups
            val g1 = repository.insertGroup(
                Group(name = "Группа А (Младшая)", academicYearId = yearId, disciplines = "Рисунок,Живопись,Композиция", schedule = "1:Рисунок,3:Живопись")
            ).toInt()
            val g2 = repository.insertGroup(
                Group(name = "Группа Б (Старшая)", academicYearId = yearId, disciplines = "Рисунок,Живопись,История Искусств", schedule = "2:Рисунок,4:Живопись")
            ).toInt()

            // Quarters Setup
            repository.insertQuarter(Quarter(academicYearId = yearId, name = "I четверть", startDate = "2026-09-01", endDate = "2026-10-25"))
            repository.insertQuarter(Quarter(academicYearId = yearId, name = "II четверть", startDate = "2026-11-01", endDate = "2026-12-28"))
            repository.insertQuarter(Quarter(academicYearId = yearId, name = "III четверть", startDate = "2026-01-10", endDate = "2026-03-20"))
            repository.insertQuarter(Quarter(academicYearId = yearId, name = "IV четверть", startDate = "2026-04-01", endDate = "2026-05-30"))

            // 3. Students for G1 (Младшая)
            val s1 = repository.insertStudent(
                Student(lastName = "Иванов", firstName = "Максим", birthday = "2015-04-12", enrollmentDate = "2026-05-01", contractNumber = "ДОП-102", groupId = g1, paperPaymentDate = "2026-05-15", paperPaymentAmount = 1500.0)
            ).toInt()
            val s2 = repository.insertStudent(
                Student(lastName = "Петрова", firstName = "София", birthday = "2016-09-22", enrollmentDate = "2026-05-01", contractNumber = "ДОП-103", groupId = g1, paperPaymentDate = null, paperPaymentAmount = null)
            ).toInt()
            val s3 = repository.insertStudent(
                Student(lastName = "Сидорова", firstName = "Анна", birthday = "2015-11-05", enrollmentDate = "2026-05-05", contractNumber = "ДОП-104", groupId = g1, paperPaymentDate = "2026-05-10", paperPaymentAmount = 1500.0)
            ).toInt()

            // 4. Students for G2 (Старшая)
            val s4 = repository.insertStudent(
                Student(lastName = "Кузнецов", firstName = "Даниил", birthday = "2011-03-18", enrollmentDate = "2026-05-01", contractNumber = "ДОП-201", groupId = g2)
            ).toInt()
            val s5 = repository.insertStudent(
                Student(lastName = "Смирнова", firstName = "Елена", birthday = "2010-07-31", enrollmentDate = "2026-05-01", contractNumber = "ДОП-202", groupId = g2)
            ).toInt()

            // Set default selected group
            _selectedGroupId.value = g1

            // 5. Payments history
            repository.insertPayment(Payment(studentId = s1, date = "2026-05-05", amount = 3000.0, comment = "Оплата обучения за май"))
            repository.insertPayment(Payment(studentId = s3, date = "2026-05-08", amount = 3000.0, comment = "Органайзер и майское обучение"))
            repository.insertPayment(Payment(studentId = s4, date = "2026-04-20", amount = 3000.0, comment = "Оплата обучения за апрель")) // Unpaid for May!

            // 6. Topics & Criteria for G1
            val t1 = repository.insertTopic(
                Topic(
                    name = "Осенний натюрморт с кувшином",
                    discipline = "Живопись",
                    criteria = "Композиция:10,Цветовая гамма:10,Объем и светотень:5",
                    groupIds = "$g1,$g2",
                    quarterIds = "1"
                )
            ).toInt()
            val t2 = repository.insertTopic(
                Topic(
                    name = "Построение куба и гипсовой сферы",
                    discipline = "Рисунок",
                    criteria = "Перспектива:10,Штриховка:5,Композиция:5",
                    groupIds = "$g1",
                    quarterIds = "1"
                )
            ).toInt()

            // 7. Calendar Lessons (Class Dates)
            val l1 = repository.insertLesson(Lesson(groupId = g1, date = "2026-05-12", discipline = "Рисунок", topicId = t2)).toInt()
            val l2 = repository.insertLesson(Lesson(groupId = g1, date = "2026-05-14", discipline = "Живопись", topicId = t1)).toInt()
            val l3 = repository.insertLesson(Lesson(groupId = g1, date = "2026-05-19", discipline = "Рисунок", topicId = t2)).toInt()
            val l4 = repository.insertLesson(Lesson(groupId = g1, date = "2026-05-21", discipline = "Живопись", topicId = t1)).toInt()

            // Double lessons query demonstration on 2026-05-26
            val l5 = repository.insertLesson(Lesson(groupId = g1, date = "2026-05-26", discipline = "Рисунок")).toInt()
            val l6 = repository.insertLesson(Lesson(groupId = g1, date = "2026-05-26", discipline = "Живопись")).toInt()

            // 8. Grades & Absences (StudentLessonStates)
            // Lesson 1 states (s1 got 5, s2 absent, s3 present with no grade)
            repository.insertStudentLessonState(StudentLessonState(studentId = s1, lessonId = l1, grade = 5, isPresent = true, homeworkPoints = 90, note = "Прекрасный штрих сферического объема"))
            repository.insertStudentLessonState(StudentLessonState(studentId = s2, lessonId = l1, grade = null, isPresent = false, isExcusedAbsence = false)) // Unexcused absence
            repository.insertStudentLessonState(StudentLessonState(studentId = s3, lessonId = l1, grade = 4, isPresent = true, homeworkPoints = 80))

            // Lesson 2 states (s1 got 4, s2 consecutive missed! (gives consecutive absence badge), s3 got 5)
            repository.insertStudentLessonState(StudentLessonState(studentId = s1, lessonId = l2, grade = 4, isPresent = true))
            repository.insertStudentLessonState(StudentLessonState(studentId = s2, lessonId = l2, grade = null, isPresent = false, isExcusedAbsence = true)) // Excused absence
            repository.insertStudentLessonState(StudentLessonState(studentId = s3, lessonId = l2, grade = 5, isPresent = true))

            // Topic criteria scoring
            repository.insertStudentTopicProgress(StudentTopicProgress(studentId = s1, topicId = t1, stage = 75, criteriaGrades = "Композиция:8,Цветовая гамма:9,Объем и светотень:4"))
            repository.insertStudentTopicProgress(StudentTopicProgress(studentId = s2, topicId = t1, stage = 20, criteriaGrades = "Композиция:3,Цветовая гамма:2,Объем и светотень:0"))

            // Audit
            repository.insertAuditLog(AuditLog(action = "Загрузка данных", details = "Сгенерированы тестовые группы, ученики, контрольные темы и оценки успеваемости"))

            Toast.makeText(getApplication(), "Тестовые данные загружены!", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                db.clearAllTables()
            }
            _selectedGroupId.value = null
            logAction("Сброс базы", "Все таблицы базы данных очищены")
            Toast.makeText(getApplication(), "Все данные удалены!", Toast.LENGTH_SHORT).show()
        }
    }
}
