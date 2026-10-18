#!/usr/bin/env python3
"""Render the widget picker image for Android 11 and older
(res/drawable-nodpi/widget_savings_preview.png), drawn like res/layout/widget_savings_preview.xml,
which Android 12+ uses instead. Sample numbers only.

Requires: pip install cairosvg pillow
Run from the repo root: python3 scripts/generate_widget_preview.py
"""
import io
import cairosvg
from PIL import Image, ImageDraw, ImageFont

S = 3  # pixels per dp
W, H = 250 * S, 190 * S
SPRUCE = (15, 61, 52); DEEP = (10, 44, 38); INK = (234, 245, 240)
MINT = (143, 227, 192); GOLD = (244, 203, 106); MUTED = (157, 189, 178)
FONT = 'app/src/main/res/font/'

img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([0, 0, W - 1, H - 1], radius=28 * S, fill=SPRUCE)
pad = 16 * S

mark = open('docs/branding/sparely-mark.svg').read()
# The mark's SVG has room around the bars, so it's drawn larger than its 28dp slot and centered on it
m = Image.open(io.BytesIO(cairosvg.svg2png(bytestring=mark.encode(), output_width=44 * S, output_height=44 * S)))
img.paste(m, (pad - 8 * S, pad - 8 * S), m.convert('RGBA'))
word = ImageFont.truetype(FONT + 'poppins_bold.ttf', 17 * S)
wx, wy = pad + 36 * S, pad + 1 * S
d.text((wx, wy), 'sparely', font=word, fill=INK)
d.text((wx + d.textlength('sparely', font=word), wy), '.', font=word, fill=GOLD)

# The "+" (Log purchase) button: spruce plus on a mint circle
r = 16 * S; cx, cy = W - pad - r, pad + 14 * S
d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=MINT)
arm, t = 7 * S, int(1.2 * S)
d.rounded_rectangle([cx - arm, cy - t, cx + arm, cy + t], radius=t, fill=SPRUCE)
d.rounded_rectangle([cx - t, cy - arm, cx + t, cy + arm], radius=t, fill=SPRUCE)

top = pad + 28 * S + 12 * S
d.rounded_rectangle([pad, top, W - pad, H - pad], radius=16 * S, fill=DEEP)
x = pad + 14 * S
body = lambda size: ImageFont.truetype(FONT + 'roboto_flex.ttf', size * S)
d.text((x, top + 12 * S), 'Total savings', font=body(12), fill=MINT)
# The bundled Roboto Flex has no bold, so the bold figures use Poppins
big = ImageFont.truetype(FONT + 'poppins_semibold.ttf', 28 * S)
d.text((x, top + 28 * S), '$4,812.40', font=big, fill=INK)
small = body(11)
mid = ImageFont.truetype(FONT + 'poppins_semibold.ttf', 16 * S)
row = top + 74 * S
d.text((x, row), 'This month', font=small, fill=MUTED)
d.text((x, row + 14 * S), '+$318.75', font=mid, fill=MINT)
right = W - pad - 14 * S
d.text((right - d.textlength('Savings Rate', font=small), row), 'Savings Rate', font=small, fill=MUTED)
d.text((right - d.textlength('24%', font=mid), row + 14 * S), '24%', font=mid, fill=MINT)

img.save('app/src/main/res/drawable-nodpi/widget_savings_preview.png', optimize=True)
