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

**过载 TNT** 是一枚被过载奇点压进壳体的烟火。它不属于生产线上的常规消耗品——点火后更像是一次**可控的天灾**：从天而降的闪电把爆心周围反复淋洗一遍，而不是一声响亮的爆炸。

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

点燃 / 被激活后，过载 TNT 的引信约 **4 秒** 后到达。届时它会按如下顺序释放能量：

1. 爆心正上方先落下一道**"起手雷"**，随后多道闪电从天而降，以爆心为中心**向外层层烧灼地表**——这才是过载 TNT 真正做功的阶段，扩散范围相当大。
2. 起爆时，爆心周围较大范围内的**所有生物都会受到剧烈的雷击伤害**，越靠近中心越致命，几乎没有生还空间。
3. 第一波闪电打完后，还会持续数秒的**余震**，不定时补打随机雷点。
4. 爆心附近会被强行切入一段**短暂的雷暴天气**，大约 8 秒后恢复。
5. 整段过程产生的所有闪电都属于**人工闪电**，可以被 [闪电收集器](lightning-collector.md) 捕获并兑换成**高压闪电**存入 ME 网络。

过载 TNT 的清场很"讲规矩"：**基岩、屏障、末地传送门框架、命令方块**这些特殊方块不会被打碎；对于挂了区域保护的地方，它也会尊重保护规则、不会硬拆。

> 过载 TNT 一旦起爆就无法再被放回原处；请像对待雷暴一样对待它。

## 配置与关闭

在配置 `ae2lt-common.toml` 的 `overloadTnt.enableTerrainDamage`（默认**开启**）控制整段引爆行为：

* **开启**：按上面的"引爆行为"执行——闪电轰击、地表烧灼、雷暴、生物伤害一并到位。
* **关闭**：过载 TNT 起爆时**什么也不会发生**——没有闪电、没有破坏、没有伤害，等同于哑弹。

## 使用建议

* 放置在**坚固方块**之上，给自己留足够远的安全距离，并优先在开阔地面使用；起爆半径相当大，站在同一张地图上都有可能被卷进去。
* 在低空开阔地使用，可以让 <ItemLink id="ae2lt:lightning_collector" /> 一次性吃到大量人工闪电，速度远快于依赖常规 [特斯拉线圈](tesla-coil.md) 点射。
* 若只是想用它打散地形而不想让周边生物一起报销，记得先把该撤的生物都撤走。
