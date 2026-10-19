#!/usr/bin/env python3
"""ENC-120: original dashed-to-wind mark generator.

One geometry source (sampled polylines on the 108dp adaptive canvas, mark
inside the central 66dp safe circle: 21..87) -> master SVG, Android
VectorDrawable XML, legacy PNG renders, preview sheet.
Design language (our own geometry): dashed voice pulses gather into three
continuous wind streamlines ending in spiral curls.
"""
import math, os
from PIL import Image, ImageDraw

MARK = (0xED, 0xEE, 0xF0)
BG = (0x3B, 0x3B, 0xAA)
STROKE = 6.0  # dp on the 108 canvas

def spiral(cx, cy, start, sweep_deg, r_end, steps=48):
    """Archimedean spiral from `start` point, winding around (cx, cy)."""
    x0, y0 = start
    r0 = math.hypot(x0 - cx, y0 - cy)
    a0 = math.atan2(y0 - cy, x0 - cx)
    pts = []
    for i in range(steps + 1):
        t = i / steps
        r = r0 + (r_end - r0) * t
        a = a0 + math.radians(sweep_deg) * t
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts

def line(x1, y1, x2, y2):
    return [(x1, y1), (x2, y2)]

# --- the mark (108dp canvas; safe box 21..87) --------------------------------
paths = []
# S1: top streamline + small up-curl
paths.append(line(24, 40, 47.5, 40) + spiral(53.5, 34.8, (47.5, 40), 295, 2.1)[1:])
# S2: dash, then long streamline + big up-curl
paths.append(line(24, 52, 37, 52))
paths.append(line(44, 52, 72, 52) + spiral(74.5, 41.5, (72, 52), 305, 3.2)[1:])
# S3: long streamline + small down-curl
paths.append(line(24, 64, 61.5, 64) + spiral(67, 69.5, (61.5, 64.5), -295, 2.1)[1:])
# bottom pulse dashes
paths.append(line(24, 76, 33, 76))
paths.append(line(39.5, 76, 53, 76))

def path_d(pts):
    return "M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in pts)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "design", "icon")
os.makedirs(OUT, exist_ok=True)
RES = os.path.join(ROOT, "app", "src", "main", "res")
os.makedirs(f"{RES}/drawable", exist_ok=True)
os.makedirs(f"{RES}/mipmap-anydpi-v26", exist_ok=True)
os.makedirs(f"{RES}/values", exist_ok=True)

# --- master SVG ---------------------------------------------------------------
svg_paths = "\n".join(
    f'  <path d="{path_d(p)}" fill="none" stroke="#EDEEF0" '
    f'stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round"/>'
    for p in paths)
svg = f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- Bloviate launcher mark — master design asset (ENC-120).
     Original vector redraw: dashed voice pulses -> wind streamlines -> curls.
     Canvas: 108dp adaptive-icon grid; mark inside the central 66dp safe
     circle (21..87). Mark #EDEEF0 on background #3B3BAA (gospel accent-deep).
     The Android foreground/monochrome drawables carry this same geometry. -->
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <rect width="108" height="108" fill="#3B3BAA"/>
{svg_paths}
</svg>
"""
open(f"{OUT}/bloviate-icon.svg", "w").write(svg)

# --- VectorDrawable foreground + monochrome -----------------------------------
def vd(fname, color):
    body = "\n".join(
        f'    <path android:pathData="{path_d(p)}" android:strokeColor="{color}" '
        f'android:strokeWidth="{STROKE}" android:strokeLineCap="round" '
        f'android:strokeLineJoin="round" android:fillColor="#00000000"/>'
        for p in paths)
    xml = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
"""
    open(f"{RES}/drawable/{fname}", "w").write(xml)

vd("ic_launcher_foreground.xml", "#EDEEF0")
vd("ic_launcher_monochrome.xml", "#FFFFFF")

# --- colors + adaptive icon XML ------------------------------------------------
open(f"{RES}/values/ic_launcher_background.xml", "w").write(
    """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#3B3BAA</color>
</resources>
""")
adaptive = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
open(f"{RES}/mipmap-anydpi-v26/ic_launcher.xml", "w").write(adaptive)
open(f"{RES}/mipmap-anydpi-v26/ic_launcher_round.xml", "w").write(adaptive)

# --- PNG renders from the same polylines ---------------------------------------
def render(size, mark=MARK, bg=BG, round_mask=False, mono_on_dark=False):
    S = size / 108.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0) if not mono_on_dark else (18, 18, 22, 255))
    d = ImageDraw.Draw(img)
    if bg is not None and not mono_on_dark:
        d.rectangle([0, 0, size, size], fill=bg + (255,))
    w = max(1.0, STROKE * S)
    # stamp method: filled discs along each resampled polyline -> smooth
    # strokes with round caps, no joint speckles on tight spiral samples
    for p in paths:
        for (x1, y1), (x2, y2) in zip(p, p[1:]):
            seg = math.hypot(x2 - x1, y2 - y1)
            n = max(2, int(seg / 0.35))
            for i in range(n + 1):
                x = (x1 + (x2 - x1) * i / n) * S
                y = (y1 + (y2 - y1) * i / n) * S
                d.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=mark + (255,))
    if round_mask:
        m = Image.new("L", (size, size), 0)
        ImageDraw.Draw(m).ellipse([0, 0, size, size], fill=255)
        img.putalpha(m)
    return img

DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, px in DENS.items():
    dd = f"{RES}/mipmap-{name}"
    os.makedirs(dd, exist_ok=True)
    render(px).convert("RGB").save(f"{dd}/ic_launcher.png")
    render(px, round_mask=True).save(f"{dd}/ic_launcher_round.png")
render(512).convert("RGB").save(f"{OUT}/play-store-512.png")

# --- preview sheet: full-bleed | circle mask | squircle mask | mono | 48dp cell
sheet = Image.new("RGB", (5 * 216 + 60, 236), (24, 24, 30))
base = render(216)
circ = render(216, round_mask=True)
sq = render(216)
m = Image.new("L", (216, 216), 0)
ImageDraw.Draw(m).rounded_rectangle([8, 8, 208, 208], radius=60, fill=255)
sq.putalpha(m)
mono = render(216, mark=(255, 255, 255), bg=None, mono_on_dark=True)
cell = Image.new("RGB", (216, 216), (18, 18, 22))
cell.paste(render(48).convert("RGB"), (84, 84))
for i, im in enumerate([base, circ, sq, mono, cell]):
    sheet.paste(im, (10 + i * 226, 10), im if im.mode == "RGBA" else None)
sheet.save(f"{OUT}/preview-sheet.png")
print("generated: svg, drawables, adaptive xml, pngs, play 512, preview sheet")
