---
navigation:
  title: 閃電坍縮矩陣
  icon: ae2lt:lightning_collapse_matrix
  parent: materials/materials-index.md
  position: 60
item_ids:
  - ae2lt:lightning_collapse_matrix
---

# 閃電坍縮矩陣

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

**閃電坍縮矩陣**是 AE2 閃電科技中最核心的終末級組件之一。它在機器中不作為消耗品，而是作為**代償與並行的催化元件**——在多種機器的矩陣槽中保留一枚矩陣，即可解鎖該機器的高階運行模式。

## 取得方式

### 在閃電模擬室中合成

| 材料 | 數量 |
|------|------|
| 完美電鳴水晶 | 1 |
| 極限過載核心 | 16 |
| **極高壓閃電** | 128 |
| 能量 | 50,000,000 AE |

由於成本較高，建議與過載處理工廠、閃電裝配室的排產一併規劃。

## 作為機器催化劑

閃電坍縮矩陣**不會**在加工中被消耗，但必須始終留在矩陣槽中：

* [閃電模擬室](../machines/lightning-simulation-chamber.md) / [閃電裝配室](../machines/lightning-assembly-chamber.md) — 安裝矩陣後，部分原本要求**極高壓閃電**的配方可以用數倍量的**高壓閃電**代償
* [特斯拉線圈](../lightning/tesla-coil.md) — 極高壓模式必須在槽中安裝矩陣，否則無法從高壓閃電升壓到極高壓
* [水晶催化器](../machines/crystal-catalyzer.md) — 水晶模式與粉化模式均可生效；安裝矩陣後，並行後的單次產出再提升至 **4 倍**
* [過載處理工廠](../machines/overload-processing-factory.md) — 多矩陣並行：每多一枚矩陣解鎖一檔並行度（槽位最多 32 枚，預設每枚提供 8 並行）

## 觀測備忘

> 外場小組提交過一份很薄的複核報告：矩陣在**不被任何容器約束**時會出現一種非預期反應；反應被點燃的場景記在 [過載 TNT](../lightning/overload-tnt.md) 那一頁。

產物是一枚未登記元件，接進 <ItemLink id="ae2:drive" /> 即可解析；內部殘餘**只讀一次**，讀完空置。至於會讀出什麼——報告只附了一句：「樣本分布極不均勻，最稀有的那份不是元件，是一本還沒寫字的冊子。」
