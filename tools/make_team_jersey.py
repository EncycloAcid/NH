#!/usr/bin/env python3
"""Builds the neutral jersey the app recolours for teams that have no jersey art of their own.

It starts from the Duke jersey, paints the wordmark out with the surrounding fabric, and stores only
how light or dark each pixel is, so SpriteCompose can multiply a team's own colours back in:

    R = shade, 0..255, where 128 is the base colour (folds below it, highlights above)
    G = 255 for trim (the piping), 0 for fabric
    A = the jersey's silhouette

Run from the repository root (after tools/polish_armholes.py):  python3 tools/make_team_jersey.py
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "character")
SOURCE = os.path.join(ROOT, "college_jerseys", "01_duke.png")
TARGET = os.path.join(ROOT, "jersey_template.png")


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else SOURCE
    dst = sys.argv[2] if len(sys.argv) > 2 else TARGET
    im = np.array(Image.open(src).convert("RGBA")).astype(np.float64)
    h, w = im.shape[:2]
    rgb, alpha = im[:, :, :3].copy(), im[:, :, 3]
    opaque = alpha > 0
    low = rgb.min(axis=2)
    white = opaque & (low > 150)
    light = opaque & (low > 90) & ~white

    # 1. The wordmark: light pixels on the chest, between the two vertical pipings.
    box = np.zeros((h, w), bool)
    box[322:376, 196:319] = True
    word = (white | light) & box
    for _ in range(3):                    # take the anti-aliased fringe with it
        grown = word.copy()
        grown[1:, :] |= word[:-1, :]
        grown[:-1, :] |= word[1:, :]
        grown[:, 1:] |= word[:, :-1]
        grown[:, :-1] |= word[:, 1:]
        word = grown & box & opaque

    # 2. Paint it out with the fabric around it (relaxation; every other pixel stays as drawn).
    known = opaque & ~word
    rgb[word] = rgb[box & known & ~(white | light)].mean(axis=0)
    for _ in range(1200):
        around = (np.roll(rgb, 1, 0) + np.roll(rgb, -1, 0) + np.roll(rgb, 1, 1) + np.roll(rgb, -1, 1)) / 4.0
        rgb[word] = around[word]

    # 3. Trim (piping) or fabric, and how light each pixel is against its own base tone.
    high, low = rgb.max(axis=2), rgb.min(axis=2)
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    sat = np.where(high > 0, (high - low) / np.maximum(high, 1), 0)
    trim = opaque & (low > 110) & (sat < 0.55)
    fabric = opaque & ~trim
    shade = np.zeros((h, w))
    shade[fabric] = lum[fabric] / np.median(lum[fabric & (lum > 40)])    # the base tone, ignoring the outline
    shade[trim] = lum[trim] / np.median(lum[trim])

    out = np.zeros((h, w, 4), np.uint8)
    out[:, :, 0] = np.clip(np.round(shade * 128), 0, 255)
    out[:, :, 1] = np.where(trim, 255, 0)
    out[:, :, 3] = np.where(opaque, 255, 0)
    Image.fromarray(out, "RGBA").save(dst, optimize=True)
    print(f"{os.path.relpath(dst)}: {int(opaque.sum())} px, wordmark painted out over {int(word.sum())} px, {int(trim.sum())} trim px")


if __name__ == "__main__":
    sys.exit(main())
