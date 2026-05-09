package com.sanggwonai.api.analysis.controller.request

import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.validation.constraints.Min

data class AnalysisBudgetRequest(
    @field:Min(0)
    @param:JsonAlias("depositMax")
    val depositMax: Long?,

    @field:Min(0)
    @param:JsonAlias("rentMax")
    val rentMax: Long?
)
