package com.salahface.watch.face

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.PathParser
import com.salahface.watch.core.Prayer

/**
 * Minimal line icons drawn from SVG path data on a 24x24 grid. The same strings are rendered by
 * tools/render_icons.py so the artwork can be reviewed without a watch.
 */
object Icons {
    const val FAJR = "M3 16.5h18 M8 16.5a4 4 0 0 1 8 0 M7 20.5h10 M5.5 8h.01 M18.5 9.5h.01 M12 6h.01"
    const val SHURUQ = "M3 18h18 M7 18a5 5 0 0 1 10 0 M12 5v3 M5.2 9.7l2 2 M18.8 9.7l-2 2"
    const val DHUHR = "M12 8.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 1 0 0-7z M12 2.5v2.5 M12 19v2.5 M2.5 12h2.5 M19 12h2.5 M5.3 5.3l1.8 1.8 M16.9 16.9l1.8 1.8 M5.3 18.7l1.8-1.8 M16.9 7.1l1.8-1.8"
    const val ASR = "M12 7.8a3.2 3.2 0 1 0 0 6.4 3.2 3.2 0 1 0 0-6.4z M12 2.5v2 M5.5 5.2l1.4 1.4 M18.5 5.2l-1.4 1.4 M3 11h2 M19 11h2 M3 20h18"
    const val MAGHRIB = "M3 17h18 M7.5 17a4.5 4.5 0 0 1 9 0 M12 4.5v6 M9.5 8l2.5 2.5L14.5 8 M6 20.5h12"
    const val ISHA = "M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10z"
    const val STEPS = "M8.5 3c1.9 0 3 2 3 4.3 0 2.4-1.1 4.2-3 4.2s-3-1.8-3-4.2C5.5 5 6.6 3 8.5 3z M8.5 13.2a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 1 0 0-3.2z M15.5 8c1.9 0 3 2 3 4.3 0 2.4-1.1 4.2-3 4.2s-3-1.8-3-4.2c0-2.3 1.1-4.3 3-4.3z M15.5 18.2a1.6 1.6 0 1 0 0 3.2 1.6 1.6 0 1 0 0-3.2z"
    const val HEART = "M12 20.2C6 16 3.2 12.6 3.2 9a4.6 4.6 0 0 1 8.8-1.9A4.6 4.6 0 0 1 20.8 9c0 3.6-2.8 7-8.8 11.2z"
    const val BATTERY = "M5 8h12.5a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5v-5A1.5 1.5 0 0 1 5 8z M21 11v2"

    private val cache = HashMap<String, Path>()
    private fun path(d: String): Path = cache.getOrPut(d) { PathParser.createPathFromPathData(d) }

    fun forPrayer(p: Prayer): String = when (p) {
        Prayer.FAJR -> FAJR
        Prayer.SHURUQ -> SHURUQ
        Prayer.DHUHR -> DHUHR
        Prayer.ASR -> ASR
        Prayer.MAGHRIB -> MAGHRIB
        Prayer.ISHA -> ISHA
    }

    /** Draws [d] centred on (cx, cy) in a square of [size] px using [paint]'s colour. */
    fun draw(canvas: Canvas, d: String, cx: Float, cy: Float, size: Float, paint: Paint) {
        val oldStyle = paint.style; val oldWidth = paint.strokeWidth; val oldCap = paint.strokeCap; val oldJoin = paint.strokeJoin
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.7f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path(d), paint)
        canvas.restore()
        paint.style = oldStyle; paint.strokeWidth = oldWidth; paint.strokeCap = oldCap; paint.strokeJoin = oldJoin
    }

    /** Battery outline plus a filled level bar (0..100). */
    fun drawBattery(canvas: Canvas, percent: Int?, cx: Float, cy: Float, size: Float, paint: Paint, fillColor: Int) {
        draw(canvas, BATTERY, cx, cy, size, paint)
        if (percent == null) return
        val oldStyle = paint.style; val oldColor = paint.color
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        val w = 12.6f * percent.coerceIn(0, 100) / 100f
        if (w > 0.3f) canvas.drawRoundRect(5.2f, 9.7f, 5.2f + w, 14.3f, 0.6f, 0.6f, paint)
        canvas.restore()
        paint.style = oldStyle; paint.color = oldColor
    }
}
