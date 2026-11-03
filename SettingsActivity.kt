package com.salahface.watch.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.salahface.watch.core.*
import com.salahface.watch.data.*
import java.time.Instant

private enum class Screen { MAIN, LOCATION, PRAYER_TIMES, ABOUT }

class SettingsActivity : ComponentActivity() {
    private val permissionTick = mutableStateOf(0)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            HealthRegistration.register(this)
            permissionTick.value = permissionTick.value + 1
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore.get(this)
        setContent {
            SettingsApp(store, permissionTick.value) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION,
                        Manifest.permission.BODY_SENSORS
                    )
                )
            }
        }
    }
}

private fun granted(ctx: Context, permission: String) =
    ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun Entry(label: String, value: String?, valueColor: Color = Color.Unspecified, onClick: () -> Unit) {
    if (value != null) {
        Chip(
            onClick = onClick,
            label = { Text(label, maxLines = 2) },
            secondaryLabel = { Text(value, maxLines = 3, color = valueColor) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        Chip(
            onClick = onClick,
            label = { Text(label, maxLines = 2) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OffsetRow(title: String, offsetText: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(title, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onMinus, modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                colors = ButtonDefaults.secondaryButtonColors()) { Text("−") }
            Text(offsetText, textAlign = TextAlign.Center)
            Button(onClick = onPlus, modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                colors = ButtonDefaults.secondaryButtonColors()) { Text("+") }
        }
    }
}

@Composable
private fun SettingsApp(store: SettingsStore, permissionTick: Int, requestPermissions: () -> Unit) {
    val context = LocalContext.current
    var s by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf(Screen.MAIN) }
    var busy by remember { mutableStateOf(false) }
    fun update(f: (WatchSettings) -> WatchSettings) { store.update(f); s = store.load() }

    val ar = s.language == Language.AR
    fun tr(en: String, arText: String) = if (ar) arText else en
    fun num(v: String) = Numerals.localize(v, s.numerals)
    val strings = Strings(s.language)

    BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }

    fun agoText(ms: Long): String {
        val mins = ((System.currentTimeMillis() - ms) / 60_000L).coerceAtLeast(0)
        return when {
            mins < 60 -> num("$mins") + tr(" min ago", " د مضت")
            mins < 48 * 60 -> num("${mins / 60}") + tr(" h ago", " س مضت")
            else -> num("${mins / 1440}") + tr(" days ago", " يوم مضى")
        }
    }

    fun fetchLocation() {
        if (!LocationHelper.hasPermission(context)) { requestPermissions(); return }
        busy = true
        LocationHelper.requestFresh(context) { fix ->
            busy = false
            if (fix != null) update { it.copy(locationMode = LocationMode.AUTO, fix = fix, lastSyncMs = System.currentTimeMillis()) }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme {
            val listState = rememberScalingLazyListState()
            Scaffold(
                timeText = { TimeText() },
                vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
                positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
            ) {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (screen) {
                        Screen.MAIN -> {
                            item { Text(tr("Salah Face", "ساعة الصلاة"), textAlign = TextAlign.Center) }
                            item {
                                Entry(tr("Language", "اللغة"), if (ar) "العربية" else "English") {
                                    update { it.copy(language = if (it.language == Language.EN) Language.AR else Language.EN) }
                                }
                            }
                            item {
                                Entry(tr("Numerals", "الأرقام"),
                                    if (s.numerals == NumeralSystem.WESTERN) tr("Western  0 1 2 3", "غربية  0 1 2 3")
                                    else tr("Arabic-Indic  ٠ ١ ٢ ٣", "هندية  ٠ ١ ٢ ٣")) {
                                    update { it.copy(numerals = if (it.numerals == NumeralSystem.WESTERN) NumeralSystem.ARABIC_INDIC else NumeralSystem.WESTERN) }
                                }
                            }
                            item {
                                Entry(tr("Prayer times", "أوقات الصلاة"),
                                    tr("Reference: Dulook DXB · calculated on watch", "المرجع: دلوك دبي · تُحسب على الساعة")) {
                                    screen = Screen.PRAYER_TIMES
                                }
                            }
                            item {
                                val loc = when (s.locationMode) {
                                    LocationMode.MANUAL -> Places.byId(s.manualPlaceId).let { if (ar) it.nameAr else it.nameEn }
                                    LocationMode.AUTO -> s.fix?.let { tr("Automatic · ", "تلقائي · ") + agoText(it.epochMs) }
                                        ?: tr("Automatic · no fix yet", "تلقائي · لا يوجد موقع بعد")
                                }
                                Entry(tr("Location", "الموقع"), loc) { screen = Screen.LOCATION }
                            }
                            item {
                                Entry(tr("Clock format", "نظام الساعة"), if (s.clock24) tr("24-hour", "٢٤ ساعة") else tr("12-hour", "١٢ ساعة")) {
                                    update { it.copy(clock24 = !it.clock24) }
                                }
                            }
                            item {
                                Entry(tr("Accent colour", "لون التمييز"), if (ar) s.accent.nameAr else s.accent.nameEn, Color(s.accent.argb)) {
                                    update { it.copy(accent = Accent.values()[(it.accent.ordinal + 1) % Accent.values().size]) }
                                }
                            }
                            item {
                                Entry(tr("Seconds", "الثواني"), if (s.showSeconds) tr("Shown", "ظاهرة") else tr("Hidden", "مخفية")) {
                                    update { it.copy(showSeconds = !it.showSeconds) }
                                }
                            }
                            item {
                                Entry(tr("Steps · heart · battery", "الخطوات · النبض · البطارية"),
                                    if (s.showMetrics) tr("Shown", "ظاهرة") else tr("Hidden", "مخفية")) {
                                    update { it.copy(showMetrics = !it.showMetrics) }
                                }
                            }
                            item {
                                Entry(tr("Always-On: prayer list", "العرض الدائم: قائمة الصلوات"),
                                    if (s.aodShowSchedule) tr("Shown (uses more power)", "ظاهرة (طاقة أكثر)") else tr("Hidden (saves power)", "مخفية (توفر الطاقة)")) {
                                    update { it.copy(aodShowSchedule = !it.aodShowSchedule) }
                                }
                            }
                            item {
                                val status = remember(permissionTick) {
                                    val l = LocationHelper.hasPermission(context)
                                    val a = granted(context, Manifest.permission.ACTIVITY_RECOGNITION)
                                    val b = granted(context, Manifest.permission.BODY_SENSORS)
                                    fun mark(v: Boolean) = if (v) "✓" else "✗"
                                    tr("Location ${mark(l)}  Steps ${mark(a)}  Heart ${mark(b)}", "الموقع ${mark(l)}  الخطوات ${mark(a)}  النبض ${mark(b)}")
                                }
                                Entry(tr("Permissions", "الأذونات"), status) { requestPermissions() }
                            }
                            item { Entry(tr("About & accuracy", "حول التطبيق والدقة"), null) { screen = Screen.ABOUT } }
                        }

                        Screen.LOCATION -> {
                            item { Text(tr("Location", "الموقع"), textAlign = TextAlign.Center) }
                            item {
                                val value = when {
                                    busy -> tr("Locating…", "جارٍ تحديد الموقع…")
                                    s.locationMode == LocationMode.AUTO -> tr("On", "مفعّل")
                                    else -> tr("Off", "متوقف")
                                }
                                Entry(tr("Automatic (GPS)", "تلقائي (GPS)"), value) {
                                    if (s.locationMode == LocationMode.AUTO) update { it.copy(locationMode = LocationMode.MANUAL) }
                                    else { update { it.copy(locationMode = LocationMode.AUTO) }; fetchLocation() }
                                }
                            }
                            item { Entry(tr("Update location now", "تحديث الموقع الآن"), s.fix?.let { agoText(it.epochMs) }) { fetchLocation() } }
                            Places.all.forEach { p ->
                                item {
                                    val selected = s.locationMode == LocationMode.MANUAL && s.manualPlaceId == p.id
                                    Entry((if (selected) "✓ " else "") + (if (ar) p.nameAr else p.nameEn), null) {
                                        update { it.copy(locationMode = LocationMode.MANUAL, manualPlaceId = p.id) }
                                        screen = Screen.MAIN
                                    }
                                }
                            }
                        }

                        Screen.PRAYER_TIMES -> {
                            item { Text(tr("Prayer times", "أوقات الصلاة"), textAlign = TextAlign.Center) }
                            item {
                                Text(
                                    tr("Reference: Dulook DXB. Compare with the app and nudge any time by whole minutes until they match.",
                                        "المرجع: دلوك دبي. قارن مع التطبيق وعدّل أي وقت بالدقائق حتى يتطابق."),
                                    textAlign = TextAlign.Center
                                )
                            }
                            val place = s.place()
                            val day = place?.let { ScheduleProvider().today(Instant.now(), it, it.zone(), s.profile()) }
                            val fmt = Formatter(s.language, s.numerals, s.clock24)
                            for (p in Prayer.values()) {
                                item {
                                    val off = s.offsets[p] ?: 0
                                    val time = if (day != null && place != null) fmt.timeShort(day[p], place.zone()) else "--:--"
                                    OffsetRow(
                                        title = "${strings.prayerName(p)}  $time",
                                        offsetText = num(if (off > 0) "+$off" else "$off") + tr(" min", " د"),
                                        onMinus = { update { it.copy(offsets = it.offsets + (p to ((it.offsets[p] ?: 0) - 1).coerceIn(-15, 15))) } },
                                        onPlus = { update { it.copy(offsets = it.offsets + (p to ((it.offsets[p] ?: 0) + 1).coerceIn(-15, 15))) } }
                                    )
                                }
                            }
                            item {
                                Entry(tr("Altitude correction", "تصحيح الارتفاع"), if (s.useElevation) tr("On", "مفعّل") else tr("Off", "متوقف")) {
                                    update { it.copy(useElevation = !it.useElevation) }
                                }
                            }
                            item { Entry(tr("Reset adjustments", "إعادة الضبط"), null) { update { it.copy(offsets = emptyMap(), useElevation = false) } } }
                            item {
                                Entry(tr("Sync now", "مزامنة الآن"),
                                    if (s.lastSyncMs > 0) tr("Last: ", "آخر مرة: ") + agoText(s.lastSyncMs) else tr("Recalculates and refreshes location", "يعيد الحساب ويحدّث الموقع")) {
                                    update { it.copy(lastSyncMs = System.currentTimeMillis()) }
                                    if (s.locationMode == LocationMode.AUTO) fetchLocation()
                                }
                            }
                        }

                        Screen.ABOUT -> {
                            item { Text(tr("About & accuracy", "حول التطبيق والدقة"), textAlign = TextAlign.Center) }
                            item {
                                Text(
                                    tr("Prayer times are calculated on this watch (18.2° Fajr and Isha, standard Asr, +3 min on Dhuhr, Asr and Maghrib, −3 min on Shuruq). " +
                                        "They are an approximation of the UAE timetable and have not been verified against Dulook DXB: use the adjustments to match it. " +
                                        "Not affiliated with IACAD or Dulook DXB.",
                                        "تُحسب الأوقات على الساعة (الفجر والعشاء ١٨٫٢°، العصر القياسي، ‎+٣ دقائق للظهر والعصر والمغرب، ‎−٣ للشروق). " +
                                            "هي تقريب لتقويم الإمارات ولم يتم التحقق منها مع تطبيق دلوك دبي، فاستخدم التعديلات للمطابقة. " +
                                            "التطبيق غير تابع للدائرة أو لتطبيق دلوك."),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
