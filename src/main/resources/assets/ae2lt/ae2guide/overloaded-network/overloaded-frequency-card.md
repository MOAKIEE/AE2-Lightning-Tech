---
navigation:
  title: Overloaded Frequency Card
  icon: ae2lt:overloaded_frequency_card
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_frequency_card
---

# Overloaded Frequency Card

<ItemGrid>
  <ItemIcon id="ae2lt:overloaded_frequency_card" />
  <ItemIcon id="ae2lt:advanced_wireless_overloaded_controller" />
</ItemGrid>

The **Overloaded Frequency Card** connects ME devices or parts directly to a wireless frequency without placing a Wireless Receiver. It is useful for temporary remote machines, quickly placed network parts, or wireless terminals that should extend an Overloaded ME network while carried.

The card only links targets. The frequency itself must still be transmitted by an **Advanced Wireless Overloaded Controller**. Frequencies broadcast by normal Wireless Overloaded Controllers cannot be used for frequency-card links.

## Binding

1. Put an **Advanced Wireless Overloaded Controller** on the target frequency
2. Hold an unbound Overloaded Frequency Card and **Shift + right-click** that controller
3. After binding, the card shows the frequency name and records the bound player

A bound card can only be used by the player who bound it. **Shift + right-click air** clears the binding, while keeping the auto-connect setting.

## Manual Linking

Hold a bound frequency card and right-click a supported ME network device or selectable part to toggle between connecting and disconnecting it.

Most AE2 network devices and selectable network parts can be linked. Cable bodies, controllers, and blocks without an available grid node cannot be linked. A target can only be linked to one frequency at a time; targets that belong to a different controller network are also rejected to avoid merging controller networks.

If the card reports that the frequency is temporarily unavailable, the transmitter is usually not loaded. The link will recover once the Advanced Wireless Overloaded Controller is loaded again.

## Auto Connect

When auto connect is enabled, placing blocks or AE2 parts will try to link the new target with an available carried frequency card.

Ways to toggle auto connect:

* Hold the card, then hold **Shift** and scroll the mouse wheel
* Use the “Toggle Frequency Card Auto Connect” keybind
* Install the card in a supported wireless terminal and use the terminal screen's auto-connect button

If you carry multiple auto-connect cards at once, the system may not know which one to use. When this happens, hold the target card or disable auto connect on the others.

## Curios Slot

When Curios is installed, the Overloaded Frequency Card can be placed in its dedicated Curios slot. A card in this slot participates in auto connect and keybind toggling; carrying other cards can still cause the multiple-candidate warning.

Manual right-click linking still requires holding the card, or holding a wireless terminal with a bound card installed.

## Wireless Terminals

When AE2WTLib is installed, the Overloaded Frequency Card can be installed in a wireless terminal's upgrade slot. The terminal screen adds frequency-card buttons for selecting a frequency, clearing the binding, or toggling auto connect.

Holding a wireless terminal with a bound card installed can link targets by right-clicking them, matching the held-card behavior. Terminal-installed cards can also participate in auto connect.
