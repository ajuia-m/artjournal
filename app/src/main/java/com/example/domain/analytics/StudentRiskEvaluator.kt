package com.example.domain.analytics

class StudentRiskEvaluator {

    fun hasConsecutiveAbsences(
        snapshot: AnalyticsSnapshot,
        studentId: Int,
        groupId: Int,
        asOfDate: String,
        requiredAbsences: Int = 2
    ): Boolean {
        require(requiredAbsences > 0) { "requiredAbsences must be positive" }

        val asOfDay = IsoDate.epochDay(asOfDate) ?: return false
        val statesByLesson = snapshot.lessonStates
            .filter { it.studentId == studentId }
            .groupBy { it.lessonId }
            .mapValues { (_, states) -> states.maxBy { it.id } }
        val completedLessons = snapshot.lessons
            .filter { lesson ->
                val lessonDay = IsoDate.epochDay(lesson.date)
                lesson.groupId == groupId &&
                    !lesson.isNonSchoolDay &&
                    lessonDay != null &&
                    lessonDay <= asOfDay
            }
            .sortedWith(
                compareByDescending<AnalyticsLesson> {
                    IsoDate.epochDay(it.date) ?: Long.MIN_VALUE
                }
                    .thenByDescending { it.id }
            )

        var consecutiveAbsences = 0
        for (lesson in completedLessons) {
            val state = statesByLesson[lesson.id] ?: return false
            if (state.isPresent) return false

            consecutiveAbsences++
            if (consecutiveAbsences >= requiredAbsences) return true
        }

        return false
    }

    fun paymentRecordSignal(
        enrollmentDate: String,
        paymentDates: List<String>,
        asOfDate: String,
        staleAfterDays: Int = 30
    ): PaymentRecordSignal {
        require(staleAfterDays >= 0) { "staleAfterDays must not be negative" }

        val asOfDay = IsoDate.epochDay(asOfDate)
            ?: return PaymentRecordSignal(null, null, false)
        val latestPayment = paymentDates
            .mapNotNull { date -> IsoDate.epochDay(date)?.let { day -> date to day } }
            .filter { (_, day) -> day <= asOfDay }
            .maxByOrNull { (_, day) -> day }
        val reference = latestPayment
            ?: IsoDate.epochDay(enrollmentDate)
                ?.takeIf { it <= asOfDay }
                ?.let { enrollmentDate to it }
            ?: return PaymentRecordSignal(null, null, false)
        val daysSinceReference = asOfDay - reference.second

        return PaymentRecordSignal(
            referenceDate = reference.first,
            daysSinceReference = daysSinceReference,
            isStale = daysSinceReference > staleAfterDays
        )
    }
}
