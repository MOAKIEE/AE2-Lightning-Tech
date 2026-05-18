---
navigation:
  title: ME 과부하 케이블
  icon: ae2lt:overloaded_cable
  parent: overloaded-network/overloaded-network-index.md
item_ids:
  - ae2lt:overloaded_cable
  - ae2lt:overloaded_cable_white
  - ae2lt:overloaded_cable_orange
  - ae2lt:overloaded_cable_magenta
  - ae2lt:overloaded_cable_light_blue
  - ae2lt:overloaded_cable_yellow
  - ae2lt:overloaded_cable_lime
  - ae2lt:overloaded_cable_pink
  - ae2lt:overloaded_cable_gray
  - ae2lt:overloaded_cable_light_gray
  - ae2lt:overloaded_cable_cyan
  - ae2lt:overloaded_cable_purple
  - ae2lt:overloaded_cable_blue
  - ae2lt:overloaded_cable_brown
  - ae2lt:overloaded_cable_green
  - ae2lt:overloaded_cable_red
  - ae2lt:overloaded_cable_black
---

# ME 과부하 케이블

**ME 과부하 케이블**은 바닐라 AE2 밀집 케이블의 강화 버전이며, 자체적으로 채널 상한이 없습니다.

## 핵심 특성

* **채널 상한 없음**: 단일 ME 과부하 케이블 자체에는 채널 병목이 없어 임의 수량의 채널을 수용 가능
* **제어기가 네트워크 총 채널 결정**: 전체 과부하 네트워크의 총 채널량은 네트워크 내 **과부하 ME 제어기**의 수량에 따라 결정 — 각 과부하 제어기는 네트워크에 128 채널 제공(기본값, 설정 가능)
* **스마트 채널 할당**: 과부하 네트워크는 바닐라 AE2의 BFS 채널 할당 대신 **최대 유량 알고리즘**을 사용하여, 복잡한 토폴로지에서 총 채널 용량을 더 효율적으로 활용
* **밀집 케이블 외관**: 시각적으로 바닐라 밀집 케이블과 동일
* **염색 지원**: 17가지 색상 변형이 있으며, 색상이 다르면 서로 연결되지 않음(바닐라 AE2 케이블 염색 메커니즘과 일치)

## 채널 메커니즘

바닐라 AE2 케이블의 고정 채널 상한(일반 케이블 8 채널, 밀집 케이블 32 채널)과 달리, 과부하 케이블은 다음 규칙을 사용합니다:

1. **케이블 자체에 채널 병목 없음**: 모든 ME 과부하 케이블은 임의 수량의 채널을 수용 가능
2. **네트워크 총 채널 = 과부하 제어기 수 × 128**: 전체 네트워크에서 사용 가능한 총 채널량은 과부하 ME 제어기 수량에 의해 결정
3. **최대 유량 할당**: 사용 가능한 채널은 네트워크 내에서 최대 유량 알고리즘으로 스마트하게 할당되며, 경로를 따라 단계별로 차감되지 않음

플레이어에게는 다음을 의미합니다:

* 어떤 주요 케이블의 채널이 소진될까 걱정할 필요 없음
* 네트워크 총 채널이 충분하기만 하면, 모든 설비가 채널을 받을 수 있음
* 과부하 ME 제어기를 추가하면 네트워크의 총 채널량을 확장할 수 있음

> **중요**: ME 과부하 케이블은 **양 끝이 모두 과부하 설비**(과부하 케이블, 과부하 ME 제어기 등)일 때만 완전한 무제한 용량 특성을 발휘할 수 있습니다. ME 과부하 케이블이 바닐라 AE2 설비와 직접 연결된 경우, 해당 연결은 여전히 바닐라 규칙에 따라 채널 상한에 계산됩니다.

## 색상 변형

ME 과부하 케이블은 액정(기본) 및 16가지 Minecraft 염료 색상을 포함한 17가지 색상 변형이 있습니다:

흰색, 주황, 자홍색, 하늘색, 노랑색, 연두색, 분홍색, 회색, 밝은 회색, 청록색, 보라색, 파랑색, 갈색, 초록색, 빨강색, 검정색.

색상이 다른 ME 과부하 케이블은 서로 연결되지 않습니다(바닐라 AE2 케이블 염색 메커니즘과 일치).

## 사용 방법

ME 과부하 케이블의 사용 방법은 바닐라 밀집 케이블과 동일합니다. 블록 측면에 직접 배치하기만 하면 네트워크에 연결됩니다.

## 적용 시나리오

* 대형 기지의 주 라인, 단일 ME 과부하 케이블 한 줄이 여러 밀집 케이블의 채널 부하를 담당 가능
* 과부하 ME 제어기를 중심으로 하는 대용량 네트워크
* 네트워크 토폴로지 간소화, 더 이상 채널 병목에 대한 배선 설계 불필요
