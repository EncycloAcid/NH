#!/usr/bin/env python3
"""Builds the 512x512 body layers in app/src/main/assets/character/bodies.

Source art (all 8 skin tones share the exact same geometry):
  * KIT_REV  sprites/bodies/*   - the original pack: player with short hair and a baked green kit.
  * CUT_REV  character/bodies/* - the same art with hair and kit erased. The erase left a head cut
    flat at the hairline, two stray outline strands, holes at the neck, and nothing between the
    arms, so the team jerseys (whose armholes curve in more than the baked kit did) floated beside
    the shoulders and every hairstyle sat on a flat-topped head.

This paints what the erase removed, using each tone's own skin ramp sampled from its arms/face:
  * a rounded cranium (sphere-lit, outlined like the face) joined seamlessly to the forehead;
  * the torso: trapezius slope from neck to shoulder, front deltoid into the pecs, sternum,
    clavicles, pec shadow and armpit, shaded from a height field and blended into the existing
    neck/arm skin with a Laplace offset so there is no seam. Below the armpits it stays inside the
    narrowest jersey so the jersey, not skin, is the silhouette there.
Run from the repo root:  python3 tools/build_bodies.py
"""
import glob
import sys
import io
import subprocess

import numpy as np
from PIL import Image

KIT_REV = "367e29b"
CUT_REV = "dd61b5b"
NAMES = ["01_deep_ebony", "02_dark_chocolate", "03_chestnut_brown", "04_medium_brown",
         "05_warm_caramel", "06_olive_bronze", "07_fair_peach", "08_pale_ivory"]
OUT_DIR = "app/src/main/assets/character/bodies"
JERSEY_GLOB = "app/src/main/assets/character/*_jerseys/*.png"

CX = 255.5                       # body symmetry axis
HEAD_CY, HEAD_A, HEAD_B = 105.0, 66.0, 70.0   # cranium ellipse (sides meet the face at x=191/320)
HEAD_SEAM = 92                   # first forehead row kept from the art
OUTLINE_W = 4
K = 10                           # ramp levels
RNG = np.random.default_rng(7)


def git_png(rev, path):
    data = subprocess.check_output(["git", "show", f"{rev}:{path}"])
    return np.array(Image.open(io.BytesIO(data)).convert("RGBA")).astype(np.float64)


def lum(c):
    return c[..., 0] * 0.3 + c[..., 1] * 0.59 + c[..., 2] * 0.11


def make_ramp(rgb, mask):
    px = rgb[mask]
    order = np.argsort(lum(px))
    px = px[order]
    n = len(px)
    out = []
    for q in np.linspace(0.04, 0.985, K):
        i = int(q * (n - 1))
        lo, hi = max(0, i - n // 40), min(n, i + n // 40 + 1)
        out.append(np.median(px[lo:hi], axis=0))
    return np.array(out)


def color_index(rgb, ramp):
    """Float ramp index for colors, by luminance."""
    rl = lum(ramp)
    return np.interp(lum(rgb), rl, np.arange(K, dtype=np.float64))


def value_noise(cell, amp, seed_shape=(512, 512)):
    h, w = seed_shape
    g = RNG.standard_normal((h // cell + 2, w // cell + 2))
    ys, xs = np.mgrid[0:h, 0:w] / cell
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = ys - y0, xs - x0
    v = (g[y0, x0] * (1 - fx) * (1 - fy) + g[y0, x0 + 1] * fx * (1 - fy)
         + g[y0 + 1, x0] * (1 - fx) * fy + g[y0 + 1, x0 + 1] * fx * fy)
    return v * amp


def laplace_offset(region, fixed_mask, fixed_val, iters=500):
    """Smooth field over region that equals fixed_val on fixed_mask (Neumann elsewhere)."""
    o = np.zeros(region.shape)
    o[fixed_mask] = fixed_val[fixed_mask]
    live = region & ~fixed_mask
    if fixed_mask.any():
        o[live] = fixed_val[fixed_mask].mean()
    domain = (region | fixed_mask).astype(np.float64)
    for _ in range(iters):
        s = np.zeros_like(o)
        c = np.zeros_like(o)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            s += np.roll(np.roll(o * domain, dy, 0), dx, 1)
            c += np.roll(np.roll(domain, dy, 0), dx, 1)
        avg = np.where(c > 0, s / np.maximum(c, 1), 0)
        o[live] = avg[live]
    return o


def dilate(m, r=1):
    out = m.copy()
    for _ in range(r):
        n = out.copy()
        n[1:, :] |= out[:-1, :]
        n[:-1, :] |= out[1:, :]
        n[:, 1:] |= out[:, :-1]
        n[:, :-1] |= out[:, 1:]
        out = n
    return out


def erode_dist(m, maxd):
    """Distance (in px, 4-neighbour) from each True pixel to the nearest False, capped."""
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


def blocky(field):
    """Hold values over 2x2 blocks so the 256px render keeps whole art pixels."""
    f = field.copy()
    f[1::2, :] = f[0::2, :][: f[1::2, :].shape[0]]
    f[:, 1::2] = f[:, 0::2][:, : f[:, 1::2].shape[1]]
    return f


def jersey_extent():
    lmax = np.full(512, -1)
    rmin = np.full(512, 9999)
    for f in glob.glob(JERSEY_GLOB):
        a = np.array(Image.open(f).convert("RGBA"))[..., 3] > 128
        for y in range(512):
            xs = np.nonzero(a[y, 100:420])[0]
            if len(xs):
                lmax[y] = max(lmax[y], xs[0] + 100)
                rmin[y] = min(rmin[y], xs[-1] + 100)
    return lmax, rmin


def outline_rgb(cut):
    a = cut[..., 3] > 128
    dark = a & (lum(cut) < 14)
    return np.median(cut[dark][:, :3], axis=0)


def paint(img, region, shade, ramp, existing, noise):
    """Fill region with ramp colors from shade (float index), matched to existing skin."""
    rgb = img[..., :3]
    ex_idx = color_index(rgb, ramp)
    fixed = dilate(region, 2) & ~region & existing
    off = laplace_offset(region, fixed, np.where(fixed, ex_idx - shade, 0))
    idx = blocky(shade + off + noise)
    idx = np.clip(np.rint(idx), 0, K - 1).astype(int)
    img[region, :3] = ramp[idx[region]]
    img[region, 3] = 255


def build(name, lmax, rmin):
    kit = git_png(KIT_REV, f"app/src/main/assets/sprites/bodies/{name}.png")
    cut = git_png(CUT_REV, f"app/src/main/assets/character/bodies/{name}.png")
    img = cut.copy()
    H, W = 512, 512
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float64)
    a_cut = cut[..., 3] > 128
    a_kit = kit[..., 3] > 128
    ol = outline_rgb(cut)
    rgb = cut[..., :3]
    L = lum(rgb)

    # ---- skin ramps --------------------------------------------------------------------
    face = a_cut & (ys > 95) & (ys < 215) & (xs > 205) & (xs < 306)
    fl = L[face]
    face &= (L > np.median(fl) * 0.5) & (L < np.percentile(fl, 97))
    head_ramp = make_ramp(rgb, face)
    body_skin = a_cut & (ys > 262) & (ys < 420) & (((xs > 95) & (xs < 160)) | ((xs > 352) & (xs < 418)))
    bl = L[body_skin]
    body_skin &= (L > np.median(bl) * 0.45) & (L < np.percentile(bl, 99.5))
    torso_ramp = make_ramp(rgb, body_skin)
    skin_floor = lum(head_ramp[0]) * 0.75

    # ---- cranium -----------------------------------------------------------------------
    u = (xs - CX) / HEAD_A
    v = (ys - HEAD_CY) / HEAD_B
    r2 = u * u + v * v
    inside = r2 <= 1.0
    img[(ys < HEAD_SEAM) & ~inside, 3] = 0        # stray strands outside the skull
    img[(ys < HEAD_SEAM) & ~inside, :3] = 0
    # cranium plus the temples the erase cut out (sideburn notch, strand, under-hair shadow)
    ell = inside & ((ys < HEAD_SEAM) | ((ys < 116) & (np.abs(xs - CX) > 40)) | ((ys < 128) & ~a_cut))
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    light = np.array([-0.12, -0.55, 0.83])
    light /= np.linalg.norm(light)
    s = np.clip(u * light[0] + v * light[1] + z * light[2], 0, 1)
    head_shade = 1.0 + s * (K - 2.2)
    existing_head = a_cut & (ys >= HEAD_SEAM) & (L > skin_floor)
    head_noise = value_noise(8, 0.22)
    paint(img, ell, head_shade, head_ramp, existing_head, head_noise)
    # outline + rim shadow along the cranium edge
    dist_edge = (1 - np.sqrt(np.clip(r2, 0, 1))) * min(HEAD_A, HEAD_B)
    rim = ell & (dist_edge < OUTLINE_W)
    img[rim, :3] = ol
    rim2 = ell & (dist_edge >= OUTLINE_W) & (dist_edge < OUTLINE_W + 2.2)
    img[rim2, :3] = head_ramp[0]

    # ---- torso -------------------------------------------------------------------------
    removed = a_kit & ~a_cut
    kr = kit[..., :3]
    whiteish = (kr.min(axis=2) > 140) & ((kr.max(axis=2) - kr.min(axis=2)) < 45)
    gap = whiteish & ((xs < 182) | (xs > 329)) & (ys > 300)
    torso = removed & (ys >= 200) & ~gap
    # trapezius: neck (209,236) down to the deltoid top (158,258), mirrored
    for sgn in (-1, 1):
        nx, ny = CX + sgn * 46.5, 234.0
        sx, sy = CX + sgn * 98.0, 258.0
        t = (xs - nx) / (sx - nx)
        line_y = ny + (sy - ny) * np.clip(t, 0, 1) + 3.5 * np.sin(np.clip(t, 0, 1) * np.pi)
        side = (xs - nx) * sgn > 0
        torso &= ~(side & (ys < line_y))
    # below the armpits stay inside every jersey
    for y in range(345, 512):
        if lmax[y] >= 0:
            torso[y, : lmax[y] + 2] = False
            torso[y, rmin[y] - 1:] = False
    torso[:200] = False
    # stray outline flecks left in the armpits by the erase
    for x0, x1 in ((138, 168), (343, 374)):
        box = (xs >= x0) & (xs < x1) & (ys >= 300) & (ys < 338)
        torso |= box & a_cut & (L < lum(torso_ramp[0]) * 0.7)

    # height field: chest cylinder + pecs + front delts + clavicles
    Hf = 28 * np.sqrt(np.clip(1 - ((xs - CX) / 120) ** 2, 0, 1))
    for sgn in (-1, 1):
        px, py = CX + sgn * 43, 284.0
        pr = ((xs - px) / 52) ** 2 + ((ys - py) / 36) ** 2
        Hf += 9 * np.clip(1 - pr, 0, 1) ** 2
        dx_, dy_ = CX + sgn * 104, 286.0
        dr = ((xs - dx_) / 30) ** 2 + ((ys - dy_) / 44) ** 2
        Hf += 10 * np.clip(1 - dr, 0, 1) ** 2
        # clavicle ridge
        cx0, cy0, cx1, cy1 = CX + sgn * 14, 246.0, CX + sgn * 86, 252.0
        tt = np.clip(((xs - cx0) * (cx1 - cx0) + (ys - cy0) * (cy1 - cy0)) /
                     ((cx1 - cx0) ** 2 + (cy1 - cy0) ** 2), 0, 1)
        dd = np.hypot(xs - (cx0 + tt * (cx1 - cx0)), ys - (cy0 + tt * (cy1 - cy0)))
        Hf += 3.0 * np.exp(-(dd / 3.5) ** 2)
    gy, gx = np.gradient(Hf)
    n = np.dstack([-gx, -gy, np.ones_like(Hf)])
    n /= np.linalg.norm(n, axis=2, keepdims=True)
    tl = np.array([0.0, -0.62, 0.78])
    tl /= np.linalg.norm(tl)
    ts = np.clip((n * tl).sum(axis=2), 0, 1)
    flat = tl[2]
    torso_shade = 5.0 + (ts - flat) * 22.0
    # armpit occlusion
    for sgn in (-1, 1):
        ax, ay = CX + sgn * 93, 338.0
        torso_shade -= 2.6 * np.exp(-(((xs - ax) / 12) ** 2 + ((ys - ay) / 16) ** 2))
    existing_body = a_cut & (L > lum(torso_ramp[0]) * 0.75) & (ys >= 200)
    paint(img, torso, torso_shade, torso_ramp, existing_body, value_noise(8, 0.25))
    # outline where the torso meets empty space (trapezius line, armpit, gap)
    opaque = img[..., 3] > 128
    open_dist = erode_dist(opaque, OUTLINE_W + 2)
    edge = torso & (open_dist <= 4)
    img[edge, :3] = ol
    # shadow line just inside the outline
    edge2 = torso & (open_dist == 5)
    img[edge2, :3] = torso_ramp[0]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def main():
    lmax, rmin = jersey_extent()
    for name in (sys.argv[1:] or NAMES):
        build(name, lmax, rmin).save(f"{OUT_DIR}/{name}.png", optimize=True)
        print("wrote", name)


if __name__ == "__main__":
    main()
