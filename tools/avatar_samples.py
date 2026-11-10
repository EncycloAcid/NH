"""Host check mirroring SpriteCompose's z-order. Requires Pillow.

Run: python tools/avatar_samples.py [output directory]
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/character"
ORDER = ("bodies", "jersey", "numbers", "mouths", "eyes", "beards",
         "hairstyles", "headbands", "eyewear")
HAIR_COLORS = (0x2E2A2A, 0x4A2E1E, 0x6E4628, 0xA2562A,
               0xDAB458, 0xECE6D4, 0x8E9096, 0xC22E2E)


def composite(paths, hair_color=0):
    result = Image.new("RGBA", (512, 512))
    for path in paths:
        layer = Image.open(ROOT / path)
        assert layer.mode == "RGBA" and layer.size == (512, 512), path
        if hair_color and Path(path).parts[0] in ("hairstyles", "beards"):
            color = HAIR_COLORS[hair_color]
            pixels = []
            for r, g, b, a in layer.getdata():
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
    sheet = Image.new("RGB", (4 * 272, 3 * 302), "#232933")
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
    sheet.save(out / "contact-sheet.png")
    print(f"PASS: {len(assets)} RGBA 512x512 layers; numbers 0-99; empty/error cases")
    print(f"Wrote 12 avatars + contact-sheet.png to {out}")


if __name__ == "__main__":
    main()
