---
navigation:
  title: Overload TNT
  icon: ae2lt:overload_tnt
  parent: lightning/lightning-index.md
  position: 90
item_ids:
  - ae2lt:overload_tnt
---

# Overload TNT

<Row>
  <BlockImage id="ae2lt:overload_tnt" scale="4" />
</Row>

**Overload TNT** is a firework with an Overload Singularity crammed inside. It is not a routine consumable on a production line — lighting it off feels more like a **controlled natural disaster**: lightning rains down from the sky and scours the area around the blast, rather than producing a single loud explosion.

Most of the time you use it to mine specific terrain, clear out stretches of hard rock, or demonstrate to visitors exactly why researchers around here need handrails.

## Crafting

<RecipeFor id="ae2lt:overload_tnt" />

The **Overload Singularity** at the center is what actually does the work; the gunpowder in the corners is just there to give it an excuse to go off.

## Detonation Behavior

After being ignited or activated, the Overload TNT has a fuse of roughly **4 seconds**. When it goes off, energy is released in this order:

1. A **lead-off lightning bolt** strikes directly above the blast center, followed by multiple bolts that fall outward in layered rings, **searing the terrain** as they go — this is the real payload of the Overload TNT, and its range is large.
2. At the moment of detonation, **every mob in a wide area around the blast center takes heavy lightning damage**. The closer to the center, the more lethal it gets, with effectively no survival margin at the core.
3. After the first wave, a period of **aftershocks** continues for several seconds, dropping random strikes across the area.
4. A short burst of **thunderstorm weather** is forced into the area around the blast center, which reverts after about 8 seconds.
5. All lightning produced during this sequence counts as **artificial lightning**, so it can be captured by the [Lightning Collector](lightning-collector.md) and converted into High Voltage Lightning in your ME network.

Overload TNT has good manners when it comes to cleanup: **bedrock, barrier blocks, end portal frames, and command blocks** are not broken, and area-protected zones are respected.

> Once Overload TNT detonates, it cannot be put back. Treat it the way you would treat a thunderstorm.

## Configuration and Disabling

The config option `overloadTnt.enableTerrainDamage` in `ae2lt-common.toml` (default **enabled**) controls the entire detonation behavior:

* **Enabled**: the "Detonation Behavior" above plays out in full — lightning rain, terrain searing, thunderstorm, and mob damage included.
* **Disabled**: Overload TNT **does nothing** when triggered — no lightning, no destruction, no damage. Effectively a dud.

## Usage Tips

* Place it on top of a **solid block**, give yourself plenty of distance, and prefer using it in open terrain. The blast radius is large enough that standing on the same map can still get you caught up in it.
* Used in low-altitude open ground, it lets a <ItemLink id="ae2lt:lightning_collector" /> absorb a huge amount of artificial lightning at once — much faster than relying on the regular [Tesla Coil](tesla-coil.md) cycle.
* If you only want it to break terrain and not wipe nearby mobs, move anything you want to keep away first.
