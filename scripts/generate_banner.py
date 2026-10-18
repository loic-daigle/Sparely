#!/usr/bin/env python3
"""Render the README banner (docs/branding/sparely-banner.png): the mark and "sparely." wordmark in
Poppins on spruce, with the splash tagline. Same colors as the app (ui/theme/Color.kt).

Requires: pip install cairosvg pillow
Run from the repo root: python3 scripts/generate_banner.py . docs/branding/sparely-banner.png
"""
import io, sys, cairosvg
from PIL import Image, ImageDraw, ImageFont
root = sys.argv[1]; out = sys.argv[2]
W, H = 1280, 420
SPRUCE=(15,61,52); INK=(234,245,240); MINT=(143,227,192); GOLD=(244,203,106); MUTED=(157,189,178)
img = Image.new('RGB', (W, H), SPRUCE)
# soft deeper band at the bottom for depth
d = ImageDraw.Draw(img)
for y in range(H):
    t = y / H
    c = tuple(int(SPRUCE[i]*(1-0.35*t) + (10,44,38)[i]*0.35*t) for i in range(3))
    d.line([(0,y),(W,y)], fill=c)
mark = open(f'{root}/docs/branding/sparely-mark.svg').read()
png = cairosvg.svg2png(bytestring=mark.encode(), output_width=260, output_height=260)
m = Image.open(io.BytesIO(png)).convert('RGBA')
font = ImageFont.truetype(f'{root}/app/src/main/res/font/poppins_bold.ttf', 132)
tag = ImageFont.truetype(f'{root}/app/src/main/res/font/poppins_medium.ttf', 38)
word, dot = 'sparely', '.'
ww = d.textlength(word, font=font); dw = d.textlength(dot, font=font)
gap = 28
total = 260 + gap + ww + dw
x0 = int((W - total) / 2)
img.paste(m, (x0, int(H/2 - 128)), m)
tx = x0 + 260 + gap
ty = int(H/2 - 112)
d.text((tx, ty), word, font=font, fill=INK)
d.text((tx + ww, ty), dot, font=font, fill=GOLD)
d.text((tx + 6, ty + 165), 'Save the small stuff.', font=tag, fill=MUTED)
img.save(out, optimize=True)
