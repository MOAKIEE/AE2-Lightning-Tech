# Overload Void Collapse Device Plan

## Reconstruction Source

This document was rebuilt from the historical assistant transcript that evolved
the idea from a directional detonator into a **placed AE2 machine** named
`Overload Void Collapse Device`.

The original `.cursor/plans/overload_void_collapse_device.plan.md` was an
untracked Cursor plan file and could not be recovered verbatim from Git. The
recoverable history contains the full design discussion, including:

- directed detonator feasibility and balance concerns
- rejection of direct portable quarry behavior
- switch from block destruction to void mineralization
- ore-generation data as the output source
- square-root diminishing returns
- fixed 4x4 chunk zones
- adjustable internal rate
- replacing ender pearls with mineralization matrices as the consumable

## Final Design Summary

**Overload Void Collapse Device** is a placed AE2 machine that consumes
Overload TNT and mineralization matrices to materialize ores from local worldgen
imprints.

It does not break blocks and does not spawn item entities. Instead, it reads the
local dimension/biome ore-generation profile, rolls weighted outputs, applies
zone pressure diminishing returns, and inserts results into the AE2 network or an
internal buffer.

Core fantasy:

> TNT does not create ore from nothing. It detonates an unstable mineralization
> matrix, causing the matrix to collapse into matter according to the local
> ore-generation imprint.

This preserves conservation-of-mass flavor and ties the feature to the existing
Lightning Collapse Matrix worldbuilding.

## Why This Replaced the Detonator Design

Rejected earlier versions:

- Handheld ore detonator: fun, but risks becoming a pocket quarry.
- Actual block destruction: terrain damage, protection issues, entity drops, TPS
  risk, and radar-like scanning pressure.
- Multi-stage collector/anchor tech line: too much invented machinery for a
  problem AE2 already solves.

Final version advantages:

- No terrain destruction.
- No dropped item entity flood.
- No ore-coordinate radar.
- Strong project identity: overload energy, TNT shockwave, collapse matrices.
- Output depends on the local dimension/biome ore table, so exploration remains
  meaningful.
- Balance is handled by consumables, AE2 power, zone pressure, and rate math.

## Machine Shape

Block:

- `OverloadVoidCollapseBlock`
- AE-style metal machine block.
- Opens a menu on use.

Block entity:

- `OverloadVoidCollapseBlockEntity`
- AE2 networked BE with an `IManagedGridNode`.
- Server-ticked machine logic.
- Owns inventory, rate, running state, and internal output buffer.

Suggested package layout:

- `block/OverloadVoidCollapseBlock.java`
- `blockentity/OverloadVoidCollapseBlockEntity.java`
- `logic/voidcollapse/OverloadVoidCollapseLogic.java`
- `logic/voidcollapse/OreGenerationCache.java`
- `logic/voidcollapse/OreGenerationScanner.java`
- `logic/voidcollapse/ZoneMiningData.java`
- `menu/OverloadVoidCollapseMenu.java`
- `client/OverloadVoidCollapseScreen.java`

## Slots

Final slot structure:

```text
Permanent / component slots:
  [collapse core]      machine quality, max rate, efficiency modifiers
  [energy component]   backup battery; primary power comes from AE2 grid
  [filter]             optional AE2 view cell / custom filter

Consumable slots:
  [overload TNT]       shockwave source
  [mineralization matrix] material template, consumed over time
```

The earlier pearl slot is intentionally removed. Pearls were useful for a
handheld teleport fantasy, but matrix consumption better solves lore and balance
for a placed machine.

### Collapse Core

The collapse core is not the same item as `lightning_collapse_matrix`. It should
be a lower-tier machine component in the same design family.

Suggested tiers:

- Basic Collapse Core: max rate 3
- Advanced Collapse Core: max rate 6
- Overloaded Collapse Core: max rate 10

The core is a permanent component and is not consumed during operation.

### Mineralization Matrix

Matrix is the real material template.

Suggested matrix tiers:

| Tier | Name | Unlocks | Consumed |
| --- | --- | --- | --- |
| I | Crude Mineralization Matrix | common ores | yes |
| II | Overloaded Mineralization Matrix | common + valuable ores | yes |
| III | Collapsed Pure Matrix | full spectrum, including custom late-game ores | yes |

Do not consume `lightning_collapse_matrix` directly per tick. It is too flagship
and expensive. Instead, high-tier matrices can be crafted from fragments,
byproducts, or recipes derived from the Lightning Collapse Matrix production
chain.

## Ore Generation Data

The machine depends on a reusable ore-generation scanner/cache.

### `OreGenerationScanner`

Scan after biome modifiers and worldgen registry data are available. The
scanner should run server-side and cache results.

Primary data path:

```text
Level / dimension
  -> LevelStem
  -> ChunkGenerator
  -> BiomeSource#possibleBiomes()
  -> BiomeGenerationSettings#features()
  -> PlacedFeature
  -> ConfiguredFeature
  -> OreConfiguration / compatible ore configs
  -> target BlockState + placement modifiers
```

For standard ores:

- Inspect `OreConfiguration`.
- Extract target block states.
- Parse `HeightRangePlacement`, `CountPlacement`, `RarityFilter`, and related
  placement modifiers when possible.
- Convert to an internal `OreEntry`.

For non-standard modded features:

- Keep a fallback "unknown ore feature" path.
- Prefer tag-driven recognition (`c:ores`) when direct config parsing fails.
- Keep the scanner tolerant: unknown feature data should not crash the server.

### `OreGenerationCache`

Suggested model:

```java
record OreEntry(
    ResourceKey<Biome> biome,
    ResourceKey<Level> dimension,
    Item output,
    int minY,
    int maxY,
    double expectedPerChunk,
    OreTier tier
) {}
```

Cache shape:

```text
Map<DimensionKey, Map<BiomeKey, List<OreEntry>>>
```

The collapse device asks the cache:

- What biomes are in the selected/current zone?
- What ores can generate here?
- Is the machine Y-level compatible with the ore's Y range?
- Does the inserted matrix tier allow this ore?
- Does the optional filter match?

## Zone Pressure

The final balancing model uses **fixed zones**, not per-chunk soft fields.

Zone size:

- 4x4 chunks
- 64x64 blocks
- aligned to world chunk coordinates

Zone key:

```java
int chunkX = SectionPos.blockToSectionCoord(pos.getX());
int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
int zoneX = Math.floorDiv(chunkX, 4);
int zoneZ = Math.floorDiv(chunkZ, 4);
long key = ChunkPos.asLong(zoneX, zoneZ);
```

Persistence:

- `ZoneMiningData extends SavedData`
- one instance per dimension
- stored through `ServerLevel#getDataStorage()`

State:

```java
static final class ZoneState {
    double pressure;
    long lastTickTime;
    long totalUses;
}
```

Decay:

```java
pressure *= Math.exp(-dt / TAU);
pressure = Math.max(0.0, pressure);
effectivePressure = Math.max(1.0, pressure);
```

Initial constants:

- `TAU = 12000 ticks` (10 minutes)
- remove zones when pressure drops below `0.01`

## Rate Math

The machine exposes rate `r = 1..10`.

Rate controls:

- TNT consumption speed
- matrix consumption speed
- power draw
- zone pressure growth
- short-term output speed

Key balancing rule:

```text
pressure added per tick is proportional to r^2
output attempt count is proportional to r
output multiplier is 1 / sqrt(effectivePressure)
```

This means:

- High rate gives short-term burst.
- High rate burns TNT and matrices faster.
- High rate rapidly increases zone pressure.
- Long-term steady-state output does not scale linearly with rate.

Core formula:

```text
final_yield = oregen_expected * BASE_YIELD_K * (1 / sqrt(zonePressure))
```

Initial constants:

| Constant | Value | Purpose |
| --- | --- | --- |
| `BASE_YIELD_K` | `0.6` | void mining tax; lower than hand mining |
| `ZONE_SIZE_CHUNKS` | `4` | fixed 4x4 chunk zone |
| `TAU` | `12000 ticks` | pressure decay |
| `RATE_MIN` | `1` | lowest machine rate |
| `RATE_MAX` | `10` | highest machine rate |
| `MATRIX_PER_RATE_DIVISOR` | `4` | matrix burns slower than TNT |

Consumption sketch:

```text
per operation step:
  TNT      = rate
  matrices = ceil(rate / 4)
  AE power = rate * energyPerRate
  pressure += rate * rate
```

Do not hide this math. The GUI should show pressure, rate, and estimated output
so players can choose between slow efficiency and fast burn.

## Tick Flow

Server-side loop:

```java
if (!running) return;
if (!hasGridPower()) return;
if (!hasTntFor(rate)) return;
if (!hasMatrixFor(rate)) return;
if (outputBufferFull() && !canInsertToGrid()) return;

ZoneMiningData zones = ZoneMiningData.get(level);
double pressure = zones.getEffectivePressure(worldPosition, gameTime);
double multiplier = BASE_YIELD_K / Math.sqrt(pressure);

int tntToConsume = rate;
int matrixToConsume = Math.ceilDiv(rate, MATRIX_PER_RATE_DIVISOR);

consumeTnt(tntToConsume);
consumeMatrix(matrixToConsume);
consumeAePower(rate);

List<ItemStack> produced = rollOreOutputs(
    level,
    worldPosition,
    insertedMatrixTier,
    optionalFilter,
    tntToConsume,
    multiplier
);

deliver(produced);
zones.addPressure(worldPosition, rate * rate, gameTime);
```

Roll logic:

1. Determine current zone.
2. Sample/resolve biome weights for the zone.
3. Get `OreEntry` candidates from `OreGenerationCache`.
4. Filter by dimension, biome, Y range, matrix tier, and optional filter slot.
5. Build weighted table using `expectedPerChunk`.
6. Roll output count based on consumed TNT and multiplier.
7. Optionally replace a small percentage with slag/byproduct.

## Output Delivery

The machine should never spawn bulk item entities.

Delivery chain:

1. If connected to an active AE2 grid, insert into `IStorageService`.
2. If insertion fails or the network is full, insert into the internal buffer.
3. If the internal buffer is full, pause the machine and show `OUTPUT_FULL`.

Internal buffer:

- `ItemStackHandler`
- suggested 64 slots
- exposed to menu
- can be extracted by automation if desired

Future optional extension:

- bind to existing wireless receiver/connector ecosystem, but this is not part
  of v1.

## GUI

Must show:

- Current dimension and zone coordinate.
- Current zone pressure.
- Current efficiency multiplier.
- Rate slider `1..maxRate`.
- Short-term estimated output.
- Steady-state warning/estimate.
- TNT count and required per operation.
- Matrix count and required per operation.
- Matrix tier and eligible ore groups.
- Optional filter contents.
- AE2 grid/storage status.
- Internal buffer fullness.

Suggested status values:

- `IDLE`
- `NO_GRID`
- `NO_POWER`
- `NO_TNT`
- `NO_MATRIX`
- `NO_ORE_DATA`
- `NO_VALID_OUTPUTS`
- `OUTPUT_FULL`
- `ACTIVE`

## Recipes and Items

New block:

- `overload_void_collapse_device`

New permanent components:

- `basic_collapse_core`
- `advanced_collapse_core`
- `overloaded_collapse_core`

New consumables:

- `crude_mineralization_matrix`
- `overloaded_mineralization_matrix`
- `collapsed_pure_matrix`

Optional byproduct:

- `mining_slag`

Recipe principles:

- Device requires late-midgame AE2 + AE2LT materials.
- Core tiers use overload processors/cores and machine frames.
- Matrix tiers should connect to the Lightning Collapse Matrix production chain
  but should not consume a full flagship matrix every tick.
- Overload TNT is the primary operating fuel.

## Compatibility Notes

Ore-generation scanning must be defensive:

- Do not assume all ores use vanilla `OreConfiguration`.
- Respect datapacks and biome modifiers by scanning after server data is ready.
- Unknown feature types should be ignored or logged at debug level.
- Tag-based fallback should use `c:ores` where possible.
- Keep cache rebuild hooks for data reload/server start.

AE2 behavior:

- Use AE2 grid storage insertion for outputs.
- Use AE2 power/grid checks for machine operation.
- Keep the machine paused rather than voiding items when storage is full.

Server safety:

- No item entity spam.
- No terrain modification.
- Zone data must prune low-pressure entries.
- Rate must be capped by the installed core.

## Implementation Phases

### Phase 1: Ore Data Foundation

- Implement `OreGenerationScanner`.
- Implement `OreGenerationCache`.
- Add debug dump command/logging to verify current pack ore data.
- Handle standard `OreConfiguration` first.

### Phase 2: Zone Pressure

- Implement `ZoneMiningData extends SavedData`.
- Add zone keying, decay, pressure add, serialization, pruning.
- Add debug command:
  - query current zone pressure
  - reset current zone

### Phase 3: Machine MVP

- Register block, block entity, menu, screen.
- Add core/TNT/matrix slots.
- Add internal buffer.
- Add rate slider.
- Consume TNT + matrix + AE power.
- Roll outputs from ore cache.
- Insert into AE2 storage or buffer.

### Phase 4: UX and Balance

- Add pressure and estimated output display.
- Add matrix tier filtering.
- Add optional filter slot.
- Add lang/model/recipe/guide entries.
- Tune constants from in-game testing.

### Phase 5: Extensions

- Optional mining slag.
- Optional wireless output binding.
- Optional Ponder/GuideME guide page.
- Optional JEI category showing local ore weights.

## Non-Goals for v1

- No handheld detonator.
- No actual block destruction.
- No dropped entity output path.
- No multi-block structure.
- No direct consumption of the flagship `lightning_collapse_matrix` per tick.
- No hard stop at high pressure; high pressure should remain "allowed but not
  economical".

## Final Design Sentence

**Overload TNT collapses mineralization matrices into local ore matter; 4x4 chunk
zones remember overuse, and adjustable rate controls how quickly the player burns
future value into present output.**
