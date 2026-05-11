package com.sanggwonai.api.analysis.dto

import java.math.BigDecimal
import java.time.Instant

data class AnalysisRecommendationDto(
    val rank: Int,
    val vacancyId: String,
    val score: BigDecimal,
    val distanceM: Int,
    val areaId: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val monthlyRent: Long?,
    val deposit: Long?,
    val maintenanceFee: Long?,
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
    val averageSalesPerStore: BigDecimal?
)

data class AnalysisRecommendationsDto(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val recommendations: List<AnalysisRecommendationDto>,
    val updatedAt: Instant
)

