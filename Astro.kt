package com.salahface.watch.core

import kotlin.math.*

/**
 * Astronomical routines (Meeus, "Astronomical Algorithms") for sunrise, transit and twilight angles.
 * Structure follows the open-source Adhan library so results can be cross-checked against it.
 */
internal object Astro {
    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    fun unwind(a: Double): Double = a - 360.0 * floor(a / 360.0)
    private fun normalize(n: Double, max: Double) = n - max * floor(n / max)
    private fun closestAngle(a: Double): Double =
        if (a >= -180.0 && a <= 180.0) a else a - 360.0 * Math.round(a / 360.0)

    fun julianDay(year: Int, month: Int, day: Int, hours: Double = 0.0): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val i0 = floor(365.25 * (y + 4716))
        val i1 = floor(30.6001 * (m + 1))
        return i0 + i1 + day + b - 1524.5 + hours / 24.0
    }

    class SolarCoordinates(jd: Double) {
        val declination: Double
        val rightAscension: Double
        val apparentSiderealTime: Double

        init {
            val t = (jd - 2451545.0) / 36525.0
            val l0 = unwind(280.4664567 + 36000.76983 * t + 0.0003032 * t * t)
            val lp = unwind(218.3165 + 481267.8813 * t)
            val omega = unwind(125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000.0)
            val mAnom = unwind(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
            val mRad = rad(mAnom)
            val center = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
                (0.019993 - 0.000101 * t) * sin(2 * mRad) + 0.000289 * sin(3 * mRad)
            val omega2 = 125.04 - 1934.136 * t
            val lambda = rad(unwind(l0 + center - 0.00569 - 0.00478 * sin(rad(omega2))))
            val theta0 = unwind(
                280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t - t * t * t / 38710000.0
            )
            val dPsi = (-17.2 / 3600) * sin(rad(omega)) - (1.32 / 3600) * sin(2 * rad(l0)) -
                (0.23 / 3600) * sin(2 * rad(lp)) + (0.21 / 3600) * sin(2 * rad(omega))
            val dEps = (9.2 / 3600) * cos(rad(omega)) + (0.57 / 3600) * cos(2 * rad(l0)) +
                (0.10 / 3600) * cos(2 * rad(lp)) - (0.09 / 3600) * cos(2 * rad(omega))
            val eps0 = 23.439291 - 0.013004167 * t - 0.0000001639 * t * t + 0.0000005036 * t * t * t
            val epsApp = rad(eps0 + 0.00256 * cos(rad(omega2)))
            declination = deg(asin(sin(epsApp) * sin(lambda)))
            rightAscension = unwind(deg(atan2(cos(epsApp) * sin(lambda), cos(lambda))))
            apparentSiderealTime = theta0 + (dPsi * 3600 * cos(rad(eps0 + dEps))) / 3600
        }
    }

    private fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = y2 - y1; val b = y3 - y2; val c = b - a
        return y2 + (n / 2) * (a + b + n * c)
    }

    private fun interpolateAngles(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = unwind(y2 - y1); val b = unwind(y3 - y2); val c = b - a
        return y2 + (n / 2) * (a + b + n * c)
    }

    /** Solar events for one UT calendar day at one place; all results are UT hours (may fall outside 0..24). */
    class SolarTime(year: Int, month: Int, day: Int, private val lat: Double, private val lon: Double, horizonDipDeg: Double) {
        private val jd = julianDay(year, month, day)
        private val prev = SolarCoordinates(jd - 1)
        private val cur = SolarCoordinates(jd)
        private val next = SolarCoordinates(jd + 1)
        private val m0 = normalize((cur.rightAscension - lon - cur.apparentSiderealTime) / 360.0, 1.0)

        val transit: Double
        val sunrise: Double
        val sunset: Double

        init {
            val theta = unwind(cur.apparentSiderealTime + 360.985647 * m0)
            val alpha = unwind(interpolateAngles(cur.rightAscension, prev.rightAscension, next.rightAscension, m0))
            val h = closestAngle(theta + lon - alpha)
            transit = (m0 + h / -360.0) * 24.0
            val horizon = -50.0 / 60.0 - horizonDipDeg
            sunrise = hourAngle(horizon, false)
            sunset = hourAngle(horizon, true)
        }

        fun hourAngle(angle: Double, afterTransit: Boolean): Double {
            val term1 = sin(rad(angle)) - sin(rad(lat)) * sin(rad(cur.declination))
            val term2 = cos(rad(lat)) * cos(rad(cur.declination))
            val ratio = term1 / term2
            if (ratio.isNaN() || ratio > 1.0 || ratio < -1.0) return Double.NaN
            val h0 = deg(acos(ratio))
            val m = if (afterTransit) m0 + h0 / 360.0 else m0 - h0 / 360.0
            val theta = unwind(cur.apparentSiderealTime + 360.985647 * m)
            val alpha = unwind(interpolateAngles(cur.rightAscension, prev.rightAscension, next.rightAscension, m))
            val delta = interpolate(cur.declination, prev.declination, next.declination, m)
            val hh = theta + lon - alpha
            val alt = deg(asin(sin(rad(lat)) * sin(rad(delta)) + cos(rad(lat)) * cos(rad(delta)) * cos(rad(hh))))
            val dm = (alt - angle) / (360.0 * cos(rad(delta)) * cos(rad(lat)) * sin(rad(hh)))
            return (m + dm) * 24.0
        }

        /** Asr: time when an object's shadow equals [shadowFactor] times its height plus the noon shadow. */
        fun afternoon(shadowFactor: Double): Double {
            val tangent = abs(lat - cur.declination)
            val inverse = shadowFactor + tan(rad(tangent))
            return hourAngle(deg(atan(1.0 / inverse)), true)
        }
    }
}
