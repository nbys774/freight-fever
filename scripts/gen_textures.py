#!/usr/bin/env python3
"""Deterministic 16x16 texture generator for Freight Fever.

Each texture is fully described by a recipe JSON in art/recipes/ and is
byte-identical on every regeneration (seed derived from the recipe only).

Usage:
    python3 scripts/gen_textures.py --all
    python3 scripts/gen_textures.py --list
    python3 scripts/gen_textures.py --recipe industry
    python3 scripts/gen_textures.py --all --force   # rewrite even if identical
"""

import argparse
import hashlib
import json
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "art"
RECIPES_DIR = ART / "recipes"
PALETTES_FILE = ART / "palettes.json"
TEXTURES_DIR = ROOT / "src" / "main" / "resources" / "assets" / "freightfever" / "textures"

SIZE = 16


def hex_to_rgb(value):
    value = value.lstrip("#")
    if len(value) != 6:
        raise ValueError(f"bad hex color: {value!r}")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def load_palettes():
    with open(PALETTES_FILE, encoding="utf-8") as fh:
        raw = json.load(fh)
    return {name: {role: hex_to_rgb(c) for role, c in colors.items()}
            for name, colors in raw.items()}


def recipe_seed(recipe):
    """Stable seed derived only from the recipe content."""
    blob = json.dumps(recipe, sort_keys=True).encode("utf-8")
    return int.from_bytes(hashlib.sha256(blob).digest()[:8], "big")


def new_canvas(pal):
    img = Image.new("RGBA", (SIZE, SIZE), pal["base"] + (255,))
    return img, img.load()


def shade_edges(px, pal):
    """Vanilla-style readable shading: dark bottom/right edge, light top/left."""
    for x in range(SIZE):
        px[x, 0] = pal["light"] + (255,)
        px[x, SIZE - 1] = pal["edge"] + (255,)
    for y in range(SIZE):
        px[0, y] = pal["light"] + (255,)
        px[SIZE - 1, y] = pal["edge"] + (255,)


def noise_overlay(px, rng, pal, amount=0.5):
    """Deterministic per-pixel grain between shade and light."""
    for y in range(SIZE):
        for x in range(SIZE):
            if rng.random() < amount:
                c = pal["shade"] if rng.random() < 0.5 else pal["light"]
                r, g, b, a = px[x, y]
                px[x, y] = (c[0], c[1], c[2], a)


# ---------------------------------------------------------------- patterns

def pattern_flat(recipe, pal, rng):
    img, px = new_canvas(pal)
    noise_overlay(px, rng, pal, amount=recipe.get("options", {}).get("noise", 0.25))
    shade_edges(px, pal)
    return img


def pattern_metal_plate(recipe, pal, rng):
    img, px = new_canvas(pal)
    noise_overlay(px, rng, pal, amount=0.2)
    shade_edges(px, pal)
    # rivets in the four corners
    for cx, cy in ((2, 2), (SIZE - 3, 2), (2, SIZE - 3), (SIZE - 3, SIZE - 3)):
        px[cx, cy] = pal["detail"] + (255,)
        px[cx + 1, cy] = pal["light"] + (255,)
        px[cx, cy + 1] = pal["light"] + (255,)
    # inner border one pixel in
    for i in range(1, SIZE - 1):
        px[i, 1] = pal["shade"] + (255,)
        px[i, SIZE - 2] = pal["shade"] + (255,)
        px[1, i] = pal["shade"] + (255,)
        px[SIZE - 2, i] = pal["shade"] + (255,)
    return img


def pattern_machine_casing(recipe, pal, rng):
    img, px = new_canvas(pal)
    noise_overlay(px, rng, pal, amount=0.15)
    shade_edges(px, pal)
    opts = recipe.get("options", {})
    accent = hex_to_rgb(opts["accent"]) if "accent" in opts else pal["detail"]
    # recessed panel
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = pal["shade"] + (255,)
    for y in range(4, 12):
        for x in range(4, 12):
            px[x, y] = pal["base"] + (255,)
    # panel highlight
    for x in range(4, 12):
        px[x, 4] = pal["light"] + (255,)
    # screws on panel corners
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[cx, cy] = pal["detail"] + (255,)
    # ventilation grille on the right strip
    for y in range(4, 12):
        px[13, y] = accent + (255,) if (y % 2 == 0) else pal["detail"] + (255,)
    return img


def pattern_crate(recipe, pal, rng):
    img, px = new_canvas(pal)
    shade_edges(px, pal)
    metal = hex_to_rgb(recipe.get("options", {}).get("metal", "#6b6f76"))
    # planks: vertical boards with alternating tone
    for x in range(SIZE):
        tone = pal["light"] if x % 4 < 2 else pal["base"]
        for y in range(1, SIZE - 1):
            px[x, y] = tone + (255,)
        px[x, 1] = pal["shade"] + (255,)
    # plank seams
    for x in (3, 7, 11):
        for y in range(1, SIZE - 1):
            px[x, y] = pal["detail"] + (255,)
    # metal corner brackets
    for cx, cy in ((0, 0), (SIZE - 2, 0), (0, SIZE - 2), (SIZE - 2, SIZE - 2)):
        for dx in range(2):
            for dy in range(2):
                px[cx + dx, cy + dy] = metal + (255,)
    # diagonal brace
    for i in range(2, 14):
        px[i, 14 - i] = metal + (255,)
        px[i, 15 - i] = metal + (255,)
    return img


def pattern_ore(recipe, pal, rng):
    img, px = new_canvas(pal)
    noise_overlay(px, rng, pal, amount=0.45)
    shade_edges(px, pal)
    opts = recipe.get("options", {})
    ore = hex_to_rgb(opts.get("ore_color", "#d8d8d8"))
    ore_dark = hex_to_rgb(opts.get("ore_dark", "#9a9a9a"))
    # ore blobs
    for _ in range(opts.get("blobs", 5)):
        cx = rng.randint(2, SIZE - 3)
        cy = rng.randint(2, SIZE - 3)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0), (0, -1)):
            x, y = cx + dx, cy + dy
            if 0 <= x < SIZE and 0 <= y < SIZE:
                px[x, y] = ore + (255,)
        px[cx, cy] = ore_dark + (255,)
    return img


def pattern_stripe_band(recipe, pal, rng):
    img, px = new_canvas(pal)
    opts = recipe.get("options", {})
    c1 = hex_to_rgb(opts.get("color_a", "#e8c832"))
    c2 = hex_to_rgb(opts.get("color_b", "#1c1c1c"))
    width = opts.get("width", 2)
    for y in range(SIZE):
        for x in range(SIZE):
            band = ((x + y) // width) % 2 == 0
            px[x, y] = (c1 if band else c2) + (255,)
    shade_edges(px, pal)
    return img


def pattern_gauge(recipe, pal, rng):
    """Dark housing with a colored fill bar; fill level is an option."""
    img, px = new_canvas(pal)
    opts = recipe.get("options", {})
    fill = hex_to_rgb(opts.get("fill_color", "#46c46a"))
    fill_light = hex_to_rgb(opts.get("fill_light", "#74e394"))
    level = max(0.0, min(1.0, opts.get("level", 0.75)))
    # housing
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = pal["base"] + (255,)
    # window
    for y in range(4, 12):
        for x in range(2, 14):
            px[x, y] = pal["detail"] + (255,)
    # fill bar
    filled = int(round(12 * level))
    for y in range(5, 11):
        for x in range(2, 2 + filled):
            px[x, y] = fill + (255,)
    for x in range(2, 2 + filled):
        px[x, 5] = fill_light + (255,)
    shade_edges(px, pal)
    return img


def _item_base(pal):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return img, img.load()


def pattern_item_round(recipe, pal, rng):
    img, px = _item_base(pal)
    opts = recipe.get("options", {})
    cx = cy = SIZE // 2
    r = opts.get("radius", 5)
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r:
                px[x, y] = pal["base"] + (255,)
            if d <= r - 1.5:
                px[x, y] = pal["light"] + (255,) if y < cy else pal["base"] + (255,)
            if d > r - 1:
                px[x, y] = pal["edge"] + (255,)
    return img


def pattern_item_ingot(recipe, pal, rng):
    img, px = _item_base(pal)
    # trapezoid ingot, vanilla style
    for y in range(5, 11):
        inset = 2 if y < 8 else 4
        for x in range(inset, SIZE - inset):
            px[x, y] = pal["base"] + (255,)
    for x in range(3, 13):
        px[x, 5] = pal["light"] + (255,)
    for x in range(5, 11):
        px[x, 10] = pal["edge"] + (255,)
    for y in range(5, 11):
        px[2 if y < 8 else 4, y] = pal["shade"] + (255,)
        px[13 if y < 8 else 11, y] = pal["edge"] + (255,)
    # top highlight stripe
    for x in range(4, 12):
        px[x, 6] = pal["light"] + (255,)
    return img


def pattern_item_wrench(recipe, pal, rng):
    img, px = _item_base(pal)
    head = pal["base"]
    hi = pal["light"]
    dk = pal["edge"]
    # handle: vertical bar, slightly diagonal
    for y in range(6, 15):
        x = 7 + (y - 6) // 4
        px[x, y] = head + (255,)
        px[x + 1, y] = dk + (255,)
        px[x, y - 1] = hi + (255,) if y > 6 else px[x, y]
    # open jaw at top
    for x in range(5, 11):
        px[x, 3] = head + (255,)
    for y in range(3, 6):
        px[5, y] = head + (255,)
        px[10, y] = head + (255,)
    px[5, 3] = hi + (255,)
    px[10, 3] = hi + (255,)
    px[6, 4] = (0, 0, 0, 0)
    px[7, 4] = (0, 0, 0, 0)
    px[8, 4] = (0, 0, 0, 0)
    px[9, 4] = (0, 0, 0, 0)
    px[7, 5] = (0, 0, 0, 0)
    px[8, 5] = (0, 0, 0, 0)
    return img


PATTERNS = {
    "flat": pattern_flat,
    "metal_plate": pattern_metal_plate,
    "machine_casing": pattern_machine_casing,
    "crate": pattern_crate,
    "ore": pattern_ore,
    "stripe_band": pattern_stripe_band,
    "gauge": pattern_gauge,
    "item_round": pattern_item_round,
    "item_ingot": pattern_item_ingot,
    "item_wrench": pattern_item_wrench,
}


def generate(recipe, palettes):
    name = recipe["name"]
    pattern = recipe["pattern"]
    if pattern not in PATTERNS:
        raise ValueError(f"recipe {name}: unknown pattern {pattern!r}")
    pal = palettes[recipe["palette"]]
    rng = random.Random(recipe_seed(recipe))
    img = PATTERNS[pattern](recipe, pal, rng)
    if img.size != (SIZE, SIZE):
        raise ValueError(f"recipe {name}: output is {img.size}, expected 16x16")
    if img.mode not in ("RGBA", "RGB"):
        img = img.convert("RGBA")
    return img


def main():
    ap = argparse.ArgumentParser(description="Freight Fever deterministic texture generator")
    ap.add_argument("--all", action="store_true", help="generate every recipe")
    ap.add_argument("--list", action="store_true", help="list recipes and exit")
    ap.add_argument("--recipe", help="generate a single recipe by name")
    ap.add_argument("--force", action="store_true", help="rewrite PNGs even if identical")
    args = ap.parse_args()

    palettes = load_palettes()
    recipes = []
    for path in sorted(RECIPES_DIR.glob("*.json")):
        with open(path, encoding="utf-8") as fh:
            recipes.append(json.load(fh))

    if args.list:
        for r in recipes:
            print(f"{r['name']:24s} {r['pattern']:16s} {r['palette']:18s} -> {r['target']}")
        return 0

    if not (args.all or args.recipe):
        ap.error("nothing to do: use --all, --list or --recipe NAME")

    selected = recipes if args.all else [r for r in recipes if r["name"] == args.recipe]
    if args.recipe and not selected:
        print(f"error: no recipe named {args.recipe!r}", file=sys.stderr)
        return 1

    written = skipped = 0
    for recipe in selected:
        img = generate(recipe, palettes)
        out = TEXTURES_DIR / (recipe["target"] + ".png")
        out.parent.mkdir(parents=True, exist_ok=True)
        if out.exists() and not args.force:
            import io
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            if out.read_bytes() == buf.getvalue():
                skipped += 1
                print(f"skip (identical): {out.relative_to(ROOT)}")
                continue
        img.save(out, format="PNG")
        written += 1
        print(f"wrote: {out.relative_to(ROOT)}")
    print(f"done: {written} written, {skipped} skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
