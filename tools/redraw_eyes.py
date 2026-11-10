"""Draw .131 eyes at the new bodies' native eye and brow coordinates.

Usage: python -B tools/redraw_eyes.py CHARACTER_DIRECTORY
Magenta pixels are skin-sampling markers interpreted by SpriteCompose.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops


def eye_region(x, y):
    return 115 <= y <= 154 and (198 <= x <= 248 or 263 <= x <= 313)


def replacement_mask(body):
    mask = Image.new("L", (512, 512))
    assert body.size == (512, 512)
    draw = ImageDraw.Draw(mask)
    for left, right in ((198, 248), (263, 313)):
        draw.rounded_rectangle((left, 115, right, 154), radius=2, fill=255)
    return mask


def draw_eyes(mask, style):
    out = Image.new("RGBA", (512, 512))
    out.paste((224, 0, 224, 255), mask=mask)
    draw = ImageDraw.Draw(out)
    iris = ((83, 55, 34), (72, 45, 29), (103, 66, 36), (89, 58, 32),
            (69, 139, 177), (51, 124, 88))[style]
    ink = (17, 13, 18, 255)
    for side in range(2):
        def poly(points, color):
            draw.polygon([(511 - x if side else x, y) for x, y in points], fill=color)

        if style == 1:
            brow = [(201, 123), (213, 120), (226, 123), (243, 127), (243, 131),
                    (225, 127), (213, 125), (201, 126)]
        elif (style == 2 and side == 0) or style == 3:
            brow = [(201, 122), (212, 116), (220, 115), (231, 119), (243, 123),
                    (243, 126), (228, 122), (218, 120), (207, 124), (201, 125)]
        else:
            brow = [(200, 125), (211, 119), (218, 118), (228, 122), (234, 123),
                    (244, 129), (244, 132), (230, 129), (219, 124), (208, 125)]
        poly(brow, ink)
        poly([(204, 140), (210, 135), (221, 133), (233, 135), (240, 139),
              (241, 144), (236, 149), (230, 152), (216, 152), (207, 148), (204, 145)], (128, 0, 128, 255))
        top = 140 if style == 1 else 138 if style == 0 else 136
        bottom = 148 if style == 1 else 150
        white = [(208, 141), (214, top), (230, top), (237, 141), (237, 145),
                 (230, bottom), (217, bottom), (210, 147)]
        poly(white, (235, 237, 232, 255))
        poly([(209, 141), (214, top), (230, top), (236, 141), (232, top + 2), (215, top + 2)], (193, 196, 192, 255))
        center = 224 + (2 if style == 2 and not side else -2 if style == 2 else 0)
        radius = 6 if style == 3 else 8
        iris_mask = Image.new("L", (512, 512))
        iris_draw = ImageDraw.Draw(iris_mask)
        cx = 511 - center if side else center
        iris_draw.ellipse((cx - radius, 135, cx + radius, 151), fill=255)
        opening = Image.new("L", (512, 512))
        ImageDraw.Draw(opening).polygon([(511 - x if side else x, y) for x, y in white], fill=255)
        iris_mask = ImageChops.multiply(iris_mask, opening)
        out.paste(iris + (255,), mask=iris_mask)
        pupil = Image.new("RGBA", (512, 512))
        pd = ImageDraw.Draw(pupil)
        pd.ellipse((cx - 4, 136, cx + 4, 150), fill=ink)
        pd.rectangle((cx - 5, 141, cx - 2, 143), fill=(250, 249, 244, 255))
        pd.point((cx + 3, 147), fill=tuple(min(255, c * 5 // 4) for c in iris) + (255,))
        pupil.putalpha(ImageChops.multiply(pupil.getchannel("A"), opening))
        out = Image.alpha_composite(out, pupil)
        draw = ImageDraw.Draw(out)
        if style in (0, 1):
            points = [(208, top - 1), (217, top - 2), (230, top - 2), (237, 140)]
            draw.line([(511 - x if side else x, y) for x, y in points], fill=ink, width=2)
    assert all(not a or eye_region(i % 512, i // 512) for i, (*rgb, a) in enumerate(out.get_flattened_data()))
    return out


def main():
    root = Path(sys.argv[1])
    paths = sorted((root / "eyes").glob("*.png"))
    assert len(paths) == 6
    mask = replacement_mask(Image.open(root / "bodies/08_pale_ivory.png"))
    for style, path in enumerate(paths):
        draw_eyes(mask, style).save(path)
    print("Redrew all 6 eye styles inside native eye/brow regions; body PNGs untouched")


if __name__ == "__main__":
    main()
