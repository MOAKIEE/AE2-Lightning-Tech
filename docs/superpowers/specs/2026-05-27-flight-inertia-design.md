# Flight Inertia Toggle Design

## Summary

Add a flight inertia toggle to Creative Flight and Phase Flight modules. When ON (default), flight behaves like vanilla creative flight — gradual deceleration after releasing movement keys. When OFF, the player stops almost immediately after releasing keys.

Also: module config rows in DeviceHub already show only when a module is selected — this behavior is kept as-is.

## Configuration Data Layer

### New config key

- Key: `flight_inertia`
- Type: `ByteTag` (0 = OFF, 1 = ON)
- Default: ON (1)
- Label: `ae2lt.overload_armor.config.flight_inertia` (translatable)
- Hint: `ae2lt.overload_armor.config.flight_inertia.hint`

### FlightSubmodule changes

- `getConfigs()` returns two items: `speed_multiplier` (existing) + `flight_inertia` (new)
- `setConfig()` handles `flight_inertia` key
- New static accessor: `FlightSubmodule.isInertiaEnabled(ItemStack armor)` — reads from NBT Options, defaults to true

### PhaseFlightSubmodule changes

- Same as FlightSubmodule: add `flight_inertia` config, `setConfig()` handling, `isInertiaEnabled()` accessor

## Client Inertia Handler

### New class: `ClientFlightInertiaHandler`

- Registered via `@EventBusSubscriber(modid, value = Dist.CLIENT)`
- Listens to `PlayerTickEvent.Pre`

### Behavior

When player is flying (`abilities.flying`) and inertia is OFF for the active flight module:

- Check if movement keys are NOT pressed (W/A/S/D for horizontal, Space/Shift for vertical)
- If no horizontal input: multiply `player.getDeltaMovement().x` and `.z` by 0.1 per tick
- If no vertical input: multiply `player.getDeltaMovement().y` by 0.1 per tick
- This causes the player to decelerate to near-zero within 3-4 ticks

When inertia is ON: no intervention — vanilla creative flight behavior preserved.

### Deceleration constant

`INERTIA_OFF_DECAY = 0.1` — each tick, velocity is multiplied by this factor when the corresponding input is absent.

## Client Inertia State Sync

### Problem

Client needs to know the inertia setting even when DeviceHub screen is closed. Current sync only sends config when hub is open.

### Solution

Extend the existing `OverloadArmorState` client sync to include `flight_inertia` boolean.

#### Server side

- In `OverloadArmorState`, when syncing submodule active state to client, also sync the `flight_inertia` value of the active flight module
- Add a new sync field: `clientFlightInertia` (boolean)
- The existing sync packet or mechanism gets one additional boolean field

#### Client side

- `OverloadArmorState` stores `clientFlightInertia` in its client cache
- `ClientFlightInertiaHandler` reads from `OverloadArmorState.getClientFlightInertia()`

### Sync point

The existing `OverloadArmorState` already syncs active states to client on change. The inertia value is added to the same sync path — no new packet type needed, just extend the existing one.

## UI Changes

### DeviceHubScreen — renderModuleConfig

Current behavior: config rows are already only shown when a module is selected (`selectedModuleIndex >= 0`). This is the desired "show config only for selected module" behavior — no change needed.

### Config row rendering

With two config items per flight module (speed_multiplier + flight_inertia), the existing `renderModuleConfig` naturally shows both rows. `flight_inertia` renders as a BOOLEAN kind toggle (ON/OFF button). No layout change needed — the loop already handles up to 2 config rows.

## Lang keys

New translation keys needed:

- `ae2lt.overload_armor.config.flight_inertia` — "Flight Inertia"
- `ae2lt.overload_armor.config.flight_inertia.hint` — "Keep momentum after releasing keys"

## Files to modify

1. `FlightSubmodule.java` — add inertia config, setConfig handling, isInertiaEnabled
2. `PhaseFlightSubmodule.java` — same
3. `OverloadArmorState.java` — add clientFlightInertia sync field + accessor
4. Existing sync packet class — add flightInertia boolean field
5. `ClientFlightInertiaHandler.java` — new file, client tick handler
6. Lang .json — add translation keys
7. `FlightSpeedOptionTest.java` — add test for inertia config

## Files NOT modified

- `DeviceHubScreen.java` — already shows config per selected module, BOOLEAN kind already rendered
- `DeviceHubMenu.java` — config cycling already handles boolean and cycle types
- `ArmorFlightSpeedRules.java` — speed logic unchanged
