---
navigation:
  title: Crystal Catalyzer
  icon: ae2lt:crystal_catalyzer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:crystal_catalyzer
---

# Crystal Catalyzer

<Row>
  <BlockImage id="ae2lt:crystal_catalyzer" scale="4" />
</Row>

The **Crystal Catalyzer** is a specialty processing machine that uses a fluid plus a catalyst as its inputs. Typical uses include growing specialty crystals and their related materials.

## Slots and Capacity

| Slot | Capacity | Notes |
|------|----------|-------|
| Catalyst slot | 1,024 | Holds the catalyst item; the catalyst is **not consumed** during processing |
| Matrix slot | 1 | Optional Lightning Collapse Matrix for a yield bonus |
| Output slot | 1,024 | Processed output; written by the machine only, no external input accepted |
| Fluid slot | 16,000 mB | Fed through fluid pipes |
| FE Buffer | 1,000,000 FE | Built-in energy buffer |

## Operating Flow

1. Feed the target fluid into the fluid slot through pipes
2. Put the catalyst item into the catalyst slot
3. Supply FE
4. Once a recipe matches, the machine processes automatically
5. Finished output goes into the output slot

## Lightning Collapse Matrix Bonus

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

With a **Lightning Collapse Matrix** installed in the matrix slot, the Crystal Catalyzer's per-operation output is increased to **4×**. The matrix is not consumed during processing.

## Notes

* The Crystal Catalyzer is powered by **external FE** on its sides, not by AE from the ME network
* The machine itself is also an ME network device — connecting it to the network lets you feed it through AE2 Interfaces or Pattern Providers
* Supports Auto Export; output sides can be configured in the GUI
* The Crystal Catalyzer **does not** support Speed Cards
