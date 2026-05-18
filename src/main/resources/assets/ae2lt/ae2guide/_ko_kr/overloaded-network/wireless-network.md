---
navigation:
  title: 무선 네트워크
  icon: ae2lt:wireless_overloaded_controller
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:wireless_receiver
  - ae2lt:wireless_overloaded_controller
  - ae2lt:advanced_wireless_overloaded_controller
---

# 무선 네트워크

<ItemGrid>
  <ItemIcon id="ae2lt:wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:advanced_wireless_overloaded_controller" />
  <ItemIcon id="ae2lt:wireless_receiver" />
</ItemGrid>

무선 네트워크 시스템은 케이블을 깔 필요 없이 장거리 또는 차원을 가로질러 과부하 ME 네트워크를 확장할 수 있게 합니다. **무선 과부하 제어기**가 지정된 주파수로 송출하며, 하나 이상의 **무선 수신기**가 동일한 주파수로 조정되면 가상 그리드 연결이 설정됩니다.

## 구성 요소

### 무선 과부하 제어기

<Row>
  <BlockImage id="ae2lt:wireless_overloaded_controller" scale="4" />
</Row>

**무선 과부하 제어기**는 무선 송신 기능을 겸한 [과부하 ME 제어기](overloaded-controller.md)입니다. 선택된 주파수에서 자신의 그리드 노드를 송출하여, 원격의 무선 수신기가 연결할 수 있게 합니다.

* 일반 과부하 제어기와 동일한 추가 채널과 에너지 제공
* 각 제어기는 동시에 **하나의 주파수**만 송출 가능
* 각 주파수는 동시에 하나의 송신기만 점유 가능

### 고급 무선 과부하 제어기

<Row>
  <BlockImage id="ae2lt:advanced_wireless_overloaded_controller" scale="4" />
</Row>

**고급 무선 과부하 제어기**는 업그레이드 버전이며, 두 가지 핵심 개선 사항을 갖추고 있습니다:

* **차원 간 지원**: 다른 차원의 무선 수신기도 연결 가능
* **무한 채널 용량**: 각 수신기당 채널 상한을 완전히 제거

### 무선 수신기

<Row>
  <BlockImage id="ae2lt:wireless_receiver" scale="4" />
</Row>

**무선 수신기**는 무선 과부하 제어기와 짝을 이루는 수신단입니다. 월드 어느 위치에든 배치하고, 제어기와 동일한 주파수로 설정하면 가상 그리드 연결이 자동으로 설정됩니다.

* 유휴 전력 소비 5 AE/t
* 동시에 하나의 주파수에만 연결 가능
* 차원 간 연결은 송신단이 **고급** 무선 과부하 제어기여야 함

## 구축 단계

1. **무선 과부하 제어기**를 한 대 배치하고, 우클릭으로 주파수 인터페이스 열기
2. 주파수를 선택하거나 새로 만들기
3. 원격 위치에 **무선 수신기**를 한 대 배치하고, 우클릭으로 주파수 인터페이스 열기
4. 동일한 주파수 선택 — 수신기가 제어기의 그리드와 자동으로 가상 연결 설정

연결 설정 후, 수신기의 동작은 케이블로 제어기에 직접 연결된 것과 동일합니다. 수신기 로컬 네트워크에 연결된 설비는 제어기의 ME 네트워크에 접근할 수 있으며, 채널, 저장 및 합성을 포함합니다.

## 주파수 보안

주파수는 액세스 제어를 지원합니다:

| 단계 | 동작 |
|------|------|
| 공개 | 누구나 이 주파수에 수신기를 바인딩 가능 |
| 비공개 | 주파수 소유자와 허용된 멤버만 바인딩 가능 |
| 암호화 | 비밀번호 입력이 있어야 바인딩 가능 |

바인딩되지 않은 제어기와 수신기는 항상 접근 가능하며, 보안 단계 제한을 받지 않습니다.

## 사용 권장사항

* 차원 간 네트워크 브리지가 필요할 때 고급 버전 사용
* 한 대의 제어기로 여러 수신기 서비스 가능 — 각 수신기는 각자 독립된 가상 연결 설정
