package com.sanggwonai.api.analysis.controller.response

data class AnalysisEventResponse(
    val status: String,
    val progress: Int,
    val step: AnalysisStepResponse?,
    val error: AnalysisErrorResponse?
)
