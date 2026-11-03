package com.salahface.watch.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.function.Consumer

object LocationHelper {
    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun toFix(l: Location) = GpsFix(l.latitude, l.longitude, if (l.hasAltitude()) l.altitude else 0.0, l.time)

    /** Newest cached location from any provider, or null. Never prompts and never powers up the GPS. */
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): GpsFix? {
        if (!hasPermission(ctx)) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let(::toFix)
        } catch (_: Exception) { null }
    }

    /** Requests one fresh fix (a few seconds to about a minute). Calls [done] on the main thread. */
    @SuppressLint("MissingPermission")
    fun requestFresh(ctx: Context, done: (GpsFix?) -> Unit) {
        if (!hasPermission(ctx)) { done(null); return }
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val enabled = lm.getProviders(true)
            val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .firstOrNull { it in enabled }
            if (provider == null) { done(lastKnown(ctx)); return }
            lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(ctx), Consumer<Location?> { loc ->
                done(loc?.let(::toFix) ?: lastKnown(ctx))
            })
        } catch (_: Exception) { done(lastKnown(ctx)) }
    }
}
