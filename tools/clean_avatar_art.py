"""Rebuild the .128 art cleanup from an unmodified aligned asset directory.

Usage: python tools/clean_avatar_art.py SOURCE_CHARACTER_DIR DEST_CHARACTER_DIR
Requires Pillow. All coordinates remain on the original 512px canvas.
"""
import sys
import shutil
from pathlib import Path
from statistics import median
from PIL import Image, ImageFilter


def clean_fringe(image, preserve_opaque=False):
    image = image.copy()
    alpha = image.getchannel("A")
    inside = alpha.filter(ImageFilter.MinFilter(7))
    solid = alpha.filter(ImageFilter.MinFilter(3))
    source = image.load()
    changes = []
    for y in range(512):
        for x in range(512):
            r, g, b, a = source[x, y]
            if preserve_opaque and a >= 240:
                continue
            if not a or inside.getpixel((x, y)) > 0:
                continue
            brightness = (r + g + b) / 3
            if brightness < 100 or max(r, g, b) - min(r, g, b) > 60:
                continue
            neighbors = []
            for yy in range(max(0, y - 8), min(512, y + 9)):
                for xx in range(max(0, x - 8), min(512, x + 9)):
                    nr, ng, nb, na = source[xx, yy]
                    if na >= 240 and solid.getpixel((xx, yy)) > 0:
                        neighbors.append((nr, ng, nb))
            if not neighbors:
                continue
            color = tuple(int(median(c[i] for c in neighbors)) for i in range(3))
            if brightness > sum(color) / 3 + 45:
                changes.append((x, y, (*color, a)))
    for x, y, color in changes:
        image.putpixel((x, y), color)
    return image, len(changes)


def clean_mouth(image, name):
    crop, dy = {
        "01_confident_grin": ((244, 201, 269, 213), -5),
        "02_game_face_shout": ((249, 199, 268, 217), -3),
        "03_athletic_mouthguard": ((245, 196, 270, 219), 0),
    }[name]
    out = Image.new("RGBA", image.size)
    out.paste(image.crop(crop), (crop[0], crop[1] + dy))
    return out


def clean_puffs(image):
    image = image.copy()
    original = image.copy()
    for y in range(51):
        for x in range(180, 336):
            if image.getpixel((x, y)) == (24, 18, 15, 255):
                image.putpixel((x, y), (0, 0, 0, 0))
    for y in range(75, 101):
        for x in range(190, 322):
            if ((x - 256) / 70) ** 2 + ((y - 78) / 24) ** 2 <= 1:
                if image.getpixel((x, y))[3] < 255:
                    sample = original.getpixel((x, 54 + (y - 75) % 24))
                    image.putpixel((x, y), sample if sample[3] == 255 else (24, 18, 15, 255))
    return image


def main():
    source, destination = map(Path, sys.argv[1:])
    for folder in ("mouths", "hairstyles", "beards", "headbands", "eyewear"):
        for path in sorted((source / folder).glob("*.png")):
            image = Image.open(path).convert("RGBA")
            original = image.copy()
            if folder == "mouths":
                image = clean_mouth(image, path.stem)
            if path.stem == "double_afro_puffs":
                image = clean_puffs(image)
            if folder == "mouths":
                count = 0
            else:
                image, count = clean_fringe(image, preserve_opaque=folder in ("headbands", "eyewear"))
            assert image.size == (512, 512)
            target = destination / folder / path.name
            if image.tobytes() == original.tobytes():
                if path.resolve() != target.resolve():
                    shutil.copyfile(path, target)
            else:
                image.save(target)
            print(f"{folder}/{path.name}: {count} pale edge pixels darkened")


if __name__ == "__main__":
    main()
