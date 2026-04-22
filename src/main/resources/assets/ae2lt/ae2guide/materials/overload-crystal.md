---
navigation:
  title: Overload Crystal
  icon: ae2lt:overload_crystal
  parent: index.md
  position: 40
item_ids:
  - ae2lt:overload_crystal
  - ae2lt:overload_crystal_dust
  - ae2lt:overload_crystal_block
  - ae2lt:flawless_budding_overload_crystal
  - ae2lt:flawed_budding_overload_crystal
  - ae2lt:cracked_budding_overload_crystal
  - ae2lt:damaged_budding_overload_crystal
  - ae2lt:small_overload_crystal_bud
  - ae2lt:medium_overload_crystal_bud
  - ae2lt:large_overload_crystal_bud
  - ae2lt:overload_crystal_cluster
---

# Overload Crystal

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

The **Overload Crystal** is the most fundamental and most important material in AE2 Lightning Tech. Nearly every mid- and late-game recipe needs it or one of its derivatives.

## How to Obtain

### Cultivating Budding Overload Crystal

The main source of Overload Crystals is the clusters that grow naturally on the surface of a **Budding Overload Crystal** block.

Budding Overload Crystal is obtained by placing an AE2 Budding Certus Quartz into a <ItemLink id="ae2:charger" /> and charging it. The tier of the input Budding Certus Quartz determines the tier of the resulting Budding Overload Crystal.

### Budding Tiers

There are four tiers of Budding Overload Crystal:

| Tier | Name | Decay |
|------|------|-------|
| Flawless | Flawless Budding Overload Crystal | Never decays |
| Flawed | Flawed Budding Overload Crystal | Low decay chance |
| Cracked | Cracked Budding Overload Crystal | Medium decay chance |
| Damaged | Damaged Budding Overload Crystal | High decay chance |

Every time a bud grows on an imperfect budding block, there is a chance the budding block decays one tier. When a Damaged budding block decays further, it becomes a regular Overload Crystal Block.

> **Silk Touch** prevents an imperfect budding block from decaying when broken. **Flawless Budding Overload Crystal** never decays.

### Bud Growth Stages

Overload Crystal buds grow through four stages:

1. **Small Overload Crystal Bud** → drops Overload Crystal Dust when broken
2. **Medium Overload Crystal Bud** → drops Overload Crystal Dust when broken
3. **Large Overload Crystal Bud** → drops Overload Crystal Dust when broken
4. **Overload Crystal Cluster** (fully grown) → drops **Overload Crystals** when broken (Fortune applies)

### Accelerated Growth

The <ItemLink id="ae2:growth_accelerator" /> works on Overload Crystal buds. Placing accelerators around a budding block dramatically speeds up bud growth.

## Obtaining a Flawless Budding Overload Crystal

The Flawless Budding Overload Crystal requires assembling a specific multiblock and waiting for a natural lightning strike.

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Structure requirements:

* Place a <ItemLink id="ae2:flawless_budding_quartz" /> (Flawless Budding Certus Quartz) in the center
* Place a <ItemLink id="ae2:fluix_block" /> on each of the four cardinal sides (E / W / N / S) at the same height
* Place an Overload Crystal Block on each of the four corners
* Place a Lightning Rod directly above the center

Once built, wait for a **natural lightning strike** to hit the Lightning Rod to complete the conversion.

> Only natural lightning can trigger this multiblock. Artificial lightning summoned by carrying an Overload Crystal will not trigger it.

When the strike lands, the eight surrounding outer blocks are consumed, and the center block becomes a Flawless Budding Overload Crystal.

## Derivatives

| Item | Use |
|------|-----|
| Overload Crystal Dust | Consumed by the Tesla Coil in HV mode; also used in several recipes |
| Overload Crystal Block | Building / decorative block, and also used to build the Flawless Budding Overload Crystal multiblock |
