#!/usr/bin/env python3
"""Export Blockbench .bbmodel projects into Minecraft mod assets.

Reads art/blockbench/*.bbmodel and writes:
  - models  -> src/main/resources/assets/freightfever/models/...
  - textures -> src/main/resources/assets/freightfever/textures/...

Usage:
    python3 scripts/blockbench_export.py --all
    python3 scripts/blockbench_export.py --file art/blockbench/industry_block.bbmodel
    python3 scripts/blockbench_export.py --all --dry-run

Failures are explicit: a missing texture, a face without UV or a cube
outside 0-16 aborts the export with a clear message, never a broken JSON.
"""

import argparse
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BB_DIR = ROOT / "art" / "blockbench"
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "freightfever"
MODID = "freightfever"
SIZE = 16
FACES = ("down", "up", "north", "south", "west", "east")


class ExportError(Exception):
    pass


def resolve_texture(ref, textures, bb_path):
    """Return (name, PIL.Image) for a texture reference or embedded source."""
    if ref.startswith("data:"):
        try:
            payload = base64.b64decode(ref.split(",", 1)[1])
            img = Image.open(io.BytesIO(payload))
            img.load()
        except Exception as exc:
            raise ExportError(f"{bb_path.name}: undecodable embedded texture: {exc}")
        return None, img
    if ref in textures:
        return resolve_texture(textures[ref], textures, bb_path)
    name = Path(ref).name
    # referenced by path relative to the .bbmodel
    p = (bb_path.parent / ref).resolve()
    if not p.exists():
        raise ExportError(f"{bb_path.name}: texture file not found: {ref}")
    img = Image.open(p)
    img.load()
    return name, img


def check_texture(img, label, bb_path):
    if img.size != (SIZE, SIZE):
        raise ExportError(f"{bb_path.name}: texture {label} is {img.size}, expected 16x16")
    if img.mode not in ("RGBA", "RGB"):
        raise ExportError(f"{bb_path.name}: texture {label} has mode {img.mode}")


def convert_element(el, bb_path):
    frm, to = el.get("from"), el.get("to")
    if not frm or not to:
        raise ExportError(f"{bb_path.name}: element {el.get('name')!r} missing from/to")
    for i, axis in enumerate("xyz"):
        if not (0 <= frm[i] <= 16 and 0 <= to[i] <= 16):
            raise ExportError(f"{bb_path.name}: cube {el.get('name')!r} outside 0-16 on {axis}")
    out = {"from": frm, "to": to}
    if el.get("rotation"):
        origin = el.get("origin", [8, 8, 8])
        rot = el["rotation"]
        out["rotation"] = {"origin": origin, "axis": rot.get("axis", "y"),
                           "angle": rot.get("angle", 0)}
    faces = el.get("faces", {})
    mc_faces = {}
    for face in FACES:
        f = faces.get(face)
        if not f:
            continue
        uv = f.get("uv")
        if not uv or len(uv) != 4:
            raise ExportError(f"{bb_path.name}: cube {el.get('name')!r} face {face} has no UV")
        tex = f.get("texture")
        if tex is None:
            raise ExportError(f"{bb_path.name}: cube {el.get('name')!r} face {face} has no texture")
        entry = {"uv": uv, "texture": f"#{tex}" if isinstance(tex, str) else tex}
        if f.get("rotation"):
            entry["rotation"] = f["rotation"]
        mc_faces[face] = entry
    out["faces"] = mc_faces
    return out


def export_bbmodel(bb_path, dry_run=False):
    with open(bb_path, encoding="utf-8") as fh:
        project = json.load(fh)
    fmt = project.get("format_version") or project.get("format")
    resolution = project.get("resolution", {})
    if resolution.get("width", SIZE) != SIZE or resolution.get("height", SIZE) != SIZE:
        raise ExportError(f"{bb_path.name}: resolution must be 16x16, got {resolution}")

    textures = project.get("textures", [])
    tex_map = {}
    for i, t in enumerate(textures):
        if isinstance(t, dict):
            tex_map[t.get("name", f"tex{i}")] = t.get("source", "")
            tex_map[i] = t.get("source", "")
        else:
            tex_map[i] = t

    # export textures
    exported_textures = []
    for key, ref in tex_map.items():
        if not isinstance(ref, str) or not ref:
            continue
        _, img = resolve_texture(ref, tex_map, bb_path)
        name = key if isinstance(key, str) else None
        label = name or f"embedded#{key}"
        check_texture(img, label, bb_path)
        if name:
            rel = name if name.endswith(".png") else name + ".png"
            exported_textures.append((rel, img))

    # build model JSON
    elements = [convert_element(el, bb_path) for el in project.get("elements", [])]
    model = {
        "parent": "minecraft:block/cube",
        "textures": {k: f"{MODID}:{v[:-4]}" for k, v in
                     ((t.get("name", f"tex{i}"), t.get("name", f"tex{i}")) for i, t in enumerate(textures) if isinstance(t, dict))},
        "elements": elements,
    }
    # remap texture variables to modid paths
    var_map = {}
    for i, t in enumerate(textures):
        if isinstance(t, dict) and t.get("name"):
            var_map[t["name"]] = f"{MODID}:{t['name'][:-4] if t['name'].endswith('.png') else t['name']}"
    for el in model["elements"]:
        for face in el["faces"].values():
            ref = face["texture"]
            if isinstance(ref, str) and ref.startswith("#"):
                var = ref[1:]
                if var in var_map:
                    face["texture"] = var_map[var]
                else:
                    raise ExportError(f"{bb_path.name}: unknown texture variable {ref}")
    model["textures"] = {k: v for k, v in var_map.items()}

    out_model = ASSETS / "models" / "block" / (bb_path.stem + ".json")
    actions = [f"model -> {out_model.relative_to(ROOT)}"]
    for rel, img in exported_textures:
        actions.append(f"texture -> {(ASSETS / 'textures' / rel).relative_to(ROOT)}")
    if not dry_run:
        out_model.parent.mkdir(parents=True, exist_ok=True)
        with open(out_model, "w", encoding="utf-8") as fh:
            json.dump(model, fh, indent=2)
            fh.write("\n")
        for rel, img in exported_textures:
            dest = ASSETS / "textures" / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            img.save(dest, format="PNG")
    return actions


def main():
    ap = argparse.ArgumentParser(description="Export Blockbench .bbmodel files to mod assets")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--file", help="path to a single .bbmodel")
    ap.add_argument("--dry-run", action="store_true", help="validate and print actions without writing")
    args = ap.parse_args()

    if args.file:
        files = [Path(args.file)]
    elif args.all:
        files = sorted(BB_DIR.glob("*.bbmodel"))
    else:
        ap.error("nothing to do: use --all or --file PATH")

    if not files or (args.all and not files):
        print("no .bbmodel files found", file=sys.stderr)
        return 1

    rc = 0
    for f in files:
        try:
            actions = export_bbmodel(f, dry_run=args.dry_run)
            mode = "DRY-RUN" if args.dry_run else "OK"
            print(f"{mode} {f.name}")
            for a in actions:
                print(f"  {a}")
        except ExportError as exc:
            print(f"ERROR {f.name}: {exc}", file=sys.stderr)
            rc = 1
    return rc


if __name__ == "__main__":
    sys.exit(main())
