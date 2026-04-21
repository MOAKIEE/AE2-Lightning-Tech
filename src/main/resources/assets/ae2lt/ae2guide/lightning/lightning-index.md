---
navigation:
  title: Lightning System
  icon: ae2lt:lightning_collector
  parent: index.md
  position: 20
---

# Lightning System

Lightning is a brand-new storage type that this mod adds to the ME network. Just like items and fluids, you can see its amount in the terminal, store it inside storage cells, and have processing machines consume it.

## Two Tiers

| Tier | How it is produced |
|------|---------------------|
| **High Voltage Lightning** | A Lightning Collector hit by **artificial lightning** (summoned by a player carrying an Overload Crystal, or discharged by a Tesla Coil) |
| **Extreme High Voltage Lightning** | A Lightning Collector hit by **natural lightning** (a real thunderstorm bolt), or produced by a Tesla Coil upconverting High Voltage Lightning |

Most basic recipes only need High Voltage Lightning; a few advanced recipes require Extreme High Voltage. With a **Lightning Collapse Matrix** installed, some EHV recipes can be substituted with several times the amount of HV Lightning.

## Producing Lightning

* [Lightning Collector](lightning-collector.md) — injects Lightning into the ME network when struck by lightning
* [Tesla Coil](tesla-coil.md) — consumes Overload Crystal Dust / HV Lightning and FE to produce Lightning on demand

## Storing Lightning

* [Lightning Storage Cells](lightning-storage.md) — place one in an <ItemLink id="ae2:drive" /> to store Lightning in your ME network

## Consuming Lightning

The Lightning Simulation Room, Lightning Assembly Chamber, and Overload Processing Factory under [Processing Machines](../machines/machines-index.md) all pull Lightning from the ME network while working.

## Releasing Lightning

* [Overload TNT](overload-tnt.md) — a small, controllable disaster in a box: an explosion, followed by a rain of artificial lightning
