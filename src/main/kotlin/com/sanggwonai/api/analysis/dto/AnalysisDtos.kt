package com.sanggwonai.api.analysis.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CreateAnalysisRequest(
    @field:NotBlank
    val businessType: String,

    @field:NotBlank
    val areaId: String,

    @field:Valid
    val budget: AnalysisBudgetRequest?
)

data class AnalysisBudgetRequest(
    @field:Min(0)
    val depositMax: Long?,

    @field:Min(0)
    val rentMax: Long?
)

data class CreateAnalysisData(
    val id: String,
    val status: String,
    val progress: Int,
    val createdAt: Instant,
    val estimatedSeconds: Int,
    val links: AnalysisLinksDto
)

data class AnalysisLinksDto(
    val self: String,
    val events: String
)

data class AnalysisPollingData(
    val id: String,
    val status: String,
    val progress: Int,
    val step: AnalysisStepDto?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val error: AnalysisErrorDto?
)

data class AnalysisStepDto(
    val index: Int,
    val total: Int,
    val label: String
)

data class AnalysisErrorDto(
    val code: String,
    val message: String
)

data class AnalysisEventDto(
    val status: String,
    val progress: Int,
    val step: AnalysisStepDto?,
    val error: AnalysisErrorDto?
)
