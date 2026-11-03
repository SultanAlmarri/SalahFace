# Salah Face — minimalist Islamic digital watch face for Galaxy Watch5 Pro (Wear OS)

Large 12-hour digital clock with seconds and AM/PM, six prayer entries (Fajr, Shuruq, Dhuhr, Asr, Maghrib, Isha)
with line icons, an automatic countdown (`-9:05`, `-1:9:05`) that flips to elapsed time (`+5:00`) at the Adhan,
steps / heart rate / battery, English or Arabic, Western or Arabic-Indic digits chosen independently,
and an Always-On Display mode.

## Honest status (please read)

| Item | Status |
|---|---|
| Countdown formatting, 30-minute switch, midnight, Shuruq exclusion, restart safety, numerals, AM/PM, date | **Unit-tested** (14 tests, run on a JVM; they pass) |
| Prayer-time calculation | **Cross-checked** against the open-source Adhan algorithm (Python port) for 7 places × every day of 2026 = 15,330 values: all identical |
| Match with **Dulook DXB** | **NOT verified.** No public Dulook API was found and scraping is not used. See "Matching Dulook" below |
| Android code (watch face, settings, health, location) | **Written but never compiled or run on a watch.** I had no Android build tools or Galaxy Watch5 Pro. The first cloud build may report compile errors; send me the log and I will fix them |
| Real steps / heart rate / battery | Implemented through Health Services and the battery broadcast; **untested on hardware** |
| Always-On Display, battery drain, Arabic rendering on the watch | **Untested on hardware** (layout previews in `docs/` were drawn with Pillow, they are not watch screenshots) |
| Compiled APK | **Not included** — it has to be built (cloud build below, no programming needed) |

## Install without Android Studio (beginner guide)

### Part A — get the APK (cloud build, about 5 minutes, free)
1. Create a free account at github.com and click **New repository** (name it `SalahFace`, keep it Private if you like).
2. Unzip the source ZIP on your computer. On the new repository page click **uploading an existing file** and drag in
   **everything inside the unzipped folder** (including the hidden `.github` folder and the `gradle` folder), then **Commit changes**.
   Tip: if your system hides `.github`, use GitHub Desktop or `git push` instead.
3. Open the **Actions** tab → **Build Salah Face APK** → wait for the green tick.
   (If the run is red, open it, copy the red error text and send it to me.)
4. Open the finished run → **Artifacts** → download **SalahFace-debug-apk** → unzip to get `SalahFace-debug.apk`.
   For a permanent download link, create a Release: **Releases → Create a new release → tag `v1.0.0`**; the workflow attaches the APK.

### Part B — prepare the Galaxy Watch5 Pro
1. Watch: **Settings → About watch → Software** → tap **Software version** 5 times → "Developer mode turned on".
2. **Settings → Developer options** → turn on **ADB debugging** and **Wireless debugging** (the watch and your computer must be on the same Wi-Fi).
3. Tap **Wireless debugging → Pair new device**. Leave the screen showing the pairing code and the IP address and port.

### Part C — install from your computer
1. Download Google's **Platform Tools** (search "Android SDK Platform Tools", it is a small ZIP with `adb` inside) and unzip it.
2. Open a terminal / Command Prompt in that folder and run (use the values shown on the watch):
   ```
   adb pair 192.168.1.50:37000        (type the 6-digit pairing code when asked)
   adb connect 192.168.1.50:41234     (the port shown on the main Wireless debugging screen)
   adb install -r SalahFace-debug.apk (put the APK in the same folder or give its full path)
   ```
   You should see `Success`.

### Part D — activate and set up
1. On the watch, press and hold the current watch face → swipe to the end → **+** / **Add watch face** (or Galaxy Wearable phone app → Watch faces → Downloaded) → choose **Salah Face**.
2. Open the **Salah Settings** app from the watch app list.
3. **Permissions** chip → allow Location, Physical activity (steps) and Body sensors (heart rate).
4. **Language**: tap to switch English ⇄ العربية. **Numerals**: tap to switch Western ⇄ Arabic-Indic. They are independent and apply immediately.
5. **Location**: Dubai is the default. Choose another UAE city, or turn on **Automatic (GPS)** and tap **Update location now**.
6. **Prayer times** → compare with Dulook DXB and adjust (next section).

## Matching Dulook DXB

Dulook DXB is published by Dubai's Islamic Affairs and Charitable Activities Department (IACAD). I found no public
API or developer terms, so this project does not scrape it and does not claim an exact match. Prayer times are
calculated on the watch (works offline, survives restarts) with explicit, visible parameters:

* Fajr and Isha at 18.2° below the horizon, standard (Shafi'i) Asr shadow, sunrise/sunset at −0.833°.
* Method adjustments: Dhuhr +3 min, Asr +3 min, Maghrib +3 min, Shuruq −3 min (the common "Dubai/Gulf" profile).
* Times are rounded to the nearest whole minute (30 s rounds up).
* Optional altitude correction (off by default). Dulook's own altitude handling is unknown to me.

How to align it: open `docs/DUBAI_CALCULATED_TIMES.md`, compare with Dulook DXB for the same dates, then use
**Settings → Prayer times → − / +** to move a prayer by whole minutes. If the gap is more than a few minutes or varies
through the year, fill `docs/validation_template.csv` and send it to me: I can then fit the correct angles/offsets.
Official reference to ask for authorised access: IACAD (iacad.gov.ae).

## Countdown rules (implemented in `core/Countdown.kt`)

`M:SS` below one hour, `H:M:SS` from one hour, hours/minutes never zero-padded, seconds always two digits,
`-` before the Adhan and `+` after it; switch to `+0:00` exactly at the Adhan; after 30:00 elapsed the next obligatory prayer's
countdown starts. Shuruq is never part of this logic. The state is a pure function of the current time, so restarts,
midnight, time-zone and location changes need no stored state. In Always-On mode (one redraw per minute) the countdown is
shown in whole minutes (`-9m`, `-1h 9m`, Arabic `-٩د`).

Assumption to review: while the `+` counter runs, the header reads **NOW: ASR / الآن: العصر** instead of "NEXT PRAYER".

## Known limitations
* Background location: Wear OS blocks background GPS for most apps, so in GPS mode the location updates when you open Settings → Update location (or when the system has a newer cached fix). Travelling? Tap **Update location now**.
* Heart rate comes from the platform's passive monitoring: it is sampled when the system decides, not continuously (saves battery). Old readings (>1 h) and yesterday's steps are shown dimmed; `--` means no reading yet.
* The "Customize" button on the watch-face picker may or may not open the settings on your One UI version; the **Salah Settings** app always does.
* Arabic digit separator `٬` falls back to a system font (Tajawal has no glyph for it).
* Accent colours are a fixed set of five (cycle by tapping), not a free colour picker.

## Troubleshooting
* **Actions run is red:** open the run, copy the red lines, send them to me. Most likely a library signature mismatch.
* **`adb: no devices`:** same Wi-Fi? Re-do *Pair new device* (the code changes every time); use the port from the main Wireless debugging screen for `connect`.
* **`INSTALL_FAILED_...`:** uninstall any older copy (`adb uninstall com.salahface.watch`) and retry.
* **Face not in the list:** reboot the watch; confirm `adb shell pm list packages | grep salahface`.
* **Prayer area says SET LOCATION:** GPS mode has no fix yet → Settings → Location → Update location now (or pick a city).
* **Steps or heart rate show `--`:** grant Physical activity / Body sensors in Settings → Permissions; allow a few minutes for the first sample.
* **Wrong times:** check the location and the time zone of the watch; then use the minute adjustments.

## Project layout
```
app/src/main/java/com/salahface/watch/
  core/      pure Kotlin: calculator (Astro.kt, PrayerCalculator.kt), countdown, numerals, strings, places  (tested)
  data/      settings, Health Services passive listener, location helper
  face/      SalahWatchFaceService, SalahRenderer (Canvas), Icons
  settings/  Wear Compose settings screen
tools/       icon/preview renderers, local-check/run.sh (tests + cross-check without Android)
docs/        Dubai calculated times, validation template, layout previews
```
Run the JVM checks yourself: `tools/local-check/run.sh` (needs `kotlinc`, `java`, `pip install adhanpy`).

## Licences
Source: MIT (see `LICENSE`). Font: Tajawal, SIL Open Font License 1.1 (`licenses/Tajawal-OFL.txt`).
Not affiliated with IACAD or Dulook DXB.
