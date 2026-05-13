package com.sanggwonai.api.analysis.service

import com.sanggwonai.api.analysis.controller.request.CreateAnalysisRequest
import com.sanggwonai.api.analysis.dto.AnalysisRecommendationDto
import com.sanggwonai.api.analysis.dto.AnalysisRecommendationsDto
import com.sanggwonai.api.analysis.dto.AnalysisEventDto
import com.sanggwonai.api.analysis.dto.AnalysisLinksDto
import com.sanggwonai.api.analysis.dto.AnalysisPollingData
import com.sanggwonai.api.analysis.dto.AnalysisSectionTodoDto
import com.sanggwonai.api.analysis.dto.CreateAnalysisData
import com.sanggwonai.api.analysis.entity.AnalysisEntity
import com.sanggwonai.api.analysis.entity.AnalysisStatus
import com.sanggwonai.api.analysis.entity.AnalysisVacancyRecommendationEntity
import com.sanggwonai.api.analysis.mapper.AnalysisMapper
import com.sanggwonai.api.analysis.repository.AnalysisRepository
import com.sanggwonai.api.analysis.repository.AnalysisVacancyRecommendationRepository
import com.sanggwonai.api.area.entity.AreaEntity
import com.sanggwonai.api.area.repository.AreaRepository
import com.sanggwonai.api.auth.entity.UserTier
import com.sanggwonai.api.auth.repository.UserRepository
import com.sanggwonai.api.auth.service.AuthContext
import com.sanggwonai.api.business.repository.BusinessTypeRepository
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.common.util.IdGenerator
import com.sanggwonai.api.vacancy.dto.RankedVacancy
import com.sanggwonai.api.vacancy.dto.VacancySearchCriteria
import com.sanggwonai.api.vacancy.entity.VacancyAccessibilityFoottrafficEntity
import com.sanggwonai.api.vacancy.entity.VacancyCategorySpatialEntity
import com.sanggwonai.api.vacancy.entity.VacancyCommonFeatureEntity
import com.sanggwonai.api.vacancy.entity.VacancyEntity
import com.sanggwonai.api.vacancy.service.VacancyDataset
import com.sanggwonai.api.vacancy.service.VacancyRankingService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Executors

@Service
class AnalysisService(
    private val analysisRepository: AnalysisRepository,
    private val recommendationRepository: AnalysisVacancyRecommendationRepository,
    private val vacancyRankingService: VacancyRankingService,
    private val vacancyDataset: VacancyDataset,
    private val areaRepository: AreaRepository,
    private val businessTypeRepository: BusinessTypeRepository,
    private val userRepository: UserRepository,
    private val analysisMapper: AnalysisMapper,
    private val analysisProperties: AnalysisProperties,
    private val progressWorker: AnalysisProgressWorker,
    private val clock: Clock
) {
    private val sseExecutor = Executors.newCachedThreadPool()

    @Transactional
    fun create(authContext: AuthContext, request: CreateAnalysisRequest): CreateAnalysisData {
        val user = userRepository.findById(authContext.userId)
            .orElseThrow { ApiException.of(ErrorType.AUTH_REQUIRED) }

        validateDailyLimit(user.id, user.tier)
        if (!businessTypeRepository.existsById(request.businessType)) {
            throw ApiException.of(ErrorType.INVALID_BUSINESS_TYPE)
        }
        val area = areaRepository.findById(request.areaId)
            .orElseThrow { ApiException.of(ErrorType.INVALID_AREA) }
        val searchPoint = request.resolveSearchPoint(area)
        val radiusM = request.radiusM ?: DEFAULT_RADIUS_M
        val transactionType = request.transactionType?.trim()?.takeIf { it.isNotEmpty() }
        val ranking = vacancyRankingService.findRanked(
            VacancySearchCriteria(
                areaId = request.areaId,
                categoryId = request.businessType,
                latitude = searchPoint.latitude,
                longitude = searchPoint.longitude,
                radiusM = radiusM,
                transactionType = transactionType,
                rentMax = request.budget?.rentMax,
                depositMax = request.budget?.depositMax,
                maintenanceFeeMax = request.budget?.maintenanceFeeMax,
                premiumMax = request.budget?.premiumMax,
                salePriceMax = request.budget?.salePriceMax
            )
        )
        val rankedVacancies = ranking.top
        if (rankedVacancies.isEmpty()) {
            throw ApiException.of(
                ErrorType.VACANCY_NOT_FOUND,
                mapOf(
                    "area_id" to request.areaId,
                    "radius_m" to radiusM.toString()
                )
            )
        }
        val topVacancy = rankedVacancies.first().vacancy

        val now = Instant.now(clock)
        val analysis = analysisRepository.save(
            AnalysisEntity(
                id = IdGenerator.next("an"),
                userId = authContext.userId,
                businessTypeKey = request.businessType,
                vacancyId = topVacancy.id,
                transactionType = transactionType,
                budgetDepositMax = request.budget?.depositMax,
                budgetRentMax = request.budget?.rentMax,
                budgetMaintenanceFeeMax = request.budget?.maintenanceFeeMax,
                budgetPremiumMax = request.budget?.premiumMax,
                budgetSalePriceMax = request.budget?.salePriceMax,
                centerLat = searchPoint.latitude.toCoordinate(),
                centerLng = searchPoint.longitude.toCoordinate(),
                radiusM = radiusM,
                analyzedVacancyCount = ranking.totalCandidates,
                status = AnalysisStatus.PENDING,
                progress = 0,
                stepIndex = null,
                stepTotal = null,
                stepLabel = null,
                errorCode = null,
                errorMessage = null,
                createdAt = now,
                completedAt = null,
                updatedAt = now
            )
        )
        val recommendationEntities = rankedVacancies.map { ranked ->
            AnalysisVacancyRecommendationEntity(
                id = IdGenerator.next("ar"),
                analysisId = analysis.id,
                vacancyId = ranked.vacancy.id,
                rank = ranked.rank,
                score = ranked.score,
                distanceM = ranked.distanceM,
                createdAt = now
            )
        }
        recommendationRepository.saveAll(recommendationEntities)

        progressWorker.runAsync(analysis.id)

        return CreateAnalysisData(
            id = analysis.id,
            vacancyId = analysis.vacancyId,
            status = analysis.status.name.lowercase(),
            progress = analysis.progress,
            createdAt = analysis.createdAt,
            estimatedSeconds = analysisProperties.estimatedSeconds,
            analyzedVacancyCount = analysis.analyzedVacancyCount,
            links = AnalysisLinksDto(
                self = "/v1/analyses/${analysis.id}",
                events = "/v1/analyses/${analysis.id}/events"
            ),
            recommendations = rankedVacancies.map(::toRecommendationDto)
        )
    }

    @Transactional(readOnly = true)
    fun getPolling(authContext: AuthContext, analysisId: String): AnalysisPollingData {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return analysisMapper.toPollingData(analysis)
    }

    @Transactional(readOnly = true)
    fun list(authContext: AuthContext, limit: Int?): List<AnalysisPollingData> {
        val pageSize = limit?.coerceIn(1, 50) ?: 20
        val analyses = analysisRepository.findByUserIdOrderByCreatedAtDesc(
            authContext.userId,
            PageRequest.of(0, pageSize)
        )
        if (analyses.isEmpty()) return emptyList()

        // Bulk-fetch recommendations once for all analyses on this page to avoid
        // an N+1. Group by analysisId, derive top-score + count per analysis.
        val recommendationsByAnalysis = recommendationRepository
            .findByAnalysisIdIn(analyses.map { it.id })
            .groupBy { it.analysisId }

        return analyses.map { entity ->
            val recs = recommendationsByAnalysis[entity.id] ?: emptyList()
            val base = analysisMapper.toPollingData(entity)
            base.copy(
                businessTypeKey = entity.businessTypeKey,
                transactionType = entity.transactionType,
                centerLat = entity.centerLat,
                centerLng = entity.centerLng,
                radiusM = entity.radiusM,
                budgetDepositMax = entity.budgetDepositMax,
                budgetRentMax = entity.budgetRentMax,
                budgetMaintenanceFeeMax = entity.budgetMaintenanceFeeMax,
                budgetPremiumMax = entity.budgetPremiumMax,
                budgetSalePriceMax = entity.budgetSalePriceMax,
                topScore = recs.maxOfOrNull { it.score },
                analyzedVacancyCount = entity.analyzedVacancyCount,
                recommendationCount = recs.size
            )
        }
    }

    @Transactional
    fun patch(authContext: AuthContext, analysisId: String): AnalysisPollingData {
        return analysisMapper.toPollingData(loadOwnedAnalysis(authContext.userId, analysisId))
    }

    @Transactional
    fun delete(authContext: AuthContext, analysisId: String) {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        analysisRepository.delete(analysis)
    }

    @Transactional(readOnly = true)
    fun stats(authContext: AuthContext): UserStatsData {
        return UserStatsData(
            totalAnalyses = analysisRepository.countByUserId(authContext.userId),
            savedAnalyses = analysisRepository.countByUserIdAndStatus(authContext.userId, AnalysisStatus.DONE),
            avgTopScore = 0
        )
    }

    fun openEvents(authContext: AuthContext, analysisId: String): SseEmitter {
        val emitter = SseEmitter(0L)
        sseExecutor.submit {
            try {
                var previous: AnalysisEventDto? = null
                while (true) {
                    val event = findEvent(authContext.userId, analysisId)
                    if (event != previous) {
                        emitter.send(SseEmitter.event().data(event))
                        previous = event
                    }
                    if (event.status == "done" || event.status == "failed") {
                        emitter.complete()
                        break
                    }
                    Thread.sleep(500)
                }
            } catch (_: Exception) {
                emitter.complete()
            }
        }
        return emitter
    }

    @Transactional(readOnly = true)
    fun findEvent(userId: String, analysisId: String): AnalysisEventDto {
        val analysis = loadOwnedAnalysis(userId, analysisId)
        return analysisMapper.toEventData(analysis)
    }

    @Transactional(readOnly = true)
    fun loadOwnedAnalysis(userId: String, analysisId: String): AnalysisEntity {
        val analysis = analysisRepository.findById(analysisId)
            .orElseThrow { ApiException.of(ErrorType.ANALYSIS_NOT_FOUND) }
        if (analysis.userId != userId) {
            throw ApiException.of(ErrorType.ANALYSIS_FORBIDDEN)
        }
        return analysis
    }

    @Transactional(readOnly = true)
    fun getRecommendedProperties(authContext: AuthContext, analysisId: String): AnalysisRecommendationsDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return AnalysisRecommendationsDto(
            analysisId = analysis.id,
            sectionKey = "recommended_properties",
            sectionLabel = "추천 매물",
            todo = "분석 생성 시점의 검색 반경/예산 조건으로 필터링한 뒤 점수순으로 정렬한 추천 공실이에요.",
            recommendations = loadRecommendations(analysis),
            updatedAt = analysis.updatedAt
        )
    }

    @Transactional(readOnly = true)
    fun getKeyMetrics(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "key_metrics", "주요 지표")
    }

    @Transactional(readOnly = true)
    fun getFootTraffic(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "foot_traffic", "유동인구")
    }

    @Transactional(readOnly = true)
    fun getCompetition(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "competition", "경쟁 점포")
    }

    @Transactional(readOnly = true)
    fun getEstimatedRevenue(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "estimated_revenue", "추정 매출")
    }

    @Transactional(readOnly = true)
    fun getIndustryGrowth(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "industry_growth", "업종 성장률")
    }

    @Transactional(readOnly = true)
    fun getAccessibility(authContext: AuthContext, analysisId: String): AnalysisSectionTodoDto {
        val analysis = loadOwnedAnalysis(authContext.userId, analysisId)
        return todoSection(analysis, "accessibility", "입지 접근성")
    }

    private fun todoSection(analysis: AnalysisEntity, sectionKey: String, sectionLabel: String): AnalysisSectionTodoDto {
        return AnalysisSectionTodoDto(
            analysisId = analysis.id,
            sectionKey = sectionKey,
            sectionLabel = sectionLabel,
            todo = "TODO: 공식 API/크롤링 데이터 스키마 확정 후 상세 필드 정의 예정",
            updatedAt = analysis.updatedAt
        )
    }

    private fun validateDailyLimit(userId: String, tier: UserTier) {
        // Daily limit temporarily disabled for every tier — FREE was the only
        // gated case, and the rest of the product is still in demo/eval mode
        // without billing, so the cap was preventing test users from poking at
        // the flow. Re-enable by restoring the FREE-specific 20/day check below
        // (or moving the threshold into AnalysisProperties) once subscriptions
        // ship.
        @Suppress("UNUSED_VARIABLE")
        val _unused = userId to tier
    }

    private fun loadRecommendations(analysis: AnalysisEntity): List<AnalysisRecommendationDto> {
        val rows = recommendationRepository.findByAnalysisIdOrderByRankAsc(analysis.id)
        if (rows.isEmpty()) {
            return legacyRecommendation(analysis)
        }
        val snapshot = vacancyDataset.snapshot()
        return rows.mapNotNull { row ->
            snapshot.vacancyById[row.vacancyId]?.let { vacancy ->
                val score = snapshot.scoreFor(vacancy.id, analysis.businessTypeKey)
                toRecommendationDto(
                    row = row,
                    vacancy = vacancy,
                    recommended = score?.recommended,
                    common = snapshot.commonByProperty[vacancy.id],
                    spatial = snapshot.spatialFor(vacancy.id, score),
                    accessibility = snapshot.accessibilityByProperty[vacancy.id],
                    categoryName = snapshot.categoryName(analysis.businessTypeKey)
                )
            }
        }
    }

    private fun legacyRecommendation(analysis: AnalysisEntity): List<AnalysisRecommendationDto> {
        val snapshot = vacancyDataset.snapshot()
        val vacancy = snapshot.vacancyById[analysis.vacancyId] ?: return emptyList()
        val score = snapshot.scoreFor(vacancy.id, analysis.businessTypeKey)
        val latitude = vacancy.latitude ?: analysis.centerLat
        val longitude = vacancy.longitude ?: analysis.centerLng
        val common = snapshot.commonByProperty[vacancy.id]
        val spatial = snapshot.spatialFor(vacancy.id, score)
        val accessibility = snapshot.accessibilityByProperty[vacancy.id]
        return listOf(
            AnalysisRecommendationDto(
                rank = 1,
                vacancyId = vacancy.id,
                recommended = score?.recommended,
                score = score?.scorePercent() ?: BigDecimal("0.00"),
                distanceM = 0,
                areaId = common?.areaCode ?: vacancy.dong ?: "",
                latitude = latitude,
                longitude = longitude,
                monthlyRent = vacancy.monthlyRent,
                deposit = vacancy.deposit,
                maintenanceFee = vacancy.maintenanceFee,
                premium = vacancy.premium,
                salePrice = vacancy.salePrice,
                transactionType = vacancy.transactionType,
                facilityTotalSize = common?.facilityTotalSize,
                locationArea = vacancy.dedicatedArea ?: common?.locationArea,
                category = snapshot.categoryName(analysis.businessTypeKey),
                roadAddress = vacancy.roadAddress,
                lotAddress = vacancy.lotAddress,
                businessMiddleCategoryName = vacancy.majorBusinessCategory,
                businessSubCategoryName = vacancy.middleBusinessCategory,
                floatingPopulationAnnualTotal = common?.floatingPopulationAnnualDensity?.toLong(),
                restaurantCount500m = common?.restaurantCount500m,
                cafeCount500m = common?.cafeCount500m,
                industryGrowthRate500m = spatial?.industryGrowthRate500m,
                averageSalesPerStore = common?.averageSalesPerStore?.divide(BigDecimal(3), 2, RoundingMode.HALF_UP),
                busStopInfo = accessibility?.busStopInfo,
                subwayStationInfo = accessibility?.subwayStationInfo,
                parkingInfo = accessibility?.parkingInfo,
                hourlyFloatingPopulation = accessibility?.hourlyFoottraffic()
            )
        )
    }

    private fun toRecommendationDto(ranked: RankedVacancy): AnalysisRecommendationDto {
        return toRecommendationDto(
            rank = ranked.rank,
            vacancy = ranked.vacancy,
            recommended = ranked.recommended,
            score = ranked.score,
            distanceM = ranked.distanceM,
            common = ranked.common,
            spatial = ranked.spatial,
            accessibility = vacancyDataset.snapshot().accessibilityByProperty[ranked.vacancy.id],
            categoryName = ranked.categoryName
        )
    }

    private fun toRecommendationDto(
        row: AnalysisVacancyRecommendationEntity,
        vacancy: VacancyEntity,
        recommended: Boolean?,
        common: VacancyCommonFeatureEntity?,
        spatial: VacancyCategorySpatialEntity?,
        accessibility: VacancyAccessibilityFoottrafficEntity?,
        categoryName: String?
    ): AnalysisRecommendationDto {
        return toRecommendationDto(
            rank = row.rank,
            vacancy = vacancy,
            recommended = recommended,
            score = row.score,
            distanceM = row.distanceM,
            common = common,
            spatial = spatial,
            accessibility = accessibility,
            categoryName = categoryName
        )
    }

    private fun toRecommendationDto(
        rank: Int,
        vacancy: VacancyEntity,
        recommended: Boolean?,
        score: BigDecimal,
        distanceM: Int,
        common: VacancyCommonFeatureEntity?,
        spatial: VacancyCategorySpatialEntity?,
        accessibility: VacancyAccessibilityFoottrafficEntity?,
        categoryName: String?
    ): AnalysisRecommendationDto {
        return AnalysisRecommendationDto(
            rank = rank,
            vacancyId = vacancy.id,
            recommended = recommended,
            score = score,
            distanceM = distanceM,
            areaId = common?.areaCode ?: vacancy.dong ?: "",
            latitude = vacancy.latitude ?: BigDecimal.ZERO,
            longitude = vacancy.longitude ?: BigDecimal.ZERO,
            monthlyRent = vacancy.monthlyRent,
            deposit = vacancy.deposit,
            maintenanceFee = vacancy.maintenanceFee,
            premium = vacancy.premium,
            salePrice = vacancy.salePrice,
            transactionType = vacancy.transactionType,
            facilityTotalSize = common?.facilityTotalSize,
            locationArea = vacancy.dedicatedArea ?: common?.locationArea,
            category = categoryName,
            roadAddress = vacancy.roadAddress,
            lotAddress = vacancy.lotAddress,
            businessMiddleCategoryName = vacancy.majorBusinessCategory,
            businessSubCategoryName = vacancy.middleBusinessCategory,
            floatingPopulationAnnualTotal = common?.floatingPopulationAnnualDensity?.toLong(),
            restaurantCount500m = common?.restaurantCount500m,
            cafeCount500m = common?.cafeCount500m,
            industryGrowthRate500m = spatial?.industryGrowthRate500m,
            averageSalesPerStore = common?.averageSalesPerStore?.divide(BigDecimal(3), 2, RoundingMode.HALF_UP),
            busStopInfo = accessibility?.busStopInfo,
            subwayStationInfo = accessibility?.subwayStationInfo,
            parkingInfo = accessibility?.parkingInfo,
            hourlyFloatingPopulation = accessibility?.hourlyFoottraffic()
        )
    }

    private fun CreateAnalysisRequest.resolveSearchPoint(area: AreaEntity): SearchPoint {
        center?.let { return SearchPoint(latitude = it.lat, longitude = it.lng) }
        if (x != null || y != null) {
            require(x != null && y != null) { "x and y must be provided together" }
            return SearchPoint(latitude = y, longitude = x)
        }
        return SearchPoint(
            latitude = area.centerLat.toDouble(),
            longitude = area.centerLng.toDouble()
        )
    }

    private fun Double.toCoordinate(): BigDecimal = BigDecimal.valueOf(this).setScale(6, RoundingMode.HALF_UP)

    companion object {
        private const val DEFAULT_RADIUS_M = 500
    }
}

data class UserStatsData(
    val totalAnalyses: Long,
    val savedAnalyses: Long,
    val avgTopScore: Int
)

private data class SearchPoint(
    val latitude: Double,
    val longitude: Double
)
