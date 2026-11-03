import datetime as dt
from zoneinfo import ZoneInfo
from adhanpy.PrayerTimes import PrayerTimes
from adhanpy.calculation.CalculationMethod import CalculationMethod
coords = {"dubai":(25.2048,55.2708),"abudhabi":(24.4539,54.3773),"fujairah":(25.1288,56.3265),"alain":(24.2075,55.7447),
          "makkah":(21.3891,39.8579),"cairo":(30.0444,31.2357),"london":(51.5074,-0.1278)}
names = ["fajr","sunrise","dhuhr","asr","maghrib","isha"]
stats = {}
worst = {}
for line in open("/tmp/salah_kt_out.txt"):
    p = line.split(); pid, d = p[0], dt.date.fromisoformat(p[1]); mine = list(map(int,p[2:]))
    pt = PrayerTimes(coords[pid], dt.datetime(d.year,d.month,d.day,tzinfo=dt.timezone.utc), calculation_method=CalculationMethod.DUBAI)
    ref = [getattr(pt,n) for n in names]
    for n,m,r in zip(names,mine,ref):
        diff = m - int(r.timestamp())
        s = stats.setdefault(pid,{"n":0,"exact":0,"off60":0,"bad":0})
        s["n"]+=1
        if diff==0: s["exact"]+=1
        elif abs(diff)==60: s["off60"]+=1
        else:
            s["bad"]+=1; worst.setdefault(pid,[]).append((p[1],n,diff))
for k,v in stats.items(): print(k, v)
print({k:v[:4] for k,v in worst.items()})
