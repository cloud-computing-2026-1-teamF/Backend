package com.sanggwonai.api.analysis.dto

import com.sanggwonai.api.vacancy.dto.VacancyHorizonScoreDto
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class AnalysisRecommendationDto(
    val rank: Int,
    val vacancyId: String,
    val recommended: Boolean?,
    val score: BigDecimal,
    val horizonScores: List<VacancyHorizonScoreDto>,
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
    val history: VacancyHistoryDto?
)

data class VacancyHistoryDto(
    val scoreTrend: List<VacancyScoreTrendPointDto>,
    val occupancyTimeline: List<VacancyOccupancyHistoryDto>,
    val summary: VacancyHistorySummaryDto
)

data class VacancyScoreTrendPointDto(
    val year: Int,
    val score: BigDecimal,
    val delta: BigDecimal?,
    val confidenceLabel: String?,
    val basis: String?,
    val source: String?
)

data class VacancyOccupancyHistoryDto(
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

data class VacancyHistorySummaryDto(
    val scoreDirection: String,
    val scoreDelta: BigDecimal?,
    val scoreLabel: String,
    val occupancyPatternLabel: String,
    val lastExitReason: String?,
    val source: String
)

data class AnalysisRecommendationsDto(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val recommendations: List<AnalysisRecommendationDto>,
    val updatedAt: Instant
)
