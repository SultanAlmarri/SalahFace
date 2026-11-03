package com.salahface.watch.core

import java.time.Instant
import kotlin.math.abs

/**
 * Formats a signed number of seconds as the face shows it:
 *   negative (time remaining)  -> "-M:SS" or "-H:M:SS"
 *   zero / positive (elapsed)  -> "+M:SS" or "+H:M:SS"
 * Hours appear only when non-zero, hours and minutes never get a leading zero, seconds always have two digits.
 */
object CountdownFormatter {
    fun format(deltaSeconds: Long): String {
        val sign = if (deltaSeconds < 0) "-" else "+"
        val total = abs(deltaSeconds)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val ss = if (s < 10) "0$s" else "$s"
        return if (h > 0) "$sign$h:$m:$ss" else "$sign$m:$ss"
    }

    /**
     * Coarse form for the Always-On Display, which only redraws once a minute.
     * English: "-9m", "-1h 9m", "+5m".  Arabic: "-9د", "-1س 9د".  Rounded to the nearest minute.
     */
    fun formatAmbient(deltaSeconds: Long, language: Language): String {
        val sign = if (deltaSeconds < 0) "-" else "+"
        val minutesTotal = (abs(deltaSeconds) + 30) / 60
        val h = minutesTotal / 60
        val m = minutesTotal % 60
        val hu = if (language == Language.AR) "س" else "h"
        val mu = if (language == Language.AR) "د" else "m"
        return if (h > 0) "$sign$h$hu $m$mu" else "$sign$m$mu"
    }
}

enum class Phase { COUNTDOWN, ELAPSED }

/**
 * @param prayer the prayer the header is about (upcoming one, or the one whose Adhan just passed)
 * @param deltaSeconds negative = seconds remaining, >= 0 = seconds elapsed since Adhan
 * @param nextPrayer first obligatory prayer whose Adhan is still in the future (used for highlighting)
 */
data class CountdownState(
    val phase: Phase,
    val prayer: Prayer,
    val adhan: Instant,
    val deltaSeconds: Long,
    val nextPrayer: Prayer,
    val nextAdhan: Instant
)

object CountdownEngine {
    const val ELAPSED_WINDOW_SECONDS = 30 * 60L

    /**
     * Pure function of (now, events) so the result is always correct after restarts, time-zone changes
     * and midnight. [events] must contain only obligatory prayers (never Shuruq) around [now].
     */
    fun evaluate(now: Instant, events: List<PrayerEvent>): CountdownState? {
        val nowMs = now.toEpochMilli()
        val next = events.firstOrNull { it.at.toEpochMilli() > nowMs } ?: return null
        val prev = events.lastOrNull { it.at.toEpochMilli() <= nowMs }
        if (prev != null) {
            val elapsed = Math.floorDiv(nowMs - prev.at.toEpochMilli(), 1000L)
            if (elapsed < ELAPSED_WINDOW_SECONDS) {
                return CountdownState(Phase.ELAPSED, prev.prayer, prev.at, elapsed, next.prayer, next.at)
            }
        }
        val remaining = -Math.floorDiv(-(next.at.toEpochMilli() - nowMs), 1000L) // ceil
        return CountdownState(Phase.COUNTDOWN, next.prayer, next.at, -remaining, next.prayer, next.at)
    }
}
