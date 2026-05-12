package com.sanggwonai.api.analysis.controller.request

import com.fasterxml.jackson.annotation.JsonAlias
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateAnalysisRequest(
    @field:NotBlank
    @param:JsonAlias("businessType")
    val businessType: String,

    @field:NotBlank
    @param:JsonAlias("areaId")
    val areaId: String,

    @param:JsonAlias("transactionType")
    val transactionType: String?,

    @field:Valid
    val budget: AnalysisBudgetRequest?,

    @field:Valid
    val center: AnalysisLocationRequest?,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val x: Double?,

    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val y: Double?,

    @field:Min(1)
    @field:Max(5000)
    @param:JsonAlias("radiusM")
    val radiusM: Int?
)

data class AnalysisLocationRequest(
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    @param:JsonAlias("latitude")
    val lat: Double,

    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    @param:JsonAlias("longitude")
    val lng: Double
)
