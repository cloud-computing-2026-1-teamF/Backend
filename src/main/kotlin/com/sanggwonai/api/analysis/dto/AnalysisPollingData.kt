package com.sanggwonai.api.analysis.dto

import java.math.BigDecimal
import java.time.Instant

data class AnalysisPollingData(
    val id: String,
    val vacancyId: String,
    val status: String,
    val progress: Int,
    val step: AnalysisStepDto?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val error: AnalysisErrorDto?,
    // Summary fields. Populated by the list endpoint so the History page can
    // render cards straight from the API without dipping into localStorage.
    // The single-analysis polling endpoint leaves them null because callers
    // there only care about status/progress.
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
