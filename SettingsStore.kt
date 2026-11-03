package com.salahface.watch.data

import android.content.Context
import android.content.SharedPreferences
import com.salahface.watch.core.*
import java.time.ZoneId

/** Last location fix taken while the user had GPS mode on. */
data class GpsFix(val latitude: Double, val longitude: Double, val altitudeM: Double, val epochMs: Long)

data class WatchSettings(
    val language: Language = Language.EN,
    val numerals: NumeralSystem = NumeralSystem.WESTERN,
    val clock24: Boolean = false,
    val accent: Accent = Accent.GREEN,
    val showSeconds: Boolean = true,
    val showMetrics: Boolean = true,
    val locationMode: LocationMode = LocationMode.MANUAL,
    val manualPlaceId: String = Places.default.id,
    val fix: GpsFix? = null,
    val offsets: Map<Prayer, Int> = emptyMap(),
    val useElevation: Boolean = false,
    val aodShowSchedule: Boolean = false,
    val lastSyncMs: Long = 0L
) {
    /** The place prayer times are calculated for, or null when GPS mode has no fix yet. */
    fun place(): Place? = when (locationMode) {
        LocationMode.MANUAL -> Places.byId(manualPlaceId)
        LocationMode.AUTO -> fix?.let {
            Place("gps", "Current location", "الموقع الحالي", it.latitude, it.longitude,
                it.altitudeM.coerceAtLeast(0.0), ZoneId.systemDefault().id)
        }
    }

    fun profile(): CalcProfile {
        val elevation = if (useElevation) (place()?.elevationM ?: 0.0) else 0.0
        return CalcProfile(userOffsets = offsets.filterValues { it != 0 }, elevationM = elevation)
    }

    /** GPS mode and the last fix is older than three days. */
    fun locationOutdated(nowMs: Long): Boolean =
        locationMode == LocationMode.AUTO && fix != null && nowMs - fix.epochMs > 3L * 24 * 3600 * 1000
}

class SettingsStore private constructor(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("salah_settings", Context.MODE_PRIVATE)

    fun load(): WatchSettings {
        val p = prefs
        val fix = if (p.contains("fix_lat")) GpsFix(
            java.lang.Double.longBitsToDouble(p.getLong("fix_lat", 0)),
            java.lang.Double.longBitsToDouble(p.getLong("fix_lon", 0)),
            java.lang.Double.longBitsToDouble(p.getLong("fix_alt", 0)),
            p.getLong("fix_time", 0)
        ) else null
        val offsets = HashMap<Prayer, Int>()
        for (pr in Prayer.values()) p.getInt("off_${pr.name}", 0).takeIf { it != 0 }?.let { offsets[pr] = it }
        return WatchSettings(
            language = enumOf(p.getString("language", null), Language.EN),
            numerals = enumOf(p.getString("numerals", null), NumeralSystem.WESTERN),
            clock24 = p.getBoolean("clock24", false),
            accent = enumOf(p.getString("accent", null), Accent.GREEN),
            showSeconds = p.getBoolean("showSeconds", true),
            showMetrics = p.getBoolean("showMetrics", true),
            locationMode = enumOf(p.getString("locationMode", null), LocationMode.MANUAL),
            manualPlaceId = p.getString("manualPlaceId", Places.default.id) ?: Places.default.id,
            fix = fix,
            offsets = offsets,
            useElevation = p.getBoolean("useElevation", false),
            aodShowSchedule = p.getBoolean("aodShowSchedule", false),
            lastSyncMs = p.getLong("lastSyncMs", 0)
        )
    }

    fun update(block: (WatchSettings) -> WatchSettings) = save(block(load()))

    private fun save(s: WatchSettings) {
        val e = prefs.edit()
        e.putString("language", s.language.name)
        e.putString("numerals", s.numerals.name)
        e.putBoolean("clock24", s.clock24)
        e.putString("accent", s.accent.name)
        e.putBoolean("showSeconds", s.showSeconds)
        e.putBoolean("showMetrics", s.showMetrics)
        e.putString("locationMode", s.locationMode.name)
        e.putString("manualPlaceId", s.manualPlaceId)
        s.fix?.let {
            e.putLong("fix_lat", java.lang.Double.doubleToRawLongBits(it.latitude))
            e.putLong("fix_lon", java.lang.Double.doubleToRawLongBits(it.longitude))
            e.putLong("fix_alt", java.lang.Double.doubleToRawLongBits(it.altitudeM))
            e.putLong("fix_time", it.epochMs)
        }
        for (pr in Prayer.values()) e.putInt("off_${pr.name}", s.offsets[pr] ?: 0)
        e.putBoolean("useElevation", s.useElevation)
        e.putBoolean("aodShowSchedule", s.aodShowSchedule)
        e.putLong("lastSyncMs", s.lastSyncMs)
        e.apply()
    }

    companion object {
        @Volatile private var instance: SettingsStore? = null
        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) { instance ?: SettingsStore(context).also { instance = it } }

        private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
