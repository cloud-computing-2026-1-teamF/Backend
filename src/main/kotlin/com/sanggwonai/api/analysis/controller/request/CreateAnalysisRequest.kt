package com.sanggwonai.api.analysis.controller.request

import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

data class CreateAnalysisRequest(
    @field:NotBlank
    @param:JsonAlias("businessType")
    val businessType: String,

    @field:NotBlank
    @param:JsonAlias("areaId")
    val areaId: String,

    @field:Valid
    val budget: AnalysisBudgetRequest?
)
