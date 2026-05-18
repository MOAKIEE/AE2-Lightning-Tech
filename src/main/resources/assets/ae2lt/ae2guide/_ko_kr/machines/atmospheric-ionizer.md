---
navigation:
  title: 대기 이온화 장치
  icon: ae2lt:atmospheric_ionizer
  parent: machines/machines-index.md
item_ids:
  - ae2lt:atmospheric_ionizer
  - ae2lt:clear_condensate
  - ae2lt:rain_condensate
  - ae2lt:thunderstorm_condensate
---

# 대기 이온화 장치

<Row>
  <BlockImage id="ae2lt:atmospheric_ionizer" scale="4" />
</Row>

**대기 이온화 장치**는 날씨 제어 설비입니다. **날씨 응축체**와 AE 에너지를 소비하여 세계 날씨를 지정된 상태로 강제 전환하며, 낙뢰 수집기와 연계하여 안정적인 자연 낙뢰를 얻습니다.

## 날씨 응축체

날씨 응축체는 세 가지 유형이 있으며, 각각 세 가지 날씨에 대응합니다:

<ItemGrid>
  <ItemIcon id="ae2lt:clear_condensate" />
  <ItemIcon id="ae2lt:rain_condensate" />
  <ItemIcon id="ae2lt:thunderstorm_condensate" />
</ItemGrid>

| 응축체 유형 | 목표 날씨 | AE 소비 | 지속 시간 |
|---------|---------|---------|---------|
| 맑은 응축체 | 맑음 | 500,000 AE | 12,000 ~ 180,000 tick |
| 비 응축체 | 비 | 1,000,000 AE | 12,000 ~ 24,000 tick |
| 뇌우 응축체 | 뇌우 | 8,000,000 AE | 3,600 ~ 15,600 tick |

## 작동 흐름

1. 대기 이온화 장치를 ME 네트워크에 연결
2. 필요한 날씨 응축체를 입력 슬롯에 배치
3. 기계가 ME 네트워크에서 AE 에너지를 지속적으로 추출하여 이온화 진행
4. 이온화 완료 후, 세계 날씨가 목표 날씨로 강제 전환
5. 응축체는 사용 후 소비됨

## 주의사항

* 대기 이온화 장치는 **AE 에너지**(ME 네트워크에서 공급)를 소비하며, FE 에너지가 아닙니다
* 뇌우 응축체의 단회 소비가 가장 높으므로(8,000,000 AE), 네트워크 에너지 공급이 충분한지 확인하세요
* 날씨를 지원하지 않는 차원에서는 기계가 작동할 수 없습니다
* 목표 날씨가 이미 현재 날씨일 경우, 기계는 응축체를 소비하지 않습니다
