#!/usr/bin/env python3
"""Polishes where the arms meet the jersey armholes ("sleeves") in the avatar art.

Bodies (assets/character/bodies/*.png)
    Each body carried a flat, unshaded slab of skin between the arm and the jersey so the arm would
    touch the armhole. This re-shades that slab with the body's own skin ramp (lighter on top of the
    shoulder, darker toward the jersey edge and into the armpit), gives the top of the shoulder the
    same dark outline the rest of the arm has, softens the black notch in the armpit crease, and
    extends the skin a little further under the jersey so no jersey shape can leave a see-through gap.

Jerseys (assets/character/*_jerseys/*.png)
    The armhole dropped to the side seam in two blocky 9px steps. Each step's outer corner is cut
    back to half a step, so the edge runs down in even single-pixel-art steps like the V-neck does.

Run from the repository root:  python3 tools/polish_armholes.py
Every file it changes is stamped with a PNG text chunk, so running it again leaves polished art alone.
"""
import glob
import os
import sys

import numpy as np
from PIL import Image
from PIL.PngImagePlugin import PngInfo

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "character")
N = 512
TOP, BOTTOM = 250, 374          # rows the slab spans
ART = 4.5                       # one "pixel" of the art is about 4.5 real pixels
MARK = "hd-armholes"            # PNG text chunk written to every file this script has polished


def polished(path):
    return Image.open(path).info.get(MARK) == "1"


def save(pixels, path):
    info = PngInfo()
    info.add_text(MARK, "1")
    Image.fromarray(pixels, "RGBA").save(path, optimize=True, pnginfo=info)


def lum(rgb):
    return (rgb[..., 0] * 299 + rgb[..., 1] * 587 + rgb[..., 2] * 114) / 1000.0


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def components(mask):
    """4-connected components of a boolean mask as a list of index arrays (ys, xs)."""
    seen = np.zeros_like(mask, bool)
    out = []
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        stack, ys, xs = [(y0, x0)], [], []
        seen[y0, x0] = True
        while stack:
            y, x = stack.pop()
            ys.append(y)
            xs.append(x)
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < mask.shape[0] and 0 <= xx < mask.shape[1] and mask[yy, xx] and not seen[yy, xx]:
                    seen[yy, xx] = True
                    stack.append((yy, xx))
        out.append((np.array(ys), np.array(xs)))
    return out


def jersey_edge(y):
    """Nominal outer edge of a jersey on the player's right side (image left), after the steps are halved."""
    if y < 257:
        return 189
    if y < 318:
        return 181
    if y < 327:
        return 177
    if y < 336:
        return 172
    if y < 345:
        return 168
    return 164


def polish_body(path):
    if polished(path):
        return False
    img = np.array(Image.open(path).convert("RGBA"))
    rgb = img[:, :, :3].astype(np.int64)
    opaque = img[:, :, 3] > 0

    # The slab is the most common single colour beside the arm.
    win = rgb[258:330, 156:182][opaque[258:330, 156:182]]
    colours, counts = np.unique(win.reshape(-1, 3), axis=0, return_counts=True)
    flat = colours[counts.argmax()]
    if counts.max() < 600:
        return False                      # no flat slab to work on
    is_flat = opaque & (rgb == flat).all(axis=2)

    # The body's own skin ramp, read off the shaded arm beside the slab.
    arm = np.zeros((N, N), bool)
    arm[262:372, 108:156] = True
    arm &= opaque & ~is_flat
    arm_rgb = rgb[arm].astype(np.float64)
    order = np.argsort(lum(arm_rgb))
    arm_rgb = arm_rgb[order]

    def tone(lo, hi):
        a, b = int(len(arm_rgb) * lo), max(int(len(arm_rgb) * hi), int(len(arm_rgb) * lo) + 1)
        return arm_rgb[a:b].mean(axis=0)

    outline = tone(0.0, 0.015)
    ramp = np.array([tone(0.70, 0.82), tone(0.50, 0.62), tone(0.30, 0.42), tone(0.14, 0.24), tone(0.045, 0.10)])

    out = img.copy()
    for side in (0, 1):                   # 0 = image left, 1 = image right (mirrored)
        def mx(x):
            return x if side == 0 else N - 1 - x

        slab = np.zeros((N, N), bool)
        x_lo, x_hi = (150, 196) if side == 0 else (N - 1 - 196, N - 1 - 150)
        region = np.zeros((N, N), bool)
        region[TOP:BOTTOM, min(x_lo, x_hi):max(x_lo, x_hi) + 1] = True
        for ys, xs in components(is_flat & region):
            if len(ys) >= 150:
                slab[ys, xs] = True
        if not slab.any():
            continue

        # Where the slab starts on each row (the arm side), and how far it has to reach under the jersey.
        target = np.zeros((N, N), bool)
        arm_edge = {}
        for y in range(TOP, BOTTOM):
            cols = np.nonzero(slab[y])[0]
            if len(cols) == 0:
                continue
            a = cols.min() if side == 0 else cols.max()
            arm_edge[y] = a
            # Under the stepped part of the armhole the skin runs well under the jersey; lower down, where
            # some jerseys narrow again, it stops at the edge so nothing new can peek out.
            reach = jersey_edge(y) + (12 if y < 350 else 2)
            for x in range(mx(a), reach + 1):
                xx = mx(x)
                # Only ever paint over the slab itself or empty canvas, never over the drawn arm or chest.
                if slab[y, xx] or not opaque[y, xx]:
                    target[y, xx] = True
        # Keep the painted area contiguous with the slab (no islands past the chest drawing).
        for y in range(TOP, BOTTOM):
            if y not in arm_edge:
                continue
            xs_row = range(mx(arm_edge[y]), jersey_edge(y) + 13)
            run = True
            for x in xs_row:
                xx = mx(x)
                if not target[y, xx]:
                    run = False
                elif not run:
                    target[y, xx] = False

        ys, xs = np.nonzero(target)
        for y, xx in zip(ys, xs):
            x = mx(xx)                    # work in left-side coordinates
            a = mx(arm_edge[y])
            edge = jersey_edge(y)
            # Shade per art-pixel block so the result is banded like the rest of the sprite.
            bx = (np.floor(x / ART) + 0.5) * ART
            by = (np.floor(y / ART) + 0.5) * ART
            # One plane just inside the deltoid's shadow edge: mid tone up top, falling into shade toward
            # the armpit, with a single darker art-pixel where the skin meets the jersey.
            level = 1.0
            level += smoothstep((by - 292) / 36.0)
            level += smoothstep((by - 322) / 20.0)
            if by >= 336:
                level = max(level, 3.0)                             # the sliver beside the ribs sits in shade
            if bx >= edge - ART:
                level += 1.0                                        # contact shadow against the jersey
            if by < 257:
                level -= 1.0                                        # light catches the top of the shoulder
            level = int(np.clip(np.floor(level + 0.5), 0, 4))
            out[y, xx, :3] = np.clip(np.round(ramp[level]), 0, 255)
            out[y, xx, 3] = 255

        # The same dark outline the arm has, along the top of the shoulder.
        for y, xx in zip(ys, xs):
            x = mx(xx)
            if x >= jersey_edge(y) + 2:
                continue                  # under the jersey; an outline there would never show
            above_clear = all(not opaque[y - k, xx] and not target[y - k, xx] for k in (1, 2))
            if above_clear or (not opaque[y - 1, xx] and not target[y - 1, xx]):
                out[y, xx, :3] = np.clip(np.round(outline), 0, 255)
                if y + 1 < N and target[y + 1, xx]:
                    out[y + 1, xx, :3] = np.clip(np.round(outline), 0, 255)

        # The armpit crease was a near-black notch; make it deep skin shadow instead.
        for y in range(318, 342):
            for x in range(142, 164):
                xx = mx(x)
                if opaque[y, xx] and not target[y, xx] and lum(rgb[y, xx].astype(np.float64)) < 34:
                    # leave the silhouette outline alone: only pixels with body on both sides
                    left, right = xx - 3, xx + 3
                    if opaque[y, left] and (opaque[y, right] or target[y, right]):
                        out[y, xx, :3] = np.clip(np.round(ramp[4]), 0, 255)

    save(out, path)
    return True


def trim_jersey_steps(img):
    """Halves the big armhole steps of one jersey in place. Returns True when any pixel changed."""
    opaque = img[:, :, 3] > 0
    changed = False
    for side in (0, 1):
        def outer(y):
            cols = np.nonzero(opaque[y, 120:256])[0] if side == 0 else np.nonzero(opaque[y, 256:392])[0]
            if len(cols) == 0:
                return None
            return 120 + cols[0] if side == 0 else 256 + cols[-1]

        first, last = 248, 356
        edges = {y: outer(y) for y in range(first, last + 1)}
        # Stretches where the edge holds still (within a pixel) for at least six rows.
        flats = []
        y = first
        while y <= last:
            e = edges[y]
            if e is None:
                y += 1
                continue
            j = y
            while j + 1 <= last and edges[j + 1] is not None and abs(edges[j + 1] - e) <= 1:
                j += 1
            if j - y + 1 >= 6:
                flats.append((y, j, e))
            y = j + 1
        for (a0, a1, ea), (b0, b1, eb) in zip(flats, flats[1:]):
            widen = (ea - eb) if side == 0 else (eb - ea)
            if widen < 6 or not 254 <= b0 <= 346:
                continue
            cut = widen // 2
            rows = min(9, (b1 - b0 + 1) // 2)
            limit = eb + cut if side == 0 else eb - cut      # the new edge on the trimmed rows
            for yy in range(a1 + 1, b0 + rows):
                xs = range(120, limit) if side == 0 else range(limit + 1, 392)
                for xx in xs:
                    if img[yy, xx, 3]:
                        img[yy, xx] = 0
                        changed = True
    return changed


def polish_jersey(path):
    if polished(path):
        return False
    img = np.array(Image.open(path).convert("RGBA"))
    if trim_jersey_steps(img):
        save(img, path)
        return True
    return False


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else ROOT      # an optional copy of assets/character to work on
    bodies = sorted(glob.glob(os.path.join(root, "bodies", "*.png")))
    jerseys = sorted(glob.glob(os.path.join(root, "college_jerseys", "*.png")) + glob.glob(os.path.join(root, "nba_jerseys", "*.png")))
    done_b = sum(polish_body(p) for p in bodies)
    done_j = sum(polish_jersey(p) for p in jerseys)
    print(f"bodies polished: {done_b}/{len(bodies)}; jerseys polished: {done_j}/{len(jerseys)}")


if __name__ == "__main__":
    sys.exit(main())
