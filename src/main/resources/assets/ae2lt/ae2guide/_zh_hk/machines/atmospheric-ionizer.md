---
navigation:
  title: 大氣電離儀
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# 大氣電離儀

<Row>
  <BlockImage id="ae2lt:atmospheric_ionizer" scale="4" />
</Row>

**大氣電離儀**是一台天氣控制設備。它透過消耗**天氣凝核**與 AE 能量，強制將世界天氣切換為指定狀態，從而配合閃電收集器獲得穩定的自然雷擊。

## 天氣凝核

天氣凝核有三種類型，分別對應三種天氣：

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| 凝核類型 | 目標天氣 | AE 消耗 | 持續時間 |
|---------|---------|---------|---------|
| 晴空凝核 | 晴天 | 500,000 AE | 12,000 ~ 180,000 tick |
| 降雨凝核 | 雨天 | 1,000,000 AE | 12,000 ~ 24,000 tick |
| 雷暴凝核 | 雷暴 | 8,000,000 AE | 3,600 ~ 15,600 tick |

## 工作流程

1. 將大氣電離儀接入 ME 網絡
2. 將所需的天氣凝核放入輸入槽
3. 機器從 ME 網絡中持續提取 AE 能量進行電離
4. 電離完成後，世界天氣被強制切換為目標天氣
5. 凝核在使用後被消耗

## 注意事項

* 大氣電離儀消耗**AE 能量**（來自 ME 網絡），而非 FE 能量
* 雷暴凝核單次消耗最高（8,000,000 AE），請確保網絡能量供應充足
* 在不支援天氣的維度中，機器無法工作
* 當目標天氣已經是當前天氣時，機器不會消耗凝核
