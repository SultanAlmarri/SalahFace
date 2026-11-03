import com.salahface.watch.core.*
import java.time.LocalDate
import java.time.ZoneId

fun main() {
    val profile = CalcProfile()
    for (id in listOf("dubai", "abudhabi", "fujairah", "alain", "makkah", "cairo", "london")) {
        val place = Places.byId(id)
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            val s = PrayerCalculator.compute(d, place, place.zone(), profile)
            println("$id $d " + Prayer.values().joinToString(" ") { s[it].epochSecond.toString() })
            d = d.plusDays(1)
        }
    }
}
