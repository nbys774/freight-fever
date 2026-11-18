# Blockbench conventions

Place `.bbmodel` files in this directory (`art/blockbench/`). Export them into the
mod assets with:

```
python3 scripts/blockbench_export.py --all
python3 scripts/blockbench_export.py --file art/blockbench/industry_block.bbmodel --dry-run
```

## Project settings

- **Format**: Minecraft Block (`model_format: "minecraft_block"`). If you author a
  free-format project, the exporter converts Y-up axes for you.
- **Resolution**: 16 x 16 x 16. Anything else is rejected.
- **Naming**: `snake_case` file names, e.g. `industry_block.bbmodel`. The exported
  model lands at `models/block/<file_stem>.json`.

## Textures

- Reference a texture by path relative to the `.bbmodel` file, or embed it as a
  base64 `data:` URI. Every texture must be exactly 16x16 RGB/RGBA.
- Texture variables in faces (`"texture": "name.png"`) are remapped to
  `freightfever:<name>` in the exported model.

## Validation

The exporter never writes broken JSON: a missing texture file, a face without UV,
or a cube outside the 0-16 range aborts with an explicit error. Use `--dry-run` to
validate without writing.

## Example

`industry_block.bbmodel` is a two-cube block (base plinth + casing) using the
generated `block/industry.png` texture. Validate it with:

```
python3 scripts/blockbench_export.py --file art/blockbench/industry_block.bbmodel --dry-run
```
