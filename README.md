# Wool & Concrete Stairs & Slabs Backport

Server-side backport of Minecraft 26.3's wool stairs and slabs _and_ concrete stairs and slabs (all 16 colors each, 64 blocks) to 26.2, built with [Polymer](https://polymer.pb4.eu) + Fabric. Players need **only a resource pack** (for the shapes) — no client mod. Names, drops, recipes, shears-mining (wool) and pickaxe-mining (concrete) all work server-side. Designed to vanish the moment 26.3 ships: the blocks use their native `minecraft:` ids, so world data is identical and native takes over with zero migration.

## Requirements

- A Fabric 26.2 server
- **Fabric API**
- **Polymer**

## Install

1. Drop the jar into the server's `mods/` folder (alongside Fabric API and Polymer).
2. Make the generated resource pack reach players (it's marked **required**). Easiest path —
   the bundled **autohost** serves it automatically; enable it once in
   `config/polymer/auto-host.json`:
   ```json
   { "enabled": true }
   ```
   (Behind a proxy, also set `"forced_address"`.) Alternatively run `/polymer generate-pack`
   and serve `polymer/resourcepack.zip` yourself via `resource-pack` in `server.properties`.
3. Players accept the pack on join and see proper stairs/slabs. Craft them with the usual patterns (6 blocks → 4 stairs, 3 blocks → 6 slabs). Neither material has a stonecutter recipe, matching 26.3. Concrete stairs/slabs need a **pickaxe** to drop, like concrete itself.

## When 26.3 arrives

The blocks are real `minecraft:<color>_wool_stairs` / `_slab` and `minecraft:<color>_concrete_stairs` / `_slab`, so existing builds load **natively** with no conversion. The mod also refuses to load on 26.3 (`"minecraft": ">=26.2 <26.3"`), and even if forced it skips any family whose ids already exist (wool landed in 26.3-snapshot-2, concrete in snapshot-7). Just delete the jar.

## In Memory of Apollo

This mod was created because we wanted to make a statue of our dog Apollo, who recently passed away.
Our server runs some light mods but is 100% vanilla-client compatible so we couldn't use some of the other mods that backport wool stairs and slabs.
