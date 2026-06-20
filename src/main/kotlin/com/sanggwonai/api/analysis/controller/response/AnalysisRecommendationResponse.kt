package com.sanggwonai.api.analysis.controller.response

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class AnalysisRecommendationResponse(
    val rank: Int,
    val vacancyId: String,
    val recommended: Boolean?,
    val score: BigDecimal,
    val distanceM: Int,
    val areaId: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val monthlyRent: Long?,
    val deposit: Long?,
    val maintenanceFee: Long?,
    val premium: Long?,
    val salePrice: Long?,
    val transactionType: String?,
    val facilityTotalSize: BigDecimal?,
    val locationArea: BigDecimal?,
    val category: String?,
    val roadAddress: String?,
    val lotAddress: String?,
    val businessMiddleCategoryName: String?,
    val businessSubCategoryName: String?,
    val floatingPopulationAnnualTotal: Long?,
    val restaurantCount500m: Int?,
    val cafeCount500m: Int?,
    val industryGrowthRate500m: BigDecimal?,
    val averageSalesPerStore: BigDecimal?,
    val busStopInfo: String?,
    val subwayStationInfo: String?,
    val parkingInfo: String?,
    val hourlyFloatingPopulation: List<BigDecimal>?,
    val history: VacancyHistoryResponse?
)

data class VacancyHistoryResponse(
    val scoreTrend: List<VacancyScoreTrendPointResponse>,
    val occupancyTimeline: List<VacancyOccupancyHistoryResponse>,
    val summary: VacancyHistorySummaryResponse
)

data class VacancyScoreTrendPointResponse(
    val year: Int,
    val score: BigDecimal,
    val delta: BigDecimal?,
    val confidenceLabel: String?,
    val basis: String?,
    val source: String?
)

data class VacancyOccupancyHistoryResponse(
    val id: String,
    val startedOn: LocalDate?,
    val endedOn: LocalDate?,
    val tenantLabel: String,
    val businessCategory: String?,
    val status: String,
    val monthlyRent: Long?,
    val deposit: Long?,
    val exitReasonCode: String?,
    val exitReasonSummary: String?,
    val source: String?
)

data class VacancyHistorySummaryResponse(
    val scoreDirection: String,
    val scoreDelta: BigDecimal?,
    val scoreLabel: String,
    val occupancyPatternLabel: String,
    val lastExitReason: String?,
    val source: String
)

data class AnalysisRecommendationsResponse(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val recommendations: List<AnalysisRecommendationResponse>,
    val updatedAt: Instant
)
