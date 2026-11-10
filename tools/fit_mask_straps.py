import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/character"


def main():
    before = Path(sys.argv[1])
    bodies = [Image.open(p).convert("RGBA") for p in sorted((ROOT / "bodies").glob("*.png"))]
    for name in ("02_protective_clear_mask", "03_carbon_black_mask"):
        mask = Image.open(before / f"eyewear/{name}.png").convert("RGBA")
        changed = 0
        for y in range(138, 146):
            edges = {(min(x for x in range(140, 194) if body.getpixel((x, y))[3]),
                      max(x for x in range(319, 370) if body.getpixel((x, y))[3])) for body in bodies}
            assert len(edges) == 1, ("ear silhouettes differ", y, edges)
            left, right = edges.pop()
            for x in list(range(left, 172)) + list(range(340, right + 1)):
                mask.putpixel((x, y), mask.getpixel((172 if x < 256 else 339, y)))
                changed += 1
        mask.save(ROOT / f"eyewear/{name}.png")
        print(f"{name}: extended {changed} strap pixels to ear rims x={left},{right}, y=138..145")


if __name__ == "__main__":
    main()
