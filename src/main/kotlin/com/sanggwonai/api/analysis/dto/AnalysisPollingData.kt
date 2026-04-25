package com.sanggwonai.api.analysis.dto

import java.time.Instant

data class AnalysisPollingData(
    val id: String,
    val status: String,
    val progress: Int,
    val step: AnalysisStepDto?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val error: AnalysisErrorDto?
)
