package com.sanggwonai.api.vacancy.controller.response

import java.math.BigDecimal

data class VacancyMetricReferenceResponse(
    val categoryId: String?,
    val vacancyId: String?,
    val peerCount: Int,
    val footTrafficDaily: VacancyMetricDistributionResponse,
    val competition500m: VacancyMetricDistributionResponse,
    val averageSalesMonthly: VacancyMetricDistributionResponse
)

data class VacancyMetricDistributionResponse(
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
