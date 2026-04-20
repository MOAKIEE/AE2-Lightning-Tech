---
navigation:
  title: 水晶催化器
  icon: ae2lt:crystal_catalyzer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:crystal_catalyzer
---

# 水晶催化器

<Row>
  <BlockImage id="ae2lt:crystal_catalyzer" scale="4" />
</Row>

**水晶催化器**是一种专门用于流体与催化剂驱动的加工机器。它通过将特定流体与催化剂组合来生产特殊水晶或材料。

## 基本结构

水晶催化器具有以下槽位：

* **催化剂槽**（1个）：放入催化剂材料
* **矩阵槽**（1个）：可选安装闪电坍缩矩阵，安装后产出翻 4 倍
* **输出槽**（1个）：加工完成的产物
* **流体槽**（1个，16,000 mB 容量）：输入所需的流体
* **FE 能量缓冲**（1,000,000 FE 容量）：内置能量缓存

## 工作流程

1. 通过管道向流体槽注入所需的流体
2. 将催化剂材料放入催化剂槽
3. 提供 FE 能量
4. 机器自动匹配配方并开始加工
5. 完成后产物出现在输出槽中

## 闪电坍缩矩阵加成

<ItemImage id="ae2lt:lightning_collapse_matrix" scale="2" float="left" />

当矩阵槽中安装了**闪电坍缩矩阵**时，水晶催化器的产出数量将翻 **4 倍**。矩阵不会在加工中消耗。

## 自动弹出

水晶催化器支持自动弹出功能，可以在 GUI 中配置允许的输出方向。

## 能量供应

水晶催化器通过外部 FE 能量输入供电。它同时也是 ME 网络设备，需要连接到 ME 网络。
