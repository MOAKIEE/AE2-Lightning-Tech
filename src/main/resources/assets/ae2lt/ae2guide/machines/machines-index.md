---
navigation:
  title: Processing Machines
  icon: ae2lt:lightning_simulation_room
  parent: index.md
  position: 30
---

# Processing Machines

AE2 Lightning Tech provides several processing machines that cover different material production needs. Except for the Atmospheric Ionizer (which consumes AE energy from the ME network), every machine connects to the ME network while also using external FE, and pulls Lightning from the ME network during processing.

## Machine Overview

| Machine | Core Use | Main Cost |
|---------|----------|-----------|
| [Lightning Simulation Room](lightning-simulation-chamber.md) | Simple conversion with 1~3 inputs | Lightning + FE |
| [Lightning Assembly Chamber](lightning-assembly-chamber.md) | Complex assembly with up to 9 inputs | Lightning + FE |
| [Overload Processing Factory](overload-processing-factory.md) | Item + fluid parallel processing | Lightning + FE |
| [Crystal Catalyzer](crystal-catalyzer.md) | Fluid + catalyst crystal growth | FE |
| [Atmospheric Ionizer](atmospheric-ionizer.md) | Weather control | AE energy |

## Shared Features

### Auto Export

Most machines support **Auto Export**. When enabled, a machine pushes its outputs to adjacent containers on the allowed sides once processing finishes. The allowed sides are set in the "Configure Output Sides" screen of the machine GUI.

### Speed Cards

Except for the Crystal Catalyzer, every processing machine accepts vanilla AE2 **Speed Cards** to increase processing speed, up to 4 cards.

### Lightning Substitution

Some recipes that require Extreme High Voltage Lightning support a **matrix substitution** mechanic: when a **Lightning Collapse Matrix** is installed in the machine, the recipe can be fulfilled by consuming several times the amount of High Voltage Lightning instead. The substitution ratio is defined per recipe.
