---
navigation:
  title: 過載線纜
  icon: ae2lt:overloaded_cable
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_cable
  - ae2lt:overloaded_cable_white
  - ae2lt:overloaded_cable_orange
  - ae2lt:overloaded_cable_magenta
  - ae2lt:overloaded_cable_light_blue
  - ae2lt:overloaded_cable_yellow
  - ae2lt:overloaded_cable_lime
  - ae2lt:overloaded_cable_pink
  - ae2lt:overloaded_cable_gray
  - ae2lt:overloaded_cable_light_gray
  - ae2lt:overloaded_cable_cyan
  - ae2lt:overloaded_cable_purple
  - ae2lt:overloaded_cable_blue
  - ae2lt:overloaded_cable_brown
  - ae2lt:overloaded_cable_green
  - ae2lt:overloaded_cable_red
  - ae2lt:overloaded_cable_black
---

# 過載線纜

**過載線纜**是原版 AE2 密集線纜的增強版本，本身不設頻道上限。

## 核心特性

* **無頻道上限**：單根過載線纜本身無頻道瓶頸，可承載任意數量的頻道
* **由控制器決定網路總頻道**：整個過載網路的頻道總量取決於網路中**過載 ME 控制器**的數量——每個過載控制器為網路提供 128 頻道（預設值，可設定）
* **智慧型頻道分配**：過載網路使用**最大流演算法**替代原版 AE2 的 BFS 頻道分配，在複雜拓撲下能更高效地利用總頻道容量
* **密集線纜外觀**：視覺上與原版密集線纜一致
* **支援染色**：共有 17 種顏色變體，不同顏色互不連接（與原版 AE2 線纜染色機制一致）

## 頻道機制

與原版 AE2 線纜的固定頻道上限（普通線纜 8 頻道、密集線纜 32 頻道）不同，過載線纜採用以下規則：

1. **線纜本身無頻道瓶頸**：每根過載線纜都可以承載任意數量的頻道
2. **網路總頻道 = 過載控制器數 × 128**：整個網路可用的頻道總量由過載 ME 控制器的數量決定
3. **最大流分配**：可用頻道在網路中按最大流演算法智慧分配，而非沿路徑逐段扣減

對玩家而言意味著：

* 不再需要擔心某條主幹線纜的頻道被用完
* 只要網路總頻道足夠，所有設備都能獲得頻道
* 增加過載 ME 控制器即可擴展網路的頻道總量

> **重要**：過載線纜只有在**兩端都是過載設備**（過載線纜、過載 ME 控制器等）時才能發揮完整的無限容量特性。如果過載線纜與原版 AE2 設備直接相連，該連接仍會按原版規則計入頻道上限。

## 顏色變體

過載線纜共有 17 種顏色變體，包括福魯伊克斯色（預設）以及全部 16 種 Minecraft 染料顏色：

白色、橘色、洋紅色、淺藍色、黃色、淺綠色、粉紅色、灰色、淺灰色、青色、紫色、藍色、棕色、綠色、紅色、黑色。

不同顏色的過載線纜互不連接（與原版 AE2 線纜染色機制一致）。

## 使用方式

過載線纜的使用方式與原版密集線纜一致。直接放置在方塊側面即可接入網路。

## 適用場景

* 大型基地主幹線路，一根過載線纜可承擔多根密集線纜的頻道負載
* 以過載 ME 控制器為核心的高容量網路
* 簡化網路拓撲，不再需要針對頻道瓶頸進行布線設計
