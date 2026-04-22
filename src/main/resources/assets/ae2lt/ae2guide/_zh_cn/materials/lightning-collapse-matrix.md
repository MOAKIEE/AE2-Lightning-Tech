---
navigation:
  title: 闪电坍缩矩阵
  icon: ae2lt:lightning_collapse_matrix
  parent: materials/materials-index.md
  position: 60
item_ids:
  - ae2lt:lightning_collapse_matrix
---

# 闪电坍缩矩阵

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

**闪电坍缩矩阵**是 AE2 闪电科技中最核心的一个终末级配件。它在机器中不作为消耗品，而是作为**代偿 / 并行的催化元件**——在多种机器的矩阵槽中保留一枚矩阵，就能解锁该机器的高阶运行模式。

## 获取方式

### 在闪电模拟室里合成

| 材料 | 数量 |
|------|------|
| 完美电鸣水晶 | 1 |
| 极限过载核心 | 16 |
| **极高压闪电** | 256 |
| 能量 | 500,000,000 AE |

把以上材料投进已经安装了**闪电坍缩矩阵**的 [闪电模拟室](../machines/lightning-simulation-chamber.md) 里即可生产;由于成本本身非常重,通常会与过载处理工厂、闪电装配室一起统筹排产。

## 作为机器催化剂

闪电坍缩矩阵**不会**在加工中被消耗,但必须始终留在矩阵槽中:

* [闪电模拟室](../machines/lightning-simulation-chamber.md) / [闪电装配室](../machines/lightning-assembly-chamber.md) — 安装矩阵后,部分原本要求**极高压闪电**的配方可以用数倍量的**高压闪电**进行代偿。
* [特斯拉线圈](../lightning/tesla-coil.md) — 极高压模式必须挂一枚矩阵在槽中,否则无法从高压闪电升压到极高压。
* [水晶催化器](../machines/crystal-catalyzer.md) — 矩阵在位时单次产出提升至 **4 倍**。
* [过载处理工厂](../machines/overload-processing-factory.md) — 多矩阵并行:每多一枚矩阵,解锁一档并行上限(槽位最多 64 枚,默认每枚 4 并行)。

## 观测备忘

> 外场小组提交过一份很薄的复核报告：矩阵在**不被任何容器约束**时会出现一种非预期反应；反应被点燃的场景记在 [过载 TNT](../lightning/overload-tnt.md) 那一页。

产物是一枚未登记元件，接进 <ItemLink id="ae2:drive" /> 即可解析；内部残余**只读一次**，读完空置。至于会读出什么——报告只附了一句：“样本分布极不均匀，最稀有的那份不是元件，是一本还没写字的册子。”
