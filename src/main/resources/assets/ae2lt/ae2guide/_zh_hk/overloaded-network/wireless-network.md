---
navigation:
  title: 無線網絡
  icon: ae2lt:wireless_overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:wireless_receiver
  - ae2lt:wireless_overloaded_controller
  - ae2lt:advanced_wireless_overloaded_controller
---

# 無線網絡

<ItemGrid>
  <ItemIcon id="ae2lt:wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:advanced_wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:wireless_receiver" />
</ItemGrid>

無線網絡系統允許你跨越長距離甚至跨維度擴展過載 ME 網絡，無需鋪設線纜。**無線過載控制器**在指定頻率上廣播，一個或多個**無線接收器**調諧到同一頻率即可建立虛擬網格連接。

## 組件

### 無線過載控制器

<Row>
  <BlockImage id="ae2lt:wireless_overloaded_controller" scale="4" />
</Row>

**無線過載控制器**是一台兼具無線發射功能的[過載 ME 控制器](overloaded-controller.md)。它在選定的頻率上廣播自身的網格節點，使遠端的無線接收器可以連接到它。

* 提供與普通過載控制器相同的額外頻道和能量
* 每台控制器同一時間只能廣播**一個頻率**
* 每個頻率同一時間只能被一台發射器佔用

### 高級無線過載控制器

<Row>
  <BlockImage id="ae2lt:advanced_wireless_overloaded_controller" scale="4" />
</Row>

**高級無線過載控制器**是升級版本，具備兩項關鍵改進：

* **跨維度支援**：其他維度中的無線接收器也可以連接到它
* **無限頻道容量**：完全移除每接收器的頻道上限

### 無線接收器

<Row>
  <BlockImage id="ae2lt:wireless_receiver" scale="4" />
</Row>

**無線接收器**是與無線過載控制器配對的接收端。將它放置在世界任意位置，設定為與控制器相同的頻率，即可自動建立虛擬網格連接。

* 空閒功耗 5 AE/t
* 同一時間只能連接一個頻率
* 跨維度連接需要發射端為**高級**無線過載控制器

## 搭建步驟

1. 放置一台**無線過載控制器**，右鍵開啟頻率介面
2. 選擇或建立一個頻率
3. 在遠端位置放置一台**無線接收器**，右鍵開啟其頻率介面
4. 選擇相同的頻率——接收器會自動與控制器的網格建立虛擬連接

連接建立後，接收器的行為等同於透過線纜直接連接到控制器。接入接收器本地網絡的設備可以存取控制器的 ME 網絡，包括頻道、儲存和合成。

## 頻率安全

頻率支援存取控制：

| 級別 | 行為 |
|------|------|
| 公開 | 任何人都可以將接收器綁定到此頻率 |
| 私有 | 僅頻率所有者和允許的成員可以綁定 |
| 加密 | 需要輸入密碼才能綁定 |

未綁定的控制器和接收器始終可以存取，不受安全級別限制。

## 使用建議

* 需要跨維度橋接網絡時使用高級版本
* 一台控制器可以服務多台接收器——每台接收器各自建立獨立的虛擬連接
