package com.example.sparely.ui.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object DateUtils {
    /**
     * Formats a LocalDate to a string (e.g., "Jan 1").
     */
    fun formatDate(date: LocalDate): String {
        return java.time.format.DateTimeFormatter.ofPattern("MMM d").format(date)
    }

    /**
     * Ensures a [LocalDate] is within the Material 3 DatePicker's valid range (1900-2100).
     * Returns the epoch milli in UTC for use with [rememberDatePickerState].
     */
    fun toSafeDatePickerMillis(date: LocalDate?): Long? {
        if (date == null) return null
        return if (date.year in 1900..2100) {
            date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            // Fallback to now if corrupted (e.g. year 0002)
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }

    /**
     * Converts epoch millis from DatePicker back to [LocalDate] using UTC.
     * 
     * IMPORTANT: Material 3 DatePicker returns UTC-based millis, so we must convert
     * using UTC timezone to avoid off-by-one day errors in non-UTC timezones.
     */
    fun fromDatePickerMillis(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    }

    /**
     * Nullable version of [fromDatePickerMillis].
     */
    fun fromDatePickerMillisOrNull(millis: Long?): LocalDate? {
        return millis?.let { fromDatePickerMillis(it) }
    }

    /**
     * Ensures a [LocalDateTime] is within the Material 3 DatePicker's valid range.
     */
    fun toSafeDatePickerMillis(dateTime: LocalDateTime?): Long {
        val date = dateTime?.toLocalDate() ?: LocalDate.now()
        return if (date.year in 1900..2100) {
            dateTime?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli() 
                ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    }
}

// Extension functions for backward compatibility
fun LocalDate?.toSafeDatePickerMillis(): Long? = DateUtils.toSafeDatePickerMillis(this)

fun Long.fromDatePickerMillis(): LocalDate = DateUtils.fromDatePickerMillis(this)

fun Long?.fromDatePickerMillisOrNull(): LocalDate? = DateUtils.fromDatePickerMillisOrNull(this)

fun LocalDateTime?.toSafeDatePickerMillis(): Long = DateUtils.toSafeDatePickerMillis(this)
