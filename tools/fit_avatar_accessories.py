"""Redraw .130 forehead bands/clear guard and fit beard edges to the new faces.

Usage: python -B tools/fit_avatar_accessories.py BEFORE_CHARACTER CHARACTER
Run after clean_supplied_hair.py.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
from clean_supplied_hair import polish_gaps

BAND_NAMES = (("01_classic_white", "01_white_headband"),
              ("02_bulls_red", "02_red_headband"),
              ("03_stealth_black", "03_black_headband"))
BAND_COLORS = (((221, 225, 232), (246, 248, 251), (157, 165, 179), (47, 51, 62)),
               ((174, 28, 36), (206, 49, 55), (113, 18, 26), (48, 18, 23)),
               ((25, 28, 35), (49, 53, 64), (14, 16, 22), (5, 7, 12)))
EMBEDDED_BAND_COLORS = {color + (255,) for color in BAND_COLORS[1]}


def band_image(body, colors):
    out = Image.new("RGBA", (512, 512))
    base, light, shade, edge = colors
    for x in range(192, 320):
        top = 89 + round(14 * ((x - 255.5) / 63.5) ** 2)
        for y in range(top, top + 15):
            if not body.getpixel((x, y))[3]:
                continue
            depth = y - top
            color = edge if depth in (0, 14) else light if depth in (1, 2) else shade if depth >= 11 else base
            out.putpixel((x, y), color + (255,))
    return out


def clear_mask(body):
    out = Image.new("RGBA", (512, 512))
    draw = ImageDraw.Draw(out)
    edge = (31, 38, 49, 156)
    outline = [(193, 129), (202, 123), (237, 125), (248, 131), (264, 131), (275, 125),
               (309, 123), (318, 129), (318, 153), (305, 169), (282, 171), (271, 182),
               (256, 187), (240, 182), (230, 171), (207, 169), (193, 153)]
    draw.polygon(outline, fill=(170, 197, 214, 16))
    draw.line(outline + [outline[0]], fill=edge, width=1)
    left = [(201, 131), (211, 128), (234, 130), (241, 136), (239, 149), (232, 155), (210, 154), (201, 148)]
    for points in (left, [(511 - x, y) for x, y in left]):
        draw.polygon(points, fill=(0, 0, 0, 0))
        draw.line(points + [points[0]], fill=(41, 50, 62, 132), width=1)
    for points in ([(248, 132), (246, 158), (240, 174)], [(264, 132), (266, 158), (272, 174)]):
        draw.line(points, fill=(44, 53, 65, 112), width=1)
    draw.line([(193, 140), (184, 144), (181, 146)], fill=(25, 29, 36, 185), width=1)
    draw.line([(318, 140), (327, 144), (330, 146)], fill=(25, 29, 36, 185), width=1)
    for x, y in ((205, 125), (302, 125), (196, 151), (313, 151), (251, 175)):
        draw.line([(x, y), (x + 2, y)], fill=(233, 243, 248, 120), width=1)
    for y in range(120, 190):
        for x in range(178, 334):
            if not body.getpixel((x, y))[3]:
                out.putpixel((x, y), (0, 0, 0, 0))
    return out


def jaw_left(y):
    points = [(133, 192), (137, 193), (170, 193), (175, 194), (190, 195),
              (195, 196), (200, 198), (205, 203), (210, 208), (215, 212),
              (220, 217), (225, 220), (230, 225), (235, 230), (238, 233), (240, 238)]
    for (y0, x0), (y1, x1) in zip(points, points[1:]):
        if y0 <= y <= y1:
            return round(x0 + (x1 - x0) * (y - y0) / (y1 - y0))
    raise ValueError(y)


def fit_beard(image, name, body, mouths):
    out = image.copy()
    if name != "02_trimmed_goatee":
        start = 133 if name == "01_full_beard" else 170
        for y in range(start, 241):
            left = jaw_left(y)
            width = min(24, 4 + max(0, y - 150) // 2)
            xs = range(left, 512 - left) if y >= 220 else list(range(left, left + width)) + list(range(512 - left - width, 512 - left))
            for x in xs:
                if body.getpixel((x, y))[3] and out.getpixel((x, y))[3] == 0:
                    shade = 22 + (7 if (x // 2 + y // 3) % 7 == 0 else 0)
                    alpha = 255 if name == "01_full_beard" else 112 + (32 if (x + y) % 3 == 0 else 0)
                    out.putpixel((x, y), (shade - 2, shade + 2, shade + 4, alpha))
    for y in range(180, 246):
        for x in range(185, 327):
            outside_stubble = name == "03_stubble_shadow" and (y > 240 or x < jaw_left(y) or x > 511 - jaw_left(y))
            if mouths.getpixel((x, y)) or outside_stubble:
                out.putpixel((x, y), (0, 0, 0, 0))
    return out


def main():
    before, destination = map(Path, sys.argv[1:])
    body = Image.open(destination / "bodies/01_deep_ebony.png")
    for names, colors in zip(BAND_NAMES, BAND_COLORS):
        band = band_image(body, colors)
        for name in names:
            band.save(destination / f"headbands/{name}.png")
    hair_path = destination / "hairstyles/headband_with_curls.png"
    embedded = Image.alpha_composite(band_image(body, BAND_COLORS[1]), Image.open(hair_path))
    embedded, repaired = polish_gaps(embedded, body)
    embedded.save(hair_path)
    print(f"Embedded curl band: {repaired} final seam pixels polished")
    clear_mask(body).save(destination / "eyewear/02_protective_clear_mask.png")
    mouths = Image.new("L", (512, 512))
    for path in (destination / "mouths").glob("*.png"):
        mouths.paste(255, mask=Image.open(path).getchannel("A"))
    mouths = mouths.filter(ImageFilter.MaxFilter(3))
    for name in ("01_full_beard", "02_trimmed_goatee", "03_stubble_shadow"):
        out = fit_beard(Image.open(before / f"beards/{name}.png"), name, body, mouths)
        out.save(destination / f"beards/{name}.png")
        if name == "02_trimmed_goatee":
            out.save(destination / "beards/02_goatee.png")
    print("Redrew 6 wraparound band files and clear protective mask; fitted 4 beard files; matched embedded curl band")


if __name__ == "__main__":
    main()
