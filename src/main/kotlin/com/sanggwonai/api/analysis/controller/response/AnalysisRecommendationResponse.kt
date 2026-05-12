package com.sanggwonai.api.analysis.controller.response

import java.math.BigDecimal
import java.time.Instant

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
    val hourlyFloatingPopulation: List<BigDecimal>?
)

data class AnalysisRecommendationsResponse(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val recommendations: List<AnalysisRecommendationResponse>,
    val updatedAt: Instant
)
