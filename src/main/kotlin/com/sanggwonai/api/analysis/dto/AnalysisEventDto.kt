package com.sanggwonai.api.analysis.dto

data class AnalysisEventDto(
    val status: String,
    val progress: Int,
    val step: AnalysisStepDto?,
    val error: AnalysisErrorDto?
)
