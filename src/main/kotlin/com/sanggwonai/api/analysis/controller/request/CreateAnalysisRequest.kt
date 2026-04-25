package com.sanggwonai.api.analysis.controller.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

data class CreateAnalysisRequest(
    @field:NotBlank
    val businessType: String,

    @field:NotBlank
    val areaId: String,

    @field:Valid
    val budget: AnalysisBudgetRequest?
)
