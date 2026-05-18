---
navigation:
  title: 과부하 수정
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

# 과부하 수정

<ItemImage id="ae2lt:overload_crystal" scale="2" float="left" />

**과부하 수정**은 AE2 Lightning Tech에서 가장 기본적이면서도 가장 중요한 재료이며, 거의 모든 중후반기 레시피에서 이것 또는 그 파생물이 필요합니다.

## 획득 방법

### 과부하 수정 모암 키우기

과부하 수정의 주요 출처는 **과부하 수정 모암** 표면에 자연스럽게 자라는 수정 클러스터입니다.

과부하 수정 모암은 다중 블록 구조를 만들고 **낙뢰**를 사용하여 해당 등급의 AE2 싹 틔우는 서투스 석영을 변환하여 얻습니다. 자세한 레시피는 아래 "과부하 수정 모암 획득" 절을 참조하세요.

### 모암 등급

과부하 수정 모암에는 네 가지 등급이 있습니다:

| 등급 | 이름 | 쇠퇴 |
|------|------|------|
| 완벽한 | 완벽한 과부하 수정 모암 | 쇠퇴하지 않음 |
| 흠집 있는 | 흠집 있는 과부하 수정 모암 | 낮은 확률로 쇠퇴 |
| 금이 간 | 금이 간 과부하 수정 모암 | 중간 확률로 쇠퇴 |
| 손상된 | 손상된 과부하 수정 모암 | 높은 확률로 쇠퇴 |

과부하 수정 새싹이 모암에서 한 단계 자랄 때마다, 모암은 일정 확률로 한 등급 쇠퇴할 수 있습니다. 손상된 모암이 계속 쇠퇴하면, 일반 과부하 수정 블록으로 변합니다.

> **섬세한 손길**은 완벽하지 않은 모암이 파괴될 때의 쇠퇴를 방지할 수 있습니다. **완벽한 과부하 수정 모암**은 쇠퇴하지 않습니다.

### 수정 새싹의 성장 단계

과부하 수정 새싹의 성장은 네 단계로 나뉩니다:

1. **작은 과부하 수정 새싹** → 파괴 시 과부하 수정 가루 드롭
2. **중간 과부하 수정 새싹** → 파괴 시 과부하 수정 가루 드롭
3. **큰 과부하 수정 새싹** → 파괴 시 과부하 수정 가루 드롭
4. **과부하 수정 클러스터**(완전히 자람) → 파괴 시 **과부하 수정** 드롭(행운 적용)

### 성장 가속

<ItemLink id="ae2:growth_accelerator" />(수정 성장 가속기)는 과부하 수정 새싹에도 동일하게 효과가 있습니다. 모암 주변에 가속기를 배치하면 수정의 성장 속도가 크게 향상됩니다.

## 과부하 수정 모암 획득

과부하 수정 모암은 3×3 다중 블록 구조를 만들고 중앙 바로 위의 피뢰침에서 낙뢰로 변환을 유발하여 얻습니다. **정제**와 **간소화** 두 가지 구조가 있습니다.

### 정제 구조(자연 낙뢰, 동급 변환)

<GameScene zoom="4" background="transparent">
  <ImportStructure src="../assets/assemblies/flawless_budding_overload.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

구조 요구사항:

* 중앙에 해당 등급의 AE2 싹 틔우는 서투스 석영 배치
* 동·서·남·북 네 정방향 같은 높이에 각각 <ItemLink id="ae2:fluix_block" /> 한 개씩 배치
* 네 대각선 모서리에 각각 <ItemLink id="ae2lt:overload_crystal_block" /> 한 개씩 배치
* 중앙 바로 위에 피뢰침 한 개 배치

구축 완료 후, **자연 낙뢰**가 피뢰침을 명중하기를 기다리면 **동급** 변환이 가능합니다:

| 입력(중앙) | 출력 |
|-----------|------|
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawless_budding_overload_crystal" /> |

> 정제 구조는 **자연 낙뢰**만 인정합니다. 플레이어가 과부하 수정을 휴대하여 끌어들인 인공 번개는 이 구조를 유발하지 않습니다.

### 간소화 구조(임의 번개, 산물 한 등급 하향)

과부하 수정 블록이 부족한 경우, 간소화 구조로 완벽한 등급 외의 3개 등급 모암을 생산할 수 있습니다:

* 중앙에 해당 등급의 AE2 싹 틔우는 서투스 석영 배치
* 네 대각선 모서리에 각각 <ItemLink id="ae2:quartz_block" />(서투스 석영 블록) 한 개씩 배치
* 동·서·남·북 네 정방향 같은 높이에 각각 <ItemLink id="ae2:fluix_block" /> 한 개씩 배치
* 중앙 바로 위에 피뢰침 한 개 배치

**임의의 번개**가 피뢰침을 명중하면 유발 가능하며, 산물은 입력 등급보다 한 등급 낮습니다:

| 입력(중앙) | 출력 |
|-----------|------|
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="ae2lt:damaged_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="ae2lt:cracked_budding_overload_crystal" /> |
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="ae2lt:flawed_budding_overload_crystal" /> |

> 완벽한 모암은 간소화 구조로 획득할 수 없습니다 — 완벽한 모암을 원한다면 정제 구조 + 자연 낙뢰로만 가능합니다.

낙뢰 명중 후, 주변 8개 외곽 재료 블록은 소비되고, 중앙 블록은 해당 등급의 과부하 수정 모암으로 변환됩니다.

## 파생 산물

| 아이템 | 용도 |
|------|------|
| 과부하 수정 가루 | 테슬라 코일 고전압 모드의 소모품이자 일부 레시피에도 사용 |
| 과부하 수정 블록 | 건축/장식 블록이자 완벽한 모암 구조 구축에도 사용 |
