# Overloaded Power Supply Plan

## Reconstruction Source

This document was rebuilt from the historical assistant transcript that created
`dev/overloaded-power-supply`, restored the feature from
`dev/overload-armor-wip`, and then debugged the runtime behavior.

The original `.cursor/plans/overloaded_power_supply.plan.md` was an untracked
Cursor plan file and could not be recovered verbatim from Git. The recoverable
history contains the actual branch/file-restore commands, later compile and
runtime fixes, and the final implemented code shape.

## Goal

Add an AE2-connected wireless FE distributor named **Overloaded Power Supply**.
It uses Applied Flux to push FE from an ME network to player-bound remote energy
targets.

The machine has two modes:

- `NORMAL`: simple one-send-per-target forwarding.
- `OVERLOAD`: high-throughput burst forwarding, unlocked only when a Flux Cell
  is installed as the cache backing store.

The feature must feel like an AE2 machine, not a standalone energy block:

- It participates in the AE2 grid through `IN_WORLD_GRID_NODE_HOST`.
- It gets FE from the ME network through Applied Flux.
- It sends energy wirelessly using stored target positions/faces.
- It exposes state through an AE-style menu/screen.

## Restored File Scope

The historical restore command pulled the following feature-related files from
`dev/overload-armor-wip` into the new branch:

- `src/main/java/com/moakiee/ae2lt/block/OverloadedPowerSupplyBlock.java`
- `src/main/java/com/moakiee/ae2lt/blockentity/OverloadedPowerSupplyBlockEntity.java`
- `src/main/java/com/moakiee/ae2lt/client/OverloadedPowerSupplyScreen.java`
- `src/main/java/com/moakiee/ae2lt/client/WirelessConnectorRenderer.java`
- `src/main/java/com/moakiee/ae2lt/item/OverloadedWirelessConnectorItem.java`
- `src/main/java/com/moakiee/ae2lt/logic/OverloadedPowerSupplyLogic.java`
- `src/main/java/com/moakiee/ae2lt/logic/energy/WirelessEnergyAPI.java`
- `src/main/java/com/moakiee/ae2lt/menu/OverloadedPowerSupplyMenu.java`
- `src/main/java/com/moakiee/ae2lt/network/WirelessConnectorUsePacket.java`
- relevant registry, model, language, blockstate, loot table, and recipe files

During integration, unrelated Overload Armor workbench registrations were
removed because their classes were not part of this feature.

## Core Classes

### `OverloadedPowerSupplyBlock`

Simple AE block wrapper:

- Extends `AEBaseEntityBlock<OverloadedPowerSupplyBlockEntity>`.
- Opens `OverloadedPowerSupplyMenu` on right click.
- Keeps behavior server-side and returns sided success.

### `OverloadedPowerSupplyBlockEntity`

Owns persistent state:

- `PowerMode mode`: `NORMAL` or `OVERLOAD`.
- One-slot internal inventory for an Applied Flux Flux Cell.
- `List<WirelessConnection>` storing dimension, target position, and clicked
  face.
- Cached `StorageCell` view for the installed Flux Cell.
- `OverloadedPowerSupplyLogic` as the grid ticking service.

Important invariants:

- The cell storage view is cached and invalidated only when the cell slot
  changes.
- Direction values loaded from NBT/network must be range-checked before
  `Direction.from3DDataValue`.
- Removing/unloading the BE flushes RAM-backed buffers back to the ME network.

### `OverloadedPowerSupplyLogic`

Implements `IGridTickable` and owns all runtime energy behavior.

Tick flow:

1. Check Applied Flux availability.
2. Check active AE2 grid.
3. Ensure `BufferedStorageService` proxy exists for the current grid storage.
4. Decide mode:
   - `wantsOverload = mode == OVERLOAD`
   - `hasCell = bufferCapacity > 0`
   - `overloadActive = wantsOverload && hasCell`
5. If overload is selected without a cell, clear tickets and set `NO_CELL`.
6. Advance cache history and set cost multiplier:
   - normal: `1x`
   - overload: `2x`
7. Every 20 ticks, clear invalid connections.
8. Resolve valid targets.
9. Run normal or overload dispatch.

Normal mode:

- Clear overload tickets.
- For each target, perform one `send`.
- Works without a cell as plain AppFlux forwarding.

Overload mode:

- Requires a Flux Cell.
- Limits active targets to `OVERLOAD_MAX_CONNECTIONS = 64`.
- Uses sentinel scans to detect active targets.
- Stores tickets for recently active targets.
- Bursts with `sendMulti(..., OVERLOAD_MAX_CALLS = 64)`.
- Applies `2x` FE cost through `BufferedMEStorage`.

### `WirelessEnergyAPI`

Provides high-level wireless FE helpers.

Key detail: Applied Flux `EnergyCapCache` expects a host position and side, then
resolves the actual target using `pos.relative(side)`. The wireless target stores
the target block and clicked face, so this API creates a virtual host one block
outside the target:

- `virtualHostPos = target.pos.relative(clickedFace)`
- `hostSide = clickedFace.getOpposite()`

`sendMulti` loops AppFlux `send` up to a max call count and stops on the first
non-positive result.

For batch preload paths, unlimited Applied Flux transfer rate must be capped to
avoid huge single-tick extraction:

- `TRANSFER_RATE == Long.MAX_VALUE` uses `Integer.MAX_VALUE` per target as the
  preload hint.

### `AppFluxBridge` and `AppFluxAccess`

Final design removes reflection from hot paths.

`AppFluxBridge`:

- Contains no Applied Flux imports.
- Runtime-checks AppFlux presence with `Class.forName`.
- Exposes safe defaults when AppFlux is absent.
- Delegates to `AppFluxAccess` when present.

`AppFluxAccess`:

- Has direct Applied Flux imports.
- Exposes `FluxKey.of(EnergyType.FE)`.
- Reads `AFConfig.getFluxAccessorIO()`.
- Creates `EnergyCapCache`.
- Calls `EnergyHandler.send` directly.
- Detects Flux Cells and reads Flux Cell capacity.

`build.gradle` must keep Applied Flux both compile-visible and runtime-present:

```gradle
compileOnly "curse.maven:applied-flux-965012:7429565"
runtimeOnly "curse.maven:applied-flux-965012:7429565"
```

### `BufferedMEStorage`

Acts as the FE inventory seen by Applied Flux.

Modes:

- Cell-backed cache: installed Flux Cell is the physical cache.
- RAM-backed cache: transient buffer used by legacy batch paths.
- Pass-through: no cell and no RAM capacity, direct ME extraction.

Cell-backed cache semantics:

- The cell is cache/storage backing, not a target to forcibly fill.
- Reads drain the cell first.
- If the cell cannot satisfy current demand, pull from ME into the cell once,
  sized by `recentConsumption + currentDemand`, capped by free cell space.
- If the cell still cannot satisfy demand, fall back to ME for the remainder.
- Consumption history records only FE actually extracted from the cell, not the
  ME fallback.
- Do not add a post-tick "fill the cell" pass; cache should be demand-driven.

Overflow safety:

- Multiplication by cost multiplier uses saturating arithmetic.
- Recent consumption sums use saturating addition.
- History slot accumulation uses saturating addition.

## GUI and Player Flow

### Binding Targets

`OverloadedWirelessConnectorItem` and `WirelessConnectorUsePacket` manage
target binding. A connection stores:

- target dimension
- target position
- clicked face

The power supply later uses this to emulate a virtual Applied Flux accessor.

### Menu

`OverloadedPowerSupplyMenu` exposes:

- buffer capacity
- buffered energy
- connection count
- selected mode
- active ticket count
- status
- last transferred amount

The cell slot only accepts Flux Cells.

Shift-click invariant:

- Non-Flux Cell items shift-clicked from player inventory must not be deleted.
- Return `ItemStack.EMPTY` before attempting to move invalid items into the cell
  slot.

### Screen Statuses

Statuses:

- `IDLE`
- `APPFLUX_UNAVAILABLE`
- `NO_CELL`
- `NO_GRID`
- `NO_CONNECTIONS`
- `NO_VALID_TARGETS`
- `NO_NETWORK_FE`
- `TARGET_UNSUPPORTED`
- `TARGET_BLOCKED`
- `ACTIVE`

`lastTransferAmount` is reset at tick start and accumulated across all sends in
that tick.

## Registration Checklist

Required integration points:

- Register `ModBlocks.OVERLOADED_POWER_SUPPLY`.
- Register corresponding `BlockEntityType`.
- Register menu type and screen.
- Add custom slot semantic:
  `Ae2ltSlotSemantics.OVERLOADED_POWER_SUPPLY_CELL`.
- Add creative tab entry.
- Register `AECapabilities.IN_WORLD_GRID_NODE_HOST` for the BE.
- Bind BE to block and add item drops in common setup.
- Add blockstate, item/block model, lang entries, and loot table.

## Known Bugs Found and Fixed

- Compilation broke because unrelated Overload Armor Workbench registrations
  were restored without their classes.
- Missing custom slot semantic broke menu compilation.
- Screen/menu/capability registration was incomplete.
- Non-Flux Cell shift-click in the menu could delete player items.
- GUI transfer amount showed only the last push instead of per-tick total.
- Overload mode could appear to work without a cell; final behavior is
  `NO_CELL` and no transfer when overload is explicitly selected without a cell.
- Recreating `StorageCell` views every call caused excessive object churn and
  potential state inconsistency; fixed with BE-level cache invalidation.
- Cell cache originally filled aggressively; final design is demand-driven and
  records only actual cell throughput.
- Energy math could overflow `long`; fixed with saturating arithmetic.
- Reflection bridge was replaced by direct AppFlux API access through
  `compileOnly` + guarded delegation.

## Verification Plan

Minimum checks after changes:

1. `./gradlew.bat compileJava`
2. `./gradlew.bat runClient` or `runClient2` using the project root configured
   `run`/`run2` directories, not a temporary worktree with empty configs.
3. In-game checks:
   - Place power supply on AE2 grid.
   - Bind one valid FE target.
   - Normal mode without cell transfers FE.
   - Overload mode without cell shows `NO_CELL` and transfers nothing.
   - Insert Flux Cell, switch overload, verify burst transfer and ticket count.
   - Verify buffer amount changes only under real demand.
   - Remove cell/unload BE and ensure no crash or item deletion.

## Open Follow-ups

- Add dedicated debug logging behind a config flag for cache refill/extract
  events.
- Consider a game test for menu quick-move invariants.
- Consider a small integration test wrapper around `WirelessEnergyAPI.Target`
  virtual-host direction math.
