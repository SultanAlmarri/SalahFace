"""Renders the icon path data from Icons.kt to a PNG contact sheet (pip install cairosvg)."""
import re, sys, cairosvg
src = open("app/src/main/java/com/salahface/watch/face/Icons.kt", encoding="utf-8").read()
icons = re.findall(r'const val (\w+) = "([^"]+)"', src)
cell, pad = 120, 20
cols = 5
rows = (len(icons) + cols - 1) // cols
parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{cols*cell}" height="{rows*(cell+22)}" style="background:#000"><rect width="100%" height="100%" fill="#000"/>']
for i, (name, d) in enumerate(icons):
    x, y = (i % cols) * cell, (i // cols) * (cell + 22)
    s = (cell - 2 * pad) / 24
    parts.append(f'<g transform="translate({x+pad},{y+pad}) scale({s})" fill="none" stroke="#e8e8e8" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="{d}"/></g>')
    parts.append(f'<text x="{x+cell/2}" y="{y+cell+8}" fill="#8e8e93" font-size="12" text-anchor="middle" font-family="sans-serif">{name}</text>')
parts.append("</svg>")
cairosvg.svg2png(bytestring="".join(parts).encode(), write_to=sys.argv[1] if len(sys.argv) > 1 else "icons.png")
