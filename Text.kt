package com.salahface.watch.core

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Digit conversion. Language and numeral system are independent. */
object Numerals {
    private const val ARABIC_ZERO = '\u0660'

    fun localize(s: String, system: NumeralSystem): String {
        if (system == NumeralSystem.WESTERN) return s
        val sb = StringBuilder(s.length)
        for (c in s) {
            sb.append(
                when (c) {
                    in '0'..'9' -> ARABIC_ZERO + (c - '0')
                    ',' -> '\u066C' // Arabic thousands separator
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}

/** User-facing strings for both languages. Kept in code so switching language is instant. */
class Strings(val language: Language) {
    private val ar get() = language == Language.AR

    fun prayerName(p: Prayer): String = when (p) {
        Prayer.FAJR -> if (ar) "الفجر" else "Fajr"
        Prayer.SHURUQ -> if (ar) "الشروق" else "Shuruq"
        Prayer.DHUHR -> if (ar) "الظهر" else "Dhuhr"
        Prayer.ASR -> if (ar) "العصر" else "Asr"
        Prayer.MAGHRIB -> if (ar) "المغرب" else "Maghrib"
        Prayer.ISHA -> if (ar) "العشاء" else "Isha"
    }

    /** Header line, e.g. "NEXT PRAYER: ASR" / "الصلاة القادمة: العصر". */
    fun headerNext(p: Prayer) =
        if (ar) "الصلاة القادمة: ${prayerName(p)}" else "NEXT PRAYER: ${prayerName(p).uppercase()}"

    fun headerNow(p: Prayer) =
        if (ar) "الآن: ${prayerName(p)}" else "NOW: ${prayerName(p).uppercase()}"

    val setLocation get() = if (ar) "حدّد الموقع" else "SET LOCATION"
    val locationOutdated get() = if (ar) "الموقع قديم" else "LOCATION OUTDATED"
    fun ampm(pm: Boolean) = if (ar) (if (pm) "م" else "ص") else (if (pm) "PM" else "AM")
}

/** All clock/date/number text the face draws. */
class Formatter(val language: Language, val numerals: NumeralSystem, val clock24: Boolean) {
    private val strings = Strings(language)
    private fun n(s: String) = Numerals.localize(s, numerals)
    private fun two(v: Int) = if (v < 10) "0$v" else "$v"

    private fun hour(z: ZonedDateTime): Int =
        if (clock24) z.hour else (z.hour % 12).let { if (it == 0) 12 else it }

    /** "11:29" */
    fun clockMain(z: ZonedDateTime) = n("${two(hour(z))}:${two(z.minute)}")
    fun seconds(z: ZonedDateTime) = n(two(z.second))
    /** Empty in 24-hour mode. */
    fun ampm(z: ZonedDateTime) = if (clock24) "" else strings.ampm(z.hour >= 12)
    /** "03/11/2026" */
    fun date(z: ZonedDateTime) = n("${two(z.dayOfMonth)}/${two(z.monthValue)}/${z.year}")

    /** "03:45 PM" (or 24h "15:45"). */
    fun timeWithSuffix(i: Instant, zone: ZoneId): String {
        val z = i.atZone(zone)
        val base = n("${two(hour(z))}:${two(z.minute)}")
        return if (clock24) base else "$base ${strings.ampm(z.hour >= 12)}"
    }

    /** "03:45" without suffix, for the compact schedule. */
    fun timeShort(i: Instant, zone: ZoneId): String {
        val z = i.atZone(zone)
        return n("${two(hour(z))}:${two(z.minute)}")
    }

    fun countdown(deltaSeconds: Long) = n(CountdownFormatter.format(deltaSeconds))
    fun countdownAmbient(deltaSeconds: Long) = n(CountdownFormatter.formatAmbient(deltaSeconds, language))

    /** 8542 -> "8,542" / "٨٬٥٤٢" */
    fun steps(v: Long) = n(String.format(java.util.Locale.US, "%,d", v))
    fun plain(v: Int) = n(v.toString())
    fun percent(v: Int) = n("$v%")
}
