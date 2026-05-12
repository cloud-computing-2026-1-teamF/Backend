package com.sanggwonai.api.analysis.controller.response

import java.math.BigDecimal
import java.time.Instant

data class AnalysisPollingResponse(
    val id: String,
    val vacancyId: String,
    val status: String,
    val progress: Int,
    val step: AnalysisStepResponse?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val error: AnalysisErrorResponse?,
    val businessTypeKey: String? = null,
    val centerLat: BigDecimal? = null,
    val centerLng: BigDecimal? = null,
    val radiusM: Int? = null,
    val budgetDepositMax: Long? = null,
    val budgetRentMax: Long? = null,
    val budgetMaintenanceFeeMax: Long? = null,
    val topScore: BigDecimal? = null,
    val recommendationCount: Int? = null
)
