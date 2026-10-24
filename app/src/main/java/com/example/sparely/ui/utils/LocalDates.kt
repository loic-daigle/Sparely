package com.example.sparely.ui.utils

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Date formats that follow the phone's language: "Oct 24" in English, "24 oct." in French.
 * Read each time (not cached) so a language change applies right away. Never use a fixed
 * pattern like "MMM d, yyyy" for text people read; it keeps English word order in every language.
 */
object LocalDates {
    private fun best(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton))

    /** "Oct 24" / "24 oct." */
    val dayMonth: DateTimeFormatter get() = best("dMMM")

    /** "Oct 2026" / "oct. 2026" */
    val monthYear: DateTimeFormatter get() = best("MMMyyyy")

    /** "Friday, October 24" / "vendredi 24 octobre" */
    val weekdayDayMonth: DateTimeFormatter get() = best("EEEEdMMMM")

    /** "Oct 24, 3:15 PM" / "24 oct., 15 h 15" */
    val dayMonthTime: DateTimeFormatter get() = best("dMMMjmm")

    /** "Oct 24, 2026" / "24 oct. 2026" */
    val medium: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    /** "Oct 24, 2026, 3:15 PM" / "24 oct. 2026, 15 h 15" */
    val mediumDateTime: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
}
