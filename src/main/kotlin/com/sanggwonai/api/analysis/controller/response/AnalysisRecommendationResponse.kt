package com.sanggwonai.api.analysis.controller.response

import java.math.BigDecimal
import java.time.Instant

data class AnalysisRecommendationResponse(
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
    val businessMiddleCategoryName: String?,
    val businessSubCategoryName: String?,
    val floatingPopulationAnnualTotal: Long?,
    val restaurantCount500m: Int?,
    val cafeCount500m: Int?,
    val industryGrowthRate500m: BigDecimal?,
    val averageSalesPerStore: BigDecimal?
)

data class AnalysisRecommendationsResponse(
    val analysisId: String,
    val sectionKey: String,
    val sectionLabel: String,
    val todo: String,
    val recommendations: List<AnalysisRecommendationResponse>,
    val updatedAt: Instant
)

