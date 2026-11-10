"""Host check mirroring SpriteCompose's z-order. Requires Pillow.

Run: python tools/avatar_samples.py [output directory] [before face assets] [before hair assets]
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
from fit_avatar_accessories import EMBEDDED_BAND_COLORS

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/character"
ORDER = ("bodies", "jersey", "numbers", "beards", "mouths", "eyes",
         "hairstyles", "headbands", "eyewear")
HAIR_COLORS = (0x2E2A2A, 0x4A2E1E, 0x6E4628, 0xA2562A,
               0xDAB458, 0xECE6D4, 0x8E9096, 0xC22E2E)
NUMBER_OFFSETS = {"nba_jerseys/grizzlies_retro.png": 48, "nba_jerseys/jazz.png": 24,
                  "nba_jerseys/supersonics.png": 40, "nba_jerseys/wizards.png": 16}


def tint_mouth(body, mouth):
    mouth = mouth.copy()
    pixels = mouth.load()
    for y in range(193, 219):
        left, right = body.getpixel((226, y)), body.getpixel((286, y))
        for x in range(229, 283):
            r, g, b, a = pixels[x, y]
            if a and r == b and g == 0:
                skin = tuple((left[c] * (286 - x) + right[c] * (x - 226)) // 60 for c in range(3))
                pixels[x, y] = tuple(c * r // (192 if i == 0 else 224) for i, c in enumerate(skin)) + (a,)
    return mouth


def composite(paths, hair_color=0, root=ROOT, replace_mouth=True, legacy_order=False):
    result = Image.new("RGBA", (512, 512))
    if not legacy_order:
        priorities = {name: i for i, name in enumerate(ORDER)}
        paths = sorted(paths, key=lambda p: priorities.get("jersey" if Path(p).parts[0].endswith("_jerseys") else Path(p).parts[0], len(ORDER)))
    body = next((Image.open(root / p) for p in paths if Path(p).parts[0] == "bodies"), None)
    hair = next((Image.open(root / p) for p in paths if Path(p).parts[0] == "hairstyles"), None)
    offset = next((NUMBER_OFFSETS.get(Path(p).as_posix(), 0) for p in paths
                   if Path(p).parts[0].endswith("_jerseys")), 0)
    for path in paths:
        layer = Image.open(root / path)
        assert layer.mode == "RGBA" and layer.size == (512, 512), path
        if not legacy_order and hair is not None and Path(path).parts[0] == "headbands":
            layer = layer.copy()
            layer.putdata([(0, 0, 0, 0) if hp[3] == 255 and hp not in EMBEDDED_BAND_COLORS else bp
                           for bp, hp in zip(layer.get_flattened_data(), hair.get_flattened_data())])
        if offset and Path(path).parts[0] == "numbers":
            positioned = Image.new("RGBA", (512, 512))
            positioned.paste(layer, (0, offset))
            layer = positioned
        if replace_mouth and body is not None and Path(path).parts[0] == "mouths":
            layer = tint_mouth(body, layer)
        if hair_color and Path(path).parts[0] in ("hairstyles", "beards"):
            color = HAIR_COLORS[hair_color]
            histogram = [0] * 256
            for r, g, b, a in layer.get_flattened_data():
                if a and r + g + b >= 40:
                    histogram[(r * 35 + g * 45 + b * 20) // 100] += 1
            count, accumulated, reference = sum(histogram), 0, 80
            for value, frequency in enumerate(histogram):
                accumulated += frequency
                if count and accumulated * 10 >= count * 9:
                    reference = max(80, value)
                    break
            pixels = []
            for r, g, b, a in layer.get_flattened_data():
                if a and r + g + b >= 40:
                    shade = min((r * .35 + g * .45 + b * .2) / reference, 1.5)
                    r, g, b = (min(int(((color >> shift) & 255) * shade), 255)
                               for shift in (16, 8, 0))
                pixels.append((r, g, b, a))
            layer = layer.copy()
            layer.putdata(pixels)
        result = Image.alpha_composite(result, layer)
    return result


def check_hairline(body, hair):
    combined = Image.alpha_composite(body, hair)
    mask = hair.getchannel("A").point(lambda a: 255 if a == 255 else 0)
    closed = mask.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))
    region = set()
    for y in range(84, 133):
        head = [x for x in range(185, 327) if body.getpixel((x, y))[3]]
        region.update((x, y) for x in range(head[0] - 8, head[0] + 2))
        region.update((x, y) for x in range(head[-1] - 1, head[-1] + 9))
    region.update((x, y) for y in range(133, 162) for x in list(range(185, 194)) + list(range(318, 327)))
    for x, y in region:
        assert mask.getpixel((x, y)) or not closed.getpixel((x, y)), ("1px hairline/ear sliver", x, y)
    for y in range(133, 137):
        for x in (192, 319):
            assert hair.getpixel((x, y))[3] == 255, ("missing sideburn", x, y)
            assert combined.getpixel((x, y)) == hair.getpixel((x, y)), ("sideburn gap", x, y)
    for y in range(84, 133):
        border = [x for x in range(185, 327) if body.getpixel((x, y))[3]]
        for x in (border[0], border[-1]):
            assert hair.getpixel((x, y))[3] == 255, ("exposed head border", x, y)
            assert combined.getpixel((x, y)) == hair.getpixel((x, y)), ("hairline gap", x, y)
    for x in range(205, 307):
        for y in range(84, 88):
            assert hair.getpixel((x, y))[3] == 255, ("exposed skull", x, y)


def accessory_samples(out, files, before):
    def grid(prefix, recipes, crop, skins, rows_per_page=5):
        width, height = (crop[2] - crop[0]) * 4, (crop[3] - crop[1]) * 4 + 24
        sheet = Image.new("RGB", (len(skins) * 2 * width, len(recipes) * height), "#404955")
        draw = ImageDraw.Draw(sheet)
        for row, (label, old, new) in enumerate(recipes):
            for col, body in enumerate(skins):
                for version, (root, paths) in enumerate(((before, old), (ROOT, new))):
                    avatar = composite([str(body.relative_to(ROOT))] + paths, root=root, legacy_order=not version)
                    detail = avatar.crop(crop).resize((width, height - 24), Image.Resampling.NEAREST)
                    x, y = (col * 2 + version) * width, row * height
                    sheet.paste(detail, (x, y), detail)
                    draw.text((x + 4, y + height - 20), f"{'AFTER' if version else 'BEFORE'} {body.stem} {label}", fill="white")
        sheet.save(out / f"{prefix}.png")
        page = 0
        for row in range(0, len(recipes), rows_per_page):
            for col in range(len(skins)):
                page += 1
                sheet.crop((col * 2 * width, row * height, (col + 1) * 2 * width,
                            min(len(recipes), row + rows_per_page) * height)).save(out / f"{prefix}-{page}.png")
    eyes = str(files["eyes"][0].relative_to(ROOT))
    bands = ["01_classic_white", "02_bulls_red", "03_stealth_black"]
    recipes = []
    for hair in files["hairstyles"]:
        for band in bands:
            paths = [eyes, str(hair.relative_to(ROOT)), f"headbands/{band}.png"]
            recipes.append((f"{hair.stem} {band}", paths, paths))
    grid("headbands-before-after-4x", recipes, (170, 72, 343, 166), [files["bodies"][0], files["bodies"][-1]])
    recipes = []
    for beard in files["beards"]:
        old_name = "02_trimmed_goatee" if beard.stem == "02_goatee" else beard.stem
        common = ["mouths/02_game_face_shout.png", eyes]
        old = common + [f"beards/{old_name}.png", "hairstyles/buzz_cut.png"]
        new = common + [str(beard.relative_to(ROOT)), "hairstyles/buzz_cut.png"]
        recipes.append((beard.stem, old, new))
    grid("beards-before-after-4x", recipes, (185, 128, 327, 248), files["bodies"])
    paths = [eyes, "hairstyles/afro.png"]
    grid("afro-before-after-4x", [("afro", paths, paths)], (125, 0, 390, 178), files["bodies"])
    recipes = []
    for eye in files["eyes"]:
        paths = [str(eye.relative_to(ROOT)), "hairstyles/buzz_cut.png", "eyewear/02_protective_clear_mask.png"]
        recipes.append((eye.stem, paths, paths))
    grid("mask-before-after-4x", recipes, (170, 110, 343, 193), files["bodies"])
    print("Wrote 4x before/after headbands x 15 hairstyles, 4 beards x 8 skins, afro x 8 skins, mask x every eyes layer x 8 skins")


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
    assert len({Image.open(p).getchannel("A").tobytes() for p in files["bodies"]}) == 1
    for path in files["mouths"]:
        mouth = Image.open(path)
        assert mouth.getbbox()[2] - mouth.getbbox()[0] == 54, path
        assert all(mouth.getpixel((x, y))[3] == 255 for y in (200, 201) for x in range(232, 280)), path
    for path in files["hairstyles"]:
        hair = Image.open(path)
        assert all(hair.getpixel((x, y))[3] == 255 for y in range(84, 88) for x in range(205, 307)), path
        for body in files["bodies"]:
            try:
                check_hairline(Image.open(body), hair)
            except AssertionError as error:
                raise AssertionError((path.name, body.name, error.args)) from error
    try:
        check_hairline(Image.open(files["bodies"][0]), Image.new("RGBA", (512, 512)))
        raise RuntimeError("Hairline check accepted an empty hairstyle")
    except AssertionError:
        pass
    probe = Image.open(files["hairstyles"][0]).copy()
    assert probe.getpixel((190, 100))[3] == 255
    probe.putpixel((190, 100), (0, 0, 0, 0))
    try:
        check_hairline(Image.open(files["bodies"][0]), probe)
        raise RuntimeError("Hairline check accepted a 1px sliver")
    except AssertionError as error:
        assert error.args[0][0] == "1px hairline/ear sliver", error
    print("PASS: 120 hair/body combinations have no exposed skull or head-border gaps; empty hair and injected 1px sliver rejected")
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

    edge_paths = files["hairstyles"] + files["beards"] + files["headbands"] + files["eyewear"] + files["eyes"]
    edge_sheet = Image.new("RGB", (6 * 350, ((len(edge_paths) + 5) // 6) * 174), "#232933")
    edge_draw = ImageDraw.Draw(edge_sheet)
    for i, path in enumerate(edge_paths):
        x, y = i % 6 * 350, i // 6 * 174
        for offset, color, body in ((0, "#070a0f", files["bodies"][0]), (175, "#eeeeee", files["bodies"][-1])):
            avatar = composite([str(body.relative_to(ROOT)), str(path.relative_to(ROOT))])
            face = avatar.crop((90, 0, 425, 280)).resize((175, 146), Image.Resampling.NEAREST)
            edge_sheet.paste(color, (x + offset, y, x + offset + 175, y + 146))
            edge_sheet.paste(face, (x + offset, y), face)
        edge_draw.text((x + 3, y + 150), path.stem, fill="white")
    edge_sheet.save(out / "edge-sheet.png")

    hair_sheet = Image.new("RGB", (5 * 380, 3 * 285), "#404955")
    hair_draw = ImageDraw.Draw(hair_sheet)
    for i, path in enumerate(files["hairstyles"]):
        avatar = composite([str(files["bodies"][-1].relative_to(ROOT)), str(path.relative_to(ROOT))])
        face = avatar.crop((130, 0, 385, 175)).resize((380, 261), Image.Resampling.NEAREST)
        x, y = i % 5 * 380, i // 5 * 285
        hair_sheet.paste(face, (x, y), face)
        hair_draw.text((x + 4, y + 264), path.stem, fill="white")
    hair_sheet.save(out / "hairline-after.png")
    if len(sys.argv) > 3:
        before_hair = Path(sys.argv[3])
        comparison = Image.new("RGB", (4 * 530, 15 * 378), "#404955")
        comparison_draw = ImageDraw.Draw(comparison)
        seams = Image.new("RGB", (4 * 692, 15 * 400), "#404955")
        seams_draw = ImageDraw.Draw(seams)
        for i, path in enumerate(files["hairstyles"]):
            for skin_col, body in enumerate((files["bodies"][0], files["bodies"][-1])):
                paths = [str(body.relative_to(ROOT)), str(path.relative_to(ROOT))]
                for col, root in enumerate((before_hair, ROOT)):
                    avatar = composite(paths, root=root)
                    face = avatar.crop((125, 0, 390, 175)).resize((530, 350), Image.Resampling.NEAREST)
                    x, y = (skin_col * 2 + col) * 530, i * 378
                    comparison.paste(face, (x, y), face)
                    comparison_draw.text((x + 4, y + 354), f"{'CORRECTED .130' if col else 'SUPPLIED'} {path.stem}", fill="white")
                    detail = avatar.crop((170, 72, 343, 166)).resize((692, 376), Image.Resampling.NEAREST)
                    sx, sy = (skin_col * 2 + col) * 692, i * 400
                    seams.paste(detail, (sx, sy), detail)
                    seams_draw.text((sx + 4, sy + 380), f"{'SHIPPED' if col else 'HAIR3'} {path.stem}", fill="white")
        comparison.save(out / "hairline-sheet.png")
        seams.save(out / "hairline-temples-4x.png")
        for page in range(3):
            comparison.crop((0, page * 5 * 378, comparison.width, (page + 1) * 5 * 378)).save(out / f"hairline-before-after-{page + 1}.png")
            seams.crop((0, page * 2000, seams.width, (page + 1) * 2000)).save(out / f"hairline-temples-4x-{page + 1}.png")
        print("Wrote zoomed hairline-sheet.png: supplied / corrected .130")
    else:
        hair_sheet.save(out / "hairline-sheet.png")

    fits = Image.new("RGB", (8 * 224, len(files["hairstyles"]) * 196), "#404955")
    colors = fits.copy()
    accessories = files["headbands"] + files["eyewear"]
    gear_sheet = Image.new("RGB", (len(accessories) * 2 * 224, len(files["hairstyles"]) * 196), "#404955")
    for row, hair in enumerate(files["hairstyles"]):
        hair_path = str(hair.relative_to(ROOT))
        def tile(target, avatar, col, label):
            x, y = col * 224, row * 196
            face = avatar.crop((90, 0, 425, 260)).resize((224, 174), Image.Resampling.NEAREST)
            target.paste("#111722" if col % 2 == 0 else "#eeeeee", (x, y, x + 224, y + 174))
            target.paste(face, (x, y), face)
            ImageDraw.Draw(target).text((x + 3, y + 178), label, fill="white")
        for col, body in enumerate(files["bodies"]):
            paths = [str(body.relative_to(ROOT)), hair_path]
            tile(fits, composite(paths), col, f"{hair.stem} skin {col + 1}")
            tile(colors, composite(paths, hair_color=col), col, f"{hair.stem} color {col}")
        for gear_col, gear in enumerate(accessories):
            for skin_col, body in enumerate((files["bodies"][0], files["bodies"][-1])):
                paths = [str(body.relative_to(ROOT)), hair_path, str(gear.relative_to(ROOT))]
                tile(gear_sheet, composite(paths), gear_col * 2 + skin_col, f"{hair.stem} {gear.stem[:12]}")
    fits.save(out / "hair-all-skins.png")
    colors.save(out / "hair-all-colors.png")
    gear_sheet.save(out / "hair-accessories.png")
    for page in range(3):
        gear_sheet.crop((0, page * 5 * 196, gear_sheet.width, (page + 1) * 5 * 196)).save(out / f"hair-accessories-{page + 1}.png")
    print(f"Wrote all 120 hair/skin fits, 120 hair/color combinations and {len(files['hairstyles']) * len(accessories) * 2} hair/accessory fits")

    jerseys = sorted(ROOT.glob("*_jerseys/*.png"))
    jersey_sheet = Image.new("RGB", (6 * 256, ((len(jerseys) + 5) // 6) * 286), "#404955")
    jersey_draw = ImageDraw.Draw(jersey_sheet)
    joins = Image.new("RGB", (8 * 530, 10 * 214), "#404955")
    chests = Image.new("RGB", (8 * 410, 10 * 488), "#404955")
    body_fits = Image.new("RGB", (8 * 180, 10 * 220), "#404955")
    for i, jersey in enumerate(jerseys):
        avatar = composite([str(files["bodies"][i % 8].relative_to(ROOT)), str(jersey.relative_to(ROOT)),
                            "numbers/23.png", "hairstyles/buzz_cut.png"])
        small = avatar.resize((256, 256), Image.Resampling.NEAREST)
        x, y = i % 6 * 256, i // 6 * 286
        jersey_sheet.paste(small, (x, y), small)
        jersey_draw.text((x + 5, y + 260), jersey.stem, fill="white")
        for target, crop, size, height in ((joins, (125, 215, 390, 310), (530, 190), 214),
                                           (chests, (155, 280, 360, 512), (410, 464), 488)):
            detail = avatar.crop(crop).resize(size, Image.Resampling.NEAREST)
            x, y = i % 8 * size[0], i // 8 * height
            target.paste(detail, (x, y), detail)
            ImageDraw.Draw(target).text((x + 4, y + size[1] + 4), jersey.stem, fill="white")
        for col, body in enumerate(files["bodies"]):
            fit = composite([str(body.relative_to(ROOT)), str(jersey.relative_to(ROOT)), "numbers/23.png"])
            detail = fit.crop((125, 215, 390, 512)).resize((180, 202), Image.Resampling.NEAREST)
            x, y = col * 180, i % 10 * 220
            body_fits.paste("#111722" if col % 2 == 0 else "#eeeeee", (x, y, x + 180, y + 202))
            body_fits.paste(detail, (x, y), detail)
            ImageDraw.Draw(body_fits).text((x + 3, y + 204), f"{jersey.stem} skin {col + 1}", fill="white")
        if i % 10 == 9:
            body_fits.save(out / f"jersey-all-skins-{i // 10 + 1}.png")
    jersey_sheet.save(out / "jersey-sheet.png")
    joins.save(out / "jersey-joins-zoom.png")
    chests.save(out / "jersey-numbers-zoom.png")
    for page in range(5):
        joins.crop((0, page * 2 * 214, joins.width, (page + 1) * 2 * 214)).save(out / f"jersey-joins-{page + 1}.png")
        chests.crop((0, page * 2 * 488, chests.width, (page + 1) * 2 * 488)).save(out / f"jersey-numbers-{page + 1}.png")
    bounds = [Image.open(ROOT / f"numbers/{n}.png").getbbox() for n in range(100)]
    assert all(b[3] + offset <= 512 for b in bounds for offset in NUMBER_OFFSETS.values())
    print(f"Wrote all {len(jerseys) * len(files['bodies'])} jersey/skin fits and zoomed joins/chests; number envelope:",
          (min(b[0] for b in bounds), min(b[1] for b in bounds), max(b[2] for b in bounds), max(b[3] for b in bounds)))

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
                    zoom_draw.text((x + 6, y + 496), f"{'AFTER .130' if col else 'BEFORE .127'} {body.stem} {mouth.stem}", fill="white")
        zoom.save(out / "faces-before-after.png")
        print(f"Wrote zoomed faces-before-after.png using {before}")
    print(f"PASS: {len(assets)} RGBA 512x512 layers; numbers 0-99; empty/error cases")
    if len(sys.argv) > 3:
        accessory_samples(out, files, Path(sys.argv[3]))
    print("PASS: all eight body silhouettes match; full-width mouths cover baked lip seams; all 15 hairstyles cover cranium top")
    print(f"Wrote 12 avatars + 32 all-skin mouth/puff samples + contact-sheet.png + edge-sheet.png to {out}")


if __name__ == "__main__":
    main()
