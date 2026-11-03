"""Illustrative render of the face layout (same fractions as SalahRenderer.kt) -> PNG.
This is a design preview made with Pillow, NOT a screenshot from a watch.
usage: python3 tools/render_preview.py out.png [en|ar] [ambient]"""
import sys, io, re, cairosvg
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]; LANG = sys.argv[2] if len(sys.argv) > 2 else "en"; AMBIENT = "ambient" in sys.argv
S = 450; K = 2  # supersample
H = S * K
R = "app/src/main/res/font/"
F = {w: R + f"tajawal_{w}.ttf" for w in ("light", "regular", "medium")}
src = open("app/src/main/java/com/salahface/watch/face/Icons.kt", encoding="utf-8").read()
ICON = dict(re.findall(r'const val (\w+) = "([^"]+)"', src))
ar = LANG == "ar"
dig = (lambda s: s.translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))) if ar else (lambda s: s)
ACCENT = (95, 211, 141); GREY = (154, 156, 161); SOFT = (215, 216, 219); WHITE = (255, 255, 255)
if AMBIENT:
    k = 0.72; f = lambda c: tuple(int(v * k) for v in c); ACCENT, GREY, SOFT, WHITE = map(f, (ACCENT, GREY, SOFT, WHITE))
img = Image.new("RGB", (H, H), "black"); d = ImageDraw.Draw(img)
lay = ImageFont.Layout.RAQM if True else None
def font(w, size):
    try: return ImageFont.truetype(F[w], int(size * K), layout_engine=ImageFont.Layout.RAQM)
    except Exception: return ImageFont.truetype(F[w], int(size * K))
def tw(t, fnt): return d.textlength(t, font=fnt)
def text(t, x, y, size, col, w="regular", align="l"):
    fnt = font(w, size); x *= K; y *= K
    if align == "c": x -= tw(t, fnt) / 2
    elif align == "r": x -= tw(t, fnt)
    d.text((x, y), t, font=fnt, fill=col, anchor="ls")
def icon(name, cx, cy, size, col, fillpct=None):
    hexc = "#%02x%02x%02x" % col
    extra = f'<rect x="5.2" y="9.7" width="{12.6*fillpct/100}" height="4.6" rx="0.6" fill="{hexc}"/>' if fillpct else ""
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><g fill="none" stroke="{hexc}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="{ICON[name]}"/></g>{extra}</svg>'
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=int(size * K), output_height=int(size * K))
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    img.paste(im, (int((cx - size / 2) * K), int((cy - size / 2) * K)), im)

h = S; cx = S / 2
names = {"FAJR": ("Fajr", "الفجر"), "SHURUQ": ("Shuruq", "الشروق"), "DHUHR": ("Dhuhr", "الظهر"),
         "ASR": ("Asr", "العصر"), "MAGHRIB": ("Maghrib", "المغرب"), "ISHA": ("Isha", "العشاء")}
times = {"FAJR": "05:09", "SHURUQ": "06:25", "DHUHR": "12:05", "ASR": "03:16", "MAGHRIB": "05:40", "ISHA": "06:56"}  # Dubai, 3 Nov 2026 (calculated)
# date + clock
text(dig("03/11/2026"), cx, h * .118, h * .042, GREY, "regular", "c")
main = dig("03:05"); sec = dig("42"); ap = "م" if ar else "PM"
ms, ss, aps = h * .172, h * .060, h * .046
fm, fs, fa = font("light", ms), font("regular", ss), font("regular", aps)
refm = tw(dig("08:88"), fm) / K; side = 0 if AMBIENT else max(tw(dig("88"), fs), tw(ap, fa)) / K
side = max(side, tw(ap, fa) / K); gap = h * .014
left = cx - (refm + gap + side) / 2
text(main, left + refm, h * .318, ms, WHITE, "light", "r")
sx = left + refm + gap
if not AMBIENT: text(sec, sx, h * .318 - ms * .43, ss, GREY)
text(ap, sx, h * .318, aps, GREY)
# next prayer
hdr = "الصلاة القادمة: العصر" if ar else "NEXT PRAYER: ASR"
text(hdr, cx, h * .428, h * .040, ACCENT, "medium", "c")
adhan = dig("03:16") + (" م" if ar else " PM"); counter = dig("-10:18") if not AMBIENT else dig("-10") + ("د" if ar else "m")
isz, tsz, csz, g = h * .058, h * .046, h * .086, h * .022
ft, fc = font("regular", tsz), font("medium", csz)
tW, cW = tw(adhan, ft) / K, tw(counter, fc) / K
x = cx - (isz + g + tW + g + cW) / 2
for slot in (["counter", "time", "icon"] if ar else ["icon", "time", "counter"]):
    if slot == "icon": icon("ASR", x + isz / 2, h * .520 - h * .016, isz, ACCENT); x += isz + g
    elif slot == "time": text(adhan, x, h * .520, tsz, SOFT); x += tW + g
    else: text(counter, x, h * .520, csz, WHITE, "medium"); x += cW + g
# schedule
if not AMBIENT:
    order = ["FAJR", "SHURUQ", "DHUHR", "ASR", "MAGHRIB", "ISHA"]; colW, colGap = h * .355, h * .02
    rows = [h * .650, h * .722, h * .794]; ns = h * .038; isz = h * .050
    for i, p in enumerate(order):
        col = (1 - i % 2) if ar else i % 2
        x0 = cx - colGap / 2 - colW if col == 0 else cx + colGap / 2; x1 = x0 + colW; y = rows[i // 2]
        hl = p == "ASR"; nc = ACCENT if hl else GREY; tc = ACCENT if hl else SOFT; w = "medium" if hl else "regular"
        nm = names[p][1] if ar else names[p][0]; t = dig(times[p])
        if not ar:
            icon(p, x0 + isz / 2, y - h * .013, isz, nc); text(nm, x0 + isz + h * .012, y, ns, nc, w); text(t, x1, y, ns, tc, w, "r")
        else:
            icon(p, x1 - isz / 2, y - h * .013, isz, nc); text(nm, x1 - isz - h * .012, y, ns, nc, w, "r"); text(t, x0, y, ns, tc, w)
    # metrics
    items = [("STEPS", dig("8,542" if not ar else "8,542").replace(",", "٬" if ar else ",") , SOFT, None), ("HEART", dig("72"), SOFT, None), ("BATTERY", dig("85%"), SOFT, 85)]
    if ar: items.reverse()
    sz, isz, gi, gx = h * .037, h * .048, h * .008, h * .026; fnt = font("regular", sz)
    ws = [isz + gi + tw(t, fnt) / K for _, t, _, _ in items]; x = cx - (sum(ws) + gx * 2) / 2
    for (nm, t, c, pct), wd in zip(items, ws):
        icon(nm, x + isz / 2, h * .905 - h * .012, isz, GREY, pct); text(t, x + isz + gi, h * .905, sz, c); x += wd + gx
else:
    sz, isz = h * .036, h * .046; t = dig("85%"); fnt = font("regular", sz)
    tot = isz + h * .008 + tw(t, fnt) / K; x = cx - tot / 2
    icon("BATTERY", x + isz / 2, h * .905 - h * .012, isz, GREY, 85); text(t, x + isz + h * .008, h * .905, sz, GREY)
# round mask
mask = Image.new("L", (H, H), 0); ImageDraw.Draw(mask).ellipse((0, 0, H - 1, H - 1), fill=255)
out = Image.new("RGB", (H, H), (28, 28, 30)); out.paste(img, (0, 0), mask)
out.resize((S, S), Image.LANCZOS).save(OUT)
