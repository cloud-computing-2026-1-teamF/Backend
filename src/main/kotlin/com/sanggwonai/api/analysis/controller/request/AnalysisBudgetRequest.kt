package com.sanggwonai.api.analysis.controller.request

import jakarta.validation.constraints.Min

data class AnalysisBudgetRequest(
    @field:Min(0)
    val depositMax: Long?,

    @field:Min(0)
    val rentMax: Long?
)
