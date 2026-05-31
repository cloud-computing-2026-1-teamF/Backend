# 분석 이력 기반 PDF 보고서 — 미니-RAG 설계물

생성형 AI(Claude)로 "줄글 총평 + ROI/매출 기반 액션 가이드" 보고서를 만드는 데 필요한
프롬프트·스키마·예시 묶음. 라이브 경로는 **Kotlin-direct**(Backend가 직접 Claude 호출 + PDF 렌더).

## 파일

| 파일 | 역할 |
|---|---|
| `guideline.md` | LLM **system 프롬프트**. 페르소나·규칙·구조·금융 해석법 |
| `context.schema.json` | 입력 스키마. `AnalysisRecommendationDto` → context 매핑 정의 |
| `context.example.json` | 입력 예시 (목동 카페). 프롬프트 튜닝·샘플 PDF 입력 |
| `output.schema.json` | LLM **출력 스키마**. PDF 템플릿 바인딩·검증용 |
| `output.example.json` | 위 입력에 대한 골드 출력. 튜닝 기준·샘플 PDF 본문 |

## 파이프라인 (Kotlin-direct)

```
AnalysisRecommendationDto (+ UserTier 게이트)
   │  1) ContextAssembler: DTO·가정값 → context.json,  derived.* 결정적 계산
   ▼
Claude Messages API  (RestClient)
   system = guideline.md,  user = context.json 직렬화
   │  2) 응답 = output.schema.json 형식 JSON
   ▼
Jackson 검증 (tools.jackson)  → 실패 시 1회 재시도 → 그래도 실패면 Track B 폴백
   │  3)
   ▼
Jinja/openhtmltopdf 렌더 → PDF → (S3 presigned 또는 데모 inline)
```

핵심: **숫자는 Kotlin이 계산**(`derived`)하고 LLM은 줄글만 쓴다. 심사 중 LLM 산수 오류 리스크 제거.

## 금융 공식 (ContextAssembler가 계산해 `derived`에 주입)

| 값 | 산식 |
|---|---|
| `initialInvestmentKrw` | 보증금 + 권리금 + 인테리어비 |
| `monthlyFixedCostKrw` | 월세 + 관리비 + 인건비 |
| `monthlyCogsKrw` | 추정월매출 × 원가율 |
| `monthlyNetProfitKrw` | 추정월매출 − 고정비 − 변동비 |
| `roiMonths` | 초기투자금 ÷ 월 순이익 |
| `bep.monthlyRevenueKrw` | 고정비 ÷ (1 − 원가율) |
| `bep.dailyRevenueKrw` | BEP월매출 ÷ 영업일수 |
| `bep.peakUnitsPerDay` | ⌈ BEP일매출 × 피크비중 ÷ 객단가 ⌉ |
| `target.dailyRevenueKrw` | 추정월매출 ÷ 영업일수 |
| `target.peakUnitsPerDay` | ⌈ 목표일매출 × 피크비중 ÷ 객단가 ⌉ |

피크 판매량은 **올림(ceil)** — "최소 N잔 이상" 의미.

### 예시 검산 (`context.example.json`)
- 초기투자금 = 30,000,000 + 20,000,000 + 45,000,000 = **95,000,000**
- 고정비 = 2,200,000 + 200,000 + 3,800,000 = **6,200,000**
- 변동비 = 18,500,000 × 0.35 = **6,475,000**
- 순이익 = 18,500,000 − 6,200,000 − 6,475,000 = **5,825,000**
- ROI = 95,000,000 ÷ 5,825,000 = **16.3개월**
- BEP월매출 = 6,200,000 ÷ 0.65 = **9,538,462** → 일 366,864 → 피크 ⌈366,864×0.45÷6,500⌉ = **26잔**
- 목표 = 18,500,000 ÷ 26 = 711,538/일 → 피크 ⌈711,538×0.45÷6,500⌉ = **50잔**

## 가정값 (`app.report`, ConfigurationProperties — `AnalysisProperties` 패턴)

```yaml
# application.yml
app:
  report:
    cogs-ratio: 0.35
    labor-cost-monthly-krw: 3800000
    avg-ticket-price-krw: 6500
    interior-setup-cost-krw: 45000000
    operating-days-per-month: 26
    peak-share-of-daily-sales: 0.45
    peak-hours-per-day: 3
  claude:
    model: claude-sonnet-4-6
    max-tokens: 2000
    temperature: 0.3
```

> 가정값은 업종별로 다르게 둘 수 있음(카페/음식점). 값은 반드시 출력 `disclaimers`에 노출.

## Claude 호출 권장

- `system` = `guideline.md` 원문, `user` = context.json 문자열.
- `temperature` 0.3 (일관성), `max_tokens` ~2000.
- 출력 강제: 프롬프트가 "첫 글자 `{`, 끝 글자 `}`"를 요구함. 그래도 코드펜스가 붙어 오면
  파싱 전 ```json 제거. `tools.jackson`으로 `output.schema.json` 검증.
- 실패(파싱/스키마) → 1회 재시도 → 폴백(`output.example.json` 또는 샘플 PDF).

## ⚠️ 단위 정규화

`AnalysisRecommendationDto`의 `monthlyRent/deposit/...`(Long)과 프론트 `rev`(만원)의 단위가
혼재한다. **ContextAssembler에서 전부 원(KRW) 정수로 정규화**한 뒤 context를 만들 것. (만원이면 ×10000)
이 한 줄을 놓치면 ROI/BEP가 10,000배 틀어진다 — 통합 전 반드시 검증.

## 투 트랙 연결

- Track A(라이브): 위 파이프라인.
- Track B(Pre-baked): `Frontend` `USE_MOCK=true` + mock store가 `output.example.json` 기반
  보고서와 샘플 PDF(`public/uploads/`)를 반환. 라이브 실패 시 플래그만 전환.

## 튜닝 루프

1. `context.example.json`으로 호출 → 출력을 `output.example.json`과 대조.
2. 어긋나면 `guideline.md`만 수정(스키마는 계약이므로 고정).
3. 업종/시나리오 늘릴 때 `context.example.<업종>.json` + 골드 출력 쌍을 추가.
