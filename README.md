# Freight Fever

Production and demand chains, Transport Fever style, for Minecraft **NeoForge 1.21.1**.

The mod itself never moves items between districts: **transport is entirely up to you**. It works with Create trains and packages, with plain vanilla chest minecarts and hoppers, or by hand. Optional compat with **Create** (tested with Create 6.x) and **Create: Loconautics** (sub-level trains).

## Blocks

- **Industry** — a producer with input/output buffers, recipes loaded from the datapack (data/freightfever/freight/industries/*.json), output-only variants (farms, mines), and a random **production factor** (0.5x to 2x) rolled at placement.
- **Platform** — the freight interface. 18-slot inventory, IN or OUT mode (sneak + right-click). OUT: anyone may extract. IN: anyone may insert. Industries link to platforms in a 16-block radius (consumers: 8).
- **Consumer** — towns and other demand sinks, satisfaction gauges per item, datapack-driven demands (data/freightfever/freight/demands/*.json). Satisfied consumers grow.

Demo chain shipped with the mod: iron mine -> steel mill -> town (bread + steel).

## Status

**pre-alpha 0.1 (0.1.1-pre-alpha)** — first milestone: core blocks, datapack loaders, demo chain.
Roadmap: pre-alpha 1.0 (town levels, management terminal), then train/package compatibility passes (Create, Loconautics).

Author: **naebulys** — MIT license.

## Testing

See docs/testing.md for the full walkthrough. Quick start: scripts/run-client.sh starts a Minecraft 1.21.1 dev client with the mod loaded, scripts/run-server.sh is the headless smoke test, and scripts/build-jar.sh [mods-dir] builds the jar (optionally copying it into a mods folder).
