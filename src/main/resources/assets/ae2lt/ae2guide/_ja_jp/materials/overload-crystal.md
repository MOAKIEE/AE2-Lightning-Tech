---
navigation:
  title: オーバーロードクリスタル
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

# オーバーロードクリスタル

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**オーバーロードクリスタル**は AE2 Lightning Tech で最も基本的かつ最重要な素材であり、ほぼ全ての中後期レシピに必要となるか、その派生物が必要となります。

## 入手方法

### オーバーロードクリスタルマザーロックの育成

オーバーロードクリスタルの主な供給源は**オーバーロードクリスタルマザーロック**の表面に自然に成長する水晶クラスターです。

オーバーロードクリスタルマザーロックはマルチブロック構造を構築し**落雷**により対応する等級の AE2 芽生えたケルタスクォーツを変換することで入手します。詳細なレシピは下記の「オーバーロードクリスタルマザーロックの入手」セクションを参照してください。

### マザーロックの等級

オーバーロードクリスタルマザーロックは 4 等級あります：

| 等級 | 名称 | 劣化 |
|------|------|------|
| 完璧 | 完璧なオーバーロードクリスタルマザーロック | 永遠に劣化しない |
| 傷あり | 傷ありのオーバーロードクリスタルマザーロック | 低確率で劣化 |
| ひび割れ | ひびの入ったオーバーロードクリスタルマザーロック | 中確率で劣化 |
| 損傷 | 損傷したオーバーロードクリスタルマザーロック | 高確率で劣化 |

オーバーロードクリスタルの芽がマザーロック上で 1 段階成長するたびに、マザーロックには一定の確率で 1 等級劣化することがあります。損傷したマザーロックがさらに劣化すると、通常のオーバーロードクリスタルブロックになります。

> **シルクタッチ**により、完璧でないマザーロックが破壊時に劣化することを防げます。**完璧なオーバーロードクリスタルマザーロック**は永遠に劣化しません。

### 水晶の芽の成長段階

オーバーロードクリスタルの芽の成長は 4 段階に分かれます：

1. **小型オーバーロードクリスタルの芽** → 破壊するとオーバーロードクリスタルダストをドロップ
2. **中型オーバーロードクリスタルの芽** → 破壊するとオーバーロードクリスタルダストをドロップ
3. **大型オーバーロードクリスタルの芽** → 破壊するとオーバーロードクリスタルダストをドロップ
4. **オーバーロードクリスタルクラスター**（完全成長）→ 破壊すると**オーバーロードクリスタル**をドロップ（幸運有効）

### 成長加速

<ItemLink id="ae2:growth_accelerator" />（水晶成長加速器）はオーバーロードクリスタルの芽にも有効です。マザーロックの周囲に加速器を配置することで水晶の成長速度が大幅に向上します。

## オーバーロードクリスタルマザーロックの入手

オーバーロードクリスタルマザーロックは 3×3 のマルチブロック構造を構築し、中央の真上の避雷針への落雷で変換をトリガーします。**精密**と**簡易**の 2 種類の構造があります。

### 精密構造（自然落雷、同等級変換）

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

構造要件：

* 中央に対応する等級の AE2 芽生えたケルタスクォーツを配置
* 東 / 西 / 南 / 北の 4 方向に同じ高さで各 1 つの <ItemLink id="ae2:fluix_block" /> を配置
* 四隅に各 1 つの <ItemLink id="ae2lt:overload_crystal_block" /> を配置
* 中央の真上に避雷針を配置

構築完了後、**自然落雷**が避雷針に命中するのを待つことで**同等級**変換が実行されます：

| 入力（中央） | 出力 |
|-----------|------|
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawless_budding_overload_crystal" /> |

> 精密構造は**自然落雷**のみを受け付けます。プレイヤーがオーバーロードクリスタルで誘導した人工の雷ではこの構造はトリガーされません。

### 簡易構造（任意の雷、産物 1 等級ダウン）

手元にオーバーロードクリスタルブロックがない場合、簡易構造で完璧以外の 3 等級のマザーロックを生産できます：

* 中央に対応する等級の AE2 芽生えたケルタスクォーツを配置
* 四隅に各 1 つの <ItemLink id="ae2:quartz_block" />（ケルタスクォーツブロック）を配置
* 東 / 西 / 南 / 北の 4 方向に同じ高さで各 1 つの <ItemLink id="ae2:fluix_block" /> を配置
* 中央の真上に避雷針を配置

**任意の雷**が避雷針に命中することでトリガー可能、産物は入力等級より 1 等級低くなります：

| 入力（中央） | 出力 |
|-----------|------|
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |

> 完璧なマザーロックは簡易構造では入手できません——完璧を狙うには精密構造 + 自然落雷の経路しかありません。

雷命中後、周囲 8 つの外周素材ブロックが消費され、中央のブロックが対応する等級のオーバーロードクリスタルマザーロックに変換されます。

## 派生産物

| アイテム | 用途 |
|------|------|
| オーバーロードクリスタルダスト | テスラコイル高電圧モードの消耗品、一部レシピにも使用 |
| オーバーロードクリスタルブロック | 建築 / 装飾ブロック、完璧なマザーロック構造の構築にも使用 |
