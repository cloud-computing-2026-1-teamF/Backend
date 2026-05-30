package com.sanggwonai.api.vacancy.dto

import java.math.BigDecimal

data class VacancyExplorerResult(
    val items: List<VacancyDto>,
    val total: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int,
    val summary: VacancyExplorerSummary
)

data class VacancyExplorerSummary(
    val total: Long,
    val averageScore: BigDecimal?,
    val averageRent: BigDecimal?,
    val averageDeposit: BigDecimal?,
    val averageSalePrice: BigDecimal?,
    val averageMaintenanceFee: BigDecimal?,
    val minRent: Long?,
    val maxRent: Long?,
    val areaCount: Int
)

