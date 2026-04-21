---
navigation:
  title: 过载 TNT
  icon: ae2lt:overload_tnt
  parent: lightning/lightning-index.md
  position: 90
item_ids:
  - ae2lt:overload_tnt
---

# 过载 TNT

<Row>
  <BlockImage id="ae2lt:overload_tnt" scale="4" />
</Row>

**过载 TNT** 是一枚被过载奇点压进壳体的烟火。它不属于生产线上的常规消耗品——点火后更像是一次**可控的天灾**：先是一团剧烈的爆炸，紧接着一小段地表会被从天而降的闪电反复淋洗一遍。

多数时候你用它来开采特定地形、清理一片硬石，或在远方客人面前演示"我们做研究的人为什么要有护栏"。

## 合成

工作台 3×3 配方：四角 **火药**、四条边 **过载水晶粉**、中心一枚 **过载奇点**。

<ItemGrid>
  <ItemIcon id="minecraft:gunpowder" />
  <ItemIcon id="ae2lt:overload_crystal_dust" />
  <ItemIcon id="ae2lt:overload_singularity" />
</ItemGrid>

| 槽位 | 材料 | 数量 |
|------|------|------|
| 四角 | <ItemLink id="minecraft:gunpowder" /> | 4 |
| 四边 | <ItemLink id="ae2lt:overload_crystal_dust" /> | 4 |
| 中心 | <ItemLink id="ae2lt:overload_singularity" /> | 1 |

中心那枚 <ItemLink id="ae2lt:overload_singularity" /> 才是真正在做功的部件，四角的火药只是给它一个发作的借口。

## 引爆行为

点燃 / 被激活后，过载 TNT 的引信约 **80 tick (4 秒)** 后到达。届时它会按如下顺序释放能量：

1. 在原地触发一次强度 `4.0` 的爆炸，与原版 TNT 相当但不会引燃方块。
2. 若在配置中启用 `ae2lt-common.toml` 的 `overloadTnt.enableTerrainDamage`（默认开启），爆炸之后还会追加一段**闪电地形改造**——一连串人工闪电从天而降、反复淋洗爆心附近的地形，把残骸烧成焦痕。
3. 这段洗地闪电属于**人工闪电**，因此完全可以被 [闪电收集器](lightning-collector.md) 捕获并兑换为**高压闪电**存入 ME 网络。
4. 若关闭 `enableTerrainDamage`，爆炸照常发生，但不会追加闪电洗地。

> 过载 TNT 一旦起爆就无法再被放回原处；请像对待雷暴一样对待它。

## 使用建议

* 放置在**坚固方块**之上，给自己留 10 格以上的安全距离，并优先在开阔地面使用。
* 若只是想要闪电而不想要大坑，可以在 `ae2lt-common.toml` 中将 `overloadTnt.enableTerrainDamage` 关掉；此时爆炸依旧发生，但不会追加那段闪电洗地。
* 在低空开阔地使用，可以让 <ItemLink id="ae2lt:lightning_collector" /> 一次性吃到多道人工闪电，速度远快于依赖常规 [特斯拉线圈](tesla-coil.md) 点射。

## 现场日志：一次异常落点

> **第 7 号实验。** 预期与前六次一致；实际曲线偏离。起爆时落点附近散着一组 [闪电坍缩矩阵](../materials/lightning-collapse-matrix.md) —— 没有焦坑，只有几道闪电，和一枚不在清单上的元件。

> 复现失败。控制变量的结论指向同一件事：**矩阵得躺在外面**。
