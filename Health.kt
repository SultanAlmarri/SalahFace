package com.salahface.watch.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/** Latest readings written by the passive Health Services listener. Nothing here is ever simulated. */
data class Metrics(val steps: Long?, val stepsAtMs: Long, val heartRate: Int?, val heartRateAtMs: Long)

class MetricsStore private constructor(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("salah_metrics", Context.MODE_PRIVATE)

    fun load(): Metrics = Metrics(
        steps = if (prefs.contains("steps")) prefs.getLong("steps", 0) else null,
        stepsAtMs = prefs.getLong("steps_at", 0),
        heartRate = if (prefs.contains("hr")) prefs.getInt("hr", 0) else null,
        heartRateAtMs = prefs.getLong("hr_at", 0)
    )

    fun saveSteps(total: Long) =
        prefs.edit().putLong("steps", total).putLong("steps_at", System.currentTimeMillis()).apply()

    fun saveHeartRate(bpm: Int) =
        prefs.edit().putInt("hr", bpm).putLong("hr_at", System.currentTimeMillis()).apply()

    companion object {
        @Volatile private var instance: MetricsStore? = null
        fun get(context: Context): MetricsStore =
            instance ?: synchronized(this) { instance ?: MetricsStore(context).also { instance = it } }
    }
}

/**
 * Receives daily steps and heart-rate samples from the platform's passive monitoring.
 * The system decides when to sample, so there is no continuous sensor use by this app.
 */
class HealthPassiveService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val store = MetricsStore.get(this)
        dataPoints.getData(DataType.STEPS_DAILY)?.let { store.saveSteps(it.total) }
        dataPoints.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { store.saveHeartRate(it.value.toInt()) }
    }
}

object HealthRegistration {
    private fun granted(ctx: Context, permission: String) =
        ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

    /** Safe to call repeatedly; registers only for data types whose permission has been granted. */
    fun register(context: Context) {
        try {
            val types = mutableSetOf<DataType<*, *>>()
            if (granted(context, Manifest.permission.ACTIVITY_RECOGNITION)) types.add(DataType.STEPS_DAILY)
            if (granted(context, Manifest.permission.BODY_SENSORS)) types.add(DataType.HEART_RATE_BPM)
            if (types.isEmpty()) return
            val config = PassiveListenerConfig.builder().setDataTypes(types).build()
            HealthServices.getClient(context.applicationContext).passiveMonitoringClient
                .setPassiveListenerServiceAsync(HealthPassiveService::class.java, config)
        } catch (_: Exception) {
            // Health Services unavailable or a permission was revoked: readings simply show as unavailable.
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) HealthRegistration.register(context)
    }
}
