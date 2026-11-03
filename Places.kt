package com.salahface.watch.core

/** Built-in manual locations (city-centre coordinates). First entry is the default. */
object Places {
    private fun uae(id: String, en: String, ar: String, lat: Double, lon: Double, elev: Double = 0.0) =
        Place(id, en, ar, lat, lon, elev, "Asia/Dubai")

    val all: List<Place> = listOf(
        uae("dubai", "Dubai", "دبي", 25.2048, 55.2708),
        uae("abudhabi", "Abu Dhabi", "أبوظبي", 24.4539, 54.3773),
        uae("sharjah", "Sharjah", "الشارقة", 25.3463, 55.4209),
        uae("ajman", "Ajman", "عجمان", 25.4052, 55.5136),
        uae("uaq", "Umm Al Quwain", "أم القيوين", 25.5647, 55.5552),
        uae("rak", "Ras Al Khaimah", "رأس الخيمة", 25.6741, 55.9804),
        uae("fujairah", "Fujairah", "الفجيرة", 25.1288, 56.3265),
        uae("alain", "Al Ain", "العين", 24.2075, 55.7447),
        uae("hatta", "Hatta", "حتا", 24.8000, 56.1167),
        Place("makkah", "Makkah", "مكة المكرمة", 21.3891, 39.8579, 0.0, "Asia/Riyadh"),
        Place("madinah", "Madinah", "المدينة المنورة", 24.5247, 39.5692, 0.0, "Asia/Riyadh"),
        Place("riyadh", "Riyadh", "الرياض", 24.7136, 46.6753, 0.0, "Asia/Riyadh"),
        Place("doha", "Doha", "الدوحة", 25.2854, 51.5310, 0.0, "Asia/Qatar"),
        Place("cairo", "Cairo", "القاهرة", 30.0444, 31.2357, 0.0, "Africa/Cairo"),
        Place("london", "London", "لندن", 51.5074, -0.1278, 0.0, "Europe/London")
    )

    val default: Place = all.first()
    fun byId(id: String?): Place = all.firstOrNull { it.id == id } ?: default
}
