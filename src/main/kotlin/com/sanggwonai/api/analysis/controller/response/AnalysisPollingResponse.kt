package com.sanggwonai.api.analysis.controller.response

import java.time.Instant

data class AnalysisPollingResponse(
    val id: String,
    val status: String,
    val progress: Int,
    val step: AnalysisStepResponse?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val error: AnalysisErrorResponse?
)
