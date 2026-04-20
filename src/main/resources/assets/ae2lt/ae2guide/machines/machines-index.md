---
navigation:
  title: 加工机器
  icon: ae2lt:lightning_simulation_room
  parent: index.md
  position: 30
---

# 加工机器

AE2 闪电科技引入了多种加工机器，它们利用闪电和 FE 能量执行各种加工任务。这些机器都是 ME 网络设备，需要接入 ME 网络才能工作。

## 机器一览

| 机器 | 核心功能 | 消耗 |
|------|---------|------|
| [闪电模拟室](lightning-simulation-chamber.md) | 单输入材料转化 | 闪电 + FE |
| [闪电装配室](lightning-assembly-chamber.md) | 多输入高级装配 | 闪电 + FE |
| [过载处理工厂](overload-processing-factory.md) | 流体+物品的多并行加工 | FE |
| [水晶催化器](crystal-catalyzer.md) | 流体+催化剂的水晶培育 | FE |
| [大气电离仪](atmospheric-ionizer.md) | 天气控制 | AE 能量 |

## 通用特性

所有加工机器都具有以下通用特性：

### 自动弹出

大多数机器支持**自动弹出**功能。开启后，机器完成加工时会自动将产物推送到指定方向的相邻容器中。你可以在 GUI 的输出面配置中选择允许自动弹出的方向。

### 速度卡

部分机器支持安装 AE2 原版的**速度卡**来提升加工速度。

### 闪电代偿

部分需要极高压闪电的配方支持**矩阵代偿**机制：当安装了闪电坍缩矩阵时，可以用更多的高压闪电替代极高压闪电来完成配方。
