package com.salahface.watch.face

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.BatteryManager
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.salahface.watch.R
import com.salahface.watch.core.*
import com.salahface.watch.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Draws the whole face. Layout is expressed as fractions of the screen height so it scales to any
 * round display (Galaxy Watch5 Pro: 450 x 450 px). Everything is recomputed from "now" on each frame,
 * so restarts, midnight and time-zone changes need no special handling.
 */
class SalahRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository
) : Renderer.CanvasRenderer2<SalahRenderer.Assets>(
    surfaceHolder, currentUserStyleRepository, watchState,
    androidx.wear.watchface.CanvasType.HARDWARE,
    1000L,   // redraw every second while the screen is on
    false
) {
    class Assets : SharedAssets { override fun onDestroy() {} }

    override suspend fun createSharedAssets() = Assets()

    private val settingsStore = SettingsStore.get(context)
    private val metricsStore = MetricsStore.get(context)
    @Volatile private var settings = settingsStore.load()
    @Volatile private var metrics = metricsStore.load()
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        settings = settingsStore.load(); metrics = metricsStore.load(); invalidate()
    }

    init {
        settingsStore.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        metricsStore.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        settingsStore.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        metricsStore.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    // ---- typography ------------------------------------------------------------------------------
    private fun font(id: Int): Typeface = ResourcesCompat.getFont(context, id) ?: Typeface.DEFAULT
    private val light = font(R.font.tajawal_light)
    private val regular = font(R.font.tajawal_regular)
    private val medium = font(R.font.tajawal_medium)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val white = Color.WHITE
    private val grey = 0xFF9A9CA1.toInt()
    private val softWhite = 0xFFD7D8DB.toInt()
    private val dim = 0xFF5E6064.toInt()
    private val warn = 0xFFE8A04C.toInt()

    // ---- cached calculation ----------------------------------------------------------------------
    private val provider = ScheduleProvider()
    private var cachedBattery: Int? = null
    private var batteryAt = 0L
    private var lastFixCheck = 0L

    private fun battery(nowMs: Long): Int? {
        if (nowMs - batteryAt > 20_000) {
            batteryAt = nowMs
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            cachedBattery = if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt() else null
        }
        return cachedBattery
    }

    /** In GPS mode, quietly adopt a newer cached location (at most every 30 min; needs permission). */
    private fun maybeAdoptNewerFix(s: WatchSettings, nowMs: Long) {
        if (s.locationMode != LocationMode.AUTO || nowMs - lastFixCheck < 30 * 60_000L) return
        lastFixCheck = nowMs
        val fresh = LocationHelper.lastKnown(context) ?: return
        if (s.fix == null || fresh.epochMs > s.fix.epochMs) settingsStore.update { it.copy(fix = fresh) }
    }

    // ---- drawing helpers -------------------------------------------------------------------------
    private var aa = true

    private fun style(size: Float, color: Int, tf: Typeface, align: Paint.Align = Paint.Align.LEFT): Paint {
        paint.isAntiAlias = aa
        paint.textSize = size; paint.color = color; paint.typeface = tf; paint.textAlign = align
        return paint
    }

    private fun width(text: String, size: Float, tf: Typeface): Float = style(size, white, tf).measureText(text)

    /** Same string with every digit replaced by the widest digit, so layouts do not jitter each second. */
    private fun stable(text: String): String = text.map {
        when { it in '0'..'9' -> '8'; it in '\u0660'..'\u0669' -> '\u0668'; else -> it }
    }.joinToString("")

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, tf: Typeface,
                     align: Paint.Align = Paint.Align.LEFT) {
        c.drawText(s, x, y, style(size, color, tf, align))
    }

    private fun icon(c: Canvas, d: String, cx: Float, cy: Float, size: Float, color: Int) {
        iconPaint.isAntiAlias = aa; iconPaint.color = color
        Icons.draw(c, d, cx, cy, size, iconPaint)
    }

    // ---- main entry ------------------------------------------------------------------------------
    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        val s = settings
        val ambient = renderParameters.drawMode == DrawMode.AMBIENT
        aa = !(ambient && watchState.hasLowBitAmbient)
        canvas.drawColor(Color.BLACK)

        val h = bounds.height().toFloat()
        val cx = bounds.exactCenterX()
        val rtl = s.language == Language.AR
        val fmt = Formatter(s.language, s.numerals, s.clock24)
        val strings = Strings(s.language)
        val accent = s.accent.argb
        val nowInstant = zonedDateTime.toInstant()
        val nowMs = nowInstant.toEpochMilli()

        maybeAdoptNewerFix(s, nowMs)
        val place = s.place()
        val zone: ZoneId = place?.zone() ?: zonedDateTime.zone
        val profile = s.profile()
        val events = place?.let { provider.events(nowInstant, it, zone, profile) }
        val state = events?.let { CountdownEngine.evaluate(nowInstant, it) }
        val today = place?.let { provider.today(nowInstant, it, zone, profile) }

        val tint = if (ambient) 0.72f else 1f
        fun dimmed(c: Int) = if (!ambient) c else Color.argb(
            Color.alpha(c), (Color.red(c) * tint).toInt(), (Color.green(c) * tint).toInt(), (Color.blue(c) * tint).toInt())

        // Top: date (+ status when something is wrong)
        val statusText = when {
            place == null -> strings.setLocation
            s.locationOutdated(nowMs) -> strings.locationOutdated
            else -> null
        }
        text(canvas, fmt.date(zonedDateTime), cx, h * 0.118f, h * 0.042f, dimmed(grey), regular, Paint.Align.CENTER)
        if (statusText != null && !ambient) text(canvas, statusText, cx, h * 0.072f, h * 0.030f, warn, medium, Paint.Align.CENTER)

        drawClock(canvas, zonedDateTime, fmt, cx, h, ambient, dimmed(white), dimmed(grey), s)
        drawPrayerBlock(canvas, state, place != null, fmt, strings, zone, cx, h, accent, rtl, ambient, ::dimmed)

        if (!ambient || s.aodShowSchedule) drawSchedule(canvas, today, state, fmt, strings, zone, cx, h, accent, rtl, ambient, ::dimmed)
        if (ambient) drawBatteryOnly(canvas, battery(nowMs), fmt, cx, h, ::dimmed)
        else if (s.showMetrics) drawMetrics(canvas, battery(nowMs), fmt, cx, h, rtl, nowMs)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime, sharedAssets: Assets) {
        canvas.drawColor(renderParameters.highlightLayer!!.backgroundTint)
    }

    // ---- clock -----------------------------------------------------------------------------------
    private fun drawClock(c: Canvas, z: ZonedDateTime, fmt: Formatter, cx: Float, h: Float, noSeconds: Boolean,
                          mainColor: Int, subColor: Int, s: WatchSettings) {
        val mainSize = h * 0.172f
        val secSize = h * 0.060f
        val ampmSize = h * 0.046f
        val baseline = h * 0.318f
        val showSeconds = !noSeconds && s.showSeconds
        val main = fmt.clockMain(z)
        val ampm = fmt.ampm(z)
        val refMainW = width(stable(main), mainSize, light)
        val refSecW = if (showSeconds) width(stable(fmt.seconds(z)), secSize, regular) else 0f
        val refAmpmW = if (ampm.isEmpty()) 0f else maxOf(width("PM", ampmSize, regular), width("م", ampmSize, regular))
        val sideW = maxOf(refSecW, refAmpmW)
        val gap = if (sideW > 0f) h * 0.014f else 0f
        val total = refMainW + gap + sideW
        val left = cx - total / 2f
        text(c, main, left + refMainW, baseline, mainSize, mainColor, light, Paint.Align.RIGHT)
        val sideX = left + refMainW + gap
        if (showSeconds) text(c, fmt.seconds(z), sideX, baseline - mainSize * 0.43f, secSize, subColor, regular)
        if (ampm.isNotEmpty()) text(c, ampm, sideX, baseline, ampmSize, subColor, regular)
    }

    // ---- next prayer / countdown ----------------------------------------------------------------
    private fun drawPrayerBlock(
        c: Canvas, st: CountdownState?, hasPlace: Boolean, fmt: Formatter, strings: Strings, zone: ZoneId,
        cx: Float, h: Float, accent: Int, rtl: Boolean, ambient: Boolean, dimmed: (Int) -> Int
    ) {
        val headerY = h * 0.428f
        val lineY = h * 0.520f
        if (st == null) {
            if (!hasPlace) text(c, strings.setLocation, cx, lineY, h * 0.05f, dimmed(grey), medium, Paint.Align.CENTER)
            return
        }
        val header = if (st.phase == Phase.COUNTDOWN) strings.headerNext(st.prayer) else strings.headerNow(st.prayer)
        text(c, header, cx, headerY, h * 0.040f, dimmed(accent), medium, Paint.Align.CENTER)

        val adhanText = fmt.timeWithSuffix(st.adhan, zone)
        val counter = if (ambient) fmt.countdownAmbient(st.deltaSeconds) else fmt.countdown(st.deltaSeconds)
        val iconSize = h * 0.058f
        val timeSize = h * 0.046f
        val counterSize = h * 0.086f
        val gap = h * 0.022f
        val timeW = width(stable(adhanText), timeSize, regular)
        val counterW = width(stable(counter), counterSize, medium)
        val total = iconSize + gap + timeW + gap + counterW
        // LTR order: icon, time, counter.  RTL mirrors the whole group.
        var x = cx - total / 2f
        val slots = if (rtl) listOf("counter", "time", "icon") else listOf("icon", "time", "counter")
        for (slot in slots) {
            when (slot) {
                "icon" -> { icon(c, Icons.forPrayer(st.prayer), x + iconSize / 2f, lineY - h * 0.016f, iconSize, dimmed(accent)); x += iconSize + gap }
                "time" -> { text(c, adhanText, x, lineY, timeSize, dimmed(softWhite), regular); x += timeW + gap }
                else -> { text(c, counter, x, lineY, counterSize, dimmed(white), medium); x += counterW + gap }
            }
        }
    }

    // ---- schedule --------------------------------------------------------------------------------
    private fun drawSchedule(
        c: Canvas, today: DaySchedule?, st: CountdownState?, fmt: Formatter, strings: Strings, zone: ZoneId,
        cx: Float, h: Float, accent: Int, rtl: Boolean, ambient: Boolean, dimmed: (Int) -> Int
    ) {
        val order = listOf(Prayer.FAJR, Prayer.SHURUQ, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)
        val colW = h * 0.355f
        val colGap = h * 0.02f
        val nameSize = h * 0.038f
        val timeSize = h * 0.038f
        val iconSize = h * 0.050f
        val rowY = floatArrayOf(h * 0.650f, h * 0.722f, h * 0.794f)
        for ((i, prayer) in order.withIndex()) {
            val row = i / 2
            // reading order: first entry in the right column for Arabic, left column for English
            val col = if (rtl) 1 - (i % 2) else i % 2
            val x0 = if (col == 0) cx - colGap / 2f - colW else cx + colGap / 2f
            val x1 = x0 + colW
            val highlighted = st != null && prayer == st.nextPrayer
            val nameColor = dimmed(if (highlighted) accent else grey)
            val timeColor = dimmed(if (highlighted) accent else softWhite)
            val y = rowY[row]
            val t = today?.let { fmt.timeShort(it[prayer], zone) } ?: "--:--"
            val tf = if (highlighted) medium else regular
            if (!rtl) {
                icon(c, Icons.forPrayer(prayer), x0 + iconSize / 2f, y - h * 0.013f, iconSize, nameColor)
                text(c, strings.prayerName(prayer), x0 + iconSize + h * 0.012f, y, nameSize, nameColor, tf)
                text(c, t, x1, y, timeSize, timeColor, tf, Paint.Align.RIGHT)
            } else {
                icon(c, Icons.forPrayer(prayer), x1 - iconSize / 2f, y - h * 0.013f, iconSize, nameColor)
                text(c, strings.prayerName(prayer), x1 - iconSize - h * 0.012f, y, nameSize, nameColor, tf, Paint.Align.RIGHT)
                text(c, t, x0, y, timeSize, timeColor, tf, Paint.Align.LEFT)
            }
        }
    }

    // ---- bottom metrics --------------------------------------------------------------------------
    private fun drawMetrics(c: Canvas, batteryPct: Int?, fmt: Formatter, cx: Float, h: Float, rtl: Boolean, nowMs: Long) {
        val m = metrics
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val stepsFresh = m.steps != null && Instant.ofEpochMilli(m.stepsAtMs).atZone(zone).toLocalDate() == today
        val hrFresh = m.heartRate != null && nowMs - m.heartRateAtMs < 60 * 60_000L

        data class Item(val iconPath: String, val text: String, val color: Int, val isBattery: Boolean = false)
        val items = mutableListOf(
            Item(Icons.STEPS, m.steps?.let { fmt.steps(it) } ?: "--", if (stepsFresh) softWhite else dim),
            Item(Icons.HEART, m.heartRate?.let { fmt.plain(it) } ?: "--", if (hrFresh) softWhite else dim),
            Item(Icons.BATTERY, batteryPct?.let { fmt.percent(it) } ?: "--",
                if (batteryPct != null && batteryPct <= 15) warn else softWhite, true)
        )
        if (rtl) items.reverse()
        val size = h * 0.037f
        val iconSize = h * 0.048f
        val gapIcon = h * 0.008f
        val gapItem = h * 0.026f
        val widths = items.map { iconSize + gapIcon + width(stable(it.text), size, regular) }
        val total = widths.sum() + gapItem * (items.size - 1)
        var x = cx - total / 2f
        val y = h * 0.905f
        for ((i, item) in items.withIndex()) {
            if (item.isBattery) {
                iconPaint.isAntiAlias = aa; iconPaint.color = grey
                Icons.drawBattery(c, batteryPct, x + iconSize / 2f, y - h * 0.012f, iconSize, iconPaint, item.color)
            } else icon(c, item.iconPath, x + iconSize / 2f, y - h * 0.012f, iconSize, grey)
            text(c, item.text, x + iconSize + gapIcon, y, size, item.color, regular)
            x += widths[i] + gapItem
        }
    }

    private fun drawBatteryOnly(c: Canvas, pct: Int?, fmt: Formatter, cx: Float, h: Float, dimmed: (Int) -> Int) {
        val size = h * 0.036f
        val label = pct?.let { fmt.percent(it) } ?: "--"
        val iconSize = h * 0.046f
        val total = iconSize + h * 0.008f + width(stable(label), size, regular)
        val x = cx - total / 2f
        val y = h * 0.905f
        iconPaint.isAntiAlias = aa; iconPaint.color = dimmed(grey)
        Icons.drawBattery(c, pct, x + iconSize / 2f, y - h * 0.012f, iconSize, iconPaint, dimmed(grey))
        text(c, label, x + iconSize + h * 0.008f, y, size, dimmed(grey), regular)
    }
}
