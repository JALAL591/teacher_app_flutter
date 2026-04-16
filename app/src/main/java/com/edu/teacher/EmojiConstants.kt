package com.edu.teacher

import androidx.annotation.DrawableRes

/**
 * Centralized emoji and Unicode constants for the application.
 * All emojis are defined using Unicode escape sequences to avoid encoding issues.
 */
object EmojiConstants {
    // Navigation - 5 tabs for Telegram-style nav
    const val HOME = "\uD83C\uDFE0"
    const val STUDENTS = "\uD83D\uDC65"
    const val ADD_LESSON = "\uD83D\uDCD6"
    const val ATTENDANCE = "\uD83D\uDCCB"
    const val STATS = "\uD83D\uDCCA"
    const val SETTINGS = "\u2699\uFE0F"

    // Status Indicators
    const val SUCCESS = "\u2705"
    const val ERROR = "\u274C"
    const val WARNING = "\u26A0\uFE0F"
    const val CHECK = "\u2713"

    // Theme
    const val MOON = "\uD83C\uDF19"
    const val SUN = "\u2600\uFE0F"

    // Communication
    const val WAVE = "\uD83D\uDC4B"
    const val SYNC = "\uD83D\uDD04"
    const val HOURGLASS = "\u23F3"
    const val BROADCAST = "\uD83D\uDCE1"
    const val SAVE = "\uD83D\uDCBE"

    // Subjects (emoji strings for reference)
    const val QURAN_ISLAMIC = "\uD83C\uDF19"
    const val MATH = "\uD83D\uDCD0"
    const val SCIENCE = "\uD83E\uDDEA"
    const val ARABIC = "\u270D\uFE0F"
    const val ENGLISH = "\uD83D\uDD24"
    const val HISTORY_GEOGRAPHY = "\uD83C\uDF0D"
    const val PE_SPORTS = "\u26BD"
    const val ART = "\uD83C\uDFA8"
    const val DEFAULT_SUBJECT = "\uD83D\uDCDA"

    // Media
    const val VIDEO = "\uD83C\uDFE5"
    const val BOOKS = "\uD83D\uDCDA"
    const val PEOPLE = "\uD83D\uDC65"

    @DrawableRes
    fun getSubjectIcon(subject: String): Int {
        val s = subject.lowercase()
        return when {
            s.contains("قرآن") || s.contains("إسلام") -> R.drawable.ic_book
            s.contains("رياضيات") || s.contains("حساب") -> R.drawable.ic_book
            s.contains("علوم") || s.contains("فيزياء") || s.contains("كيمياء") -> R.drawable.ic_book
            s.contains("عربي") || s.contains("لغة") -> R.drawable.ic_book
            s.contains("إنجليزي") || s.contains("انجليزي") -> R.drawable.ic_book
            s.contains("تاريخ") || s.contains("جغرافيا") -> R.drawable.ic_book
            s.contains("رياضة") || s.contains("بدنية") -> R.drawable.ic_book
            s.contains("فن") || s.contains("رسم") -> R.drawable.ic_book
            else -> R.drawable.ic_book
        }
    }
}
