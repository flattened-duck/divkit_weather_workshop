package workshop.weather

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import workshop.l10n.Localizer

fun hhmm(iso: String): String =
    LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm"))

fun round(d: Double): Int = Math.round(d).toInt()

fun shortWeekday(isoDate: String, loc: Localizer): String {
    val key = when (LocalDate.parse(isoDate).dayOfWeek) {
        DayOfWeek.MONDAY -> "weekday.mon"
        DayOfWeek.TUESDAY -> "weekday.tue"
        DayOfWeek.WEDNESDAY -> "weekday.wed"
        DayOfWeek.THURSDAY -> "weekday.thu"
        DayOfWeek.FRIDAY -> "weekday.fri"
        DayOfWeek.SATURDAY -> "weekday.sat"
        DayOfWeek.SUNDAY -> "weekday.sun"
    }
    val fallback = key.removePrefix("weekday.").replaceFirstChar { it.uppercase() }
    return loc.getOrDefault(key, fallback)
}
