# Art pipeline

Two-part workflow for Freight Fever assets: procedural 16x16 textures and
Blockbench models exported into the mod's assets. Everything committed here is
English; the `atelier/` directory is gitignored and holds working reports only.

## 1. Procedural textures

```
art/palettes.json          named color palettes (base/shade/light/detail/edge)
art/recipes/*.json         one recipe per texture
scripts/gen_textures.py    deterministic generator (Python 3 + Pillow)
```

A recipe looks like:

```json
{
  "name": "industry",
  "pattern": "machine_casing",
  "palette": "industrial_metal",
  "seed": 1001,
  "target": "block/industry",
  "options": { "accent": "#e8c832" }
}
```

- `target` is relative to `assets/freightfever/textures/`.
- The seed is mixed into a SHA-256 of the whole recipe, so every texture is
  byte-identical on regeneration. No unseeded global randomness is used.

CLI:

```
python3 scripts/gen_textures.py --list                 # list recipes
python3 scripts/gen_textures.py --recipe industry      # one texture
python3 scripts/gen_textures.py --all                  # all textures
python3 scripts/gen_textures.py --all --force          # rewrite even if identical
```

Without `--force`, a PNG that would be byte-identical is not rewritten.
Output is always exactly 16x16 RGBA.

### Patterns

`flat`, `metal_plate` (rivets + inner border), `machine_casing` (panel +
screws + grille), `crate` (planks + metal brackets), `ore` (stone + ore
blobs), `stripe_band` (diagonal hazard stripes), `gauge` (housing + colored
fill bar, `level` option), `item_round`, `item_ingot`, `item_wrench`.

All block patterns share vanilla-style shading: light top/left edge, dark
bottom/right edge, soft per-pixel grain.

## 2. Blockbench export

```
art/blockbench/*.bbmodel           Blockbench projects (see art/blockbench/README.md)
scripts/blockbench_export.py       exporter
```

```
python3 scripts/blockbench_export.py --all --dry-run
python3 scripts/blockbench_export.py --file art/blockbench/industry_block.bbmodel
```

Projects must use format Minecraft Block with 16x16x16 resolution. Cubes are
converted to Minecraft `elements` (from/to/origin/rotation/faces/UV); textures
referenced by path or embedded base64 are validated as 16x16 and copied into
`textures/`. Missing textures, faces without UV and cubes outside 0-16 abort
with an explicit error — broken JSON is never written silently. `--dry-run`
validates and prints the planned writes without touching disk.

## 3. Current texture set

| Texture | Recipe pattern | Used by |
|---|---|---|
| `block/industry` | machine_casing (industrial_metal) | block/industry |
| `block/platform_in` | machine_casing (painted_blue) | block/platform_in |
| `block/platform_out` | machine_casing (painted_amber) | block/platform_out |
| `block/consumer_satisfied` | gauge, green, level 0.9 | block/consumer_satisfied |
| `block/consumer_average` | gauge, amber, level 0.5 | block/consumer_average |
| `block/consumer_hungry` | gauge, red, level 0.15 | block/consumer_hungry |
| `item/steel_ingot` | item_ingot (steel) | item/steel_ingot |
| `item/wrench` | item_wrench (rusted_metal) | item/wrench |
