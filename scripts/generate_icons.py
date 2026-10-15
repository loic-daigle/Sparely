#!/usr/bin/env python3
"""Generate Sparely's launcher icons from the "rising bars" logo definition.

The notification icon and in-app logos are vector drawables (res/drawable/ic_stat_logo.xml,
ic_sparely_logo.xml, ic_sparely_mark_mono.xml, ic_splash_icon*.xml) and are not generated here.

Requires: pip install cairosvg pillow
Run from anywhere: python3 scripts/generate_icons.py
"""
import io
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
BRANDING = ROOT / "docs" / "branding"

MINT = "#8FE3C0"
GOLD = "#F4CB6A"
SPRUCE = "#0F3D34"
WHITE = "#FFFFFF"

# Visual center of the mark in its 200x200 coordinate space.
MARK_CENTER = (100, 89.5)

# Mark scale on the 108dp adaptive-icon canvas. Launchers mask that canvas to a ~72dp circle (or other
# shape); 0.30 keeps the mark at about 59% of the circle's height so it doesn't crowd the edge.
ADAPTIVE_MARK_SCALE = 0.30

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def mark(center, scale, bar_color=MINT, coin_color=GOLD):
    """The logo mark, placed so its visual center sits at (center, center)."""
    cx, cy = MARK_CENTER
    return (
        f'<g transform="translate({center},{center}) scale({scale}) translate({-cx},{-cy})">'
        f'<g fill="{bar_color}">'
        '<rect x="46" y="110" width="30" height="50" rx="15"/>'
        '<rect x="85" y="82" width="30" height="78" rx="15"/>'
        '<rect x="124" y="56" width="30" height="104" rx="15"/>'
        "</g>"
        f'<circle cx="139" cy="34" r="15" fill="{coin_color}"/>'
        "</g>"
    )


def svg(size, body):
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
        f'viewBox="0 0 {size} {size}">{body}</svg>'
    )


def render(svg_text, px, out):
    out.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(
        bytestring=svg_text.encode(), write_to=str(out), output_width=px, output_height=px
    )


# Icon specs: canvas size in dp -> SVG body (in dp units).
def foreground_svg():
    return svg(108, mark(54, ADAPTIVE_MARK_SCALE))


def background_svg():
    return svg(108, f'<rect width="108" height="108" fill="{SPRUCE}"/>')


def monochrome_svg():
    return svg(108, mark(54, ADAPTIVE_MARK_SCALE, WHITE, WHITE))


def legacy_svg():
    return svg(48, f'<circle cx="24" cy="24" r="24" fill="{SPRUCE}"/>' + mark(24, 0.2))


def branding_icon_svg():
    return svg(200, f'<rect width="200" height="200" rx="46" fill="{SPRUCE}"/>' + mark(100, 0.82))


def branding_mark_svg():
    return svg(200, mark(100, 0.82))


def main():
    outputs = []  # (path, expected px)
    for density, factor in DENSITIES.items():
        mipmap = RES / f"mipmap-{density}"
        for name, body, dp, folder in [
            ("ic_launcher_foreground.png", foreground_svg(), 108, mipmap),
            ("ic_launcher_background.png", background_svg(), 108, mipmap),
            ("ic_launcher_monochrome.png", monochrome_svg(), 108, mipmap),
            ("ic_launcher.png", legacy_svg(), 48, mipmap),
        ]:
            px = round(dp * factor)
            out = folder / name
            render(body, px, out)
            outputs.append((out, px))

    BRANDING.mkdir(parents=True, exist_ok=True)
    icon = branding_icon_svg()
    (BRANDING / "sparely-icon.svg").write_text(icon + "\n")
    (BRANDING / "sparely-mark.svg").write_text(branding_mark_svg() + "\n")
    play = BRANDING / "sparely-icon-512.png"
    render(icon, 512, play)
    outputs.append((play, 512))

    for path, px in outputs:
        with Image.open(path) as im:
            assert im.size == (px, px), f"{path}: {im.size} != {px}x{px}"
        print(f"{px:>4}x{px:<4} {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
