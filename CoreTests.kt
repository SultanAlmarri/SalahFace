package com.salahface.watch.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CountdownFormatterTest {
    @Test fun spec_examples() {
        assertEquals("-30:00", CountdownFormatter.format(-30 * 60L))
        assertEquals("-9:05", CountdownFormatter.format(-(9 * 60L + 5)))
        assertEquals("-5:30", CountdownFormatter.format(-(5 * 60L + 30)))
        assertEquals("-1:9:05", CountdownFormatter.format(-(3600L + 9 * 60 + 5)))
        assertEquals("-2:5:00", CountdownFormatter.format(-(2 * 3600L + 5 * 60)))
        assertEquals("+1:00", CountdownFormatter.format(60))
        assertEquals("+5:00", CountdownFormatter.format(300))
        assertEquals("+10:00", CountdownFormatter.format(600))
        assertEquals("+20:00", CountdownFormatter.format(1200))
        assertEquals("+30:00", CountdownFormatter.format(1800))
        assertEquals("+9:05", CountdownFormatter.format(9 * 60L + 5))
        assertEquals("+1:5:00", CountdownFormatter.format(3600L + 5 * 60))
    }

    @Test fun no_leading_zeros_and_two_digit_seconds() {
        assertEquals("-0:09", CountdownFormatter.format(-9))
        assertEquals("+0:00", CountdownFormatter.format(0))
        assertEquals("-1:0:05", CountdownFormatter.format(-(3600L + 5)))
        for (t in 1L..40000L step 7) {
            val s = CountdownFormatter.format(-t)
            assertTrue(s, !s.startsWith("-0") || s.startsWith("-0:"))
            assertTrue(s, !Regex("^[-+]0\\d").containsMatchIn(s))
            assertTrue(s, Regex("^[-+](\\d+:)?\\d+:\\d\\d$").matches(s))
        }
    }

    @Test fun ambient_form() {
        assertEquals("-9m", CountdownFormatter.formatAmbient(-540, Language.EN))
        assertEquals("-1h 9m", CountdownFormatter.formatAmbient(-(3600L + 9 * 60), Language.EN))
        assertEquals("+5m", CountdownFormatter.formatAmbient(300, Language.EN))
        assertEquals("-9د", CountdownFormatter.formatAmbient(-540, Language.AR))
    }
}

class NumeralTest {
    @Test fun arabic_indic_digits_independent_of_language() {
        val en = Formatter(Language.EN, NumeralSystem.ARABIC_INDIC, false)
        val ar = Formatter(Language.AR, NumeralSystem.WESTERN, false)
        assertEquals("-٩:٠٥", en.countdown(-545))
        assertEquals("-9:05", ar.countdown(-545))
        assertEquals("٨٬٥٤٢", en.steps(8542))
        assertEquals("8,542", ar.steps(8542))
    }

    @Test fun clock_and_date() {
        val z = ZonedDateTime.of(2026, 11, 3, 15, 45, 12, 0, ZoneId.of("Asia/Dubai"))
        val en = Formatter(Language.EN, NumeralSystem.WESTERN, false)
        assertEquals("03:45", en.clockMain(z)); assertEquals("12", en.seconds(z)); assertEquals("PM", en.ampm(z))
        assertEquals("03/11/2026", en.date(z))
        val ar = Formatter(Language.AR, NumeralSystem.ARABIC_INDIC, false)
        assertEquals("٠٣:٤٥", ar.clockMain(z)); assertEquals("م", ar.ampm(z)); assertEquals("٠٣/١١/٢٠٢٦", ar.date(z))
        val am = ZonedDateTime.of(2026, 11, 3, 0, 5, 0, 0, ZoneId.of("Asia/Dubai"))
        assertEquals("12:05", en.clockMain(am)); assertEquals("AM", en.ampm(am)); assertEquals("ص", ar.ampm(am))
        assertEquals("٠٣:٤٥ م", ar.timeWithSuffix(z.toInstant(), z.zone))
        assertEquals("03:45 م", Formatter(Language.AR, NumeralSystem.WESTERN, false).timeWithSuffix(z.toInstant(), z.zone))
        assertEquals("03:45 PM", en.timeWithSuffix(z.toInstant(), z.zone))
    }
}

class CountdownEngineTest {
    private val zone = ZoneId.of("Asia/Dubai")
    private val place = Places.default
    private val provider = ScheduleProvider()
    private fun at(d: LocalDate, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(d, java.time.LocalTime.of(h, m, s), zone).toInstant()
    private fun state(now: Instant) = CountdownEngine.evaluate(now, provider.events(now, place, zone, CalcProfile()))!!
    private val day = LocalDate.of(2026, 11, 3)
    private fun adhan(p: Prayer) = provider.day(day, place, zone, CalcProfile())[p]

    @Test fun counts_down_then_flips_to_elapsed_exactly_at_adhan() {
        val asr = adhan(Prayer.ASR)
        val before = state(asr.minusSeconds(545))
        assertEquals(Phase.COUNTDOWN, before.phase); assertEquals(Prayer.ASR, before.prayer)
        assertEquals("-9:05", CountdownFormatter.format(before.deltaSeconds))
        val oneMs = state(asr.minusMillis(1))
        assertEquals(Phase.COUNTDOWN, oneMs.phase); assertEquals(-1L, oneMs.deltaSeconds)
        val exact = state(asr)
        assertEquals(Phase.ELAPSED, exact.phase); assertEquals("+0:00", CountdownFormatter.format(exact.deltaSeconds))
        val after = state(asr.plusSeconds(60))
        assertEquals("+1:00", CountdownFormatter.format(after.deltaSeconds))
        assertEquals(Prayer.MAGHRIB, after.nextPrayer)
    }

    @Test fun switches_to_next_prayer_after_30_minutes() {
        val asr = adhan(Prayer.ASR)
        val last = state(asr.plusSeconds(30 * 60 - 1))
        assertEquals(Phase.ELAPSED, last.phase); assertEquals("+29:59", CountdownFormatter.format(last.deltaSeconds))
        val switched = state(asr.plusSeconds(30 * 60))
        assertEquals(Phase.COUNTDOWN, switched.phase); assertEquals(Prayer.MAGHRIB, switched.prayer)
        assertEquals(adhan(Prayer.MAGHRIB), switched.adhan)
    }

    @Test fun shuruq_is_never_used() {
        val shuruq = adhan(Prayer.SHURUQ)
        for (offset in -600L..600L step 30) {
            val s = state(shuruq.plusSeconds(offset))
            assertTrue(s.prayer != Prayer.SHURUQ && s.nextPrayer != Prayer.SHURUQ)
        }
        // Between Fajr+30min and Dhuhr the countdown targets Dhuhr, not sunrise.
        assertEquals(Prayer.DHUHR, state(shuruq).prayer)
    }

    @Test fun midnight_and_day_rollover() {
        val isha = adhan(Prayer.ISHA)
        val lateNight = state(at(day, 23, 59, 59))
        assertEquals(Phase.COUNTDOWN, lateNight.phase); assertEquals(Prayer.FAJR, lateNight.prayer)
        val tomorrowFajr = provider.day(day.plusDays(1), place, zone, CalcProfile())[Prayer.FAJR]
        assertEquals(tomorrowFajr, lateNight.adhan)
        val justAfterMidnight = state(at(day.plusDays(1), 0, 0, 1))
        assertEquals(Prayer.FAJR, justAfterMidnight.prayer)
        // Isha elapsed window straddling nothing special, still correct
        assertEquals(Phase.ELAPSED, state(isha.plusSeconds(5)).phase)
        // early morning before Fajr: counting down to today's Fajr
        val early = state(at(day.plusDays(1), 3, 0))
        assertEquals(Prayer.FAJR, early.prayer); assertEquals(Phase.COUNTDOWN, early.phase)
    }

    @Test fun close_prayers_do_not_misfire() {
        val t = Instant.parse("2026-11-03T08:00:00Z")
        val events = listOf(
            PrayerEvent(Prayer.DHUHR, t), PrayerEvent(Prayer.ASR, t.plusSeconds(10 * 60)), PrayerEvent(Prayer.MAGHRIB, t.plusSeconds(3 * 3600))
        )
        val s = CountdownEngine.evaluate(t.plusSeconds(11 * 60), events)!!
        assertEquals(Prayer.ASR, s.prayer); assertEquals(Phase.ELAPSED, s.phase)
        assertEquals(Prayer.MAGHRIB, s.nextPrayer)
    }

    @Test fun restart_gives_same_answer() {
        val now = at(day, 13, 7, 41)
        val a = state(now)
        val b = CountdownEngine.evaluate(now, ScheduleProvider().events(now, place, zone, CalcProfile()))!!
        assertEquals(a, b)
    }
}

class PrayerCalculatorTest {
    @Test fun dubai_sanity_and_ordering() {
        val p = Places.default
        val zone = p.zone()
        for (i in 0 until 365) {
            val d = LocalDate.of(2026, 1, 1).plusDays(i.toLong())
            val s = PrayerCalculator.compute(d, p, zone, CalcProfile())
            val order = Prayer.values().map { s[it] }
            assertEquals(order.sorted(), order)
            for (t in order) { assertEquals(0, t.epochSecond % 60); assertEquals(d, t.atZone(zone).toLocalDate()) }
        }
    }

    @Test fun user_offsets_apply_in_whole_minutes() {
        val p = Places.default
        val d = LocalDate.of(2026, 11, 3)
        val base = PrayerCalculator.compute(d, p, p.zone(), CalcProfile())
        val moved = PrayerCalculator.compute(d, p, p.zone(), CalcProfile(userOffsets = mapOf(Prayer.ASR to -2, Prayer.FAJR to 1)))
        assertEquals(base[Prayer.ASR].minusSeconds(120), moved[Prayer.ASR])
        assertEquals(base[Prayer.FAJR].plusSeconds(60), moved[Prayer.FAJR])
        assertEquals(base[Prayer.ISHA], moved[Prayer.ISHA])
    }

    @Test fun timezone_crossing_places_still_land_on_requested_date() {
        val auckland = Place("akl", "Auckland", "أوكلاند", -36.85, 174.76, 0.0, "Pacific/Auckland")
        val d = LocalDate.of(2026, 11, 3)
        val s = PrayerCalculator.compute(d, auckland, auckland.zone(), CalcProfile())
        assertNotNull(s)
        assertEquals(d, s[Prayer.DHUHR].atZone(auckland.zone()).toLocalDate())
    }
}
