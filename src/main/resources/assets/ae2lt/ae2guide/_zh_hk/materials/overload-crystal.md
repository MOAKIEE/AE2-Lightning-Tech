---
navigation:
  title: 過載水晶
  icon: ae2lt:overload_crystal
  parent: materials/materials-index.md
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

# 過載水晶

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**過載水晶**是 AE2 閃電科技中最基礎也最重要的材料，幾乎所有中後期配方都需要它或其衍生產物。

## 取得方式

### 培育過載水晶母岩

過載水晶的主要來源是**過載水晶母岩**表面自然生長的水晶簇。

過載水晶母岩透過搭建多方塊結構並使用**雷擊**轉化對應等級的 AE2 賽特斯石英母岩獲得。詳細配方參見下文「取得過載水晶母岩」一節。

### 母岩等級

過載水晶母岩共有四個等級：

| 等級 | 名稱 | 衰減 |
|------|------|------|
| 無瑕 | 無瑕的過載水晶母岩 | 永不衰減 |
| 有瑕 | 有瑕的過載水晶母岩 | 較低機率衰減 |
| 開裂 | 開裂的過載水晶母岩 | 中等機率衰減 |
| 損壞 | 損壞的過載水晶母岩 | 較高機率衰減 |

每當過載水晶芽在母岩上生長一個階段，母岩都有一定機率衰減一個等級。當損壞的母岩繼續衰減時，它將變為普通的過載水晶塊。

> **絲綢之觸**可以防止不完美的母岩在被破壞時衰減。**無瑕的過載水晶母岩**永不衰減。

### 水晶芽的生長階段

過載水晶芽的生長分為四個階段：

1. **小型過載水晶芽** → 破壞掉落過載水晶粉
2. **中型過載水晶芽** → 破壞掉落過載水晶粉
3. **大型過載水晶芽** → 破壞掉落過載水晶粉
4. **過載水晶簇**（完全長成）→ 破壞掉落**過載水晶**（時運生效）

### 加速生長

<ItemLink id="ae2:growth_accelerator" /> 對過載水晶芽同樣有效。在母岩周圍放置加速器可顯著提升水晶的生長速率。

## 取得過載水晶母岩

過載水晶母岩透過搭建 3×3 多方塊結構並在中心正上方的避雷針處雷擊觸發轉化。有**精製**與**簡化**兩種結構。

### 精製結構（自然雷擊，同級轉化）

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

結構要求：

* 中心放置對應等級的 AE2 賽特斯石英母岩
* 東 / 西 / 南 / 北四個正方向同一高度各放置一個 <ItemLink id="ae2:fluix_block" />
* 四個對角各放置一個 <ItemLink id="ae2lt:overload_crystal_block" />
* 中心正上方放置一個避雷針

搭建完成後，等待**自然雷擊**命中避雷針即可**同級**轉化：

| 輸入（中心） | 輸出 |
|-----------|------|
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawless_budding_overload_crystal" /> |

> 精製結構只承認**自然雷擊**。由玩家攜帶過載水晶引來的人工閃電不會觸發該結構。

### 簡化結構（任意閃電，產物降一級）

如果手頭缺少過載水晶塊，可以用簡化結構生產除無瑕外的 3 個等級母岩：

* 中心放置對應等級的 AE2 賽特斯石英母岩
* 四個對角各放置一個 <ItemLink id="ae2:quartz_block" />
* 東 / 西 / 南 / 北四個正方向同一高度各放置一個 <ItemLink id="ae2:fluix_block" />
* 中心正上方放置一個避雷針

**任意閃電**命中避雷針即可觸發，產物比輸入等級低一級：

| 輸入（中心） | 輸出 |
|-----------|------|
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |

> 無瑕母岩無法透過簡化結構獲取——想要無瑕只能走精製結構 + 自然雷擊。

雷擊命中後，周圍八個外圍材料方塊會被消耗，中心方塊轉化為對應等級的過載水晶母岩。

## 衍生產物

| 物品 | 用途 |
|------|------|
| 過載水晶粉 | 特斯拉線圈高壓模式的消耗品，也用於部分配方 |
| 過載水晶塊 | 建築 / 裝飾方塊，也用於搭建無瑕母岩結構 |
