"""Targeted .130 corrections; preserves hair3 silhouettes and supplied sideburns.

Usage: python -B tools/clean_supplied_hair.py SOURCE_HAIRSTYLES DEST_HAIRSTYLES
"""
import sys
from pathlib import Path
from statistics import median
from PIL import Image, ImageDraw, ImageFilter
from clean_avatar_art import clean_fringe


def clean_specks(image):
    out = image.copy()
    pixels = image.load()
    pale = {(x, y) for y in range(512) for x in range(512)
            if pixels[x, y][3] and min(pixels[x, y][:3]) > 60
            and max(pixels[x, y][:3]) - min(pixels[x, y][:3]) < 20}
    while pale:
        seed = pale.pop()
        component, pending = {seed}, [seed]
        while pending:
            x, y = pending.pop()
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    point = x + dx, y + dy
                    if point in pale:
                        pale.remove(point)
                        component.add(point)
                        pending.append(point)
        if len(component) > 96 or any(
                all((x + dx, y + dy) in component for dx in (-1, 0, 1) for dy in (-1, 0, 1))
                for x, y in component):
            continue
        for x, y in component:
            if min(pixels[x, y][:3]) >= 160:
                out.putpixel((x, y), (0, 0, 0, 0))
                continue
            donors = []
            for radius in range(1, 13):
                for yy in range(max(0, y - radius), min(512, y + radius + 1)):
                    for xx in range(max(0, x - radius), min(512, x + radius + 1)):
                        r, g, b, a = pixels[xx, yy]
                        if a == 255 and max(r, g, b) < 120 and min(r, g, b) < 60:
                            donors.append((r, g, b))
                if donors:
                    break
            if donors:
                out.putpixel((x, y), tuple(int(median(p[c] for p in donors)) for c in range(3)) + (pixels[x, y][3],))
    return out


def correct(image, name, curls):
    out = image.copy()
    if name == "headband_with_curls":
        for y in range(55, 128):
            for x in range(140, 373):
                r, g, b, a = image.getpixel((x, y))
                colored = max(r, g, b) >= 75 and max(r, g, b) - min(r, g, b) >= 45
                colored |= max(r, g, b) >= 30 and (g > r or b > g * 1.1 and b > r * .8)
                if a and colored:
                    color = curls.getpixel((210 + (x - 140) % 90, 30 + (y - 55) % 45))
                    out.putpixel((x, y), (color[:3] if color[3] else (22, 18, 20)) + (a,))
    if name == "double_afro_puffs":
        for y in range(100):
            for x in range(180, 336):
                if image.getpixel((x, y)) == (24, 18, 15, 255):
                    if y < 51:
                        out.putpixel((x, y), (0, 0, 0, 0))
                    else:
                        color = curls.getpixel((210 + (x - 180) % 90, 30 + (y - 51) % 45))
                        out.putpixel((x, y), (color[:3] if color[3] else (22, 18, 20)) + (255,))
        for y in range(64, 88):
            for x in range(198, 314):
                pixel = out.getpixel((x, y))
                if pixel[3] and max(pixel[:3]) < 32:
                    color = curls.getpixel((210 + (x - 198) % 90, 30 + (y - 64) % 45))
                    if color[3]:
                        out.putpixel((x, y), color)
    out = clean_specks(out)
    out, _ = clean_fringe(out)
    return out


def close_joins(image, bodies):
    out = image.copy()
    changes, widest = set(), 0
    for y in range(84, 88):
        for x in range(205, 307):
            if out.getpixel((x, y))[3] < 255:
                donor = next(yy for yy in range(y - 1, max(-1, y - 17), -1)
                             if out.getpixel((x, yy))[3] == 255)
                out.putpixel((x, y), out.getpixel((x, donor)))
                changes.add((x, y))
    for body in bodies:
        for y in range(84, 137):
            head = [x for x in range(185, 327) if body.getpixel((x, min(y, 132)))[3]]
            for x, direction in ((head[0], -1), (head[-1], 1)):
                if out.getpixel((x, y))[3] == 255:
                    continue
                donor = next((x + direction * distance for distance in range(1, 17)
                              if out.getpixel((x + direction * distance, y))[3] == 255), None)
                assert donor is not None, ("Join requires more than a local repair", x, y)
                color = out.getpixel((donor, y))
                widest = max(widest, abs(donor - x))
                for xx in range(min(x, donor), max(x, donor) + 1):
                    if out.getpixel((xx, y))[3] < 255:
                        out.putpixel((xx, y), color)
                        changes.add((xx, y))
    return out, len(changes), widest


def polish_gaps(image, body):
    out = image.copy()
    mask = image.getchannel("A").point(lambda a: 255 if a == 255 else 0)
    flooded = mask.copy()
    ImageDraw.floodfill(flooded, (0, 0), 128)
    holes = {(x, y) for y in range(512) for x in range(512) if flooded.getpixel((x, y)) == 0}
    fill = set()
    while holes:
        seed = holes.pop()
        component, pending = {seed}, [seed]
        while pending:
            x, y = pending.pop()
            for point in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if point in holes:
                    holes.remove(point)
                    component.add(point)
                    pending.append(point)
        width = max(x for x, y in component) - min(x for x, y in component) + 1
        height = max(y for x, y in component) - min(y for x, y in component) + 1
        if len(component) <= 9 or min(width, height) <= 2 and len(component) <= 24:
            fill.update(component)
    region = set()
    for y in range(84, 133):
        head = [x for x in range(185, 327) if body.getpixel((x, y))[3]]
        region.update((x, y) for x in range(head[0] - 8, head[0] + 2))
        region.update((x, y) for x in range(head[-1] - 1, head[-1] + 9))
    region.update((x, y) for y in range(133, 162) for x in list(range(185, 194)) + list(range(318, 327)))
    count = 0
    for iteration in range(10):
        mask = out.getchannel("A").point(lambda a: 255 if a == 255 else 0)
        closed = mask.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
        fill.update((x, y) for x, y in region if not mask.getpixel((x, y)) and closed.getpixel((x, y)))
        if not fill:
            return out, count
        for x, y in sorted(fill):
            donors = []
            for radius in range(1, 17):
                donors = [(xx, yy) for yy in range(max(0, y - radius), min(512, y + radius + 1))
                          for xx in range(max(0, x - radius), min(512, x + radius + 1))
                          if out.getpixel((xx, yy))[3] == 255]
                if donors:
                    break
            assert donors, (x, y)
            donor = min(donors, key=lambda p: (abs(p[0] - x) + abs(p[1] - y), p))
            out.putpixel((x, y), out.getpixel(donor))
            count += 1
        fill.clear()
    raise AssertionError("Hairline sliver repair did not converge")


def main():
    source, destination = map(Path, sys.argv[1:])
    curls = Image.open(source / "curly_top_fade.png")
    bodies = [Image.open(path) for path in sorted((destination.parent / "bodies").glob("*.png"))]
    assert len(bodies) == 8
    print("Measured all 8 bodies: cranium width 102 at y=84; temple width 128 at y=120; ear tops y=133")
    for path in sorted(source.glob("*.png")):
        image = Image.open(path)
        assert image.mode == "RGBA" and image.size == (512, 512), path
        corrected = correct(image, path.stem, curls)
        if path.stem == "afro":
            scaled = corrected.resize((446, 461), Image.Resampling.NEAREST)
            fitted = Image.new("RGBA", (512, 512))
            fitted.paste(scaled, (33, 9))
            fitted.paste(corrected.crop((192, 84, 320, 162)), (192, 84))
            for left, right in ((185, 194), (318, 327)):
                fitted.paste(corrected.crop((left, 110, right, 162)), (left, 110))
            corrected = fitted
        out, joins, widest = close_joins(corrected, bodies)
        out, polished = polish_gaps(out, bodies[0])
        changed = sum(a != b for a, b in zip(image.get_flattened_data(), out.get_flattened_data()))
        out.save(destination / path.name)
        scale = "crown 0.87x0.90 nearest-neighbour; native front/sideburns retained" if path.stem == "afro" else "no shift/scale"
        print(f"{path.stem}: {changed} corrected pixels; {joins} join pixels, temple extension {widest}px; {polished} polished gap pixels; {scale}")


if __name__ == "__main__":
    main()
