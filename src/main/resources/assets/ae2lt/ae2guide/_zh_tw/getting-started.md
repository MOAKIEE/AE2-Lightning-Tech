---
navigation:
  title: 入門指南
  icon: ae2lt:overload_crystal
  parent: index.md
  position: 10
---

# 入門指南

本頁介紹從零開始到搭建第一條閃電產線的完整流程。

## 前置條件

開始前請確保已經具備一個可運行的 AE2 ME 網路，至少包含：

* 一個 <ItemLink id="ae2:controller" /> 或一張無控制器的小型網路
* 少量 <ItemLink id="ae2:certus_quartz_crystal" />、<ItemLink id="ae2:fluix_crystal" /> 與 <ItemLink id="ae2:fluix_block" />
* 一個 <ItemLink id="minecraft:lightning_rod" /> 與若干等級的 AE2 賽特斯石英母岩

## 第一步：取得過載水晶

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**過載水晶**是本模組最基礎的材料，幾乎所有配方都需要用到它或其衍生產物。

獲得方式是培育**過載水晶母岩**，而母岩本身透過**多方塊雷劈儀式**由 AE2 賽特斯石英母岩轉化得來：

1. 搭建 3×3 多方塊結構：中心放置對應等級的賽特斯石英母岩；精製結構的四角放 <ItemLink id="ae2lt:overload_crystal_block" /> + 四邊 <ItemLink id="ae2:fluix_block" />；簡化結構的四角放 <ItemLink id="ae2:quartz_block" /> + 四邊 <ItemLink id="ae2:fluix_block" />
2. 中心正上方放置一個 <ItemLink id="minecraft:lightning_rod" />
3. 等待雷擊——精製結構需要**自然雷擊**，全 fluix 的簡化結構接受**任意閃電**
4. 取出轉化完成的**過載水晶母岩**，放置在世界中等待表面自然生長水晶芽

過載水晶母岩的行為與賽特斯石英母岩基本一致：

* 水晶芽會依次生長為小型 → 中型 → 大型 → **過載水晶簇**
* <ItemLink id="ae2:growth_accelerator" /> 對過載水晶同樣有效
* 破壞**未完全長成**的水晶芽會掉落**過載水晶粉**
* 破壞**完全長成**的過載水晶簇會掉落**過載水晶**，時運附魔生效
* 不完美的母岩每次生長都可能衰減一個等級;絲綢之觸可以避免被破壞時的衰減

更多細節請參閱 [過載水晶](materials/overload-crystal.md)。

## 第二步：透過閃電轉化取得起步材料

本模組的許多起步材料需要透過**閃電轉化**獲得——將物品丟棄在地面上，再讓雷電擊中這些掉落物以完成轉化。

<ItemImage id="ae2lt:overload_alloy" scale="2" float="left" />

典型的閃電轉化產物包括：

* **過載合金錠**、**過載電路板**、**過載壓印模板**等基礎材料
* **閃電收集器**、**閃電模擬室**等第一批機器
* **電鳴水晶**與**閃電儲存組件 I** 等閃電體系的起步物品

觸發閃電轉化有兩種方式：

* **自然雷暴**：雷暴天氣下，野外的掉落物會被自然雷擊命中
* **主動召喚**：攜帶過載水晶站在露天位置約 10 秒，將在周圍召喚一次人工閃電

> 人工閃電可以觸發閃電轉化配方，但**不能**觸發下一步「無瑕母岩」所需的結構轉化。結構轉化只接受自然雷擊。

## 第三步：取得無瑕的過載水晶母岩

<ItemImage id="ae2lt:flawless_budding_overload_crystal" scale="2" float="left" />

**無瑕的過載水晶母岩**不會衰減，是大規模產線的基礎。只有**精製結構 + 自然雷擊**能產出無瑕母岩：

<GameScene zoom="4" background="transparent">
  <ImportStructure src="assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

* 中心放置一個**無瑕的賽特斯石英母岩**
* 東 / 西 / 南 / 北四個正方向同一高度各放置一個 <ItemLink id="ae2:fluix_block" />
* 四個對角各放置一個**過載水晶塊**
* 中心正上方放置一個**避雷針**

搭建完成後，等待**自然雷擊**命中避雷針即可完成轉化。雷擊命中後，周圍八個外圍方塊會被消耗，中心方塊轉化為無瑕的過載水晶母岩。

> 無瑕轉化只接受雷暴天氣下的自然雷電。由過載水晶召喚的人工閃電不會觸發。較低等級的母岩另有「簡化結構」可選，詳見 [過載水晶](materials/overload-crystal.md) 頁。

## 第四步：建立第一條閃電產線

獲得起步材料與基礎機器後，可先搭建最小可用的產線：**收集 → 儲存 → 消耗**。

1. **收集**：在空曠處放置一台**閃電收集器**並接入 ME 網路。雷電命中時，收集器會將閃電能量直接注入 ME 網路
2. **儲存**：將**閃電儲存組件 I** 放入 <ItemLink id="ae2:drive" />；閃電將與物品、流體一樣出現在 ME 終端中
3. **消耗**：**閃電模擬室**與**閃電裝配室**在加工時會從 ME 網路中自動提取閃電

此外常用的輔助設備：

* [特斯拉線圈](lightning/tesla-coil.md) — 消耗過載水晶粉與 FE 能量，穩定批量產出高壓 / 極高壓閃電
* [大氣電離儀](machines/atmospheric-ionizer.md) — 消耗 AE 能量與**天氣凝核**，強制改變世界天氣

## 第五步：中後期目標

起步產線成型後，可以依次解鎖以下內容：

* **閃電模擬室**（3 輸入槽，簡單材料轉化）與**閃電裝配室**（9 輸入槽，複雜裝配）
* **過載處理工廠**（9 輸入槽，支援物品與流體輸入輸出，可用閃電坍縮矩陣啟用多並行）
* **水晶催化器**（水 + 槽內材料，支援水晶 / 粉化兩種模式）
* **過載 ME 控制器** — 每個控制器為網路提供 128 頻道
* **過載線纜** — 單根線纜不設頻道上限
* **過載樣板供應器**與**過載 ME 介面** — 36 槽位，支援無線發貨與無線 I/O

具體細節請參閱左側導覽中的各分類頁面。
