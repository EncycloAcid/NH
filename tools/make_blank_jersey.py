#!/usr/bin/env python3
"""Builds character/nba_jerseys/blank.png, the plain template SpriteCompose recolors for teams
without jersey art. It takes the silhouette of the jersey pack (so it lines up with the bodies),
closes the digit cutouts, and paints fabric / trim / outline tones that recolorJersey maps to the
team's primary, secondary and black. Run from the repo root: python3 tools/make_blank_jersey.py
"""
from collections import deque
from PIL import Image

SRC = 'app/src/main/assets/character/nba_jerseys/timberwolves.png'
OUT = 'app/src/main/assets/character/nba_jerseys/blank.png'
FABRIC = (232, 204, 120)   # saturated: recolored to the primary color
TRIM = (130, 130, 130)     # gray: recolored to the secondary color
OUTLINE = (12, 10, 10)     # near black: left as is


def main():
    src = Image.open(SRC).convert('RGBA')
    w, h = src.size
    sp = src.load()
    solid = [[sp[x, y][3] >= 128 for x in range(w)] for y in range(h)]
    # Close enclosed holes (the digit cutouts): anything not reachable from the border.
    outside = [[False] * w for _ in range(h)]
    q = deque((x, y) for x in range(w) for y in (0, h - 1))
    q.extend((x, y) for y in range(h) for x in (0, w - 1))
    while q:
        x, y = q.popleft()
        if not (0 <= x < w and 0 <= y < h) or outside[y][x] or solid[y][x]:
            continue
        outside[y][x] = True
        q.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    # Distance (chessboard) from the outside, capped.
    dist = [[0 if outside[y][x] else 99 for x in range(w)] for y in range(h)]
    for _ in range(8):
        for y in range(h):
            for x in range(w):
                if dist[y][x] == 0:
                    continue
                m = dist[y][x]
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < w and 0 <= yy < h:
                        m = min(m, dist[yy][xx] + 1)
                dist[y][x] = m
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    op = out.load()
    for y in range(h):
        for x in range(w):
            d = dist[y][x]
            if d == 0:
                continue
            if d <= 2:
                c = OUTLINE
            elif d <= 6 and y < h - 8:
                c = TRIM
            else:
                # Soft side shading so the template doesn't read as a flat cutout.
                k = 1.0 - 0.12 * min(1.0, abs(x - 256) / 90)
                c = tuple(int(v * k) for v in FABRIC)
            op[x, y] = c + (255,)
    out.save(OUT, optimize=True)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
