# AE2 Lightning Tech 1.20.1 Forge 移植进度

更新日期：2026-05-19

当前分支：`port/1.20.1-forge`

## 本次完成

### Forge 1.20.1 资源格式兼容

已对照本地 1.20.1 参考源码：

- `Source Code/1.20.1/ae2-1.20.1-forge`
- `Source Code/1.20.1/AdvancedAE-1.20.1-forge`
- `Source Code/1.20.1/AE2OmniCells-1.20.1`
- `Source Code/1.20.1/Flux-Networks-1.20`
- `Source Code/1.20.1/Powah-1.20.1`

完成以下 NeoForge 资源写法到 Forge 1.20.1 写法的迁移：

| 范围 | 原写法 | 1.20.1 Forge 写法 |
| --- | --- | --- |
| 条件配方键 | `neoforge:conditions` | `conditions` |
| Mod 加载条件 | `neoforge:mod_loaded` | `forge:mod_loaded` |
| 条件取反 | `neoforge:not` | `forge:not` |
| 标签为空判断 | `neoforge:tag_empty` | `forge:tag_empty` |
| Ingredient 差集 | `neoforge:difference` | `forge:difference` |
| 组合模型 loader | `neoforge:composite` | `forge:composite` |
| 模型发光面数据 | `neoforge_data` | `forge_data` |

涉及资源包括：

- 可选 Mod 配方：AppFlux、AdvancedAE、ExtendedAE、MEGA/Omni 相关 `overload_processing` 配方
- Mekanism 兼容配方
- Crystal Catalyzer dust 配方
- Overloaded Power Supply 条件配方
- 过载线缆去色配方
- 发光/组合方块模型

### 1.20.1 数据包目录约定

已对照本地 AE2 / AdvancedAE 1.20.1 参考源码，将资源目录改为 1.20.1 可读取的复数路径：

| 原目录 | 1.20.1 目录 |
| --- | --- |
| `data/*/tags/item` | `data/*/tags/items` |
| `data/*/tags/block` | `data/*/tags/blocks` |
| `data/*/loot_table` | `data/*/loot_tables` |

涉及范围：

- Minecraft 挖掘/工具等级方块标签
- AE2 `growth_acceleratable` 和 `inscriber_presses` 标签
- AE2LT 过载线缆标签
- `c` 命名空间的方块/物品通用标签
- AE2LT 全部方块掉落表

## 已验证

- `rg -n 'neoforge:|neoforge_data|\"loader\"\\s*:\\s*\"neoforge' src\\main\\resources -g "*.json"`
  - 结果：无残留匹配
- `rg --files src\\main\\resources\\data | rg '(^|/|\\\\)tags(\\\\|/)(item|block)(\\\\|/)|(^|/|\\\\)loot_table(\\\\|/)'`
  - 结果：无旧数据包目录残留
- PowerShell `ConvertFrom-Json -AsHashtable` 解析 `src/main/resources` 下全部 JSON
  - 结果：全部 JSON 可解析
- `./gradlew.bat compileJava`
  - 结果：通过
- `./gradlew.bat build`
  - 结果：通过，包含 `processResources`、`jar`、`jarJar`、`reobfJar`、`reobfJarJar`

## 当前判断

基础 Java 编译和打包阶段已经可通过，但这不等于全部功能完成。后续仍需要按 `port_1.20.1_module_breakdown.md` 继续逐块做运行期验证，尤其是：

- Forge `LazyOptional` Capability 生命周期是否需要缓存和失效处理
- AE2 1.20.1 内部 Mixin 目标方法是否在真实游戏加载阶段全部命中
- 自定义配方在数据包加载后是否被 Forge 条件正确过滤
- GUI、JEI、Jade、AE2 Guide 资源是否能在客户端实际打开
- 可选 Mod 缺失时 AppFlux / AdvancedAE / ExtendedAE / Mekanism 相关内容是否不会硬崩

## 下一步建议

1. 启动客户端或 `runClient`，检查模型 loader、mixin、菜单和配方加载日志。
2. 优先验证基础框架：Capability、网络 SimpleChannel、NBT 替代 DataComponent。
3. 再按计划进入材料与物品、水晶系统、闪电能量和收集器的功能级测试。
