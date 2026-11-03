package com.salahface.watch.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The six entries on the face. Only the five obligatory ones ever take part in the countdown. */
enum class Prayer(val obligatory: Boolean) {
    FAJR(true), SHURUQ(false), DHUHR(true), ASR(true), MAGHRIB(true), ISHA(true)
}

enum class Language { EN, AR }
enum class NumeralSystem { WESTERN, ARABIC_INDIC }
enum class LocationMode { MANUAL, AUTO }

/** Accent colours (ARGB). The first is the default "subtle green". */
enum class Accent(val argb: Int, val nameEn: String, val nameAr: String) {
    GREEN(0xFF5FD38D.toInt(), "Green", "أخضر"),
    GOLD(0xFFD9B45A.toInt(), "Gold", "ذهبي"),
    TEAL(0xFF4FD1C5.toInt(), "Teal", "فيروزي"),
    SKY(0xFF6CB4F5.toInt(), "Sky", "سماوي"),
    ROSE(0xFFF08DB0.toInt(), "Rose", "وردي")
}

data class Place(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double,
    val zoneId: String
) {
    fun zone(): ZoneId = ZoneId.of(zoneId)
}

/**
 * Everything that influences the calculated times. All values are explicit and shown in the README;
 * nothing is hidden. Defaults mirror the widely used "Dubai/Gulf" profile (18.2° / 18.2°, standard Asr,
 * +3 min on Dhuhr/Asr/Maghrib, -3 min on Shuruq). They are an APPROXIMATION of the UAE official
 * timetable and have NOT been verified against the Dulook DXB app: use [userOffsets] to align them.
 */
data class CalcProfile(
    val fajrAngle: Double = 18.2,
    val ishaAngle: Double = 18.2,
    val asrShadowFactor: Double = 1.0,
    val methodAdjustMinutes: Map<Prayer, Int> = mapOf(
        Prayer.SHURUQ to -3, Prayer.DHUHR to 3, Prayer.ASR to 3, Prayer.MAGHRIB to 3
    ),
    /** Extra per-prayer calibration in whole minutes, chosen by the user. */
    val userOffsets: Map<Prayer, Int> = emptyMap(),
    /** Observer elevation in metres; lowers the sunrise/sunset horizon. 0 = ignore elevation. */
    val elevationM: Double = 0.0
)

class DaySchedule(
    val date: LocalDate,
    val zone: ZoneId,
    val times: Map<Prayer, Instant>
) {
    operator fun get(p: Prayer): Instant = times.getValue(p)
    fun obligatory(): List<PrayerEvent> =
        Prayer.values().filter { it.obligatory }.map { PrayerEvent(it, times.getValue(it)) }
}

data class PrayerEvent(val prayer: Prayer, val at: Instant)
