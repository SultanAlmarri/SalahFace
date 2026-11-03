package com.salahface.watch.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.sqrt

/** Computes one day of prayer times for a place, entirely on the device (no network). */
object PrayerCalculator {

    /** Whole seconds since the UT-day start, truncated the same way the reference library does. */
    private fun hoursToSeconds(h: Double): Long = floor(h * 3600.0).toLong()

    /** Round half-up to the whole minute: 29 s drops, 30 s rolls over. */
    internal fun roundToMinute(epochSec: Long): Long {
        val s = Math.floorMod(epochSec, 60L)
        return epochSec - s + if (s >= 30) 60 else 0
    }

    private class Raw(val dayStartSec: Long, val solar: Astro.SolarTime, val nextSunriseSec: Long) {
        fun at(hours: Double): Long? = if (hours.isNaN() || hours.isInfinite()) null else dayStartSec + hoursToSeconds(hours)
    }

    private fun raw(utDate: LocalDate, p: Place, dip: Double): Raw {
        val start = utDate.atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
        val solar = Astro.SolarTime(utDate.year, utDate.monthValue, utDate.dayOfMonth, p.latitude, p.longitude, dip)
        val t = utDate.plusDays(1)
        val tomorrow = Astro.SolarTime(t.year, t.monthValue, t.dayOfMonth, p.latitude, p.longitude, dip)
        val nextSunrise = tomorrow.sunrise
        val nextSunriseSec = if (nextSunrise.isNaN()) start + 86400L + 6 * 3600 else start + 86400L + hoursToSeconds(nextSunrise)
        return Raw(start, solar, nextSunriseSec)
    }

    fun compute(date: LocalDate, place: Place, zone: ZoneId, profile: CalcProfile): DaySchedule {
        val dip = if (profile.elevationM > 0) 0.0293 * sqrt(profile.elevationM) else 0.0
        // The solar day (UT) that contains local noon-ish transit for [date] in [zone].
        var chosen: Raw? = null
        for (offset in longArrayOf(0, -1, 1)) {
            val r = raw(date.plusDays(offset), place, dip)
            val transit = r.at(r.solar.transit) ?: continue
            if (Instant.ofEpochSecond(transit).atZone(zone).toLocalDate() == date) { chosen = r; break }
        }
        val r = chosen ?: raw(date, place, dip)

        val sunriseSec = r.at(r.solar.sunrise)
        val sunsetSec = r.at(r.solar.sunset)
        val transitSec = r.at(r.solar.transit)
        val night = if (sunsetSec != null) (r.nextSunriseSec - sunsetSec) else 12 * 3600L
        val sunriseBase = sunriseSec ?: (r.dayStartSec + 6 * 3600)
        val sunsetBase = sunsetSec ?: (r.dayStartSec + 18 * 3600)

        // High-latitude safety bound (middle of the night); never triggers in the UAE.
        val half = night / 2
        var fajr = r.at(r.solar.hourAngle(-profile.fajrAngle, false))
        val safeFajr = sunriseBase - half
        if (fajr == null || fajr < safeFajr) fajr = safeFajr
        var isha = r.at(r.solar.hourAngle(-profile.ishaAngle, true))
        val safeIsha = sunsetBase + half
        if (isha == null || isha > safeIsha) isha = safeIsha

        val asr = r.at(r.solar.afternoon(profile.asrShadowFactor))
            ?: (transitSec ?: (r.dayStartSec + 12 * 3600)) + 3 * 3600

        val base = mapOf(
            Prayer.FAJR to fajr,
            Prayer.SHURUQ to sunriseBase,
            Prayer.DHUHR to (transitSec ?: (r.dayStartSec + 12 * 3600)),
            Prayer.ASR to asr,
            Prayer.MAGHRIB to sunsetBase,
            Prayer.ISHA to isha
        )
        val result = LinkedHashMap<Prayer, Instant>()
        for (p in Prayer.values()) {
            val minutes = (profile.methodAdjustMinutes[p] ?: 0) + (profile.userOffsets[p] ?: 0)
            val sec = roundToMinute(base.getValue(p) + minutes * 60L)
            result[p] = Instant.ofEpochSecond(sec)
        }
        return DaySchedule(date, zone, result)
    }
}

/** Small cache so the face does not recompute astronomy every second. */
class ScheduleProvider {
    private data class Key(val date: LocalDate, val place: Place, val zoneId: String, val profile: CalcProfile)

    private val cache = LinkedHashMap<Key, DaySchedule>()

    fun day(date: LocalDate, place: Place, zone: ZoneId, profile: CalcProfile): DaySchedule {
        val key = Key(date, place, zone.id, profile)
        return cache.getOrPut(key) {
            if (cache.size > 16) cache.remove(cache.keys.first())
            PrayerCalculator.compute(date, place, zone, profile)
        }
    }

    /** Obligatory prayers for yesterday, today and tomorrow, sorted by time. */
    fun events(now: Instant, place: Place, zone: ZoneId, profile: CalcProfile): List<PrayerEvent> {
        val today = now.atZone(zone).toLocalDate()
        return (-1L..1L)
            .flatMap { day(today.plusDays(it), place, zone, profile).obligatory() }
            .sortedBy { it.at }
    }

    fun today(now: Instant, place: Place, zone: ZoneId, profile: CalcProfile): DaySchedule =
        day(now.atZone(zone).toLocalDate(), place, zone, profile)
}
