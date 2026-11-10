"""Host check mirroring SpriteCompose's z-order. Requires Pillow.

Run: python tools/avatar_samples.py [output directory] [before asset directory]
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/character"
ORDER = ("bodies", "jersey", "numbers", "mouths", "eyes", "beards",
         "hairstyles", "headbands", "eyewear")
HAIR_COLORS = (0x2E2A2A, 0x4A2E1E, 0x6E4628, 0xA2562A,
               0xDAB458, 0xECE6D4, 0x8E9096, 0xC22E2E)


def mouth_base(body):
    body = body.copy()
    pixels = body.load()
    for y in range(191, 211):
        left, right = pixels[226, y], pixels[286, y]
        for x in range(226, 287):
            blend = min(1, (x - 225) / 5, (287 - x) / 5, (y - 190) / 3, (211 - y) / 4)
            skin = tuple((left[c] * (286 - x) + right[c] * (x - 226)) // 60 for c in range(3))
            pixels[x, y] = tuple(int(skin[c] * blend + pixels[x, y][c] * (1 - blend))
                                 for c in range(3)) + (pixels[x, y][3],)
    return body


def composite(paths, hair_color=0, root=ROOT, replace_mouth=True):
    result = Image.new("RGBA", (512, 512))
    for path in paths:
        layer = Image.open(root / path)
        assert layer.mode == "RGBA" and layer.size == (512, 512), path
        if replace_mouth and Path(path).parts[0] == "bodies" and any(Path(p).parts[0] == "mouths" for p in paths):
            layer = mouth_base(layer)
        if hair_color and Path(path).parts[0] in ("hairstyles", "beards"):
            color = HAIR_COLORS[hair_color]
            pixels = []
            for r, g, b, a in layer.get_flattened_data():
                if a and r + g + b >= 40:
                    shade = min((r * .35 + g * .45 + b * .2) / 80, 1.5)
                    r, g, b = (min(int(((color >> shift) & 255) * shade), 255)
                               for shift in (16, 8, 0))
                pixels.append((r, g, b, a))
            layer = layer.copy()
            layer.putdata(pixels)
        result = Image.alpha_composite(result, layer)
    return result


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "avatar_samples"
    out.mkdir(parents=True, exist_ok=True)
    files = {folder: sorted((ROOT / folder).glob("*.png")) for folder in
             ("bodies", "hairstyles", "beards", "mouths", "eyes", "headbands", "eyewear")}
    assets = list(ROOT.rglob("*.png"))
    for asset in assets:
        with Image.open(asset) as layer:
            assert layer.mode == "RGBA" and layer.size == (512, 512), asset
    assert not (ROOT / "numbers/chest").exists()
    assert all((ROOT / f"numbers/{n}.png").is_file() for n in range(100))
    assert composite([]).getbbox() is None
    try:
        composite(["missing.png"])
        raise AssertionError("Missing layers must fail the host check")
    except FileNotFoundError:
        pass
    jerseys = ["nba_jerseys/lakers", "nba_jerseys/celtics", "college_jerseys/01_duke",
               "college_jerseys/02_unc", "nba_jerseys/heat", "college_jerseys/04_kansas"]
    numbers = [0, 7, 23, 34, 99, 11, 1, 3, 15, 30, 44, 55]
    sheet = Image.new("RGB", (8 * 200, 3 * 302 + 4 * 214), "#232933")
    draw = ImageDraw.Draw(sheet)
    for i, number in enumerate(numbers):
        chosen = {folder: str(paths[i % len(paths)].relative_to(ROOT)) for folder, paths in files.items()}
        chosen["jersey"] = jerseys[i % len(jerseys)] + ".png"
        chosen["numbers"] = f"numbers/{number}.png"
        if i % 4 == 0:
            for folder in ("beards", "headbands", "eyewear"):
                chosen.pop(folder)
        avatar = composite([chosen[key] for key in ORDER if key in chosen], i % 8)
        avatar.save(out / f"avatar-{i:02d}.png")
        x, y = (i % 4) * 272 + 8, (i // 4) * 302 + 8
        small = avatar.resize((256, 256), Image.Resampling.NEAREST)
        sheet.paste(small, (x, y), small)
        draw.text((x, y + 260), f"{i:02d} {Path(chosen['jersey']).stem} #{number}", fill="white")
    for row, mouth in enumerate(files["mouths"] + [None]):
        for col, body in enumerate(files["bodies"]):
            paths = [str(body.relative_to(ROOT))]
            if mouth:
                paths.append(str(mouth.relative_to(ROOT)))
            paths.append("hairstyles/double_afro_puffs.png")
            avatar = composite(paths)
            name = mouth.stem if mouth else "neutral-puffs"
            avatar.save(out / f"{body.stem}-{name}.png")
            x, y = col * 200, 906 + row * 214
            sheet.paste("#111722" if col % 2 == 0 else "#eeeeee", (x, y, x + 200, y + 190))
            face = avatar.crop((125, 0, 390, 250)).resize((200, 189), Image.Resampling.NEAREST)
            sheet.paste(face, (x, y), face)
            draw.text((x + 3, y + 192), f"{col + 1} {name[:22]}", fill="white")
    sheet.save(out / "contact-sheet.png")

    edge_paths = files["hairstyles"] + files["beards"] + files["headbands"] + files["eyewear"]
    edge_sheet = Image.new("RGB", (6 * 350, ((len(edge_paths) + 5) // 6) * 174), "#232933")
    edge_draw = ImageDraw.Draw(edge_sheet)
    for i, path in enumerate(edge_paths):
        avatar = composite([str(files["bodies"][0].relative_to(ROOT)), str(path.relative_to(ROOT))])
        face = avatar.crop((90, 0, 425, 280)).resize((175, 146), Image.Resampling.NEAREST)
        x, y = i % 6 * 350, i // 6 * 174
        for offset, color in ((0, "#070a0f"), (175, "#eeeeee")):
            edge_sheet.paste(color, (x + offset, y, x + offset + 175, y + 146))
            edge_sheet.paste(face, (x + offset, y), face)
        edge_draw.text((x + 3, y + 150), path.stem, fill="white")
    edge_sheet.save(out / "edge-sheet.png")

    if len(sys.argv) > 2:
        before = Path(sys.argv[2])
        zoom = Image.new("RGB", (4 * 530, len(files["mouths"]) * 520), "#232933")
        zoom_draw = ImageDraw.Draw(zoom)
        for row, mouth in enumerate(files["mouths"]):
            for skin_col, body in enumerate((files["bodies"][0], files["bodies"][-1])):
                paths = [str(body.relative_to(ROOT)), str(mouth.relative_to(ROOT)), "hairstyles/double_afro_puffs.png"]
                for col, root in enumerate((before, ROOT)):
                    avatar = composite(paths, root=root, replace_mouth=col == 1)
                    face = avatar.crop((125, 0, 390, 245)).resize((530, 490), Image.Resampling.NEAREST)
                    x, y = (skin_col * 2 + col) * 530, row * 520
                    zoom.paste("#111722" if skin_col == 0 else "#eeeeee", (x, y, x + 530, y + 490))
                    zoom.paste(face, (x, y), face)
                    zoom_draw.text((x + 6, y + 496), f"{'AFTER' if col else 'BEFORE'} {body.stem} {mouth.stem}", fill="white")
        zoom.save(out / "faces-before-after.png")
        print(f"Wrote zoomed faces-before-after.png using {before}")
    print(f"PASS: {len(assets)} RGBA 512x512 layers; numbers 0-99; empty/error cases")
    print(f"Wrote 12 avatars + 32 all-skin mouth/puff samples + contact-sheet.png + edge-sheet.png to {out}")


if __name__ == "__main__":
    main()
