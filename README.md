# TRMT Paving

Carries [The Roads More Travelled](https://modrinth.com/mod/trmt)'s erosion **one step further**:
keep walking on a fully eroded path and it becomes a paved block.

Minecraft 1.20.1 / Forge. Requires TRMT. MIT.

## What it does

TRMT's erosion chain ends at `trmt:eroded_coarse_dirt`. Walk on that long enough and this mod
turns it into `minecraft:dirt_path` (configurable).

The result is a single continuous progression — **game trail → packed dirt → road** — and
`dirt_path` is already in [Via Romana](https://modrinth.com/mod/via-romana)'s `path_block_ids`, so
the finished road registers as a route without any further setup.

## Why a mod and not a datapack

Not speed. **TRMT calls `ErosionMapManager.removeEntry(pos)` every time it converts a block**
(its own `ShovelItemMixin` is the reference implementation for this).

A datapack `setblock` never goes through that path. Worse, the resulting `dirt_path` is neither a
TRMT erosion target nor an `ErodedDirtBlock`, so its `randomTick` never runs either — meaning
**the entry for that position is never cleaned up again**. On a server where players walk a lot,
that map grows without bound.

## This is not a fork

It calls TRMT's public classes. No TRMT code is bundled or redistributed.

The same feature has been proposed upstream (`milkucha/trmt#59`). **If it is accepted, this mod is
no longer needed** and will be retired.

## Config

`config/trmt_paving-common.toml`:

| Key | Default | |
| - | - | - |
| `enabled` | `true` | Convert `trmt:eroded_coarse_dirt` when walked on |
| `resultBlock` | `minecraft:dirt_path` | The paved block. Pick something your path-detection mod recognises |
| `debugLog` | `false` | Log accumulation and threshold on every step. Only for diagnosing "I walked and nothing happened" |

## Build

```bash
export JAVA_HOME=/path/to/jdk-17      # JDK 17 ならどれでもよい
./gradlew build --no-daemon
# → build/libs/trmt_paving-<version>.jar
```

## Contributing

Issues and pull requests are welcome.
