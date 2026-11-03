#!/usr/bin/env bash
# Runs the pure-Kotlin core tests and the cross-check against the open-source Adhan reference
# WITHOUT Android. Needs: java 17+, kotlinc (https://github.com/JetBrains/kotlin/releases), python3 + `pip install adhanpy`.
set -euo pipefail
cd "$(dirname "$0")/../.."
CORE=$(find app/src/main/java/com/salahface/watch/core -name '*.kt')
OUT=$(mktemp -d)
echo "== unit tests =="
kotlinc $CORE app/src/test/java/com/salahface/watch/core/CoreTests.kt tools/local-check/shim/org/junit/*.kt tools/local-check/Runner.kt -include-runtime -d "$OUT/tests.jar" 2>/dev/null
java -cp "$OUT/tests.jar" RunnerKt
echo "== cross-check vs adhanpy (rounding of exact 30 s ties differs by design, see README) =="
kotlinc $CORE tools/local-check/CrossCheck.kt -include-runtime -d "$OUT/cc.jar" 2>/dev/null
java -jar "$OUT/cc.jar" > /tmp/salah_kt_out.txt
python3 - <<'PY'
import adhanpy.PrayerTimes as PT
from datetime import datetime, timedelta
def half_up(w):  # Java Adhan rounds 30 s up; adhanpy rounds 30 s down
    b = w.replace(second=0, microsecond=0); return b + timedelta(minutes=1) if w.second >= 30 else b
PT.rounded_minute = half_up
exec(open("tools/local-check/compare_with_adhanpy.py").read())
PY
