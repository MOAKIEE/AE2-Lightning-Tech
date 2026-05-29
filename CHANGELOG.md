# Changelog

## [1.0.10-forge] - 2026-05-27

### Fixed

#### Critical Bugs
- **Infinite recursion** in `OverloadedInterfaceLogic.allKeyTypes()` — called itself instead of `AEKeyTypes.getAll()`, causing `StackOverflowError` at runtime
- **NaturalLightningTransformationHandler** was a 42-line skeleton — restored full 260-line implementation with lightning strike rituals, structure detection, and particle effects
- **mods.toml variables not substituted** — missing `processResources` configuration caused `${mod_id}`, `${mod_version}` etc. to remain as raw strings, making Forge reject the JAR as "not a valid mod file"
- **pack_format mismatch** — `pack_format: 34` (1.20.3+) changed to `pack_format: 15` (1.20.1), which was causing Forge to reject the mod

#### NeoForge → Forge Migration (58 resource files)
- All recipe JSONs: `neoforge:conditions` → `conditions`, `neoforge:mod_loaded` → `forge:mod_loaded`, `neoforge:not` → `forge:not`, `neoforge:tag_empty` → `forge:tag_empty`, `neoforge:difference` → `forge:difference`
- All model JSONs: `neoforge_data` → `forge_data` (emissive rendering), `neoforge:composite` → `forge:composite` (composite model loader)

#### Eject Mode (was non-functional)
- Restored `EjectCapabilityMixin` — intercepts `BlockEntity.getCapability()` to proxy item/fluid handlers to host PatternProvider; includes rejecting handlers for offline hosts and recursion protection
- Implemented `GhostOutputBlockEntity` Forge capabilities — `IItemHandler` that inserts items into ME network via `ejectInsertToNetwork()`
- Added `OverloadedPatternProviderLogic.ejectInsertToNetwork()` public wrapper

### Changed
- `GRID_TICK_MAX`: 16 → 20 (aligned with source version)
- `TICK_MAX` (PowerSupply): 16 → 20
- `TickingRequest` max (Interface): 4 → 5
- AE2 version range in `mods.toml`: `[0,)` → `[15.2.0,)`

### Removed
- Duplicate `ForgeRegistries` import in `AE2LightningTech.java`
- Unused imports: `ILightningEnergyHandler`, `ModContainer`, `CapabilityManager`, `CapabilityToken`
- Stale commented-out dependencies in `build.gradle` (duplicate AdvancedAE/Applied Flux with wrong IDs)
- Orphan `ae2wtlib_version` from `gradle.properties`
- Unused `flywheel` and `ponder` optional dependencies from `mods.toml`

### Added
- `curios` optional dependency in `mods.toml`
- `mekanism` optional dependency in `mods.toml`
- `processResources` configuration in `build.gradle` for `mods.toml` and `pack.mcmeta` variable substitution
- Parchment maven repository in `build.gradle` (mappings channel requires network access to enable)

### Documentation
- Fixed NeoForge references in `api/package-info.java` and `api/frequency/package-info.java`
- Fixed "NeoForge FE" references in 4 Java source files to "Forge Energy (FE)"
- Added `CHANGELOG.md`
