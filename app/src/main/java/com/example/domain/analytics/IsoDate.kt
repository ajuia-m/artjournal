package com.example.domain.analytics

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal object IsoDate {
    private const val MillisecondsPerDay = 24L * 60L * 60L * 1_000L

    fun epochDay(value: String): Long? {
        if (value.length != 10) return null

        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val position = ParsePosition(0)
        val parsed = formatter.parse(value, position) ?: return null
        if (position.index != value.length) return null

        return parsed.time / MillisecondsPerDay
    }

    fun isWithin(date: String, period: AnalyticsPeriod): Boolean {
        val day = epochDay(date) ?: return false
        val start = epochDay(period.startDate) ?: return false
        val end = epochDay(period.endDate) ?: return false
        val asOf = epochDay(period.asOfDate) ?: return false
        if (start > end) return false

        return day in start..minOf(end, asOf)
    }
}
