#!/usr/bin/env python3
"""Repairs the 512x512 body layers in app/src/main/assets/character/bodies.

The body pack ships with the scalp cut off flat at the hairline (y=86), two leftover hair
strands standing up where the temples were, and no torso between the arms. Hair overlays then
float above a flat-topped head and the background shows between the arms and the jersey.

This rebuilds a rounded skull (outline + skin extended up from the forehead) and fills the torso
between the arms with shaded skin, so every hairstyle sits on a head and the arms join the
jersey. Run from the repo root on the original pack files:  python3 tools/repair_bodies.py
"""
import glob
import math
from PIL import Image

SKULL_TOP = 50        # top of the rebuilt cranium
SKULL_LEFT, SKULL_RIGHT = 191, 320   # head outline columns at the temples (from the pack)
SKULL_BOTTOM = 116    # first row where the pack's head is whole again
CORNER = 46           # radius of the cranium's rounded top corners
OUTLINE = 3
OUTLINE_RGB = (6, 3, 6)
TORSO_TOP = 236       # torso fill starts at the shoulder line
TORSO_SHADE = 0.72    # torso sides sit in the armpit shadow


def opaque(c):
    return c[3] >= 128


def skull_edges(y):
    """Left/right outer edge of the cranium on row y (None above the top)."""
    if y < SKULL_TOP:
        return None
    inset = 0
    dy = SKULL_TOP + CORNER - y
    if dy > 0:
        inset = CORNER - math.sqrt(max(0.0, CORNER * CORNER - dy * dy))
    left = int(round(SKULL_LEFT + inset))
    right = int(round(SKULL_RIGHT - inset))
    return (left, right) if left <= right else None


def scalp_colors(px):
    """Per-column scalp skin: forehead rows 88-96, outline-free, box-blurred over 11 columns."""
    raw = {}
    for x in range(SKULL_LEFT, SKULL_RIGHT + 1):
        cs = [px[x, y] for y in range(88, 97) if opaque(px[x, y]) and sum(px[x, y][:3]) >= 60]
        if cs:
            raw[x] = tuple(sum(c[i] for c in cs) / len(cs) for i in range(3))
    out = {}
    for x in range(SKULL_LEFT, SKULL_RIGHT + 1):
        near = [raw[xx] for xx in range(x - 5, x + 6) if xx in raw]
        if not near:  # temples: use the nearest forehead column
            xx = min(raw, key=lambda k: abs(k - x))
            near = [raw[xx]]
        out[x] = tuple(sum(c[i] for c in near) / len(near) for i in range(3))
    return out


def rebuild_skull(im):
    px = im.load()
    scalp = scalp_colors(px)
    # Strip the strands and anything else above the hairline outside the new cranium.
    for y in range(0, SKULL_BOTTOM):
        edges = skull_edges(y)
        for x in range(150, 362):
            if edges is None or x < edges[0] or x > edges[1]:
                if y < 86 or x < SKULL_LEFT - 2 or x > SKULL_RIGHT + 2:
                    px[x, y] = (0, 0, 0, 0)
    for y in range(SKULL_BOTTOM - 1, SKULL_TOP - 1, -1):
        left, right = skull_edges(y)
        for x in range(left, right + 1):
            prev_edges = skull_edges(y - OUTLINE) if y - OUTLINE >= SKULL_TOP else None
            on_side = x < left + OUTLINE or x > right - OUTLINE
            on_top = prev_edges is None or x < prev_edges[0] + OUTLINE or x > prev_edges[1] - OUTLINE
            if on_side or on_top:
                if not opaque(px[x, y]) or y < 86 or x <= SKULL_LEFT + 2 or x >= SKULL_RIGHT - 2:
                    px[x, y] = OUTLINE_RGB + (255,)
                continue
            if opaque(px[x, y]) and y >= 86:
                continue
            # Skin from the forehead's top rows, smoothed sideways so it doesn't streak, and a
            # touch darker toward the crown.
            r, g, b = scalp[x]
            k = 1.0 - 0.18 * max(0.0, (86 - y) / (86 - SKULL_TOP))
            px[x, y] = (int(r * k), int(g * k), int(b * k), 255)


def runs(px, y):
    out, start = [], None
    for x in range(513):
        o = x < 512 and opaque(px[x, y])
        if o and start is None:
            start = x
        if not o and start is not None:
            out.append((start, x - 1))
            start = None
    return out


def fill_torso(im):
    px = im.load()
    rows = {}
    for y in range(TORSO_TOP, 512):
        r = runs(px, y)
        if len(r) < 2:
            continue
        left, right = r[0], r[-1]
        if left[1] >= 205 or right[0] <= 306:
            continue
        rows[y] = (left[1], right[0], px[left[1] - 6, y], px[right[0] + 6, y])
    # One skin tone per side, smoothed down the torso so rows don't band.
    for y, (lx, rx, _, _) in rows.items():
        near = [rows[yy] for yy in range(y - 16, y + 17) if yy in rows]
        for side, x0, x1 in ((2, lx + 1, 255), (3, 256, rx - 1)):
            cs = [n[side] for n in near if sum(n[side][:3]) >= 60] or [n[side] for n in near]
            fill = tuple(int(sum(c[i] for c in cs) / len(cs) * TORSO_SHADE) for i in range(3)) + (255,)
            for x in range(x0, x1 + 1):
                if not opaque(px[x, y]):
                    px[x, y] = fill


def main():
    for path in sorted(glob.glob('app/src/main/assets/character/bodies/*.png')):
        im = Image.open(path).convert('RGBA')
        rebuild_skull(im)
        fill_torso(im)
        im.save(path, optimize=True)
        print('repaired', path)


if __name__ == '__main__':
    main()
