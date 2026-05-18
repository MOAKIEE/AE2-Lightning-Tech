---
navigation:
  title: 大気イオナイザー
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# 大気イオナイザー

<Row>
  <BlockImage id="ae2lt:atmospheric_ionizer" scale="4" />
</Row>

**大気イオナイザー**は天気制御設備です。**天気コンデンセート**と AE エネルギーを消費することで、ワールドの天気を指定状態に強制的に切替え、落雷コレクターと組み合わせて安定した自然落雷を得ることができます。

## 天気コンデンセート

天気コンデンセートには 3 種類あり、それぞれ 3 種類の天気に対応します：

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| コンデンセート種類 | 目標天気 | AE 消費 | 持続時間 |
|---------|---------|---------|---------|
| 晴天コンデンセート | 晴れ | 500,000 AE | 12,000 ~ 180,000 tick |
| 降雨コンデンセート | 雨 | 1,000,000 AE | 12,000 ~ 24,000 tick |
| 雷雨コンデンセート | 雷雨 | 8,000,000 AE | 3,600 ~ 15,600 tick |

## 稼働フロー

1. 大気イオナイザーを ME ネットワークに接続
2. 必要な天気コンデンセートを入力スロットに入れる
3. マシンが ME ネットワークから AE エネルギーを継続的に抽出してイオン化を実行
4. イオン化完了後、ワールドの天気が目標天気に強制切替えされます
5. コンデンセートは使用後に消費されます

## 注意事項

* 大気イオナイザーは**AE エネルギー**（ME ネットワークから）を消費し、FE エネルギーは消費しません
* 雷雨コンデンセートは単回消費量が最大（8,000,000 AE）です、ネットワークのエネルギー供給が十分であることを確認してください
* 天気をサポートしないディメンションでは、マシンは動作できません
* 目標天気が現在の天気と同じ場合、マシンはコンデンセートを消費しません
