package com.kirox.app.ui.format

import com.kirox.app.telegram.TelegramUserStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Pure date/status formatting for Telegram data; [nowMillis] and [zone] are injectable for tests. */
object TelegramFormat {
    private val locale = Locale.forLanguageTag("ru")

    /** Chat list time: today HH:mm, this week weekday, older dd.MM.yy. [unixSeconds] ≤ 0 → empty. */
    fun chatTime(unixSeconds: Int, nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): String {
        if (unixSeconds <= 0) return ""
        val millis = unixSeconds * 1000L
        val days = daysBetween(millis, nowMillis, zone)
        val pattern = when {
            days == 0 -> "HH:mm"
            days in 1..6 -> "EE"
            else -> "dd.MM.yy"
        }
        return format(pattern, millis, zone)
    }

    fun status(status: TelegramUserStatus, nowMillis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): String = when (status) {
        TelegramUserStatus.Online -> "в сети"
        TelegramUserStatus.Recently -> "был(а) недавно"
        TelegramUserStatus.LastWeek -> "был(а) на этой неделе"
        TelegramUserStatus.LastMonth -> "был(а) в этом месяце"
        TelegramUserStatus.LongAgo -> "был(а) давно"
        is TelegramUserStatus.Offline -> {
            val millis = status.wasOnline * 1000L
            when (daysBetween(millis, nowMillis, zone)) {
                0 -> "был(а) в ${format("HH:mm", millis, zone)}"
                1 -> "был(а) вчера в ${format("HH:mm", millis, zone)}"
                else -> "был(а) ${format("dd.MM.yy", millis, zone)}"
            }
        }
    }

    /** TDLib phone numbers come without "+". */
    fun phone(raw: String): String = if (raw.isBlank()) "" else "+" + raw.trimStart('+')

    private fun format(pattern: String, millis: Long, zone: TimeZone): String =
        SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(millis))

    private fun daysBetween(fromMillis: Long, toMillis: Long, zone: TimeZone): Int {
        fun dayStart(millis: Long) = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ((dayStart(toMillis) - dayStart(fromMillis)) / 86_400_000L).toInt()
    }
}
