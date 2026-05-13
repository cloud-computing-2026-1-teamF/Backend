package com.sanggwonai.api.vacancy.dto

import java.math.BigDecimal

data class VacancyMetricReference(
    val categoryId: String?,
    val vacancyId: String?,
    val peerCount: Int,
    val footTrafficDaily: VacancyMetricDistribution,
    val competition500m: VacancyMetricDistribution,
    val averageSalesMonthly: VacancyMetricDistribution
)

data class VacancyMetricDistribution(
    val selected: BigDecimal?,
    val average: BigDecimal?,
    val median: BigDecimal?,
    val min: BigDecimal?,
    val max: BigDecimal?,
    val p10: BigDecimal?,
    val p25: BigDecimal?,
    val p75: BigDecimal?,
    val p90: BigDecimal?,
    val percentile: BigDecimal?
)
