"""Rebuild mouth and accessory art from the original .127 aligned assets.

Supplied hairstyle files are left untouched.

Usage: python tools/clean_avatar_art.py SOURCE_CHARACTER_DIR DEST_CHARACTER_DIR
Requires Pillow. All coordinates remain on the original 512px canvas.
"""
import sys
import shutil
from pathlib import Path
from statistics import median
from PIL import Image, ImageFilter, ImageDraw


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
    out = Image.new("RGBA", image.size)
    draw = ImageDraw.Draw(out)
    def lip(shade):
        return (shade, 0, shade, 255)
    if name == "01_confident_grin":
        rows = [(193, 195, 239, 272), (196, 198, 233, 278),
                (199, 201, 229, 282), (202, 204, 232, 279),
                (205, 207, 237, 274), (208, 209, 243, 268)]
    else:
        rows = [(193, 195, 239, 272), (196, 198, 233, 278),
                (199, 204, 229, 282), (205, 210, 232, 279),
                (211, 215, 238, 273), (216, 218, 244, 267)]
    for top, bottom, left, right in rows:
        draw.rectangle((left, top, right, bottom), fill=lip(120))
    draw.rectangle((253, 193, 258, 195), fill=(0, 0, 0, 0))
    if name == "01_confident_grin":
        draw.polygon([(232, 197), (238, 199), (273, 199), (279, 197),
                      (276, 203), (270, 206), (241, 206), (235, 203)], fill=lip(48))
        draw.rectangle((239, 199, 272, 201), fill=(235, 229, 209, 255))
        draw.rectangle((242, 202, 269, 202), fill=(165, 163, 151, 255))
        draw.rectangle((243, 207, 268, 208), fill=lip(168))
    else:
        draw.polygon([(239, 196), (272, 196), (278, 199), (278, 207),
                      (272, 213), (266, 216), (245, 216), (239, 213),
                      (233, 207), (233, 199)], fill=lip(40))
        draw.rectangle((240, 197, 271, 200), fill=(235, 229, 209, 255))
        draw.rectangle((242, 201, 269, 201), fill=(165, 163, 151, 255))
        if name == "02_game_face_shout":
            draw.rectangle((246, 210, 265, 213), fill=(128, 48, 54, 255))
            draw.rectangle((249, 210, 262, 211), fill=(168, 75, 76, 255))
        else:
            draw.rectangle((238, 200, 273, 206), fill=(42, 111, 109, 255))
            draw.rectangle((241, 200, 270, 203), fill=(114, 181, 165, 255))
            draw.rectangle((244, 200, 267, 201), fill=(182, 210, 186, 255))
            draw.rectangle((244, 207, 267, 208), fill=(21, 63, 63, 255))
        draw.rectangle((247, 217, 264, 218), fill=lip(156))
    return out


def clean_puffs(image, curls):
    image = image.copy()
    for y in range(51):
        for x in range(180, 336):
            if image.getpixel((x, y)) == (24, 18, 15, 255):
                image.putpixel((x, y), (0, 0, 0, 0))
    wings = Image.new("RGBA", image.size)
    wings.paste(image.crop((0, 0, 221, 512)), (0, 0))
    wings.paste(image.crop((291, 0, 512, 512)), (291, 0))
    base = Image.new("RGBA", image.size)
    crown = curls.crop((160, 4, 353, 88)).resize((193, 44), Image.Resampling.NEAREST)
    base.paste(crown, (160, 44))
    for x in range(198, 314):
        bottom = 89 + int(((x - 256) / 60) ** 2 * 9) + (x // 6) % 2
        for y in range(87, bottom + 1):
            color = curls.getpixel((x, 55 + (y - 87) * 2))
            base.putpixel((x, y), (11, 9, 12, 255) if y >= bottom - 1 else color)
    return Image.alpha_composite(base, wings)


def main():
    source, destination = map(Path, sys.argv[1:])
    for folder in ("mouths", "beards", "headbands", "eyewear"):
        for path in sorted((source / folder).glob("*.png")):
            image = Image.open(path).convert("RGBA")
            original = image.copy()
            if folder == "mouths":
                image = clean_mouth(image, path.stem)
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
