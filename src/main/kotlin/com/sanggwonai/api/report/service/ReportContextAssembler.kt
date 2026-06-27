package com.sanggwonai.api.report.service

import com.sanggwonai.api.analysis.dto.AnalysisRecommendationDto
import com.sanggwonai.api.analysis.dto.VacancyHistoryDto
import com.sanggwonai.api.analysis.service.AnalysisService
import com.sanggwonai.api.vacancy.dto.VacancyScoreExplanationDto
import com.sanggwonai.api.auth.service.AuthContext
import com.sanggwonai.api.report.config.ReportProperties
import com.sanggwonai.api.report.repository.ReviewStorePoolRepository
import com.sanggwonai.api.report.repository.VacancyInvestmentPaybackRepository
import com.sanggwonai.api.vacancy.dto.VacancyMetricDistribution
import com.sanggwonai.api.vacancy.dto.VacancyMetricReference
import com.sanggwonai.api.vacancy.repository.VacancyMetricReferenceRepository
import com.sanggwonai.api.vacancy.service.VacancyDataset
import com.sanggwonai.api.vacancy.service.VacancyDatasetSnapshot
import org.springframework.stereotype.Service
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 보고서 input_data 조립기 (Step 3).
 *
 * RDS(매물·메트릭·9카테고리 점수·리뷰 태그)를 읽어 reports/input_data.json 과 같은 형태의
 * Map 을 만든다. 이 Map 을 OpenAI 호출(Step 4)에 그대로 직렬화해 넣는다.
 *
 * 단위: 금액은 전부 만원(프론트/DTO와 동일). KRW 변환 없음.
 *
 * 데이터 출처
 *  - saved_analysis           : AnalysisEntity + 추천 DTO(AnalysisRecommendationDto)
 *  - top3[].foot/comp/rev      : vacancy_metric_references (매물별 selected)
 *  - top3[].floor              : VacancyEntity.floor (추천 DTO엔 없음)
 *  - vacancy_metric_reference  : 동일 업종 분포(top1)
 *  - selected_vacancy_extra    : 9-카테고리 점수 + 동네 시그널(VacancyCommonFeatureEntity)
 *  - section_05_review_insight : crawl_naver_stores 반경 50m 동종 가게 태그 -> 런타임 옵션B 집계(없으면 ch8 비활성)
 *  - section_06_investment_payback : 가영 산식으로 백엔드 계산(투자회수는 RDS 미적재)
 *
 * ⚠ 검증 포인트(로컬 컴파일 시 실제 필드명과 대조):
 *   VacancyCommonFeatureEntity 의 getter명(closureRate/openingRate/officialLandPrice/
 *   age2030PopulationRatio/femalePopulationRatio/eveningSalesRatio/commercialTurnoverType/
 *   commercialGrowthType), VacancyEntity.floor, VacancyCategoryScoreEntity.scorePercent().
 */
@Service
class ReportContextAssembler(
    private val analysisService: AnalysisService,
    private val vacancyDataset: VacancyDataset,
    private val metricRepository: VacancyMetricReferenceRepository,
    private val reviewStorePoolRepository: ReviewStorePoolRepository,
    private val reviewTagAggregator: ReviewTagAggregator,
    private val paybackRepository: VacancyInvestmentPaybackRepository,
    private val reportProperties: ReportProperties
) {
    private val zone = ZoneId.of("Asia/Seoul")
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun assemble(authContext: AuthContext, analysisId: String): Map<String, Any?> {
        val analysis = analysisService.loadOwnedAnalysis(authContext.userId, analysisId)
        val recs = analysisService.getRecommendedProperties(authContext, analysisId).recommendations
            .sortedBy { it.rank }
        val categoryId = analysis.businessTypeKey
        val snap = vacancyDataset.snapshot()
        val top1 = recs.firstOrNull()
        val top1Id = top1?.vacancyId
        val categoryLabel = snap.categoryName(categoryId) ?: top1?.category

        val createdAt = analysis.createdAt.atZone(zone)
        val top3 = recs.take(3).map { buildTop3Item(it, categoryId, categoryLabel, snap) }

        val result = LinkedHashMap<String, Any?>()
        result["saved_analysis"] = linkedMapOf(
            "id" to analysis.id,
            "date" to createdAt.format(dateFmt),
            "time" to createdAt.format(timeFmt),
            "region" to analysis.region,
            "regionDetail" to analysis.region,
            "radius" to analysis.radiusM,
            "displayName" to (analysis.region?.let { "$it 일대" }),
            "centerLat" to analysis.centerLat.toDouble(),
            "centerLng" to analysis.centerLng.toDouble(),
            "category" to categoryLabel,
            "businessTypeKey" to categoryId,
            "categoryEmoji" to categoryEmoji(categoryId),
            "budget" to budgetLabel(analysis),
            "topScore" to recs.maxOfOrNull { it.score.toInt() },
            "count" to (analysis.analyzedVacancyCount ?: recs.size),
            "saved" to analysis.saved,
            "top3" to top3
        )
        result["vacancy_metric_reference"] = top1Id?.let { metricBlock(metricRepository.find(categoryId, it)) }
        result["selected_vacancy_extra"] = top1?.let { buildSelectedExtra(it, categoryId, categoryLabel, snap) }

        // chapter_8 — 리뷰 태그(런타임 50m 동종 조인 + 옵션B 가중). Top3 각 매물 좌표 기준으로
        // crawl_naver_stores 에서 반경 내 동종 가게를 조회해 백엔드가 직접 집계. 데이터 없으면 비활성.
        // top-level=Top1 + properties[]=Top3.
        val radiusM = reportProperties.reviewInsightRadiusM
        val categoryTokens = REVIEW_CATEGORY_SYNONYMS[categoryId].orEmpty()
        val reviews = recs.take(3).mapNotNull { rec ->
            val vacancy = snap.vacancyById[rec.vacancyId] ?: return@mapNotNull null
            val rlat = vacancy.latitude?.toDouble() ?: return@mapNotNull null
            val rlng = vacancy.longitude?.toDouble() ?: return@mapNotNull null
            val stores = reviewStorePoolRepository.findWithin(rlat, rlng, radiusM, categoryTokens)
            if (stores.isEmpty()) null
            else Triple(rec.rank, rec.vacancyId, reviewTagAggregator.build(stores, radiusM, categoryLabel))
        }
        if (reviews.isNotEmpty()) {
            val primary = reviews.firstOrNull { it.second == top1Id }?.third ?: reviews.first().third
            val merged = LinkedHashMap<String, Any?>(primary)
            merged["properties"] = reviews.map { (rank, vid, ins) ->
                linkedMapOf(
                    "rank" to rank,
                    "vacancy_id" to vid,
                    "주소_간략" to (recs.firstOrNull { it.vacancyId == vid }?.roadAddress),
                    "scope" to ins["scope"],
                    "demand_tags" to (ins["demand_tags"] as? List<*>)?.take(5),
                    "popular_store_common_tags" to ins["popular_store_common_tags"],
                    "differentiator_tags" to ins["differentiator_tags"]
                )
            }
            result["section_05_review_insight"] = merged
        }

        // chapter_9 — 투자회수(백엔드 계산)
        result["section_06_investment_payback"] = top1?.let { buildPayback(recs, categoryId) }

        return result
    }

    // ── Top3 매물 1건 ──────────────────────────────────────────────
    private fun buildTop3Item(rec: AnalysisRecommendationDto, categoryId: String, categoryLabel: String?, snap: VacancyDatasetSnapshot): Map<String, Any?> {
        val metric = metricRepository.find(categoryId, rec.vacancyId)
        val vacancy = snap.vacancyById[rec.vacancyId]
        val common = snap.commonByProperty[rec.vacancyId]
        val accessibility = snap.accessibilityByProperty[rec.vacancyId]
        val score = snap.categoryScoreFor(rec.vacancyId, categoryId)
        val spatial = snap.spatialFor(rec.vacancyId, score)
        val floor = vacancy?.floor
        val compFallback = (rec.restaurantCount500m ?: 0) + (rec.cafeCount500m ?: 0)
        return linkedMapOf(
            "rank" to rec.rank,
            "vacancyId" to rec.vacancyId,
            "categoryId" to categoryId,
            "addr" to rec.roadAddress,
            "recommended" to (rec.recommended ?: true),
            "score" to rec.score.toInt(),
            "rent" to rec.monthlyRent,
            "deposit" to rec.deposit,
            "mgmt" to rec.maintenanceFee,
            "premium" to rec.premium,
            "salePrice" to rec.salePrice,
            "transactionType" to rec.transactionType,
            "area" to (rec.locationArea ?: rec.facilityTotalSize)?.toDouble(),
            "floor" to floor,
            "foot" to metric?.footTrafficDaily?.selected?.toLong(),
            "comp" to (metric?.competition500m?.selected?.toInt() ?: compFallback),
            "rev" to metric?.averageSalesMonthly?.selected?.toLong(),
            "growth" to rec.industryGrowthRate500m?.toDouble(),
            "footHourly" to rec.hourlyFloatingPopulation?.map { it.toInt() },
            "nearby" to linkedMapOf(
                "subway" to (accessibility?.subwayStationInfo ?: rec.subwayStationInfo),
                "bus" to (accessibility?.busStopInfo ?: rec.busStopInfo),
                "parking" to (accessibility?.parkingInfo ?: rec.parkingInfo)
            ),
            "property" to linkedMapOf(
                "roadAddress" to vacancy?.roadAddress,
                "lotAddress" to vacancy?.lotAddress,
                "detailAddress" to vacancy?.detailAddress,
                "buildingName" to vacancy?.buildingName,
                "transactionType" to vacancy?.transactionType,
                "floor" to vacancy?.floor,
                "totalFloors" to vacancy?.totalFloors,
                "buildingType" to vacancy?.buildingType,
                "buildingUse" to vacancy?.buildingUse,
                "majorBusinessCategory" to vacancy?.majorBusinessCategory,
                "middleBusinessCategory" to vacancy?.middleBusinessCategory,
                "registeredAt" to vacancy?.registeredAt,
                "viewCount" to vacancy?.viewCount,
                "favoriteCount" to vacancy?.favoriteCount
            ),
            "lease" to linkedMapOf(
                "deposit" to vacancy?.deposit,
                "monthlyRent" to vacancy?.monthlyRent,
                "maintenanceFee" to vacancy?.maintenanceFee,
                "premium" to vacancy?.premium,
                "salePrice" to vacancy?.salePrice,
                "dedicatedArea" to vacancy?.dedicatedArea?.toDouble(),
                "supplyArea" to vacancy?.supplyArea?.toDouble(),
                "facilityTotalSize" to common?.facilityTotalSize?.toDouble(),
                "locationArea" to common?.locationArea?.toDouble(),
                "officialLandPrice" to common?.officialLandPrice?.toLong()
            ),
            "facilities" to linkedMapOf(
                "parkingAvailable" to vacancy?.parkingAvailable,
                "parkingCount" to vacancy?.parkingCount?.toDouble(),
                "elevatorAvailable" to vacancy?.elevatorAvailable,
                "elevatorCount" to vacancy?.elevatorCount,
                "restroomType" to vacancy?.restroomType,
                "restroomCount" to vacancy?.restroomCount?.toDouble(),
                "heatingType" to vacancy?.heatingType,
                "airConditioner" to vacancy?.airConditioner,
                "heater" to vacancy?.heater,
                "terrace" to vacancy?.terrace,
                "rooftop" to vacancy?.rooftop,
                "interior" to vacancy?.interior,
                "storage" to vacancy?.storage,
                "lateNightOperationAvailable" to vacancy?.lateNightOperationAvailable,
                "priceNegotiable" to vacancy?.priceNegotiable,
                "rentAdjustable" to vacancy?.rentAdjustable,
                "rentFreePeriodAvailable" to vacancy?.rentFreePeriodAvailable,
                "multiUseFacility" to common?.multiUseFacility
            ),
            "population" to linkedMapOf(
                "floatingAnnual" to common?.floatingPopulationAnnualDensity?.toLong(),
                "floatingQuarterly" to common?.floatingPopulationQuarterlyDensity?.toLong(),
                "residentAnnual" to common?.residentPopulationAnnualDensity?.toLong(),
                "residentQuarterly" to common?.residentPopulationQuarterlyDensity?.toLong(),
                "workerAnnual" to common?.workerPopulationAnnualDensity?.toLong(),
                "workerQuarterly" to common?.workerPopulationQuarterlyDensity?.toLong(),
                "eveningPopulationRatio" to common?.eveningPopulationRatio?.toDouble(),
                "lateNightPopulationRatio" to common?.lateNightPopulationRatio?.toDouble(),
                "morningPopulationRatio" to common?.morningPopulationRatio?.toDouble(),
                "weekendPopulationRatio" to common?.weekendPopulationRatio?.toDouble(),
                "age2030PopulationRatio" to common?.age2030PopulationRatio?.toDouble(),
                "femalePopulationRatio" to common?.femalePopulationRatio?.toDouble(),
                "residentToFloatingRatio" to common?.residentToFloatingRatio?.toDouble(),
                "workerToFloatingRatio" to common?.workerToFloatingRatio?.toDouble()
            ),
            "commercial" to linkedMapOf(
                "restaurantCount250m" to common?.restaurantCount250m,
                "restaurantCount500m" to common?.restaurantCount500m,
                "restaurantCount1000m" to common?.restaurantCount1000m,
                "cafeCount250m" to common?.cafeCount250m,
                "cafeCount500m" to common?.cafeCount500m,
                "cafeCount1000m" to common?.cafeCount1000m,
                "sameCategoryRestaurantCount250m" to spatial?.sameCategoryRestaurantCount250m,
                "sameCategoryRestaurantCount500m" to spatial?.sameCategoryRestaurantCount500m,
                "sameCategoryRestaurantCount1000m" to spatial?.sameCategoryRestaurantCount1000m,
                "industryGrowthRate250m" to spatial?.industryGrowthRate250m?.toDouble(),
                "industryGrowthRate500m" to spatial?.industryGrowthRate500m?.toDouble(),
                "industryGrowthRate1000m" to spatial?.industryGrowthRate1000m?.toDouble(),
                "openingRate" to common?.openingRate?.toDouble(),
                "closureRate" to common?.closureRate?.toDouble(),
                "averageSalesPerStore" to common?.averageSalesPerStore?.toLong(),
                "eveningSalesRatio" to common?.eveningSalesRatio?.toDouble(),
                "lateNightSalesRatio" to common?.lateNightSalesRatio?.toDouble(),
                "weekendSalesRatio" to common?.weekendSalesRatio?.toDouble(),
                "age2030SalesRatio" to common?.age2030SalesRatio?.toDouble(),
                "femaleSalesRatio" to common?.femaleSalesRatio?.toDouble(),
                "totalSpending" to common?.totalSpending?.toLong(),
                "foodSpending" to common?.foodSpending?.toLong(),
                "spendingPerStore" to common?.spendingPerStore?.toLong(),
                "commercialTurnoverType" to ((common?.commercialTurnoverType?.signum() ?: 0) != 0),
                "commercialGrowthType" to ((common?.commercialGrowthType?.signum() ?: 0) != 0)
            ),
            // v6.5 매물별 — 보고서 탭이 매물마다 점수분해/미래전망/내력/9업종을 그리려면 top3 각 매물에 직접 들어가야 함.
            "scoreExplanation" to scoreExplanationBlock(rec.scoreExplanation),
            "horizon" to rec.horizonScores.sortedBy { it.horizonYears }
                .map { linkedMapOf("years" to it.horizonYears, "score" to it.survivalScore.toInt()) },
            "history" to rec.history?.scoreTrend
                ?.map { linkedMapOf("year" to it.year, "score" to it.score.toDouble(), "delta" to it.delta?.toDouble()) },
            "occupancy" to occupancyBlock(rec.history),
            "selected_vacancy_extra" to buildSelectedExtra(rec, categoryId, categoryLabel, snap)
        )
    }

    // ── 매물별 점수 분해(SHAP) — 렌더러 scoreExplanation.top_features 형태로 ──────────
    private fun scoreExplanationBlock(exp: VacancyScoreExplanationDto?): Map<String, Any?>? = exp?.let {
        linkedMapOf(
            "source" to it.source,
            "top_features" to it.features.sortedBy { f -> f.rank }.map { f ->
                linkedMapOf(
                    "rank" to f.rank,
                    "label" to f.featureLabel,
                    "effect" to f.effect,
                    "current" to f.currentValue?.toDouble(),
                    "average" to f.averageValue?.toDouble(),
                    "unit" to f.displayUnit
                )
            }
        )
    }

    // ── 매물별 점유 이력 — 렌더러 occupancy{tenant_count,closed_count,score_direction,timeline[]} 형태로 ──
    private fun occupancyBlock(history: VacancyHistoryDto?): Map<String, Any?>? = history?.let { h ->
        val timeline = h.occupancyTimeline
        val trend = h.scoreTrend
        val dir = if (trend.size >= 2) {
            when (trend.last().score.compareTo(trend.first().score)) { 1 -> "up"; -1 -> "down"; else -> "flat" }
        } else "flat"
        linkedMapOf(
            "tenant_count" to timeline.size,
            "closed_count" to timeline.count { it.endedOn != null || it.status == "closed" },
            "score_direction" to dir,
            "pattern_label" to h.summary.occupancyPatternLabel,
            "timeline" to timeline.map { ev ->
                linkedMapOf(
                    "tenant" to ev.tenantLabel,
                    "category" to ev.businessCategory,
                    "from" to ev.startedOn?.toString(),
                    "to" to ev.endedOn?.toString(),
                    "status" to (if (ev.endedOn != null) "closed" else "open")
                )
            }
        )
    }

    // ── vacancy_metric_reference ───────────────────────────────────
    private fun metricBlock(m: VacancyMetricReference?): Map<String, Any?>? = m?.let {
        linkedMapOf(
            "peerCount" to it.peerCount,
            "footTrafficDaily" to distBlock(it.footTrafficDaily),
            "competition500m" to distBlock(it.competition500m),
            "averageSalesMonthly" to distBlock(it.averageSalesMonthly)
        )
    }

    private fun distBlock(d: VacancyMetricDistribution): Map<String, Any?> = linkedMapOf(
        "selected" to d.selected?.toLong(),
        "average" to d.average?.toLong(),
        "median" to d.median?.toLong(),
        "min" to d.min?.toLong(),
        "max" to d.max?.toLong(),
        "p10" to d.p10?.toLong(),
        "p25" to d.p25?.toLong(),
        "p75" to d.p75?.toLong(),
        "p90" to d.p90?.toLong(),
        "percentile" to d.percentile?.toInt()
    )

    // ── selected_vacancy_extra ─────────────────────────────────────
    private fun buildSelectedExtra(
        top1: AnalysisRecommendationDto,
        categoryId: String,
        categoryLabel: String?,
        snap: VacancyDatasetSnapshot
    ): Map<String, Any?> {
        val id = top1.vacancyId
        val nine = LinkedHashMap<String, Any?>()
        val scored = mutableListOf<Pair<String, Int>>()
        for ((cid, name) in CATEGORY_NAMES) {
            val pct = snap.categoryScoreFor(id, cid)?.scorePercent()?.toInt()
            nine[name] = pct
            if (pct != null) scored.add(cid to pct)
        }
        val ranking = scored.sortedByDescending { it.second }
            .indexOfFirst { it.first == categoryId }
            .let { if (it < 0) null else it + 1 }
        val common = snap.commonByProperty[id]
        return linkedMapOf(
            "vacancy_id" to id,
            "selected_category" to categoryLabel,
            "selected_score_percent" to snap.categoryScoreFor(id, categoryId)?.scorePercent()?.toInt(),
            "nine_category_scores" to nine,
            "ranking_in_9_categories" to ranking,
            "neighborhood_signals" to linkedMapOf(
                "동네_폐업률" to common?.closureRate?.toDouble(),
                "동네_개업율" to common?.openingRate?.toDouble(),
                "공시지가_원per_m2" to common?.officialLandPrice?.toLong(),
                "유동인구_2030_비율" to common?.age2030PopulationRatio?.toDouble(),
                "유동인구_여성_비율" to common?.femalePopulationRatio?.toDouble(),
                "동네_저녁매출_비율" to common?.eveningSalesRatio?.toDouble(),
                "상권_교체활발형" to ((common?.commercialTurnoverType?.signum() ?: 0) != 0),
                "상권_성장형" to ((common?.commercialGrowthType?.signum() ?: 0) != 0),
                "업종성장률_500m" to top1.industryGrowthRate500m?.toDouble()
            ),
            "_doc_seoul_avg" to linkedMapOf(
                "서울_평균_폐업률" to 0.14,
                "서울_평균_2030_비율" to 0.28,
                "서울_평균_저녁매출_비율" to 0.28
            )
        )
    }

    // ── section_06_investment_payback (백엔드 계산) ────────────────
    private fun buildPayback(recs: List<AnalysisRecommendationDto>, categoryId: String): Map<String, Any?> {
        val a = reportProperties.assumptions
        val margin = reportProperties.categoryMargins[categoryId] ?: 0.12
        val rows = recs.take(3).map { rec ->
            val p = payback(rec, categoryId, margin)
            linkedMapOf<String, Any?>(
                "rank" to rec.rank,
                "vacancy_id" to rec.vacancyId,
                "주소_간략" to rec.roadAddress,
                "초기투자비_만원" to p.initial,
                "점포당평균매출_만원" to p.sales,
                "월순이익_만원" to p.netProfit,
                "투자회수기간_개월" to p.months,
                "투자회수평가" to p.label,
                "기존시설_활용가능" to p.facilityReusable,
                "기존시설_문구" to p.facilityReuseNote,
                "매출매칭기준" to p.basis
            )
        }
        val top1 = recs.first()
        val p1 = payback(top1, categoryId, margin)
        val rent = (top1.monthlyRent ?: 0) + (top1.maintenanceFee ?: 0) // 만원
        val bepRevenueMan = if (margin > 0) rent.toDouble() / margin else 0.0
        val bepPeakUnits = peakUnits(bepRevenueMan, a)
        val targetPeakUnits = p1.sales?.let { peakUnits(it.toDouble(), a) }
        return linkedMapOf(
            "_doc" to "투자 회수. 가영 실데이터(vacancy_investment_payback) 우선, 미적재 매물은 백엔드 추정(영업이익률 ${margin}). " +
                "초기투자비에 인테리어·설비 capex 미포함. '기존시설_활용가능'=true 면 이전에도 같은 업종이라 인테리어·설비비 절감 여지.",
            "vacancy_id" to top1.vacancyId,
            "matched_category" to categoryId,
            "초기투자비_만원" to p1.initial,
            "점포당평균매출_만원" to p1.sales,
            "월순이익_만원" to p1.netProfit,
            "투자회수기간_개월" to p1.months,
            "투자회수평가" to p1.label,
            "기존시설_활용가능" to p1.facilityReusable,
            "기존시설_문구" to p1.facilityReuseNote,
            "매출매칭기준" to p1.basis,
            "bep_action" to linkedMapOf(
                "객단가원" to a.avgTicketPriceKrw,
                "피크_손익분기_잔수" to bepPeakUnits,
                "피크_목표매출_잔수" to targetPeakUnits,
                "_doc" to "손익분기/추정매출을 영업일(${a.operatingDaysPerMonth})·피크비중(${a.peakShareOfDailySales})·객단가로 환산한 피크시간 필요 판매량"
            ),
            "properties" to rows
        )
    }

    private data class Payback(
        val initial: Long?, val sales: Long?, val netProfit: Long?,
        val months: Double?, val label: String, val basis: String,
        // 기존 시설 활용 가능(이전에도 같은 업종 -> 인테리어·설비 capex 절감). 가영 실데이터에만 존재.
        val facilityReusable: Boolean = false, val facilityReuseNote: String? = null
    )

    private fun payback(rec: AnalysisRecommendationDto, categoryId: String, margin: Double): Payback {
        // 가영 실데이터 우선 (vacancy_investment_payback, property_id 일치). 미적재면 아래 백엔드 추정 폴백.
        paybackRepository.find(rec.vacancyId, categoryId)?.let { p ->
            return Payback(
                initial = (p.initialInvestmentMan ?: 0).toLong(),
                sales = p.storeAvgSalesMan?.toLong(),
                netProfit = p.monthlyNetProfitMan?.toLong(),
                months = p.paybackMonths?.toDouble(),
                label = p.paybackLabel
                    ?: paybackLabel(p.paybackMonths?.toDouble(), p.storeAvgSalesMan?.toLong(), p.monthlyNetProfitMan?.toLong()),
                basis = "상권분석 추정매출(가영·${p.salesBasis ?: "상권"})",
                facilityReusable = p.facilityReusable,
                facilityReuseNote = p.facilityReuseNote
            )
        }
        val a = reportProperties.assumptions
        val deposit = rec.deposit ?: 0
        val premium = rec.premium ?: 0
        val rent = rec.monthlyRent ?: 0
        val maint = rec.maintenanceFee ?: 0
        val brokerage = ((deposit + rent * 100).toDouble() * a.brokerageRate).roundToLong() // 만원
        val sale = rec.salePrice
        // 매매(거래유형 '매매' 또는 매매가 존재)인데 매매가가 미적재면 초기투자비를 계산할 수 없다.
        // 이전엔 else 로 빠져 (월세+관리비)×3 만 잡혀 '초기투자비 수십만원·회수 0개월' 같은 오값이 나왔음 → '매매가 미확인'.
        val isSale = rec.transactionType == "매매" || (sale != null && sale > 0)
        if (isSale && (sale == null || sale <= 0)) {
            return Payback(null, null, null, null, "매매가 미확인", "매매가 미적재로 회수 계산 불가")
        }
        val initial = if (isSale) {
            sale!! + brokerage
        } else {
            deposit + premium + brokerage + (rent + maint) * a.workingCapitalMonths
        }
        // 점포당평균매출(만원/월): 동일업종 분포의 selected(=지역평균). 가영 상권매칭은 RDS 미반영.
        val sales = metricRepository.find(categoryId, rec.vacancyId)?.averageSalesMonthly?.selected?.toLong()
        val netProfit = sales?.let { (it * margin - (rent + maint)).roundToLong() }
        val months = if (netProfit != null && netProfit > 0) round1(initial.toDouble() / netProfit) else null
        return Payback(initial, sales, netProfit, months, paybackLabel(months, sales, netProfit), "지역평균 추정매출(가영 상권매칭 미반영)")
    }

    private fun peakUnits(monthlyRevenueMan: Double, a: ReportProperties.Assumptions): Int {
        val dailyMan = monthlyRevenueMan / a.operatingDaysPerMonth
        val peakWon = dailyMan * a.peakShareOfDailySales * 10_000.0
        return (peakWon / a.avgTicketPriceKrw).roundToInt()
    }

    private fun paybackLabel(months: Double?, sales: Long?, net: Long?): String = when {
        sales == null -> "데이터없음"
        net == null || net <= 0 -> "적자"
        months == null -> "적자"
        months <= 12 -> "1년 이내 회수"
        months <= 24 -> "1~2년 회수"
        months <= 36 -> "2~3년 회수"
        else -> "3년 이상"
    }

    private fun budgetLabel(analysis: com.sanggwonai.api.analysis.entity.AnalysisEntity): String {
        val any = listOfNotNull(
            analysis.budgetDepositMax, analysis.budgetRentMax,
            analysis.budgetMaintenanceFeeMax, analysis.budgetPremiumMax, analysis.budgetSalePriceMax
        )
        return if (any.isEmpty()) "예산 조건 없음" else "예산 조건 있음"
    }

    private fun categoryEmoji(key: String?): String = when (key) {
        "1" -> "🍚"; "2" -> "🥡"; "3" -> "🍣"; "4" -> "🍝"; "5" -> "🍽️"
        "6" -> "🍱"; "7" -> "🍔"; "8" -> "🍺"; "9" -> "☕"; else -> "🍽️"
    }

    private fun round1(x: Double): Double = (x * 10).roundToLong() / 10.0

    companion object {
        // category_id("1".."9") -> input_data.nine_category_scores 의 한글 키
        val CATEGORY_NAMES: Map<String, String> = linkedMapOf(
            "1" to "한식", "2" to "중식", "3" to "일식", "4" to "서양식", "5" to "기타",
            "6" to "구내식당및뷔페", "7" to "패스트푸드", "8" to "주점업", "9" to "카페디저트"
        )

        // category_id("1".."9") -> crawl_naver_stores.category(네이버 원문) LIKE 매칭용 동의어 토큰.
        // 크롤러 실측 187종(홍대 2km, 6,634곳) 전수 검증으로 보강 — 커버리지 76% -> 99%.
        // 변경점: (1) "바" -> "bar" (소바/우동 등 오매칭 제거)  (2) 이자카야는 주점(8)에만
        //         (3) 5(기타)에서 "식당" 제거(중식당/일식당 중복 매칭 방지)
        //         (4) 고기/곱창/족발/감자탕/돈가스/라면/케이크/아이스크림 등 누락 토큰 추가.
        // 미매칭(의도적): "차"(포장마차 오염 회피), 서비스/장소대여·게임·가공식품(요식업 아님).
        val REVIEW_CATEGORY_SYNONYMS: Map<String, List<String>> = linkedMapOf(
            "1" to listOf("한식", "백반", "가정식", "국밥", "분식", "찌개", "전골", "한정식", "국수",
                "칼국수", "막국수", "냉면", "기사식당", "고기", "육류", "곱창", "막창", "족발", "보쌈",
                "순대", "감자탕", "곰탕", "설렁탕", "해장국", "추어탕", "삼계탕", "백숙", "닭갈비",
                "닭발", "닭볶음탕", "찜닭", "닭요리", "장어", "주꾸미", "낙지", "아귀", "해물",
                "생선구이", "매운탕", "쌈밥", "보리밥", "비빔밥", "두부", "오리요리", "영양탕", "사찰",
                "빈대떡", "떡볶이", "게요리", "조개", "대게", "굴요리", "만두", "소고기", "돼지고기",
                "정육", "불닭", "김밥", "죽"),
            "2" to listOf("중식", "중국집", "중화", "마라", "양꼬치", "양갈비", "딤섬"),
            "3" to listOf("일식", "초밥", "스시", "라멘", "라면", "돈카츠", "돈가스", "우동", "소바",
                "생선회", "복어", "카레", "오니기리", "덮밥", "오므라이스", "샤브"),
            "4" to listOf("양식", "이탈리안", "이탈리아", "파스타", "스파게티", "스테이크", "레스토랑",
                "피자", "샐러드", "다이어트"),
            "5" to listOf("음식점", "베트남", "태국", "인도", "아시아", "퓨전", "멕시코", "남미",
                "스페인", "프랑스", "그리스", "터키", "푸드코트", "도시락", "컵밥", "야식"),
            "6" to listOf("뷔페", "부페", "구내식당"),
            "7" to listOf("패스트푸드", "버거", "햄버거", "치킨", "토스트", "핫도그", "맘스터치",
                "후렌치후라이"),
            "8" to listOf("주점", "술집", "호프", "이자카야", "포차", "포장마차", "bar", "펍", "와인",
                "칵테일", "오뎅"),
            "9" to listOf("카페", "커피", "디저트", "베이커리", "브런치", "찻집", "제과", "빵", "케이크",
                "아이스크림", "빙수", "주스", "과일", "초콜릿", "도넛", "크레페", "와플", "호떡",
                "호두과자", "베이글", "샌드위치", "홍차", "한과")
        )
    }
}
