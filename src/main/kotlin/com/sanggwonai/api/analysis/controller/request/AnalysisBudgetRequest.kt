package com.sanggwonai.api.analysis.controller.request

import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.validation.constraints.Min

data class AnalysisBudgetRequest(
    @field:Min(0)
    @param:JsonAlias("depositMax")
    val depositMax: Long?,

    @field:Min(0)
    @param:JsonAlias("rentMax")
    val rentMax: Long?,

    @field:Min(0)
    @param:JsonAlias("maintenanceFeeMax")
    val maintenanceFeeMax: Long?,

    @field:Min(0)
    @param:JsonAlias("premiumMax")
    val premiumMax: Long?,

    @field:Min(0)
    @param:JsonAlias("salePriceMax")
    val salePriceMax: Long?
)
