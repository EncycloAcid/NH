#!/usr/bin/env python3
"""Rebuilds the 512x512 hairstyle layers in app/src/main/assets/character/hairstyles.

The hairstyle pack (SRC_REV) was cut out of portraits that already had short hair baked into the
head, so on their own the styles stop in a flat line at the old hairline, leave the temples bare,
and some (locs, twists) have a hard rectangular window cut over the forehead. On the bald body
cranium they read as hats floating on the head.

Each style gets a close-cropped base that follows the cranium from tools/build_bodies.py: the
original pack's own short-hair texture (from the kit bodies at KIT_REV) clipped to the skull plus
the temples and sideburns, recoloured to the style's palette by luminance rank, and outlined. The
style's own pixels are kept on top. Mohawk sides become a translucent shaved stubble instead.
Run from the repo root:  python3 tools/build_hair.py
"""
import io
import subprocess
import sys

import numpy as np
from PIL import Image

SRC_REV = "dd61b5b"
KIT_REV = "367e29b"
OUT_DIR = "app/src/main/assets/character/hairstyles"
STYLES = ["afro", "bleached_blonde_crop", "braided_top_bun", "buzz_cut", "cornrows",
          "curly_top_fade", "double_afro_puffs", "dreadlocks", "flat_top", "headband_with_curls",
          "high_top_fade", "messy_waves", "mohawk", "slicked_back_hair", "twists"]
CX, HEAD_CY, HEAD_A, HEAD_B = 255.5, 105.0, 66.0, 70.0   # must match build_bodies.py
HAIR_PAD = 3          # base hair stands this far off the scalp
OUTLINE = 3
STUBBLE = {"mohawk"}
MOHAWK_HALF = 21      # half-width of the mohawk strip kept as full hair


def git_png(rev, path):
    data = subprocess.check_output(["git", "show", f"{rev}:{path}"])
    return np.array(Image.open(io.BytesIO(data)).convert("RGBA")).astype(np.float64)


def lum(c):
    return c[..., 0] * 0.3 + c[..., 1] * 0.59 + c[..., 2] * 0.11


def dist_inside(m, maxd):
    d = np.zeros(m.shape, dtype=int)
    cur = m.copy()
    for i in range(1, maxd + 1):
        d[cur] = i
        n = cur.copy()
        n[1:, :] &= cur[:-1, :]
        n[:-1, :] &= cur[1:, :]
        n[:, 1:] &= cur[:, :-1]
        n[:, :-1] &= cur[:, 1:]
        cur = n
    d[cur] = maxd + 1
    return d


def base_cap():
    """Short-hair texture of the original pack, clipped to the padded cranium + sideburns."""
    kit = git_png(KIT_REV, "app/src/main/assets/sprites/bodies/02_dark_chocolate.png")
    cut = git_png("dd61b5b", "app/src/main/assets/character/bodies/02_dark_chocolate.png")
    ys, xs = np.mgrid[0:512, 0:512].astype(np.float64)
    hair = (kit[..., 3] > 128) & (cut[..., 3] < 128) & (ys < 128)
    a, b = HEAD_A + HAIR_PAD, HEAD_B + HAIR_PAD
    skull = ((xs - CX) / a) ** 2 + ((ys - HEAD_CY) / b) ** 2 <= 1.0
    # pack hair outside the padded skull is the old afro's bulk; drop it
    cap = skull & (ys < 126)
    # forehead: a rounded hairline that recedes a little at the temples, sideburns to the ears
    rng = np.random.default_rng(11)
    jag = np.kron(rng.random((128, 128)), np.ones((4, 4)))       # 4px steps along the edge
    forehead = ((np.abs(xs - CX) / 53) ** 4 + (np.abs(ys - 132) / 46) ** 4) <= 1.0 + (jag - 0.5) * 0.16
    cap &= ~forehead
    tex = lum(kit[..., :3])
    # fill cap pixels the pack had no hair on with a smooth fallback from nearby hair
    fallback = np.median(tex[hair])
    tex = np.where(hair, tex, fallback)
    return cap, tex, skull


def build(style, cap, tex, skull):
    src = git_png(SRC_REV, f"app/src/main/assets/character/hairstyles/{style}.png")
    out = src.copy()
    alpha = src[..., 3]
    m = alpha > 128
    px = src[m][:, :3]
    order = np.argsort(lum(px))
    pal = px[order]
    ys, xs = np.mgrid[0:512, 0:512].astype(np.float64)

    # the style's own bottom outline where the base now continues below it is a seam: refill it
    dark = m & (lum(src[..., :3]) <= np.percentile(lum(px), 12))
    near = cap & ~m
    for _ in range(5):
        grow = near.copy()
        grow[1:, :] |= near[:-1, :]
        grow[:-1, :] |= near[1:, :]
        grow[:, 1:] |= near[:, :-1]
        grow[:, :-1] |= near[:, 1:]
        near = grow
    seam = dark & near & cap & (ys > 40)
    if style in STUBBLE:
        # the hawk keeps its spikes and a strip down the middle; the rest becomes shaved sides
        strip = np.abs(xs - CX) <= MOHAWK_HALF + np.clip((ys - 60) * -0.4, 0, 12)
        shaved = m & ~strip & (ys > 52) & cap
        m = m & ~shaved
        out[shaved, 3] = 0
    m = m & ~seam
    region = cap & ~m
    if style in STUBBLE:
        strip = np.abs(xs - CX) <= MOHAWK_HALF
        region = cap & ~m & ~strip
        # sparse dark stubble so each skin tone reads through
        rng = np.random.default_rng(3)
        dots = rng.random((256, 256)) < 0.55
        dots = np.kron(dots, np.ones((2, 2), dtype=bool))
        dark = pal[int(len(pal) * 0.03)]
        sel = region & dots
        out[sel, :3] = dark
        out[sel, 3] = 150
        sel = region & ~dots
        out[sel, :3] = dark
        out[sel, 3] = 95
        # keep the strip itself solid down to the hairline
        region = cap & ~m & strip
    # luminance-rank recolour of the pack texture into this style's palette
    t = tex[region]
    tr = (np.argsort(np.argsort(t)) + 0.5) / max(1, len(t))
    # bias toward the style's darker half: base hair sits under the style's volume
    idx = np.clip((tr * 0.75 + 0.03) * (len(pal) - 1), 0, len(pal) - 1).astype(int)
    out[region, :3] = pal[idx]
    out[region, 3] = 255
    # 2x2 blocks so the 256px render keeps whole pixels
    blk = region.copy()
    for c in range(3):
        ch = out[..., c]
        ch[1::2, :] = np.where(blk[1::2, :], ch[0::2, :], ch[1::2, :])
        ch[:, 1::2] = np.where(blk[:, 1::2], ch[:, 0::2], ch[:, 1::2])
    # outline the new outer edge of the hair against the background; against the forehead skin
    # use a soft dark hair tone and a translucent shadow on the skin instead of a black line
    solid = out[..., 3] > 40
    ol = pal[max(0, int(len(pal) * 0.02))]
    soft = pal[int(len(pal) * 0.12)]
    empty = ~solid
    bg = empty & ~skull
    skin = empty & skull
    def near(mask, r):
        g = mask.copy()
        for _ in range(r):
            n = g.copy()
            n[1:, :] |= g[:-1, :]
            n[:-1, :] |= g[1:, :]
            n[:, 1:] |= g[:, :-1]
            n[:, :-1] |= g[:, 1:]
            g = n
        return g
    out[region & near(bg, OUTLINE), :3] = ol
    out[region & near(skin, 2) & ~near(bg, OUTLINE), :3] = soft
    shadow = skin & near(region, 2) & (ys > 80)
    out[shadow, :3] = ol
    out[shadow, 3] = 70
    # light fringe left on the silhouette by the pack's cut-out: make it outline
    solid = out[..., 3] > 128
    d = dist_inside(solid, 3)
    rgbo = out[..., :3]
    sat = (rgbo.max(axis=2) - rgbo.min(axis=2)) / np.maximum(rgbo.max(axis=2), 1)
    fringe = solid & (d <= 2) & (lum(rgbo) > 150) & (sat < 0.3)
    out[fringe, :3] = ol
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def main():
    cap, tex, skull = base_cap()
    for style in (sys.argv[1:] or STYLES):
        build(style, cap, tex, skull).save(f"{OUT_DIR}/{style}.png", optimize=True)
        print("wrote", style)


if __name__ == "__main__":
    main()
