# Manual testing guide

## Prerequisites

- JDK 21 available (set JAVA_HOME if java is not on PATH).

## Option A: dev client (recommended)

Run scripts/run-client.sh (needs a display). The mod is compiled and loaded automatically into a Minecraft 1.21.1 dev client.

For a headless smoke test, run scripts/run-server.sh instead: the server must start and log the loaded industries/demands without errors.

## Option B: launcher instance

Run scripts/build-jar.sh: it builds build/libs/freightfever-<version>.jar (and copies it to a mods directory if one is passed as the first argument). Drop that jar into the mods folder of any NeoForge 1.21.1 instance.

## Test walkthrough (demo chain: iron mine -> steel mill -> town)

1. Create a creative world, open the Freight Fever tab: take an Industry, two Platforms and a Consumer.
2. Place the Industry, right-click it: chat shows the production factor (x0.5 to x2.0) and the current recipe; keep right-clicking to cycle between iron_mine and steel_mill.
3. Set the recipe to iron_mine. Place a Platform nearby, sneak + right-click it until the chat reports mode OUT: the mine fills the platform with iron ingots (a hopper can drain it).
4. Place another Industry set to steel_mill, plus a Platform in mode IN within 16 blocks. Carry iron there (by hand, hopper, chest minecart or Create trains): the mill consumes 2 iron ingots and produces 1 steel ingot.
5. Place the Consumer, right-click it to check its demands (bread + steel). Put an IN Platform within 8 blocks and feed it bread and steel: the satisfaction gauges rise and the block turns amber then green.
6. Access rules check: a hopper must NOT be able to insert into an OUT platform, nor extract from an IN platform.
