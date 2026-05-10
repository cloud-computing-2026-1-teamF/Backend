package com.sanggwonai.api.vacancy.controller.response

import java.math.BigDecimal

data class VacancySearchResponse(
    val items: List<VacancyResponse>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val summary: VacancySearchSummaryResponse
)

data class VacancySearchSummaryResponse(
    val total: Long,
    val averageScore: BigDecimal?,
    val averageRent: BigDecimal?,
    val averageDeposit: BigDecimal?,
    val averageMaintenanceFee: BigDecimal?,
    val minRent: Long?,
    val maxRent: Long?,
    val areaCount: Int
)

